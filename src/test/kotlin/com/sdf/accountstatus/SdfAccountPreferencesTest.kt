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
        original.saveAccountUrl("1234567_SB1", "https://1234567-sb1.app.netsuite.com/")
        original.saveAccountUrl("1234567", "https://1234567.app.netsuite.com/")
        val xml = XmlSerializer.serialize(original.getState())
        val restored = SdfAccountPreferences()
        restored.loadState(XmlSerializer.deserialize(xml, SdfAccountPreferences.PreferencesState::class.java))
        assertEquals(StatusDisplayStyle.ACCOUNT_DETAILS, restored.displayStyle)
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
}
