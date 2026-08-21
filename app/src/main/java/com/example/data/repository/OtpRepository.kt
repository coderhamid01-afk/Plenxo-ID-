package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.model.OtpUiState
import com.example.model.SendOtpDetails
import com.example.model.SendOtpRequest
import com.example.model.SendOtpResponse
import com.example.network.NetworkModule
import com.example.network.OtpApiService
import com.example.util.OtpRateLimiter
import com.example.util.OtpUtils
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import retrofit2.Response

sealed interface OtpDeliveryResult {
    data class Success(
        val message: String,
        val details: SendOtpDetails? = null,
        val otp: String = ""
    ) : OtpDeliveryResult

    data class LimitExceeded(
        val message: String,
        val isDailyUserLimit: Boolean = false,
        val isGlobalDailyCap: Boolean = false
    ) : OtpDeliveryResult

    data class Error(
        val message: String,
        val httpCode: Int? = null
    ) : OtpDeliveryResult
}

/**
 * Clean repository implementation for OTP dispatching and limit enforcement.
 * Communicates with https://plenxo-back.netlify.app/api/send-otp
 */
class OtpRepository(
    private val context: Context,
    private val apiService: OtpApiService = NetworkModule.otpApiService,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val moshi = NetworkModule.moshi

    /**
     * Sends OTP with full client-side rate limits, daily quota checks, unique code generation,
     * Retrofit network dispatch, and strict non-200 error parsing.
     *
     * @param email The recipient Gmail address
     * @param purpose One of: "login", "signup", "forgot_password", "delete_account"
     * @param clientOtp Optional predetermined OTP; if null, a unique secure OTP is generated
     */
    suspend fun dispatchOtp(
        email: String,
        purpose: String = "signup",
        clientOtp: String? = null
    ): OtpDeliveryResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank()) {
            return@withContext OtpDeliveryResult.Error("Email address cannot be empty.")
        }

        // 1. Check local per-device / per-email daily limit (max 5 requests per day)
        val (canRequest, remainingDaily) = OtpRateLimiter.checkDailyLimit(context, cleanEmail)
        if (!canRequest) {
            val limitMsg = "Daily limit exceeded: You can only request up to ${OtpRateLimiter.MAX_DAILY_REQUESTS} verification codes per day. Please try again tomorrow."
            Log.w("OtpRepository", "Per-user daily limit hit for $cleanEmail ($remainingDaily remaining)")
            return@withContext OtpDeliveryResult.LimitExceeded(
                message = limitMsg,
                isDailyUserLimit = true,
                isGlobalDailyCap = false
            )
        }

        // 2. Generate unique 6-digit OTP using SecureRandom & session memory
        val activeOtp = if (!clientOtp.isNullOrBlank()) {
            clientOtp.trim().padStart(6, '0')
        } else {
            OtpUtils.generateOtp(cleanEmail)
        }

        // 3. Pre-sync OTP in Firestore
        try {
            OtpUtils.saveOtpToFirestore(cleanEmail, activeOtp, firestore)
        } catch (e: Throwable) {
            Log.w("OtpRepository", "Firestore pre-sync non-fatal error: ${e.message}")
        }

        // 4. Record request against user/device daily quota
        OtpRateLimiter.recordRequest(context, cleanEmail)

        // 5. Normalize purpose to supported backend types
        val normalizedPurpose = when (purpose.lowercase().trim()) {
            "login" -> "login"
            "forgot_password", "forgot-password", "forgot" -> "forgot_password"
            "delete_account", "delete-account", "delete" -> "delete_account"
            else -> "signup"
        }

        val requestPayload = SendOtpRequest(
            email = cleanEmail,
            purpose = normalizedPurpose,
            otp = activeOtp
        )

        Log.d("OtpRepository", "Dispatching POST to ${OtpApiService.BASE_URL}api/send-otp (Email=$cleanEmail, Purpose=$normalizedPurpose, OTP=$activeOtp)")

        // 6. Network Dispatch with 15s timeout
        try {
            val response = withTimeoutOrNull(15_000L) {
                apiService.sendOtp(requestPayload)
            }

            if (response != null) {
                return@withContext parseApiResponse(response, activeOtp)
            } else {
                Log.w("OtpRepository", "Network timeout connecting to OTP endpoint.")
                return@withContext OtpDeliveryResult.Error(
                    message = "Connection timed out while sending verification code. Please try again."
                )
            }
        } catch (e: Exception) {
            Log.e("OtpRepository", "Network dispatch exception: ${e.message}", e)
            return@withContext OtpDeliveryResult.Error(
                message = "Failed to reach verification server. Please check your network and try again."
            )
        }
    }

    /**
     * Strict parsing of Retrofit Response, handling HTTP 200, 400, 429, 500, etc.
     */
    private fun parseApiResponse(
        response: Response<SendOtpResponse>,
        activeOtp: String
    ): OtpDeliveryResult {
        val httpCode = response.code()

        if (response.isSuccessful) {
            val body = response.body()
            val message = body?.message?.ifBlank { null } ?: "OTP Sent Successfully!"
            Log.d("OtpRepository", "OTP delivered successfully (HTTP 200): ${body?.details?.sentVia}")
            return OtpDeliveryResult.Success(
                message = message,
                details = body?.details,
                otp = activeOtp
            )
        }

        // Strict error parsing for non-200 responses
        val errorBodyString = response.errorBody()?.string().orEmpty()
        Log.e("OtpRepository", "Non-200 response from backend (HTTP $httpCode): $errorBodyString")

        var parsedErrorMsg: String? = null
        var isGlobalCap = false

        if (errorBodyString.isNotBlank()) {
            try {
                val json = JSONObject(errorBodyString)
                val status = json.optString("status", "")
                val error = json.optString("error", "")
                val msg = json.optString("message", "")

                parsedErrorMsg = when {
                    msg.isNotBlank() -> msg
                    error.isNotBlank() -> error
                    status.isNotBlank() -> status
                    else -> null
                }

                if (parsedErrorMsg?.contains("limit", ignoreCase = true) == true ||
                    parsedErrorMsg?.contains("quota", ignoreCase = true) == true ||
                    parsedErrorMsg?.contains("capacity", ignoreCase = true) == true ||
                    parsedErrorMsg?.contains("daily", ignoreCase = true) == true
                ) {
                    isGlobalCap = true
                }
            } catch (_: Exception) {
                parsedErrorMsg = errorBodyString
            }
        }

        return when (httpCode) {
            429 -> {
                val rateLimitMsg = parsedErrorMsg ?: "Server rate limit reached (Global daily email quota of 2,496 reached). Please try again later."
                OtpDeliveryResult.LimitExceeded(
                    message = rateLimitMsg,
                    isDailyUserLimit = false,
                    isGlobalDailyCap = true
                )
            }
            400 -> {
                if (isGlobalCap) {
                    OtpDeliveryResult.LimitExceeded(
                        message = parsedErrorMsg ?: "Daily limit exceeded. Please try again tomorrow.",
                        isDailyUserLimit = false,
                        isGlobalDailyCap = true
                    )
                } else {
                    OtpDeliveryResult.Error(
                        message = parsedErrorMsg ?: "Invalid OTP request. Please check the entered email address.",
                        httpCode = 400
                    )
                }
            }
            500, 502, 503, 504 -> {
                OtpDeliveryResult.Error(
                    message = parsedErrorMsg ?: "OTP delivery service temporarily unavailable (HTTP $httpCode).",
                    httpCode = httpCode
                )
            }
            else -> {
                OtpDeliveryResult.Error(
                    message = parsedErrorMsg ?: "Unexpected server error (HTTP $httpCode).",
                    httpCode = httpCode
                )
            }
        }
    }
}
