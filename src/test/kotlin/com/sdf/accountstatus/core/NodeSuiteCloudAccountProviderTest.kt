package com.sdf.accountstatus.core

import com.intellij.openapi.progress.ProcessCanceledException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
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
        val provider = NodeSuiteCloudAccountProvider(resolver = { command }, detailParallelism = 1, executor = NodeCliProcessExecutor { args, cwd, _, _ ->
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
        val provider = NodeSuiteCloudAccountProvider(resolver = { command }, detailParallelism = 1, executor = NodeCliProcessExecutor { _, _, _, _ ->
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
    fun `empty account list retains its explanation without reporting zero-total progress`() {
        val provider = NodeSuiteCloudAccountProvider(resolver = { command }, executor = NodeCliProcessExecutor { _, _, _, _ ->
            success("There are no authentication IDs available.")
        })
        val result = assertIs<SdfAuthListLoadResult.Unavailable>(provider.load(onProgress = { _, _ ->
            error("An empty list has no detail-loading progress")
        }))
        assertEquals("No SuiteCloud authentication IDs are configured in this execution context.", result.message)
    }

    @Test
    fun `bounds each process by the remaining whole-refresh budget`() {
        val nanos = AtomicLong()
        val timeouts = mutableListOf<Long>()
        val provider = NodeSuiteCloudAccountProvider(
            resolver = { command }, processTimeoutMillis = 90, refreshTimeoutMillis = 100, nanoTime = nanos::get,
            executor = NodeCliProcessExecutor { _, _, timeout, _ ->
                timeouts += timeout
                nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(70))
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

    @Test
    fun `loads at most two details concurrently with verified progress and preserves list order`() {
        val ids = listOf("first", "second", "third", "fourth")
        val started = ids.associateWith { CountDownLatch(1) }
        val release = ids.associateWith { CountDownLatch(1) }
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()
        val detailThreads = ConcurrentHashMap.newKeySet<Thread>()
        val progressThreads = ConcurrentHashMap.newKeySet<Thread>()
        val progress = CopyOnWriteArrayList<Pair<Int, Int>>()
        val provider = NodeSuiteCloudAccountProvider(resolver = { command }, executor = NodeCliProcessExecutor { args, _, _, _ ->
            val id = args.last()
            if (id == "--list") {
                success(listOutput(ids))
            } else {
                detailThreads += Thread.currentThread()
                maximumActive.accumulateAndGet(active.incrementAndGet(), ::maxOf)
                started.getValue(id).countDown()
                try {
                    await(release.getValue(id))
                    success(infoOutput(id))
                } finally {
                    active.decrementAndGet()
                }
            }
        })
        val coordinator = Executors.newSingleThreadExecutor()
        try {
            val load = coordinator.submit<SdfAuthListLoadResult> {
                provider.load(onProgress = { completed, total ->
                    progressThreads += Thread.currentThread()
                    progress += completed to total
                })
            }
            await(started.getValue("first"))
            await(started.getValue("second"))
            assertEquals(1L, started.getValue("third").count)
            assertEquals(listOf(0 to 4), progress.toList())
            release.getValue("second").countDown()
            await(started.getValue("third"))
            assertEquals(listOf(0 to 4, 1 to 4), progress.toList())
            release.getValue("third").countDown()
            await(started.getValue("fourth"))
            assertEquals(listOf(0 to 4, 1 to 4, 2 to 4), progress.toList())
            release.getValue("fourth").countDown()
            release.getValue("first").countDown()
            val result = assertIs<SdfAuthListLoadResult.Available>(load.get(10, TimeUnit.SECONDS))
            assertEquals(ids, result.accounts.map { it.authenticationId })
            assertEquals((0..4).map { it to 4 }, progress.toList())
            assertEquals(2, maximumActive.get())
            assertEquals(1, progressThreads.size)
            assertTrue(detailThreads.intersect(progressThreads).isEmpty())
        } finally {
            release.values.forEach(CountDownLatch::countDown)
            coordinator.shutdownNow()
        }
    }

    @Test
    fun `failed detail cancels sibling and does not publish partial results or raw output`() {
        val siblingStarted = CountDownLatch(1)
        val siblingStopped = CountDownLatch(1)
        val workingDirectory = AtomicReference<Path>()
        val calls = CopyOnWriteArrayList<String>()
        val progress = mutableListOf<Pair<Int, Int>>()
        val provider = NodeSuiteCloudAccountProvider(resolver = { command }, executor = NodeCliProcessExecutor { args, cwd, _, _ ->
            workingDirectory.set(cwd)
            val id = args.last()
            calls += id
            when (id) {
                "--list" -> success(listOutput(listOf("failing", "sibling", "queued")))
                "failing" -> {
                    await(siblingStarted)
                    success("RAW_SECRET")
                }
                else -> {
                    siblingStarted.countDown()
                    try {
                        CountDownLatch(1).await()
                        error("Sibling should be interrupted")
                    } finally {
                        siblingStopped.countDown()
                    }
                }
            }
        })
        val result = assertRedacted(provider.load(onProgress = { completed, total -> progress += completed to total }))
        assertTrue(result.message.contains("unrecognized"))
        await(siblingStopped)
        assertEquals(setOf("--list", "failing", "sibling"), calls.toSet())
        assertEquals(listOf(0 to 3), progress)
        assertFalse(Files.exists(workingDirectory.get()))
    }

    @Test
    fun `coordinator cancellation interrupts outstanding details and removes working directory`() {
        val detailsStarted = CountDownLatch(2)
        val detailsStopped = CountDownLatch(2)
        val cancelled = AtomicBoolean()
        val workingDirectory = AtomicReference<Path>()
        val provider = NodeSuiteCloudAccountProvider(resolver = { command }, executor = NodeCliProcessExecutor { args, cwd, _, _ ->
            workingDirectory.set(cwd)
            if (args.last() == "--list") {
                success(listOutput(listOf("first", "second", "queued")))
            } else {
                detailsStarted.countDown()
                try {
                    CountDownLatch(1).await()
                    error("Detail should be interrupted")
                } finally {
                    detailsStopped.countDown()
                }
            }
        })
        val coordinator = Executors.newSingleThreadExecutor()
        try {
            val load = coordinator.submit<SdfAuthListLoadResult> {
                provider.load(cancellation = NodeCliCancellationCheck {
                    if (cancelled.get()) throw ProcessCanceledException()
                })
            }
            await(detailsStarted)
            cancelled.set(true)
            val thrown = assertFailsWith<ExecutionException> { load.get(10, TimeUnit.SECONDS) }
            assertIs<ProcessCanceledException>(thrown.cause)
            await(detailsStopped)
            assertFalse(Files.exists(workingDirectory.get()))
        } finally {
            cancelled.set(true)
            coordinator.shutdownNow()
        }
    }

    @Test
    fun `parallel details share whole-refresh deadline and timeout cancels their processes`() {
        val nanos = AtomicLong()
        val detailsStarted = CountDownLatch(2)
        val detailsStopped = CountDownLatch(2)
        val timeouts = CopyOnWriteArrayList<Long>()
        val provider = NodeSuiteCloudAccountProvider(
            resolver = { command }, processTimeoutMillis = 90, refreshTimeoutMillis = 100, nanoTime = nanos::get,
            executor = NodeCliProcessExecutor { args, _, timeout, _ ->
                timeouts += timeout
                if (args.last() == "--list") {
                    nanos.set(TimeUnit.MILLISECONDS.toNanos(70))
                    success(listOutput(listOf("first", "second", "queued")))
                } else {
                    detailsStarted.countDown()
                    try {
                        CountDownLatch(1).await()
                        error("Detail should be interrupted")
                    } finally {
                        detailsStopped.countDown()
                    }
                }
            }
        )
        val coordinator = Executors.newSingleThreadExecutor()
        try {
            val load = coordinator.submit<SdfAuthListLoadResult> { provider.load() }
            await(detailsStarted)
            nanos.set(TimeUnit.MILLISECONDS.toNanos(100))
            assertTrue(assertRedacted(load.get(10, TimeUnit.SECONDS)).message.contains("in time"))
            await(detailsStopped)
            assertEquals(listOf(90L, 30L, 30L), timeouts.toList())
        } finally {
            coordinator.shutdownNow()
        }
    }

    private fun await(latch: CountDownLatch) = assertTrue(latch.await(10, TimeUnit.SECONDS), "Timed out waiting for worker")
    private fun listOutput(ids: List<String>) = ids.joinToString("\n") { "$it | Developer @ Example Co" }
    private fun infoOutput(id: String) = NodeSuiteCloudOutputParserTest.INFO.replace("example_sb", id)

    private fun assertRedacted(result: SdfAuthListLoadResult): SdfAuthListLoadResult.Unavailable =
        assertIs<SdfAuthListLoadResult.Unavailable>(result).also { assertFalse(it.message.contains("RAW_SECRET")) }

    private fun success(stdout: String) = SdfCliExecutionResult(0, stdout, "")
}
