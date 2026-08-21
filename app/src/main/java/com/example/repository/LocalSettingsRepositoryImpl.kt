package com.example.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException

private val Context.localSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "local_device_settings")

class LocalSettingsRepositoryImpl(private val context: Context) : LocalSettingsRepository {

    companion object {
        val KEY_THEME = stringPreferencesKey("app_theme")
        val KEY_HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val KEY_NOTIFICATION_SOUNDS = booleanPreferencesKey("notification_sounds")
        val KEY_NOTIFICATION_RINGTONE = stringPreferencesKey("notification_ringtone")
        val KEY_CHAT_WALLPAPER_URI = stringPreferencesKey("chat_wallpaper_uri")
        val KEY_IS_LOCAL_ONLY = booleanPreferencesKey("is_local_only")
        val KEY_LANGUAGE = stringPreferencesKey("app_language")
    }

    override val themeFlow: Flow<String> = context.localSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_THEME] ?: "system"
        }

    override val hapticFeedbackFlow: Flow<Boolean> = context.localSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_HAPTIC_FEEDBACK] ?: true
        }

    override val notificationSoundsFlow: Flow<Boolean> = context.localSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_NOTIFICATION_SOUNDS] ?: true
        }

    override val notificationRingtoneFlow: Flow<String> = context.localSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_NOTIFICATION_RINGTONE] ?: "minimal_ping"
        }

    override val chatWallpaperUriFlow: Flow<String?> = context.localSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_CHAT_WALLPAPER_URI]
        }

    override val isLocalOnlyEnabledFlow: Flow<Boolean> = context.localSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_IS_LOCAL_ONLY] ?: false
        }

    override val languageFlow: Flow<String> = context.localSettingsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_LANGUAGE] ?: "en"
        }

    override suspend fun setTheme(theme: String) {
        context.localSettingsDataStore.edit { preferences ->
            preferences[KEY_THEME] = theme
        }
    }

    override suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        context.localSettingsDataStore.edit { preferences ->
            preferences[KEY_HAPTIC_FEEDBACK] = enabled
        }
    }

    override suspend fun setNotificationSoundsEnabled(enabled: Boolean) {
        context.localSettingsDataStore.edit { preferences ->
            preferences[KEY_NOTIFICATION_SOUNDS] = enabled
        }
    }

    override suspend fun setNotificationRingtone(ringtone: String) {
        context.localSettingsDataStore.edit { preferences ->
            preferences[KEY_NOTIFICATION_RINGTONE] = ringtone
        }
    }

    override suspend fun setChatWallpaperUri(uri: String?) {
        context.localSettingsDataStore.edit { preferences ->
            if (uri != null) {
                preferences[KEY_CHAT_WALLPAPER_URI] = uri
            } else {
                preferences.remove(KEY_CHAT_WALLPAPER_URI)
            }
        }
    }

    override suspend fun setLocalOnlyEnabled(enabled: Boolean) {
        context.localSettingsDataStore.edit { preferences ->
            preferences[KEY_IS_LOCAL_ONLY] = enabled
        }
    }

    override suspend fun setLanguage(language: String) {
        context.localSettingsDataStore.edit { preferences ->
            preferences[KEY_LANGUAGE] = language
        }
    }

    override suspend fun clearCache() {
        try {
            val cacheDir = context.cacheDir
            deleteDirContents(cacheDir)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun deleteDirContents(dir: File?): Boolean {
        if (dir != null && dir.isDirectory) {
            val children = dir.list() ?: return false
            for (i in children.indices) {
                val success = deleteDirContents(File(dir, children[i]))
                if (!success) {
                    return false
                }
            }
            return dir.delete()
        } else if (dir != null && dir.isFile) {
            return dir.delete()
        }
        return false
    }
}
