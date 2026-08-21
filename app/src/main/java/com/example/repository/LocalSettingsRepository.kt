package com.example.repository

import kotlinx.coroutines.flow.Flow

interface LocalSettingsRepository {
    val themeFlow: Flow<String> // "light", "dark", "system"
    val hapticFeedbackFlow: Flow<Boolean>
    val notificationSoundsFlow: Flow<Boolean>
    val notificationRingtoneFlow: Flow<String> // Resource name
    val chatWallpaperUriFlow: Flow<String?>
    val isLocalOnlyEnabledFlow: Flow<Boolean>
    val languageFlow: Flow<String> // "en", "es", "fr", "ur", "ar"
    
    suspend fun setTheme(theme: String)
    suspend fun setHapticFeedbackEnabled(enabled: Boolean)
    suspend fun setNotificationSoundsEnabled(enabled: Boolean)
    suspend fun setNotificationRingtone(ringtone: String)
    suspend fun setChatWallpaperUri(uri: String?)
    suspend fun setLocalOnlyEnabled(enabled: Boolean)
    suspend fun setLanguage(language: String)
    suspend fun clearCache()
}
