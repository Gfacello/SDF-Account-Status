package com.sdf.accountstatus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NodeSuiteCloudOutputParserTest {
    @Test
    fun `accepts Oracle list fixture without interpreting company and role delimiters`() {
        assertEquals(
            listOf("example_sb", "example_admin"),
            NodeSuiteCloudOutputParser.authenticationIds(
                "example_sb | Developer @ Example Co | 1234567-sb1.app.netsuite.com\n" +
                    "example_admin | Admin | Support @ Example @ Group "
            )
        )
    }

    @Test
    fun `accepts empty outputs from 3_1_2 and 4_x`() {
        listOf("", "\r\n", "There are no authentication IDs available.").forEach {
            assertEquals(emptyList(), NodeSuiteCloudOutputParser.authenticationIds(it))
        }
    }

    @Test
    fun `handles ANSI CRLF and exact spinner progress`() {
        val output = "\uFEFF\u001B[33m| Loading the configured authentication IDs in this machine...\r" +
            "\u001B[0K\u001B[0mexample_sb | Developer @ Example Co\r\n"
        assertEquals(listOf("example_sb"), NodeSuiteCloudOutputParser.authenticationIds(output))
        assertEquals("example_sb", NodeSuiteCloudOutputParser.account(
            "Loading \"example_sb\" authentication ID information...\r\n\u001B[32m$INFO\u001B[0m", "example_sb"
        )?.authenticationId)
    }

    @Test
    fun `rejects errors duplicate ids empty marker mixed with accounts and unsafe ids`() {
        listOf(
            "The passkey cannot decrypt RAW_SECRET", "error\nexample_sb | Developer @ Example Co",
            "example_sb | Developer @ Example Co\nexample_sb | Administrator @ Example Co",
            "There are no authentication IDs available.\nexample_sb | Developer @ Example Co",
            "-remove | Developer @ Example Co", "example sb | Developer @ Example Co",
            "example_sb | incomplete", "example_sb\u0000 | Developer @ Example Co"
        ).forEach { assertNull(NodeSuiteCloudOutputParser.authenticationIds(it), it) }
    }

    @Test
    fun `normalizes info to existing model without losing role or company punctuation`() {
        val account = NodeSuiteCloudOutputParser.account(
            INFO.replace("Example Co", "Example: Co [Europe] | Group @ Inc").replace("Developer", "[Developer] @ ERP | Custom"),
            "example_sb"
        )!!
        assertEquals("1234567_SB1", account.accountDetails?.accountId)
        assertEquals("Example: Co [Europe] | Group @ Inc", account.accountDetails?.accountName)
        assertEquals("[Developer] @ ERP | Custom", account.accountDetails?.role)
    }

    @Test
    fun `rejects mismatched missing duplicate unknown and malformed fields`() {
        listOf(
            INFO.replace("Authentication ID: example_sb", "Authentication ID: other"),
            INFO.replace("Account ID: 1234567_SB1\n", ""),
            INFO.replace("Domain: 1234567-sb1.app.netsuite.com", "Role: Administrator"),
            INFO.replace("Domain:", "Secret:"), INFO.replace("Account Type: Sandbox", "Account Type: mystery"),
            INFO + "\nRAW_SECRET", INFO.replace("1234567_SB1", "123 4567"),
            INFO.replace("Account Name: Example Co", "Account Name: ")
        ).forEach { assertNull(NodeSuiteCloudOutputParser.account(it, "example_sb")) }
    }

    companion object {
        const val INFO = "Authentication ID: example_sb\n" +
            "Account Name: Example Co\nAccount ID: 1234567_SB1\nRole: Developer\n" +
            "Domain: 1234567-sb1.app.netsuite.com\nAccount Type: Sandbox"
    }
}
