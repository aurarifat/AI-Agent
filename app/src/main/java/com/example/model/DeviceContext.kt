package com.example.model

data class AppInfo(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean = false
)

data class ContactItem(
    val id: String,
    val name: String,
    val phoneNumber: String
)

data class DeviceContext(
    val currentApp: String? = null,
    val isAccessibilityEnabled: Boolean = false,
    val hasContactsPermission: Boolean = false,
    val hasPhonePermission: Boolean = false
)
