package com.sdf.accountstatus.core

import com.sdf.accountstatus.StatusDisplayStyle
import com.sdf.accountstatus.domain.AccountWidgetState
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

    @Test
    fun `detailed label distinguishes production numbered sandboxes and release preview`() {
        val examples = listOf(
            "123" to "PROD", "123_SB1" to "SB1", "123_sb12" to "SB12", "123_RP" to "RP", "unrecognized" to "Unverified"
        )
        examples.forEach { (accountId, marker) ->
            val state = detailed("$accountId: Acme [Developer]")
            assertEquals("Acme · $marker · Developer", state.text)
            assertTrue(state.accessibleDescription.contains("Account ID: $accountId"))
        }
        assertEquals(WidgetTone.OK, detailed("123_SB1: Acme [Developer]").tone)
        assertEquals(WidgetTone.WARNING, detailed("123_RP: Acme [Developer]").tone)
        assertEquals(WidgetTone.WARNING, detailed("unknown: Acme [Developer]").tone)
        assertTrue(detailed("123: Acme [Developer]").showCriticalIcon)
    }

    @Test
    fun `compact style remains default and both styles expose all metadata`() {
        val authentication = SdfAuthentication("current", "123_SB4: Example Company [Account Administrator]")
        val compact = SdfAccountStatusPresentation.present(ProjectJsonSnapshot.configured("current"), authentication)
        val rich = SdfAccountStatusPresentation.present(
            ProjectJsonSnapshot.configured("current"), authentication, StatusDisplayStyle.ACCOUNT_DETAILS
        )
        assertEquals("current", compact.text)
        assertEquals(compact.tooltip, rich.tooltip)
        assertEquals(compact.accessibleDescription, rich.accessibleDescription)
        listOf("Example Company", "123_SB4", "current", "Sandbox (SB4)", "Account Administrator").forEach {
            assertTrue(compact.tooltip.contains(it))
            assertTrue(compact.accessibleDescription.contains(it))
        }
        assertFalse(compact.accessibleDescription.contains("<html>"))
    }

    @Test
    fun `role changes are resolved by authentication ID even on the same account`() {
        val developer = SdfAuthentication("developer", "123_SB1: Acme [Developer]")
        val administrator = SdfAuthentication("administrator", "123_SB1: Acme [Administrator]")
        val snapshot = ProjectJsonSnapshot.configured("developer")
        val stale = SdfAccountStatusPresentation.present(snapshot, administrator, StatusDisplayStyle.ACCOUNT_DETAILS)
        assertEquals("developer", stale.text)
        assertEquals(WidgetTone.WARNING, stale.tone)
        assertFalse(stale.tooltip.contains("Administrator"))
        val correct = SdfAccountStatusPresentation.present(snapshot, developer, StatusDisplayStyle.ACCOUNT_DETAILS)
        assertEquals("Acme · SB1 · Developer", correct.text)
        val switched = SdfAccountStatusPresentation.present(
            ProjectJsonSnapshot.configured("administrator"), administrator, StatusDisplayStyle.ACCOUNT_DETAILS
        )
        assertEquals("Acme · SB1 · Administrator", switched.text)
    }

    @Test
    fun `long customer and role are abbreviated while environment and full descriptions remain`() {
        val company = "A very long customer company name across multiple regions"
        val role = "Administrator for multiple integration projects"
        val state = detailed("123_SB42: $company [$role]")
        val parts = state.text.split(" · ")
        assertEquals(3, parts.size)
        assertEquals("SB42", parts[1])
        assertTrue(parts[0].endsWith("…"))
        assertTrue(parts[2].endsWith("…"))
        assertTrue(state.text.codePointCount(0, state.text.length) <= 66)
        assertTrue(state.tooltip.contains(company))
        assertTrue(state.tooltip.contains(role))
        assertTrue(state.accessibleDescription.contains(company))
        assertTrue(state.accessibleDescription.contains(role))
    }

    @Test
    fun `abbreviation preserves supplementary Unicode characters`() {
        val company = "🚀".repeat(40)
        val state = detailed("123_SB1: $company [Developer]")
        assertEquals("🚀".repeat(31) + "… · SB1 · Developer", state.text)
        assertTrue(state.accessibleDescription.contains(company))
    }

    @Test
    fun `account metadata is escaped in HTML tooltip and literal in accessible description`() {
        val state = detailed("123_SB1: Acme <West> & \"Partners\" [Developer & Support]")
        assertTrue(state.tooltip.contains("Acme &lt;West&gt; &amp; &quot;Partners&quot;"))
        assertTrue(state.tooltip.contains("Developer &amp; Support"))
        assertFalse(state.tooltip.contains("<West>"))
        assertTrue(state.accessibleDescription.contains("Acme <West> & \"Partners\""))
        assertTrue(state.accessibleDescription.contains("Developer & Support"))
    }

    @Test
    fun `missing or malformed metadata falls back to auth ID without inferred environment`() {
        val snapshot = ProjectJsonSnapshot.configured("production-admin")
        listOf(null, SdfAuthentication("production-admin", "unrecognized metadata")).forEach { authentication ->
            val state = SdfAccountStatusPresentation.present(snapshot, authentication, StatusDisplayStyle.ACCOUNT_DETAILS)
            assertEquals("production-admin", state.text)
            assertEquals(WidgetTone.WARNING, state.tone)
            assertFalse(state.showCriticalIcon)
            assertTrue(state.tooltip.contains("Unverified"))
            assertTrue(state.accessibleDescription.contains("Refresh SuiteCloud accounts"))
        }
    }

    @Test
    fun `project errors keep their actionable presentation in either display style`() {
        val problem = AccountWidgetState("project.json invalid", "Fix project.json before choosing an account", WidgetTone.ERROR)
        val snapshot = ProjectJsonSnapshot(null, problem, 1)
        StatusDisplayStyle.entries.forEach { style ->
            assertEquals(problem, SdfAccountStatusPresentation.present(snapshot, SdfAuthentication("old", "123: Acme [Administrator]"), style))
        }
    }

    private fun detailed(details: String) = SdfAccountStatusPresentation.present(
        ProjectJsonSnapshot.configured("current"), SdfAuthentication("current", details), StatusDisplayStyle.ACCOUNT_DETAILS
    )
}
