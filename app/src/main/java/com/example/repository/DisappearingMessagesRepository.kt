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

class DisappearingMessagesRepository {

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    suspend fun saveDefaultDisappearingTimer(durationMs: Long): Boolean {
        val uid = currentUserId
        if (uid.isEmpty()) {
            Log.e("DisappearingMessagesRepo", "Cannot set disappearing timer: user not authenticated")
            return false
        }

        return try {
            val updates = mapOf(
                "disappearingTimerMs" to durationMs,
                "defaultDisappearingDuration" to durationMs,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("users").document(uid)
                .set(updates, SetOptions.merge())
                .await()

            Log.d("DisappearingMessagesRepo", "Saved default disappearing timer: ${durationMs}ms for $uid")
            true
        } catch (e: Exception) {
            Log.e("DisappearingMessagesRepo", "Failed to save disappearing timer: ${e.message}", e)
            false
        }
    }

    fun getDefaultDisappearingTimerFlow(): Flow<Long> = callbackFlow {
        val uid = currentUserId
        if (uid.isEmpty()) {
            trySend(0L)
            close()
            return@callbackFlow
        }

        val docRef = firestore.collection("users").document(uid)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("DisappearingMessagesRepo", "Error observing disappearing timer: ${error.message}")
                trySend(0L)
                return@addSnapshotListener
            }

            val timerVal = snapshot?.get("disappearingTimerMs")
                ?: snapshot?.get("defaultDisappearingDuration")
                ?: 0L

            val timerMs = when (timerVal) {
                is Number -> timerVal.toLong()
                is String -> timerVal.toLongOrNull() ?: 0L
                else -> 0L
            }

            trySend(timerMs)
        }

        awaitClose {
            listener.remove()
        }
    }

    suspend fun fetchDefaultDisappearingTimer(): Long {
        val uid = currentUserId
        if (uid.isEmpty()) return 0L
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            val timerVal = doc.get("disappearingTimerMs") ?: doc.get("defaultDisappearingDuration")
            when (timerVal) {
                is Number -> timerVal.toLong()
                is String -> timerVal.toLongOrNull() ?: 0L
                else -> 0L
            }
        } catch (e: Exception) {
            Log.e("DisappearingMessagesRepo", "Failed to fetch disappearing timer: ${e.message}")
            0L
        }
    }

    suspend fun purgeExpiredMessagesForChat(chatId: String): Int {
        if (chatId.isEmpty()) return 0
        val now = System.currentTimeMillis()

        return try {
            val querySnapshot = firestore.collection("messages")
                .whereEqualTo("chatId", chatId)
                .whereLessThan("expiresAt", now)
                .get()
                .await()

            if (querySnapshot.isEmpty) return 0

            val batch = firestore.batch()
            var count = 0
            for (doc in querySnapshot.documents) {
                val expiresAt = doc.getLong("expiresAt")
                if (expiresAt != null && expiresAt > 0L && expiresAt <= now) {
                    batch.delete(doc.reference)
                    count++
                }
            }

            if (count > 0) {
                batch.commit().await()
                Log.d("DisappearingMessagesRepo", "Purged $count expired messages for chat $chatId")
            }
            count
        } catch (e: Exception) {
            Log.e("DisappearingMessagesRepo", "Failed to purge expired messages for chat $chatId: ${e.message}", e)
            0
        }
    }

    suspend fun setChatRoomDisappearingTimer(chatId: String, durationMs: Long): Boolean {
        if (chatId.isEmpty()) return false
        return try {
            val updates = mapOf(
                "disappearingTimerMs" to durationMs,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("chats").document(chatId)
                .set(updates, SetOptions.merge())
                .await()
            Log.d("DisappearingMessagesRepo", "Set chat $chatId disappearing timer to ${durationMs}ms")
            true
        } catch (e: Exception) {
            Log.e("DisappearingMessagesRepo", "Failed to set chat room disappearing timer: ${e.message}", e)
            false
        }
    }
}
