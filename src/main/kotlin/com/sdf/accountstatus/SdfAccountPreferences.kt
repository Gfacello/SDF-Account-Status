package com.sdf.accountstatus

import com.intellij.openapi.components.PersistentStateComponent
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
        @JvmField var accountUrls: MutableMap<String, String> = linkedMapOf()
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
        @Synchronized set(value) { preferences.statusDisplayStyle = value.name }

    @Synchronized
    override fun accountUrl(accountId: String): String? = preferences.accountUrls[accountKey(accountId)]

    @Synchronized
    override fun saveAccountUrl(accountId: String, url: String) {
        preferences.accountUrls[accountKey(accountId)] = url
    }

    private fun accountKey(accountId: String) = accountId.uppercase(Locale.ROOT)
}
