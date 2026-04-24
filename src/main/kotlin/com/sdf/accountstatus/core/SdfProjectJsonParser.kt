package com.sdf.accountstatus.core

object SdfProjectJsonParser {
    private val defaultAuthIdRegex = Regex("\"(?:defaultAuthId|DefaultAuthID)\"\\s*:\\s*\"([^\"]+)\"")

    fun parseDefaultAuthId(content: String): String? {
        return defaultAuthIdRegex.find(content)?.groupValues?.getOrNull(1)
    }
}
