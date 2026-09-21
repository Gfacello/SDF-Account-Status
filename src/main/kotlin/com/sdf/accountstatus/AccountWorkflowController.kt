package com.sdf.accountstatus

import com.sdf.accountstatus.core.ProjectJsonUpdateResult
import com.sdf.accountstatus.domain.AccountEnvironment

/** UI effects for a selection. The restore closure retains the originating picker's state. */
internal interface AccountSelectionUi {
    fun closePicker()
    fun confirmProduction(account: AccountPickerAccount): Boolean
    fun restorePicker()
    fun showError(message: String)
}

internal fun interface ProjectJsonWriter {
    fun update(authenticationId: String): ProjectJsonUpdateResult
}

/** Account-selection policy independent of popup construction and IntelliJ document storage. */
internal class AccountWorkflowController(
    private val writer: ProjectJsonWriter,
    private val currentAuthenticationId: () -> String?,
    private val isUnavailable: () -> Boolean,
    private val later: (() -> Unit) -> Unit,
    private val onSaved: (AccountPickerAccount) -> Unit
) {
    private var selectionGeneration = 0
    private var disposed = false

    fun selectAccount(account: AccountPickerAccount, ui: AccountSelectionUi) {
        if (unavailable()) return
        val generation = ++selectionGeneration
        // A row's cached isCurrent marker can be stale after an external edit.
        if (account.authenticationId == currentAuthenticationId()) {
            ui.closePicker()
            return
        }
        if (account.environment == AccountEnvironment.PRODUCTION) {
            ui.closePicker()
            later {
                if (unavailable() || generation != selectionGeneration) return@later
                val confirmed = ui.confirmProduction(account)
                if (unavailable() || generation != selectionGeneration) return@later
                if (confirmed) save(account, ui) else ui.restorePicker()
            }
        } else {
            save(account, ui)
        }
    }

    fun dispose() {
        disposed = true
        selectionGeneration++
    }

    private fun unavailable(): Boolean = disposed || isUnavailable()

    private fun save(account: AccountPickerAccount, ui: AccountSelectionUi) {
        if (unavailable()) return
        if (account.authenticationId == currentAuthenticationId()) {
            ui.closePicker()
            return
        }
        when (val result = writer.update(account.authenticationId)) {
            is ProjectJsonUpdateResult.Updated -> {
                if (unavailable()) return
                onSaved(account)
                ui.closePicker()
            }
            is ProjectJsonUpdateResult.Invalid -> ui.showError(result.message)
        }
    }
}
