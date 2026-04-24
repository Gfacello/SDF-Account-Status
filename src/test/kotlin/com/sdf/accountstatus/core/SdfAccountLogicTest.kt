package com.sdf.accountstatus.core

import com.sdf.accountstatus.domain.AccountEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SdfAccountLogicTest {
    @Test
    fun `parses defaultAuthId`() {
        val json = """{"defaultAuthId":"5860676_SB3-Adm-Sand"}"""
        assertEquals("5860676_SB3-Adm-Sand", SdfProjectJsonParser.parseDefaultAuthId(json))
    }

    @Test
    fun `parses legacy DefaultAuthID`() {
        val json = """{"DefaultAuthID":"5860676-Adm-Prod"}"""
        assertEquals("5860676-Adm-Prod", SdfProjectJsonParser.parseDefaultAuthId(json))
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
            SdfAccountEnvironmentClassifier.classify("5860676_SB3-Adm-Sand")
        )
    }

    @Test
    fun `classifies production account`() {
        assertEquals(
            AccountEnvironment.PRODUCTION,
            SdfAccountEnvironmentClassifier.classify("5860676-Adm-Prod")
        )
    }

    @Test
    fun `classifies unknown account`() {
        assertEquals(
            AccountEnvironment.UNKNOWN,
            SdfAccountEnvironmentClassifier.classify("5860676-Admin")
        )
    }
}
