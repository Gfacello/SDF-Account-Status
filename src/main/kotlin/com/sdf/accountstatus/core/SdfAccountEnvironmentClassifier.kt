package com.sdf.accountstatus.core

import com.sdf.accountstatus.domain.AccountEnvironment
import java.util.Locale

object SdfAccountEnvironmentClassifier {
    private val sandboxPattern = Regex("(^|[_-])SB\\d*($|[_-])")
    private val releasePreviewPattern = Regex("(^|[_-])RP($|[_-])")
    private val sandboxAccountIdPattern = Regex(".+_SB\\d*$", RegexOption.IGNORE_CASE)
    private val releasePreviewAccountIdPattern = Regex(".+_RP\\d*$", RegexOption.IGNORE_CASE)
    private val productionAccountIdPattern = Regex("\\d+")

    fun classify(account: String): AccountEnvironment {
        val normalized = account.uppercase(Locale.ROOT)

        val isSandbox = sandboxPattern.containsMatchIn(normalized) ||
            normalized.contains("SANDBOX") ||
            normalized.contains("SAND")
        if (isSandbox) return AccountEnvironment.SANDBOX

        val isReleasePreview = releasePreviewPattern.containsMatchIn(normalized) ||
            normalized.contains("RELEASE PREVIEW") ||
            normalized.contains("RELEASE-PREVIEW") ||
            normalized.contains("RELEASE_PREVIEW")
        if (isReleasePreview) return AccountEnvironment.RELEASE_PREVIEW

        val isProduction = normalized.contains("PROD") || normalized.contains("PRODUCTION")
        if (isProduction) return AccountEnvironment.PRODUCTION

        return AccountEnvironment.UNKNOWN
    }

    /** Classifies the actual account ID returned by SuiteCloud CLI, not the user-defined auth ID. */
    fun classifyAccountId(accountId: String): AccountEnvironment = when {
        sandboxAccountIdPattern.matches(accountId) -> AccountEnvironment.SANDBOX
        releasePreviewAccountIdPattern.matches(accountId) -> AccountEnvironment.RELEASE_PREVIEW
        productionAccountIdPattern.matches(accountId) -> AccountEnvironment.PRODUCTION
        else -> AccountEnvironment.UNKNOWN
    }
}
