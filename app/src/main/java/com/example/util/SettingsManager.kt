package com.example.util

import android.content.Context
import android.content.SharedPreferences

class SettingsManager private constructor(context: Context) {

    private val sharedPreferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        const val PREFS_NAME = "app_settings"

        // Keys
        const val KEY_SAVE_TO_GALLERY = "save_to_gallery"
        const val KEY_ENTER_IS_SEND = "enter_is_send"
        const val KEY_MUTE_NOTIFICATIONS = "mute_notifications"
        const val KEY_POPUP_NOTIFICATIONS = "popup_notifications"
        const val KEY_NOTIFICATION_SOUND = "notification_ringtone_sp"
        const val KEY_LANGUAGE_SELECTION = "language_selection"
        const val KEY_SCREEN_LOCK_TIMEOUT = "screen_lock_timeout"
        const val KEY_LINK_PREVIEWS_ENABLED = "link_previews_enabled"
        const val KEY_DND_ENABLED = "dnd_enabled"
        const val KEY_DND_START_TIME = "dnd_start_time"
        const val KEY_DND_END_TIME = "dnd_end_time"

        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context).also { INSTANCE = it }
            }
        }
    }

    // 1. Save to Gallery
    fun isSaveToGalleryEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_SAVE_TO_GALLERY, true)
    }

    fun setSaveToGalleryEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SAVE_TO_GALLERY, enabled).apply()
    }

    // 2. Enter is Send
    fun isEnterIsSendEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_ENTER_IS_SEND, false)
    }

    fun setEnterIsSendEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_ENTER_IS_SEND, enabled).apply()
    }

    // 3. Mute Notifications
    fun isMuteNotificationsEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_MUTE_NOTIFICATIONS, false)
    }

    fun setMuteNotificationsEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_MUTE_NOTIFICATIONS, enabled).apply()
    }

    // 4. Popup Notification (High Priority)
    fun isPopupNotificationsEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_POPUP_NOTIFICATIONS, true)
    }

    fun setPopupNotificationsEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_POPUP_NOTIFICATIONS, enabled).apply()
    }

    // 5. Notification Sound
    fun getNotificationSound(): String {
        return sharedPreferences.getString(KEY_NOTIFICATION_SOUND, "minimal_ping") ?: "minimal_ping"
    }

    fun setNotificationSound(context: Context, soundName: String) {
        // Delegate to NotificationHelper to update SharedPreferences and recreate the channel immediately
        NotificationHelper.saveSelectedSound(context, soundName)
    }

    // 6. Language Selection
    fun getLanguageSelection(): String {
        return sharedPreferences.getString(KEY_LANGUAGE_SELECTION, "English") ?: "English"
    }

    fun setLanguageSelection(language: String) {
        sharedPreferences.edit().putString(KEY_LANGUAGE_SELECTION, language).apply()
    }

    // 7. Screen Lock Timeout (in milliseconds, default is 0L - Immediately)
    fun getScreenLockTimeout(): Long {
        return sharedPreferences.getLong(KEY_SCREEN_LOCK_TIMEOUT, 0L)
    }

    fun setScreenLockTimeout(timeoutMs: Long) {
        sharedPreferences.edit().putLong(KEY_SCREEN_LOCK_TIMEOUT, timeoutMs).apply()
    }

    // 8. Link Previews Enabled (default is true)
    fun isLinkPreviewsEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_LINK_PREVIEWS_ENABLED, true)
    }

    fun setLinkPreviewsEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_LINK_PREVIEWS_ENABLED, enabled).apply()
    }

    // 9. Do Not Disturb Enabled
    fun isDndEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_DND_ENABLED, false)
    }

    fun setDndEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_DND_ENABLED, enabled).apply()
    }

    // 10. DND Start Time
    fun getDndStartTime(): String {
        return sharedPreferences.getString(KEY_DND_START_TIME, "22:00") ?: "22:00"
    }

    fun setDndStartTime(time: String) {
        sharedPreferences.edit().putString(KEY_DND_START_TIME, time).apply()
    }

    // 11. DND End Time
    fun getDndEndTime(): String {
        return sharedPreferences.getString(KEY_DND_END_TIME, "07:00") ?: "07:00"
    }

    fun setDndEndTime(time: String) {
        sharedPreferences.edit().putString(KEY_DND_END_TIME, time).apply()
    }

    // Checks if the current system time lies inside the configured DND quiet hours
    fun isCurrentTimeInQuietHours(): Boolean {
        if (!isDndEnabled()) return false
        try {
            val startStr = getDndStartTime() // e.g. "22:00"
            val endStr = getDndEndTime() // e.g. "07:00"
            
            val startParts = startStr.split(":")
            val endParts = endStr.split(":")
            if (startParts.size < 2 || endParts.size < 2) return false
            
            val startMin = startParts[0].toInt() * 60 + startParts[1].toInt()
            val endMin = endParts[0].toInt() * 60 + endParts[1].toInt()
            
            val cal = java.util.Calendar.getInstance()
            val currentMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            
            return if (startMin < endMin) {
                currentMin in startMin..endMin
            } else {
                currentMin >= startMin || currentMin <= endMin
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
