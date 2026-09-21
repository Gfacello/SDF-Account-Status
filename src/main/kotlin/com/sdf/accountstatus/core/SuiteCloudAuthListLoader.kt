package com.sdf.accountstatus.core

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.progress.ProcessCanceledException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile

internal class SdfCliExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false
) {
    override fun toString(): String =
        "SdfCliExecutionResult(exitCode=$exitCode, timedOut=$timedOut, stdout=<redacted>, stderr=<redacted>)"
}

internal fun interface SdfCliExecutor {
    fun execute(): SdfCliExecutionResult
}

internal sealed interface SdfAuthListLoadResult {
    data class Available(val accounts: List<SdfAuthentication>) : SdfAuthListLoadResult
    data class Unavailable(val message: String) : SdfAuthListLoadResult
}

internal class SuiteCloudAuthListLoader(
    private val executor: SdfCliExecutor = InstalledSuiteCloudCliExecutor()
) {
    fun load(): SdfAuthListLoadResult {
        val execution = try {
            executor.execute()
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (_: SuiteCloudCliUnavailableException) {
            return SdfAuthListLoadResult.Unavailable(
                "SuiteCloud CLI is unavailable. Verify that the SuiteCloud SDK is installed."
            )
        } catch (_: Exception) {
            return SdfAuthListLoadResult.Unavailable(
                "Unable to read SuiteCloud authentication IDs. Check the SuiteCloud CLI setup."
            )
        }

        if (execution.timedOut) {
            return SdfAuthListLoadResult.Unavailable(
                "SuiteCloud CLI did not finish while reading authentication IDs. Try again."
            )
        }

        // The Java CLI can report failures while still returning exit code zero, so stderr is significant.
        if (execution.exitCode != 0 || execution.stderr.isNotBlank()) {
            return SdfAuthListLoadResult.Unavailable(
                "Unable to read SuiteCloud authentication IDs. Check the SuiteCloud CLI setup."
            )
        }

        return when (val parsed = SdfAuthListParser.parse(execution.stdout)) {
            is SdfAuthListParseResult.Success -> SdfAuthListLoadResult.Available(parsed.accounts)
            SdfAuthListParseResult.Empty -> SdfAuthListLoadResult.Unavailable(
                "No SuiteCloud authentication IDs are configured."
            )
            SdfAuthListParseResult.Malformed -> SdfAuthListLoadResult.Unavailable(
                "SuiteCloud CLI returned an unrecognized account list."
            )
        }
    }
}

internal class SuiteCloudCliUnavailableException : Exception()

internal data class SdfCliCommand(
    val executable: Path,
    val arguments: List<String>
)

/**
 * Executes the read-only `sdfcli manageauth -list` operation. It never reads SDK credential files
 * directly and deliberately keeps captured stdout/stderr in memory without logging either stream.
 */
internal class InstalledSuiteCloudCliExecutor(
    private val commandResolver: SuiteCloudCliCommandResolver = SuiteCloudCliCommandResolver(),
    private val timeoutMillis: Int = 30_000
) : SdfCliExecutor {
    override fun execute(): SdfCliExecutionResult {
        val command = commandResolver.resolve()
        val output = CapturingProcessHandler(
            GeneralCommandLine(command.executable.toString())
                .withParameters(command.arguments)
                .withCharset(StandardCharsets.UTF_8)
        ).runProcess(timeoutMillis)

        return SdfCliExecutionResult(
            exitCode = output.exitCode,
            stdout = output.stdout,
            stderr = output.stderr,
            timedOut = output.isTimeout || output.isCancelled
        )
    }
}

/**
 * Resolves the Java SuiteCloud CLI inside the configured SDK root without executing a shell or
 * inspecting credentials. An SDK-contained `sdfcli` launcher wins when available; the SDK CLI JAR
 * remains a compatibility fallback.
 */
internal class SuiteCloudCliCommandResolver(
    private val sdkRoot: Path = defaultSdkRoot(),
    private val launcherSearchDirectories: List<Path> = defaultLauncherSearchDirectories(sdkRoot),
    private val javaExecutables: List<Path> = defaultJavaExecutables(),
    private val osName: String = System.getProperty("os.name").orEmpty()
) {
    fun resolve(): SdfCliCommand {
        findInstalledLauncher()?.let { launcher ->
            return SdfCliCommand(launcher, MANAGE_AUTH_ARGUMENTS)
        }

        if (!Files.isDirectory(sdkRoot)) throw SuiteCloudCliUnavailableException()

        val cliJar = findLatestCliJar() ?: throw SuiteCloudCliUnavailableException()
        val javaExecutable = javaExecutables.firstOrNull(::isExecutableRegularFile)
            ?: throw SuiteCloudCliUnavailableException()

        // The SDK JAR is the installed Java CLI behind the `sdfcli` launcher.
        return SdfCliCommand(
            executable = javaExecutable,
            arguments = listOf("-jar", cliJar.toString()) + MANAGE_AUTH_ARGUMENTS
        )
    }

    private fun findInstalledLauncher(): Path? {
        val launcherNames = if (isWindows()) {
            listOf("sdfcli.exe", "sdfcli.cmd", "sdfcli.bat", "sdfcli")
        } else {
            listOf("sdfcli")
        }

        return launcherSearchDirectories.asSequence()
            // Never resolve a launcher relative to an SDF project or the IDE working directory.
            .filter(Path::isAbsolute)
            .flatMap { directory -> launcherNames.asSequence().map(directory::resolve) }
            .firstOrNull(::isExecutableRegularFile)
    }

    private fun findLatestCliJar(): Path? {
        val cliDirectory = sdkRoot.resolve("cli")
        if (!Files.isDirectory(cliDirectory)) return null

        return Files.list(cliDirectory).use { paths ->
            paths.filter { path ->
                path.isRegularFile() && CLI_JAR.matches(path.fileName.toString())
            }.max(Comparator { left, right -> compareCliJarVersions(left, right) }).orElse(null)
        }
    }

    private fun compareCliJarVersions(left: Path, right: Path): Int {
        val leftParts = versionParts(left)
        val rightParts = versionParts(right)
        val componentCount = maxOf(leftParts.size, rightParts.size)

        repeat(componentCount) { index ->
            val comparison = compareNumericComponents(
                leftParts.getOrElse(index) { "0" },
                rightParts.getOrElse(index) { "0" }
            )
            if (comparison != 0) return comparison
        }

        return left.fileName.toString().compareTo(right.fileName.toString())
    }

    private fun versionParts(path: Path): List<String> = CLI_JAR
        .matchEntire(path.fileName.toString())
        ?.groupValues
        ?.get(1)
        ?.split('.')
        .orEmpty()

    private fun compareNumericComponents(left: String, right: String): Int {
        val normalizedLeft = left.trimStart('0').ifEmpty { "0" }
        val normalizedRight = right.trimStart('0').ifEmpty { "0" }
        return normalizedLeft.length.compareTo(normalizedRight.length)
            .takeIf { it != 0 }
            ?: normalizedLeft.compareTo(normalizedRight)
    }

    private fun isExecutableRegularFile(path: Path): Boolean = try {
        path.isRegularFile() && path.isExecutable()
    } catch (_: SecurityException) {
        false
    }

    private fun isWindows(): Boolean = osName.lowercase(Locale.ROOT).startsWith("windows")

    private companion object {
        val CLI_JAR = Regex("cli-(\\d+(?:\\.\\d+)*)\\.jar")
        val MANAGE_AUTH_ARGUMENTS = listOf("manageauth", "-list")

        fun defaultSdkRoot(): Path = Path.of(
            System.getProperty("user.home"),
            ".suitecloud-sdk"
        )

        fun defaultLauncherSearchDirectories(sdkRoot: Path): List<Path> = listOf(
            sdkRoot.resolve("bin"),
            sdkRoot.resolve("cli"),
            sdkRoot
        )

        fun defaultJavaExecutables(): List<Path> {
            val executableName = if (
                System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).startsWith("windows")
            ) {
                "java.exe"
            } else {
                "java"
            }
            val javaHomes = sequenceOf(
                System.getenv("JAVA_HOME"),
                System.getProperty("java.home")
            ).filterNotNull()
                .filter(String::isNotBlank)
                .mapNotNull { value -> runCatching { Path.of(value) }.getOrNull() }
                .filter(Path::isAbsolute)

            return javaHomes.map { it.resolve("bin").resolve(executableName) }
                .distinct()
                .toList()
        }
    }
}
