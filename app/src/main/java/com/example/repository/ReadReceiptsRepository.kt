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

class ReadReceiptsRepository {

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    suspend fun setReadReceiptsEnabled(enabled: Boolean): Boolean {
        val uid = currentUserId
        if (uid.isEmpty()) {
            Log.e("ReadReceiptsRepo", "Cannot update read receipts: user not authenticated")
            return false
        }

        return try {
            val updates = mapOf(
                "readReceiptsEnabled" to enabled,
                "read_receipts_enabled" to enabled,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("users").document(uid)
                .set(updates, SetOptions.merge())
                .await()

            Log.d("ReadReceiptsRepo", "Read receipts setting updated to: $enabled for user $uid")
            true
        } catch (e: Exception) {
            Log.e("ReadReceiptsRepo", "Failed to update read receipts setting: ${e.message}", e)
            false
        }
    }

    fun getReadReceiptsEnabledFlow(): Flow<Boolean> = callbackFlow {
        val uid = currentUserId
        if (uid.isEmpty()) {
            trySend(true)
            close()
            return@callbackFlow
        }

        val docRef = firestore.collection("users").document(uid)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("ReadReceiptsRepo", "Error listening for read receipts setting: ${error.message}")
                trySend(true)
                return@addSnapshotListener
            }

            val enabled = snapshot?.getBoolean("readReceiptsEnabled")
                ?: snapshot?.getBoolean("read_receipts_enabled")
                ?: true

            trySend(enabled)
        }

        awaitClose {
            listener.remove()
        }
    }

    suspend fun isReadReceiptsEnabled(userId: String): Boolean {
        if (userId.isEmpty()) return true
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            doc.getBoolean("readReceiptsEnabled")
                ?: doc.getBoolean("read_receipts_enabled")
                ?: true
        } catch (e: Exception) {
            Log.e("ReadReceiptsRepo", "Failed to fetch read receipts setting for user $userId: ${e.message}")
            true
        }
    }

    suspend fun markMessageAsRead(messageId: String, senderId: String): Boolean {
        val uid = currentUserId
        if (uid.isEmpty() || messageId.isEmpty()) return false

        return try {
            val isMyReadReceiptsEnabled = isReadReceiptsEnabled(uid)
            val isSenderReadReceiptsEnabled = if (senderId.isNotEmpty()) isReadReceiptsEnabled(senderId) else true

            if (!isMyReadReceiptsEnabled || !isSenderReadReceiptsEnabled) {
                Log.d("ReadReceiptsRepo", "Read receipt suppressed due to privacy setting")
                return false
            }

            val msgRef = firestore.collection("messages").document(messageId)
            msgRef.update(
                mapOf(
                    "status" to "READ",
                    "readTimestamp" to System.currentTimeMillis()
                )
            ).await()
            true
        } catch (e: Exception) {
            Log.e("ReadReceiptsRepo", "Failed to mark message $messageId as read: ${e.message}", e)
            false
        }
    }

    suspend fun markBatchMessagesAsRead(messageIds: List<String>): Boolean {
        val uid = currentUserId
        if (uid.isEmpty() || messageIds.isEmpty()) return false

        return try {
            val isEnabled = isReadReceiptsEnabled(uid)
            if (!isEnabled) {
                Log.d("ReadReceiptsRepo", "Batch read receipts suppressed by local privacy setting")
                return false
            }

            val batch = firestore.batch()
            val now = System.currentTimeMillis()
            messageIds.forEach { id ->
                val ref = firestore.collection("messages").document(id)
                batch.update(ref, mapOf(
                    "status" to "READ",
                    "readTimestamp" to now
                ))
            }
            batch.commit().await()
            true
        } catch (e: Exception) {
            Log.e("ReadReceiptsRepo", "Failed batch marking messages as read: ${e.message}", e)
            false
        }
    }
}
