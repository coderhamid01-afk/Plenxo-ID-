package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class VoiceCallService : Service() {

    companion object {
        const val CHANNEL_ID = "voice_call_channel"
        const val NOTIFICATION_ID = 2001

        const val EXTRA_ROOM_ID = "extra_room_id"
        const val EXTRA_PEER_UID = "extra_peer_uid"
        const val EXTRA_PEER_NAME = "extra_peer_name"
        const val EXTRA_PEER_PIC = "extra_peer_pic"
        const val EXTRA_CALL_TYPE = "extra_call_type"

        fun startService(
            context: Context,
            roomId: String,
            receiverUid: String,
            receiverName: String,
            receiverPic: String,
            callType: String
        ) {
            val intent = Intent(context, VoiceCallService::class.java).apply {
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_PEER_UID, receiverUid)
                putExtra(EXTRA_PEER_NAME, receiverName)
                putExtra(EXTRA_PEER_PIC, receiverPic)
                putExtra(EXTRA_CALL_TYPE, callType)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, VoiceCallService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val peerName = intent?.getStringExtra(EXTRA_PEER_NAME) ?: "Plenxo User"
        val callType = intent?.getStringExtra(EXTRA_CALL_TYPE) ?: "Audio"

        val notification = buildCallNotification(peerName, callType)
        startForeground(NOTIFICATION_ID, notification)

        return START_NOT_STICKY
    }

    private fun buildCallNotification(peerName: String, callType: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Ongoing $callType Call")
            .setContentText("In call with $peerName")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ongoing Calls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for active in-progress calls"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
