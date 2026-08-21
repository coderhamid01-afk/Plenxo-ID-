package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.usecase.UpdateThemeUseCase
import com.example.repository.LocalSettingsRepositoryImpl
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NormalSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocalSettingsRepositoryImpl(application)
    private val updateThemeUseCase = UpdateThemeUseCase(repository)

    val themeState: StateFlow<String> = repository.themeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "system"
    )

    val hapticFeedbackState: StateFlow<Boolean> = repository.hapticFeedbackFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val notificationSoundsState: StateFlow<Boolean> = repository.notificationSoundsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val notificationRingtoneState: StateFlow<String> = repository.notificationRingtoneFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "minimal_ping"
    )

    val chatWallpaperUriState: StateFlow<String?> = repository.chatWallpaperUriFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val languageState: StateFlow<String> = repository.languageFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "en"
    )

    fun setTheme(theme: String) {
        viewModelScope.launch {
            updateThemeUseCase(theme)
        }
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setHapticFeedbackEnabled(enabled)
        }
    }

    fun setNotificationSoundsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setNotificationSoundsEnabled(enabled)
        }
    }

    fun setNotificationRingtone(ringtone: String) {
        viewModelScope.launch {
            repository.setNotificationRingtone(ringtone)
            com.example.service.AppNotificationService.updateNotificationChannelSound(getApplication(), ringtone)
        }
    }

    fun setChatWallpaperUri(uri: String?) {
        viewModelScope.launch {
            repository.setChatWallpaperUri(uri)
        }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch {
            repository.setLanguage(language)
            com.example.util.LocaleHelper.setLocale(getApplication(), language)
        }
    }

    fun clearCache(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.clearCache()
            onComplete()
        }
    }
}
