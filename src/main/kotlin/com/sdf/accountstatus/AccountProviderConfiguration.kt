package com.sdf.accountstatus

import java.nio.file.Files
import java.nio.file.Path

internal enum class AccountProviderKind(val displayName: String) {
    NODE_CLI("Node.js CLI"),
    LEGACY_JAVA("Java CLI (legacy)");

    override fun toString(): String = displayName
}

internal data class AccountProviderConfiguration(
    val provider: AccountProviderKind = AccountProviderKind.NODE_CLI,
    val nodeExecutable: String = "",
    val suiteCloudLauncher: String = ""
) {
    fun normalized(): AccountProviderConfiguration = copy(
        nodeExecutable = nodeExecutable.trim(),
        suiteCloudLauncher = suiteCloudLauncher.trim()
    )

    fun validationError(): ProviderConfigurationError? {
        // Retain inactive Node paths so an explicit switch to Java does not require a Node install.
        if (provider == AccountProviderKind.LEGACY_JAVA) return null
        validatePath(nodeExecutable, ProviderPathField.NODE_EXECUTABLE)?.let { return it }
        return validatePath(suiteCloudLauncher, ProviderPathField.SUITECLOUD_LAUNCHER)
    }

    private fun validatePath(value: String, field: ProviderPathField): ProviderConfigurationError? {
        if (value.isBlank()) return null
        val path = runCatching { Path.of(value.trim()) }.getOrNull()
        if (path == null || !path.isAbsolute) {
            return ProviderConfigurationError(field, "Enter an absolute path or leave this field blank for automatic detection.")
        }
        val regularFile = runCatching { Files.isRegularFile(path) && Files.isReadable(path) }.getOrDefault(false)
        if (!regularFile) return ProviderConfigurationError(field, "Select an existing, readable file.")
        return when (field) {
            ProviderPathField.NODE_EXECUTABLE ->
                if (Files.isExecutable(path)) null else ProviderConfigurationError(field, "Select an executable Node.js file.")
            ProviderPathField.SUITECLOUD_LAUNCHER ->
                if (runCatching { path.toRealPath().fileName.toString().endsWith(".js") }.getOrDefault(false)) null
                else ProviderConfigurationError(field, "Select the SuiteCloud JavaScript entrypoint (suitecloud.js).")
        }
    }
}

internal enum class ProviderPathField { NODE_EXECUTABLE, SUITECLOUD_LAUNCHER }

internal data class ProviderConfigurationError(val field: ProviderPathField, val message: String)
