package com.sdf.accountstatus

import com.intellij.openapi.progress.ProcessCanceledException
import com.sdf.accountstatus.core.SdfAuthListLoadResult
import com.sdf.accountstatus.core.SdfAuthentication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ConfiguredAccountProviderTest {
    private val nodeAccounts = SdfAuthListLoadResult.Available(listOf(SdfAuthentication("same-id", "1234567: Node Company [Developer]")))
    private val legacyAccounts = SdfAuthListLoadResult.Available(listOf(SdfAuthentication("same-id", "7654321: Legacy Company [Administrator]")))

    @Test
    fun `Node account progress reaches the caller without exposing account metadata`() {
        val progress = mutableListOf<AccountLoadingProgress>()
        val provider = ConfiguredAccountProvider(SdfAccountPreferences(), { _, report ->
            report(AccountLoadingProgress(0, 2))
            report(AccountLoadingProgress(1, 2))
            report(AccountLoadingProgress(2, 2))
            nodeAccounts
        })
        assertEquals(nodeAccounts, provider.load(progress::add))
        assertEquals(listOf(0, 1, 2), progress.map { it.completed })
        assertEquals(listOf(2, 2, 2), progress.map { it.total })
    }

    @Test
    fun `default Node selection returns only Node accounts even when authentication IDs match`() {
        var legacyCalls = 0
        val provider = ConfiguredAccountProvider(SdfAccountPreferences(), { _, _ -> nodeAccounts }, { legacyCalls++; legacyAccounts })
        assertEquals(nodeAccounts, provider.load())
        assertEquals(0, legacyCalls)
    }

    @Test
    fun `Node failure identifies provider and never tries legacy`() {
        var legacyCalls = 0
        val provider = ConfiguredAccountProvider(SdfAccountPreferences(), { _, _ -> SdfAuthListLoadResult.Unavailable("Missing runtime.") }, {
            legacyCalls++
            legacyAccounts
        })
        assertEquals(SdfAuthListLoadResult.Unavailable("Node.js CLI: Missing runtime."), provider.load())
        assertEquals(0, legacyCalls)
    }

    @Test
    fun `explicit provider changes affect the next load without mixing cached accounts`() {
        val preferences = SdfAccountPreferences()
        var nodeCalls = 0
        var legacyCalls = 0
        val provider = ConfiguredAccountProvider(preferences, { _, _ -> nodeCalls++; nodeAccounts }, { legacyCalls++; legacyAccounts })
        assertEquals(nodeAccounts, provider.load())
        preferences.providerConfiguration = AccountProviderConfiguration(AccountProviderKind.LEGACY_JAVA, "missing-node")
        assertEquals(legacyAccounts, provider.load())
        preferences.providerConfiguration = AccountProviderConfiguration()
        assertEquals(nodeAccounts, provider.load())
        assertEquals(2, nodeCalls)
        assertEquals(1, legacyCalls)
    }

    @Test
    fun `invalid saved Node paths return guidance without invoking either provider`() {
        val preferences = SdfAccountPreferences()
        preferences.providerConfiguration = AccountProviderConfiguration(nodeExecutable = "relative-path")
        var calls = 0
        val provider = ConfiguredAccountProvider(preferences, { _, _ -> calls++; nodeAccounts }, { calls++; legacyAccounts })
        val result = assertIs<SdfAuthListLoadResult.Unavailable>(provider.load())
        assertEquals("Node.js CLI: Enter an absolute path or leave this field blank for automatic detection. Check Account provider settings.", result.message)
        assertEquals(0, calls)
    }

    @Test
    fun `legacy errors remain explicit and cancellation is not converted into a fallback`() {
        val preferences = SdfAccountPreferences()
        preferences.providerConfiguration = AccountProviderConfiguration(AccountProviderKind.LEGACY_JAVA)
        val provider = ConfiguredAccountProvider(preferences, { _, _ -> error("Node must not be called") }, { SdfAuthListLoadResult.Unavailable("SDK missing.") })
        assertEquals(SdfAuthListLoadResult.Unavailable("Java CLI (legacy): SDK missing."), provider.load())
        preferences.providerConfiguration = AccountProviderConfiguration()
        assertFailsWith<ProcessCanceledException> {
            ConfiguredAccountProvider(preferences, { _, _ -> throw ProcessCanceledException() }, { error("No fallback on cancellation") }).load()
        }
    }
}
