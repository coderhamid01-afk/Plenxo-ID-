package com.example.database

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.model.DeliveryStatus
import com.example.model.Message
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class OfflineMessageWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("OfflineMessageWorker", "OfflineMessageWorker triggered - checking for pending messages.")
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.localMessageDao()
        val firestore = FirebaseFirestore.getInstance()

        try {
            val pendingMessages = dao.getPendingSendingMessages()
            Log.d("OfflineMessageWorker", "Found ${pendingMessages.size} pending messages to sync.")

            if (pendingMessages.isEmpty()) {
                return Result.success()
            }

            for (localMsg in pendingMessages) {
                val firestoreMsg = Message(
                    messageId = localMsg.messageId,
                    chatId = localMsg.chatId,
                    senderId = localMsg.senderId,
                    receiverId = localMsg.receiverId,
                    messageText = localMsg.messageText,
                    timestamp = localMsg.timestamp,
                    status = "SENT",
                    deliveryStatus = DeliveryStatus.SENT,
                    messageType = localMsg.messageType,
                    replyToMessageId = localMsg.replyToMessageId,
                    isEdited = localMsg.isEdited,
                    originalContentHistory = emptyList(),
                    expiresAt = localMsg.expiresAt
                )

                firestore.collection("messages").document(localMsg.messageId).set(firestoreMsg).await()

                val chatRoomUpdate = mapOf(
                    "lastMessage" to localMsg.messageText,
                    "lastMessageTimestamp" to localMsg.timestamp
                )
                firestore.collection("chats").document(localMsg.chatId).update(chatRoomUpdate).await()

                dao.updateMessageStatus(localMsg.messageId, "SENT")
                Log.d("OfflineMessageWorker", "Successfully synced message: ${localMsg.messageId}")
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e("OfflineMessageWorker", "Error during message sync retry", e)
            return Result.retry()
        }
    }
}
