package com.sdf.accountstatus

import com.intellij.openapi.progress.ProcessCanceledException
import com.sdf.accountstatus.core.AccountUrlValidation
import com.sdf.accountstatus.core.NetSuiteAccountUrl
import com.sdf.accountstatus.core.SdfAuthentication

internal data class BrowserAccountTarget(
    val authenticationId: String,
    val accountId: String,
    val accountName: String
) {
    companion object {
        fun from(authentication: SdfAuthentication?): BrowserAccountTarget? =
            authentication?.accountDetails?.takeIf { it.accountId.isNotBlank() }?.let {
                BrowserAccountTarget(authentication.authenticationId, it.accountId, it.accountName)
            }
    }
}

internal interface AccountUrlStore {
    fun accountUrl(accountId: String): String?
    fun saveAccountUrl(accountId: String, url: String)
}

internal fun interface AccountUrlPrompt {
    /** Returns null on cancel. The UI validates inline before accepting the supplied value. */
    fun request(target: BrowserAccountTarget, currentUrl: String?): String?
}

internal fun interface AccountBrowser {
    fun open(url: String)
}

/** This workflow has no project-document or account-switching capability. */
internal class AccountBrowserController(
    private val store: AccountUrlStore,
    private val prompt: AccountUrlPrompt,
    private val browser: AccountBrowser,
    private val showError: (String) -> Unit,
    private val isDisposed: () -> Boolean = { false }
) {
    fun open(target: BrowserAccountTarget?) {
        if (isDisposed()) return
        if (target == null) return missingMetadata()
        val saved = store.accountUrl(target.accountId)
        val valid = saved?.let(NetSuiteAccountUrl::validate) as? AccountUrlValidation.Valid
        val url = valid?.url ?: configure(target, saved) ?: return
        if (isDisposed()) return
        try {
            browser.open(url)
        } catch (cancelled: ProcessCanceledException) {
            throw cancelled
        } catch (_: Exception) {
            showError("Unable to open NetSuite. Check the IDE's default browser settings and try again.")
        }
    }

    fun edit(target: BrowserAccountTarget?) {
        if (isDisposed()) return
        if (target == null) return missingMetadata()
        configure(target, store.accountUrl(target.accountId))
    }

    private fun configure(target: BrowserAccountTarget, saved: String?): String? {
        val entered = prompt.request(target, saved) ?: return null
        if (isDisposed()) return null
        return when (val result = NetSuiteAccountUrl.validate(entered)) {
            is AccountUrlValidation.Invalid -> {
                showError(result.message)
                null
            }
            is AccountUrlValidation.Valid -> {
                store.saveAccountUrl(target.accountId, result.url)
                result.url
            }
        }
    }

    private fun missingMetadata() = showError(
        "Load the account list before configuring or opening a NetSuite account URL. " +
            "An authoritative account ID is required."
    )
}
