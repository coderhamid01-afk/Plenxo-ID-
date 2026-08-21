package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/*
 * VoiceCallService is temporarily disabled.
 */
class VoiceCallService : Service() {
    companion object {
        fun startService(context: Context, roomId: String, receiverUid: String, receiverName: String, receiverPic: String, callType: String) {}
        fun stopService(context: Context) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
