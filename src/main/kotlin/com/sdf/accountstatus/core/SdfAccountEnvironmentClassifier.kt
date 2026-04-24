package com.sdf.accountstatus.core

import com.sdf.accountstatus.domain.AccountEnvironment

object SdfAccountEnvironmentClassifier {
    private val sandboxPattern = Regex("(^|[_-])SB\\d*($|[_-])")

    fun classify(account: String): AccountEnvironment {
        val normalized = account.uppercase()

        val isSandbox = sandboxPattern.containsMatchIn(normalized) ||
            normalized.contains("SANDBOX") ||
            normalized.contains("SAND")
        if (isSandbox) return AccountEnvironment.SANDBOX

        val isProduction = normalized.contains("PROD") || normalized.contains("PRODUCTION")
        if (isProduction) return AccountEnvironment.PRODUCTION

        return AccountEnvironment.UNKNOWN
    }
}
