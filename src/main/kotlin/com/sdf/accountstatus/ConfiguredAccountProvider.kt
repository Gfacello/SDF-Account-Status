package com.sdf.accountstatus

import com.sdf.accountstatus.core.NodeSuiteCloudAccountProvider
import com.sdf.accountstatus.core.NodeSuiteCloudCommandResolver
import com.sdf.accountstatus.core.SdfAuthListLoadResult
import com.sdf.accountstatus.core.SuiteCloudAuthListLoader
import java.nio.file.Path

/** Each request uses exactly one provider. Failure never triggers a second authentication store. */
internal class ConfiguredAccountProvider(
    private val preferences: SdfAccountPreferences,
    private val loadNode: (AccountProviderConfiguration, (AccountLoadingProgress) -> Unit) -> SdfAuthListLoadResult = ::loadNodeAccounts,
    private val loadLegacy: () -> SdfAuthListLoadResult = { SuiteCloudAuthListLoader().load() }
) : AccountProvider {
    override fun load(): SdfAuthListLoadResult = load {}

    override fun load(onProgress: (AccountLoadingProgress) -> Unit): SdfAuthListLoadResult {
        val configuration = preferences.providerConfiguration.normalized()
        val validationError = configuration.validationError()
        if (validationError != null) {
            return SdfAuthListLoadResult.Unavailable(
                "${configuration.provider.displayName}: ${validationError.message} Check Account provider settings."
            )
        }
        return when (val result = when (configuration.provider) {
            AccountProviderKind.NODE_CLI -> loadNode(configuration, onProgress)
            AccountProviderKind.LEGACY_JAVA -> loadLegacy()
        }) {
            is SdfAuthListLoadResult.Available -> result
            is SdfAuthListLoadResult.Unavailable -> SdfAuthListLoadResult.Unavailable(
                "${configuration.provider.displayName}: ${result.message}"
            )
        }
    }
}

private fun loadNodeAccounts(
    configuration: AccountProviderConfiguration,
    onProgress: (AccountLoadingProgress) -> Unit
): SdfAuthListLoadResult =
    NodeSuiteCloudAccountProvider(resolver = {
        NodeSuiteCloudCommandResolver(
            explicitNode = configuration.nodeExecutable.takeIf(String::isNotEmpty)?.let(Path::of),
            explicitLauncher = configuration.suiteCloudLauncher.takeIf(String::isNotEmpty)?.let(Path::of)
        ).resolve()
    }).load(onProgress = { completed, total -> onProgress(AccountLoadingProgress(completed, total)) })
