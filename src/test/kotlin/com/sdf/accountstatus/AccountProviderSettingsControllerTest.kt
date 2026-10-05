package com.sdf.accountstatus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountProviderSettingsControllerTest {
    @Test
    fun `applying a provider atomically saves selection and paths before callback`() {
        val selected = AccountProviderConfiguration(AccountProviderKind.LEGACY_JAVA, " /custom node ", " /custom/suitecloud.js ")
        val fixture = Fixture(selected)
        fixture.controller.configure()
        assertEquals(selected.normalized(), fixture.preferences.providerConfiguration)
        assertEquals(listOf(selected.normalized()), fixture.applied)
        assertTrue(fixture.errors.isEmpty())
    }

    @Test
    fun `cancel and an unchanged selection do not persist or request a reload`() {
        for (answer in listOf(null, AccountProviderConfiguration())) {
            val fixture = Fixture(answer)
            fixture.controller.configure()
            assertEquals(AccountProviderConfiguration(), fixture.preferences.providerConfiguration)
            assertTrue(fixture.applied.isEmpty())
            assertTrue(fixture.errors.isEmpty())
        }
    }

    @Test
    fun `invalid Node configuration keeps old selection and reports actionable error`() {
        val fixture = Fixture(AccountProviderConfiguration(nodeExecutable = "relative-path"))
        fixture.controller.configure()
        assertEquals(AccountProviderConfiguration(), fixture.preferences.providerConfiguration)
        assertEquals(ProviderPathField.NODE_EXECUTABLE, fixture.errors.single().field)
        assertTrue(fixture.applied.isEmpty())
    }

    @Test
    fun `closing project before opening settings avoids the dialog`() {
        val fixture = Fixture(AccountProviderConfiguration(AccountProviderKind.LEGACY_JAVA))
        fixture.disposed = true
        fixture.controller.configure()
        assertEquals(0, fixture.prompts)
        assertTrue(fixture.applied.isEmpty())
    }

    @Test
    fun `closing project while dialog is open prevents persistence and reload`() {
        val fixture = Fixture(AccountProviderConfiguration(AccountProviderKind.LEGACY_JAVA), disposeDuringPrompt = true)
        fixture.controller.configure()
        assertEquals(1, fixture.prompts)
        assertEquals(AccountProviderConfiguration(), fixture.preferences.providerConfiguration)
        assertTrue(fixture.applied.isEmpty())
    }

    private class Fixture(answer: AccountProviderConfiguration?, disposeDuringPrompt: Boolean = false) {
        val preferences = SdfAccountPreferences()
        val applied = mutableListOf<AccountProviderConfiguration>()
        val errors = mutableListOf<ProviderConfigurationError>()
        var prompts = 0
        var disposed = false
        val controller = AccountProviderSettingsController(preferences, {
            prompts++
            disposed = disposeDuringPrompt
            answer
        }, errors::add, { disposed }, { applied.add(preferences.providerConfiguration) })
    }
}
