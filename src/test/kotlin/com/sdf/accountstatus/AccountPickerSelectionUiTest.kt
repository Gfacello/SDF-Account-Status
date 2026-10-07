package com.sdf.accountstatus

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.sdf.accountstatus.core.ProjectJsonUpdateResult
import com.sdf.accountstatus.core.SdfAuthentication
import com.sdf.accountstatus.domain.AccountEnvironment
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountPickerSelectionUiTest {
    private lateinit var fixture: IdeaProjectTestFixture

    @BeforeEach
    fun setUp() {
        fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("Picker cancel and reopen").fixture
        fixture.setUp()
    }

    @AfterEach
    fun tearDown() = onEdt { fixture.tearDown() }

    @Test
    fun `cancel reopens real picker with environment and role even without search text`() = onEdt {
        val filters = AccountPickerFilterState(environment = AccountEnvironment.PRODUCTION, role = "Developer")
        val original = panel().apply { restoreFilterState(filters) }
        var reopened: AccountPickerPanel? = null
        var open = true
        val writes = mutableListOf<String>()
        val deferred = ArrayDeque<() -> Unit>()
        val controller = AccountWorkflowController(
            writer = { writes.add(it); ProjectJsonUpdateResult.Updated("{}") },
            currentAuthenticationId = { "example-sandbox" },
            isUnavailable = { false },
            later = { deferred.add(it) },
            onSaved = { error("Cancelled selection must not be published") }
        )
        val ui = AccountPickerSelectionUi(
            panel = original,
            isPickerOpen = { open },
            close = { open = false },
            confirm = { false },
            reopen = { saved -> reopened = panel().apply { restoreFilterState(saved) } },
            showDetachedError = { error(it) }
        )
        controller.selectAccount(production, ui)
        assertFalse(open)
        original.clearFilters() // The closed component must not change the captured restoration state.
        while (deferred.isNotEmpty()) deferred.removeFirst().invoke()
        assertTrue(writes.isEmpty())
        assertEquals(filters, reopened?.filterState())
    }

    @Test
    fun `confirmation uses selected account and persists once without reopening`() = onEdt {
        val original = panel()
        val deferred = ArrayDeque<() -> Unit>()
        val writes = mutableListOf<String>()
        var confirmed: AccountPickerAccount? = null
        val controller = AccountWorkflowController(
            writer = { writes.add(it); ProjectJsonUpdateResult.Updated("{}") },
            currentAuthenticationId = { "example-sandbox" },
            isUnavailable = { false },
            later = { deferred.add(it) },
            onSaved = { assertEquals(production.authenticationId, it.authenticationId) }
        )
        controller.selectAccount(production, AccountPickerSelectionUi(
            original, { false }, {}, { confirmed = it; true },
            { error("Confirmed selection must not reopen") }, { error(it) }
        ))
        while (deferred.isNotEmpty()) deferred.removeFirst().invoke()
        assertEquals(production, confirmed)
        assertEquals(listOf("example-production"), writes)
    }

    private fun panel() = AccountPickerPanel({}, {}, {}).apply {
        showAccounts(AccountPickerModelBuilder.build(
            listOf(
                production.authentication,
                SdfAuthentication("example-sandbox", "123456_SB1: Example Company [Developer]")
            ), "example-sandbox"
        ))
    }

    private fun onEdt(block: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) block() else application.invokeAndWait(block, ModalityState.nonModal())
    }

    private companion object {
        val production = AccountPickerAccount(
            SdfAuthentication("example-production", "123456: Example Company [Developer]"),
            AccountEnvironment.PRODUCTION, false, false
        )
    }
}
