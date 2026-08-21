package com.example.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.model.NotificationSoundProfile

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {
    companion object {
        val LOCAL_STORAGE_ONLY_KEY = booleanPreferencesKey("local_storage_only")
        val SELECTED_NOTIFICATION_SOUND_KEY = stringPreferencesKey("selected_notification_sound")
    }

    val localStorageOnlyFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[LOCAL_STORAGE_ONLY_KEY] ?: false
        }

    val selectedSoundFlow: Flow<NotificationSoundProfile> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val key = preferences[SELECTED_NOTIFICATION_SOUND_KEY]
            NotificationSoundProfile.fromSystemKey(key)
        }

    suspend fun setLocalStorageOnly(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LOCAL_STORAGE_ONLY_KEY] = value
        }
    }

    suspend fun setSelectedSound(profile: NotificationSoundProfile) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_NOTIFICATION_SOUND_KEY] = profile.systemKey
        }
    }
}
