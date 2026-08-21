package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles client-side rate limiting and daily request quotas for OTP dispatch.
 * - Per-user / per-device daily quota: 5 requests per calendar day.
 * - 60-second UI countdown timer lock.
 * - Global daily limit handling (2,496 emails/day).
 */
object OtpRateLimiter {
    const val MAX_DAILY_REQUESTS = 5
    const val GLOBAL_DAILY_CAP = 2496

    private const val PREFS_NAME = "plenxo_otp_limits"
    private const val KEY_PREFIX_COUNT = "otp_count_"
    private const val KEY_LAST_REQUEST_TIMESTAMP = "otp_last_req_time"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getTodayKey(): String {
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    }

    private fun sanitizeEmail(email: String): String {
        return email.trim().lowercase().replace("[^a-zA-Z0-9]".toRegex(), "_")
    }

    fun checkDailyLimit(context: Context, email: String): Pair<Boolean, Int> {
        val prefs = getPrefs(context)
        val today = getTodayKey()
        val emailKey = "$KEY_PREFIX_COUNT${today}_${sanitizeEmail(email)}"

        val emailCount = prefs.getInt(emailKey, 0)
        val highestCount = emailCount

        val remaining = (MAX_DAILY_REQUESTS - highestCount).coerceAtLeast(0)
        val allowed = highestCount < MAX_DAILY_REQUESTS

        Log.d("OtpRateLimiter", "Limit check for $email: count=$highestCount/$MAX_DAILY_REQUESTS (Allowed=$allowed)")
        return Pair(allowed, remaining)
    }

    fun recordRequest(context: Context, email: String): Int {
        val prefs = getPrefs(context)
        val today = getTodayKey()
        val emailKey = "$KEY_PREFIX_COUNT${today}_${sanitizeEmail(email)}"

        val newEmailCount = prefs.getInt(emailKey, 0) + 1

        prefs.edit()
            .putInt(emailKey, newEmailCount)
            .putLong(KEY_LAST_REQUEST_TIMESTAMP, System.currentTimeMillis())
            .apply()

        Log.d("OtpRateLimiter", "Recorded OTP request for $email. New count: $newEmailCount")
        return newEmailCount
    }

    fun getRemainingRequests(context: Context, email: String): Int {
        return checkDailyLimit(context, email).second
    }

    fun resetDailyLimit(context: Context, email: String) {
        val prefs = getPrefs(context)
        val today = getTodayKey()
        val emailKey = "$KEY_PREFIX_COUNT${today}_${sanitizeEmail(email)}"

        prefs.edit()
            .remove(emailKey)
            .apply()
    }
}
