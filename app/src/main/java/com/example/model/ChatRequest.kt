package com.example.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val requestId: String = "",
    val senderUid: String = "",
    val senderPlenxoId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String = "",
    val receiverUid: String = "",
    val receiverPlenxoId: String = "",
    val receiverName: String = "",
    val receiverPhotoUrl: String = "",
    val status: String = "PENDING",
    val timestamp: Long = 0L
) {
    val id: String get() = requestId
    val senderId: String get() = senderUid
    val receiverId: String get() = receiverUid
    val senderProfilePic: String get() = senderPhotoUrl
    val receiverProfilePic: String get() = receiverPhotoUrl
}
