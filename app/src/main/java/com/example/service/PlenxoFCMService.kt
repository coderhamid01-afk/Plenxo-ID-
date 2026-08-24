package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.net.HttpURLConnection
import java.net.URL

open class PlenxoFCMService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token generated: $token")
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        updateFcmTokenInDatabase(uid, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

        val data = remoteMessage.data

        val senderId = data["sender_id"] ?: data["senderId"] ?: ""
        val senderName = data["sender_name"] ?: data["senderName"] ?: remoteMessage.notification?.title ?: "Plenxo User"
        val messageText = data["message_text"] ?: data["message"] ?: data["body"] ?: remoteMessage.notification?.body ?: "New message received"
        val chatId = data["chat_id"] ?: data["chatId"] ?: ""
        val avatarUrl = data["avatar_url"] ?: data["avatarUrl"] ?: data["sender_avatar"] ?: ""
        val type = data["type"] ?: "chat"

        val title = remoteMessage.notification?.title ?: senderName
        val body = remoteMessage.notification?.body ?: messageText

        showMessagingStyleNotification(
            context = this,
            title = title,
            body = body,
            senderId = senderId,
            senderName = senderName,
            chatId = chatId,
            avatarUrl = avatarUrl,
            type = type
        )
    }

    private fun showMessagingStyleNotification(
        context: Context,
        title: String,
        body: String,
        senderId: String,
        senderName: String,
        chatId: String,
        avatarUrl: String,
        type: String
    ) {
        val channelId = "plenxo_messages"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Plenxo Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time notifications for incoming Plenxo chat messages"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val targetScreen = if (type == "friend_request" || type == "chat_request") "CHAT_REQUESTS" else if (chatId.isNotBlank()) "CHAT_DETAIL" else "HOME"
        val notificationId = if (chatId.isNotBlank()) chatId.hashCode() else (senderId + System.currentTimeMillis()).hashCode()

        // 1. Fetch or generate Sender's Profile Picture / Avatar Bitmap
        val avatarBitmap = getAvatarBitmap(context, avatarUrl, senderName)

        // 2. Build Person models for NotificationCompat.MessagingStyle
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: "me"
        val mePerson = Person.Builder()
            .setName("Me")
            .setKey(currentUid)
            .build()

        val senderPersonBuilder = Person.Builder()
            .setName(senderName)
            .setKey(senderId)

        if (avatarBitmap != null) {
            senderPersonBuilder.setIcon(IconCompat.createWithBitmap(avatarBitmap))
        }
        val senderPerson = senderPersonBuilder.build()

        // 3. Create MessagingStyle layout (similar to WhatsApp / Instagram)
        val messagingStyle = NotificationCompat.MessagingStyle(mePerson)
            .setConversationTitle(if (type == "group") title else senderName)
            .setGroupConversation(type == "group")
            .addMessage(body, System.currentTimeMillis(), senderPerson)

        // 4. Intent when tapping the main notification body -> Opens Chat Screen
        val openChatIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", targetScreen)
            if (chatId.isNotBlank()) putExtra("chatId", chatId)
            if (senderId.isNotBlank()) putExtra("senderId", senderId)
            putExtra("type", type)
        }

        val openChatPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openChatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 5. Inline Direct Reply (RemoteInput) Setup
        val remoteInput = RemoteInput.Builder(DirectReplyReceiver.KEY_TEXT_REPLY)
            .setLabel("Type a reply...")
            .build()

        val replyIntent = Intent(context, DirectReplyReceiver::class.java).apply {
            action = DirectReplyReceiver.ACTION_DIRECT_REPLY
            putExtra("CHAT_ID", chatId)
            putExtra("chat_id", chatId)
            putExtra("RECIPIENT_UID", senderId)
            putExtra("recipient_uid", senderId)
            putExtra("NOTIFICATION_ID", notificationId)
            putExtra("SENDER_NAME", senderName)
        }

        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            (chatId + "_reply").hashCode(),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        // 6. "Mark as Read" Action Setup
        val markReadIntent = Intent(context, DirectReplyReceiver::class.java).apply {
            action = DirectReplyReceiver.ACTION_MARK_READ
            putExtra("CHAT_ID", chatId)
            putExtra("chat_id", chatId)
            putExtra("RECIPIENT_UID", senderId)
            putExtra("NOTIFICATION_ID", notificationId)
            putExtra("MARK_READ", true)
        }

        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            (chatId + "_read").hashCode(),
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Mark as Read",
            markReadPendingIntent
        ).build()

        // 7. Custom sound fallback
        val soundName = NotificationHelper.getSelectedSoundName(context)
        val soundUri = NotificationHelper.getSoundUri(context, soundName)

        // 8. Assemble complete Messaging Style notification with Grouping
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setStyle(messagingStyle)
            .setContentTitle(senderName)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openChatPendingIntent)
            .setSound(soundUri)
            .setGroup("plenxo_chat_$chatId") // Group notifications by chat_id
            .addAction(replyAction)
            .addAction(markReadAction)

        if (avatarBitmap != null) {
            notificationBuilder.setLargeIcon(avatarBitmap)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notificationBuilder.build())
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to display notification: ${e.message}")
        }
    }

    private fun getAvatarBitmap(context: Context, avatarUrl: String?, senderName: String): Bitmap? {
        if (!avatarUrl.isNullOrBlank()) {
            try {
                val url = URL(avatarUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.doInput = true
                connection.connect()
                val inputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) return bitmap
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load avatar from $avatarUrl: ${e.message}")
            }
        }
        return createInitialAvatarBitmap(senderName)
    }

    private fun createInitialAvatarBitmap(name: String): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1F6FEB")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        val initial = name.trim().take(1).ifBlank { "P" }.uppercase()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 56f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val y = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(initial, size / 2f, y, textPaint)
        return bitmap
    }

    companion object {
        private const val TAG = "PlenxoFCMService"

        fun fetchAndSaveFcmToken(uid: String) {
            if (uid.isBlank()) return
            try {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful && !task.result.isNullOrBlank()) {
                        val token = task.result
                        updateFcmTokenInDatabase(uid, token)
                    }
                }.addOnFailureListener { e ->
                    Log.w(TAG, "Failed to fetch FCM token for $uid: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting FCM token: ${e.message}")
            }
        }

        fun updateFcmTokenInDatabase(uid: String, token: String) {
            if (uid.isBlank() || token.isBlank()) return

            try {
                val rdbRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
                val rdbMap = mapOf(
                    "fcm_token" to token,
                    "fcmToken" to token
                )
                rdbRef.updateChildren(rdbMap)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update FCM token in Realtime DB: ${e.message}")
            }

            try {
                val firestore = FirebaseFirestore.getInstance()
                val firestoreMap = mapOf(
                    "fcmToken" to token,
                    "fcm_token" to token,
                    "updatedAt" to System.currentTimeMillis()
                )
                firestore.collection("users").document(uid).set(firestoreMap, SetOptions.merge())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update FCM token in Firestore: ${e.message}")
            }
        }
    }
}
