package com.sdf.accountstatus

/** Dialog cancellation and project disposal never persist or publish a provider change. */
internal class AccountProviderSettingsController(
    private val preferences: SdfAccountPreferences,
    private val prompt: (AccountProviderConfiguration) -> AccountProviderConfiguration?,
    private val showError: (ProviderConfigurationError) -> Unit,
    private val isUnavailable: () -> Boolean,
    private val onApplied: () -> Unit
) {
    fun configure() {
        if (isUnavailable()) return
        val selected = prompt(preferences.providerConfiguration)?.normalized() ?: return
        if (isUnavailable()) return
        val error = selected.validationError()
        if (error != null) {
            showError(error)
            return
        }
        if (selected == preferences.providerConfiguration) return
        preferences.providerConfiguration = selected
        onApplied()
    }
}
