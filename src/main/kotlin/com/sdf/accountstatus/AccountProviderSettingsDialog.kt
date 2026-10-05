package com.sdf.accountstatus

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class AccountProviderSettingsDialog(project: Project, initial: AccountProviderConfiguration) : DialogWrapper(project) {
    private val provider = ComboBox(AccountProviderKind.entries.toTypedArray())
    private val node = JBTextField(initial.nodeExecutable, 48)
    private val launcher = JBTextField(initial.suiteCloudLauncher, 48)

    init {
        title = "SuiteCloud Account Provider"
        setOKButtonText("Apply")
        provider.selectedItem = initial.provider
        provider.accessibleContext.accessibleName = "Account provider"
        node.accessibleContext.accessibleName = "Node.js executable"
        launcher.accessibleContext.accessibleName = "SuiteCloud JavaScript entrypoint"
        provider.addActionListener { updatePathFields() }
        updatePathFields()
        init()
    }

    override fun createCenterPanel(): JComponent = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(row("Account provider", provider))
        add(row("Node.js executable (optional absolute path)", node))
        add(row("SuiteCloud JavaScript entrypoint (optional absolute path)", launcher))
        add(JBLabel("Leave paths blank to detect an existing installation. These settings apply to all open projects."))
        add(JBLabel("Paths are checked in the background when accounts are loaded. Any errors appear in the account picker."))
        add(JBLabel("Java CLI is a legacy fallback and must be selected explicitly. Provider failures never switch automatically."))
    }

    override fun getPreferredFocusedComponent(): JComponent = provider

    override fun doValidate(): ValidationInfo? = configuration().syntaxError()?.let {
        ValidationInfo(it.message, if (it.field == ProviderPathField.NODE_EXECUTABLE) node else launcher)
    }

    fun configuration(): AccountProviderConfiguration = AccountProviderConfiguration(
        provider = provider.selectedItem as AccountProviderKind,
        nodeExecutable = node.text,
        suiteCloudLauncher = launcher.text
    ).normalized()

    private fun updatePathFields() {
        val enabled = provider.selectedItem == AccountProviderKind.NODE_CLI
        node.isEnabled = enabled
        launcher.isEnabled = enabled
    }

    private fun row(text: String, component: JComponent) = JPanel(BorderLayout(0, JBUI.scale(4))).apply {
        border = JBUI.Borders.emptyBottom(12)
        add(JBLabel(text).apply { labelFor = component }, BorderLayout.NORTH)
        add(component, BorderLayout.CENTER)
    }
}
