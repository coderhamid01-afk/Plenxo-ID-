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

data class AppFontPreference(
    val fontId: String = "DEFAULT",
    val fontName: String = "Default System Font",
    val fontSizeScale: Float = 1.0f
)

class FontPreferencesRepository {

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    suspend fun saveFontPreference(fontId: String, fontName: String = "", scale: Float = 1.0f): Boolean {
        val uid = currentUserId
        if (uid.isEmpty()) {
            Log.e("FontPreferencesRepo", "User not authenticated")
            return false
        }

        return try {
            val normalizedFontId = fontId.ifEmpty { "DEFAULT" }
            val updates = mapOf(
                "activeFontId" to normalizedFontId,
                "activeFontName" to fontName.ifEmpty { normalizedFontId },
                "fontSizeScale" to scale,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("users").document(uid)
                .set(updates, SetOptions.merge())
                .await()

            Log.d("FontPreferencesRepo", "Font preference saved for user $uid: $normalizedFontId (scale: $scale)")
            true
        } catch (e: Exception) {
            Log.e("FontPreferencesRepo", "Failed to save font preference: ${e.message}", e)
            false
        }
    }

    fun getFontPreferenceFlow(): Flow<AppFontPreference> = callbackFlow {
        val uid = currentUserId
        if (uid.isEmpty()) {
            trySend(AppFontPreference())
            close()
            return@callbackFlow
        }

        val docRef = firestore.collection("users").document(uid)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("FontPreferencesRepo", "Error listening for font preferences: ${error.message}")
                trySend(AppFontPreference())
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists()) {
                trySend(AppFontPreference())
                return@addSnapshotListener
            }

            val fontId = snapshot.getString("activeFontId") ?: "DEFAULT"
            val fontName = snapshot.getString("activeFontName") ?: "Default System Font"
            val scaleVal = snapshot.get("fontSizeScale")
            val scale = when (scaleVal) {
                is Number -> scaleVal.toFloat()
                is String -> scaleVal.toFloatOrNull() ?: 1.0f
                else -> 1.0f
            }

            val fontPref = AppFontPreference(
                fontId = fontId,
                fontName = fontName,
                fontSizeScale = scale
            )

            trySend(fontPref)
        }

        awaitClose {
            listener.remove()
        }
    }

    suspend fun fetchFontPreference(): AppFontPreference {
        val uid = currentUserId
        if (uid.isEmpty()) return AppFontPreference()

        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            if (!doc.exists()) return AppFontPreference()

            val fontId = doc.getString("activeFontId") ?: "DEFAULT"
            val fontName = doc.getString("activeFontName") ?: "Default System Font"
            val scaleVal = doc.get("fontSizeScale")
            val scale = when (scaleVal) {
                is Number -> scaleVal.toFloat()
                is String -> scaleVal.toFloatOrNull() ?: 1.0f
                else -> 1.0f
            }

            AppFontPreference(
                fontId = fontId,
                fontName = fontName,
                fontSizeScale = scale
            )
        } catch (e: Exception) {
            Log.e("FontPreferencesRepo", "Failed to fetch font preference: ${e.message}")
            AppFontPreference()
        }
    }
}
