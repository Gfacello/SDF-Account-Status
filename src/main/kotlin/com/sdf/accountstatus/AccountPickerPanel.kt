package com.sdf.accountstatus

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.sdf.accountstatus.domain.AccountEnvironment
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

/**
 * Compact, keyboard-accessible account picker content. Popup ownership and mutations remain in the
 * widget controller; this component only presents state and reports explicit user actions.
 */
internal class AccountPickerPanel(
    private val onAccountChosen: (AccountPickerAccount) -> Unit,
    private val onRetry: () -> Unit,
    private val onOpenProjectJson: () -> Unit
) : JPanel(BorderLayout()) {
    val searchField = SearchTextField(false)

    private val rootNode = DefaultMutableTreeNode(PickerTreeValue.Root)
    private val treeModel = ListTreeTableModelOnColumns(rootNode, COLUMNS)
    private val treeTable = TreeTable(treeModel)
    private val warningLabel = JBLabel()
    private val stateLabel = JBLabel()
    private val retryLink = ActionLink("Retry") { onRetry() }
    private val openProjectJsonLink = ActionLink("Open project.json") { onOpenProjectJson() }

    private var fullModel: AccountPickerModel? = null

    init {
        preferredSize = JBUI.size(720, 360)
        minimumSize = JBUI.size(560, 280)
        border = JBUI.Borders.empty(8)

        searchField.textEditor.emptyText.text =
            "Search authentication IDs, customers, accounts, roles, or environments"
        searchField.textEditor.accessibleContext.accessibleName = "Search SuiteCloud accounts"
        searchField.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(event: javax.swing.event.DocumentEvent) = applyFilter()
            override fun removeUpdate(event: javax.swing.event.DocumentEvent) = applyFilter()
            override fun changedUpdate(event: javax.swing.event.DocumentEvent) = applyFilter()
        })
        searchField.textEditor.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(event: KeyEvent) {
                when (event.keyCode) {
                    KeyEvent.VK_DOWN -> {
                        selectFirstAccount()
                        treeTable.requestFocusInWindow()
                        event.consume()
                    }
                    KeyEvent.VK_ENTER -> {
                        if (treeTable.selectedRow < 0) selectFirstAccount()
                        activateSelectedRow()
                        event.consume()
                    }
                }
            }
        })

        warningLabel.isVisible = false
        warningLabel.border = JBUI.Borders.emptyTop(6)
        warningLabel.accessibleContext.accessibleName = "Account picker warning"

        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(6)
            add(searchField, BorderLayout.NORTH)
            add(warningLabel, BorderLayout.SOUTH)
        }

        configureTreeTable()

        stateLabel.foreground = com.intellij.ui.JBColor.GRAY
        stateLabel.accessibleContext.accessibleName = "Account picker status"
        retryLink.mnemonic = KeyEvent.VK_R
        retryLink.accessibleContext.accessibleName = "Retry reading SuiteCloud accounts"
        retryLink.isVisible = false
        openProjectJsonLink.mnemonic = KeyEvent.VK_O
        openProjectJsonLink.accessibleContext.accessibleName = "Open project.json"

        val footerLeft = JPanel(FlowLayout(FlowLayout.LEADING, JBUI.scale(8), 0)).apply {
            isOpaque = false
            add(stateLabel)
            add(retryLink)
        }
        val footer = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(6)
            add(footerLeft, BorderLayout.WEST)
            add(openProjectJsonLink, BorderLayout.EAST)
        }

        add(header, BorderLayout.NORTH)
        add(JBScrollPane(treeTable), BorderLayout.CENTER)
        add(footer, BorderLayout.SOUTH)
        showLoading()
    }

    fun showLoading() {
        fullModel = null
        searchField.isEnabled = false
        warningLabel.isVisible = false
        retryLink.isVisible = false
        stateLabel.text = "Reading SuiteCloud accounts…"
        val currentRoot = treeModel.root as? DefaultMutableTreeNode
        if (currentRoot == null || currentRoot.childCount > 0) {
            replaceRoot(DefaultMutableTreeNode(PickerTreeValue.Root))
        } else {
            treeTable.clearSelection()
        }
        treeTable.emptyText.text = "Reading SuiteCloud authentication IDs…"
        setBusy(true)
    }

    fun showAccounts(model: AccountPickerModel) {
        fullModel = model
        searchField.isEnabled = true
        retryLink.isVisible = false
        setBusy(false)
        warningLabel.text = if (model.currentAuthenticationMissing) {
            "The default authentication ID in project.json is not present in the SuiteCloud account list."
        } else {
            ""
        }
        warningLabel.isVisible = model.currentAuthenticationMissing
        stateLabel.text =
            "${model.accounts.size} configured account${if (model.accounts.size == 1) "" else "s"}" +
                "  ·  Enter or double-click to switch"
        treeTable.emptyText.text = "No accounts match this search."
        applyFilter()
    }

    fun showUnavailable(message: String, canRetry: Boolean = true) {
        fullModel = null
        searchField.isEnabled = false
        warningLabel.isVisible = false
        setBusy(false)
        retryLink.isVisible = canRetry
        stateLabel.text = message
        replaceRoot(DefaultMutableTreeNode(PickerTreeValue.Root))
        treeTable.emptyText.text = message
    }

    fun showOperationError(message: String) {
        stateLabel.text = message
        stateLabel.accessibleContext.accessibleDescription = message
    }

    fun focusSearch() {
        searchField.requestFocusInWindow()
        searchField.selectText()
    }

    fun searchQuery(): String = searchField.text

    fun restoreSearchQuery(query: String) {
        searchField.text = query
        searchField.textEditor.caretPosition = query.length
    }

    private fun setBusy(busy: Boolean) {
        // Plain Swing/unit-test contexts have no IntelliJ application and therefore no modality
        // state for JBTable's animated busy icon. A real plugin UI always has an application.
        if (ApplicationManager.getApplication() != null) treeTable.setPaintBusy(busy)
    }

    private fun configureTreeTable() {
        treeModel.setSortable(false)
        treeTable.setRootVisible(false)
        treeTable.setProcessCursorKeys(true)
        treeTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        treeTable.cellSelectionEnabled = false
        treeTable.rowSelectionAllowed = true
        treeTable.setShowVerticalLines(false)
        treeTable.setShowHorizontalLines(false)
        treeTable.setTreeCellRenderer(AccountTreeRenderer())
        treeTable.accessibleContext.accessibleName = "Configured SuiteCloud accounts"
        treeTable.tableHeader.reorderingAllowed = false
        treeTable.rowHeight = JBUI.scale(24)
        treeTable.columnModel.getColumn(0).preferredWidth = JBUI.scale(290)
        treeTable.columnModel.getColumn(1).preferredWidth = JBUI.scale(120)
        treeTable.columnModel.getColumn(2).preferredWidth = JBUI.scale(105)
        treeTable.columnModel.getColumn(3).preferredWidth = JBUI.scale(150)
        treeTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.button == MouseEvent.BUTTON1 && event.clickCount == 2) {
                    // The embedded tree owns disclosure clicks; double-click only activates leaves.
                    activateSelectedRow(toggleGroups = false)
                }
            }
        })
        treeTable.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(event: KeyEvent) {
                if (event.keyCode == KeyEvent.VK_ENTER) {
                    activateSelectedRow()
                    event.consume()
                }
            }
        })
    }

    private fun applyFilter() {
        val model = fullModel ?: return
        val query = searchField.text.trim()
        val filtered = model.filtered(query)
        val root = buildTree(filtered)
        replaceRoot(root)
        if (query.isNotEmpty()) {
            expandAll()
        } else {
            applyInitialExpansion(filtered)
        }
        selectCurrentOrFirst(filtered.currentAuthenticationId)
    }

    private fun replaceRoot(root: DefaultMutableTreeNode) {
        treeModel.setRoot(root)
        treeModel.reload()
        treeTable.clearSelection()
    }

    internal fun buildTree(model: AccountPickerModel): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode(PickerTreeValue.Root)
        model.sections.filter { it.groups.isNotEmpty() }.forEach { section ->
            val sectionNode = DefaultMutableTreeNode(PickerTreeValue.Section(section))
            section.groups.forEach { group ->
                val groupNode = DefaultMutableTreeNode(PickerTreeValue.Group(group))
                group.accounts.forEach { account ->
                    groupNode.add(DefaultMutableTreeNode(PickerTreeValue.Account(account)))
                }
                sectionNode.add(groupNode)
            }
            root.add(sectionNode)
        }
        return root
    }

    private fun applyInitialExpansion(model: AccountPickerModel) {
        val root = treeModel.root as? DefaultMutableTreeNode ?: return
        if (root.childCount == 0) return

        val preferredSection = (0 until root.childCount)
            .map { root.getChildAt(it) as DefaultMutableTreeNode }
            .firstOrNull {
                (it.userObject as? PickerTreeValue.Section)?.model?.kind ==
                    AccountPickerSection.CURRENT_AND_RECOMMENDED
            }
        if (preferredSection != null) {
            expandSubtree(preferredSection)
            return
        }

        val configuredSection = (0 until root.childCount)
            .map { root.getChildAt(it) as DefaultMutableTreeNode }
            .firstOrNull {
                (it.userObject as? PickerTreeValue.Section)?.model?.kind ==
                    AccountPickerSection.CONFIGURED
            }
        configuredSection?.let { treeTable.tree.expandPath(TreePath(it.path)) }

        val currentId = model.currentAuthenticationId ?: return
        findAccountNode(currentId)?.path?.let { path ->
            for (index in 1 until path.size - 1) {
                treeTable.tree.expandPath(TreePath(path.copyOfRange(0, index + 1)))
            }
        }
    }

    private fun expandSubtree(node: DefaultMutableTreeNode) {
        treeTable.tree.expandPath(TreePath(node.path))
        for (index in 0 until node.childCount) {
            expandSubtree(node.getChildAt(index) as DefaultMutableTreeNode)
        }
    }

    private fun expandAll() {
        var row = 0
        while (row < treeTable.tree.rowCount) {
            treeTable.tree.expandRow(row)
            row += 1
        }
    }

    private fun selectCurrentOrFirst(currentAuthenticationId: String?) {
        val current = currentAuthenticationId?.let(::findAccountNode)
        val target = current ?: firstAccountNode()
        if (target != null) {
            val path = TreePath(target.path)
            treeTable.tree.expandPath(path.parentPath)
            val row = treeTable.tree.getRowForPath(path)
            if (row >= 0) {
                treeTable.selectionModel.setSelectionInterval(row, row)
                treeTable.scrollRectToVisible(treeTable.getCellRect(row, 0, true))
            }
        }
    }

    private fun selectFirstAccount() {
        val account = firstAccountNode() ?: return
        val path = TreePath(account.path)
        treeTable.tree.expandPath(path.parentPath)
        val row = treeTable.tree.getRowForPath(path)
        if (row >= 0) treeTable.selectionModel.setSelectionInterval(row, row)
    }

    private fun activateSelectedRow(toggleGroups: Boolean = true) {
        val row = treeTable.selectedRow
        if (row < 0) return
        val path = treeTable.tree.getPathForRow(row) ?: return
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        when (val value = node.userObject as? PickerTreeValue) {
            is PickerTreeValue.Account -> onAccountChosen(value.model)
            PickerTreeValue.Root, null -> Unit
            else -> if (!toggleGroups) {
                Unit
            } else if (treeTable.tree.isExpanded(path)) {
                treeTable.tree.collapsePath(path)
            } else {
                treeTable.tree.expandPath(path)
            }
        }
    }

    private fun findAccountNode(authenticationId: String): DefaultMutableTreeNode? =
        accountNodes().firstOrNull {
            ((it.userObject as PickerTreeValue.Account).model.authentication.authenticationId ==
                authenticationId)
        }

    private fun firstAccountNode(): DefaultMutableTreeNode? = accountNodes().firstOrNull()

    private fun accountNodes(): Sequence<DefaultMutableTreeNode> {
        val root = treeModel.root as? DefaultMutableTreeNode ?: return emptySequence()
        return root.depthFirstEnumeration().asSequence()
            .filterIsInstance<DefaultMutableTreeNode>()
            .filter { it.userObject is PickerTreeValue.Account }
    }

    private sealed interface PickerTreeValue {
        data object Root : PickerTreeValue
        data class Section(val model: AccountPickerSectionModel) : PickerTreeValue
        data class Group(val model: AccountPickerGroup) : PickerTreeValue
        data class Account(val model: AccountPickerAccount) : PickerTreeValue
    }

    private class AccountTreeRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: javax.swing.JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ) {
            val node = value as? DefaultMutableTreeNode ?: return
            when (val item = node.userObject as? PickerTreeValue) {
                PickerTreeValue.Root, null -> Unit
                is PickerTreeValue.Section -> {
                    append(item.model.kind.title, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    append("  ${item.model.accounts.size}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    super.getAccessibleContext().accessibleName =
                        "${item.model.kind.title}, ${item.model.accounts.size} accounts"
                }
                is PickerTreeValue.Group -> {
                    append(item.model.displayName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    append("  ${item.model.accounts.size}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    super.getAccessibleContext().accessibleName =
                        "${item.model.accountFamily}, ${item.model.customerName}, " +
                            "${item.model.accounts.size} accounts"
                }
                is PickerTreeValue.Account -> {
                    append(item.model.authentication.authenticationId, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    if (item.model.isCurrent) {
                        append("  Current", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    }
                    super.getAccessibleContext().accessibleName = buildString {
                        append(item.model.authentication.authenticationId)
                        append(", ")
                        append(item.model.accountName)
                        append(", ")
                        append(item.model.accountId)
                        append(", ")
                        append(item.model.environment.label)
                        append(", ")
                        append(item.model.role)
                        if (item.model.isCurrent) append(", current")
                    }
                }
            }
        }
    }

    private companion object {
        private val COLUMNS: Array<ColumnInfo<*, *>> = arrayOf(
            TreeColumnInfo("Authentication / account / customer"),
            object : ColumnInfo<DefaultMutableTreeNode, String>("Account ID") {
                override fun valueOf(item: DefaultMutableTreeNode): String =
                    ((item.userObject as? PickerTreeValue.Account)?.model?.accountId).orEmpty()
            },
            object : ColumnInfo<DefaultMutableTreeNode, String>("Environment") {
                override fun valueOf(item: DefaultMutableTreeNode): String = when (
                    val environment = (item.userObject as? PickerTreeValue.Account)?.model?.environment
                ) {
                    AccountEnvironment.SANDBOX -> "Sandbox"
                    AccountEnvironment.PRODUCTION -> "Production"
                    AccountEnvironment.RELEASE_PREVIEW -> "Release Preview"
                    AccountEnvironment.UNKNOWN, null -> if (environment == null) "" else "Unverified"
                }
            },
            object : ColumnInfo<DefaultMutableTreeNode, String>("Role") {
                override fun valueOf(item: DefaultMutableTreeNode): String =
                    ((item.userObject as? PickerTreeValue.Account)?.model?.role).orEmpty()
            }
        )
    }
}
