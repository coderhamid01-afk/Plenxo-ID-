package com.example.model

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable

@Keep
@Immutable
data class CallLog(
    val callId: String = "",
    val peerUid: String = "",
    val peerName: String = "",
    val peerPhotoUrl: String = "",
    val peerPlenxoId: String = "",
    val callType: String = "", // "AUDIO" / "VIDEO"
    val direction: String = "", // "INCOMING" / "OUTGOING" / "MISSED"
    val timestamp: Long = 0L,
    val durationSeconds: Long = 0L
)
