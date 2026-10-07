package com.sdf.accountstatus

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil

internal fun interface AccountWorkflowTask {
    fun cancel()
}

/** Delivers completion on the UI thread; cancellation also interrupts running CLI processes. */
internal interface AccountWorkflowScheduler {
    fun later(action: () -> Unit)
    fun <T> background(work: () -> T, completed: (T) -> Unit): AccountWorkflowTask
}

internal class IntellijAccountWorkflowScheduler : AccountWorkflowScheduler {
    override fun later(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(action)
    }

    override fun <T> background(work: () -> T, completed: (T) -> Unit): AccountWorkflowTask {
        val future = AppExecutorUtil.getAppExecutorService().submit {
            val result = work()
            later { completed(result) }
        }
        return AccountWorkflowTask { future.cancel(true) }
    }
}
