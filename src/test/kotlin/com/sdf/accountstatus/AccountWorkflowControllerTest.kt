package com.sdf.accountstatus

import com.sdf.accountstatus.core.ProjectJsonUpdateResult
import com.sdf.accountstatus.core.SdfAuthentication
import com.sdf.accountstatus.domain.AccountEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountWorkflowControllerTest {
    @Test
    fun `production cancel writes nothing and restores captured picker state`() {
        val fixture = Fixture()
        val ui = fixture.ui(confirmed = false, query = "Acme administrator")
        fixture.controller.selectAccount(production, ui)
        assertEquals(1, ui.closed)
        assertEquals(0, ui.confirmations)
        fixture.deliver()
        assertEquals(1, ui.confirmations)
        assertEquals("Acme administrator", ui.restoredQuery)
        assertTrue(fixture.writes.isEmpty())
        assertTrue(fixture.saved.isEmpty())
    }

    @Test
    fun `production confirm persists once before publishing selected account`() {
        val fixture = Fixture()
        fixture.controller.selectAccount(production, fixture.ui(true))
        assertTrue(fixture.writes.isEmpty())
        fixture.deliver()
        assertEquals(listOf("prod"), fixture.writes)
        assertEquals(listOf("persist:prod", "publish:prod"), fixture.events)
    }

    @Test
    fun `current account closes picker without confirmation or write`() {
        val fixture = Fixture(current = "prod")
        val ui = fixture.ui(true)
        fixture.controller.selectAccount(production, ui)
        assertEquals(1, ui.closed)
        assertEquals(0, ui.confirmations)
        assertTrue(fixture.writes.isEmpty())
    }

    @Test
    fun `cached current row does not prevent switch after external account change`() {
        val fixture = Fixture(current = "different")
        fixture.controller.selectAccount(sandbox.copy(isCurrent = true), fixture.ui(true))
        assertEquals(listOf("sandbox"), fixture.writes)
    }

    @Test
    fun `failed persistence reports error without publishing account`() {
        val fixture = Fixture(result = ProjectJsonUpdateResult.Invalid("read-only"))
        val ui = fixture.ui(true)
        fixture.controller.selectAccount(sandbox, ui)
        assertEquals(listOf("read-only"), ui.errors)
        assertEquals(0, ui.closed)
        assertTrue(fixture.saved.isEmpty())
    }

    @Test
    fun `widget disposal before scheduled confirmation prevents all further UI and writes`() {
        val fixture = Fixture()
        val ui = fixture.ui(true)
        fixture.controller.selectAccount(production, ui)
        fixture.controller.dispose()
        fixture.deliver()
        assertEquals(0, ui.confirmations)
        assertTrue(fixture.writes.isEmpty())
        assertTrue(fixture.saved.isEmpty())
    }

    @Test
    fun `project closure while confirmation is open prevents persistence`() {
        val fixture = Fixture()
        val ui = fixture.ui(true).apply { duringConfirmation = { fixture.unavailable = true } }
        fixture.controller.selectAccount(production, ui)
        fixture.deliver()
        assertEquals(1, ui.confirmations)
        assertTrue(fixture.writes.isEmpty())
    }

    @Test
    fun `superseded pending selection cannot switch the account`() {
        val fixture = Fixture()
        val ui = fixture.ui(true)
        fixture.controller.selectAccount(production, ui)
        fixture.controller.selectAccount(sandbox, fixture.ui(true))
        fixture.deliver()
        assertEquals(0, ui.confirmations)
        assertEquals(listOf("sandbox"), fixture.writes)
    }

    private class Fixture(
        var current: String = "original",
        val result: ProjectJsonUpdateResult = ProjectJsonUpdateResult.Updated("{}")
    ) {
        var unavailable = false
        val writes = mutableListOf<String>()
        val saved = mutableListOf<String>()
        val events = mutableListOf<String>()
        val deferred = ArrayDeque<() -> Unit>()
        val controller = AccountWorkflowController(
            writer = { id -> writes.add(id); events.add("persist:$id"); result },
            currentAuthenticationId = { current },
            isUnavailable = { unavailable },
            later = { deferred.add(it) },
            onSaved = { saved.add(it.authenticationId); events.add("publish:${it.authenticationId}") }
        )
        fun deliver() { while (deferred.isNotEmpty()) deferred.removeFirst().invoke() }
        fun ui(confirmed: Boolean, query: String = "") = FakeUi(confirmed, query)
    }

    private class FakeUi(val confirmed: Boolean, val query: String) : AccountSelectionUi {
        var closed = 0
        var confirmations = 0
        var restoredQuery: String? = null
        var duringConfirmation: () -> Unit = {}
        val errors = mutableListOf<String>()
        override fun closePicker() { closed++ }
        override fun confirmProduction(account: AccountPickerAccount): Boolean {
            confirmations++
            duringConfirmation()
            return confirmed
        }
        override fun restorePicker() { restoredQuery = query }
        override fun showError(message: String) { errors.add(message) }
    }

    private companion object {
        val production = AccountPickerAccount(
            SdfAuthentication("prod", "123: Acme [Administrator]"), AccountEnvironment.PRODUCTION, false, false
        )
        val sandbox = AccountPickerAccount(
            SdfAuthentication("sandbox", "123_SB1: Acme [Administrator]"), AccountEnvironment.SANDBOX, false, false
        )
    }
}
