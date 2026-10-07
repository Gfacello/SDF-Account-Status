package com.sdf.accountstatus

import com.intellij.openapi.progress.ProcessCanceledException
import com.sdf.accountstatus.core.ProjectJsonSnapshot
import com.sdf.accountstatus.core.SdfAuthListLoadResult
import com.sdf.accountstatus.core.SdfAuthentication
import com.sdf.accountstatus.domain.AccountEnvironment
import com.sdf.accountstatus.domain.WidgetTone
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AccountStateControllerTest {
    @Test
    fun `progress waits for UI delivery and cannot overwrite completed refreshed or disposed state`() {
        val scheduler = ControlledScheduler()
        scheduler.deferLater = true
        val callbacks = mutableListOf<(AccountLoadingProgress) -> Unit>()
        val provider = object : AccountProvider {
            override fun load() = available("current", "123_SB1")
            override fun load(onProgress: (AccountLoadingProgress) -> Unit): SdfAuthListLoadResult {
                callbacks += onProgress
                onProgress(AccountLoadingProgress(0, 73))
                return load()
            }
        }
        val controller = AccountStateController(provider, { ProjectJsonSnapshot.configured("current") }, scheduler, { false }, {})
        controller.loadAccounts()
        scheduler.runWorker(0)
        assertEquals(null, assertIs<AccountListState.Loading>(controller.state.accountList).progress)
        scheduler.deliverLater()
        assertEquals(AccountLoadingProgress(0, 73), assertIs<AccountListState.Loading>(controller.state.accountList).progress)

        callbacks[0](AccountLoadingProgress(12, 73))
        controller.loadAccounts(force = true)
        scheduler.deliverLater()
        assertEquals(null, assertIs<AccountListState.Loading>(controller.state.accountList).progress)
        scheduler.runWorker(1)
        scheduler.deliver(1)
        scheduler.deliverLater()
        assertIs<AccountListState.Available>(controller.state.accountList)

        controller.loadAccounts(force = true)
        scheduler.runWorker(2)
        controller.dispose()
        scheduler.deliverLater()
        assertEquals(null, assertIs<AccountListState.Loading>(controller.state.accountList).progress)
    }

    @Test
    fun `older account completion delivered last cannot replace newer accounts or presentation`() {
        val fixture = Fixture()
        fixture.loadProject("current")
        fixture.controller.loadAccounts()
        fixture.result = available("current", "123")
        fixture.scheduler.runWorker(1)
        fixture.controller.loadAccounts(force = true)
        fixture.result = available("current", "123_SB1")
        fixture.scheduler.runWorker(2)
        fixture.scheduler.deliver(2)
        fixture.scheduler.deliver(1)
        assertEquals("123_SB1", fixture.controller.state.currentAuthentication?.accountDetails?.accountId)
        assertEquals(WidgetTone.OK, fixture.controller.state.presentation.tone)
        assertTrue(fixture.scheduler.jobs[1].cancelled)
    }

    @Test
    fun `worker completion does not publish partial state before UI delivery`() {
        val fixture = Fixture()
        fixture.loadProject("current")
        fixture.controller.loadAccounts()
        fixture.scheduler.runWorker(1)
        assertIs<AccountListState.Loading>(fixture.controller.state.accountList)
        assertEquals(WidgetTone.WARNING, fixture.controller.state.presentation.tone)
        fixture.scheduler.deliver(1)
        assertIs<AccountListState.Available>(fixture.controller.state.accountList)
        assertEquals(WidgetTone.ERROR, fixture.controller.state.presentation.tone)
    }

    @Test
    fun `ordinary overlapping load requests launch only one job`() {
        val fixture = Fixture()
        fixture.controller.loadAccounts()
        fixture.controller.loadAccounts()
        fixture.scheduler.runWorker(0)
        fixture.controller.loadAccounts()
        assertEquals(1, fixture.scheduler.jobs.size)
    }

    @Test
    fun `failed refresh discards previously verified metadata and permits retry`() {
        val fixture = Fixture()
        fixture.loadProject("current")
        fixture.controller.loadAccounts()
        fixture.scheduler.finish(1)
        fixture.controller.loadAccounts()
        fixture.result = SdfAuthListLoadResult.Unavailable("retry")
        fixture.scheduler.finish(2)
        assertEquals(null, fixture.controller.state.currentAuthentication)
        assertEquals(WidgetTone.WARNING, fixture.controller.state.presentation.tone)
        assertEquals("current", fixture.controller.state.currentAuthenticationId)
        fixture.controller.loadAccounts()
        fixture.result = available("current", "123_SB1")
        fixture.scheduler.finish(3)
        assertEquals(WidgetTone.OK, fixture.controller.state.presentation.tone)
    }

    @Test
    fun `unexpected provider failure leaves loading with a safe message and permits ordinary retry`() {
        val fixture = Fixture()
        fixture.loadProject("current")
        fixture.failure = IllegalStateException("private authentication detail")
        fixture.controller.loadAccounts()
        fixture.scheduler.runWorker(1)
        assertIs<AccountListState.Loading>(fixture.controller.state.accountList)
        fixture.scheduler.deliver(1)
        val unavailable = assertIs<AccountListState.Unavailable>(fixture.controller.state.accountList)
        assertFalse(unavailable.message.contains("private authentication detail"))
        assertTrue(unavailable.message.contains("Refresh"))
        assertEquals("current", fixture.controller.state.currentAuthenticationId)

        fixture.failure = null
        fixture.result = available("current", "123_SB1")
        fixture.controller.loadAccounts()
        assertEquals(3, fixture.scheduler.jobs.size)
        fixture.scheduler.finish(2)
        assertIs<AccountListState.Available>(fixture.controller.state.accountList)
        assertEquals("123_SB1", fixture.controller.state.currentAuthentication?.accountDetails?.accountId)
    }

    @Test
    fun `late failed provider cannot replace the accounts from a newer request`() {
        val fixture = Fixture()
        fixture.failure = IllegalArgumentException("private authentication detail")
        fixture.controller.loadAccounts()
        fixture.scheduler.runWorker(0)
        fixture.controller.loadAccounts(force = true)
        fixture.failure = null
        fixture.result = available("current", "123_SB2")
        fixture.scheduler.finish(1)
        val publicationCount = fixture.published.size
        fixture.scheduler.deliver(0)
        val accounts = assertIs<AccountListState.Available>(fixture.controller.state.accountList)
        assertEquals("123_SB2", accounts.accounts.single().accountDetails?.accountId)
        assertEquals(publicationCount, fixture.published.size)
    }

    @Test
    fun `provider cancellation is not converted into an account error`() {
        for (cancelled in listOf(ProcessCanceledException(), CancellationException())) {
            val fixture = Fixture()
            fixture.failure = cancelled
            fixture.controller.loadAccounts()
            assertSame(cancelled, assertFailsWith<RuntimeException> { fixture.scheduler.runWorker(0) })
            assertIs<AccountListState.Loading>(fixture.controller.state.accountList)
            assertEquals(1, fixture.published.size)
        }
    }

    @Test
    fun `provider interruption preserves the thread interruption flag`() {
        val fixture = Fixture()
        val interrupted = InterruptedException()
        fixture.failure = interrupted
        fixture.controller.loadAccounts()
        try {
            assertSame(interrupted, assertFailsWith<InterruptedException> { fixture.scheduler.runWorker(0) })
            assertTrue(Thread.currentThread().isInterrupted)
            assertEquals(1, fixture.published.size)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `JVM errors are not converted into an account error`() {
        val fixture = Fixture()
        val error = OutOfMemoryError("synthetic JVM error")
        fixture.failure = error
        fixture.controller.loadAccounts()
        assertSame(error, assertFailsWith<OutOfMemoryError> { fixture.scheduler.runWorker(0) })
        assertEquals(1, fixture.published.size)
    }

    @Test
    fun `disposal cancels both jobs and drops already queued callbacks`() {
        val fixture = Fixture()
        fixture.controller.refreshProject()
        fixture.controller.loadAccounts()
        fixture.scheduler.runWorker(0)
        fixture.scheduler.runWorker(1)
        val publicationCount = fixture.published.size
        fixture.controller.dispose()
        fixture.scheduler.deliver(0)
        fixture.scheduler.deliver(1)
        assertTrue(fixture.scheduler.jobs.all { it.cancelled })
        assertEquals(publicationCount, fixture.published.size)
    }

    @Test
    fun `project disposal before delivery prevents updates`() {
        val fixture = Fixture()
        fixture.controller.refreshProject()
        fixture.scheduler.runWorker(0)
        fixture.unavailable = true
        fixture.scheduler.deliver(0)
        assertTrue(fixture.published.isEmpty())
    }

    @Test
    fun `external edit updates current marker and removes other accounts metadata`() {
        val fixture = Fixture()
        fixture.loadProject("current")
        fixture.controller.loadAccounts()
        fixture.scheduler.finish(1)
        fixture.loadProject("unknown")
        assertEquals("unknown", fixture.controller.state.presentation.text)
        assertEquals(null, fixture.controller.state.currentAuthentication)
        val accounts = (fixture.controller.state.accountList as AccountListState.Available).accounts
        val picker = AccountPickerModelBuilder.build(accounts, fixture.controller.state.currentAuthenticationId)
        assertEquals("unknown", picker.currentAuthenticationId)
        assertTrue(picker.currentAuthenticationMissing)
        assertTrue(picker.accounts.none { it.isCurrent })
    }

    @Test
    fun `late project snapshot uses latest account metadata when published`() {
        val fixture = Fixture()
        fixture.controller.refreshProject()
        fixture.scheduler.runWorker(0)
        fixture.controller.loadAccounts()
        fixture.result = available("current", "123_SB2")
        fixture.scheduler.finish(1)
        fixture.scheduler.deliver(0)
        assertEquals(WidgetTone.OK, fixture.controller.state.presentation.tone)
    }

    @Test
    fun `older project completion cannot restore a superseded default or marker`() {
        val fixture = Fixture()
        fixture.snapshot = ProjectJsonSnapshot.configured("old", 1)
        fixture.controller.refreshProject()
        fixture.scheduler.runWorker(0)
        fixture.snapshot = ProjectJsonSnapshot.configured("new", 2)
        fixture.controller.refreshProject()
        fixture.scheduler.finish(1)
        fixture.scheduler.deliver(0)
        assertEquals("new", fixture.controller.state.currentAuthenticationId)
        assertEquals("new", fixture.controller.state.presentation.text)
        assertEquals(2, fixture.controller.state.project.lastModifiedMillis)
        assertEquals(1, fixture.published.size)
    }

    @Test
    fun `published account list is independent of provider collection reuse`() {
        val fixture = Fixture()
        val accounts = mutableListOf(SdfAuthentication("current", "123_SB1: Acme [Developer]"))
        fixture.result = SdfAuthListLoadResult.Available(accounts)
        fixture.loadProject("current")
        fixture.controller.loadAccounts()
        fixture.scheduler.finish(1)
        accounts.clear()
        assertEquals("123_SB1", fixture.controller.state.currentAuthentication?.accountDetails?.accountId)
        assertEquals(WidgetTone.OK, fixture.controller.state.presentation.tone)
    }

    @Test
    fun `snapshot changed while reading is retried without publishing mixed state`() {
        val fixture = Fixture()
        fixture.snapshot = ProjectJsonSnapshot.configured("old", 1).copy(changedWhileReading = true)
        fixture.controller.refreshProject()
        fixture.scheduler.finish(0)
        assertTrue(fixture.published.isEmpty())
        fixture.snapshot = ProjectJsonSnapshot.configured("new", 2)
        fixture.scheduler.finish(1)
        assertEquals("new", fixture.controller.state.currentAuthenticationId)
        assertEquals(2, fixture.controller.state.project.lastModifiedMillis)
    }

    @Test
    fun `successful persistence invalidates a queued old snapshot`() {
        val fixture = Fixture()
        fixture.controller.refreshProject()
        fixture.scheduler.runWorker(0)
        val selected = AccountPickerAccount(
            SdfAuthentication("saved", "123_SB1: Acme [Developer]"), AccountEnvironment.SANDBOX, false, false
        )
        fixture.controller.accountSaved(selected)
        fixture.scheduler.deliver(0)
        assertEquals("saved", fixture.controller.state.currentAuthenticationId)
        fixture.snapshot = ProjectJsonSnapshot.configured("saved")
        fixture.scheduler.finish(1)
        assertEquals("saved", fixture.controller.state.presentation.text)
    }

    private class Fixture {
        val scheduler = ControlledScheduler()
        var unavailable = false
        var result: SdfAuthListLoadResult = available("current", "123")
        var failure: Throwable? = null
        var snapshot = ProjectJsonSnapshot.configured("current", 1)
        val published = mutableListOf<AccountWorkflowState>()
        val controller = AccountStateController(
            { failure?.let { throw it }; result }, { snapshot }, scheduler, { unavailable }, published::add
        )
        fun loadProject(id: String) {
            snapshot = ProjectJsonSnapshot.configured(id, 1)
            controller.refreshProject()
            scheduler.finish(scheduler.jobs.lastIndex)
        }
    }

    private class ControlledScheduler : AccountWorkflowScheduler {
        class Job(val work: () -> Unit, val deliver: () -> Unit, var cancelled: Boolean = false)
        val jobs = mutableListOf<Job>()
        var deferLater = false
        private val laterActions = mutableListOf<() -> Unit>()
        override fun later(action: () -> Unit) {
            if (deferLater) laterActions.add(action) else action()
        }
        fun deliverLater() {
            val actions = laterActions.toList()
            laterActions.clear()
            actions.forEach { it() }
        }
        override fun <T> background(work: () -> T, completed: (T) -> Unit): AccountWorkflowTask {
            var result: Any? = null
            val job = Job({ result = work() }, { @Suppress("UNCHECKED_CAST") completed(result as T) })
            jobs.add(job)
            return AccountWorkflowTask { job.cancelled = true }
        }
        // Intentionally allow cancelled jobs to complete: providers can ignore interruption.
        fun runWorker(index: Int) = jobs[index].work()
        fun deliver(index: Int) = jobs[index].deliver()
        fun finish(index: Int) { runWorker(index); deliver(index) }
    }

    private companion object {
        fun available(id: String, accountId: String) = SdfAuthListLoadResult.Available(
            listOf(SdfAuthentication(id, "$accountId: Acme [Administrator]"))
        )
    }
}
