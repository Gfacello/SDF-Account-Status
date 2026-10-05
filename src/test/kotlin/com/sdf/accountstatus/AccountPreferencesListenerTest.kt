package com.sdf.accountstatus

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountPreferencesListenerTest {
    private lateinit var fixture: IdeaProjectTestFixture

    @BeforeEach
    fun setUp() {
        fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("Account preference events").fixture
        fixture.setUp()
    }

    @AfterEach
    fun tearDown() = onEdt { fixture.tearDown() }

    @Test
    fun `all live widgets receive changed values once and disposed widgets stop receiving changes`() = onEdt {
        val preferences = SdfAccountPreferences()
        val first = mutableListOf<Pair<AccountPreferenceChange, AccountProviderConfiguration>>()
        val second = mutableListOf<AccountPreferenceChange>()
        val firstDisposable = Disposer.newDisposable()
        val secondDisposable = Disposer.newDisposable()
        var firstDisposed = false
        try {
            val bus = ApplicationManager.getApplication().messageBus
            bus.connect(firstDisposable).subscribe(AccountPreferencesListener.TOPIC, AccountPreferencesListener {
                first.add(it to preferences.providerConfiguration)
            })
            bus.connect(secondDisposable).subscribe(AccountPreferencesListener.TOPIC, AccountPreferencesListener { second.add(it) })
            val legacy = AccountProviderConfiguration(AccountProviderKind.LEGACY_JAVA)
            preferences.providerConfiguration = legacy
            preferences.providerConfiguration = legacy
            assertEquals(listOf(AccountPreferenceChange.PROVIDER to legacy), first)
            assertEquals(listOf(AccountPreferenceChange.PROVIDER), second)

            Disposer.dispose(firstDisposable)
            firstDisposed = true
            preferences.displayStyle = StatusDisplayStyle.ACCOUNT_DETAILS
            preferences.displayStyle = StatusDisplayStyle.ACCOUNT_DETAILS
            assertEquals(listOf(AccountPreferenceChange.PROVIDER to legacy), first)
            assertEquals(listOf(AccountPreferenceChange.PROVIDER, AccountPreferenceChange.DISPLAY_STYLE), second)
        } finally {
            if (!firstDisposed) Disposer.dispose(firstDisposable)
            Disposer.dispose(secondDisposable)
        }
    }

    private fun onEdt(action: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) action() else application.invokeAndWait(action, ModalityState.nonModal())
    }
}
