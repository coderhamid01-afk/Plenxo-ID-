@file:Suppress("DEPRECATION")
package com.example.repository

import android.content.Context
import android.util.Log
import com.example.database.AppDatabase
import com.example.model.LocalMessage
import com.example.model.MessagePayload
import com.example.util.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

class ChatRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val localMessageDao = database.localMessageDao()
    private val localSettingsRepo = com.example.repository.LocalSettingsRepositoryImpl(context)

    private val firebaseAuth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    suspend fun isLocalOnlyMode(): Boolean {
        return try {
            localSettingsRepo.isLocalOnlyEnabledFlow.first()
        } catch (e: Exception) {
            SessionManager.getLocalOnlyMode(context)
        }
    }

    fun getLocalMessages(chatId: String, limit: Int = 50, offset: Int = 0): Flow<List<LocalMessage>> {
        return localMessageDao.getMessagesForChatPaginated(chatId, limit, offset)
    }

    suspend fun getMoreLocalMessages(chatId: String, limit: Int = 50, offset: Int): List<LocalMessage> {
        return localMessageDao.getMessagesForChatStatic(chatId, limit, offset)
    }

    suspend fun insertLocalMessage(message: LocalMessage) {
        localMessageDao.insertMessage(message)
    }

    suspend fun updateLocalMessage(message: LocalMessage) {
        localMessageDao.updateMessage(message)
    }

    suspend fun deleteLocalMessage(messageId: String) {
        localMessageDao.deleteMessage(messageId)
    }

    suspend fun deleteExpiredMessages(currentTime: Long): Int {
        return localMessageDao.deleteExpiredMessages(currentTime)
    }

    // Remote (Firestore) Operations
    fun getRemoteMessages(chatId: String, limit: Int = 50, offset: Int = 0): Flow<List<MessagePayload>> = callbackFlow {
        val currentUid = firebaseAuth.currentUser?.uid ?: ""
        if (currentUid.isEmpty()) {
            close(Exception("User not authenticated"))
            return@callbackFlow
        }

        val query = firestore.collection("messages")
            .whereEqualTo("chatId", chatId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                trySend(emptyList())
                return@addSnapshotListener
            }

            val messages = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                MessagePayload(
                    messageId = (data["messageId"] as? String) ?: doc.id,
                    chatId = (data["chatId"] as? String) ?: chatId,
                    senderId = (data["senderId"] as? String) ?: "",
                    receiverId = (data["receiverId"] as? String) ?: "",
                    messageText = (data["messageText"] as? String) ?: "",
                    messageType = (data["messageType"] as? String) ?: "TEXT",
                    mediaUrl = (data["mediaUrl"] as? String) ?: "",
                    timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis()
                )
            }.reversed()

            trySend(messages)
        }

        awaitClose {
            listener.remove()
        }
    }

    suspend fun getMoreRemoteMessages(chatId: String, limit: Int = 50, offset: Int): List<MessagePayload> {
        return try {
            val snapshot = firestore.collection("messages")
                .whereEqualTo("chatId", chatId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                MessagePayload(
                    messageId = (data["messageId"] as? String) ?: doc.id,
                    chatId = (data["chatId"] as? String) ?: chatId,
                    senderId = (data["senderId"] as? String) ?: "",
                    receiverId = (data["receiverId"] as? String) ?: "",
                    messageText = (data["messageText"] as? String) ?: "",
                    messageType = (data["messageType"] as? String) ?: "TEXT",
                    mediaUrl = (data["mediaUrl"] as? String) ?: "",
                    timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis()
                )
            }.reversed()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun sendMessage(chatId: String, payload: MessagePayload) {
        if (isLocalOnlyMode()) {
            Log.d("ChatRepository", "Privacy Mode: Bypassing remote sendMessage")
            return
        }
        val currentUid = firebaseAuth.currentUser?.uid ?: ""
        if (currentUid.isEmpty()) {
            throw Exception("Not authenticated")
        }
        try {
            val messageData = mapOf(
                "messageId" to payload.messageId,
                "chatId" to payload.chatId,
                "senderId" to payload.senderId,
                "receiverId" to payload.receiverId,
                "messageText" to payload.messageText,
                "messageType" to payload.messageType,
                "mediaUrl" to payload.mediaUrl,
                "timestamp" to payload.timestamp,
                "status" to "SENT"
            )
            firestore.collection("messages").document(payload.messageId).set(messageData).await()
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to send message: ${e.message}", e)
            throw e
        }
    }
}
