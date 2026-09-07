package com.sdf.accountstatus.core

import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

internal sealed interface ProjectJsonUpdateResult {
    data class Updated(val content: String) : ProjectJsonUpdateResult
    data class Invalid(val message: String) : ProjectJsonUpdateResult
}

internal object SdfProjectJsonUpdater {
    private const val CANONICAL_KEY = "defaultAuthId"
    private const val LEGACY_KEY = "DefaultAuthID"

    fun updateDefaultAuthId(content: String, authenticationId: String): ProjectJsonUpdateResult {
        if (authenticationId.isBlank()) {
            return ProjectJsonUpdateResult.Invalid("The selected authentication ID is invalid.")
        }
        if (SdfProjectJsonParser.parseRootObject(content) == null) {
            return ProjectJsonUpdateResult.Invalid(
                "project.json is not a valid JSON object. The file was not changed."
            )
        }

        val layout = scanRootObject(content)
            ?: return ProjectJsonUpdateResult.Invalid(
                "project.json could not be safely updated. The file was not changed."
            )
        val encodedAuthenticationId = JsonPrimitive(authenticationId).toString()
        val targetValues = layout.properties
            .filter { it.key == CANONICAL_KEY || it.key == LEGACY_KEY }
            .map(PropertyToken::valueRange)

        val updated = if (targetValues.isEmpty()) {
            insertCanonicalProperty(content, layout, encodedAuthenticationId)
        } else {
            targetValues
                .sortedByDescending(IntRange::first)
                .fold(content) { current, range ->
                    current.replaceRange(range.first, range.last + 1, encodedAuthenticationId)
                }
        }

        // This should only fail if the text scanner and strict parser disagree. In that case,
        // never hand invalid JSON to the editor/document write path.
        if (SdfProjectJsonParser.parseRootObject(updated) == null) {
            return ProjectJsonUpdateResult.Invalid(
                "project.json could not be safely updated. The file was not changed."
            )
        }
        return ProjectJsonUpdateResult.Updated(updated)
    }

    /** Kept for source compatibility with existing internal callers. */
    internal fun parseRootObject(content: String) = SdfProjectJsonParser.parseRootObject(content)

    private fun insertCanonicalProperty(
        content: String,
        layout: RootObjectLayout,
        encodedAuthenticationId: String
    ): String {
        val property = "\"$CANONICAL_KEY\""
        if (layout.properties.isEmpty()) {
            val whitespace = content.substring(layout.openBraceIndex + 1, layout.closeBraceIndex)
            if (!whitespace.contains('\n') && !whitespace.contains('\r')) {
                val replacement = if (whitespace.isEmpty()) {
                    "$property:$encodedAuthenticationId"
                } else {
                    "$whitespace$property: $encodedAuthenticationId$whitespace"
                }
                return content.replaceRange(
                    layout.openBraceIndex + 1,
                    layout.closeBraceIndex,
                    replacement
                )
            }

            val newline = preferredNewline(content)
            val closingIndent = whitespace.substringAfterLastLineBreak()
            val indentUnit = inferIndentUnit(content, layout) ?: "  "
            val insertion = "$indentUnit$property: $encodedAuthenticationId$newline$closingIndent"
            return content.replaceRange(layout.closeBraceIndex, layout.closeBraceIndex, insertion)
        }

        val lastProperty = layout.properties.last()
        val trailingWhitespace = content.substring(lastProperty.valueRange.last + 1, layout.closeBraceIndex)
        val multiline = trailingWhitespace.contains('\n') || trailingWhitespace.contains('\r')
        val insertion = if (multiline) {
            val newline = preferredNewline(content)
            val indent = content.lineIndentAt(lastProperty.keyStart)
            ",$newline$indent$property: $encodedAuthenticationId"
        } else {
            val memberSeparator = inferMemberSeparator(content, layout)
            val colonWhitespace = inferColonWhitespace(content, lastProperty)
            "$memberSeparator$property:$colonWhitespace$encodedAuthenticationId"
        }
        val insertionIndex = lastProperty.valueRange.last + 1
        return content.replaceRange(insertionIndex, insertionIndex, insertion)
    }

    private fun preferredNewline(content: String): String =
        if (content.contains("\r\n")) "\r\n" else "\n"

    private fun inferIndentUnit(content: String, layout: RootObjectLayout): String? {
        val first = layout.properties.firstOrNull() ?: return null
        return content.lineIndentAt(first.keyStart).takeIf(String::isNotEmpty)
    }

    private fun inferMemberSeparator(content: String, layout: RootObjectLayout): String {
        if (layout.properties.size >= 2) {
            val first = layout.properties[0]
            val second = layout.properties[1]
            val between = content.substring(first.valueRange.last + 1, second.keyStart)
            return if (between.startsWith(", ")) ", " else ","
        }
        return if (content.substring(layout.openBraceIndex + 1, layout.closeBraceIndex).contains(' ')) {
            ", "
        } else {
            ","
        }
    }

    private fun inferColonWhitespace(content: String, property: PropertyToken): String {
        val whitespace = content.substring(property.colonIndex + 1, property.valueRange.first)
        return whitespace.takeIf { it.isNotEmpty() && !it.contains('\n') && !it.contains('\r') } ?: ""
    }

    private fun String.lineIndentAt(index: Int): String {
        val lineStart = lastIndexOf('\n', startIndex = (index - 1).coerceAtLeast(0)) + 1
        return substring(lineStart, index).takeIf { prefix -> prefix.all(Char::isWhitespace) }.orEmpty()
    }

    private fun String.substringAfterLastLineBreak(): String {
        val newlineIndex = maxOf(lastIndexOf('\n'), lastIndexOf('\r'))
        return substring(newlineIndex + 1)
    }

    private fun scanRootObject(content: String): RootObjectLayout? = runCatching {
        var index = skipWhitespace(content, 0)
        if (content.getOrNull(index) == '\uFEFF') index = skipWhitespace(content, index + 1)
        require(content.getOrNull(index) == '{')
        val openBraceIndex = index++
        val properties = mutableListOf<PropertyToken>()

        while (true) {
            index = skipWhitespace(content, index)
            if (content.getOrNull(index) == '}') {
                return@runCatching RootObjectLayout(openBraceIndex, index, properties)
            }

            val keyStart = index
            val keyEndExclusive = scanString(content, keyStart)
            val key = JsonParser.parseString(content.substring(keyStart, keyEndExclusive)).asString
            index = skipWhitespace(content, keyEndExclusive)
            require(content.getOrNull(index) == ':')
            val colonIndex = index++
            index = skipWhitespace(content, index)
            val valueStart = index
            val valueEndExclusive = scanValue(content, valueStart)
            require(valueEndExclusive > valueStart)
            properties += PropertyToken(
                key = key,
                keyStart = keyStart,
                colonIndex = colonIndex,
                valueRange = valueStart until valueEndExclusive
            )
            index = skipWhitespace(content, valueEndExclusive)

            when (content.getOrNull(index)) {
                ',' -> index++
                '}' -> return@runCatching RootObjectLayout(openBraceIndex, index, properties)
                else -> error("Unexpected character after a JSON property")
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("Unreachable")
    }.getOrNull()

    private fun scanValue(content: String, start: Int): Int = when (content.getOrNull(start)) {
        '"' -> scanString(content, start)
        '{', '[' -> scanComposite(content, start)
        else -> {
            var index = start
            while (index < content.length && content[index] != ',' && content[index] != '}') index++
            while (index > start && content[index - 1].isWhitespace()) index--
            index
        }
    }

    private fun scanString(content: String, start: Int): Int {
        require(content.getOrNull(start) == '"')
        var index = start + 1
        while (index < content.length) {
            when (content[index]) {
                '\\' -> index += 2
                '"' -> return index + 1
                else -> index++
            }
        }
        error("Unterminated JSON string")
    }

    private fun scanComposite(content: String, start: Int): Int {
        val closingTokens = ArrayDeque<Char>()
        closingTokens.addLast(if (content[start] == '{') '}' else ']')
        var index = start + 1
        while (index < content.length) {
            when (val character = content[index]) {
                '"' -> index = scanString(content, index)
                '{' -> {
                    closingTokens.addLast('}')
                    index++
                }
                '[' -> {
                    closingTokens.addLast(']')
                    index++
                }
                '}', ']' -> {
                    require(closingTokens.removeLast() == character)
                    index++
                    if (closingTokens.isEmpty()) return index
                }
                else -> index++
            }
        }
        error("Unterminated JSON value")
    }

    private fun skipWhitespace(content: String, start: Int): Int {
        var index = start
        while (index < content.length && content[index].isWhitespace()) index++
        return index
    }

    private data class RootObjectLayout(
        val openBraceIndex: Int,
        val closeBraceIndex: Int,
        val properties: List<PropertyToken>
    )

    private data class PropertyToken(
        val key: String,
        val keyStart: Int,
        val colonIndex: Int,
        val valueRange: IntRange
    )
}
