package com.example.util

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

object OtpUtils {

    private const val FIRESTORE_TIMEOUT_MS = 8000L
    private val secureRandom = SecureRandom()

    // Thread-safe session memory storing generated OTPs per calendar day to avoid duplicates
    private val dailyGeneratedOtps = Collections.synchronizedSet(mutableSetOf<String>())

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    }

    /**
     * Generates a cryptographically strong 6-digit numerical OTP using java.security.SecureRandom.
     * Checks session memory to ensure no duplicate OTP is issued for the current calendar day.
     */
    fun generateOtp(identifier: String = ""): String {
        val today = getTodayDateString()
        var code: String
        var attempts = 0

        do {
            val num = 100000 + secureRandom.nextInt(900000)
            code = num.toString()
            val uniqueKey = "$today:${identifier.trim().lowercase()}:$code"
            val globalDailyKey = "$today:$code"

            if (!dailyGeneratedOtps.contains(uniqueKey) && !dailyGeneratedOtps.contains(globalDailyKey)) {
                dailyGeneratedOtps.add(uniqueKey)
                dailyGeneratedOtps.add(globalDailyKey)
                break
            }
            attempts++
        } while (attempts < 100)

        Log.d("OtpUtils", "Generated cryptographically secure 6-digit OTP: $code (attempt #$attempts)")
        return code
    }

    private val OTP_FIELDS = listOf(
        "otpCode", "otp_code", "otp", "emailOtp", "email_otp",
        "currentOtp", "current_otp", "verificationCode", "verification_code", "code"
    )

    /**
     * Safely queries Firestore for any active/saved OTPs associated with [identifier] (UID or Email).
     * Extracts values regardless of underlying data type (String, Long, Double, Int) without truncation.
     * Wrapped in a strict timeout so it can never hang.
     */
    suspend fun getFirestoreOtpsForUser(
        identifier: String,
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    ): List<String> = withContext(Dispatchers.IO) {
        val cleanId = identifier.trim()
        if (cleanId.isBlank()) return@withContext emptyList()

        val foundOtps = mutableListOf<String>()

        fun extractFromDoc(doc: DocumentSnapshot?) {
            if (doc == null || !doc.exists()) return
            for (f in OTP_FIELDS) {
                val v = doc.get(f)
                if (v != null) {
                    val str = when (v) {
                        is Long -> v.toString().padStart(6, '0')
                        is Int -> v.toString().padStart(6, '0')
                        is Double -> v.toLong().toString().padStart(6, '0')
                        is Float -> v.toLong().toString().padStart(6, '0')
                        is Number -> v.toLong().toString().padStart(6, '0')
                        else -> {
                            val trimmed = v.toString().trim()
                            if (trimmed.matches(Regex("^\\d{1,5}$"))) trimmed.padStart(6, '0') else trimmed
                        }
                    }
                    if (str.isNotBlank() && str.length >= 4) {
                        foundOtps.add(str)
                    }
                }
            }
        }

        try {
            withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
                // 1. By direct document ID lookup
                runCatching { firestore.collection("users").document(cleanId).get().await() }.getOrNull()?.let { extractFromDoc(it) }
                runCatching { firestore.collection("otp_codes").document(cleanId).get().await() }.getOrNull()?.let { extractFromDoc(it) }

                // 2. By email query if identifier is or contains an email
                if (cleanId.contains("@")) {
                    runCatching { firestore.collection("users").whereEqualTo("email", cleanId).get().await() }.getOrNull()?.documents?.forEach { extractFromDoc(it) }
                    runCatching { firestore.collection("otp_codes").whereEqualTo("email", cleanId).get().await() }.getOrNull()?.documents?.forEach { extractFromDoc(it) }
                }
            }
        } catch (e: Throwable) {
            Log.w("OTP_FIX", "getFirestoreOtpsForUser error: ${e.message}")
        }

        val distinctOtps = foundOtps.distinct()
        Log.d("OTP_FIX", "getFirestoreOtpsForUser($cleanId) found: $distinctOtps")
        return@withContext distinctOtps
    }

    /**
     * Helper to write a generated OTP code across Firestore user documents for sync.
     * Wrapped in a strict timeout so it never blocks execution.
     */
    suspend fun saveOtpToFirestore(
        uidOrEmail: String,
        otpCode: String,
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    ) = withContext(Dispatchers.IO) {
        val cleanId = uidOrEmail.trim()
        val rawOtp = otpCode.trim()
        val cleanOtp = if (rawOtp.matches(Regex("^\\d{1,5}$"))) rawOtp.padStart(6, '0') else rawOtp
        if (cleanId.isBlank() || cleanOtp.isBlank()) return@withContext

        val updateMap = mapOf<String, Any>(
            "otpCode" to cleanOtp,
            "otp_code" to cleanOtp,
            "otp" to cleanOtp,
            "emailOtp" to cleanOtp,
            "lastOtpGeneratedAt" to System.currentTimeMillis()
        )

        try {
            withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
                if (cleanId.contains("@")) {
                    firestore.collection("otp_codes").document(cleanId).set(updateMap, com.google.firebase.firestore.SetOptions.merge()).await()
                } else {
                    firestore.collection("users").document(cleanId).set(updateMap, com.google.firebase.firestore.SetOptions.merge()).await()
                }
                Log.d("OTP_FIX", "Successfully saved OTP '$cleanOtp' to Firestore for $cleanId")
            }
        } catch (e: Throwable) {
            Log.w("OTP_FIX", "Failed saving OTP to Firestore: ${e.message}")
        }
    }
}
