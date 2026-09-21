package com.sdf.accountstatus

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.sdf.accountstatus.core.AccountUrlValidation
import com.sdf.accountstatus.core.NetSuiteAccountUrl
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class AccountUrlDialog(
    project: Project,
    target: BrowserAccountTarget,
    initialUrl: String?
) : DialogWrapper(project) {
    private val field = JBTextField(initialUrl.orEmpty(), 44)
    private val accountLabel = "${target.accountName} (${target.accountId})"

    init {
        title = "Set NetSuite Account URL"
        setOKButtonText("Save URL")
        field.accessibleContext.accessibleName = "NetSuite UI URL"
        init()
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, JBUI.scale(8))).apply {
        add(JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JBLabel(accountLabel))
            add(JBLabel("Copy the NetSuite UI URL from Setup > Company > Company Information > Company URLs."))
            add(JBLabel("The browser uses its existing session or normal sign-in; it does not select your CLI role."))
        }, BorderLayout.NORTH)
        add(field, BorderLayout.CENTER)
    }

    override fun getPreferredFocusedComponent(): JComponent = field

    override fun doValidate(): ValidationInfo? = when (val result = NetSuiteAccountUrl.validate(field.text)) {
        is AccountUrlValidation.Valid -> null
        is AccountUrlValidation.Invalid -> ValidationInfo(result.message, field)
    }

    fun url(): String = field.text
}
