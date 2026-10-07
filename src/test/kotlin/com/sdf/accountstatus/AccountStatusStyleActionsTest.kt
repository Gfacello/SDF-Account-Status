package com.sdf.accountstatus

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountStatusStyleActionsTest {
    private lateinit var fixture: IdeaProjectTestFixture

    @BeforeEach
    fun setUp() {
        fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("Status display actions").fixture
        fixture.setUp()
    }

    @AfterEach
    fun tearDown() = onEdt { fixture.tearDown() }

    @Test
    fun `menu selection persists one display style before repaint and cannot uncheck the current style`() = onEdt {
        val preferences = SdfAccountPreferences()
        val renderedStyles = mutableListOf<StatusDisplayStyle>()
        val group = AccountStatusStyleActions(preferences) { renderedStyles.add(preferences.displayStyle) }.group()
        val actions = group.getChildren(null).map { it as ToggleAction }
        assertEquals(listOf("Authentication ID", "Account details"), actions.map { it.templatePresentation.text })
        val compact = actions[0]
        val detailed = actions[1]
        val compactEvent = AnActionEvent.createEvent(
            compact, DataContext.EMPTY_CONTEXT, compact.templatePresentation.clone(), "StatusDisplayTest", ActionUiKind.POPUP, null
        )
        val detailedEvent = AnActionEvent.createEvent(
            detailed, DataContext.EMPTY_CONTEXT, detailed.templatePresentation.clone(), "StatusDisplayTest", ActionUiKind.POPUP, null
        )
        assertTrue(compact.isSelected(compactEvent))
        assertFalse(detailed.isSelected(detailedEvent))

        detailed.actionPerformed(detailedEvent)
        assertEquals(listOf(StatusDisplayStyle.ACCOUNT_DETAILS), renderedStyles)
        assertTrue(detailed.isSelected(detailedEvent))
        assertFalse(compact.isSelected(compactEvent))
        detailed.actionPerformed(detailedEvent)
        assertEquals(listOf(StatusDisplayStyle.ACCOUNT_DETAILS), renderedStyles)
        assertTrue(detailed.isSelected(detailedEvent))

        compact.actionPerformed(compactEvent)
        assertEquals(listOf(StatusDisplayStyle.ACCOUNT_DETAILS, StatusDisplayStyle.AUTHENTICATION_ID), renderedStyles)
        assertTrue(compact.isSelected(compactEvent))
        assertFalse(detailed.isSelected(detailedEvent))
    }

    private fun onEdt(action: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) action() else application.invokeAndWait(action, ModalityState.nonModal())
    }
}
