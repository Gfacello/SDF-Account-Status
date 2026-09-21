package com.sdf.accountstatus

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.util.ui.UIUtil
import com.sdf.accountstatus.core.SdfAuthentication
import com.sdf.accountstatus.domain.AccountEnvironment
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountPickerPanelTest {
    private lateinit var fixture: IdeaProjectTestFixture

    @BeforeEach
    fun setUp() {
        fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("Account picker filters").fixture
        fixture.setUp()
    }

    @AfterEach
    fun tearDown() = onEdt { fixture.tearDown() }

    @Test
    fun `keeps open project action fixed in the popup footer`() = onEdt {
        val panel = AccountPickerPanel({}, {}, {})
        val openAction = panel.link("Open project.json")
        val footer = openAction.parent as JPanel

        assertEquals(BorderLayout.EAST, (footer.layout as BorderLayout).getConstraints(openAction))
    }

    @Test
    fun `selector changes combine with search and retain full role choices`() = onEdt {
        val activations = mutableListOf<String>()
        val panel = AccountPickerPanel({ activations.add(it.authenticationId) }, {}, {})
        panel.showAccounts(model())

        panel.searchField.text = "acme"
        panel.environmentSelector().choose("Sandbox")
        panel.roleSelector().choose("Developer")

        assertEquals(
            AccountPickerFilterState("acme", AccountEnvironment.SANDBOX, "Developer"),
            panel.filterState()
        )
        assertEquals("1 of 4 accounts", panel.status().text)
        assertEquals(listOf("All roles", "Administrator", "Developer", "Viewer"), panel.roleSelector().choices())
        assertTrue(activations.isEmpty(), "Changing filters must not activate an account")
        panel.searchField.textEditor.pressEnter()
        assertEquals(listOf("sandbox-dev"), activations)

        panel.environmentSelector().choose("All environments")
        assertEquals("2 of 4 accounts", panel.status().text)
        panel.roleSelector().choose("All roles")
        assertEquals("3 of 4 accounts", panel.status().text)
    }

    @Test
    fun `zero results offers clear action which resets every constraint`() = onEdt {
        val panel = AccountPickerPanel({}, {}, {})
        panel.showAccounts(model())
        panel.restoreFilterState(AccountPickerFilterState("unmatched", AccountEnvironment.UNKNOWN, "Developer"))

        assertEquals("0 of 4 accounts", panel.status().text)
        assertEquals("No accounts match the search and filters.", panel.table().emptyText.text)
        assertEquals(0, panel.table().rowCount)
        val clear = panel.link("Clear filters")
        assertTrue(clear.isVisible)
        clear.doClick()

        assertEquals(AccountPickerFilterState(), panel.filterState())
        assertEquals("All environments", panel.environmentSelector().selectedItem.toString())
        assertEquals("All roles", panel.roleSelector().selectedItem.toString())
        assertEquals("4 of 4 accounts", panel.status().text)
        assertFalse(clear.isVisible)
    }

    @Test
    fun `refresh preserves valid constraints through loading failure and retry`() = onEdt {
        var retryCount = 0
        val activations = mutableListOf<String>()
        val panel = AccountPickerPanel({ activations.add(it.authenticationId) }, { retryCount++ }, {})
        panel.showAccounts(model())
        val state = AccountPickerFilterState("acme", AccountEnvironment.SANDBOX, "Developer")
        panel.restoreFilterState(state)
        panel.showLoading()

        assertEquals(state, panel.filterState())
        assertFalse(panel.environmentSelector().isEnabled)
        assertFalse(panel.roleSelector().isEnabled)
        panel.searchField.textEditor.pressEnter()
        assertTrue(activations.isEmpty())
        panel.showUnavailable("Account read failed")
        assertEquals(state, panel.filterState())
        assertTrue(panel.link("Retry").isVisible)
        panel.link("Retry").doClick()
        assertEquals(1, retryCount)
        panel.showLoading()
        panel.showAccounts(model())

        assertEquals(state, panel.filterState())
        assertEquals("1 of 4 accounts", panel.status().text)
        assertTrue(panel.environmentSelector().isEnabled)
        assertTrue(panel.roleSelector().isEnabled)
        assertFalse(panel.link("Retry").isVisible)
    }

    @Test
    fun `refresh clears only a vanished role and explains the change`() = onEdt {
        val panel = AccountPickerPanel({}, {}, {})
        panel.showAccounts(model())
        val state = AccountPickerFilterState("acme", AccountEnvironment.SANDBOX, "Developer")
        panel.restoreFilterState(state)
        panel.showLoading()
        panel.showAccounts(AccountPickerModelBuilder.build(
            listOf(SdfAuthentication("sandbox-admin", "123456_SB1: Acme Example Corp [Administrator]")),
            "sandbox-admin"
        ))

        assertEquals(state.copy(role = null), panel.filterState())
        assertEquals(listOf("All roles", "Administrator"), panel.roleSelector().choices())
        assertEquals("1 of 1 account", panel.status().text)
        val warning = panel.label("Account picker warning")
        assertTrue(warning.isVisible)
        assertTrue(warning.text.contains("Selected role is no longer available"))
        panel.searchField.text = "acme sandbox"
        assertFalse(warning.isVisible)
    }

    @Test
    fun `restores the entire filter state into a reopened panel before accounts load`() = onEdt {
        val state = AccountPickerFilterState("acme", AccountEnvironment.PRODUCTION, "Developer")
        val original = AccountPickerPanel({}, {}, {})
        original.showAccounts(model())
        original.restoreFilterState(state)
        val selected = mutableListOf<String>()
        val reopened = AccountPickerPanel({ selected.add(it.authenticationId) }, {}, {})

        reopened.restoreFilterState(original.filterState())
        reopened.showAccounts(model())

        assertEquals(state, reopened.filterState())
        assertEquals("Production", reopened.environmentSelector().selectedItem.toString())
        assertEquals("Developer", reopened.roleSelector().selectedItem.toString())
        assertEquals("1 of 4 accounts", reopened.status().text)
        reopened.searchField.textEditor.pressEnter()
        assertEquals(listOf("production-dev"), selected)
    }

    @Test
    fun `enter on a group toggles it while enter and double click activate only leaves`() = onEdt {
        val selected = mutableListOf<String>()
        val panel = AccountPickerPanel({ selected.add(it.authenticationId) }, {}, {})
        panel.showAccounts(model())
        panel.restoreSearchQuery("sandbox-dev")
        UIUtil.dispatchAllInvocationEvents()
        val table = panel.table()
        // Row zero is a section; IntelliJ keeps top-level tree paths expanded. The customer/account
        // group below it is the disclosure row controlled by this keyboard interaction.
        table.setRowSelectionInterval(1, 1)
        val group = table.tree.getPathForRow(1)
        assertTrue(table.tree.isExpanded(group))
        assertEquals(group, table.tree.selectionPath)
        table.pressEnter()
        assertFalse(table.tree.isExpanded(group))
        assertTrue(selected.isEmpty())
        table.pressEnter()
        assertTrue(table.tree.isExpanded(group))
        table.mouseListeners.forEach { listener ->
            listener.mouseClicked(MouseEvent(table, MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 2, false, MouseEvent.BUTTON1))
        }
        assertTrue(selected.isEmpty())

        panel.restoreSearchQuery("sandbox-dev")
        table.pressEnter()
        assertEquals(listOf("sandbox-dev"), selected)
        table.mouseListeners.forEach { listener ->
            listener.mouseClicked(MouseEvent(table, MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 2, false, MouseEvent.BUTTON1))
        }
        assertEquals(listOf("sandbox-dev", "sandbox-dev"), selected)
    }

    @Test
    fun `selectors and clear action are accessible and fit the minimum picker width`() = onEdt {
        val panel = AccountPickerPanel({}, {}, {})
        panel.showAccounts(model())
        panel.restoreSearchQuery("acme")
        panel.setSize(panel.minimumSize)
        panel.layoutRecursively()

        listOf(panel.environmentSelector(), panel.roleSelector(), panel.link("Clear filters")).forEach {
            assertTrue(it.isFocusable)
            assertTrue(it.accessibleContext.accessibleName.isNotBlank())
            assertTrue(it.width > 0)
            val bounds = SwingUtilities.convertRectangle(it.parent, it.bounds, panel)
            assertTrue(bounds.x >= 0 && bounds.x + bounds.width <= panel.width)
        }
        val labels = panel.descendants().filterIsInstance<JBLabel>().filter { it.labelFor != null }.toList()
        assertEquals(2, labels.size)
        assertTrue(labels.all { it.displayedMnemonic != 0 })
    }

    @Test
    fun `places each combined account and customer group directly below its section`() = onEdt {
        val panel = AccountPickerPanel({}, {}, {})
        val model = AccountPickerModelBuilder.build(
            accounts = listOf(
                SdfAuthentication("example-current", "123456_SB1: Acme Example Corp [Administrator]"),
                SdfAuthentication("example-production", "123456: Acme Example Corp [Administrator]")
            ),
            currentAuthenticationId = "example-current"
        )

        val root = panel.buildTree(model)
        val section = root.onlyChild()
        val combinedGroup = section.onlyChild()

        assertEquals(2, combinedGroup.childCount)
        assertEquals(0, (combinedGroup.getChildAt(0) as DefaultMutableTreeNode).childCount)
        assertEquals(0, (combinedGroup.getChildAt(1) as DefaultMutableTreeNode).childCount)
    }

    private fun model() = AccountPickerModelBuilder.build(
        accounts = listOf(
            SdfAuthentication("sandbox-admin", "123456_SB1: Acme Example Corp [Administrator]"),
            SdfAuthentication("sandbox-dev", "123456_SB1: Acme Example Corp [Developer]"),
            SdfAuthentication("production-dev", "123456: Acme Example Corp [Developer]"),
            SdfAuthentication("unverified", "TSTDRV0000000: Training Example Corp [Viewer]")
        ),
        currentAuthenticationId = "sandbox-admin"
    )

    private fun AccountPickerPanel.environmentSelector() = selector("Filter accounts by environment")
    private fun AccountPickerPanel.roleSelector() = selector("Filter accounts by role")
    private fun AccountPickerPanel.selector(name: String) = descendants().filterIsInstance<JComboBox<*>>()
        .single { it.accessibleContext.accessibleName == name }
    private fun AccountPickerPanel.table() = descendants().filterIsInstance<TreeTable>().single()
    private fun AccountPickerPanel.status() = label("Account picker status")
    private fun AccountPickerPanel.label(name: String) = descendants().filterIsInstance<JBLabel>()
        .single { it.accessibleContext.accessibleName == name }
    private fun AccountPickerPanel.link(text: String) = descendants().filterIsInstance<ActionLink>()
        .single { it.text == text }
    private fun JComboBox<*>.choices() = (0 until itemCount).map { getItemAt(it).toString() }
    private fun JComboBox<*>.choose(label: String) {
        selectedIndex = choices().indexOf(label).also { assertTrue(it >= 0) }
    }
    private fun Component.pressEnter() {
        val event = KeyEvent(this, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ENTER, '\n')
        keyListeners.forEach { it.keyPressed(event) }
    }
    private fun Container.layoutRecursively() {
        doLayout()
        components.filterIsInstance<Container>().forEach { it.layoutRecursively() }
    }
    private fun Container.descendants(): Sequence<Component> = sequence {
        components.forEach { component ->
            yield(component)
            if (component is Container) yieldAll(component.descendants())
        }
    }
    private fun DefaultMutableTreeNode.onlyChild(): DefaultMutableTreeNode {
        assertEquals(1, childCount)
        return getChildAt(0) as DefaultMutableTreeNode
    }
    private fun onEdt(block: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) block() else application.invokeAndWait(block, ModalityState.nonModal())
    }
}
