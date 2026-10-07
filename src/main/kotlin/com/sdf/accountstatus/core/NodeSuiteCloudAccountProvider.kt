package com.sdf.accountstatus.core

import com.intellij.openapi.progress.ProcessCanceledException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** Loads display metadata through Oracle's public, noninteractive Node CLI commands. */
internal class NodeSuiteCloudAccountProvider(
    private val resolver: () -> NodeSuiteCloudCommand = { NodeSuiteCloudCommandResolver().resolve() },
    private val executor: NodeCliProcessExecutor = NodeSuiteCloudProcessExecutor(),
    private val processTimeoutMillis: Long = 30_000,
    private val refreshTimeoutMillis: Long = 120_000,
    private val nanoTime: () -> Long = System::nanoTime,
    private val createWorkingDirectory: () -> Path = { Files.createTempDirectory("sdf-account-discovery-") },
    private val detailParallelism: Int = 2
) {
    init {
        require(processTimeoutMillis > 0)
        require(refreshTimeoutMillis > 0)
        require(detailParallelism > 0)
    }

    fun load(
        cancellation: NodeCliCancellationCheck = NodeCliCancellationCheck.THREAD_INTERRUPTION,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): SdfAuthListLoadResult {
        val started = nanoTime()
        var directory: Path? = null
        var workers: ExecutorService? = null
        val pending = mutableSetOf<Future<IndexedAccount>>()
        try {
            checkCancellation(cancellation)
            val command = resolver()
            val workingDirectory = createWorkingDirectory()
            directory = workingDirectory
            // Never run account commands in an SDF project: the CLI loads cwd's JS configuration.
            if (!Files.isDirectory(workingDirectory) || Files.list(workingDirectory).use { it.findAny().isPresent }) {
                return unavailable(SETUP_ERROR)
            }
            fun remainingMillis(): Long {
                checkCancellation(cancellation)
                val remaining = refreshTimeoutMillis - TimeUnit.NANOSECONDS.toMillis(nanoTime() - started)
                if (remaining <= 0) throw NodeCliTimeout()
                return remaining
            }
            fun execute(options: List<String>): SdfCliExecutionResult {
                val remaining = remainingMillis()
                val result = executor.execute(command.arguments(options), workingDirectory, minOf(processTimeoutMillis, remaining), cancellation)
                checkCancellation(cancellation)
                if (result.timedOut || TimeUnit.NANOSECONDS.toMillis(nanoTime() - started) >= refreshTimeoutMillis) throw NodeCliTimeout()
                if (result.exitCode != 0 || result.stderr.isNotBlank()) throw NodeCliFailure()
                return result
            }
            val ids = NodeSuiteCloudOutputParser.authenticationIds(execute(listOf("--list")).stdout)
                ?: return unavailable(FORMAT_ERROR)
            if (ids.isEmpty()) return unavailable("No SuiteCloud authentication IDs are configured in this execution context.")
            onProgress(0, ids.size)
            val pool = Executors.newFixedThreadPool(minOf(detailParallelism, ids.size)) { task ->
                Thread(task, "SDF account details").apply { isDaemon = true }
            }
            workers = pool
            val completion = ExecutorCompletionService<IndexedAccount>(pool)
            val accounts = arrayOfNulls<SdfAuthentication>(ids.size)
            var next = 0
            fun submitNext() {
                val index = next++
                pending += completion.submit {
                    val id = ids[index]
                    val account = NodeSuiteCloudOutputParser.account(execute(listOf("--info", id)).stdout, id)
                        ?: throw NodeCliFormatFailure()
                    IndexedAccount(index, account)
                }
            }
            repeat(minOf(detailParallelism, ids.size)) { submitNext() }
            var completed = 0
            while (completed < ids.size) {
                val future = completion.poll(minOf(remainingMillis(), 20L), TimeUnit.MILLISECONDS) ?: continue
                pending.remove(future)
                val result = try {
                    future.get()
                } catch (exception: ExecutionException) {
                    throw exception.cause ?: exception
                }
                remainingMillis()
                accounts[result.index] = result.account
                onProgress(++completed, ids.size)
                // Only replace a completed task: large account lists never create an unbounded queue.
                if (next < ids.size) submitNext()
            }
            return SdfAuthListLoadResult.Available(accounts.map(::requireNotNull))
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ProcessCanceledException()
        } catch (_: NodeCliTimeout) {
            return unavailable("SuiteCloud account discovery did not finish in time. Try Refresh again.")
        } catch (_: NodeCliFormatFailure) {
            return unavailable(FORMAT_ERROR)
        } catch (_: Exception) {
            return unavailable(SETUP_ERROR)
        } finally {
            pending.forEach { it.cancel(true) }
            workers?.let { pool ->
                pool.shutdownNow()
                // Let interrupted process executors finish terminating their children before cwd cleanup.
                var interrupted = Thread.interrupted()
                try {
                    pool.awaitTermination(5, TimeUnit.SECONDS)
                } catch (_: InterruptedException) {
                    interrupted = true
                } finally {
                    if (interrupted) Thread.currentThread().interrupt()
                }
            }
            // Oracle manageauth should leave the empty cwd alone. Never recursively delete unexpected files.
            directory?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }

    private fun checkCancellation(check: NodeCliCancellationCheck) {
        NodeCliCancellationCheck.THREAD_INTERRUPTION.checkCancelled()
        check.checkCancelled()
    }

    private fun unavailable(message: String) = SdfAuthListLoadResult.Unavailable(message)
    private data class IndexedAccount(val index: Int, val account: SdfAuthentication)
    private class NodeCliTimeout : Exception()
    private class NodeCliFailure : Exception()
    private class NodeCliFormatFailure : Exception()

    private companion object {
        const val SETUP_ERROR = "Unable to read SuiteCloud accounts. Verify the Node.js CLI, Node.js, Java, and authentication context."
        const val FORMAT_ERROR = "SuiteCloud CLI returned unrecognized account details. Check the supported CLI version."
    }
}
