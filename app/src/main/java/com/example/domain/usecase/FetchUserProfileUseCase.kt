package com.example.domain.usecase

import com.example.model.UserProfileDomainModel
import com.example.repository.ProfileSettingsRepository
import kotlinx.coroutines.flow.Flow

class FetchUserProfileUseCase(private val profileSettingsRepository: ProfileSettingsRepository) {
    operator fun invoke(userId: String): Flow<UserProfileDomainModel?> {
        return profileSettingsRepository.getProfileFlow(userId)
    }
}
