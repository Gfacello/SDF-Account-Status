package com.sdf.accountstatus.core

import com.intellij.openapi.progress.ProcessCanceledException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Loads display metadata through Oracle's public, noninteractive Node CLI commands. */
internal class NodeSuiteCloudAccountProvider(
    private val resolver: () -> NodeSuiteCloudCommand = { NodeSuiteCloudCommandResolver().resolve() },
    private val executor: NodeCliProcessExecutor = NodeSuiteCloudProcessExecutor(),
    private val processTimeoutMillis: Long = 30_000,
    private val refreshTimeoutMillis: Long = 120_000,
    private val nanoTime: () -> Long = System::nanoTime,
    private val createWorkingDirectory: () -> Path = { Files.createTempDirectory("sdf-account-discovery-") }
) {
    init {
        require(processTimeoutMillis > 0)
        require(refreshTimeoutMillis > 0)
    }

    fun load(cancellation: NodeCliCancellationCheck = NodeCliCancellationCheck.THREAD_INTERRUPTION): SdfAuthListLoadResult {
        val started = nanoTime()
        var directory: Path? = null
        try {
            checkCancellation(cancellation)
            val command = resolver()
            val workingDirectory = createWorkingDirectory()
            directory = workingDirectory
            // Never run account commands in an SDF project: the CLI loads cwd's JS configuration.
            if (!Files.isDirectory(workingDirectory) || Files.list(workingDirectory).use { it.findAny().isPresent }) {
                return unavailable(SETUP_ERROR)
            }
            fun execute(options: List<String>): SdfCliExecutionResult {
                checkCancellation(cancellation)
                val remaining = refreshTimeoutMillis - TimeUnit.NANOSECONDS.toMillis(nanoTime() - started)
                if (remaining <= 0) throw NodeCliTimeout()
                val result = executor.execute(command.arguments(options), workingDirectory, minOf(processTimeoutMillis, remaining), cancellation)
                checkCancellation(cancellation)
                if (result.timedOut || TimeUnit.NANOSECONDS.toMillis(nanoTime() - started) >= refreshTimeoutMillis) throw NodeCliTimeout()
                if (result.exitCode != 0 || result.stderr.isNotBlank()) throw NodeCliFailure()
                return result
            }
            val ids = NodeSuiteCloudOutputParser.authenticationIds(execute(listOf("--list")).stdout)
                ?: return unavailable(FORMAT_ERROR)
            if (ids.isEmpty()) return unavailable("No SuiteCloud authentication IDs are configured in this execution context.")
            val accounts = ids.map { id ->
                NodeSuiteCloudOutputParser.account(execute(listOf("--info", id)).stdout, id)
                    ?: return unavailable(FORMAT_ERROR)
            }
            return SdfAuthListLoadResult.Available(accounts)
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ProcessCanceledException()
        } catch (_: NodeCliTimeout) {
            return unavailable("SuiteCloud account discovery did not finish in time. Try Refresh again.")
        } catch (_: Exception) {
            return unavailable(SETUP_ERROR)
        } finally {
            // Oracle manageauth should leave the empty cwd alone. Never recursively delete unexpected files.
            directory?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }

    private fun checkCancellation(check: NodeCliCancellationCheck) {
        NodeCliCancellationCheck.THREAD_INTERRUPTION.checkCancelled()
        check.checkCancelled()
    }

    private fun unavailable(message: String) = SdfAuthListLoadResult.Unavailable(message)
    private class NodeCliTimeout : Exception()
    private class NodeCliFailure : Exception()

    private companion object {
        const val SETUP_ERROR = "Unable to read SuiteCloud accounts. Verify the Node.js CLI, Node.js, Java, and authentication context."
        const val FORMAT_ERROR = "SuiteCloud CLI returned unrecognized account details. Check the supported CLI version."
    }
}
