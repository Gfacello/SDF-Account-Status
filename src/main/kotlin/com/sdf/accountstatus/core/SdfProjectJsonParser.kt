package com.sdf.accountstatus.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader

internal sealed interface ProjectJsonParseResult {
    data class Configured(
        val authenticationId: String,
        val key: String
    ) : ProjectJsonParseResult

    data object MissingKey : ProjectJsonParseResult

    data class InvalidKeyType(val key: String) : ProjectJsonParseResult

    data class BlankAuthenticationId(val key: String) : ProjectJsonParseResult

    data object InvalidJson : ProjectJsonParseResult
}

object SdfProjectJsonParser {
    private const val CANONICAL_KEY = "defaultAuthId"
    private const val LEGACY_KEY = "DefaultAuthID"

    /**
     * Retained for callers that only need the configured authentication ID. Use
     * [inspect] when the reason an ID is unavailable matters.
     */
    fun parseDefaultAuthId(content: String): String? =
        (inspect(content) as? ProjectJsonParseResult.Configured)?.authenticationId

    internal fun inspect(content: String): ProjectJsonParseResult {
        val root = parseRootObject(content) ?: return ProjectJsonParseResult.InvalidJson
        val key = when {
            root.has(CANONICAL_KEY) -> CANONICAL_KEY
            root.has(LEGACY_KEY) -> LEGACY_KEY
            else -> return ProjectJsonParseResult.MissingKey
        }
        val value = root.get(key)
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
            return ProjectJsonParseResult.InvalidKeyType(key)
        }

        val authenticationId = value.asString
        return if (authenticationId.isBlank()) {
            ProjectJsonParseResult.BlankAuthenticationId(key)
        } else {
            ProjectJsonParseResult.Configured(authenticationId, key)
        }
    }

    internal fun parseRootObject(content: String): JsonObject? = runCatching {
        JsonReader(StringReader(content)).use { reader ->
            reader.strictness = Strictness.STRICT
            val element = JsonParser.parseReader(reader)
            if (reader.peek() != JsonToken.END_DOCUMENT || !element.isJsonObject) return null
            element.asJsonObject
        }
    }.getOrNull()
}
