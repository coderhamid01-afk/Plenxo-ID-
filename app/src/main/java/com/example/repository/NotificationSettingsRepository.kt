package com.example.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class NotificationPreferences(
    val notificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val showPreviews: Boolean = true,
    val fcmToken: String = ""
)

class NotificationSettingsRepository {

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private val fcm: FirebaseMessaging
        get() = FirebaseMessaging.getInstance()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    suspend fun updateNotificationPreferences(prefs: NotificationPreferences): Boolean {
        val uid = currentUserId
        if (uid.isEmpty()) {
            Log.e("NotificationSettingsRepo", "User not authenticated")
            return false
        }

        return try {
            val updates = mapOf(
                "notificationsEnabled" to prefs.notificationsEnabled,
                "soundEnabled" to prefs.soundEnabled,
                "vibrationEnabled" to prefs.vibrationEnabled,
                "showPreviews" to prefs.showPreviews,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("users").document(uid)
                .set(updates, SetOptions.merge())
                .await()

            if (prefs.notificationsEnabled) {
                syncFcmToken()
            }

            Log.d("NotificationSettingsRepo", "Notification settings updated for $uid: $prefs")
            true
        } catch (e: Exception) {
            Log.e("NotificationSettingsRepo", "Failed to update notification preferences: ${e.message}", e)
            false
        }
    }

    fun getNotificationPreferencesFlow(): Flow<NotificationPreferences> = callbackFlow {
        val uid = currentUserId
        if (uid.isEmpty()) {
            trySend(NotificationPreferences())
            close()
            return@callbackFlow
        }

        val docRef = firestore.collection("users").document(uid)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("NotificationSettingsRepo", "Error listening for notification preferences: ${error.message}")
                trySend(NotificationPreferences())
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists()) {
                trySend(NotificationPreferences())
                return@addSnapshotListener
            }

            val prefs = NotificationPreferences(
                notificationsEnabled = snapshot.getBoolean("notificationsEnabled") ?: true,
                soundEnabled = snapshot.getBoolean("soundEnabled") ?: true,
                vibrationEnabled = snapshot.getBoolean("vibrationEnabled") ?: true,
                showPreviews = snapshot.getBoolean("showPreviews") ?: true,
                fcmToken = snapshot.getString("fcmToken") ?: ""
            )

            trySend(prefs)
        }

        awaitClose {
            listener.remove()
        }
    }

    suspend fun syncFcmToken(): String? {
        val uid = currentUserId
        if (uid.isEmpty()) return null

        return try {
            val token = fcm.token.await()
            if (!token.isNullOrEmpty()) {
                val tokenData = mapOf(
                    "fcmToken" to token,
                    "lastTokenUpdate" to FieldValue.serverTimestamp()
                )

                firestore.collection("users").document(uid)
                    .set(tokenData, SetOptions.merge())
                    .await()

                Log.d("NotificationSettingsRepo", "FCM token synced successfully for $uid")
            }
            token
        } catch (e: Throwable) {
            Log.e("NotificationSettingsRepo", "Failed to sync FCM token: ${e.message}")
            null
        }
    }

    suspend fun subscribeToTopic(topicName: String): Boolean {
        return try {
            fcm.subscribeToTopic(topicName).await()
            val uid = currentUserId
            if (uid.isNotEmpty()) {
                firestore.collection("users").document(uid)
                    .update("subscribedTopics", FieldValue.arrayUnion(topicName))
                    .await()
            }
            Log.d("NotificationSettingsRepo", "Subscribed to FCM topic: $topicName")
            true
        } catch (e: Throwable) {
            Log.e("NotificationSettingsRepo", "Failed to subscribe to topic $topicName: ${e.message}")
            false
        }
    }

    suspend fun unsubscribeFromTopic(topicName: String): Boolean {
        return try {
            fcm.unsubscribeFromTopic(topicName).await()
            val uid = currentUserId
            if (uid.isNotEmpty()) {
                firestore.collection("users").document(uid)
                    .update("subscribedTopics", FieldValue.arrayRemove(topicName))
                    .await()
            }
            Log.d("NotificationSettingsRepo", "Unsubscribed from FCM topic: $topicName")
            true
        } catch (e: Throwable) {
            Log.e("NotificationSettingsRepo", "Failed to unsubscribe from topic $topicName: ${e.message}")
            false
        }
    }

    suspend fun restoreNotificationSettingsOnLogin(): NotificationPreferences {
        val uid = currentUserId
        if (uid.isEmpty()) return NotificationPreferences()

        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            val prefs = if (doc.exists()) {
                NotificationPreferences(
                    notificationsEnabled = doc.getBoolean("notificationsEnabled") ?: true,
                    soundEnabled = doc.getBoolean("soundEnabled") ?: true,
                    vibrationEnabled = doc.getBoolean("vibrationEnabled") ?: true,
                    showPreviews = doc.getBoolean("showPreviews") ?: true,
                    fcmToken = doc.getString("fcmToken") ?: ""
                )
            } else {
                NotificationPreferences()
            }

            syncFcmToken()
            prefs
        } catch (e: Exception) {
            Log.e("NotificationSettingsRepo", "Failed to restore notification settings on login: ${e.message}", e)
            NotificationPreferences()
        }
    }
}
