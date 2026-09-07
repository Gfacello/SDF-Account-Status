package com.sdf.accountstatus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SdfAuthListParserTest {
    @Test
    fun `parses current unbulleted CLI output`() {
        val output = """
            The following authentication IDs are available for your account:
            sandbox-admin | 123456_SB1: Example Company [Administrator]
            production-developer | 123456: Example Company [Developer]
        """.trimIndent()

        val result = assertIs<SdfAuthListParseResult.Success>(SdfAuthListParser.parse(output))

        assertEquals(2, result.accounts.size)
        assertEquals("sandbox-admin", result.accounts[0].authenticationId)
        assertEquals("123456_SB1: Example Company [Administrator]", result.accounts[0].details)
        assertEquals("123456_SB1", result.accounts[0].accountDetails?.accountId)
        assertEquals("Example Company", result.accounts[0].accountDetails?.accountName)
        assertEquals("Administrator", result.accounts[0].accountDetails?.role)
        assertTrue(result.accounts[0].searchableText.startsWith("Example Company"))
        assertTrue(result.accounts[0].searchableText.contains("sandbox admin"))
        assertEquals("production-developer", result.accounts[1].authenticationId)
        assertEquals("123456: Example Company [Developer]", result.accounts[1].details)
    }

    @Test
    fun `parses documented legacy bulleted CLI output`() {
        val output = """
            The following authentication IDs are available for your account:
            - sandbox-admin | Example Sandbox - 123456_SB1 [Administrator]
            - production-developer | Example Company - 123456 [Developer]
        """.trimIndent()

        val result = assertIs<SdfAuthListParseResult.Success>(SdfAuthListParser.parse(output))

        assertEquals(2, result.accounts.size)
        assertEquals("sandbox-admin", result.accounts[0].authenticationId)
        assertEquals("Example Sandbox - 123456_SB1 [Administrator]", result.accounts[0].details)
        assertEquals("123456_SB1", result.accounts[0].accountDetails?.accountId)
        assertEquals("Example Sandbox", result.accounts[0].accountDetails?.accountName)
        assertEquals("production-developer", result.accounts[1].authenticationId)
        assertEquals("Example Company - 123456 [Developer]", result.accounts[1].details)
    }

    @Test
    fun `splits an account row only on the first pipe`() {
        val output = """
            The following authentication IDs are available for your account:
            sandbox-admin | 123456_SB1: Example | Holdings [Administrator]
        """.trimIndent()

        val result = assertIs<SdfAuthListParseResult.Success>(SdfAuthListParser.parse(output))

        assertEquals("sandbox-admin", result.accounts.single().authenticationId)
        assertEquals(
            "123456_SB1: Example | Holdings [Administrator]",
            result.accounts.single().details
        )
    }

    @Test
    fun `returns empty for the exact no authentication IDs output`() {
        val result = SdfAuthListParser.parse("There are no authentication IDs available.")

        assertIs<SdfAuthListParseResult.Empty>(result)

        val resultWithHeader = SdfAuthListParser.parse(
            "The following authentication IDs are available for your account:\r\n" +
                "There are no authentication IDs available.\r\n"
        )
        assertIs<SdfAuthListParseResult.Empty>(resultWithHeader)
    }

    @Test
    fun `rejects malformed rows instead of returning a partial account list`() {
        val output = """
            The following authentication IDs are available for your account:
            sandbox-admin | 123456_SB1: Example Company [Administrator]
            this row has no account separator
        """.trimIndent()

        val result = SdfAuthListParser.parse(output)

        assertIs<SdfAuthListParseResult.Malformed>(result)
    }

    @Test
    fun `rejects rows without account and role details`() {
        val invalidRows = listOf(
            "sandbox-admin | account details without a role",
            "sandbox-admin | account details [Administrator]"
        )

        invalidRows.forEach { row ->
            val output = """
                The following authentication IDs are available for your account:
                $row
            """.trimIndent()
            assertIs<SdfAuthListParseResult.Malformed>(SdfAuthListParser.parse(output))
        }
    }

    @Test
    fun `rejects a header without rows and duplicate authentication IDs`() {
        assertIs<SdfAuthListParseResult.Malformed>(
            SdfAuthListParser.parse("The following authentication IDs are available for your account:")
        )

        val duplicateOutput = """
            The following authentication IDs are available for your account:
            sandbox-admin | 123456_SB1: Example Company [Administrator]
            sandbox-admin | 123456_SB1: Example Company [Developer]
        """.trimIndent()
        assertIs<SdfAuthListParseResult.Malformed>(SdfAuthListParser.parse(duplicateOutput))
    }

    @Test
    fun `rejects blank and unexpected output`() {
        assertIs<SdfAuthListParseResult.Malformed>(SdfAuthListParser.parse("  \r\n\t"))
        assertIs<SdfAuthListParseResult.Malformed>(
            SdfAuthListParser.parse("SuiteCloud returned a new undocumented response")
        )
    }
}
