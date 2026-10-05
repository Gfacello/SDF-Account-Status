package com.sdf.accountstatus.core

import com.intellij.openapi.progress.ProcessCanceledException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal fun interface NodeCliCancellationCheck {
    fun checkCancelled()

    companion object {
        val THREAD_INTERRUPTION = NodeCliCancellationCheck {
            if (Thread.currentThread().isInterrupted) throw ProcessCanceledException()
        }
    }
}

internal fun interface NodeCliProcessExecutor {
    fun execute(
        command: List<String>,
        workingDirectory: Path,
        timeoutMillis: Long,
        cancellation: NodeCliCancellationCheck
    ): SdfCliExecutionResult
}

/** Bounded in-memory capture. All errors and output stay out of logs. */
internal class NodeSuiteCloudProcessExecutor(
    private val outputLimitBytes: Int = 1_048_576
) : NodeCliProcessExecutor {
    init {
        require(outputLimitBytes > 0)
    }

    override fun execute(
        command: List<String>,
        workingDirectory: Path,
        timeoutMillis: Long,
        cancellation: NodeCliCancellationCheck
    ): SdfCliExecutionResult {
        checkCancellation(cancellation)
        if (timeoutMillis <= 0) return timedOut()
        val process = ProcessBuilder(command)
            .directory(workingDirectory.toFile())
            .apply { environment()["NO_COLOR"] = "1"; environment().remove("FORCE_COLOR") }
            .start()
        val readers = Executors.newFixedThreadPool(2) { task ->
            Thread(task, "SDF account CLI output").apply { isDaemon = true }
        }
        val overflow = AtomicBoolean(false)
        val stdout = readers.submit<String> { capture(process.inputStream, overflow) }
        val stderr = readers.submit<String> { capture(process.errorStream, overflow) }
        val descendants = linkedMapOf<Long, ProcessHandle>()
        val started = System.nanoTime()
        var timeout = false
        try {
            process.outputStream.close() // No interactive input is ever supplied.
            while (true) {
                rememberDescendants(process, descendants)
                checkCancellation(cancellation)
                if (overflow.get()) return failed()
                val remaining = timeoutMillis - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                if (remaining <= 0) {
                    timeout = true
                    return timedOut()
                }
                if (process.waitFor(minOf(remaining, 20L), TimeUnit.MILLISECONDS)) break
            }
            checkCancellation(cancellation)
            // A finished launcher must not leave SDK children retaining captured pipes.
            stopDescendants(descendants)
            val remaining = timeoutMillis - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            if (remaining <= 0) return timedOut()
            val output = stdout.get(remaining, TimeUnit.MILLISECONDS)
            val remainingAfterOutput = timeoutMillis - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            if (remainingAfterOutput <= 0) return timedOut()
            val errors = stderr.get(remainingAfterOutput, TimeUnit.MILLISECONDS)
            checkCancellation(cancellation)
            if (overflow.get()) return failed()
            return SdfCliExecutionResult(process.exitValue(), output, errors)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ProcessCanceledException()
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (_: java.util.concurrent.TimeoutException) {
            timeout = true
            return timedOut()
        } catch (_: Exception) {
            return if (timeout) timedOut() else failed()
        } finally {
            // Cleanup must neither mask cancellation nor skip the other resources if one close fails.
            runCatching { rememberDescendants(process, descendants) }
            runCatching { stopDescendants(descendants) }
            runCatching { if (process.isAlive) process.destroyForcibly() }
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            stdout.cancel(true)
            stderr.cancel(true)
            readers.shutdownNow()
        }
    }

    private fun capture(stream: InputStream, overflow: AtomicBoolean): String {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (bytes.size() + count > outputLimitBytes) {
                overflow.set(true)
                return ""
            }
            bytes.write(buffer, 0, count)
        }
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
    }

    private fun rememberDescendants(process: Process, known: MutableMap<Long, ProcessHandle>) {
        process.descendants().use { children -> children.forEach { known[it.pid()] = it } }
    }

    private fun stopDescendants(known: Map<Long, ProcessHandle>) {
        // Capture grandchildren again before termination, while their parents still exist.
        val all = linkedMapOf<Long, ProcessHandle>().apply { putAll(known) }
        known.values.forEach { child ->
            runCatching { child.descendants().use { descendants -> descendants.forEach { all[it.pid()] = it } } }
        }
        all.values.toList().asReversed().forEach { runCatching { if (it.isAlive) it.destroyForcibly() } }
    }

    private fun checkCancellation(check: NodeCliCancellationCheck) {
        NodeCliCancellationCheck.THREAD_INTERRUPTION.checkCancelled()
        check.checkCancelled()
    }

    private fun timedOut() = SdfCliExecutionResult(-1, "", "", timedOut = true)
    private fun failed() = SdfCliExecutionResult(-1, "", "")
}
