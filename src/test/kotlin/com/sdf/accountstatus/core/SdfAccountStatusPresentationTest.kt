package com.sdf.accountstatus.core

import com.sdf.accountstatus.domain.WidgetTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SdfAccountStatusPresentationTest {
    @Test
    fun `status derives metadata only from the exact authentication ID`() {
        val state = SdfAccountStatusPresentation.present(
            ProjectJsonSnapshot.configured("sandbox"), SdfAuthentication("prod", "123: Acme [Administrator]")
        )
        assertEquals("sandbox", state.text)
        assertEquals(WidgetTone.WARNING, state.tone)
        assertTrue(state.tooltip.contains("Unverified"))
        assertFalse(state.showCriticalIcon)
    }

    @Test
    fun `same account with different auth role cannot supply metadata`() {
        val state = SdfAccountStatusPresentation.present(
            ProjectJsonSnapshot.configured("developer"), SdfAuthentication("admin", "123: Acme [Administrator]")
        )
        assertEquals(WidgetTone.WARNING, state.tone)
    }

    @Test
    fun `snapshot and just persisted account share identical presentation`() {
        val authentication = SdfAuthentication("prod", "123: Acme [Administrator]")
        val initial = SdfAccountStatusPresentation.present(ProjectJsonSnapshot.configured("prod", 42), authentication)
        val saved = SdfAccountStatusPresentation.present(ProjectJsonSnapshot.configured("prod"), authentication)
        assertEquals(initial, saved)
        assertTrue(saved.showCriticalIcon)
        assertEquals(WidgetTone.ERROR, saved.tone)
    }
}
