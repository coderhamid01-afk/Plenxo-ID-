package com.example.service

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.example.MainActivity
import com.example.util.NotificationHelper

class PlenxoMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid)
            .set(mapOf("fcmToken" to token), SetOptions.merge())
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        try {
            val currentState = androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
            if (currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                return
            }
        } catch (e: Exception) {
            // ignore lifecycle check failure
        }

        val title = message.notification?.title ?: message.data["title"] ?: "New message"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val chatId = message.data["chatId"]

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            chatId?.let { putExtra("chatId", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, chatId?.hashCode() ?: 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationHelper.createNotificationBuilder(this, title, body, pendingIntent)
        NotificationManagerCompat.from(this).apply {
            notify((chatId ?: title).hashCode(), builder.build())
        }
    }
}
