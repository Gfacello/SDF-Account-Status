package com.sdf.accountstatus

/** Captures all filters before a production confirmation closes the originating picker. */
internal class AccountPickerSelectionUi(
    private val panel: AccountPickerPanel,
    private val isPickerOpen: () -> Boolean,
    private val close: () -> Unit,
    private val confirm: (AccountPickerAccount) -> Boolean,
    private val reopen: (AccountPickerFilterState) -> Unit,
    private val showDetachedError: (String) -> Unit
) : AccountSelectionUi {
    private val filters = panel.filterState()

    override fun closePicker() = close()
    override fun confirmProduction(account: AccountPickerAccount): Boolean = confirm(account)
    override fun restorePicker() = reopen(filters)
    override fun showError(message: String) {
        if (isPickerOpen()) {
            panel.setAccountActivationInProgress(false)
            panel.showOperationError(message)
        } else {
            showDetachedError(message)
        }
    }
}
