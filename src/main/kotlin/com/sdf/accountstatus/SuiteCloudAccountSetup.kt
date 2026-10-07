package com.sdf.accountstatus

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/** Uses the public platform settings API; Oracle owns authentication and credential storage. */
internal object SuiteCloudAccountSetup {
    private const val SUITECLOUD_PLUGIN_ID = "com.netsuite.ide.webstorm.app"
    private const val ACCOUNT_MANAGEMENT_ID =
        "com.netsuite.ide.webstorm.ui.ideconfiguration.accountmanagement.AccountManagementConfigurable"

    /** True when settings were shown, including cancellation after an authentication was saved. */
    fun open(project: Project): Boolean {
        if (project.isDisposed) return false
        if (!PluginManagerCore.isLoaded(PluginId.getId(SUITECLOUD_PLUGIN_ID))) {
            Messages.showInfoMessage(
                project,
                "Install or enable Oracle's SuiteCloud IDE Plug-in for WebStorm in Settings | Plugins " +
                    "to add an account. Then try Add an account again.",
                "Add an Account"
            )
            return false
        }
        val opened = try {
            ShowSettingsUtil.getInstance().showSettingsDialog(
                project,
                { configurable -> (configurable as? ConfigurableWithId)?.id == ACCOUNT_MANAGEMENT_ID },
                null
            )
            true
        } catch (_: IllegalStateException) {
            false
        }
        if (!opened && !project.isDisposed) {
            Messages.showInfoMessage(
                project,
                "NetSuite Account Management could not be opened. " +
                    "Open Settings | Tools | NetSuite | Account Management and use + to add an account.",
                "Add an Account"
            )
        }
        return opened
    }
}
