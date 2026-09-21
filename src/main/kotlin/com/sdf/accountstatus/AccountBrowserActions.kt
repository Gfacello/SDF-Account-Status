package com.sdf.accountstatus

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

internal class AccountBrowserActions(project: Project, isDisposed: () -> Boolean) {
    private val controller = AccountBrowserController(
        store = service<SdfAccountPreferences>(),
        prompt = { target, initial ->
            AccountUrlDialog(project, target, initial).let { dialog ->
                if (dialog.showAndGet()) dialog.url() else null
            }
        },
        browser = { url -> BrowserUtil.browse(url, project) },
        showError = { message -> Messages.showErrorDialog(project, message, "Open in NetSuite") },
        isDisposed = isDisposed
    )

    /** Resolve at invocation for a status menu; capture a row target for a picker context menu. */
    fun group(target: () -> BrowserAccountTarget?): DefaultActionGroup = DefaultActionGroup().apply {
        val name = target()?.let { "${it.accountName} (${it.accountId})" } ?: "Current account"
        addSeparator(name)
        add(object : DumbAwareAction("Open in NetSuite", "Open $name in your default browser", null) {
            override fun actionPerformed(event: AnActionEvent) = controller.open(target())
        })
        add(object : DumbAwareAction("Set account URL…", "Set the NetSuite UI URL for $name", null) {
            override fun actionPerformed(event: AnActionEvent) = controller.edit(target())
        })
    }
}
