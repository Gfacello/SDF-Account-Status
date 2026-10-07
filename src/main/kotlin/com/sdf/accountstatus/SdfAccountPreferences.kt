package com.sdf.accountstatus

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.util.Locale

internal enum class StatusDisplayStyle { AUTHENTICATION_ID, ACCOUNT_DETAILS }

/** Application-local preferences: never stored in an SDF project or synchronized by Settings Sync. */
@Service(Service.Level.APP)
@State(name = "SdfAccountPreferences", storages = [Storage("sdfAccountStatus.xml", roamingType = RoamingType.DISABLED)])
internal class SdfAccountPreferences : PersistentStateComponent<SdfAccountPreferences.PreferencesState>, AccountUrlStore {
    data class PreferencesState(
        @JvmField var statusDisplayStyle: String = StatusDisplayStyle.AUTHENTICATION_ID.name,
        @JvmField var accountUrls: MutableMap<String, String> = linkedMapOf(),
        @JvmField var accountProvider: String = AccountProviderKind.NODE_CLI.name,
        @JvmField var nodeExecutable: String = "",
        @JvmField var suiteCloudLauncher: String = ""
    )

    private var preferences = PreferencesState()

    @Synchronized
    override fun getState(): PreferencesState = preferences.copy(accountUrls = LinkedHashMap(preferences.accountUrls))

    @Synchronized
    override fun loadState(state: PreferencesState) {
        preferences = state.copy(accountUrls = LinkedHashMap(state.accountUrls))
    }

    var displayStyle: StatusDisplayStyle
        @Synchronized get() = StatusDisplayStyle.entries.firstOrNull { it.name == preferences.statusDisplayStyle }
            ?: StatusDisplayStyle.AUTHENTICATION_ID
        set(value) {
            val changed = synchronized(this) {
                if (preferences.statusDisplayStyle == value.name) false else {
                    preferences.statusDisplayStyle = value.name
                    true
                }
            }
            if (changed) publish(AccountPreferenceChange.DISPLAY_STYLE)
        }

    /** One immutable snapshot prevents loads from mixing an old provider with newly saved paths. */
    var providerConfiguration: AccountProviderConfiguration
        @Synchronized get() = AccountProviderConfiguration(
            provider = AccountProviderKind.entries.firstOrNull { it.name == preferences.accountProvider }
                ?: AccountProviderKind.NODE_CLI,
            nodeExecutable = preferences.nodeExecutable,
            suiteCloudLauncher = preferences.suiteCloudLauncher
        )
        set(value) {
            val changed = synchronized(this) {
                if (providerConfiguration == value) false else {
                    preferences.accountProvider = value.provider.name
                    preferences.nodeExecutable = value.nodeExecutable
                    preferences.suiteCloudLauncher = value.suiteCloudLauncher
                    true
                }
            }
            if (changed) publish(AccountPreferenceChange.PROVIDER)
        }

    @Synchronized
    override fun accountUrl(accountId: String): String? = preferences.accountUrls[accountKey(accountId)]

    @Synchronized
    override fun saveAccountUrl(accountId: String, url: String) {
        preferences.accountUrls[accountKey(accountId)] = url
    }

    private fun accountKey(accountId: String) = accountId.uppercase(Locale.ROOT)

    private fun publish(change: AccountPreferenceChange) {
        val application = ApplicationManager.getApplication() ?: return
        if (!application.isDisposed) application.messageBus.syncPublisher(AccountPreferencesListener.TOPIC).changed(change)
    }
}
