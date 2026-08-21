package com.example.util

/*
 * CallPrivacyManager is temporarily disabled.
 */
object CallPrivacyManager {
    fun authenticateForCall(context: Any, onGranted: () -> Unit, onDenied: (String) -> Unit) {
        onGranted()
    }
}
