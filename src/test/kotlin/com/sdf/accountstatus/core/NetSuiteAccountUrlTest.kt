package com.sdf.accountstatus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NetSuiteAccountUrlTest {
    @Test
    fun `accepts copied production and sandbox UI base URLs`() {
        assertEquals(AccountUrlValidation.Valid("https://1234567.app.netsuite.com/"),
            NetSuiteAccountUrl.validate(" https://1234567.app.netsuite.com "))
        assertEquals(AccountUrlValidation.Valid("https://1234567-sb1.app.netsuite.com/"),
            NetSuiteAccountUrl.validate("HTTPS://1234567-SB1.APP.NETSUITE.COM/"))
    }

    @Test
    fun `rejects non UI domains lookalikes and URLs containing session material`() {
        listOf(
            "http://1234567.app.netsuite.com", "https://netsuite.com", "https://app.netsuite.com",
            "https://1234567.restlets.api.netsuite.com", "https://1234567.app.netsuite.com.example.org",
            "https://1234567.app.netsuite.com@evil.example", "https://user:secret@1234567.app.netsuite.com",
            "https://1234567.app.netsuite.com:443", "https://1234567.app.netsuite.com/?token=secret",
            "https://1234567.app.netsuite.com/#secret", "https://1234567.app.netsuite.com/app/login/secure/enterpriselogin.nl",
            "https://1234567.app.netsuite.com/%2f%2fevil.example", "https://1234567.app.netsuite.com./",
            "https://-123.app.netsuite.com", "https://123-.app.netsuite.com", "https://one.two.app.netsuite.com",
            "https://1234567.app.netsuite.com/\nsecret", "javascript:alert(1)", "", "not a URL"
        ).forEach { assertIs<AccountUrlValidation.Invalid>(NetSuiteAccountUrl.validate(it), it) }
    }
}
