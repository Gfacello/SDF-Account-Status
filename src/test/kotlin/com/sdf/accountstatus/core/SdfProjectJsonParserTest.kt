package com.sdf.accountstatus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SdfProjectJsonParserTest {
    @Test
    fun `distinguishes configured canonical and legacy keys`() {
        val canonical = assertIs<ProjectJsonParseResult.Configured>(
            SdfProjectJsonParser.inspect("""{"defaultAuthId":"demo-sandbox"}""")
        )
        val legacy = assertIs<ProjectJsonParseResult.Configured>(
            SdfProjectJsonParser.inspect("""{"DefaultAuthID":"demo-production"}""")
        )

        assertEquals("demo-sandbox", canonical.authenticationId)
        assertEquals("defaultAuthId", canonical.key)
        assertEquals("demo-production", legacy.authenticationId)
        assertEquals("DefaultAuthID", legacy.key)
    }

    @Test
    fun `distinguishes missing key from invalid JSON`() {
        assertIs<ProjectJsonParseResult.MissingKey>(
            SdfProjectJsonParser.inspect("""{"projectName":"Example"}""")
        )
        assertIs<ProjectJsonParseResult.InvalidJson>(
            SdfProjectJsonParser.inspect("""{"projectName":}""")
        )
        assertIs<ProjectJsonParseResult.InvalidJson>(
            SdfProjectJsonParser.inspect("[]")
        )
    }

    @Test
    fun `distinguishes invalid key type from a blank authentication ID`() {
        val wrongType = assertIs<ProjectJsonParseResult.InvalidKeyType>(
            SdfProjectJsonParser.inspect("""{"defaultAuthId":42}""")
        )
        val blank = assertIs<ProjectJsonParseResult.BlankAuthenticationId>(
            SdfProjectJsonParser.inspect("""{"DefaultAuthID":"  "}""")
        )

        assertEquals("defaultAuthId", wrongType.key)
        assertEquals("DefaultAuthID", blank.key)
    }

    @Test
    fun `canonical key takes precedence when both spellings exist`() {
        val parsed = assertIs<ProjectJsonParseResult.InvalidKeyType>(
            SdfProjectJsonParser.inspect(
                """{"defaultAuthId":false,"DefaultAuthID":"legacy-auth"}"""
            )
        )

        assertEquals("defaultAuthId", parsed.key)
        assertNull(
            SdfProjectJsonParser.parseDefaultAuthId(
                """{"defaultAuthId":false,"DefaultAuthID":"legacy-auth"}"""
            )
        )
    }

    @Test
    fun `nullable compatibility helper returns null for every unavailable state`() {
        listOf(
            "{}",
            "[]",
            """{"defaultAuthId":null}""",
            """{"defaultAuthId":""}"""
        ).forEach { content ->
            assertNull(SdfProjectJsonParser.parseDefaultAuthId(content))
        }
    }
}
