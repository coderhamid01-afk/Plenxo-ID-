package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/*
 * IncomingCallService is temporarily disabled.
 */
class IncomingCallService : Service() {
    companion object {
        fun startService(context: Context, roomId: String, callerUid: String, callerName: String, callerPic: String, callType: String) {}
        fun stopService(context: Context) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
