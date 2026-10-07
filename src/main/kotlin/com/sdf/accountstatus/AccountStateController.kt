package com.sdf.accountstatus

import com.intellij.openapi.progress.ProcessCanceledException
import com.sdf.accountstatus.core.ProjectJsonSnapshot
import com.sdf.accountstatus.core.SdfAccountStatusPresentation
import com.sdf.accountstatus.core.SdfAuthListLoadResult
import com.sdf.accountstatus.core.SdfAuthentication
import com.sdf.accountstatus.domain.AccountWidgetState
import com.sdf.accountstatus.domain.WidgetTone
import java.util.concurrent.CancellationException

internal fun interface AccountProvider {
    fun load(): SdfAuthListLoadResult
    fun load(onProgress: (AccountLoadingProgress) -> Unit): SdfAuthListLoadResult = load()
}

internal data class AccountLoadingProgress(val completed: Int, val total: Int) {
    init {
        require(total > 0 && completed in 0..total)
    }
}

internal sealed interface AccountListState {
    data class Loading(val progress: AccountLoadingProgress? = null) : AccountListState
    data class Available(val accounts: List<SdfAuthentication>) : AccountListState
    data class Unavailable(val message: String) : AccountListState
}

internal data class AccountWorkflowState(
    val project: ProjectJsonSnapshot = ProjectJsonSnapshot(
        null, AccountWidgetState("Reading project.json…", "Reading project.json", WidgetTone.WARNING), Long.MIN_VALUE
    ),
    val accountList: AccountListState = AccountListState.Loading()
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
        publish(state.copy(accountList = AccountListState.Loading()))
        accountTask = scheduler.background({
            loadAccountsSafely { progress ->
                scheduler.later {
                    if (!unavailable() && generation == accountGeneration && loadingAccounts) {
                        publish(state.copy(accountList = AccountListState.Loading(progress)))
                    }
                }
            }
        }) { result ->
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

    private fun loadAccountsSafely(onProgress: (AccountLoadingProgress) -> Unit): SdfAuthListLoadResult = try {
        provider.load(onProgress)
    } catch (cancelled: ProcessCanceledException) {
        throw cancelled
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        throw interrupted
    } catch (_: Exception) {
        // A failed worker must still deliver a result so the picker can leave Loading and retry.
        SdfAuthListLoadResult.Unavailable(
            "Unable to read SuiteCloud accounts. Check Account provider settings and try Refresh again."
        )
    }

    private fun unavailable(): Boolean = disposed || isUnavailable()
    private fun publish(value: AccountWorkflowState) {
        state = value
        onStateChanged(value)
    }
}
