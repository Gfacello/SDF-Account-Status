package com.sdf.accountstatus.core

import com.intellij.openapi.progress.ProcessCanceledException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Real subprocess/pipe tests using a JDK source launcher, requiring no installed Node or accounts. */
class NodeSuiteCloudProcessExecutorTest {
    private val java = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
    private val noCancellation = NodeCliCancellationCheck.THREAD_INTERRUPTION

    @Test
    fun `real process honors spaced paths argument boundaries and working directory`() = fixture { root, source ->
        val result = NodeSuiteCloudProcessExecutor().execute(
            listOf(java.toString(), source.toString(), "echo", "literal $() and spaces"), root, 15_000, noCancellation
        )
        assertEquals(0, result.exitCode)
        assertEquals("literal $() and spaces\n${root.toRealPath()}\n", result.stdout)
        assertEquals("", result.stderr)
    }

    @Test
    fun `actual process provider round trip does not touch project defaults`() = fixture { root, source ->
        val project = root.resolve("project.json")
        Files.writeString(project, "{\"defaultAuthId\":\"keep\",\"other\":123}")
        val provider = NodeSuiteCloudAccountProvider(resolver = { NodeSuiteCloudCommand(java, source) })
        val result = assertIs<SdfAuthListLoadResult.Available>(provider.load())
        assertEquals("example_sb", result.accounts.single().authenticationId)
        assertEquals("1234567_SB1", result.accounts.single().accountDetails?.accountId)
        assertEquals("{\"defaultAuthId\":\"keep\",\"other\":123}", Files.readString(project))
    }

    @Test
    fun `enforces time and output limits with real processes`() = fixture { root, source ->
        val timeout = NodeSuiteCloudProcessExecutor().execute(
            listOf(java.toString(), source.toString(), "slow"), root, 300, noCancellation
        )
        assertTrue(timeout.timedOut)
        val excessive = NodeSuiteCloudProcessExecutor(outputLimitBytes = 64).execute(
            listOf(java.toString(), source.toString(), "large"), root, 15_000, noCancellation
        )
        assertEquals(-1, excessive.exitCode)
        assertEquals("", excessive.stdout)
        assertEquals("", excessive.stderr)
    }

    @Test
    fun `preserves stderr and nonzero result for provider validation`() = fixture { root, source ->
        val result = NodeSuiteCloudProcessExecutor().execute(
            listOf(java.toString(), source.toString(), "error"), root, 15_000, noCancellation
        )
        assertEquals(7, result.exitCode)
        assertTrue(result.stderr.contains("fixture stderr"))
        assertFalse(result.toString().contains("fixture stderr"))
    }

    @Test
    fun `rejects malformed UTF-8 rather than accepting replacement characters`() = fixture { root, source ->
        val result = NodeSuiteCloudProcessExecutor().execute(
            listOf(java.toString(), source.toString(), "invalid-utf8"), root, 15_000, noCancellation
        )
        assertEquals(-1, result.exitCode)
        assertEquals("", result.stdout)
        assertEquals("", result.stderr)
    }

    @Test
    fun `timeout kills a launcher and its child after they have started`() = fixture { root, source ->
        val pidFile = root.resolve("child.pid")
        var child: ProcessHandle? = null
        try {
            val result = NodeSuiteCloudProcessExecutor().execute(
                listOf(java.toString(), source.toString(), "spawn", pidFile.toString(), source.toString()), root, 5_000, noCancellation
            )
            assertTrue(result.timedOut)
            assertTrue(Files.exists(pidFile), "The fixture must have spawned its child before the timeout")
            val childId = Files.readString(pidFile).trim().toLong()
            child = ProcessHandle.of(childId).orElse(null)
            await(5_000) { child?.isAlive != true }
        } finally {
            child?.takeIf { it.isAlive }?.destroyForcibly()
        }
    }

    @Test
    fun `cancellation kills a long-lived launcher and its child`() = fixture { root, source ->
        val cancelled = AtomicBoolean(false)
        val pidFile = root.resolve("child.pid")
        val workers = Executors.newSingleThreadExecutor()
        var child: ProcessHandle? = null
        try {
            val future = workers.submit<Boolean> {
                assertFailsWith<ProcessCanceledException> {
                    NodeSuiteCloudProcessExecutor().execute(
                        listOf(java.toString(), source.toString(), "spawn", pidFile.toString(), source.toString()), root, 30_000,
                        NodeCliCancellationCheck { if (cancelled.get()) throw ProcessCanceledException() }
                    )
                }
                true
            }
            await(15_000) { Files.exists(pidFile) && Files.size(pidFile) > 0 }
            child = ProcessHandle.of(Files.readString(pidFile).trim().toLong()).orElseThrow()
            assertTrue(child.isAlive)
            cancelled.set(true)
            assertTrue(future.get(5, TimeUnit.SECONDS))
            await(5_000) { !child.isAlive }
        } finally {
            cancelled.set(true)
            child?.takeIf { it.isAlive }?.destroyForcibly()
            workers.shutdownNow()
        }
    }

    @Test
    fun `thread interruption cancels the worker process`() = fixture { root, source ->
        val workers = Executors.newSingleThreadExecutor()
        val cancelled = AtomicBoolean(false)
        val pidFile = root.resolve("child.pid")
        var child: ProcessHandle? = null
        try {
            val future = workers.submit {
                try {
                    NodeSuiteCloudProcessExecutor().execute(
                        listOf(java.toString(), source.toString(), "spawn", pidFile.toString(), source.toString()), root, 30_000, noCancellation
                    )
                } catch (_: ProcessCanceledException) {
                    cancelled.set(true)
                }
            }
            await(15_000) { Files.exists(pidFile) && Files.size(pidFile) > 0 }
            child = ProcessHandle.of(Files.readString(pidFile).trim().toLong()).orElseThrow()
            future.cancel(true)
            await(5_000) { cancelled.get() && !child.isAlive }
        } finally {
            child?.takeIf { it.isAlive }?.destroyForcibly()
            workers.shutdownNow()
        }
    }

    private fun await(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!condition()) {
            if (System.nanoTime() >= deadline) error("Synthetic process did not reach its expected state")
            Thread.sleep(20)
        }
    }

    private fun fixture(block: (Path, Path) -> Unit) {
        val root = Files.createTempDirectory("sdf process fixture ").toRealPath()
        val source = root.resolve("Fixture.java")
        Files.writeString(source, """
            import java.nio.file.*;
            class Fixture {
                public static void main(String[] args) throws Exception {
                    switch (args[0]) {
                        case "echo":
                            System.out.println(args[1]);
                            System.out.println(Path.of("").toRealPath());
                            break;
                        case "error": System.err.println("fixture stderr"); System.exit(7); break;
                        case "large": System.out.print("x".repeat(100000)); break;
                        case "invalid-utf8": System.out.write(new byte[] {(byte) 0xc3, (byte) 0x28}); break;
                        case "slow": case "child": Thread.sleep(60000); break;
                        case "spawn":
                            String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
                            Process child = new ProcessBuilder(java, args[2], "child").start();
                            Files.writeString(Path.of(args[1]), Long.toString(child.pid()));
                            Thread.sleep(60000);
                            break;
                        case "account:manageauth":
                            if (Files.exists(Path.of("suitecloud.config.js")) || Files.exists(Path.of("project.json"))) System.exit(9);
                            if (args[1].equals("--list")) System.out.println("example_sb | Developer @ Example Co");
                            else if (args[1].equals("--info") && args[2].equals("example_sb")) {
                                System.out.println("Authentication ID: example_sb\nAccount Name: Example Co\nAccount ID: 1234567_SB1\nRole: Developer\nDomain: 1234567-sb1.app.netsuite.com\nAccount Type: Sandbox");
                            } else System.exit(10);
                            break;
                        default: System.exit(11);
                    }
                }
            }
        """.trimIndent())
        try { block(root, source) } finally { root.toFile().deleteRecursively() }
    }
}
