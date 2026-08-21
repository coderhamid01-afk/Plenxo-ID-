package com.example.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class MessageStatus {
    SENDING, SENT, DELIVERED, READ, FAILED
}

@Serializable
enum class DeliveryStatus {
    SENDING, SENT, DELIVERED, READ, FAILED
}

@Serializable
@Immutable
data class Message(
    val messageId: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val messageText: String = "",
    val mediaUrl: String = "",
    val localUri: String? = null,
    val timestamp: Long? = System.currentTimeMillis(),
    val status: String = "SENT", // For backwards compatibility
    val deliveryStatus: DeliveryStatus = DeliveryStatus.SENT,
    val messageStatus: MessageStatus = MessageStatus.SENT,
    val messageType: String = "TEXT", // "TEXT", "IMAGE", "VOICE", "audio", "SYSTEM"
    val replyToMessageId: String? = null,
    val isEdited: Boolean = false,
    val originalContentHistory: List<String> = emptyList(),
    val expiresAt: Long? = null,
    val expiryTimestamp: Long? = null,
    val senderActiveFontId: String = "DEFAULT",
    val uploadProgress: Int = 100
) {
    val effectiveStatus: MessageStatus
        get() {
            return when {
                messageStatus == MessageStatus.FAILED || status == "FAILED" || deliveryStatus == DeliveryStatus.FAILED -> MessageStatus.FAILED
                messageStatus == MessageStatus.SENDING || status == "SENDING" || deliveryStatus == DeliveryStatus.SENDING -> MessageStatus.SENDING
                messageStatus == MessageStatus.READ || status == "READ" || deliveryStatus == DeliveryStatus.READ -> MessageStatus.READ
                messageStatus == MessageStatus.DELIVERED || status == "DELIVERED" || deliveryStatus == DeliveryStatus.DELIVERED -> MessageStatus.DELIVERED
                else -> MessageStatus.SENT
            }
        }
}
