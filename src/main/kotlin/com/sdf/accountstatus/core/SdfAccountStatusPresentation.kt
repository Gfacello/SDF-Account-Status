package com.sdf.accountstatus.core

import com.sdf.accountstatus.StatusDisplayStyle
import com.sdf.accountstatus.domain.AccountEnvironment
import com.sdf.accountstatus.domain.AccountWidgetState
import com.sdf.accountstatus.domain.WidgetTone
import java.util.Locale

/** One presentation path for disk refresh, account loading and successful switching. */
internal object SdfAccountStatusPresentation {
    private const val ACCOUNT_NAME_LIMIT = 32
    private const val ROLE_LIMIT = 24

    fun present(
        snapshot: ProjectJsonSnapshot,
        authentication: SdfAuthentication? = null,
        style: StatusDisplayStyle = StatusDisplayStyle.AUTHENTICATION_ID
    ): AccountWidgetState {
        snapshot.problem?.let { return it }
        val id = requireNotNull(snapshot.authenticationId)
        val metadata = authentication?.takeIf { it.authenticationId == id }?.accountDetails
            ?: return forAuthentication(id, AccountEnvironment.UNKNOWN)
        val environment = SdfAccountEnvironmentClassifier.classifyAccountId(metadata.accountId)
        val marker = when (environment) {
            AccountEnvironment.SANDBOX -> metadata.accountId.substringAfterLast('_').uppercase(Locale.ROOT)
            AccountEnvironment.PRODUCTION -> "PROD"
            AccountEnvironment.RELEASE_PREVIEW -> "RP"
            AccountEnvironment.UNKNOWN -> "Unverified"
        }
        val text = when (style) {
            StatusDisplayStyle.AUTHENTICATION_ID -> id
            StatusDisplayStyle.ACCOUNT_DETAILS -> listOf(
                abbreviate(metadata.accountName, ACCOUNT_NAME_LIMIT), marker, abbreviate(metadata.role, ROLE_LIMIT)
            ).joinToString(" · ")
        }
        return state(text, environment, listOf(
            "Current SDF default account",
            "Account: ${metadata.accountName}",
            "Account ID: ${metadata.accountId}",
            "Authentication ID: $id",
            "Environment: ${environment.label}" + if (environment == AccountEnvironment.UNKNOWN) "" else " ($marker)",
            "Role: ${metadata.role}",
            "Click to choose an account"
        ))
    }

    internal fun forAuthentication(id: String, environment: AccountEnvironment): AccountWidgetState =
        state(id, environment, buildList {
            add("Current SDF default account")
            add("Authentication ID: $id")
            add("Environment: ${environment.label}")
            if (environment == AccountEnvironment.UNKNOWN) add("Account details unavailable. Refresh SuiteCloud accounts.")
            add("Click to choose an account")
        })

    private fun state(text: String, environment: AccountEnvironment, lines: List<String>): AccountWidgetState =
        AccountWidgetState(
            text = text,
            tooltip = lines.joinToString("<br>", "<html>", "</html>", transform = ::escapeHtml),
            tone = when (environment) {
                AccountEnvironment.SANDBOX -> WidgetTone.OK
                AccountEnvironment.PRODUCTION -> WidgetTone.ERROR
                AccountEnvironment.RELEASE_PREVIEW, AccountEnvironment.UNKNOWN -> WidgetTone.WARNING
            },
            showCriticalIcon = environment == AccountEnvironment.PRODUCTION,
            accessibleDescription = (lines.dropLast(1) + "Press Enter or Space to choose an account").joinToString(". ")
        )

    /** Bound long names/roles without splitting a supplementary Unicode character. */
    private fun abbreviate(value: String, limit: Int): String =
        if (value.codePointCount(0, value.length) <= limit) value
        else value.substring(0, value.offsetByCodePoints(0, limit - 1)) + "…"

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
