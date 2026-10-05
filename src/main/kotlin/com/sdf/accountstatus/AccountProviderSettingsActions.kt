package com.sdf.accountstatus

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

internal class AccountProviderSettingsActions(
    project: Project,
    private val preferences: SdfAccountPreferences,
    isDisposed: () -> Boolean,
    onApplied: () -> Unit = {}
) {
    private val controller = AccountProviderSettingsController(
        preferences = preferences,
        prompt = { initial ->
            AccountProviderSettingsDialog(project, initial).let { if (it.showAndGet()) it.configuration() else null }
        },
        showError = { Messages.showErrorDialog(project, it.message, "SuiteCloud Account Provider") },
        isUnavailable = { project.isDisposed || isDisposed() },
        onApplied = onApplied
    )

    fun group(): DefaultActionGroup = DefaultActionGroup(
        "Account provider: ${preferences.providerConfiguration.provider.displayName}", true
    ).apply {
        add(object : DumbAwareAction("Configure account provider…", "Choose the SuiteCloud account provider and local paths", null) {
            override fun actionPerformed(event: AnActionEvent) = controller.configure()
        })
    }
}
