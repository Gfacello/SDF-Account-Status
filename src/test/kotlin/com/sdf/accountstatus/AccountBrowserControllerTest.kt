package com.sdf.accountstatus

import com.sdf.accountstatus.core.SdfAuthentication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountBrowserControllerTest {
    private val production = SdfAuthentication("example_prod", "1234567: Example Company [Administrator]")
    private val sandbox = SdfAuthentication("example_sb", "1234567_SB1: Example Company [Developer]")
    private val productionUrl = "https://1234567.app.netsuite.com/"
    private val sandboxUrl = "https://1234567-sb1.app.netsuite.com/"

    @Test
    fun `opens only the supplied target and shares URL across roles of the same account`() {
        val fixture = Fixture()
        fixture.store.saveAccountUrl("1234567", productionUrl)
        fixture.store.saveAccountUrl("1234567_SB1", sandboxUrl)
        fixture.controller.open(BrowserAccountTarget.from(sandbox))
        fixture.controller.open(BrowserAccountTarget.from(production))
        fixture.controller.open(BrowserAccountTarget.from(SdfAuthentication("example_prod_dev", "1234567: Example Company [Developer]")))
        assertEquals(listOf(sandboxUrl, productionUrl, productionUrl), fixture.opened)
        assertTrue(fixture.prompted.isEmpty())
    }

    @Test
    fun `first use identifies the selected account saves its URL and opens it`() {
        val fixture = Fixture(answer = sandboxUrl)
        val target = BrowserAccountTarget.from(sandbox)!!
        fixture.controller.open(target)
        assertEquals(target, fixture.prompted.single().first)
        assertNull(fixture.prompted.single().second)
        assertEquals(sandboxUrl, fixture.store.accountUrl("1234567_SB1"))
        assertNull(fixture.store.accountUrl("1234567"))
        assertEquals(listOf(sandboxUrl), fixture.opened)
    }

    @Test
    fun `cancel and invalid configuration neither save nor launch`() {
        for (answer in listOf(null, "https://evil.example/?secret=123")) {
            val fixture = Fixture(answer)
            fixture.controller.open(BrowserAccountTarget.from(production))
            assertTrue(fixture.opened.isEmpty())
            assertNull(fixture.store.accountUrl("1234567"))
        }
    }

    @Test
    fun `invalid persisted URL requests correction and is never launched`() {
        val fixture = Fixture(answer = productionUrl)
        fixture.store.saveAccountUrl("1234567", "https://evil.example")
        fixture.controller.open(BrowserAccountTarget.from(production))
        assertEquals("https://evil.example", fixture.prompted.single().second)
        assertEquals(listOf(productionUrl), fixture.opened)
        assertEquals(productionUrl, fixture.store.accountUrl("1234567"))
    }

    @Test
    fun `editing saves locally without opening a browser`() {
        val fixture = Fixture(answer = productionUrl)
        fixture.controller.edit(BrowserAccountTarget.from(production))
        assertEquals(productionUrl, fixture.store.accountUrl("1234567"))
        assertTrue(fixture.opened.isEmpty())
    }

    @Test
    fun `missing authoritative metadata produces guidance without prompting or launching`() {
        val fixture = Fixture()
        fixture.controller.open(BrowserAccountTarget.from(SdfAuthentication("example", "unknown")))
        assertTrue(fixture.errors.single().contains("Load the account list"))
        assertTrue(fixture.prompted.isEmpty())
        assertTrue(fixture.opened.isEmpty())
    }

    @Test
    fun `browser failure is actionable and does not expose exception contents`() {
        val fixture = Fixture(answer = productionUrl, failBrowser = true)
        fixture.controller.open(BrowserAccountTarget.from(production))
        assertTrue(fixture.errors.single().contains("browser settings"))
        assertTrue(fixture.errors.none { "private detail" in it })
        assertEquals(productionUrl, fixture.store.accountUrl("1234567"))
    }

    @Test
    fun `closing the project during URL configuration cancels all followup work`() {
        val fixture = Fixture(answer = productionUrl, disposeDuringPrompt = true)
        fixture.controller.open(BrowserAccountTarget.from(production))
        assertTrue(fixture.opened.isEmpty())
        assertNull(fixture.store.accountUrl("1234567"))
    }

    private class Fixture(answer: String? = null, failBrowser: Boolean = false, disposeDuringPrompt: Boolean = false) {
        val store = SdfAccountPreferences()
        val prompted = mutableListOf<Pair<BrowserAccountTarget, String?>>()
        val opened = mutableListOf<String>()
        val errors = mutableListOf<String>()
        var disposed = false
        val controller = AccountBrowserController(store, { target, initial ->
            prompted += target to initial
            disposed = disposeDuringPrompt
            answer
        }, { url ->
            if (failBrowser) error("private detail")
            opened += url
        }, errors::add, { disposed })
    }
}
