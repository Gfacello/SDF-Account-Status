package com.sdf.accountstatus.core

/** Public CLI display output only; no credential formats or internal SDK APIs. */
internal object NodeSuiteCloudOutputParser {
    private const val EMPTY = "There are no authentication IDs available."
    private const val LIST_PROGRESS = "Loading the configured authentication IDs in this machine..."
    private val ansi = Regex("\\u001B(?:\\[[0-?]*[ -/]*[@-~]|[@-Z\\\\-_])")
    private val authenticationId = Regex("[A-Za-z0-9_][A-Za-z0-9_-]*")
    private val accountId = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}_-]*")
    private val fields = listOf("Authentication ID", "Account Name", "Account ID", "Role", "Domain", "Account Type")

    /** Null means an unrecognized response, never a partial list. */
    fun authenticationIds(output: String): List<String>? {
        val lines = lines(output)?.filterNot { isProgress(it, LIST_PROGRESS) } ?: return null
        if (lines.isEmpty() || lines == listOf(EMPTY)) return emptyList()
        val ids = lines.map { line ->
            val delimiter = line.indexOf(" | ")
            if (delimiter <= 0) return null
            val id = line.substring(0, delimiter)
            val summary = line.substring(delimiter + 3)
            if (!authenticationId.matches(id) || !summary.contains(" @ ")) return null
            id
        }
        return ids.takeIf { it.distinct().size == it.size }
    }

    fun account(output: String, expectedId: String): SdfAuthentication? {
        if (!authenticationId.matches(expectedId)) return null
        val progress = "Loading \"$expectedId\" authentication ID information..."
        val lines = lines(output)?.filterNot { isProgress(it, progress) } ?: return null
        if (lines.size != fields.size) return null
        val values = linkedMapOf<String, String>()
        for (line in lines) {
            val delimiter = line.indexOf(": ")
            if (delimiter <= 0) return null
            val key = line.substring(0, delimiter)
            if (key !in fields || key in values) return null
            values[key] = line.substring(delimiter + 2).trim()
        }
        if (values["Authentication ID"] != expectedId || values.values.any(String::isBlank)) return null
        if (values["Account Type"] !in setOf("Production", "Sandbox", "Release Preview")) return null
        val id = values.getValue("Account ID")
        if (!accountId.matches(id)) return null
        val metadata = SdfAuthenticationDetails(id, values.getValue("Account Name"), values.getValue("Role"))
        val details = "${metadata.accountId}: ${metadata.accountName} [${metadata.role}]"
        // Preserve separately labelled fields; roles and names may themselves contain brackets.
        return SdfAuthentication(expectedId, details, metadata)
    }

    private fun lines(output: String): List<String>? {
        val text = ansi.replace(output.removePrefix("\uFEFF"), "")
        if (text.any { it.isISOControl() && it != '\r' && it != '\n' }) return null
        return text.replace('\r', '\n').lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
    }

    private fun isProgress(line: String, message: String): Boolean =
        line == message || (line.length > 2 && line[0] in "|/-\\" && line.substring(1).trimStart() == message)
}
