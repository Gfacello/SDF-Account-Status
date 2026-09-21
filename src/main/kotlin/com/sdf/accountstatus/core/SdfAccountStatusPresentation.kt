package com.sdf.accountstatus.core

import com.sdf.accountstatus.domain.AccountEnvironment
import com.sdf.accountstatus.domain.AccountWidgetState
import com.sdf.accountstatus.domain.WidgetTone

/** One presentation path for disk refresh, account loading and successful switching. */
internal object SdfAccountStatusPresentation {
    fun present(snapshot: ProjectJsonSnapshot, authentication: SdfAuthentication? = null): AccountWidgetState {
        snapshot.problem?.let { return it }
        val id = requireNotNull(snapshot.authenticationId)
        val metadata = authentication?.takeIf { it.authenticationId == id }
        val environment = metadata?.accountDetails?.accountId
            ?.let(SdfAccountEnvironmentClassifier::classifyAccountId) ?: AccountEnvironment.UNKNOWN
        return forAuthentication(id, environment)
    }

    internal fun forAuthentication(id: String, environment: AccountEnvironment): AccountWidgetState =
        AccountWidgetState(
            text = id,
            tooltip = "Current SDF default account ($id) - ${environment.label}. Click to choose an account",
            tone = when (environment) {
                AccountEnvironment.SANDBOX -> WidgetTone.OK
                AccountEnvironment.PRODUCTION -> WidgetTone.ERROR
                AccountEnvironment.RELEASE_PREVIEW, AccountEnvironment.UNKNOWN -> WidgetTone.WARNING
            },
            showCriticalIcon = environment == AccountEnvironment.PRODUCTION
        )
}
