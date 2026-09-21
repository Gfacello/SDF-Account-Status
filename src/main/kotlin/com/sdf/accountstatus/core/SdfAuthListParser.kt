package com.sdf.accountstatus.core

internal data class SdfAuthentication(
    val authenticationId: String,
    val details: String,
    val accountDetails: SdfAuthenticationDetails? = SdfAuthenticationDetailsParser.parse(details)
) {
    /** Company name comes first so IntelliJ's speed search matches names as naturally as IDs. */
    val searchableText: String = accountDetails?.let { parsed ->
        listOf(
            parsed.accountName,
            parsed.accountId,
            authenticationId,
            parsed.role,
            details,
            authenticationId.replace(SEARCH_TOKEN_SEPARATOR, " "),
            details.replace(SEARCH_TOKEN_SEPARATOR, " ")
        ).joinToString(" ")
    } ?: "$authenticationId $details"

    private companion object {
        val SEARCH_TOKEN_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
    }
}

internal data class SdfAuthenticationDetails(
    val accountId: String,
    val accountName: String,
    val role: String
)

internal object SdfAuthenticationDetailsParser {
    private val accountIdPattern = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}_-]*")

    fun parse(details: String): SdfAuthenticationDetails? {
        if (details.isBlank() || details.any(Char::isISOControl)) return null

        val roleStart = details.lastIndexOf('[')
        val role = if (roleStart > 0 && details.endsWith(']')) {
            details.substring(roleStart + 1, details.lastIndex).trim()
        } else {
            ""
        }
        if (role.isEmpty() || role.any { it == '[' || it == ']' }) return null

        val accountDetails = details.substring(0, roleStart).trim()
        val currentFormatSeparator = accountDetails.indexOf(':')
        val accountId: String
        val accountName: String
        if (currentFormatSeparator > 0) {
            accountId = accountDetails.substring(0, currentFormatSeparator).trim()
            accountName = accountDetails.substring(currentFormatSeparator + 1).trim()
        } else {
            val legacyFormatSeparator = accountDetails.lastIndexOf(" - ")
            if (legacyFormatSeparator <= 0) return null
            accountName = accountDetails.substring(0, legacyFormatSeparator).trim()
            accountId = accountDetails.substring(legacyFormatSeparator + 3).trim()
        }

        if (!accountIdPattern.matches(accountId) || accountName.isEmpty()) return null
        return SdfAuthenticationDetails(accountId, accountName, role)
    }
}

internal sealed interface SdfAuthListParseResult {
    data class Success(val accounts: List<SdfAuthentication>) : SdfAuthListParseResult
    data object Empty : SdfAuthListParseResult
    data object Malformed : SdfAuthListParseResult
}

/** Parses only the documented, display-safe output of `sdfcli manageauth -list`. */
internal object SdfAuthListParser {
    private const val HEADER = "The following authentication IDs are available for your account:"
    private const val EMPTY_MESSAGE = "There are no authentication IDs available."
    private val ansiEscape = Regex("\\u001B(?:[@-Z\\\\-_]|\\[[0-?]*[ -/]*[@-~])")

    fun parse(output: String): SdfAuthListParseResult {
        val lines = ansiEscape.replace(output.removePrefix("\uFEFF"), "")
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()

        if (lines == listOf(EMPTY_MESSAGE)) {
            return SdfAuthListParseResult.Empty
        }

        if (lines.firstOrNull() != HEADER) {
            return SdfAuthListParseResult.Malformed
        }

        val rows = lines.drop(1)
        if (rows == listOf(EMPTY_MESSAGE)) {
            return SdfAuthListParseResult.Empty
        }
        if (rows.isEmpty() || rows.any { it == EMPTY_MESSAGE }) {
            return SdfAuthListParseResult.Malformed
        }

        val accounts = rows.map { parseRow(it) ?: return SdfAuthListParseResult.Malformed }
        if (accounts.map(SdfAuthentication::authenticationId).distinct().size != accounts.size) {
            return SdfAuthListParseResult.Malformed
        }

        return SdfAuthListParseResult.Success(accounts)
    }

    private fun parseRow(line: String): SdfAuthentication? {
        val row = if (line.startsWith("- ")) line.drop(2).trimStart() else line
        val separatorIndex = row.indexOf('|')
        if (separatorIndex <= 0 || separatorIndex == row.lastIndex) return null

        val authenticationId = row.substring(0, separatorIndex).trim()
        val details = row.substring(separatorIndex + 1).trim()
        if (authenticationId.isEmpty() || authenticationId.any(Char::isISOControl)) return null
        if (SdfAuthenticationDetailsParser.parse(details) == null) return null

        return SdfAuthentication(authenticationId, details)
    }
}
