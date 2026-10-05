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

    /** Safe for dialog validation on EDT: no filesystem access or symlink resolution. */
    fun syntaxError(): ProviderConfigurationError? {
        // Retain inactive Node paths so an explicit switch to Java does not require a Node install.
        if (provider == AccountProviderKind.LEGACY_JAVA) return null
        validateSyntax(nodeExecutable, ProviderPathField.NODE_EXECUTABLE)?.let { return it }
        return validateSyntax(suiteCloudLauncher, ProviderPathField.SUITECLOUD_LAUNCHER)
    }

    /** Run in the account-loading worker; mounted paths can block while probing the filesystem. */
    fun validationError(): ProviderConfigurationError? {
        syntaxError()?.let { return it }
        if (provider == AccountProviderKind.LEGACY_JAVA) return null
        validateFile(nodeExecutable, ProviderPathField.NODE_EXECUTABLE)?.let { return it }
        return validateFile(suiteCloudLauncher, ProviderPathField.SUITECLOUD_LAUNCHER)
    }

    private fun validateSyntax(value: String, field: ProviderPathField): ProviderConfigurationError? {
        if (value.isBlank()) return null
        val path = runCatching { Path.of(value.trim()) }.getOrNull()
        return if (path == null || !path.isAbsolute)
            ProviderConfigurationError(field, "Enter an absolute path or leave this field blank for automatic detection.")
        else null
    }

    private fun validateFile(value: String, field: ProviderPathField): ProviderConfigurationError? {
        if (value.isBlank()) return null
        val path = Path.of(value.trim())
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
