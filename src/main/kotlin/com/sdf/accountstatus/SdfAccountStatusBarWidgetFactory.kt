package com.sdf.accountstatus

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
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
import com.sdf.accountstatus.domain.AccountWidgetState
import com.sdf.accountstatus.domain.WidgetTone
import java.awt.Color
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.UIManager

class SdfAccountStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = WIDGET_ID

    override fun getDisplayName(): String = "SDF Account Status"

    override fun isAvailable(project: Project): Boolean = project.basePath != null

    override fun isEnabledByDefault(): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = SdfAccountStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

    companion object {
        private const val WIDGET_ID = "SdfAccountStatusWidget"
    }
}

private class SdfAccountStatusBarWidget(private val project: Project) : CustomStatusBarWidget, Disposable {
    private val label = JBLabel("loading...")
    private val criticalIcon: Icon? = UIManager.getIcon("OptionPane.errorIcon")?.let {
        IconUtil.scale(it, label, 0.7f)
    }

    private val projectJsonPath: Path? = project.basePath?.let { Path.of(it, "project.json") }
    private val resolver = SdfAccountStatusResolver(projectJsonPath)
    private val connection = project.messageBus.connect(this)
    private val pollingTask: ScheduledFuture<*>

    @Volatile
    private var lastModifiedMillis: Long = -1L

    init {
        label.font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
        label.border = JBUI.Borders.empty(0, 4)
        label.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        label.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                val path = projectJsonPath ?: return
                val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(path) ?: return
                FileEditorManager.getInstance(project).openFile(virtualFile, true)
            }
        })

        refresh()
        pollingTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            { pollProjectJsonChanges() },
            2,
            2,
            TimeUnit.SECONDS
        )

        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                val trackedPath = projectJsonPath?.toAbsolutePath()?.normalize()?.toString() ?: return
                val changedProjectJson = events.any { event ->
                    event.path.equals(trackedPath, ignoreCase = false) || event.path.endsWith("/project.json")
                }
                if (changedProjectJson) {
                    refresh()
                }
            }
        })
    }

    override fun ID(): String = "SdfAccountStatusWidget"

    override fun getComponent(): JComponent = label

    override fun dispose() {
        pollingTask.cancel(true)
        connection.dispose()
    }

    private fun refresh() {
        val state = resolver.resolve()
        lastModifiedMillis = resolver.getLastModifiedMillisOrMinusOne()
        applyState(state)
    }

    private fun pollProjectJsonChanges() {
        val newTimestamp = resolver.getLastModifiedMillisOrMinusOne()
        if (newTimestamp != lastModifiedMillis) {
            ApplicationManager.getApplication().invokeLater { refresh() }
        }
    }

    private fun applyState(state: AccountWidgetState) {
        val toneColor = when (state.tone) {
            WidgetTone.OK -> JBColor(Color(0x2E7D32), Color(0x81C784))
            WidgetTone.WARNING -> JBColor(Color(0xB58900), Color(0xFFD54F))
            WidgetTone.ERROR -> JBColor(Color(0xB00020), Color(0xEF9A9A))
        }

        ApplicationManager.getApplication().invokeLater {
            label.text = state.text
            label.toolTipText = state.tooltip
            label.foreground = toneColor
            label.icon = if (state.showCriticalIcon) criticalIcon else null
            label.iconTextGap = 4
            label.revalidate()
            label.repaint()
        }
    }
}
