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

enum class AutoDownloadMode {
    WIFI_ONLY,
    WIFI_AND_CELLULAR,
    NEVER;

    companion object {
        fun fromString(value: String?): AutoDownloadMode {
            return when (value?.uppercase()) {
                "WIFI_AND_CELLULAR", "ALWAYS", "CELLULAR_AND_WIFI" -> WIFI_AND_CELLULAR
                "NEVER", "OFF", "DISABLED" -> NEVER
                else -> WIFI_ONLY
            }
        }
    }
}

data class GlobalAutoDownloadSettings(
    val photosMode: AutoDownloadMode = AutoDownloadMode.WIFI_ONLY,
    val voiceNotesMode: AutoDownloadMode = AutoDownloadMode.WIFI_AND_CELLULAR,
    val documentsMode: AutoDownloadMode = AutoDownloadMode.WIFI_ONLY,
    val globalMediaMode: AutoDownloadMode = AutoDownloadMode.WIFI_ONLY
)

class AutoDownloadPreferencesRepository {

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    suspend fun saveGlobalAutoDownloadMode(mode: AutoDownloadMode): Boolean {
        val uid = currentUserId
        if (uid.isEmpty()) {
            Log.e("AutoDownloadRepo", "User not authenticated")
            return false
        }

        return try {
            val modeStr = mode.name
            val updates = mapOf(
                "autoDownloadMode" to modeStr,
                "globalAutoDownload" to modeStr,
                "photosAutoDownload" to modeStr,
                "documentsAutoDownload" to modeStr,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("users").document(uid)
                .set(updates, SetOptions.merge())
                .await()

            Log.d("AutoDownloadRepo", "Global auto-download mode saved: $modeStr for user $uid")
            true
        } catch (e: Exception) {
            Log.e("AutoDownloadRepo", "Failed to save auto-download mode: ${e.message}", e)
            false
        }
    }

    suspend fun saveDetailedAutoDownloadSettings(settings: GlobalAutoDownloadSettings): Boolean {
        val uid = currentUserId
        if (uid.isEmpty()) return false

        return try {
            val updates = mapOf(
                "autoDownloadMode" to settings.globalMediaMode.name,
                "photosAutoDownload" to settings.photosMode.name,
                "voiceNotesAutoDownload" to settings.voiceNotesMode.name,
                "documentsAutoDownload" to settings.documentsMode.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("users").document(uid)
                .set(updates, SetOptions.merge())
                .await()

            Log.d("AutoDownloadRepo", "Detailed auto-download settings saved for user $uid")
            true
        } catch (e: Exception) {
            Log.e("AutoDownloadRepo", "Failed to save detailed auto-download settings: ${e.message}", e)
            false
        }
    }

    fun getAutoDownloadSettingsFlow(): Flow<GlobalAutoDownloadSettings> = callbackFlow {
        val uid = currentUserId
        if (uid.isEmpty()) {
            trySend(GlobalAutoDownloadSettings())
            close()
            return@callbackFlow
        }

        val docRef = firestore.collection("users").document(uid)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("AutoDownloadRepo", "Error listening for auto-download settings: ${error.message}")
                trySend(GlobalAutoDownloadSettings())
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists()) {
                trySend(GlobalAutoDownloadSettings())
                return@addSnapshotListener
            }

            val globalStr = snapshot.getString("autoDownloadMode") ?: snapshot.getString("globalAutoDownload") ?: "WIFI_ONLY"
            val photosStr = snapshot.getString("photosAutoDownload") ?: globalStr
            val voiceStr = snapshot.getString("voiceNotesAutoDownload") ?: "WIFI_AND_CELLULAR"
            val docsStr = snapshot.getString("documentsAutoDownload") ?: globalStr

            val settings = GlobalAutoDownloadSettings(
                photosMode = AutoDownloadMode.fromString(photosStr),
                voiceNotesMode = AutoDownloadMode.fromString(voiceStr),
                documentsMode = AutoDownloadMode.fromString(docsStr),
                globalMediaMode = AutoDownloadMode.fromString(globalStr)
            )

            trySend(settings)
        }

        awaitClose {
            listener.remove()
        }
    }

    suspend fun fetchAutoDownloadSettings(): GlobalAutoDownloadSettings {
        val uid = currentUserId
        if (uid.isEmpty()) return GlobalAutoDownloadSettings()

        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            if (!doc.exists()) return GlobalAutoDownloadSettings()

            val globalStr = doc.getString("autoDownloadMode") ?: doc.getString("globalAutoDownload") ?: "WIFI_ONLY"
            val photosStr = doc.getString("photosAutoDownload") ?: globalStr
            val voiceStr = doc.getString("voiceNotesAutoDownload") ?: "WIFI_AND_CELLULAR"
            val docsStr = doc.getString("documentsAutoDownload") ?: globalStr

            GlobalAutoDownloadSettings(
                photosMode = AutoDownloadMode.fromString(photosStr),
                voiceNotesMode = AutoDownloadMode.fromString(voiceStr),
                documentsMode = AutoDownloadMode.fromString(docsStr),
                globalMediaMode = AutoDownloadMode.fromString(globalStr)
            )
        } catch (e: Exception) {
            Log.e("AutoDownloadRepo", "Failed to fetch auto-download settings: ${e.message}")
            GlobalAutoDownloadSettings()
        }
    }
}
