package com.example.model

data class AgentAction(
    val type: ActionType,
    val packageName: String? = null,
    val appName: String? = null,
    val url: String? = null,
    val milliseconds: Long? = null,
    val target: String? = null,
    val text: String? = null,
    val direction: String? = null,
    val name: String? = null,
    val phoneNumber: String? = null,
    val message: String? = null,
    val title: String? = null
) {
    fun toReadableDescription(): String {
        return when (type) {
            ActionType.OPEN_APP -> "Open ${appName ?: packageName ?: "App"}"
            ActionType.OPEN_URL -> "Open Link: ${url?.take(30) ?: ""}"
            ActionType.WAIT -> "Wait ${milliseconds ?: 1000}ms"
            ActionType.CLICK -> "Tap \"${target ?: "target"}\""
            ActionType.LONG_CLICK -> "Long press \"${target ?: "target"}\""
            ActionType.TYPE_TEXT -> "Type \"$text\"${if (!target.isNullOrBlank()) " into \"$target\"" else ""}"
            ActionType.SWIPE -> "Swipe ${direction ?: "UP"}"
            ActionType.SCROLL -> "Scroll ${direction ?: "FORWARD"}"
            ActionType.PRESS_BACK -> "Press Back button"
            ActionType.PRESS_HOME -> "Press Home button"
            ActionType.SEARCH -> "Search \"${text ?: target ?: ""}\""
            ActionType.FIND_CONTACT -> "Find contact: \"$name\""
            ActionType.REQUEST_CONFIRMATION -> "Confirm: ${message ?: title ?: "Action"}"
            ActionType.CALL_CONTACT -> "Call ${name ?: phoneNumber ?: "Contact"}"
            ActionType.STOP_TASK -> "End task"
        }
    }
}
