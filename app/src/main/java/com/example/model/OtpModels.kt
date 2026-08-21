package com.example.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Request payload for sending OTP via backend Netlify endpoint.
 *
 * Supported purposes:
 * - "login"
 * - "signup"
 * - "forgot_password"
 * - "delete_account"
 */
@JsonClass(generateAdapter = true)
data class SendOtpRequest(
    @Json(name = "email") val email: String,
    @Json(name = "purpose") val purpose: String,
    @Json(name = "otp") val otp: String
)

@JsonClass(generateAdapter = true)
data class SmtpResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class SendOtpDetails(
    @Json(name = "email") val email: String? = null,
    @Json(name = "sent_via") val sentVia: String? = null,
    @Json(name = "global_count") val globalCount: Int? = null,
    @Json(name = "smtp_response") val smtpResponse: SmtpResponse? = null
)

@JsonClass(generateAdapter = true)
data class SendOtpResponse(
    @Json(name = "status") val status: String = "",
    @Json(name = "message") val message: String = "",
    @Json(name = "details") val details: SendOtpDetails? = null,
    @Json(name = "error") val error: String? = null
)

/**
 * Sealed interface representing UI states for OTP operations in Jetpack Compose.
 */
sealed interface OtpUiState {
    object Idle : OtpUiState
    object Loading : OtpUiState
    data class TimerActive(val secondsRemaining: Int) : OtpUiState
    data class Success(val message: String, val details: SendOtpDetails? = null) : OtpUiState
    data class Error(
        val message: String,
        val isRateLimited: Boolean = false,
        val isDailyLimitExceeded: Boolean = false,
        val isGlobalCapReached: Boolean = false
    ) : OtpUiState
}
