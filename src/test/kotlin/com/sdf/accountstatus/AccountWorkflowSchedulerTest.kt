package com.sdf.accountstatus

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountWorkflowSchedulerTest {
    private lateinit var fixture: IdeaProjectTestFixture

    @BeforeEach
    fun setUp() {
        fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("Account scheduling").fixture
        fixture.setUp()
    }

    @AfterEach
    fun tearDown() = onEdt { fixture.tearDown() }

    @Test
    fun `work runs off EDT and completion is delivered on EDT`() {
        val workerOnEdt = AtomicBoolean(true)
        val completionOnEdt = AtomicBoolean(false)
        val received = AtomicReference<String>()
        val delivered = CountDownLatch(1)
        lateinit var task: AccountWorkflowTask
        onEdt {
            task = IntellijAccountWorkflowScheduler().background({
                workerOnEdt.set(ApplicationManager.getApplication().isDispatchThread)
                "accounts"
            }) {
                received.set(it)
                completionOnEdt.set(ApplicationManager.getApplication().isDispatchThread)
                delivered.countDown()
            }
        }
        try {
            assertTrue(delivered.await(10, TimeUnit.SECONDS), "Background result was not delivered")
            assertFalse(workerOnEdt.get())
            assertTrue(completionOnEdt.get())
            assertEquals("accounts", received.get())
        } finally {
            task.cancel()
        }
    }

    @Test
    fun `cancelling task interrupts running provider and does not publish its unfinished result`() {
        val started = CountDownLatch(1)
        val workerStopped = CountDownLatch(1)
        val blocked = CountDownLatch(1)
        val completed = AtomicBoolean(false)
        lateinit var task: AccountWorkflowTask
        onEdt {
            task = IntellijAccountWorkflowScheduler().background({
                started.countDown()
                try {
                    blocked.await()
                    "unfinished"
                } finally {
                    workerStopped.countDown()
                }
            }) { completed.set(true) }
        }
        try {
            assertTrue(started.await(10, TimeUnit.SECONDS), "Provider did not start")
            task.cancel()
            assertTrue(workerStopped.await(10, TimeUnit.SECONDS), "Provider was not interrupted")
            // Drain the UI queue after worker termination, without relying on arbitrary sleeps.
            onEdt { assertFalse(completed.get()) }
        } finally {
            task.cancel()
            blocked.countDown()
        }
    }

    private fun onEdt(action: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) action() else application.invokeAndWait(action, ModalityState.nonModal())
    }
}
