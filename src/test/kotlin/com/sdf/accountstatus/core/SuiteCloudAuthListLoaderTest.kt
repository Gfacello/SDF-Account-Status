package com.sdf.accountstatus.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SuiteCloudAuthListLoaderTest {
    @Test
    fun `returns available accounts for successful CLI output`() {
        val loader = loaderReturning(
            SdfCliExecutionResult(
                exitCode = 0,
                stdout = """
                    The following authentication IDs are available for your account:
                    sandbox-admin | 123456_SB1: Example Company [Administrator]
                """.trimIndent(),
                stderr = ""
            )
        )

        val result = assertIs<SdfAuthListLoadResult.Available>(loader.load())

        assertEquals("sandbox-admin", result.accounts.single().authenticationId)
        assertEquals(
            "123456_SB1: Example Company [Administrator]",
            result.accounts.single().details
        )
    }

    @Test
    fun `returns unavailable when no authentication IDs are configured`() {
        val loader = loaderReturning(
            SdfCliExecutionResult(
                exitCode = 0,
                stdout = "There are no authentication IDs available.",
                stderr = ""
            )
        )

        val result = assertSafeUnavailable(loader.load())
        assertTrue(result.message.contains("No SuiteCloud authentication IDs"))
    }

    @Test
    fun `returns unavailable without exposing malformed CLI output`() {
        val stdoutSecret = "RAW_STDOUT_SECRET"
        val loader = loaderReturning(
            SdfCliExecutionResult(
                exitCode = 0,
                stdout = "unexpected $stdoutSecret output",
                stderr = ""
            )
        )

        val result = assertSafeUnavailable(loader.load(), stdoutSecret)
        assertTrue(result.message.contains("unrecognized account list"))
    }

    @Test
    fun `returns unavailable without exposing stderr from a zero exit command`() {
        val stderrSecret = "RAW_STDERR_SECRET"
        val loader = loaderReturning(
            SdfCliExecutionResult(
                exitCode = 0,
                stdout = "There are no authentication IDs available.",
                stderr = stderrSecret
            )
        )

        assertSafeUnavailable(loader.load(), stderrSecret)
    }

    @Test
    fun `returns unavailable without exposing output when CLI exits nonzero`() {
        val stdoutSecret = "RAW_FAILURE_STDOUT_SECRET"
        val stderrSecret = "RAW_FAILURE_STDERR_SECRET"
        val loader = loaderReturning(
            SdfCliExecutionResult(
                exitCode = 1,
                stdout = """
                    The following authentication IDs are available for your account:
                    failure-auth | 123456: $stdoutSecret [Administrator]
                """.trimIndent(),
                stderr = stderrSecret
            )
        )

        val result = assertSafeUnavailable(loader.load(), stdoutSecret, stderrSecret)
        assertTrue(result.message.contains("Check the SuiteCloud CLI setup"))
    }

    @Test
    fun `returns unavailable without exposing output when CLI times out`() {
        val stdoutSecret = "RAW_TIMEOUT_STDOUT_SECRET"
        val stderrSecret = "RAW_TIMEOUT_STDERR_SECRET"
        val loader = loaderReturning(
            SdfCliExecutionResult(
                exitCode = 0,
                stdout = """
                    The following authentication IDs are available for your account:
                    timeout-auth | 123456: $stdoutSecret [Administrator]
                """.trimIndent(),
                stderr = stderrSecret,
                timedOut = true
            )
        )

        val result = assertSafeUnavailable(loader.load(), stdoutSecret, stderrSecret)
        assertTrue(result.message.contains("did not finish"))
    }

    @Test
    fun `returns unavailable without exposing a command exception`() {
        val exceptionSecret = "RAW_EXCEPTION_SECRET"
        val loader = SuiteCloudAuthListLoader(
            SdfCliExecutor { throw IllegalStateException(exceptionSecret) }
        )

        assertSafeUnavailable(loader.load(), exceptionSecret)
    }

    @Test
    fun `returns a safe unavailable message when SuiteCloud CLI is missing`() {
        val loader = SuiteCloudAuthListLoader(
            SdfCliExecutor { throw SuiteCloudCliUnavailableException() }
        )

        val result = assertIs<SdfAuthListLoadResult.Unavailable>(loader.load())
        assertTrue(result.message.contains("SuiteCloud CLI"))
        assertFalse(result.message.contains(".suitecloud-sdk"))
    }

    @Test
    fun `execution result string representation redacts captured output`() {
        val result = SdfCliExecutionResult(
            exitCode = 1,
            stdout = "RAW_STDOUT_SECRET",
            stderr = "RAW_STDERR_SECRET"
        )

        assertFalse(result.toString().contains("RAW_STDOUT_SECRET"))
        assertFalse(result.toString().contains("RAW_STDERR_SECRET"))
    }

    @Test
    fun `prefers an installed sdfcli launcher and passes only the read-only arguments`() {
        withTempDirectory { root ->
            val launcher = createExecutable(root.resolve("bin/sdfcli"))
            val resolver = SuiteCloudCliCommandResolver(
                sdkRoot = root.resolve("missing-sdk"),
                launcherSearchDirectories = listOf(launcher.parent),
                javaExecutables = emptyList(),
                osName = "Mac OS X"
            )

            val command = resolver.resolve()

            assertEquals(launcher, command.executable)
            assertEquals(listOf("manageauth", "-list"), command.arguments)
        }
    }

    @Test
    fun `default launcher discovery is confined to the configured SDK root`() {
        withTempDirectory { root ->
            val sdkRoot = root.resolve(".suitecloud-sdk")
            val launcher = createExecutable(sdkRoot.resolve("bin/sdfcli"))
            val resolver = SuiteCloudCliCommandResolver(
                sdkRoot = sdkRoot,
                javaExecutables = emptyList(),
                osName = "Mac OS X"
            )

            val command = resolver.resolve()

            assertEquals(launcher, command.executable)
            assertEquals(listOf("manageauth", "-list"), command.arguments)
        }
    }

    @Test
    fun `uses search directory order when more than one launcher exists`() {
        withTempDirectory { root ->
            val firstLauncher = createExecutable(root.resolve("first/sdfcli"))
            createExecutable(root.resolve("second/sdfcli"))
            val resolver = SuiteCloudCliCommandResolver(
                sdkRoot = root.resolve("missing-sdk"),
                launcherSearchDirectories = listOf(
                    firstLauncher.parent,
                    root.resolve("second")
                ),
                javaExecutables = emptyList(),
                osName = "Mac OS X"
            )

            assertEquals(firstLauncher, resolver.resolve().executable)
        }
    }

    @Test
    fun `ignores relative launcher locations`() {
        withTempDirectory { root ->
            val resolver = SuiteCloudCliCommandResolver(
                sdkRoot = root.resolve("missing-sdk"),
                launcherSearchDirectories = listOf(Path.of("bin")),
                javaExecutables = emptyList(),
                osName = "Mac OS X"
            )

            assertFailsWith<SuiteCloudCliUnavailableException> { resolver.resolve() }
        }
    }

    @Test
    fun `falls back to newest SDK jar using numeric version order`() {
        withTempDirectory { root ->
            val sdkRoot = root.resolve(".suitecloud-sdk")
            val cliDirectory = Files.createDirectories(sdkRoot.resolve("cli"))
            Files.createFile(cliDirectory.resolve("cli-2.9.99.jar"))
            val newestJar = Files.createFile(cliDirectory.resolve("cli-2.10.0.jar"))
            Files.createFile(cliDirectory.resolve("cli-latest.jar"))
            val javaExecutable = createExecutable(root.resolve("jdk/bin/java"))
            val resolver = SuiteCloudCliCommandResolver(
                sdkRoot = sdkRoot,
                launcherSearchDirectories = emptyList(),
                javaExecutables = listOf(javaExecutable),
                osName = "Mac OS X"
            )

            val command = resolver.resolve()

            assertEquals(javaExecutable, command.executable)
            assertEquals(
                listOf("-jar", newestJar.toString(), "manageauth", "-list"),
                command.arguments
            )
        }
    }

    @Test
    fun `numeric jar ordering handles components longer than padded integers`() {
        withTempDirectory { root ->
            val sdkRoot = root.resolve(".suitecloud-sdk")
            val cliDirectory = Files.createDirectories(sdkRoot.resolve("cli"))
            Files.createFile(cliDirectory.resolve("cli-99999999.9.jar"))
            val newestJar = Files.createFile(cliDirectory.resolve("cli-100000000.0.jar"))
            val javaExecutable = createExecutable(root.resolve("jdk/bin/java"))
            val resolver = SuiteCloudCliCommandResolver(
                sdkRoot = sdkRoot,
                launcherSearchDirectories = emptyList(),
                javaExecutables = listOf(javaExecutable),
                osName = "Mac OS X"
            )

            assertEquals(
                newestJar.toString(),
                resolver.resolve().arguments[1]
            )
        }
    }

    @Test
    fun `reports CLI unavailable when neither launcher nor SDK exists`() {
        withTempDirectory { root ->
            val resolver = SuiteCloudCliCommandResolver(
                sdkRoot = root.resolve("missing-sdk"),
                launcherSearchDirectories = emptyList(),
                javaExecutables = emptyList(),
                osName = "Mac OS X"
            )

            assertFailsWith<SuiteCloudCliUnavailableException> { resolver.resolve() }
        }
    }

    @Test
    fun `reports CLI unavailable when SDK jar has no Java executable`() {
        withTempDirectory { root ->
            val sdkRoot = root.resolve(".suitecloud-sdk")
            val cliDirectory = Files.createDirectories(sdkRoot.resolve("cli"))
            Files.createFile(cliDirectory.resolve("cli-1.0.0.jar"))
            val resolver = SuiteCloudCliCommandResolver(
                sdkRoot = sdkRoot,
                launcherSearchDirectories = emptyList(),
                javaExecutables = listOf(root.resolve("missing-java")),
                osName = "Mac OS X"
            )

            assertFailsWith<SuiteCloudCliUnavailableException> { resolver.resolve() }
        }
    }

    private fun loaderReturning(result: SdfCliExecutionResult): SuiteCloudAuthListLoader {
        return SuiteCloudAuthListLoader(SdfCliExecutor { result })
    }

    private fun assertSafeUnavailable(
        result: SdfAuthListLoadResult,
        vararg secrets: String
    ): SdfAuthListLoadResult.Unavailable {
        val unavailable = assertIs<SdfAuthListLoadResult.Unavailable>(result)
        assertTrue(unavailable.message.isNotBlank())
        secrets.forEach { secret ->
            assertFalse(
                unavailable.message.contains(secret),
                "The user-facing error must not contain captured CLI output"
            )
        }
        return unavailable
    }

    private fun createExecutable(path: Path): Path {
        Files.createDirectories(path.parent)
        Files.createFile(path)
        assertTrue(path.toFile().setExecutable(true), "Test executable must be executable")
        return path
    }

    private inline fun withTempDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("suitecloud-cli-test-")
        try {
            block(directory)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
