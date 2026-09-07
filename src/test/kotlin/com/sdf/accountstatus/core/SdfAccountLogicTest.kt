package com.sdf.accountstatus.core

import com.sdf.accountstatus.domain.AccountEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SdfAccountLogicTest {
    @Test
    fun `parses defaultAuthId`() {
        val json = """{"defaultAuthId":"demo-sandbox"}"""
        assertEquals("demo-sandbox", SdfProjectJsonParser.parseDefaultAuthId(json))
    }

    @Test
    fun `parses legacy DefaultAuthID`() {
        val json = """{"DefaultAuthID":"demo-production"}"""
        assertEquals("demo-production", SdfProjectJsonParser.parseDefaultAuthId(json))
    }

    @Test
    fun `returns null when auth id key is missing`() {
        val json = """{"project":"demo"}"""
        assertNull(SdfProjectJsonParser.parseDefaultAuthId(json))
    }

    @Test
    fun `classifies sandbox account`() {
        assertEquals(
            AccountEnvironment.SANDBOX,
            SdfAccountEnvironmentClassifier.classify("123456_SB3-Adm-Sand")
        )
    }

    @Test
    fun `classifies production account`() {
        assertEquals(
            AccountEnvironment.PRODUCTION,
            SdfAccountEnvironmentClassifier.classify("123456-Adm-Prod")
        )
    }

    @Test
    fun `classifies release preview account`() {
        assertEquals(
            AccountEnvironment.RELEASE_PREVIEW,
            SdfAccountEnvironmentClassifier.classify("123456-RP-Administrator")
        )
    }

    @Test
    fun `classifies unknown account`() {
        assertEquals(
            AccountEnvironment.UNKNOWN,
            SdfAccountEnvironmentClassifier.classify("123456-Admin")
        )
    }

    @Test
    fun `classifies CLI account IDs without relying on auth ID naming`() {
        assertEquals(
            AccountEnvironment.SANDBOX,
            SdfAccountEnvironmentClassifier.classifyAccountId("123456_SB2")
        )
        assertEquals(
            AccountEnvironment.PRODUCTION,
            SdfAccountEnvironmentClassifier.classifyAccountId("123456")
        )
        assertEquals(
            AccountEnvironment.RELEASE_PREVIEW,
            SdfAccountEnvironmentClassifier.classifyAccountId("123456_RP")
        )
        assertEquals(
            AccountEnvironment.UNKNOWN,
            SdfAccountEnvironmentClassifier.classifyAccountId("TSTDRV0000000")
        )
    }

    @Test
    fun `normalizes account families and customer environment suffixes`() {
        assertEquals("123456", SdfAccountIdentityNormalizer.accountFamily("123456_SB12"))
        assertEquals("123456", SdfAccountIdentityNormalizer.accountFamily("123456_RP"))
        assertEquals(
            "ACME-EXAMPLE",
            SdfAccountIdentityNormalizer.normalizedAccountFamily("acme-example_SB1")
        )
        assertEquals(
            "acme example corp",
            SdfAccountIdentityNormalizer.normalizedCustomer("Acme Example Corp - Release Preview")
        )
    }
}
