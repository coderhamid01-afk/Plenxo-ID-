package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.data.model.UserSecurityModel
import com.example.util.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

import com.example.network.NetworkModule
import com.example.util.OtpUtils
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class NetlifyOtpResponse(
    val isSuccessful: Boolean,
    val message: String = "",
    val code: Int = 200,
    val otpCode: String? = null
)

/**
 * Enterprise-grade Security Repository for Plenxo.
 * Handles EncryptedSharedPreferences for local security AND
 * Firestore-backed 24-Hour Account Lockdown Engine.
 */
class SecurityRepository(private val context: Context) {

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    // =========================================================================
    // Local Security Preferences (EncryptedSharedPreferences)
    // =========================================================================

    private fun getPrefs(): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "security_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            Log.e("SecurityRepository", "Failed to get EncryptedSharedPreferences, falling back to standard SharedPreferences", e)
            try {
                context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            } catch (ex: Throwable) {
                null
            }
        }
    }

    fun setGlobalAppLock(pin: String?) {
        try {
            getPrefs()?.edit()?.putString("global_app_pin", pin)?.apply()
        } catch (e: Throwable) {
            Log.e("SecurityRepository", "Failed to setGlobalAppLock", e)
        }
    }

    fun getGlobalAppLock(): String? {
        return try {
            getPrefs()?.getString("global_app_pin", null)
        } catch (e: Throwable) {
            null
        }
    }

    fun setLockType(type: String) {
        try {
            getPrefs()?.edit()?.putString("lock_type", type)?.apply()
        } catch (e: Throwable) {
            Log.e("SecurityRepository", "Failed to setLockType", e)
        }
    }

    fun getLockType(): String {
        return try {
            getPrefs()?.getString("lock_type", "PIN") ?: "PIN"
        } catch (e: Throwable) {
            "PIN"
        }
    }

    fun setChatLockType(chatId: String, type: String?) {
        try {
            getPrefs()?.edit()?.putString("chat_lock_type_$chatId", type)?.apply()
        } catch (e: Throwable) {
            Log.e("SecurityRepository", "Failed to setChatLockType", e)
        }
    }

    fun getChatLockType(chatId: String): String? {
        return try {
            getPrefs()?.getString("chat_lock_type_$chatId", null)
        } catch (e: Throwable) {
            null
        }
    }

    fun setChatLock(chatId: String, pin: String?) {
        try {
            getPrefs()?.edit()?.putString("chat_pin_$chatId", pin)?.apply()
        } catch (e: Throwable) {
            Log.e("SecurityRepository", "Failed to setChatLock", e)
        }
    }

    fun getChatLock(chatId: String): String? {
        return try {
            getPrefs()?.getString("chat_pin_$chatId", null)
        } catch (e: Throwable) {
            null
        }
    }

    fun isChatLocked(chatId: String): Boolean {
        return try {
            getChatLock(chatId) != null
        } catch (e: Throwable) {
            false
        }
    }

    fun setCustomBlurBackground(uri: String?) {
        try {
            getPrefs()?.edit()?.putString("blur_bg_uri", uri)?.apply()
        } catch (e: Throwable) {
            Log.e("SecurityRepository", "Failed to setCustomBlurBackground", e)
        }
    }

    fun getCustomBlurBackground(): String? {
        return try {
            getPrefs()?.getString("blur_bg_uri", null)
        } catch (e: Throwable) {
            null
        }
    }

    // =========================================================================
    // Core Security & 24-Hour Lockdown Engine (Firestore)
    // =========================================================================

    /**
     * Fetches user security profile and checks lockout status.
     * If lock expired (currentTime >= lockoutUntil), automatically resets lockout status in Firestore.
     */
    suspend fun checkAccountLockoutStatus(emailOrPlenxoId: String): UserSecurityModel? = withContext(Dispatchers.IO) {
        runCatching {
            val cleanInput = emailOrPlenxoId.trim()
            if (cleanInput.isBlank()) return@runCatching null

            var targetDoc: com.google.firebase.firestore.DocumentSnapshot? = null

            // 1. Attempt direct document fetch by UID
            val directDoc = firestore.collection("users").document(cleanInput).get().await()
            if (directDoc.exists()) {
                targetDoc = directDoc
            } else {
                // 2. Query users by email
                val emailQuery = firestore.collection("users")
                    .whereEqualTo("email", cleanInput)
                    .limit(1)
                    .get()
                    .await()

                if (!emailQuery.isEmpty) {
                    targetDoc = emailQuery.documents[0]
                } else {
                    // 3. Query users by plenxoId or userCode
                    val plenxoQuery = firestore.collection("users")
                        .whereEqualTo("plenxoId", cleanInput)
                        .limit(1)
                        .get()
                        .await()

                    if (!plenxoQuery.isEmpty) {
                        targetDoc = plenxoQuery.documents[0]
                    } else {
                        val numericCode = cleanInput.removePrefix("PX-")
                        val codeQuery = firestore.collection("users")
                            .whereEqualTo("userCode", numericCode)
                            .limit(1)
                            .get()
                            .await()
                        if (!codeQuery.isEmpty) {
                            targetDoc = codeQuery.documents[0]
                        }
                    }
                }
            }

            if (targetDoc == null || !targetDoc.exists()) {
                return@runCatching null
            }

            val uid = targetDoc.id
            val email = targetDoc.getString("email") ?: ""
            val plenxoId = targetDoc.getString("plenxoId")
                ?: targetDoc.getString("px_id")
                ?: ("PX-" + (targetDoc.getString("userCode") ?: ""))
            val is2FA = targetDoc.getBoolean("is2FAEnabled")
                ?: targetDoc.getBoolean("is_2fa_enabled")
                ?: false
            val securityPin = targetDoc.getString("securityPin")
                ?: targetDoc.getString("security_pin")
                ?: targetDoc.getString("masterPin")
                ?: targetDoc.getString("master_pin")
                ?: ""
            val masterPin = targetDoc.getString("masterPin")
                ?: targetDoc.getString("securityPin")
                ?: ""
            val lockoutUntil = targetDoc.getLong("lockoutUntil")
                ?: targetDoc.getLong("lockout_until")
                ?: 0L
            val failedCount = (targetDoc.getLong("failedAttemptsCount")
                ?: targetDoc.getLong("failed_attempts_count")
                ?: 0L).toInt()
            val violationReason = targetDoc.getString("lastSecurityViolationReason")
                ?: targetDoc.getString("last_security_violation_reason")

            val currentTime = System.currentTimeMillis()

            if (lockoutUntil > 0L && currentTime >= lockoutUntil) {
                // Lock expired -> reset lockoutUntil and failedAttemptsCount in Firestore
                val resetMap = mapOf<String, Any>(
                    "lockoutUntil" to 0L,
                    "failedAttemptsCount" to 0,
                    "lastSecurityViolationReason" to FieldValue.delete()
                )
                runCatching {
                    firestore.collection("users").document(uid).set(resetMap, SetOptions.merge()).await()
                }

                UserSecurityModel(
                    uid = uid,
                    email = email,
                    plenxoId = plenxoId,
                    is2FAEnabled = is2FA,
                    securityPin = securityPin,
                    masterPin = masterPin,
                    lockoutUntil = 0L,
                    failedAttemptsCount = 0,
                    lastSecurityViolationReason = null
                )
            } else {
                UserSecurityModel(
                    uid = uid,
                    email = email,
                    plenxoId = plenxoId,
                    is2FAEnabled = is2FA,
                    securityPin = securityPin,
                    masterPin = masterPin,
                    lockoutUntil = lockoutUntil,
                    failedAttemptsCount = failedCount,
                    lastSecurityViolationReason = violationReason
                )
            }
        }.getOrNull()
    }

    /**
     * Atomic 24-hour lockdown trigger across `users/{uid}`.
     * Signs out local Firebase session immediately.
     */
    suspend fun trigger24HourLockdown(targetUid: String, violationReason: String): UserSecurityModel = withContext(Dispatchers.IO) {
        val lockTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000L) // 24 Hours in milliseconds
        val lockData = mapOf<String, Any>(
            "lockoutUntil" to lockTime,
            "failedAttemptsCount" to FieldValue.increment(1),
            "lastSecurityViolationReason" to violationReason
        )

        runCatching {
            firestore.collection("users").document(targetUid).set(lockData, SetOptions.merge()).await()
        }.onFailure { e ->
            Log.e("SecurityRepository", "Failed to write 24-hour lockdown to Firestore for $targetUid: ${e.message}")
        }

        // Revoke active sessions locally
        runCatching {
            auth.signOut()
            SessionManager.clearLoginState(context)
        }.onFailure { e ->
            Log.e("SecurityRepository", "Failed to clear session locally: ${e.message}")
        }

        UserSecurityModel(
            uid = targetUid,
            lockoutUntil = lockTime,
            lastSecurityViolationReason = violationReason
        )
    }

    /**
     * Verifies if entered Plenxo ID matches the provided Email address in Firestore.
     */
    suspend fun verifyPlenxoIdMatchesEmail(email: String, inputPlenxoId: String): Pair<Boolean, UserSecurityModel?> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val cleanInput = inputPlenxoId.trim()

        val query = firestore.collection("users")
            .whereEqualTo("email", cleanEmail)
            .limit(1)
            .get()
            .await()

        if (query.isEmpty) {
            return@withContext Pair(false, null)
        }

        val doc = query.documents[0]
        val uid = doc.id
        val fetchedEmail = doc.getString("email") ?: cleanEmail
        val fetchedPlenxoId = doc.getString("plenxoId")
            ?: doc.getString("px_id")
            ?: ("PX-" + (doc.getString("userCode") ?: ""))
        val is2FA = doc.getBoolean("is2FAEnabled")
            ?: doc.getBoolean("is_2fa_enabled")
            ?: false
        val securityPin = doc.getString("securityPin")
            ?: doc.getString("security_pin")
            ?: doc.getString("masterPin")
            ?: doc.getString("master_pin")
            ?: ""
        val masterPin = doc.getString("masterPin")
            ?: doc.getString("securityPin")
            ?: ""
        val lockoutUntil = doc.getLong("lockoutUntil")
            ?: doc.getLong("lockout_until")
            ?: 0L
        val failedCount = (doc.getLong("failedAttemptsCount")
            ?: doc.getLong("failed_attempts_count")
            ?: 0L).toInt()
        val violationReason = doc.getString("lastSecurityViolationReason")

        val normInput = if (cleanInput.startsWith("PX-", ignoreCase = true)) cleanInput else "PX-$cleanInput"
        val normFetched = if (fetchedPlenxoId.startsWith("PX-", ignoreCase = true)) fetchedPlenxoId else "PX-$fetchedPlenxoId"

        val matches = normInput.equals(normFetched, ignoreCase = true)

        val model = UserSecurityModel(
            uid = uid,
            email = fetchedEmail,
            plenxoId = fetchedPlenxoId,
            is2FAEnabled = is2FA,
            securityPin = securityPin,
            masterPin = masterPin,
            lockoutUntil = lockoutUntil,
            failedAttemptsCount = failedCount,
            lastSecurityViolationReason = violationReason
        )

        if (!matches) {
            return@withContext Pair(false, model)
        }
        return@withContext Pair(true, model)
    }

    /**
     * Verifies 6-digit Security PIN against stored hashed or plain Security PIN.
     */
    suspend fun verifySecurityPin(targetUid: String, inputPin: String, expectedPinOrHash: String): Boolean = withContext(Dispatchers.IO) {
        val cleanInput = inputPin.trim()
        val cleanExpected = expectedPinOrHash.trim()
        if (cleanInput.isBlank() || cleanExpected.isBlank()) return@withContext false

        val hashedInput = UserSecurityModel.hashMasterPin(cleanInput)
        val matches = hashedInput.equals(cleanExpected, ignoreCase = true) || cleanInput == cleanExpected

        if (!matches && targetUid.isNotBlank()) {
            Log.w("SecurityRepository", "Security PIN Mismatch for $targetUid")
        }
        return@withContext matches
    }

    /**
     * Zero-Tolerance Plenxo ID Verification.
     * Instantly triggers 24-hour lockdown on mismatch.
     */
    suspend fun verifyPlenxoId(targetUid: String, inputPlenxoId: String, expectedPlenxoId: String): Boolean {
        val cleanInput = inputPlenxoId.trim()
        val cleanExpected = expectedPlenxoId.trim()

        val normInput = if (cleanInput.startsWith("PX-")) cleanInput else "PX-$cleanInput"
        val normExpected = if (cleanExpected.startsWith("PX-")) cleanExpected else "PX-$cleanExpected"

        val matches = normInput.equals(normExpected, ignoreCase = true)
        if (!matches) {
            Log.w("SecurityRepository", "Plenxo ID Mismatch for $targetUid! Input: '$cleanInput', Expected: '$cleanExpected'. Triggering lockdown.")
            trigger24HourLockdown(targetUid, "Plenxo ID Verification Failed ($cleanInput)")
            return false
        }
        return true
    }

    /**
     * Zero-Tolerance Master PIN Verification using SHA-256 digest.
     * Instantly triggers 24-hour lockdown on mismatch.
     */
    suspend fun verifyMasterPin(targetUid: String, inputPin: String, expectedHashedPin: String): Boolean {
        val hashedInput = UserSecurityModel.hashMasterPin(inputPin)
        val matches = expectedHashedPin.isNotBlank() && hashedInput.equals(expectedHashedPin.trim(), ignoreCase = true)

        if (!matches) {
            Log.w("SecurityRepository", "Master PIN Mismatch for $targetUid! Triggering 24-hour lockdown.")
            trigger24HourLockdown(targetUid, "Master PIN Challenge Failed")
            return false
        }
        return true
    }

    private val otpRepository by lazy { OtpRepository(context, NetworkModule.otpApiService, firestore) }

    /**
     * Dispatches client-generated OTP to Netlify backend to deliver via email.
     */
    suspend fun sendOtpToNetlify(
        recipientEmail: String,
        purpose: String = "signup",
        generatedOtp: String
    ): NetlifyOtpResponse = withContext(Dispatchers.IO) {
        val cleanEmail = recipientEmail.trim()
        val cleanOtp = generatedOtp.trim().padStart(6, '0')

        val result = otpRepository.dispatchOtp(cleanEmail, purpose, cleanOtp)
        when (result) {
            is OtpDeliveryResult.Success -> {
                NetlifyOtpResponse(
                    isSuccessful = true,
                    message = result.message,
                    code = 200,
                    otpCode = cleanOtp
                )
            }
            is OtpDeliveryResult.LimitExceeded -> {
                NetlifyOtpResponse(
                    isSuccessful = false,
                    message = result.message,
                    code = 429,
                    otpCode = cleanOtp
                )
            }
            is OtpDeliveryResult.Error -> {
                NetlifyOtpResponse(
                    isSuccessful = false,
                    message = result.message,
                    code = result.httpCode ?: 500,
                    otpCode = cleanOtp
                )
            }
        }
    }

    /**
     * Dispatches client-generated OTP to Netlify backend to deliver via email.
     */
    suspend fun sendNetlifyEmailOtp(
        recipientEmail: String,
        purpose: String = "general",
        generatedOtp: String
    ): Boolean = withContext(Dispatchers.IO) {
        val result = sendOtpToNetlify(recipientEmail, purpose, generatedOtp)
        return@withContext result.isSuccessful
    }

    /**
     * Strict String-Normalized Email OTP Verification.
     */
    suspend fun verifyEmailOtp(targetUid: String, inputOtp: String, sentOtp: String): Boolean = withContext(Dispatchers.IO) {
        val rawInput = inputOtp.trim()
        val cleanInput = if (rawInput.matches(Regex("^\\d{1,5}$"))) rawInput.padStart(6, '0') else rawInput

        val rawSent = sentOtp.trim()
        val cleanSent = if (rawSent.matches(Regex("^\\d{1,5}$"))) rawSent.padStart(6, '0') else rawSent

        Log.d("OTP_DEBUG", "Received from Netlify: $cleanSent")
        Log.d("OTP_DEBUG", "Entered by User: $cleanInput")

        if (cleanSent.isNotEmpty() && (cleanSent == cleanInput || cleanSent.equals(cleanInput, ignoreCase = true))) {
            Log.d("OTP_DEBUG", "SecurityRepository: Direct match success for $targetUid")
            return@withContext true
        }

        val firestoreOtps = withTimeoutOrNull(10_000L) {
            OtpUtils.getFirestoreOtpsForUser(targetUid, firestore)
        } ?: emptyList()

        val matches = (cleanSent.isNotBlank() && cleanInput.isNotBlank() && (cleanInput == cleanSent || cleanInput.equals(cleanSent, ignoreCase = true))) ||
                      (cleanInput.isNotBlank() && firestoreOtps.any { it.isNotBlank() && (it.trim() == cleanInput || it.trim().equals(cleanInput, ignoreCase = true)) })

        if (!matches) {
            Log.w("OTP_DEBUG", "SecurityRepository: Email OTP Mismatch for $targetUid! Expected: '$cleanSent', Firestore: $firestoreOtps, Got: '$cleanInput'.")
            return@withContext false
        }

        Log.d("OTP_DEBUG", "SecurityRepository: Email OTP Verification Success for $targetUid!")
        return@withContext true
    }
}
