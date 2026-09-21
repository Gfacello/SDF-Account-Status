package com.sdf.accountstatus.core

import java.net.URI
import java.util.Locale

internal sealed interface AccountUrlValidation {
    data class Valid(val url: String) : AccountUrlValidation
    data class Invalid(val message: String) : AccountUrlValidation
}

/** Validates a user-supplied Company URLs entry; never derives a domain from an account ID. */
internal object NetSuiteAccountUrl {
    private val uiHost = Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.app\\.netsuite\\.com")

    fun validate(value: String): AccountUrlValidation {
        val input = value.trim()
        val uri = try {
            URI(input)
        } catch (_: Exception) {
            return invalid()
        }
        val host = uri.host?.lowercase(Locale.ROOT) ?: return invalid()
        if (!uri.scheme.equals("https", ignoreCase = true) || !uiHost.matches(host) ||
            uri.rawUserInfo != null || uri.port != -1 || uri.rawQuery != null || uri.rawFragment != null ||
            uri.rawPath !in listOf("", "/") || input.any(Char::isISOControl)
        ) return invalid()

        return AccountUrlValidation.Valid("https://$host/")
    }

    private fun invalid() = AccountUrlValidation.Invalid(
        "Paste the HTTPS NetSuite UI base URL from Company URLs, such as " +
            "https://1234567-sb1.app.netsuite.com. Do not include a path, query, sign-in token, or fragment."
    )
}
