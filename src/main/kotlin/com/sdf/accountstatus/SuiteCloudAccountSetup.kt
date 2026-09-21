package com.sdf.accountstatus

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/** Opens Oracle's account setup UI through the platform settings API, without accessing credentials. */
internal object SuiteCloudAccountSetup {
    private const val SUITECLOUD_PLUGIN_ID = "com.netsuite.ide.webstorm.app"
    // This is the instance class registered by Oracle's applicationConfigurable extension.
    private const val ACCOUNT_MANAGEMENT_ID =
        "com.netsuite.ide.webstorm.ui.ideconfiguration.accountmanagement.AccountManagementConfigurable"

    /** Returns after the settings dialog closes; authentication may persist even if it was cancelled. */
    fun open(project: Project): Boolean {
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
            // The settings API throws if a plugin update removes or renames this configurable.
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
