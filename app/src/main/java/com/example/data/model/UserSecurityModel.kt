package com.example.data.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * Enterprise-grade Security Data Model for Plenxo.
 * Mapped to Firestore `users/{uid}`.
 */
@Keep
@Serializable
data class UserSecurityModel(
    val uid: String = "",
    val email: String = "",
    val plenxoId: String = "",
    val is2FAEnabled: Boolean = false,
    val securityPin: String = "", // encrypted/hashed PIN used for 2FA password recovery
    val masterPin: String = "", // SHA-256 hashed 6-digit PIN string
    val lockoutUntil: Long = 0L, // Epoch timestamp in milliseconds; 0L if active/unlocked
    val failedAttemptsCount: Int = 0, // Tracks consecutive security verification failures
    val lastSecurityViolationReason: String? = null // Audit field tracking exact reason for lockdown
) {
    /**
     * Checks if the account is currently in a 24-hour lockdown state.
     */
    fun isLockedOut(currentTime: Long = System.currentTimeMillis()): Boolean {
        return lockoutUntil > 0L && currentTime < lockoutUntil
    }

    /**
     * Calculates remaining lockdown duration in milliseconds.
     */
    fun getRemainingLockoutMs(currentTime: Long = System.currentTimeMillis()): Long {
        return if (isLockedOut(currentTime)) lockoutUntil - currentTime else 0L
    }

    companion object {
        /**
         * Hashes a 6-digit Master PIN string using SHA-256.
         */
        fun hashMasterPin(pin: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(pin.trim().toByteArray(Charsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }
}
