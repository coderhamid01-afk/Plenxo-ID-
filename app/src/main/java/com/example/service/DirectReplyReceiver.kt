package com.example.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.example.MainActivity
import com.example.database.AppDatabase
import com.example.model.MessagePayload
import com.example.repository.CloudChatRepositoryImpl
import com.example.repository.LocalChatRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class DirectReplyReceiver : BroadcastReceiver() {

    companion object {
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val ACTION_DIRECT_REPLY = "com.example.ACTION_DIRECT_REPLY"
        const val ACTION_MARK_READ = "com.example.ACTION_MARK_READ"
        const val CHANNEL_ID = "plenxo_messages"
        private const val TAG = "DirectReplyReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val chatId = intent.getStringExtra("CHAT_ID") ?: intent.getStringExtra("chat_id") ?: ""
        val recipientUid = intent.getStringExtra("RECIPIENT_UID") ?: intent.getStringExtra("recipient_uid") ?: ""
        val notificationId = intent.getIntExtra("NOTIFICATION_ID", chatId.hashCode())
        val senderName = intent.getStringExtra("SENDER_NAME") ?: "Plenxo User"

        Log.d(TAG, "Broadcast received: action=$action, chatId=$chatId, recipientUid=$recipientUid")

        val notificationManager = NotificationManagerCompat.from(context)

        if (action == ACTION_MARK_READ || intent.hasExtra("MARK_READ")) {
            // Dismiss notification immediately
            try {
                notificationManager.cancel(notificationId)
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling notification: ${e.message}")
            }

            // Asynchronously update message status to READ
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    markMessagesAsRead(context, chatId, recipientUid)
                } catch (e: Exception) {
                    Log.e(TAG, "Error marking messages as read: ${e.message}", e)
                }
            }
            return
        }

        // Direct Reply processing
        val results: Bundle? = RemoteInput.getResultsFromIntent(intent)
        val replyText = results?.getCharSequence(KEY_TEXT_REPLY)?.toString()?.trim()

        if (!replyText.isNullOrBlank() && chatId.isNotBlank()) {
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            if (currentUid.isBlank()) {
                Log.w(TAG, "User not authenticated for direct reply")
                return
            }

            Log.d(TAG, "Direct reply text: '$replyText' to chat $chatId")

            // Send reply asynchronously on Dispatchers.IO without launching main Activity
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val messageId = UUID.randomUUID().toString()
                    val timestamp = System.currentTimeMillis()

                    val payload = MessagePayload(
                        messageId = messageId,
                        chatId = chatId,
                        senderId = currentUid,
                        receiverId = recipientUid,
                        messageText = replyText,
                        messageType = "TEXT",
                        mediaUrl = "",
                        timestamp = timestamp,
                        status = "SENT",
                        expiresAt = null,
                        senderActiveFontId = "DEFAULT"
                    )

                    // 1. Save in Cloud (Firestore)
                    try {
                        val cloudRepo = CloudChatRepositoryImpl(context)
                        cloudRepo.saveMessage(payload)
                    } catch (e: Exception) {
                        Log.w(TAG, "Cloud save for direct reply failed: ${e.message}")
                    }

                    // 2. Save in Local DB (Room)
                    try {
                        val localRepo = LocalChatRepositoryImpl(context)
                        localRepo.saveMessage(payload)
                    } catch (e: Exception) {
                        Log.w(TAG, "Local save for direct reply failed: ${e.message}")
                    }

                    // 3. Queue Realtime Database Notification for recipient
                    try {
                        val notifData = mapOf(
                            "sender_id" to currentUid,
                            "sender_name" to "Me",
                            "message_text" to replyText,
                            "chat_id" to chatId,
                            "timestamp" to timestamp
                        )
                        FirebaseDatabase.getInstance()
                            .getReference("notifications")
                            .child(recipientUid)
                            .child(messageId)
                            .setValue(notifData)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to queue recipient notification: ${e.message}")
                    }

                    Log.d(TAG, "Direct reply dispatched successfully for messageId $messageId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send direct reply", e)
                }
            }

            // Update Notification Shade to show MessagingStyle with sent reply acknowledged
            val userPerson = Person.Builder().setName("You").build()

            val updatedStyle = NotificationCompat.MessagingStyle(userPerson)
                .setConversationTitle(senderName)
                .addMessage(replyText, System.currentTimeMillis(), userPerson)

            val openChatIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("NAVIGATE_TO", "CHAT_DETAIL")
                putExtra("chatId", chatId)
            }
            val pendingOpen = PendingIntent.getActivity(
                context,
                chatId.hashCode(),
                openChatIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val updatedNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setStyle(updatedStyle)
                .setContentTitle(senderName)
                .setContentText("Replied: $replyText")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setContentIntent(pendingOpen)
                .build()

            try {
                notificationManager.notify(notificationId, updatedNotification)
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception updating reply notification", e)
            }
        }
    }

    private suspend fun markMessagesAsRead(context: Context, chatId: String, recipientUid: String) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (chatId.isBlank()) return

        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("chats").document(chatId)
                .collection("messages")
                .whereEqualTo("receiverId", currentUid)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    for (doc in querySnapshot.documents) {
                        doc.reference.update(mapOf("status" to "READ", "messageStatus" to "READ"))
                    }
                }

            val rdbRef = FirebaseDatabase.getInstance().getReference("messages").child(chatId)
            rdbRef.get().addOnSuccessListener { snapshot ->
                for (child in snapshot.children) {
                    val receiver = child.child("receiverId").value?.toString()
                    if (receiver == currentUid) {
                        child.ref.child("status").setValue("READ")
                    }
                }
            }

            // Local Room DB status update
            val dao = AppDatabase.getDatabase(context).localMessageDao()
            dao.markChatMessagesAsRead(chatId, currentUid)
            Log.d(TAG, "Marked messages as read for chatId $chatId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark messages as read: ${e.message}", e)
        }
    }
}
