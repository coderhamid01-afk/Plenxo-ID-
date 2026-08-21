package com.example.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDomainModel(
    val userId: String = "",
    val id: String = "", // Document ID compatibility
    val email: String = "",
    val name: String = "",
    val displayName: String = "",
    val bio: String = "",
    val statusMessage: String = "",
    val profileUrl: String = "",
    val profilePicUrl: String = "",
    val userCode: String = "",
    val plenxoId: String = "",
    val selectedRingId: String = "NONE",
    val profileRingId: String = "none",
    val profileRing: String? = null,
    val selectedFontId: String = "DEFAULT",
    val phoneNumber: String = "",
    val leagueData: LeagueData = LeagueData()
)

