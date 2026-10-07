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
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComboBox
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.KeyStroke
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
        panel.showAccounts(AccountPickerModelBuilder.build(
            listOf(SdfAuthentication("sandbox-admin", "123456_SB1: Acme Example Corp [Administrator]")),
            "sandbox-admin"
        ))
        panel.restoreFilterState(panel.filterState())
        assertTrue(warning.isVisible, "An unchanged model or filter render must retain the explanation")
        assertTrue(warning.text.contains("Selected role is no longer available"))
        panel.searchField.text = "acme sandbox"
        assertFalse(warning.isVisible)
    }

    @Test
    fun `clearing filters or explicitly refreshing dismisses an earlier removed role notice`() = onEdt {
        val panel = AccountPickerPanel({}, {}, {})
        val refreshed = AccountPickerModelBuilder.build(
            listOf(SdfAuthentication("sandbox-admin", "123456_SB1: Acme Example Corp [Administrator]")),
            "sandbox-admin"
        )
        fun removeSelectedRole() {
            panel.showAccounts(model())
            panel.restoreFilterState(AccountPickerFilterState(role = "Developer"))
            panel.showLoading()
            panel.showAccounts(refreshed)
            assertEquals(AccountPickerFilterState(), panel.filterState())
            assertTrue(panel.label("Account picker warning").isVisible)
        }

        removeSelectedRole()
        panel.clearFilters()
        assertFalse(panel.label("Account picker warning").isVisible,
            "Clear filters must dismiss the notice even when the removed role was the only filter")

        removeSelectedRole()
        panel.refreshButton().doClick()
        panel.showAccounts(refreshed)
        assertFalse(panel.label("Account picker warning").isVisible)

        removeSelectedRole()
        panel.showUnavailable("Account read failed")
        panel.link("Retry").doClick()
        panel.showAccounts(refreshed)
        assertFalse(panel.label("Account picker warning").isVisible)
    }

    @Test
    fun `changing environment or role dismisses the removed role notice`() = onEdt {
        val panel = AccountPickerPanel({}, {}, {})
        panel.showAccounts(model())
        panel.restoreFilterState(AccountPickerFilterState(role = "Removed role"))
        assertTrue(panel.label("Account picker warning").isVisible)

        panel.environmentSelector().choose("Sandbox")
        assertFalse(panel.label("Account picker warning").isVisible)

        panel.restoreFilterState(panel.filterState().copy(role = "Removed role"))
        assertTrue(panel.label("Account picker warning").isVisible)
        panel.roleSelector().choose("Administrator")
        assertFalse(panel.label("Account picker warning").isVisible)
    }

    @Test
    fun `refresh and retry share the loader while preventing repeated clicks and account activation`() = onEdt {
        var fallbackRetries = 0
        var refreshes = 0
        val activations = mutableListOf<String>()
        val panel = AccountPickerPanel(
            { activations.add(it.authenticationId) },
            { fallbackRetries++ },
            {},
            onRefresh = { refreshes++ }
        )
        val refresh = panel.refreshButton()
        assertTrue(refresh.isVisible)
        assertFalse(refresh.isEnabled)
        refresh.doClick()
        assertEquals(0, refreshes)

        panel.showAccounts(model())
        val filters = AccountPickerFilterState("acme", AccountEnvironment.SANDBOX, "Developer")
        panel.restoreFilterState(filters)
        refresh.doClick()

        assertEquals(1, refreshes)
        assertEquals(filters, panel.filterState())
        assertEquals("Reading SuiteCloud accounts…", panel.status().text)
        assertFalse(refresh.isEnabled)
        assertFalse(panel.table().isEnabled)
        assertEquals(0, panel.table().rowCount, "Old rows must not appear as refreshed results")
        refresh.doClick()
        panel.searchField.textEditor.pressEnter()
        assertEquals(1, refreshes)
        assertTrue(activations.isEmpty())

        panel.showUnavailable("The account list could not be read")
        assertTrue(refresh.isEnabled)
        panel.link("Retry").doClick()
        assertEquals(2, refreshes)
        assertEquals(0, fallbackRetries, "Retry uses the same explicit reload callback as Refresh")
        assertFalse(refresh.isEnabled)

        panel.showAccounts(model())
        assertTrue(refresh.isEnabled)
        assertEquals(filters, panel.filterState())
        assertEquals("1 of 4 accounts", panel.status().text)
    }

    @Test
    fun `refresh defaults to the existing retry callback and remains available for empty and error lists`() = onEdt {
        var reads = 0
        val panel = AccountPickerPanel({}, { reads++ }, {})
        val empty = AccountPickerModelBuilder.build(emptyList(), "removed-current")
        panel.showAccounts(empty)
        assertTrue(panel.label("Account picker warning").isVisible)
        assertEquals("No SuiteCloud accounts are configured.", panel.table().emptyText.text)
        panel.refreshButton().activateWithSpace()
        assertEquals(1, reads)

        panel.showUnavailable("Account provider is unavailable", canRetry = false)
        assertFalse(panel.link("Retry").isVisible)
        assertTrue(panel.refreshButton().isEnabled)
        panel.refreshButton().activateWithSpace()
        assertEquals(2, reads)
    }

    @Test
    fun `account activation holds refresh until the controller completes or cancels it`() = onEdt {
        var refreshes = 0
        var activations = 0
        val panel = AccountPickerPanel({ activations++ }, { refreshes++ }, {})
        panel.showAccounts(model())
        panel.setAccountActivationInProgress(true)

        assertFalse(panel.refreshButton().isEnabled)
        assertFalse(panel.table().isEnabled)
        panel.refreshButton().doClick()
        panel.searchField.textEditor.pressEnter()
        assertEquals(0, refreshes)
        assertEquals(0, activations)

        panel.setAccountActivationInProgress(false)
        assertTrue(panel.refreshButton().isEnabled)
        assertTrue(panel.table().isEnabled)
        panel.searchField.textEditor.pressEnter()
        assertEquals(1, activations)
        panel.refreshButton().doClick()
        assertEquals(1, refreshes)
    }

    @Test
    fun `add account works from loading loaded empty and unavailable states without changing filters`() = onEdt {
        var added = 0
        var refreshed = 0
        val selected = mutableListOf<String>()
        val panel = AccountPickerPanel({ selected += it.authenticationId }, { refreshed++ }, {},
            onAddAccount = { added++ })
        val add = panel.addAccountButton()
        assertTrue(add.isVisible)
        assertTrue(add.isEnabled)
        add.activateWithSpace()
        assertEquals(1, added)

        panel.showAccounts(model())
        val filters = AccountPickerFilterState("acme", AccountEnvironment.SANDBOX, "Developer")
        panel.restoreFilterState(filters)
        add.doClick()
        assertEquals(2, added)
        assertEquals(filters, panel.filterState())

        panel.showAccounts(AccountPickerModelBuilder.build(emptyList(), null))
        assertTrue(add.isEnabled)
        add.doClick()
        panel.showUnavailable("Account provider is unavailable", canRetry = false)
        assertTrue(add.isEnabled)
        add.doClick()

        assertEquals(4, added)
        assertEquals(0, refreshed)
        assertTrue(selected.isEmpty(), "Starting setup must not activate an account")
    }

    @Test
    fun `setup and account activation prevent duplicate add actions and conflicting account actions`() = onEdt {
        var added = 0
        var refreshed = 0
        var selected = 0
        var contextMenus = 0
        val panel = AccountPickerPanel({ selected++ }, { refreshed++ }, {},
            onAccountContext = { _, _, _ -> contextMenus++ }, onAddAccount = { added++ })
        panel.showAccounts(model())
        val add = panel.addAccountButton()

        panel.setAccountSetupInProgress(true)
        assertFalse(add.isEnabled)
        assertFalse(panel.refreshButton().isEnabled)
        assertFalse(panel.environmentSelector().isEnabled)
        assertFalse(panel.roleSelector().isEnabled)
        assertFalse(panel.table().isEnabled)
        add.doClick()
        panel.refreshButton().doClick()
        panel.searchField.textEditor.pressEnter()
        panel.table().keyListeners.forEach { listener ->
            listener.keyPressed(KeyEvent(panel.table(), KeyEvent.KEY_PRESSED, 0, 0,
                KeyEvent.VK_CONTEXT_MENU, KeyEvent.CHAR_UNDEFINED))
        }
        assertEquals(0, added)
        assertEquals(0, refreshed)
        assertEquals(0, selected)
        assertEquals(0, contextMenus)

        panel.setAccountSetupInProgress(false)
        assertTrue(add.isEnabled)
        assertTrue(panel.refreshButton().isEnabled)
        panel.setAccountActivationInProgress(true)
        assertFalse(add.isEnabled)
        add.doClick()
        assertEquals(0, added)
        panel.setAccountActivationInProgress(false)
        add.doClick()
        assertEquals(1, added)
    }

    @Test
    fun `add account is omitted when no setup callback is available`() = onEdt {
        val panel = AccountPickerPanel({}, {}, {})
        panel.showAccounts(model())
        assertFalse(panel.addAccountButton().isVisible)
        assertFalse(panel.addAccountButton().isEnabled)
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
    fun `control double click opens the filtered account context without activating it`() = onEdt {
        val selected = mutableListOf<String>()
        val contexts = mutableListOf<String>()
        val panel = AccountPickerPanel({ selected += it.authenticationId }, {}, {},
            onAccountContext = { account, _, _ -> contexts += account.authenticationId },
            onAddAccount = {})
        panel.showAccounts(model())
        val filters = AccountPickerFilterState("acme", AccountEnvironment.SANDBOX, "Developer")
        panel.restoreFilterState(filters)
        UIUtil.dispatchAllInvocationEvents()
        val table = panel.table()
        val bounds = table.getCellRect(table.selectedRow, 0, true)
        // BasicTableUI's press listener asks the native toolkit for its menu shortcut, which is
        // unavailable headlessly. Exercise the panel's listener on the real table instead.
        val pickerMouseListener = table.mouseListeners.single {
            it.javaClass.enclosingClass == AccountPickerPanel::class.java
        }
        val press = MouseEvent(table, MouseEvent.MOUSE_PRESSED, 0, KeyEvent.CTRL_DOWN_MASK,
            bounds.x + 8, bounds.y + bounds.height / 2, 2, true, MouseEvent.BUTTON1)
        pickerMouseListener.mousePressed(press)
        assertTrue(press.isConsumed)
        assertEquals(listOf("sandbox-dev"), contexts)

        listOf(KeyEvent.CTRL_DOWN_MASK to false, 0 to true).forEach { (modifiers, popupTrigger) ->
            val click = MouseEvent(table, MouseEvent.MOUSE_CLICKED, 0, modifiers,
                bounds.x + 8, bounds.y + bounds.height / 2, 2, popupTrigger, MouseEvent.BUTTON1)
            pickerMouseListener.mouseClicked(click)
        }
        assertTrue(selected.isEmpty(), "A context-menu gesture must never switch the project account")
        assertEquals(filters, panel.filterState())
        pickerMouseListener.mouseClicked(MouseEvent(table, MouseEvent.MOUSE_CLICKED, 0, 0,
            bounds.x + 8, bounds.y + bounds.height / 2, 2, false, MouseEvent.BUTTON1))
        assertEquals(listOf("sandbox-dev"), selected, "The same listener must still activate a normal double click")
    }

    @Test
    fun `refresh add selectors and clear action are accessible and fit the minimum picker width`() = onEdt {
        val panel = AccountPickerPanel({}, {}, {}, onAddAccount = {})
        panel.showAccounts(model())
        panel.restoreSearchQuery("acme")
        panel.setSize(panel.minimumSize)
        panel.layoutRecursively()

        listOf(panel.refreshButton(), panel.addAccountButton(), panel.environmentSelector(),
            panel.roleSelector(), panel.link("Clear filters")).forEach {
            assertTrue(it.isFocusable)
            assertTrue(it.accessibleContext.accessibleName.isNotBlank())
            assertTrue(it.width > 0)
            val bounds = SwingUtilities.convertRectangle(it.parent, it.bounds, panel)
            assertTrue(bounds.x >= 0 && bounds.x + bounds.width <= panel.width)
        }
        val labels = panel.descendants().filterIsInstance<JBLabel>().filter { it.labelFor != null }.toList()
        assertEquals(2, labels.size)
        assertTrue(labels.all { it.displayedMnemonic != 0 })
        assertTrue(panel.refreshButton().mnemonic != 0)
        assertEquals("Refresh SuiteCloud accounts", panel.refreshButton().toolTipText)
        assertTrue(panel.refreshButton().icon != null)
        assertTrue(panel.addAccountButton().mnemonic != 0)
        assertTrue(panel.addAccountButton().icon != null)
        assertTrue(panel.searchField.width > 200, "Refresh and Add must leave room for the account query")
        val refreshBounds = SwingUtilities.convertRectangle(panel.refreshButton().parent, panel.refreshButton().bounds, panel)
        val addBounds = SwingUtilities.convertRectangle(panel.addAccountButton().parent, panel.addAccountButton().bounds, panel)
        assertFalse(refreshBounds.intersects(addBounds), "The two account actions must not overlap")
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
    private fun AccountPickerPanel.refreshButton() = descendants().filterIsInstance<JButton>()
        .single { it.accessibleContext.accessibleName == "Refresh SuiteCloud accounts" }
    private fun AccountPickerPanel.addAccountButton() = descendants().filterIsInstance<JButton>()
        .single { it.accessibleContext.accessibleName == "Add an account" }
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
    private fun JButton.activateWithSpace() {
        listOf("pressed SPACE", "released SPACE").forEach { key ->
            val actionKey = getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key))
            actionMap.get(actionKey).actionPerformed(ActionEvent(this, ActionEvent.ACTION_PERFORMED, key))
        }
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
