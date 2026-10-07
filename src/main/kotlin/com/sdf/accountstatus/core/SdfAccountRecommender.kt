package com.sdf.accountstatus.core

import com.sdf.accountstatus.domain.AccountEnvironment
import java.util.Locale

/** Finds accounts that are meaningfully related to the currently selected authentication ID. */
internal object SdfAccountRecommender {
    private const val DEFAULT_LIMIT = 5

    fun recommend(
        accounts: List<SdfAuthentication>,
        currentAuthenticationId: String?,
        limit: Int = DEFAULT_LIMIT
    ): List<SdfAuthentication> {
        if (currentAuthenticationId.isNullOrBlank() || limit <= 0) return emptyList()

        val current = accounts.firstOrNull {
            it.authenticationId == currentAuthenticationId
        } ?: return emptyList()
        val currentIdentity = current.toIdentity() ?: return emptyList()

        return accounts.asSequence()
            .filterNot { it.authenticationId == current.authenticationId }
            .mapNotNull { account ->
                val identity = account.toIdentity() ?: return@mapNotNull null
                val sameAccountFamily = identity.accountFamily == currentIdentity.accountFamily
                val sameCompany = identity.companyKey == currentIdentity.companyKey
                if (!sameAccountFamily && !sameCompany) return@mapNotNull null

                RecommendationCandidate(
                    account = account,
                    accountIdKey = identity.accountIdKey,
                    sameAccountFamily = sameAccountFamily,
                    sameCompany = sameCompany,
                    sameRole = identity.roleKey == currentIdentity.roleKey,
                    environment = identity.environment,
                    complementaryEnvironment = identity.environment != AccountEnvironment.UNKNOWN &&
                        currentIdentity.environment != AccountEnvironment.UNKNOWN &&
                        identity.environment != currentIdentity.environment,
                    differentAccountId = identity.accountIdKey != currentIdentity.accountIdKey
                )
            }
            .sortedWith(
                compareByDescending<RecommendationCandidate> { it.sameAccountFamily }
                    .thenByDescending { it.complementaryEnvironment }
                    .thenByDescending { it.differentAccountId }
                    .thenByDescending { it.sameCompany }
                    .thenByDescending { it.sameRole }
                    .thenBy { environmentOrder.getValue(it.environment) }
                    .thenBy { it.account.authenticationId.lowercase(Locale.ROOT) }
                    .thenBy { it.account.authenticationId }
            )
            .toList()
            .let(::prioritizeDistinctAccounts)
            .take(limit)
            .map(RecommendationCandidate::account)
    }

    private fun prioritizeDistinctAccounts(
        candidates: List<RecommendationCandidate>
    ): List<RecommendationCandidate> {
        val firstByAccountId = linkedMapOf<String, RecommendationCandidate>()
        candidates.forEach { candidate -> firstByAccountId.putIfAbsent(candidate.accountIdKey, candidate) }
        val firstCandidates = firstByAccountId.values.toSet()
        return firstByAccountId.values + candidates.filterNot(firstCandidates::contains)
    }

    private fun SdfAuthentication.toIdentity(): AccountIdentity? {
        val parsed = accountDetails ?: return null
        val accountFamily = SdfAccountIdentityNormalizer.normalizedAccountFamily(parsed.accountId)
        val accountIdKey = parsed.accountId.uppercase(Locale.ROOT)
        val companyKey = SdfAccountIdentityNormalizer.normalizedCustomer(parsed.accountName)
        val roleKey = parsed.role.lowercase(Locale.ROOT)
        if (accountFamily.isEmpty() || companyKey.isEmpty()) return null

        return AccountIdentity(
            accountFamily = accountFamily,
            accountIdKey = accountIdKey,
            companyKey = companyKey,
            roleKey = roleKey,
            environment = SdfAccountEnvironmentClassifier.classifyAccountId(parsed.accountId)
        )
    }

    private data class AccountIdentity(
        val accountFamily: String,
        val accountIdKey: String,
        val companyKey: String,
        val roleKey: String,
        val environment: AccountEnvironment
    )

    private data class RecommendationCandidate(
        val account: SdfAuthentication,
        val accountIdKey: String,
        val sameAccountFamily: Boolean,
        val sameCompany: Boolean,
        val sameRole: Boolean,
        val environment: AccountEnvironment,
        val complementaryEnvironment: Boolean,
        val differentAccountId: Boolean
    )

    private val environmentOrder = mapOf(
        AccountEnvironment.PRODUCTION to 0,
        AccountEnvironment.SANDBOX to 1,
        AccountEnvironment.RELEASE_PREVIEW to 2,
        AccountEnvironment.UNKNOWN to 3
    )
}

/** Shared normalization keeps recommendation and picker grouping behavior identical. */
internal object SdfAccountIdentityNormalizer {
    private val nonProductionAccountSuffix = Regex("_(?:SB|RP)\\d*$", RegexOption.IGNORE_CASE)
    private val companyEnvironmentSuffix = Regex(
        "(?:\\s*[-_]\\s*(?:SB\\d*|SANDBOX|DEV|PRE[-_\\s]*PROD|RP\\d*|RELEASE[-_\\s]*PREVIEW))+$",
        RegexOption.IGNORE_CASE
    )
    private val companySeparator = Regex("[^\\p{L}\\p{N}]+")

    fun accountFamily(accountId: String): String =
        nonProductionAccountSuffix.replace(accountId.trim(), "").trim()

    fun normalizedAccountFamily(accountId: String): String =
        accountFamily(accountId).uppercase(Locale.ROOT)

    fun customerDisplayName(accountName: String): String =
        companyEnvironmentSuffix.replace(accountName.trim(), "").trim()

    fun normalizedCustomer(accountName: String): String = companySeparator.replace(
        customerDisplayName(accountName),
        " "
    ).trim().lowercase(Locale.ROOT)
}
