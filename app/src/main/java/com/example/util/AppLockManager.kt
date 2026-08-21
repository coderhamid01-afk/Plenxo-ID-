package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Debug
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

object AppLockManager {
    private const val PREFS_NAME = "security_prefs"
    private const val KEY_IS_APP_LOCKED = "is_app_locked"
    private const val KEY_PERMANENT_LOCK = "is_permanently_locked"

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
            Log.e("AppLockManager", "Failed to get EncryptedSharedPreferences, falling back to standard SharedPreferences", e)
            try {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            } catch (ex: Throwable) {
                null
            }
        }
    }

    /**
     * Checks if the App Lock feature is currently enabled by the user.
     */
    fun isAppLockEnabled(context: Context): Boolean {
        return try {
            SessionManager.getGlobalAppLock(context)
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Returns whether the application is currently in a locked state.
     * Defaults to true if app lock is enabled to prevent cold start bypasses.
     */
    fun isLocked(context: Context): Boolean {
        return try {
            if (!isAppLockEnabled(context)) return false
            
            // If a permanent security risk lock is active, always lock.
            if (isPermanentlyLocked(context)) return true

            getPrefs(context)?.getBoolean(KEY_IS_APP_LOCKED, false) ?: false
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Updates the persistent locked state.
     */
    fun setLocked(context: Context, locked: Boolean) {
        try {
            getPrefs(context)?.edit()?.putBoolean(KEY_IS_APP_LOCKED, locked)?.apply()
        } catch (e: Throwable) {
            Log.e("AppLockManager", "Failed to setLocked", e)
        }
    }

    /**
     * Marks the app as permanently locked due to security risks.
     */
    fun setPermanentlyLocked(context: Context, locked: Boolean) {
        try {
            getPrefs(context)?.edit()?.putBoolean(KEY_PERMANENT_LOCK, locked)?.apply()
        } catch (e: Throwable) {
            Log.e("AppLockManager", "Failed to setPermanentlyLocked", e)
        }
    }

    fun isPermanentlyLocked(context: Context): Boolean {
        return try {
            getPrefs(context)?.getBoolean(KEY_PERMANENT_LOCK, false) ?: false
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Comprehensive Root Detection
     */
    fun isDeviceRooted(): Boolean {
        // 1. Check Build Tags
        val buildTags = android.os.Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }

        // 2. Check SU binary paths
        val suPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        for (path in suPaths) {
            if (File(path).exists()) {
                return true
            }
        }

        // 3. Try executing su command
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val reader = process.inputStream.bufferedReader()
            reader.readLine() != null
        } catch (e: Exception) {
            false
        } finally {
            process?.destroy()
        }
    }

    /**
     * Debug Detection
     */
    fun isDebuggerAttached(): Boolean {
        return Debug.isDebuggerConnected()
    }

    /**
     * Overall security integrity check. Returns true if there is a security violation (Rooted/Debugged).
     */
    fun checkSecurityRisk(context: Context): Boolean {
        if (isDeviceRooted() || isDebuggerAttached()) {
            setPermanentlyLocked(context, true)
            return true
        }
        return false
    }
}
