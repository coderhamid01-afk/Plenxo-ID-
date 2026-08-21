package com.example.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val profilePicUrl: String = "",
    val themePreference: String = "Blue",
    val userCode: String = "",
    val plenxoId: String = "",
    val dob: String = "",
    val phoneNumber: String = "",
    val lastLoginTimestamp: Long = 0L,
    val totalAccumulatedSeconds: Long = 0L,
    val selectedRingId: String = "",
    val profileRingId: String = "none",
    val profileRing: String? = null,
    val termsAccepted: Boolean = false,
    val termsAcceptedAt: String = "",
    val leagueData: LeagueData = LeagueData()
)

@Serializable
@Immutable
data class ChatRoom(
    val chatId: String = "",
    val participantUids: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageTimestamp: Long? = null,
    val unreadCounts: Map<String, Int> = emptyMap()
)

@Serializable
data class Invitation(
    val invitationId: String = "",
    val requestId: String = "",
    val senderUid: String = "",
    val senderId: String = "",
    val senderUserCode: String = "",
    val senderName: String = "",
    val receiverUid: String = "",
    val receiverId: String = "",
    val status: String = "PENDING",
    val timestamp: Long = System.currentTimeMillis()
)
