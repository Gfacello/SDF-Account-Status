package com.sdf.accountstatus

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AccountProviderConfigurationTest {
    @TempDir lateinit var root: Path

    @Test
    fun `blank optional paths allow automatic resolution and surrounding whitespace is normalized`() {
        assertNull(AccountProviderConfiguration(nodeExecutable = "  ", suiteCloudLauncher = "\t").validationError())
        assertEquals(AccountProviderConfiguration(), AccountProviderConfiguration(nodeExecutable = "  ").normalized())
    }

    @Test
    fun `existing executable and readable JS paths containing spaces are accepted`() {
        val node = Files.writeString(root.resolve("node executable"), "fixture").also { it.toFile().setExecutable(true) }
        val launcher = Files.writeString(root.resolve("suitecloud launcher.js"), "fixture")
        val configuration = AccountProviderConfiguration(nodeExecutable = " $node ", suiteCloudLauncher = launcher.toString())
        assertNull(configuration.validationError())
        assertEquals(node.toString(), configuration.normalized().nodeExecutable)
    }

    @Test
    fun `relative malformed missing and directory paths identify the responsible field`() {
        for (path in listOf("node", "relative/node", "bad\u0000path", root.resolve("missing-node").toString(), root.toString())) {
            assertEquals(ProviderPathField.NODE_EXECUTABLE, AccountProviderConfiguration(nodeExecutable = path).validationError()?.field)
        }
        for (path in listOf("suitecloud.js", "bad\u0000path", root.resolve("missing.js").toString(), root.toString())) {
            assertEquals(ProviderPathField.SUITECLOUD_LAUNCHER, AccountProviderConfiguration(suiteCloudLauncher = path).validationError()?.field)
        }
    }

    @Test
    fun `shell wrappers cannot be configured as the JavaScript entrypoint`() {
        val wrapper = Files.writeString(root.resolve("suitecloud.cmd"), "fixture")
        val error = AccountProviderConfiguration(suiteCloudLauncher = wrapper.toString()).validationError()
        assertNotNull(error)
        assertEquals(ProviderPathField.SUITECLOUD_LAUNCHER, error.field)
        assertEquals("Select the SuiteCloud JavaScript entrypoint (suitecloud.js).", error.message)
    }

    @Test
    fun `a symlink to a JS entrypoint is accepted without running it`() {
        val launcher = Files.writeString(root.resolve("suitecloud.js"), "throw new Error('do not execute')")
        val link = Files.createSymbolicLink(root.resolve("suitecloud-link"), launcher)
        assertNull(AccountProviderConfiguration(suiteCloudLauncher = link.toString()).validationError())
    }

    @Test
    fun `explicit legacy selection retains but does not validate inactive Node paths`() {
        val configuration = AccountProviderConfiguration(AccountProviderKind.LEGACY_JAVA, "unavailable-node", "missing.js")
        assertNull(configuration.validationError())
        assertNotNull(configuration.copy(provider = AccountProviderKind.NODE_CLI).validationError())
    }
}
