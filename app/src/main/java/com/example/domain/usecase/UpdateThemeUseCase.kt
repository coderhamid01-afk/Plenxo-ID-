package com.example.domain.usecase

import com.example.repository.LocalSettingsRepository

class UpdateThemeUseCase(private val localSettingsRepository: LocalSettingsRepository) {
    suspend operator fun invoke(theme: String) {
        if (theme in listOf("light", "dark", "system")) {
            localSettingsRepository.setTheme(theme)
        }
    }
}
