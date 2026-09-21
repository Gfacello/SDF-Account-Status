package com.sdf.accountstatus.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SdfAccountRecommenderTest {
    @Test
    fun `recommends sibling environments in the same account family`() {
        val accounts = listOf(
            authentication("unrelated", "654321: Blue Sky Example LLC [Administrator]"),
            authentication("acme-sb2", "123456_SB2: Acme Example Corp [Administrator]"),
            authentication("acme-sb1", "123456_SB1: Acme Example Corp [Administrator]"),
            authentication("acme-prod", "123456: Acme Example Corp [Administrator]")
        )

        val recommendations = SdfAccountRecommender.recommend(accounts, "acme-sb2")

        assertEquals(
            listOf("acme-prod", "acme-sb1"),
            recommendations.map(SdfAuthentication::authenticationId)
        )
    }

    @Test
    fun `ranks account family and complementary environments above company-only matches`() {
        val accounts = listOf(
            authentication("current", "123456_SB2: Example Company [Administrator]"),
            authentication("same-company-role", "999999: Example Company [Administrator]"),
            authentication("same-family-other-company", "123456_SB1: Renamed Company [Developer]"),
            authentication("same-family-and-company-other-role", "123456: Example Company [Developer]"),
            authentication("same-family-and-company-role", "123456_SB3: Example Company [Administrator]")
        )

        val recommendations = SdfAccountRecommender.recommend(accounts, "current")

        assertEquals(
            listOf(
                "same-family-and-company-other-role",
                "same-family-and-company-role",
                "same-family-other-company",
                "same-company-role"
            ),
            recommendations.map(SdfAuthentication::authenticationId)
        )
    }

    @Test
    fun `normalizes company punctuation case and environment suffixes`() {
        val accounts = listOf(
            authentication("current", "123456_SB1: Example Company, Inc. - SB1 [Administrator]"),
            authentication("company-match", "987654: EXAMPLE COMPANY INC [Administrator]"),
            authentication("different", "111111: Example Company Holdings [Administrator]")
        )

        val recommendations = SdfAccountRecommender.recommend(accounts, "current")

        assertEquals(listOf("company-match"), recommendations.map(SdfAuthentication::authenticationId))
    }

    @Test
    fun `supports legacy CLI account details`() {
        val accounts = listOf(
            authentication("current", "Example Company - 123456_SB2 [Administrator]"),
            authentication("production", "Example Company - 123456 [Administrator]"),
            authentication("unrelated", "Other Company - 654321 [Administrator]")
        )

        val recommendations = SdfAccountRecommender.recommend(accounts, "current")

        assertEquals(listOf("production"), recommendations.map(SdfAuthentication::authenticationId))
    }

    @Test
    fun `recommends sandbox and release preview siblings from production`() {
        val accounts = listOf(
            authentication("current", "123456: Example Company [Administrator]"),
            authentication("sandbox", "123456_sb1: Renamed Sandbox Company [Administrator]"),
            authentication("release-preview", "123456_RP: Release Preview Company [Administrator]")
        )

        val recommendations = SdfAccountRecommender.recommend(accounts, "current")

        assertEquals(
            listOf("sandbox", "release-preview"),
            recommendations.map(SdfAuthentication::authenticationId)
        )
    }

    @Test
    fun `recommends Oracle suffix variants in the same family despite different customer names`() {
        val accounts = listOf(
            authentication("current", "123456: Original Company [Administrator]"),
            authentication("sandbox", "123456_SB: Sandbox Company [Administrator]"),
            authentication("release-preview", "123456_RP2: Preview Company [Administrator]"),
            authentication("unrelated", "999999_RP2: Other Company [Administrator]")
        )
        assertEquals(
            listOf("sandbox", "release-preview"),
            SdfAccountRecommender.recommend(accounts, "current").map(SdfAuthentication::authenticationId)
        )
    }

    @Test
    fun `returns deterministic authentication ID ordering and observes the limit`() {
        val accounts = listOf(
            authentication("current", "123456_SB3: Example Company [Administrator]"),
            authentication("z-last", "123456_SB2: Example Company [Administrator]"),
            authentication("A-first", "123456: Example Company [Administrator]"),
            authentication("b-middle", "123456_SB1: Example Company [Administrator]")
        )

        val recommendations = SdfAccountRecommender.recommend(accounts, "current", limit = 2)

        assertEquals(
            listOf("A-first", "b-middle"),
            recommendations.map(SdfAuthentication::authenticationId)
        )
    }

    @Test
    fun `prioritizes distinct sibling accounts before duplicate authentication aliases`() {
        val accounts = listOf(
            authentication("current", "123456: Example Company [Administrator]"),
            authentication("a-sb1-admin", "123456_SB1: Example Company [Administrator]"),
            authentication("b-sb1-developer", "123456_SB1: Example Company [Developer]"),
            authentication("c-sb1-viewer", "123456_SB1: Example Company [Viewer]"),
            authentication("z-sb2-admin", "123456_SB2: Example Company [Administrator]")
        )

        val recommendations = SdfAccountRecommender.recommend(accounts, "current", limit = 2)

        assertEquals(
            listOf("a-sb1-admin", "z-sb2-admin"),
            recommendations.map(SdfAuthentication::authenticationId)
        )
    }

    @Test
    fun `returns no recommendations when current authentication is unavailable`() {
        val accounts = listOf(
            authentication("available", "123456: Example Company [Administrator]")
        )

        assertEquals(emptyList(), SdfAccountRecommender.recommend(accounts, null))
        assertEquals(emptyList(), SdfAccountRecommender.recommend(accounts, ""))
        assertEquals(emptyList(), SdfAccountRecommender.recommend(accounts, "missing"))
        assertEquals(emptyList(), SdfAccountRecommender.recommend(accounts, "available", limit = 0))
    }

    private fun authentication(id: String, details: String) = SdfAuthentication(
        authenticationId = id,
        details = details
    )
}
