package com.sdf.accountstatus

import com.sdf.accountstatus.core.SdfAuthentication
import com.sdf.accountstatus.domain.AccountEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountPickerModelTest {
    @Test
    fun `promotes current and recommended accounts without duplicates`() {
        val model = AccountPickerModelBuilder.build(
            accounts = listOf(
                authentication("unrelated", "654321: Blue Sky Example LLC [Administrator]"),
                authentication("acme-current", "123456_SB2: Acme Example Corp - SB2 [Administrator]"),
                authentication("acme-sandbox", "123456_SB1: Acme Example Corp - Sandbox [Developer]"),
                authentication("acme-production", "123456: ACME Example Corp [Administrator]")
            ),
            currentAuthenticationId = "acme-current"
        )

        val promoted = model.section(AccountPickerSection.CURRENT_AND_RECOMMENDED).accounts
        val configured = model.section(AccountPickerSection.CONFIGURED).accounts

        assertEquals(
            listOf("acme-current", "acme-production", "acme-sandbox"),
            promoted.map(AccountPickerAccount::authenticationId)
        )
        assertTrue(promoted.first().isCurrent)
        assertFalse(promoted.first().isRecommended)
        assertTrue(promoted.drop(1).all(AccountPickerAccount::isRecommended))
        assertEquals(listOf("unrelated"), configured.map(AccountPickerAccount::authenticationId))
        assertEquals(
            model.accounts.size,
            model.accounts.map(AccountPickerAccount::authenticationId).distinct().size
        )
        assertFalse(model.currentAuthenticationMissing)
    }

    @Test
    fun `promotes current account even when there are no recommendations`() {
        val model = AccountPickerModelBuilder.build(
            accounts = listOf(
                authentication("current", "123456: Acme Example Corp [Administrator]"),
                authentication("other", "654321: Blue Sky Example LLC [Administrator]")
            ),
            currentAuthenticationId = "current"
        )

        assertEquals(
            listOf("current"),
            model.section(AccountPickerSection.CURRENT_AND_RECOMMENDED)
                .accounts
                .map(AccountPickerAccount::authenticationId)
        )
        assertEquals(
            listOf("other"),
            model.section(AccountPickerSection.CONFIGURED)
                .accounts
                .map(AccountPickerAccount::authenticationId)
        )
    }

    @Test
    fun `reports when configured project authentication is absent`() {
        val model = AccountPickerModelBuilder.build(
            accounts = listOf(
                authentication("available", "123456: Acme Example Corp [Administrator]")
            ),
            currentAuthenticationId = "missing-authentication"
        )

        assertTrue(model.currentAuthenticationMissing)
        assertTrue(model.section(AccountPickerSection.CURRENT_AND_RECOMMENDED).groups.isEmpty())
        assertEquals(listOf("available"), model.accounts.map(AccountPickerAccount::authenticationId))
    }

    @Test
    fun `groups normalized customer names and account environments into one family`() {
        val model = AccountPickerModelBuilder.build(
            accounts = listOf(
                authentication("production", "123456: Acme Example Corp [Administrator]"),
                authentication("sandbox", "123456_SB1: ACME EXAMPLE CORP - SB1 [Administrator]"),
                authentication("release-preview", "123456_RP: Acme_Example_Corp - Release Preview [Developer]"),
                authentication("another-family", "654321: Acme Example Corp [Administrator]")
            ),
            currentAuthenticationId = null
        )

        val groups = model.section(AccountPickerSection.CONFIGURED).groups
        assertEquals(2, groups.size)
        assertEquals("123456", groups[0].accountFamily)
        assertEquals("Acme Example Corp", groups[0].customerName)
        assertEquals("123456 · Acme Example Corp", groups[0].displayName)
        assertEquals(
            listOf("production", "sandbox", "release-preview"),
            groups[0].accounts.map(AccountPickerAccount::authenticationId)
        )
        assertEquals(
            listOf(
                AccountEnvironment.PRODUCTION,
                AccountEnvironment.SANDBOX,
                AccountEnvironment.RELEASE_PREVIEW
            ),
            groups[0].accounts.map(AccountPickerAccount::environment)
        )
        assertEquals("654321", groups[1].accountFamily)
        assertEquals("654321 · Acme Example Corp", groups[1].displayName)
    }

    @Test
    fun `exposes combined groups directly under each section`() {
        val model = AccountPickerModelBuilder.build(
            accounts = listOf(
                authentication("current", "123456_SB1: Acme Example Corp [Administrator]"),
                authentication("production", "123456: Acme Example Corp [Administrator]"),
                authentication("configured", "654321: Blue Sky Example LLC [Developer]")
            ),
            currentAuthenticationId = "current"
        )

        val recommendedGroups =
            model.section(AccountPickerSection.CURRENT_AND_RECOMMENDED).groups
        val configuredGroups = model.section(AccountPickerSection.CONFIGURED).groups

        assertEquals(
            listOf("123456 · Acme Example Corp"),
            recommendedGroups.map(AccountPickerGroup::displayName)
        )
        assertEquals(
            listOf("current", "production"),
            recommendedGroups.single().accounts.map(AccountPickerAccount::authenticationId)
        )
        assertEquals(
            listOf("654321 · Blue Sky Example LLC"),
            configuredGroups.map(AccountPickerGroup::displayName)
        )
        assertEquals(
            listOf("configured"),
            configuredGroups.single().accounts.map(AccountPickerAccount::authenticationId)
        )
    }

    @Test
    fun `orders leaves by current environment and authentication id deterministically`() {
        val model = AccountPickerModelBuilder.build(
            accounts = listOf(
                authentication("z-sandbox", "123456_SB1: Acme Example Corp [Administrator]"),
                authentication("z-production", "123456: Acme Example Corp [Administrator]"),
                authentication("a-production", "123456: Acme Example Corp [Developer]"),
                authentication("release-preview", "123456_RP: Acme Example Corp [Administrator]"),
                authentication("current-sandbox", "123456_SB2: Acme Example Corp [Administrator]")
            ),
            currentAuthenticationId = "current-sandbox"
        )

        assertEquals(
            listOf(
                "current-sandbox",
                "a-production",
                "z-production",
                "z-sandbox",
                "release-preview"
            ),
            model.section(AccountPickerSection.CURRENT_AND_RECOMMENDED)
                .groups
                .single()
                .accounts
                .map(AccountPickerAccount::authenticationId)
        )
    }

    @Test
    fun `filters every displayed field and requires all query tokens`() {
        val model = AccountPickerModelBuilder.build(
            accounts = listOf(
                authentication("acme-sandbox-admin", "123456_SB1: Acme Example Corp [Administrator]"),
                authentication("acme-production-dev", "123456: Acme Example Corp [Developer]"),
                authentication("blue-release", "654321_RP: Blue Sky Example LLC [Release Manager]"),
                authentication("training-viewer", "TSTDRV0000000: Green Field Example Ltd [Viewer]")
            ),
            currentAuthenticationId = null
        )

        assertEquals(
            listOf("acme-sandbox-admin"),
            model.filtered("acme sandbox administrator").ids()
        )
        assertEquals(
            listOf("acme-production-dev"),
            model.filtered("123456 production developer").ids()
        )
        assertEquals(
            listOf("blue-release"),
            model.filtered("blue sky 654321 rp release manager").ids()
        )
        assertEquals(listOf("training-viewer"), model.filtered("green unverified viewer").ids())
        assertEquals(listOf("training-viewer"), model.filtered("green unknown viewer").ids())
        assertTrue(model.filtered("acme sandbox developer").accounts.isEmpty())
        assertEquals(model, model.filtered("  "))
    }

    @Test
    fun `combines query environment and exact role without conflating account identities`() {
        val model = AccountPickerModelBuilder.build(
            accounts = listOf(
                authentication("sandbox-admin", "123456_SB1: Acme Example Corp [Administrator]"),
                authentication("sandbox-dev", "123456_SB1: Acme Example Corp [Developer]"),
                authentication("production-dev", "123456: Acme Example Corp [Developer]"),
                authentication("other-dev", "654321_SB1: Other Example Corp [Developer]")
            ),
            currentAuthenticationId = "sandbox-admin"
        )

        val filters = AccountPickerFilterState("acme", AccountEnvironment.SANDBOX, "Developer")
        val filtered = model.filtered(filters)

        assertEquals(listOf("sandbox-dev"), filtered.ids())
        assertTrue(filtered.accounts.single().isRecommended)
        assertEquals("sandbox-admin", filtered.currentAuthenticationId)
        assertFalse(filtered.currentAuthenticationMissing)
        assertEquals(
            listOf("production-dev", "sandbox-dev"),
            model.filtered(filters.copy(environment = null)).ids()
        )
        assertEquals(
            listOf("sandbox-admin", "sandbox-dev"),
            model.filtered(filters.copy(role = null)).ids()
        )
        assertTrue(model.filtered(filters.copy(role = "Develop")).accounts.isEmpty())
        assertEquals(4, model.accounts.size)
    }

    @Test
    fun `environment constraints use account metadata and never authentication name hints`() {
        val model = AccountPickerModelBuilder.build(
            accounts = listOf(
                authentication("sandbox-developer", "123456: Acme Example Corp [Developer]"),
                authentication("production-admin", "123456_SB1: Acme Example Corp [Administrator]"),
                authentication("prod-sandbox-unknown", "TSTDRV0000000: Training [Viewer]"),
                authentication("sb-missing-details", ""),
                authentication("sandbox-preview", "123456_RP: Acme Example Corp [Viewer]")
            ),
            currentAuthenticationId = null
        )

        assertEquals(
            listOf("sandbox-developer"),
            model.filtered(AccountPickerFilterState(environment = AccountEnvironment.PRODUCTION)).ids()
        )
        assertEquals(
            listOf("production-admin"),
            model.filtered(AccountPickerFilterState(environment = AccountEnvironment.SANDBOX)).ids()
        )
        assertEquals(
            setOf("prod-sandbox-unknown", "sb-missing-details"),
            model.filtered(AccountPickerFilterState(environment = AccountEnvironment.UNKNOWN)).ids().toSet()
        )
        assertEquals(
            listOf("sandbox-preview"),
            model.filtered(AccountPickerFilterState(environment = AccountEnvironment.RELEASE_PREVIEW)).ids()
        )
    }

    @Test
    fun `role choices are unique complete and sorted while matching groups retain their order`() {
        val model = AccountPickerModelBuilder.build(
            accounts = listOf(
                authentication("current", "123456_SB1: Acme Example Corp [Developer]"),
                authentication("acme-production", "123456: Acme Example Corp [Developer]"),
                authentication("blue-admin", "654321: Blue Example Corp [Administrator]"),
                authentication("blue-dev", "654321_SB1: Blue Example Corp [Developer]"),
                authentication("unknown", "")
            ),
            currentAuthenticationId = "current"
        )

        assertEquals(listOf("Administrator", "Developer"), model.availableRoles)
        val filtered = model.filtered(AccountPickerFilterState(role = "Developer"))
        assertEquals(listOf("current", "acme-production", "blue-dev"), filtered.ids())
        assertEquals(listOf("Administrator", "Developer"), model.availableRoles)
        assertTrue(filtered.accounts.first().isCurrent)
        assertTrue(filtered.accounts[1].isRecommended)
        assertFalse(filtered.accounts.last().isRecommended)
        val noMatches = model.filtered(AccountPickerFilterState("no such customer"))
        assertTrue(noMatches.accounts.isEmpty())
        assertTrue(noMatches.sections.all { it.groups.isEmpty() })
        assertEquals(model, model.filtered(AccountPickerFilterState()))
    }

    private fun AccountPickerModel.section(section: AccountPickerSection) =
        sections.single { it.kind == section }

    private fun AccountPickerModel.ids() = accounts.map(AccountPickerAccount::authenticationId)

    private fun authentication(id: String, details: String) = SdfAuthentication(
        authenticationId = id,
        details = details
    )
}
