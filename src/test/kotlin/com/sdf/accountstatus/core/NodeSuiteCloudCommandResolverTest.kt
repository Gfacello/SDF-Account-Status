package com.sdf.accountstatus.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NodeSuiteCloudCommandResolverTest {
    @Test
    fun `uses explicit paths with spaces without invoking a wrapper`() = temporary { root ->
        val node = executable(root.resolve("node runtime/node"))
        val launcher = file(root.resolve("custom cli/suitecloud.js"))
        val result = NodeSuiteCloudCommandResolver(launcher, node, emptyList(), root, appData = null).resolve()
        assertEquals(node.toRealPath(), result.node)
        assertEquals(launcher.toRealPath(), result.launcher)
        assertEquals(listOf(node.toRealPath().toString(), launcher.toRealPath().toString(), "account:manageauth", "--list"), result.arguments(listOf("--list")))
    }

    @Test
    fun `discovers npm layout from absolute PATH directories`() = temporary { root ->
        val node = executable(root.resolve("bin/node"))
        val launcher = file(root.resolve("lib/node_modules/@oracle/suitecloud-cli/src/suitecloud.js"))
        val result = NodeSuiteCloudCommandResolver(searchDirectories = listOf(root.resolve("bin")), home = root, appData = null).resolve()
        assertEquals(node.toRealPath(), result.node)
        assertEquals(launcher.toRealPath(), result.launcher)
    }

    @Test
    fun `resolves a Unix npm symlink to JavaScript`() = temporary { root ->
        val node = executable(root.resolve("bin/node"))
        val launcher = file(root.resolve("installed package/src/suitecloud.js"))
        val link = root.resolve("bin/suitecloud")
        Files.createSymbolicLink(link, launcher)
        assertEquals(launcher.toRealPath(), NodeSuiteCloudCommandResolver(
            searchDirectories = listOf(node.parent), home = root, appData = null
        ).resolve().launcher)
    }

    @Test
    fun `Windows npm wrapper resolves to JS and node exe`() = temporary { root ->
        val node = executable(root.resolve("node bin/node.exe"))
        val wrapper = file(root.resolve("npm/suitecloud.cmd"))
        val launcher = file(root.resolve("npm/node_modules/@oracle/suitecloud-cli/src/suitecloud.js"))
        val result = NodeSuiteCloudCommandResolver(
            explicitLauncher = wrapper, searchDirectories = listOf(node.parent), home = root,
            osName = "Windows 11", appData = null
        ).resolve()
        assertEquals(node.toRealPath(), result.node)
        assertEquals(launcher.toRealPath(), result.launcher)
    }

    @Test
    fun `invalid explicit paths do not silently use another installation`() = temporary { root ->
        val node = executable(root.resolve("bin/node"))
        file(root.resolve("bin/suitecloud.js"))
        assertFailsWith<SuiteCloudCliUnavailableException> {
            NodeSuiteCloudCommandResolver(root.resolve("missing.js"), node, listOf(node.parent), root, appData = null).resolve()
        }
        assertFailsWith<SuiteCloudCliUnavailableException> {
            NodeSuiteCloudCommandResolver(root.resolve("bin/suitecloud.js"), root.resolve("missing-node"), listOf(node.parent), root, appData = null).resolve()
        }
    }

    @Test
    fun `rejects relative paths and shell launchers without known JS sibling`() = temporary { root ->
        val node = executable(root.resolve("node"))
        val shell = executable(root.resolve("suitecloud"))
        assertFailsWith<SuiteCloudCliUnavailableException> {
            NodeSuiteCloudCommandResolver(shell, node, emptyList(), root, appData = null).resolve()
        }
        assertFailsWith<SuiteCloudCliUnavailableException> {
            NodeSuiteCloudCommandResolver(searchDirectories = listOf(Path.of(".")), home = root, appData = null).resolve()
        }
        assertFailsWith<SuiteCloudCliUnavailableException> {
            NodeSuiteCloudCommandResolver(Path.of("suitecloud.js"), node, emptyList(), root, appData = null).resolve()
        }
    }

    private fun file(path: Path): Path {
        Files.createDirectories(path.parent)
        return Files.writeString(path, "fixture")
    }

    private fun executable(path: Path): Path = file(path).also { it.toFile().setExecutable(true) }

    private fun temporary(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("sdf node paths ")
        try { block(root) } finally { root.toFile().deleteRecursively() }
    }
}
