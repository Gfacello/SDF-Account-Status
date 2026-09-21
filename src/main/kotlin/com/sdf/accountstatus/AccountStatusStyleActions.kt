package com.sdf.accountstatus

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

/** Each selection persists before the caller renders the current account again. */
internal class AccountStatusStyleActions(
    private val preferences: SdfAccountPreferences,
    private val onChanged: () -> Unit
) {
    fun group(): DefaultActionGroup = DefaultActionGroup("Status display", true).apply {
        add(styleAction("Authentication ID", StatusDisplayStyle.AUTHENTICATION_ID))
        add(styleAction("Account details", StatusDisplayStyle.ACCOUNT_DETAILS))
    }

    private fun styleAction(text: String, style: StatusDisplayStyle): ToggleAction =
        object : ToggleAction(text), DumbAware {
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

            override fun isSelected(event: AnActionEvent): Boolean = preferences.displayStyle == style

            override fun setSelected(event: AnActionEvent, state: Boolean) {
                // Selecting the checked option must not leave the menu without a display style.
                if (!state || preferences.displayStyle == style) return
                preferences.displayStyle = style
                onChanged()
            }
        }
}
