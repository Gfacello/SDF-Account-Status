package com.sdf.accountstatus

import com.sdf.accountstatus.domain.AccountEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountSetupControllerTest {
    @Test
    fun `settings completion reloads and reopens without filters even when no account was added`() {
        val fixture = Fixture()
        fixture.start()
        assertEquals(listOf("close"), fixture.events)
        fixture.deliver()
        assertEquals(listOf("close", "settings", "reload", "reopen"), fixture.events)
        assertEquals(AccountPickerFilterState(), fixture.restored)
    }

    @Test
    fun `unavailable settings preserves filters and allows another attempt`() {
        val fixture = Fixture(opened = false)
        fixture.start()
        fixture.deliver()
        assertEquals(listOf("close", "settings", "reopen"), fixture.events)
        assertEquals(filters, fixture.restored)
        fixture.start()
        fixture.deliver()
        assertEquals(2, fixture.events.count { it == "settings" })
    }

    @Test
    fun `duplicate setup before deferred settings runs opens only one dialog`() {
        val fixture = Fixture()
        fixture.start()
        fixture.start()
        fixture.deliver()
        assertEquals(1, fixture.events.count { it == "settings" })
        assertEquals(1, fixture.events.count { it == "close" })
    }

    @Test
    fun `disposal before settings suppresses deferred work`() {
        val fixture = Fixture()
        fixture.start()
        fixture.unavailable = true
        fixture.deliver()
        assertEquals(listOf("close"), fixture.events)
    }

    @Test
    fun `disposal during settings prevents reload and popup resurrection`() {
        val fixture = Fixture()
        fixture.duringSettings = { fixture.unavailable = true }
        fixture.start()
        fixture.deliver()
        assertEquals(listOf("close", "settings"), fixture.events)
    }

    @Test
    fun `unexpected settings error restores picker with safe guidance`() {
        val fixture = Fixture()
        fixture.duringSettings = { error("private diagnostic must not be displayed") }
        fixture.start()
        fixture.deliver()
        assertEquals(filters, fixture.restored)
        assertEquals(1, fixture.errors.size)
        assertTrue(fixture.errors.single().contains("Settings | Tools | NetSuite"))
        assertTrue(fixture.errors.none { it.contains("private diagnostic") })
        assertTrue("reload" !in fixture.events)
    }

    private class Fixture(opened: Boolean = true) {
        var unavailable = false
        var duringSettings: () -> Unit = {}
        val events = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val deferred = ArrayDeque<() -> Unit>()
        var restored: AccountPickerFilterState? = null
        val controller = AccountSetupController(
            isUnavailable = { unavailable },
            later = { deferred.add(it) },
            openSettings = { events.add("settings"); duringSettings(); opened },
            reloadAccounts = { events.add("reload") },
            reopenPicker = { events.add("reopen"); restored = it },
            showError = { errors.add(it) }
        )
        fun start() = controller.start(filters) { events.add("close") }
        fun deliver() { while (deferred.isNotEmpty()) deferred.removeFirst().invoke() }
    }

    private companion object {
        val filters = AccountPickerFilterState("Example", AccountEnvironment.SANDBOX, "Developer")
    }
}
