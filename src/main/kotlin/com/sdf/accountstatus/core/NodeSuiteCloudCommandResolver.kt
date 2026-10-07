package com.sdf.accountstatus.core

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

internal data class NodeSuiteCloudCommand(val node: Path, val launcher: Path) {
    fun arguments(options: List<String>): List<String> =
        listOf(node.toString(), launcher.toString(), "account:manageauth") + options
}

/** Resolves JavaScript launchers; never starts a shell, npm, npx, or Windows command wrapper. */
internal class NodeSuiteCloudCommandResolver(
    private val explicitLauncher: Path? = null,
    private val explicitNode: Path? = null,
    private val searchDirectories: List<Path> = defaultSearchDirectories(),
    private val home: Path = Path.of(System.getProperty("user.home")),
    private val osName: String = System.getProperty("os.name").orEmpty(),
    private val appData: Path? = absolutePath(System.getenv("APPDATA"))
) {
    fun resolve(): NodeSuiteCloudCommand {
        val directories = searchDirectories.filter(Path::isAbsolute).distinct()
        val launcher = if (explicitLauncher != null) {
            javascriptLauncher(explicitLauncher) ?: throw SuiteCloudCliUnavailableException()
        } else {
            val candidates = directories.flatMap { directory ->
                listOf(
                    directory.resolve("suitecloud"),
                    directory.resolve("suitecloud.cmd"),
                    directory.resolve("suitecloud.js"),
                    directory.resolve("node_modules/$PACKAGE_LAUNCHER"),
                    directory.resolve("../lib/node_modules/$PACKAGE_LAUNCHER")
                )
            } + listOfNotNull(
                home.takeIf(Path::isAbsolute)?.resolve(".npm-global/lib/node_modules/$PACKAGE_LAUNCHER"),
                home.takeIf(Path::isAbsolute)?.resolve(".local/lib/node_modules/$PACKAGE_LAUNCHER"),
                appData?.resolve("npm/node_modules/$PACKAGE_LAUNCHER")
            )
            candidates.firstNotNullOfOrNull(::javascriptLauncher) ?: throw SuiteCloudCliUnavailableException()
        }
        val nodeName = if (osName.lowercase(Locale.ROOT).startsWith("windows")) "node.exe" else "node"
        val node = if (explicitNode != null) {
            explicitNode.takeIf(::executable) ?: throw SuiteCloudCliUnavailableException()
        } else {
            directories.map { it.resolve(nodeName) }.firstOrNull(::executable)
                ?: throw SuiteCloudCliUnavailableException()
        }
        return NodeSuiteCloudCommand(node.toRealPath(), launcher)
    }

    private fun javascriptLauncher(path: Path): Path? {
        if (!path.isAbsolute) return null
        val resolved = try {
            if (!Files.isRegularFile(path)) return null
            path.toRealPath()
        } catch (_: Exception) {
            return null
        }
        if (resolved.fileName.toString().endsWith(".js") && Files.isReadable(resolved)) return resolved
        // npm on Windows puts wrappers beside node_modules; inspect the known JS location only.
        if (path.fileName.toString() in listOf("suitecloud.cmd", "suitecloud.bat", "suitecloud")) {
            val script = path.parent.resolve("node_modules/$PACKAGE_LAUNCHER")
            if (Files.isRegularFile(script) && Files.isReadable(script)) return script.toRealPath()
        }
        return null
    }

    private fun executable(path: Path): Boolean =
        path.isAbsolute && Files.isRegularFile(path) && Files.isExecutable(path)

    companion object {
        private const val PACKAGE_LAUNCHER = "@oracle/suitecloud-cli/src/suitecloud.js"

        private fun absolutePath(value: String?): Path? = value?.takeIf(String::isNotBlank)?.let {
            runCatching { Path.of(it) }.getOrNull()?.takeIf(Path::isAbsolute)
        }

        private fun defaultSearchDirectories(): List<Path> =
            System.getenv("PATH").orEmpty().split(File.pathSeparator).mapNotNull(::absolutePath) +
                listOf("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin").map(Path::of)
    }
}
