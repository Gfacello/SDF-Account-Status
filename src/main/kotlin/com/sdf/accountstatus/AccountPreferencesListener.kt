package com.sdf.accountstatus

import com.intellij.util.messages.Topic

internal enum class AccountPreferenceChange { PROVIDER, DISPLAY_STYLE }

/** Subscribe on the application bus with the widget disposable; handle changes on its UI thread. */
internal fun interface AccountPreferencesListener {
    fun changed(change: AccountPreferenceChange)

    companion object {
        val TOPIC: Topic<AccountPreferencesListener> = Topic.create(
            "SDF account preferences changed", AccountPreferencesListener::class.java
        )
    }
}
