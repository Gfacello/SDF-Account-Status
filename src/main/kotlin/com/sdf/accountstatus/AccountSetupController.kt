package com.sdf.accountstatus

import com.intellij.openapi.progress.ProcessCanceledException

/** Account setup has no project writer; choosing the new default remains a separate action. */
internal class AccountSetupController(
    private val isUnavailable: () -> Boolean,
    private val later: (() -> Unit) -> Unit,
    private val openSettings: () -> Boolean,
    private val reloadAccounts: () -> Unit,
    private val reopenPicker: (AccountPickerFilterState) -> Unit,
    private val showError: (String) -> Unit
) {
    private var opening = false

    fun start(filters: AccountPickerFilterState, closePicker: () -> Unit) {
        if (opening || isUnavailable()) return
        opening = true
        closePicker()
        later {
            try {
                if (isUnavailable()) return@later
                val opened = openSettings()
                if (isUnavailable()) return@later
                if (opened) {
                    // Authentication can persist even when the user cancels the settings dialog.
                    reloadAccounts()
                    reopenPicker(AccountPickerFilterState())
                } else {
                    reopenPicker(filters)
                }
            } catch (cancelled: ProcessCanceledException) {
                throw cancelled
            } catch (_: Exception) {
                if (!isUnavailable()) {
                    reopenPicker(filters)
                    showError("Account setup could not be opened. Use Settings | Tools | NetSuite | Account Management.")
                }
            } finally {
                opening = false
            }
        }
    }
}
