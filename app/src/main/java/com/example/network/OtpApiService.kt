package com.example.network

import com.example.model.SendOtpRequest
import com.example.model.SendOtpResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Retrofit API interface for OTP operations.
 * Base URL: https://plenxo-back.netlify.app/
 * Endpoint: api/send-otp
 */
interface OtpApiService {

    @Headers("Content-Type: application/json")
    @POST("api/send-otp")
    suspend fun sendOtp(
        @Body request: SendOtpRequest
    ): Response<SendOtpResponse>

    companion object {
        const val BASE_URL = "https://plenxo-back.netlify.app/"
    }
}
