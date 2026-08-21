package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class VisibilityOption {
    EVERYONE, CONTACTS, NOBODY
}

data class UserSettings(
    val themePreference: String = "system", // "light", "dark", "system"
    val fontSize: String = "medium", // "small", "medium", "large"
    val chatWallpaperUri: String? = null,
    val messageNotifications: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val popupEnabled: Boolean = true
)

data class PrivacySettings(
    val lastSeenVisibility: VisibilityOption = VisibilityOption.EVERYONE,
    val profilePhotoVisibility: VisibilityOption = VisibilityOption.EVERYONE,
    val readReceiptsEnabled: Boolean = true,
    val disappearingMessagesDefaultTimer: Long = 0L // 0 = disabled, or milliseconds for 24h/7d
)

@Entity(tableName = "local_messages")
data class LocalMessage(
    @PrimaryKey val messageId: String,
    val chatId: String,
    val senderId: String,
    val receiverId: String,
    val messageText: String,
    val timestamp: Long,
    val status: String = "SENDING", // "SENDING", "SENT", "DELIVERED", "READ"
    val messageType: String = "TEXT", // "TEXT", "IMAGE", "VOICE"
    val replyToMessageId: String? = null,
    val isEdited: Boolean = false,
    val originalContentHistoryJson: String = "[]", // Serialized list of strings for history
    val expiresAt: Long? = null,
    val expiryTimestamp: Long? = null,
    val isPinned: Boolean = false,
    val senderActiveFontId: String = "DEFAULT"
)
