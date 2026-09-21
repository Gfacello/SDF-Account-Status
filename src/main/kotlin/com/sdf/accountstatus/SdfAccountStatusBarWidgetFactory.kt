package com.sdf.accountstatus

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.IconUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.sdf.accountstatus.core.SdfAccountStatusResolver
import com.sdf.accountstatus.core.SuiteCloudAuthListLoader
import com.sdf.accountstatus.domain.AccountWidgetState
import com.sdf.accountstatus.domain.WidgetTone
import java.awt.Color
import java.awt.Cursor
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.jvm.JvmDefaultWithoutCompatibility

class SdfAccountStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = STATUS_BAR_WIDGET_ID

    override fun getDisplayName(): String = "NetSuite SDF Account Status"

    override fun isAvailable(project: Project): Boolean = project.basePath != null

    override fun isEnabledByDefault(): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = SdfAccountStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

@JvmDefaultWithoutCompatibility
private class SdfAccountStatusBarWidget(private val project: Project) : CustomStatusBarWidget, Disposable {
    private val label = JBLabel("Reading project.json…")
    private val criticalIcon: Icon? = UIManager.getIcon("OptionPane.errorIcon")?.let {
        IconUtil.scale(it, label, 0.7f)
    }

    private val projectJsonPath: Path? = project.basePath
        ?.let { Path.of(it, "project.json").toAbsolutePath().normalize() }
    private val resolver = SdfAccountStatusResolver(projectJsonPath)
    private val projectJsonGateway = IntellijProjectJsonGateway(project, projectJsonPath)
    private val browserActions = AccountBrowserActions(project) { isDisposed || project.isDisposed }
    private val stateController = AccountStateController(
        provider = { SuiteCloudAuthListLoader().load() },
        readProject = {
            val snapshot = resolver.readSnapshot()
            try {
                projectJsonPath?.let { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it) }
            } catch (cancelled: ProcessCanceledException) {
                throw cancelled
            } catch (_: Exception) {
                // A VFS refresh failure must not discard the already captured project status.
            }
            snapshot
        },
        scheduler = IntellijAccountWorkflowScheduler(),
        isUnavailable = { isDisposed || project.isDisposed },
        onStateChanged = { state ->
            lastModifiedMillis = state.project.lastModifiedMillis
            applyState(state.presentation)
            accountPickerPanel?.let(::renderAccountListState)
        }
    )
    private val controller = AccountWorkflowController(
        writer = projectJsonGateway,
        currentAuthenticationId = { stateController.state.currentAuthenticationId },
        isUnavailable = { isDisposed || project.isDisposed },
        later = { ApplicationManager.getApplication().invokeLater(it) },
        onSaved = stateController::accountSaved
    )
    private val connection = project.messageBus.connect(this)
    private val pollingTask: ScheduledFuture<*>

    @Volatile
    private var isDisposed = false

    @Volatile
    private var lastModifiedMillis: Long = Long.MIN_VALUE

    private var accountPopup: JBPopup? = null
    private var accountPickerPanel: AccountPickerPanel? = null

    init {
        configureStatusLabel()
        refreshAsync()
        loadAccountsInBackground()

        pollingTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            { pollProjectJsonChanges() },
            2,
            2,
            TimeUnit.SECONDS
        )

        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                val trackedPath = projectJsonPath?.toString() ?: return
                if (events.any { it.path == trackedPath }) {
                    refreshAsync()
                }
            }
        })
    }

    override fun ID(): String = STATUS_BAR_WIDGET_ID

    /** Return null because this custom widget is represented by [getComponent]. */
    override fun getPresentation(): StatusBarWidget.WidgetPresentation? = null

    override fun getComponent(): JComponent = label

    override fun dispose() {
        isDisposed = true
        controller.dispose()
        stateController.dispose()
        accountPopup?.let { popup ->
            val cancelPopup = Runnable {
                if (!popup.isDisposed) popup.cancel()
            }
            if (ApplicationManager.getApplication().isDispatchThread) {
                cancelPopup.run()
            } else {
                ApplicationManager.getApplication().invokeLater(cancelPopup)
            }
        }
        pollingTask.cancel(true)
        connection.dispose()
    }

    private fun configureStatusLabel() {
        label.font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
        label.border = JBUI.Borders.empty(0, 4)
        label.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        label.isFocusable = true
        label.putClientProperty("html.disable", true)
        label.accessibleContext.accessibleName = "NetSuite SDF account status"
        label.accessibleContext.accessibleDescription =
            "Shows the current SDF authentication ID. Press Enter or Space to choose another account."
        label.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) = showContextIfRequested(event)
            override fun mouseReleased(event: MouseEvent) = showContextIfRequested(event)

            private fun showContextIfRequested(event: MouseEvent) {
                if (event.isPopupTrigger) {
                    showStatusContextMenu(event.point)
                    event.consume()
                }
            }

            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 1 && SwingUtilities.isLeftMouseButton(event) &&
                    !event.isPopupTrigger && !event.isControlDown) {
                    showAccountPicker()
                }
            }
        })
        label.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(event: KeyEvent) {
                if (event.keyCode == KeyEvent.VK_CONTEXT_MENU ||
                    event.keyCode == KeyEvent.VK_F10 && event.isShiftDown) {
                    showStatusContextMenu(Point(0, label.height))
                    event.consume()
                    return
                }
                if (event.keyCode == KeyEvent.VK_ENTER || event.keyCode == KeyEvent.VK_SPACE) {
                    showAccountPicker()
                    event.consume()
                }
            }
        })
    }

    private fun showStatusContextMenu(point: Point) {
        if (isDisposed || project.isDisposed) return
        val target = BrowserAccountTarget.from(stateController.state.currentAuthentication)
        ActionManager.getInstance().createActionPopupMenu(
            "SdfAccountStatus.Context", browserActions.group { target }
        ).component.show(label, point.x, point.y)
    }

    private fun showAccountPicker(restoredQuery: String = "") {
        if (isDisposed || project.isDisposed) return
        val existingPopup = accountPopup
        if (existingPopup != null && existingPopup.isVisible && !existingPopup.isDisposed) return

        lateinit var popup: JBPopup
        lateinit var panel: AccountPickerPanel
        panel = AccountPickerPanel(
            onAccountChosen = { account -> handleAccountSelection(account, popup, panel) },
            onRetry = {
                panel.showLoading()
                loadAccountsInBackground(force = true)
            },
            onOpenProjectJson = {
                if (!popup.isDisposed) popup.cancel()
                openProjectJson()
            },
            onAccountContext = { account, component, point ->
                val target = BrowserAccountTarget.from(account.authentication)
                ActionManager.getInstance().createActionPopupMenu(
                    "SdfAccountPicker.Context", browserActions.group { target }
                ).component.show(component, point.x, point.y)
            }
        )

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, panel.searchField.textEditor)
            .setTitle("Select NetSuite SDF Account")
            .setProject(project)
            .setRequestFocus(true)
            .setFocusable(true)
            .setResizable(true)
            .setMovable(false)
            .setMinSize(JBUI.size(560, 280))
            .setDimensionServiceKey(project, ACCOUNT_PICKER_DIMENSION_KEY, false)
            .setLocateWithinScreenBounds(true)
            .setCancelKeyEnabled(true)
            .setCancelOnClickOutside(true)
            .createPopup()

        popup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                if (accountPopup === popup) {
                    accountPopup = null
                    accountPickerPanel = null
                }
            }
        })
        accountPopup = popup
        accountPickerPanel = panel
        renderAccountListState(panel)
        popup.show(JBPopupFactory.getInstance().guessBestPopupLocation(label))
        panel.focusSearch()
        if (restoredQuery.isNotEmpty()) panel.restoreSearchQuery(restoredQuery)
    }

    private fun renderAccountListState(panel: AccountPickerPanel) {
        when (val state = stateController.state.accountList) {
            AccountListState.Loading -> panel.showLoading()
            is AccountListState.Available -> panel.showAccounts(
                AccountPickerModelBuilder.build(state.accounts, stateController.state.currentAuthenticationId)
            )
            is AccountListState.Unavailable -> panel.showUnavailable(state.message)
        }
    }

    private fun loadAccountsInBackground(force: Boolean = false) = onUiThread {
        stateController.loadAccounts(force)
    }

    private fun handleAccountSelection(
        account: AccountPickerAccount,
        popup: JBPopup,
        panel: AccountPickerPanel
    ) {
        val query = panel.searchQuery()
        controller.selectAccount(account, object : AccountSelectionUi {
            override fun closePicker() {
                if (!popup.isDisposed) popup.cancel()
            }

            override fun confirmProduction(account: AccountPickerAccount): Boolean =
                Messages.showYesNoDialog(
                    project,
                    buildString {
                        appendLine("Switch the default SDF account to production?")
                        appendLine()
                        appendLine("Authentication ID: ${account.authenticationId}")
                        appendLine("Account: ${account.accountName} (${account.accountId})")
                        append("Role: ${account.role}")
                    },
                    "Confirm Production Account",
                    "Switch to Production",
                    "Cancel",
                    Messages.getWarningIcon()
                ) == Messages.YES

            override fun restorePicker() = showAccountPicker(restoredQuery = query)

            override fun showError(message: String) {
                if (!popup.isDisposed) panel.showOperationError(message) else showPopupMessage(message)
            }
        })
    }

    private fun openProjectJson() {
        val virtualFile = projectJsonGateway.findFile()
        if (virtualFile == null) {
            showPopupMessage("project.json was not found in this project.")
            return
        }
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }

    private fun showPopupMessage(message: String) {
        val popup = JBPopupFactory.getInstance().createMessage(message)
        popup.show(JBPopupFactory.getInstance().guessBestPopupLocation(label))
    }

    private fun refreshAsync() = onUiThread { stateController.refreshProject() }

    private fun onUiThread(action: () -> Unit) {
        if (ApplicationManager.getApplication().isDispatchThread) action()
        else ApplicationManager.getApplication().invokeLater(action)
    }

    private fun pollProjectJsonChanges() {
        if (isDisposed) return
        val newTimestamp = resolver.getLastModifiedMillisOrMinusOne()
        if (newTimestamp != lastModifiedMillis) refreshAsync()
    }

    private fun applyState(state: AccountWidgetState) {
        val toneColor = when (state.tone) {
            WidgetTone.OK -> JBColor(Color(0x2E7D32), Color(0x81C784))
            WidgetTone.WARNING -> JBColor(Color(0x8A6500), Color(0xFFD54F))
            WidgetTone.ERROR -> JBColor(Color(0xB00020), Color(0xEF9A9A))
        }

        val updateLabel = Runnable {
            label.text = state.text
            label.toolTipText = state.tooltip
            label.accessibleContext.accessibleDescription = state.tooltip
            label.foreground = toneColor
            label.icon = if (state.showCriticalIcon) criticalIcon else null
            label.iconTextGap = 4
            label.revalidate()
            label.repaint()
        }
        if (ApplicationManager.getApplication().isDispatchThread) {
            updateLabel.run()
        } else {
            ApplicationManager.getApplication().invokeLater(updateLabel)
        }
    }

    private companion object {
        // Version the key so users of the former oversized flat chooser start with compact defaults.
        const val ACCOUNT_PICKER_DIMENSION_KEY = "com.sdf.accountstatus.accountPicker.v2"
    }
}
