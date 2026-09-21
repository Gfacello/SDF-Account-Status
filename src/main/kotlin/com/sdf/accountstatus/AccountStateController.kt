package com.sdf.accountstatus

import com.sdf.accountstatus.core.ProjectJsonSnapshot
import com.sdf.accountstatus.core.SdfAccountStatusPresentation
import com.sdf.accountstatus.core.SdfAuthListLoadResult
import com.sdf.accountstatus.core.SdfAuthentication
import com.sdf.accountstatus.domain.AccountWidgetState
import com.sdf.accountstatus.domain.WidgetTone

internal fun interface AccountProvider {
    fun load(): SdfAuthListLoadResult
}

internal sealed interface AccountListState {
    data object Loading : AccountListState
    data class Available(val accounts: List<SdfAuthentication>) : AccountListState
    data class Unavailable(val message: String) : AccountListState
}

internal data class AccountWorkflowState(
    val project: ProjectJsonSnapshot = ProjectJsonSnapshot(
        null, AccountWidgetState("Reading project.json…", "Reading project.json", WidgetTone.WARNING), Long.MIN_VALUE
    ),
    val accountList: AccountListState = AccountListState.Loading
) {
    val currentAuthenticationId: String? get() = project.authenticationId
    val currentAuthentication: SdfAuthentication? get() =
        (accountList as? AccountListState.Available)?.accounts
            ?.firstOrNull { it.authenticationId == currentAuthenticationId }
    val presentation: AccountWidgetState get() = SdfAccountStatusPresentation.present(project, currentAuthentication)
}

/**
 * State is published only on the UI thread. Worker results carry no mutable references to this
 * state, and generations are checked at delivery, after every possible superseding request.
 */
internal class AccountStateController(
    private val provider: AccountProvider,
    private val readProject: () -> ProjectJsonSnapshot,
    private val scheduler: AccountWorkflowScheduler,
    private val isUnavailable: () -> Boolean,
    private val onStateChanged: (AccountWorkflowState) -> Unit
) {
    var state = AccountWorkflowState()
        private set
    private var accountGeneration = 0
    private var projectGeneration = 0
    private var accountTask: AccountWorkflowTask? = null
    private var projectTask: AccountWorkflowTask? = null
    private var loadingAccounts = false
    @Volatile private var disposed = false

    fun loadAccounts(force: Boolean = false) {
        if (unavailable() || loadingAccounts && !force) return
        val generation = ++accountGeneration
        accountTask?.cancel()
        loadingAccounts = true
        publish(state.copy(accountList = AccountListState.Loading))
        accountTask = scheduler.background(provider::load) { result ->
            if (unavailable() || generation != accountGeneration) return@background
            loadingAccounts = false
            val accounts = when (result) {
                is SdfAuthListLoadResult.Available -> AccountListState.Available(result.accounts.toList())
                is SdfAuthListLoadResult.Unavailable -> AccountListState.Unavailable(result.message)
            }
            publish(state.copy(accountList = accounts))
        }
    }

    fun refreshProject() {
        if (unavailable()) return
        val generation = ++projectGeneration
        projectTask?.cancel()
        projectTask = scheduler.background(readProject) { snapshot ->
            if (unavailable() || generation != projectGeneration) return@background
            if (snapshot.changedWhileReading) {
                refreshProject()
                return@background
            }
            // Presentation derives from the current account list, even if it changed during this read.
            publish(state.copy(project = snapshot))
        }
    }

    fun accountSaved(account: AccountPickerAccount) {
        if (unavailable()) return
        ++projectGeneration
        projectTask?.cancel()
        publish(state.copy(project = ProjectJsonSnapshot.configured(account.authenticationId)))
        refreshProject()
    }

    fun dispose() {
        disposed = true
        accountTask?.cancel()
        projectTask?.cancel()
    }

    private fun unavailable(): Boolean = disposed || isUnavailable()
    private fun publish(value: AccountWorkflowState) {
        state = value
        onStateChanged(value)
    }
}
