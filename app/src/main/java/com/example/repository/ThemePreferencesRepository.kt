package com.example.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromString(value: String?): AppThemeMode {
            return when (value?.uppercase()) {
                "LIGHT" -> LIGHT
                "DARK" -> DARK
                else -> SYSTEM
            }
        }
    }
}

class ThemePreferencesRepository {

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    suspend fun saveThemePreference(mode: AppThemeMode): Boolean {
        val uid = currentUserId
        if (uid.isEmpty()) {
            Log.e("ThemePreferencesRepo", "User not authenticated")
            return false
        }

        return try {
            val modeName = mode.name
            val updates = mapOf(
                "themeMode" to modeName,
                "appTheme" to modeName,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("users").document(uid)
                .set(updates, SetOptions.merge())
                .await()

            Log.d("ThemePreferencesRepo", "Theme mode saved to Firestore: $modeName for user $uid")
            true
        } catch (e: Exception) {
            Log.e("ThemePreferencesRepo", "Failed to save theme preference: ${e.message}", e)
            false
        }
    }

    fun getThemePreferenceFlow(): Flow<AppThemeMode> = callbackFlow {
        val uid = currentUserId
        if (uid.isEmpty()) {
            trySend(AppThemeMode.SYSTEM)
            close()
            return@callbackFlow
        }

        val docRef = firestore.collection("users").document(uid)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("ThemePreferencesRepo", "Error observing theme preference: ${error.message}")
                trySend(AppThemeMode.SYSTEM)
                return@addSnapshotListener
            }

            val themeStr = snapshot?.getString("themeMode")
                ?: snapshot?.getString("appTheme")
                ?: "SYSTEM"

            trySend(AppThemeMode.fromString(themeStr))
        }

        awaitClose {
            listener.remove()
        }
    }

    suspend fun fetchThemePreference(): AppThemeMode {
        val uid = currentUserId
        if (uid.isEmpty()) return AppThemeMode.SYSTEM

        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            val themeStr = doc.getString("themeMode")
                ?: doc.getString("appTheme")
                ?: "SYSTEM"
            AppThemeMode.fromString(themeStr)
        } catch (e: Exception) {
            Log.e("ThemePreferencesRepo", "Failed to fetch theme preference: ${e.message}")
            AppThemeMode.SYSTEM
        }
    }
}
