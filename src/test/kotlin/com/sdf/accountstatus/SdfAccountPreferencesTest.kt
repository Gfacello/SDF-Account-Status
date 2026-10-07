package com.sdf.accountstatus

import com.intellij.util.xmlb.XmlSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SdfAccountPreferencesTest {
    @Test
    fun `preferences survive actual platform XML serialization and a new service instance`() {
        val original = SdfAccountPreferences()
        original.displayStyle = StatusDisplayStyle.ACCOUNT_DETAILS
        original.providerConfiguration = AccountProviderConfiguration(
            AccountProviderKind.LEGACY_JAVA, "/custom runtime/node", "/custom cli/suitecloud.js"
        )
        original.saveAccountUrl("1234567_SB1", "https://1234567-sb1.app.netsuite.com/")
        original.saveAccountUrl("1234567", "https://1234567.app.netsuite.com/")
        val xml = XmlSerializer.serialize(original.getState())
        val restored = SdfAccountPreferences()
        restored.loadState(XmlSerializer.deserialize(xml, SdfAccountPreferences.PreferencesState::class.java))
        assertEquals(StatusDisplayStyle.ACCOUNT_DETAILS, restored.displayStyle)
        assertEquals(original.providerConfiguration, restored.providerConfiguration)
        assertEquals("https://1234567-sb1.app.netsuite.com/", restored.accountUrl("1234567_sb1"))
        assertEquals("https://1234567.app.netsuite.com/", restored.accountUrl("1234567"))
        assertNull(restored.accountUrl("1234567_SB2"))
    }

    @Test
    fun `unknown stored display style uses compact default`() {
        val preferences = SdfAccountPreferences()
        preferences.loadState(SdfAccountPreferences.PreferencesState(statusDisplayStyle = "REMOVED_STYLE"))
        assertEquals(StatusDisplayStyle.AUTHENTICATION_ID, preferences.displayStyle)
    }

    @Test
    fun `old state and unknown provider select Node without inferring a legacy fallback`() {
        val preferences = SdfAccountPreferences()
        assertEquals(AccountProviderConfiguration(), preferences.providerConfiguration)
        val oldState = org.jdom.Element("PreferencesState")
        preferences.loadState(XmlSerializer.deserialize(oldState, SdfAccountPreferences.PreferencesState::class.java))
        assertEquals(AccountProviderConfiguration(), preferences.providerConfiguration)
        preferences.loadState(SdfAccountPreferences.PreferencesState(accountProvider = "UNKNOWN_PROVIDER"))
        assertEquals(AccountProviderKind.NODE_CLI, preferences.providerConfiguration.provider)
    }

    @Test
    fun `state snapshots cannot mutate saved provider or URL preferences`() {
        val preferences = SdfAccountPreferences()
        preferences.saveAccountUrl("1234567", "https://1234567.app.netsuite.com/")
        val snapshot = preferences.getState()
        snapshot.accountProvider = AccountProviderKind.LEGACY_JAVA.name
        snapshot.accountUrls.clear()
        assertEquals(AccountProviderKind.NODE_CLI, preferences.providerConfiguration.provider)
        assertEquals("https://1234567.app.netsuite.com/", preferences.accountUrl("1234567"))
        preferences.loadState(snapshot)
        snapshot.nodeExecutable = "/unexpected/node"
        snapshot.accountUrls["1234567"] = "https://unexpected.app.netsuite.com/"
        assertEquals("", preferences.providerConfiguration.nodeExecutable)
        assertNull(preferences.accountUrl("1234567"))
    }
}
