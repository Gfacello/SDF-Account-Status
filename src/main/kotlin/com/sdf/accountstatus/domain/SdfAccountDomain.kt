package com.sdf.accountstatus.domain

enum class AccountEnvironment(val label: String) {
    SANDBOX("Sandbox"),
    PRODUCTION("Production"),
    UNKNOWN("Unknown Environment")
}

enum class WidgetTone {
    OK,
    WARNING,
    ERROR
}

data class AccountWidgetState(
    val text: String,
    val tooltip: String,
    val tone: WidgetTone,
    val showCriticalIcon: Boolean = false
)
