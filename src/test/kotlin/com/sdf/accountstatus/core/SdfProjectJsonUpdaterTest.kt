package com.sdf.accountstatus.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SdfProjectJsonUpdaterTest {
    @Test
    fun `updates only the canonical value token and preserves exact formatting`() {
        val original = "{\r\n" +
            "\t\"defaultAuthId\"  :  \"old-auth\",\r\n" +
            "\t\"nested\": { \"description\": \"defaultAuthId: old-auth\" },\r\n" +
            "\t\"enabled\": true\r\n" +
            "}\r\n"
        val expected = original.replaceFirst("\"old-auth\"", "\"new-auth\"")

        val result = assertIs<ProjectJsonUpdateResult.Updated>(
            SdfProjectJsonUpdater.updateDefaultAuthId(original, "new-auth")
        )

        assertEquals(expected, result.content)
    }

    @Test
    fun `updates legacy DefaultAuthID without changing its spelling or adding canonical key`() {
        val original = """
            {
              "DefaultAuthID" : "old-legacy-auth",
              "projectName": "Legacy example"
            }
        """.trimIndent()
        val expected = original.replace("\"old-legacy-auth\"", "\"new-legacy-auth\"")

        val result = assertIs<ProjectJsonUpdateResult.Updated>(
            SdfProjectJsonUpdater.updateDefaultAuthId(original, "new-legacy-auth")
        )

        assertEquals(expected, result.content)
        assertFalse(parseObject(result.content).has("defaultAuthId"))
    }

    @Test
    fun `updates both supported key spellings in place when both exist`() {
        val original = """{"DefaultAuthID":"legacy","defaultAuthId" : "canonical","keep":7}"""

        val result = assertIs<ProjectJsonUpdateResult.Updated>(
            SdfProjectJsonUpdater.updateDefaultAuthId(original, "selected-auth")
        )

        assertEquals(
            """{"DefaultAuthID":"selected-auth","defaultAuthId" : "selected-auth","keep":7}""",
            result.content
        )
    }

    @Test
    fun `corrects a supported key whose old value has the wrong JSON type`() {
        val original = """{"defaultAuthId":null,"projectName":"Example"}"""

        val result = assertIs<ProjectJsonUpdateResult.Updated>(
            SdfProjectJsonUpdater.updateDefaultAuthId(original, "selected-auth")
        )

        assertEquals(
            """{"defaultAuthId":"selected-auth","projectName":"Example"}""",
            result.content
        )
    }

    @Test
    fun `adds canonical key to a compact object with minimal changes`() {
        val original = """{"projectName":"Example","settings":{"deploy":false}}"""

        val result = assertIs<ProjectJsonUpdateResult.Updated>(
            SdfProjectJsonUpdater.updateDefaultAuthId(original, "selected-auth")
        )

        assertEquals(
            """{"projectName":"Example","settings":{"deploy":false},"defaultAuthId":"selected-auth"}""",
            result.content
        )
    }

    @Test
    fun `adds canonical key to a multiline object using its indentation and line endings`() {
        val original = "{\r\n\t\"projectName\": \"Example\"\r\n}\r\n"

        val result = assertIs<ProjectJsonUpdateResult.Updated>(
            SdfProjectJsonUpdater.updateDefaultAuthId(original, "selected-auth")
        )

        assertEquals(
            "{\r\n\t\"projectName\": \"Example\",\r\n\t\"defaultAuthId\": \"selected-auth\"\r\n}\r\n",
            result.content
        )
    }

    @Test
    fun `adds canonical key to empty compact and multiline objects`() {
        val compact = assertIs<ProjectJsonUpdateResult.Updated>(
            SdfProjectJsonUpdater.updateDefaultAuthId("{}", "selected-auth")
        )
        val spaced = assertIs<ProjectJsonUpdateResult.Updated>(
            SdfProjectJsonUpdater.updateDefaultAuthId("{ }", "selected-auth")
        )
        val multiline = assertIs<ProjectJsonUpdateResult.Updated>(
            SdfProjectJsonUpdater.updateDefaultAuthId("{\n}\n", "selected-auth")
        )

        assertEquals("""{"defaultAuthId":"selected-auth"}""", compact.content)
        assertEquals("""{ "defaultAuthId": "selected-auth" }""", spaced.content)
        assertEquals("{\n  \"defaultAuthId\": \"selected-auth\"\n}\n", multiline.content)
    }

    @Test
    fun `escapes quotes backslashes and control characters in selected authentication ID`() {
        val original = """{"defaultAuthId":"old-auth","projectName":"Example"}"""
        val selectedAuthId = "selected\\\"auth\nline"

        val result = assertIs<ProjectJsonUpdateResult.Updated>(
            SdfProjectJsonUpdater.updateDefaultAuthId(original, selectedAuthId)
        )
        val updated = parseObject(result.content)

        assertEquals(selectedAuthId, updated["defaultAuthId"].asString)
        assertEquals("Example", updated["projectName"].asString)
        assertFalse(result.content.contains('\n'))
    }

    @Test
    fun `does not update similarly named nested properties`() {
        val original = """
            {
              "settings": {
                "defaultAuthId": "nested-auth"
              },
              "name": "Example"
            }
        """.trimIndent()

        val result = assertIs<ProjectJsonUpdateResult.Updated>(
            SdfProjectJsonUpdater.updateDefaultAuthId(original, "top-level-auth")
        )
        val updated = parseObject(result.content)

        assertEquals("nested-auth", updated["settings"].asJsonObject["defaultAuthId"].asString)
        assertEquals("top-level-auth", updated["defaultAuthId"].asString)
    }

    @Test
    fun `rejects malformed nonstandard and non-object JSON`() {
        val invalidInputs = listOf(
            """{"defaultAuthId":"old-auth","nested": }""",
            """{'defaultAuthId':'old-auth'}""",
            """{"defaultAuthId":"old-auth",}""",
            """{"defaultAuthId":"old-auth" /* comment */}""",
            """{defaultAuthId:"old-auth"}""",
            "[]",
            "null",
            ""
        )

        invalidInputs.forEach { content ->
            val result = assertIs<ProjectJsonUpdateResult.Invalid>(
                SdfProjectJsonUpdater.updateDefaultAuthId(content, "selected-auth")
            )
            assertTrue(result.message.contains("not changed"))
        }
    }

    @Test
    fun `rejects a blank selected authentication ID`() {
        assertIs<ProjectJsonUpdateResult.Invalid>(
            SdfProjectJsonUpdater.updateDefaultAuthId("{}", "   ")
        )
    }

    private fun parseObject(content: String): JsonObject = JsonParser.parseString(content).asJsonObject
}
