package com.sdf.accountstatus

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.util.ui.UIUtil
import com.sdf.accountstatus.core.SdfAuthentication
import java.awt.Component
import java.awt.Container
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountBrowserMenuTest {
    private lateinit var fixture: IdeaProjectTestFixture

    @BeforeEach
    fun setUp() {
        fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("Account browser menu").fixture
        fixture.setUp()
    }

    @AfterEach
    fun tearDown() = onEdt { fixture.tearDown() }

    @Test
    fun `keyboard context targets the highlighted row without switching the default`() = onEdt {
        val opened = mutableListOf<String>()
        val switched = mutableListOf<String>()
        val panel = AccountPickerPanel({ switched += it.authenticationId }, {}, {},
            onAccountContext = { account, _, _ -> opened += account.authenticationId })
        panel.showAccounts(model())
        panel.restoreSearchQuery("sandbox")
        UIUtil.dispatchAllInvocationEvents()
        val table = panel.componentsRecursive().filterIsInstance<TreeTable>().single()
        val event = KeyEvent(table, KeyEvent.KEY_PRESSED, 0, KeyEvent.SHIFT_DOWN_MASK, KeyEvent.VK_F10, KeyEvent.CHAR_UNDEFINED)
        table.keyListeners.forEach { it.keyPressed(event) }
        assertTrue(event.isConsumed)
        assertEquals(listOf("example-sandbox"), opened)
        assertTrue(switched.isEmpty())
    }

    @Test
    fun `right click targets its account row and ignores group and loading rows`() = onEdt {
        val opened = mutableListOf<String>()
        val switched = mutableListOf<String>()
        val panel = AccountPickerPanel({ switched += it.authenticationId }, {}, {},
            onAccountContext = { account, _, _ -> opened += account.authenticationId })
        panel.showAccounts(model())
        panel.restoreSearchQuery("sandbox")
        UIUtil.dispatchAllInvocationEvents()
        val table = panel.componentsRecursive().filterIsInstance<TreeTable>().single()
        fun popupAt(row: Int) {
            val rect = table.getCellRect(row, 0, true)
            val event = MouseEvent(table, MouseEvent.MOUSE_RELEASED, 0, 0, rect.x + 8,
                rect.y + rect.height / 2, 1, true, MouseEvent.BUTTON3)
            table.mouseListeners.forEach { it.mouseReleased(event) }
        }
        popupAt(table.selectedRow)
        assertEquals(listOf("example-sandbox"), opened)
        popupAt(0)
        assertEquals(1, opened.size, "Group headings must not open account URLs")
        panel.showLoading()
        val event = KeyEvent(table, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_CONTEXT_MENU, KeyEvent.CHAR_UNDEFINED)
        table.keyListeners.forEach { it.keyPressed(event) }
        assertEquals(1, opened.size)
        assertTrue(switched.isEmpty())
    }

    private fun model() = AccountPickerModelBuilder.build(listOf(
        SdfAuthentication("example-production", "1234567: Example Company [Administrator]"),
        SdfAuthentication("example-sandbox", "1234567_SB1: Example Company [Developer]")
    ), "example-production")

    private fun Container.componentsRecursive(): Sequence<Component> = sequence {
        for (component in components) {
            yield(component)
            if (component is Container) yieldAll(component.componentsRecursive())
        }
    }

    private fun onEdt(block: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (SwingUtilities.isEventDispatchThread()) block()
        else if (application != null) application.invokeAndWait(block, ModalityState.defaultModalityState())
        else SwingUtilities.invokeAndWait(block)
    }
}
