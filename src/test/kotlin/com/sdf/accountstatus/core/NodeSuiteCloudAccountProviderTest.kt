package com.sdf.accountstatus.core

import com.intellij.openapi.progress.ProcessCanceledException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NodeSuiteCloudAccountProviderTest {
    private val command = NodeSuiteCloudCommand(Path.of("/installed/node"), Path.of("/installed/suitecloud.js"))

    @Test
    fun `lists then loads each distinct auth id in an empty temporary directory`() {
        val commands = mutableListOf<List<String>>()
        val workingDirectories = mutableListOf<Path>()
        val provider = NodeSuiteCloudAccountProvider(resolver = { command }, executor = NodeCliProcessExecutor { args, cwd, _, _ ->
            commands += args
            workingDirectories.add(cwd)
            assertFalse(Files.list(cwd).use { it.findAny().isPresent })
            success(when (args.last()) {
                "--list" -> "example_sb | Developer @ Example Co\nexample_admin | Administrator @ Example Co"
                "example_sb" -> NodeSuiteCloudOutputParserTest.INFO
                else -> NodeSuiteCloudOutputParserTest.INFO.replace("example_sb", "example_admin").replace("Developer", "Administrator")
            })
        })
        val result = assertIs<SdfAuthListLoadResult.Available>(provider.load())
        assertEquals(listOf("example_sb", "example_admin"), result.accounts.map { it.authenticationId })
        assertEquals(1, result.accounts.map { it.accountDetails?.accountId }.distinct().size)
        assertEquals(listOf(
            command.arguments(listOf("--list")), command.arguments(listOf("--info", "example_sb")),
            command.arguments(listOf("--info", "example_admin"))
        ), commands)
        assertEquals(1, workingDirectories.distinct().size)
        assertFalse(Files.exists(workingDirectories.first()))
    }

    @Test
    fun `publishes no partial account list when detail lookup fails`() {
        var calls = 0
        val provider = NodeSuiteCloudAccountProvider(resolver = { command }, executor = NodeCliProcessExecutor { _, _, _, _ ->
            calls++
            success(when (calls) {
                1 -> "example_sb | Developer @ Example Co\nexample_admin | Administrator @ Example Co"
                2 -> NodeSuiteCloudOutputParserTest.INFO
                else -> "RAW_SECRET"
            })
        })
        assertRedacted(provider.load())
        assertEquals(3, calls)
    }

    @Test
    fun `returns safe failure for zero-exit stdout errors stderr nonzero and exceptions`() {
        listOf(
            success("RAW_SECRET"), SdfCliExecutionResult(0, "", "RAW_SECRET"),
            SdfCliExecutionResult(1, "RAW_SECRET", "RAW_SECRET")
        ).forEach { result ->
            assertRedacted(NodeSuiteCloudAccountProvider(resolver = { command }, executor = NodeCliProcessExecutor { _, _, _, _ -> result }).load())
        }
        assertRedacted(NodeSuiteCloudAccountProvider(resolver = { throw IllegalStateException("RAW_SECRET") }).load())
    }

    @Test
    fun `bounds each process by the remaining whole-refresh budget`() {
        var nanos = 0L
        val timeouts = mutableListOf<Long>()
        val provider = NodeSuiteCloudAccountProvider(
            resolver = { command }, processTimeoutMillis = 90, refreshTimeoutMillis = 100, nanoTime = { nanos },
            executor = NodeCliProcessExecutor { _, _, timeout, _ ->
                timeouts += timeout
                nanos += TimeUnit.MILLISECONDS.toNanos(70)
                success(if (timeouts.size == 1) "example_sb | Developer @ Example Co" else NodeSuiteCloudOutputParserTest.INFO)
            }
        )
        val result = assertIs<SdfAuthListLoadResult.Unavailable>(provider.load())
        assertTrue(result.message.contains("in time"))
        assertEquals(listOf(90L, 30L), timeouts)
    }

    @Test
    fun `per-process timeout fails without exposing output`() {
        val provider = NodeSuiteCloudAccountProvider(resolver = { command }, executor = NodeCliProcessExecutor { _, _, _, _ ->
            SdfCliExecutionResult(0, "RAW_SECRET", "RAW_SECRET", timedOut = true)
        })
        assertTrue(assertRedacted(provider.load()).message.contains("in time"))
    }

    @Test
    fun `cancellation propagates and removes the working directory`() {
        var workingDirectory: Path? = null
        val provider = NodeSuiteCloudAccountProvider(resolver = { command }, executor = NodeCliProcessExecutor { _, cwd, _, _ ->
            workingDirectory = cwd
            throw ProcessCanceledException()
        })
        assertFailsWith<ProcessCanceledException> { provider.load() }
        assertFalse(Files.exists(workingDirectory!!))
    }

    @Test
    fun `refuses a nonempty working directory and preserves its project file`() {
        val directory = Files.createTempDirectory("sdf-provider-test-")
        try {
            val projectFile = directory.resolve("project.json")
            Files.writeString(projectFile, "{\"defaultAuthId\":\"unchanged\"}")
            val provider = NodeSuiteCloudAccountProvider(
                resolver = { command }, createWorkingDirectory = { directory },
                executor = NodeCliProcessExecutor { _, _, _, _ -> error("Must not execute") }
            )
            assertIs<SdfAuthListLoadResult.Unavailable>(provider.load())
            assertEquals("{\"defaultAuthId\":\"unchanged\"}", Files.readString(projectFile))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun assertRedacted(result: SdfAuthListLoadResult): SdfAuthListLoadResult.Unavailable =
        assertIs<SdfAuthListLoadResult.Unavailable>(result).also { assertFalse(it.message.contains("RAW_SECRET")) }

    private fun success(stdout: String) = SdfCliExecutionResult(0, stdout, "")
}
