package com.example.domain.usecase

import com.example.repository.ProfileSettingsRepository

class SaveUserProfileUseCase(private val profileSettingsRepository: ProfileSettingsRepository) {
    suspend operator fun invoke(userId: String, name: String, bio: String, profileUrl: String) {
        if (name.isNotBlank()) {
            profileSettingsRepository.updateProfile(userId, name, bio, profileUrl)
        } else {
            throw IllegalArgumentException("Display Name cannot be empty")
        }
    }
}
