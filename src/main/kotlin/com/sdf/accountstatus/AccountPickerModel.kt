package com.sdf.accountstatus

import com.sdf.accountstatus.core.SdfAccountEnvironmentClassifier
import com.sdf.accountstatus.core.SdfAccountIdentityNormalizer
import com.sdf.accountstatus.core.SdfAccountRecommender
import com.sdf.accountstatus.core.SdfAuthentication
import com.sdf.accountstatus.domain.AccountEnvironment
import java.util.Locale

internal object AccountPickerModelBuilder {
    fun build(
        accounts: List<SdfAuthentication>,
        currentAuthenticationId: String?
    ): AccountPickerModel {
        val uniqueAccounts = accounts.distinctBy(SdfAuthentication::authenticationId)
        val current = uniqueAccounts.firstOrNull {
            it.authenticationId == currentAuthenticationId
        }
        val recommendations = SdfAccountRecommender.recommend(
            uniqueAccounts,
            currentAuthenticationId
        )
        val promotedIds = buildSet {
            current?.let { add(it.authenticationId) }
            recommendations.forEach { add(it.authenticationId) }
        }

        val promotedAccounts = buildList {
            current?.let {
                add(it.toPickerAccount(isCurrent = true))
            }
            recommendations.forEach {
                add(it.toPickerAccount(isRecommended = true))
            }
        }
        val configuredAccounts = uniqueAccounts
            .asSequence()
            .filterNot { it.authenticationId in promotedIds }
            .map { it.toPickerAccount() }
            .toList()

        return AccountPickerModel(
            sections = listOf(
                AccountPickerSectionModel(
                    kind = AccountPickerSection.CURRENT_AND_RECOMMENDED,
                    groups = promotedAccounts.toGroups()
                ),
                AccountPickerSectionModel(
                    kind = AccountPickerSection.CONFIGURED,
                    groups = configuredAccounts.toGroups()
                )
            ),
            currentAuthenticationId = currentAuthenticationId,
            currentAuthenticationMissing = !currentAuthenticationId.isNullOrBlank() && current == null
        )
    }

    private fun SdfAuthentication.toPickerAccount(
        isCurrent: Boolean = false,
        isRecommended: Boolean = false
    ): AccountPickerAccount {
        val environment = accountDetails?.accountId
            ?.let(SdfAccountEnvironmentClassifier::classifyAccountId)
            ?: AccountEnvironment.UNKNOWN
        return AccountPickerAccount(
            authentication = this,
            environment = environment,
            isCurrent = isCurrent,
            isRecommended = isRecommended
        )
    }

    private fun List<AccountPickerAccount>.toGroups(): List<AccountPickerGroup> =
        groupBy { account ->
            AccountPickerGroupKey(
                customer = SdfAccountIdentityNormalizer.normalizedCustomer(
                    account.accountName
                ),
                accountFamily = SdfAccountIdentityNormalizer.normalizedAccountFamily(
                    account.accountId
                )
            )
        }
            .map { (key, groupedAccounts) ->
                val sortedAccounts = groupedAccounts.sortedWith(ACCOUNT_COMPARATOR)
                AccountPickerGroup(
                    customerName = sortedAccounts
                        .asSequence()
                        .map(AccountPickerAccount::accountName)
                        .map(SdfAccountIdentityNormalizer::customerDisplayName)
                        .firstOrNull(String::isNotBlank)
                        ?: UNKNOWN_CUSTOMER,
                    accountFamily = sortedAccounts
                        .asSequence()
                        .map(AccountPickerAccount::accountId)
                        .map(SdfAccountIdentityNormalizer::accountFamily)
                        .firstOrNull(String::isNotBlank)
                        ?: UNKNOWN_ACCOUNT,
                    accounts = sortedAccounts,
                    normalizedCustomer = key.customer,
                    normalizedAccountFamily = key.accountFamily
                )
            }
            .sortedWith(GROUP_COMPARATOR)

    private val ACCOUNT_COMPARATOR =
        compareByDescending<AccountPickerAccount>(AccountPickerAccount::isCurrent)
            .thenBy { ENVIRONMENT_ORDER.getValue(it.environment) }
            .thenBy { it.authentication.authenticationId.lowercase(Locale.ROOT) }
            .thenBy { it.authentication.authenticationId }

    private val GROUP_COMPARATOR =
        compareByDescending<AccountPickerGroup> { group ->
            group.accounts.any(AccountPickerAccount::isCurrent)
        }
            .thenBy { it.customerName.lowercase(Locale.ROOT) }
            .thenBy { it.customerName }
            .thenBy { it.accountFamily.lowercase(Locale.ROOT) }
            .thenBy { it.accountFamily }

    private val ENVIRONMENT_ORDER = mapOf(
        AccountEnvironment.PRODUCTION to 0,
        AccountEnvironment.SANDBOX to 1,
        AccountEnvironment.RELEASE_PREVIEW to 2,
        AccountEnvironment.UNKNOWN to 3
    )

    private const val UNKNOWN_CUSTOMER = "Account details unavailable"
    private const val UNKNOWN_ACCOUNT = "Unknown account"
}

internal data class AccountPickerModel(
    val sections: List<AccountPickerSectionModel>,
    val currentAuthenticationId: String?,
    val currentAuthenticationMissing: Boolean
) {
    val accounts: List<AccountPickerAccount>
        get() = sections.flatMap { section ->
            section.groups.flatMap(AccountPickerGroup::accounts)
        }

    /** Read from the complete model so other active constraints never hide a role choice. */
    val availableRoles: List<String>
        get() = accounts.map(AccountPickerAccount::role)
            .filter(String::isNotBlank)
            .distinct()
            .sortedWith(compareBy<String> { it.lowercase(Locale.ROOT) }.thenBy { it })

    /**
     * Returns the same hierarchy with only matching account leaves retained.
     * Every query token must occur in one of the account's searchable fields.
     */
    fun filtered(query: String): AccountPickerModel = filtered(AccountPickerFilterState(query = query))

    fun filtered(filters: AccountPickerFilterState): AccountPickerModel {
        val tokens = SEARCH_TOKEN_SEPARATOR.split(filters.query.lowercase(Locale.ROOT))
            .filter(String::isNotBlank)
        if (tokens.isEmpty() && filters.environment == null && filters.role == null) return this

        return copy(
            sections = sections.map { section ->
                section.copy(
                    groups = section.groups.mapNotNull { group ->
                        val matches = group.accounts.filter { account ->
                            tokens.all(account.normalizedSearchText::contains) &&
                                (filters.environment == null || account.environment == filters.environment) &&
                                (filters.role == null || account.role == filters.role)
                        }
                        group.takeIf { matches.isNotEmpty() }?.copy(accounts = matches)
                    }
                )
            }
        )
    }

    private companion object {
        val SEARCH_TOKEN_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
    }
}

/** A complete, restorable picker selection. Null environment/role mean no constraint. */
internal data class AccountPickerFilterState(
    val query: String = "",
    val environment: AccountEnvironment? = null,
    val role: String? = null
) {
    val isActive: Boolean
        get() = query.isNotBlank() || environment != null || role != null
}

internal data class AccountPickerSectionModel(
    val kind: AccountPickerSection,
    val groups: List<AccountPickerGroup>
) {
    val accounts: List<AccountPickerAccount>
        get() = groups.flatMap(AccountPickerGroup::accounts)
}

internal data class AccountPickerGroup(
    val customerName: String,
    val accountFamily: String,
    val accounts: List<AccountPickerAccount>,
    internal val normalizedCustomer: String,
    internal val normalizedAccountFamily: String
) {
    /** One compact tree label for the combined account-family/customer grouping level. */
    val displayName: String = "$accountFamily · $customerName"
}

internal data class AccountPickerAccount(
    val authentication: SdfAuthentication,
    val environment: AccountEnvironment,
    val isCurrent: Boolean,
    val isRecommended: Boolean
) {
    val authenticationId: String
        get() = authentication.authenticationId
    val accountId: String
        get() = authentication.accountDetails?.accountId.orEmpty()
    val accountName: String
        get() = authentication.accountDetails?.accountName.orEmpty()
    val role: String
        get() = authentication.accountDetails?.role.orEmpty()
    val searchText: String = listOf(
        authentication.authenticationId,
        authentication.accountDetails?.accountName.orEmpty(),
        authentication.accountDetails?.accountId.orEmpty(),
        authentication.accountDetails?.role.orEmpty(),
        environment.label,
        if (environment == AccountEnvironment.UNKNOWN) "Unknown" else ""
    ).joinToString(" ")
    internal val normalizedSearchText: String = searchText.lowercase(Locale.ROOT)
}

internal enum class AccountPickerSection(val title: String) {
    CURRENT_AND_RECOMMENDED("Current and recommended accounts"),
    CONFIGURED("Configured accounts")
}

private data class AccountPickerGroupKey(
    val customer: String,
    val accountFamily: String
)
