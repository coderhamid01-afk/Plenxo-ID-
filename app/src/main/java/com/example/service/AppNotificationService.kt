package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity

class AppNotificationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    // Note: FCM is handled by PlenxoMessagingService; this service manages channel creation and foreground states.

    companion object {
        fun getDynamicChannelId(context: Context): String {
            return com.example.util.NotificationHelper.getDynamicChannelId(context)
        }

        fun createNotificationChannel(context: Context) {
            val ringtone = com.example.util.NotificationHelper.getSelectedSoundName(context)
            com.example.util.NotificationHelper.recreateNotificationChannel(context, ringtone)
        }

        fun updateNotificationChannelSound(context: Context, ringtoneName: String) {
            com.example.util.NotificationHelper.recreateNotificationChannel(context, ringtoneName)
        }
    }
}
