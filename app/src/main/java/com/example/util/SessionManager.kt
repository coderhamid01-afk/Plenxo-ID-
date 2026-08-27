package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SessionManager {
    private const val PREF_NAME = "plenxo_pro_secure_prefs"
    private const val KEY_IS_LOGGED_IN = "key_is_logged_in"
    private const val KEY_USER_TOKEN = "key_user_token"
    private const val KEY_USER_EMAIL = "key_user_email"
    
    private const val KEY_GLOBAL_APP_LOCK = "key_global_app_lock"
    private const val KEY_LOCKED_CHATS = "key_locked_chats"
    private const val KEY_BLOCKED_USERS = "key_blocked_users"
    private const val KEY_DISAPPEARING_TIMER = "key_disappearing_timer"
    private const val KEY_FONT_SIZE = "key_font_size"
    private const val KEY_WALLPAPER_URI = "key_wallpaper_uri"
    private const val KEY_NOTIFS_ENABLED = "key_notifs_enabled"
    private const val KEY_VIBRATE_ENABLED = "key_vibrate_enabled"
    private const val KEY_POPUP_ENABLED = "key_popup_enabled"
    private const val KEY_LAST_SEEN_VIS = "key_last_seen_vis"
    private const val KEY_PHOTO_VIS = "key_photo_vis"
    private const val KEY_BIO_VIS = "key_bio_vis"
    private const val KEY_READ_RECEIPTS = "key_read_receipts"
    private const val KEY_ABOUT_TEXT = "key_about_text"
    private const val KEY_PINNED_CHATS = "key_pinned_chats"
    private const val KEY_LOCAL_ONLY_MODE = "key_local_only_mode"
    private const val KEY_BLOCK_SCREENSHOTS = "key_block_screenshots"
    private const val KEY_APPLOCK_TYPE = "key_applock_type"
    private const val KEY_APPLOCK_CREDENTIAL = "key_applock_credential"
    private const val KEY_SESSION_ID = "key_session_id"
    private const val KEY_PLENXO_ID = "key_plenxo_id"
    private const val KEY_DISPLAY_NAME = "key_display_name"
    private const val KEY_BIO = "key_bio"
    private const val KEY_PROFILE_PIC_URL = "key_profile_pic_url"
    private const val KEY_USER_AGE = "key_user_age"

    private fun getEncryptedPrefs(context: Context): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("SessionManager", "Secure initialization failed: EncryptedSharedPreferences could not be initialized securely.")
            throw SecurityException("Secure storage failed to initialize. Re-authentication required.")
        }
    }

    fun saveGlobalAppLock(context: Context, enabled: Boolean) {
        try {
            getEncryptedPrefs(context)?.edit()?.putBoolean(KEY_GLOBAL_APP_LOCK, enabled)?.apply()
        } catch (e: Exception) {
            Log.e("SessionManager", "Error saving global app lock", e)
        }
    }
    fun getGlobalAppLock(context: Context): Boolean {
        return try {
            getEncryptedPrefs(context)?.getBoolean(KEY_GLOBAL_APP_LOCK, false) ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun saveLockedChats(context: Context, chatIds: Set<String>) {
        getEncryptedPrefs(context)?.edit()?.putStringSet(KEY_LOCKED_CHATS, chatIds)?.apply()
    }
    fun getLockedChats(context: Context): Set<String> {
        return getEncryptedPrefs(context)?.getStringSet(KEY_LOCKED_CHATS, emptySet()) ?: emptySet()
    }

    fun saveBlockedUsers(context: Context, uids: Set<String>) {
        getEncryptedPrefs(context)?.edit()?.putStringSet(KEY_BLOCKED_USERS, uids)?.apply()
    }
    fun getBlockedUsers(context: Context): Set<String> {
        return getEncryptedPrefs(context)?.getStringSet(KEY_BLOCKED_USERS, emptySet()) ?: emptySet()
    }

    fun saveDisappearingTimer(context: Context, durationMs: Long) {
        getEncryptedPrefs(context)?.edit()?.putLong(KEY_DISAPPEARING_TIMER, durationMs)?.apply()
    }
    fun getDisappearingTimer(context: Context): Long {
        return getEncryptedPrefs(context)?.getLong(KEY_DISAPPEARING_TIMER, 0L) ?: 0L
    }

    fun saveFontSize(context: Context, size: String) {
        getEncryptedPrefs(context)?.edit()?.putString(KEY_FONT_SIZE, size)?.apply()
    }
    fun getFontSize(context: Context): String {
        return getEncryptedPrefs(context)?.getString(KEY_FONT_SIZE, "medium") ?: "medium"
    }

    fun saveWallpaperUri(context: Context, uri: String?) {
        getEncryptedPrefs(context)?.edit()?.putString(KEY_WALLPAPER_URI, uri)?.apply()
    }
    fun getWallpaperUri(context: Context): String? {
        return getEncryptedPrefs(context)?.getString(KEY_WALLPAPER_URI, null)
    }

    fun saveNotifsEnabled(context: Context, enabled: Boolean) {
        getEncryptedPrefs(context)?.edit()?.putBoolean(KEY_NOTIFS_ENABLED, enabled)?.apply()
    }
    fun getNotifsEnabled(context: Context): Boolean {
        return getEncryptedPrefs(context)?.getBoolean(KEY_NOTIFS_ENABLED, true) ?: true
    }

    fun saveVibrateEnabled(context: Context, enabled: Boolean) {
        getEncryptedPrefs(context)?.edit()?.putBoolean(KEY_VIBRATE_ENABLED, enabled)?.apply()
    }
    fun getVibrateEnabled(context: Context): Boolean {
        return getEncryptedPrefs(context)?.getBoolean(KEY_VIBRATE_ENABLED, true) ?: true
    }

    fun savePopupEnabled(context: Context, enabled: Boolean) {
        getEncryptedPrefs(context)?.edit()?.putBoolean(KEY_POPUP_ENABLED, enabled)?.apply()
    }
    fun getPopupEnabled(context: Context): Boolean {
        return getEncryptedPrefs(context)?.getBoolean(KEY_POPUP_ENABLED, true) ?: true
    }

    fun saveLastSeenVis(context: Context, visibility: String) {
        getEncryptedPrefs(context)?.edit()?.putString(KEY_LAST_SEEN_VIS, visibility)?.apply()
    }
    fun getLastSeenVis(context: Context): String {
        return getEncryptedPrefs(context)?.getString(KEY_LAST_SEEN_VIS, "EVERYONE") ?: "EVERYONE"
    }

    fun savePhotoVis(context: Context, visibility: String) {
        getEncryptedPrefs(context)?.edit()?.putString(KEY_PHOTO_VIS, visibility)?.apply()
    }
    fun getPhotoVis(context: Context): String {
        return getEncryptedPrefs(context)?.getString(KEY_PHOTO_VIS, "EVERYONE") ?: "EVERYONE"
    }

    fun saveBioVis(context: Context, visibility: String) {
        getEncryptedPrefs(context)?.edit()?.putString(KEY_BIO_VIS, visibility)?.apply()
    }
    fun getBioVis(context: Context): String {
        return getEncryptedPrefs(context)?.getString(KEY_BIO_VIS, "EVERYONE") ?: "EVERYONE"
    }

    fun saveReadReceipts(context: Context, enabled: Boolean) {
        getEncryptedPrefs(context)?.edit()?.putBoolean(KEY_READ_RECEIPTS, enabled)?.apply()
    }
    fun getReadReceipts(context: Context): Boolean {
        return getEncryptedPrefs(context)?.getBoolean(KEY_READ_RECEIPTS, true) ?: true
    }

    fun saveAboutText(context: Context, text: String) {
        getEncryptedPrefs(context)?.edit()?.putString(KEY_ABOUT_TEXT, text)?.apply()
    }
    fun getAboutText(context: Context): String {
        return getEncryptedPrefs(context)?.getString(KEY_ABOUT_TEXT, "Hey there! I am using Plenxo Pro.") ?: "Hey there! I am using Plenxo Pro."
    }

    fun savePinnedChats(context: Context, chatIds: Set<String>) {
        getEncryptedPrefs(context)?.edit()?.putStringSet(KEY_PINNED_CHATS, chatIds)?.apply()
    }
    fun getPinnedChats(context: Context): Set<String> {
        return getEncryptedPrefs(context)?.getStringSet(KEY_PINNED_CHATS, emptySet()) ?: emptySet()
    }

    fun saveLocalOnlyMode(context: Context, enabled: Boolean) {
        getEncryptedPrefs(context)?.edit()?.putBoolean(KEY_LOCAL_ONLY_MODE, enabled)?.apply()
    }
    fun getLocalOnlyMode(context: Context): Boolean {
        return getEncryptedPrefs(context)?.getBoolean(KEY_LOCAL_ONLY_MODE, false) ?: false
    }

    fun saveDarkMode(context: Context, enabled: Boolean) {
        getEncryptedPrefs(context)?.edit()?.putBoolean("key_dark_mode", enabled)?.apply()
    }
    fun getDarkMode(context: Context): Boolean {
        return getEncryptedPrefs(context)?.getBoolean("key_dark_mode", true) ?: true
    }

    fun saveSessionId(context: Context, sessionId: String) {
        getEncryptedPrefs(context)?.edit()?.putString(KEY_SESSION_ID, sessionId)?.apply()
    }
    fun getSessionId(context: Context): String? {
        return getEncryptedPrefs(context)?.getString(KEY_SESSION_ID, null)
    }

    fun saveLoginState(context: Context, token: String, email: String) {
        try {
            getEncryptedPrefs(context)?.edit()?.apply {
                putBoolean(KEY_IS_LOGGED_IN, true)
                putString(KEY_USER_TOKEN, token)
                putString(KEY_USER_EMAIL, email)
                apply()
            }
            Log.d("SessionManager", "Saved login state successfully")
        } catch (e: Exception) {
            Log.e("SessionManager", "Error saving login state", e)
        }
    }

    fun getLoginState(context: Context): LoginState {
        return try {
            val prefs = getEncryptedPrefs(context)
            val isLoggedIn = prefs?.getBoolean(KEY_IS_LOGGED_IN, false) ?: false
            val token = prefs?.getString(KEY_USER_TOKEN, null)
            val email = prefs?.getString(KEY_USER_EMAIL, null)
            LoginState(isLoggedIn = isLoggedIn, token = token, email = email)
        } catch (e: Exception) {
            Log.e("SessionManager", "Error reading login state", e)
            LoginState(isLoggedIn = false, token = null, email = null)
        }
    }

    fun clearLoginState(context: Context) {
        try {
            getEncryptedPrefs(context)?.edit()?.apply {
                clear()
                apply()
            }
            Log.d("SessionManager", "Cleared login state successfully")
        } catch (e: Exception) {
            Log.e("SessionManager", "Error clearing login state", e)
        }
    }

    fun saveUserProfileLocally(
        context: Context,
        plenxoId: String,
        displayName: String,
        bio: String,
        profilePicUrl: String,
        age: String = ""
    ) {
        try {
            getEncryptedPrefs(context)?.edit()?.apply {
                if (plenxoId.isNotBlank()) putString(KEY_PLENXO_ID, plenxoId)
                if (displayName.isNotBlank()) putString(KEY_DISPLAY_NAME, displayName)
                if (bio.isNotBlank()) putString(KEY_BIO, bio)
                if (profilePicUrl.isNotBlank()) putString(KEY_PROFILE_PIC_URL, profilePicUrl)
                if (age.isNotBlank()) putString(KEY_USER_AGE, age)
                apply()
            }
            Log.d("SessionManager", "Saved user profile locally: plenxoId=$plenxoId, name=$displayName")
        } catch (e: Exception) {
            Log.e("SessionManager", "Error saving user profile locally", e)
        }
    }

    fun getLocalPlenxoId(context: Context): String =
        getEncryptedPrefs(context)?.getString(KEY_PLENXO_ID, "") ?: ""

    fun getLocalDisplayName(context: Context): String =
        getEncryptedPrefs(context)?.getString(KEY_DISPLAY_NAME, "") ?: ""

    fun getLocalBio(context: Context): String =
        getEncryptedPrefs(context)?.getString(KEY_BIO, "") ?: ""

    fun getLocalProfilePicUrl(context: Context): String =
        getEncryptedPrefs(context)?.getString(KEY_PROFILE_PIC_URL, "") ?: ""

    fun getLocalAge(context: Context): String =
        getEncryptedPrefs(context)?.getString(KEY_USER_AGE, "") ?: ""

    fun saveScreenshotsBlocked(context: Context, blocked: Boolean) {
        getEncryptedPrefs(context)?.edit()?.putBoolean(KEY_BLOCK_SCREENSHOTS, blocked)?.apply()
    }
    fun isScreenshotsBlocked(context: Context): Boolean {
        return getEncryptedPrefs(context)?.getBoolean(KEY_BLOCK_SCREENSHOTS, false) ?: false
    }


    fun saveAppLockType(context: Context, type: String) {
        getEncryptedPrefs(context)?.edit()?.putString(KEY_APPLOCK_TYPE, type)?.apply()
    }
    fun getAppLockType(context: Context): String {
        return getEncryptedPrefs(context)?.getString(KEY_APPLOCK_TYPE, "PIN") ?: "PIN"
    }

    fun saveAppLockCredential(context: Context, hash: String) {
        getEncryptedPrefs(context)?.edit()?.putString(KEY_APPLOCK_CREDENTIAL, hash)?.apply()
    }
    fun getAppLockCredential(context: Context): String? {
        return getEncryptedPrefs(context)?.getString(KEY_APPLOCK_CREDENTIAL, null)
    }

    fun saveProfileRingId(context: Context, ringId: String) {
        getEncryptedPrefs(context)?.edit()?.putString("key_profile_ring_id", ringId)?.apply()
    }
    fun getProfileRingId(context: Context): String {
        return getEncryptedPrefs(context)?.getString("key_profile_ring_id", "none") ?: "none"
    }

    fun saveOnboardingCompleted(context: Context, completed: Boolean) {
        getEncryptedPrefs(context)?.edit()?.putBoolean("key_onboarding_completed", completed)?.apply()
    }
    fun isOnboardingCompleted(context: Context): Boolean {
        return getEncryptedPrefs(context)?.getBoolean("key_onboarding_completed", false) ?: false
    }

    fun saveChatLock(context: Context, chatId: String, type: String, credential: String) {
        getEncryptedPrefs(context)?.edit()?.apply {
            putString("key_chatlock_type_$chatId", type)
            putString("key_chatlock_credential_$chatId", credential)
            apply()
        }
    }

    fun getChatLockType(context: Context, chatId: String): String? {
        return getEncryptedPrefs(context)?.getString("key_chatlock_type_$chatId", null)
    }

    fun getChatLockCredential(context: Context, chatId: String): String? {
        return getEncryptedPrefs(context)?.getString("key_chatlock_credential_$chatId", null)
    }

    fun removeChatLock(context: Context, chatId: String) {
        getEncryptedPrefs(context)?.edit()?.apply {
            remove("key_chatlock_type_$chatId")
            remove("key_chatlock_credential_$chatId")
            apply()
        }
    }

    fun getFailedPasswordAttempts(context: Context, identifier: String): Int {
        val sanitized = identifier.trim().lowercase()
        return getEncryptedPrefs(context)?.getInt("failed_attempts_$sanitized", 0) ?: 0
    }

    fun incrementFailedPasswordAttempts(context: Context, identifier: String): Int {
        val sanitized = identifier.trim().lowercase()
        val current = getFailedPasswordAttempts(context, sanitized)
        val next = current + 1
        getEncryptedPrefs(context)?.edit()?.putInt("failed_attempts_$sanitized", next)?.apply()
        return next
    }

    fun resetFailedPasswordAttempts(context: Context, identifier: String) {
        val sanitized = identifier.trim().lowercase()
        getEncryptedPrefs(context)?.edit()?.putInt("failed_attempts_$sanitized", 0)?.apply()
        getEncryptedPrefs(context)?.edit()?.putLong("lockout_until_$sanitized", 0L)?.apply()
    }

    fun getLockoutUntil(context: Context, identifier: String): Long {
        val sanitized = identifier.trim().lowercase()
        return getEncryptedPrefs(context)?.getLong("lockout_until_$sanitized", 0L) ?: 0L
    }

    fun saveLockoutUntil(context: Context, identifier: String, timestamp: Long) {
        val sanitized = identifier.trim().lowercase()
        getEncryptedPrefs(context)?.edit()?.putLong("lockout_until_$sanitized", timestamp)?.apply()
    }
    fun getUserProfileLocally(context: Context): LocalUserProfile {
        return try {
            LocalUserProfile(
                plenxoId = getLocalPlenxoId(context),
                displayName = getLocalDisplayName(context),
                bio = getLocalBio(context),
                profilePicUrl = getLocalProfilePicUrl(context),
                age = getLocalAge(context)
            )
        } catch (_: Exception) {
            LocalUserProfile()
        }
    }
}

data class LocalUserProfile(
    val plenxoId: String = "",
    val displayName: String = "",
    val bio: String = "",
    val profilePicUrl: String = "",
    val age: String = ""
)

data class LoginState(
    val isLoggedIn: Boolean,
    val token: String?,
    val email: String?
)
