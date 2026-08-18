package com.example.model

enum class ActionType(val isSensitive: Boolean = false) {
    OPEN_APP(isSensitive = false),
    OPEN_URL(isSensitive = false),
    WAIT(isSensitive = false),
    CLICK(isSensitive = false),
    LONG_CLICK(isSensitive = false),
    TYPE_TEXT(isSensitive = false),
    SWIPE(isSensitive = false),
    SCROLL(isSensitive = false),
    PRESS_BACK(isSensitive = false),
    PRESS_HOME(isSensitive = false),
    SEARCH(isSensitive = false),
    FIND_CONTACT(isSensitive = false),
    REQUEST_CONFIRMATION(isSensitive = true),
    CALL_CONTACT(isSensitive = true),
    STOP_TASK(isSensitive = false);

    companion object {
        fun fromString(name: String): ActionType? {
            return entries.find { it.name.equals(name.trim(), ignoreCase = true) }
        }
    }
}
