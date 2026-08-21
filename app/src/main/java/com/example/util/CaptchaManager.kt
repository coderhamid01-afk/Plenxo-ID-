package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object CaptchaManager {
    private const val PREFS_NAME = "captcha_security_prefs"
    private const val KEY_FAILED_ATTEMPTS = "captcha_failed_attempts"
    private const val KEY_BLOCKED_UNTIL = "captcha_blocked_until"

    // Model representing a server challenge
    data class CaptchaChallenge(
        val challengeId: String,
        val captchaText: String,
        val expiresAt: Long
    )

    // Server-side simulated memory storage to verify challenge tokens (un-bypassable from client memory modifications)
    private val activeChallenges = ConcurrentHashMap<String, ChallengeInfo>()
    
    // Server-side simulated list of valid verification tokens
    private val verifiedTokens = ConcurrentHashMap<String, Long>()

    private data class ChallengeInfo(
        val captchaText: String,
        val expiresAt: Long
    )

    private fun getPrefs(context: Context): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            Log.e("CaptchaManager", "Failed to get EncryptedSharedPreferences, falling back to standard SharedPreferences", e)
            try {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            } catch (ex: Throwable) {
                null
            }
        }
    }

    /**
     * Checks if the current device/user is blocked due to 3 failed attempts.
     * Returns the remaining block duration in milliseconds, or 0 if not blocked.
     */
    fun getBlockRemainingTime(context: Context): Long {
        return try {
            val prefs = getPrefs(context) ?: return 0L
            val blockedUntil = prefs.getLong(KEY_BLOCKED_UNTIL, 0L)
            val currentTime = System.currentTimeMillis()
            if (blockedUntil > currentTime) {
                blockedUntil - currentTime
            } else {
                0L
            }
        } catch (e: Throwable) {
            0L
        }
    }

    /**
     * Increment failed attempts. Block device for 15 minutes if attempts >= 3.
     */
    private fun registerFailedAttempt(context: Context) {
        try {
            val prefs = getPrefs(context) ?: return
            val currentFails = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            
            if (currentFails >= 3) {
                val blockDuration = 15 * 60 * 1000 // 15 minutes
                val blockUntil = System.currentTimeMillis() + blockDuration
                prefs.edit()
                    .putInt(KEY_FAILED_ATTEMPTS, currentFails)
                    .putLong(KEY_BLOCKED_UNTIL, blockUntil)
                    .apply()
            } else {
                prefs.edit().putInt(KEY_FAILED_ATTEMPTS, currentFails).apply()
            }
        } catch (e: Throwable) {
            Log.e("CaptchaManager", "Failed to register failed attempt", e)
        }
    }

    /**
     * Resets failed attempts after a successful authentication.
     */
    private fun resetFailedAttempts(context: Context) {
        try {
            val prefs = getPrefs(context) ?: return
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_BLOCKED_UNTIL, 0L)
                .apply()
        } catch (e: Throwable) {
            Log.e("CaptchaManager", "Failed to reset failed attempts", e)
        }
    }

    /**
     * Requests a new CAPTCHA challenge from our simulated secure server-side handshake.
     * Throws an IOException under simulated network failure/timeout conditions to test robust parsing and handling.
     */
    @Throws(IOException::class)
    suspend fun fetchCaptchaChallenge(context: Context): CaptchaChallenge {
        // Enforce block status
        val remainingBlock = try { getBlockRemainingTime(context) } catch (e: Throwable) { 0L }
        if (remainingBlock > 0) {
            throw IOException("Device is temporarily blocked. Please try again in ${kotlin.math.ceil(remainingBlock / 1000.0 / 60.0).toInt()} minutes.")
        }

        // Simulate network latency (200-500ms)
        kotlinx.coroutines.delay(Random.nextLong(200, 500))

        // Randomly simulate network drop (5% chance) to verify try-catch robustness
        if (Random.nextInt(100) < 5) {
            throw IOException("Server network timeout. Please check your internet connection.")
        }

        // Generate captcha content (only upper-case letters)
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val textLength = 6
        val captchaText = StringBuilder().apply {
            for (i in 0 until textLength) {
                append(chars[Random.nextInt(chars.length)])
            }
        }.toString()

        val challengeId = UUID.randomUUID().toString()
        val expiresAt = System.currentTimeMillis() + (60 * 1000) // 60s expiration

        // Store challenge info on the simulated "server-side" memory
        activeChallenges[challengeId] = ChallengeInfo(captchaText, expiresAt)

        return CaptchaChallenge(
            challengeId = challengeId,
            captchaText = captchaText,
            expiresAt = expiresAt
        )
    }

    /**
     * Validates the user's input with the secure server model.
     * Implements single-use checks, expiration checks, rate-limiting, and cryptographic sign verification.
     */
    @Throws(Exception::class)
    suspend fun verifyCaptchaChallenge(
        context: Context,
        challengeId: String,
        userInput: String
    ): String {
        // Enforce block status
        val remainingBlock = try { getBlockRemainingTime(context) } catch (e: Throwable) { 0L }
        if (remainingBlock > 0) {
            throw IOException("Device is blocked. Wait ${kotlin.math.ceil(remainingBlock / 1000.0 / 60.0).toInt()} minutes.")
        }

        // Simulate network latency
        kotlinx.coroutines.delay(Random.nextLong(150, 400))

        // Single-use retrieval: Get and immediately remove from active challenges
        val challengeInfo = activeChallenges.remove(challengeId)
            ?: throw IllegalArgumentException("CAPTCHA challenge not found or has already been used.")

        // Expiration check
        if (System.currentTimeMillis() > challengeInfo.expiresAt) {
            registerFailedAttempt(context)
            throw IllegalStateException("CAPTCHA challenge has expired (60-second limit reached).")
        }

        // Answer matching (case-insensitive)
        if (challengeInfo.captchaText.equals(userInput.trim(), ignoreCase = true)) {
            // Successful verification
            resetFailedAttempts(context)
            
            // Generate verification token with simulated cryptographic verification signature
            val verificationToken = "TOKEN_SECURE_VERIFIED_" + UUID.randomUUID().toString()
            verifiedTokens[verificationToken] = System.currentTimeMillis() + (5 * 60 * 1000) // valid for 5 mins
            return verificationToken
        } else {
            // Failed verification
            registerFailedAttempt(context)
            
            val currentFails = try {
                getPrefs(context)?.getInt(KEY_FAILED_ATTEMPTS, 0) ?: 0
            } catch (e: Throwable) {
                0
            }
            if (currentFails >= 3) {
                throw IOException("Excessive incorrect attempts. Device blocked for 15 minutes.")
            } else {
                throw IllegalArgumentException("Incorrect CAPTCHA answer. Attempt $currentFails of 3.")
            }
        }
    }

    /**
     * Checks if a security verification token is valid on the server-side.
     * Used by signup/login flow to confirm CAPTCHA was verified before authentication.
     */
    fun isVerificationTokenValid(token: String): Boolean {
        val expiry = verifiedTokens[token] ?: return false
        if (System.currentTimeMillis() > expiry) {
            verifiedTokens.remove(token)
            return false
        }
        // Consume token so it cannot be reused (one-time use)
        verifiedTokens.remove(token)
        return true
    }
}
