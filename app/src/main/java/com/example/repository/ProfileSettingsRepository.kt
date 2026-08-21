package com.example.repository

import com.example.model.UserProfileDomainModel
import kotlinx.coroutines.flow.Flow

interface ProfileSettingsRepository {
    fun getProfileFlow(userId: String): Flow<UserProfileDomainModel?>
    suspend fun updateProfile(userId: String, name: String, bio: String, profileUrl: String)
    suspend fun updateRing(userId: String, ringId: String)
    suspend fun updateProfileRing(userId: String, ringId: String)
    suspend fun uploadProfileImage(context: android.content.Context, uri: android.net.Uri): String
}
