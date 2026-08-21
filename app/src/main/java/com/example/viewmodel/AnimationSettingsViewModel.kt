package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AnimationPreferences
import com.example.repository.AnimationPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AnimationSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AnimationPreferencesRepository(application)

    val animationPreferences: StateFlow<AnimationPreferences> = repository.animationPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AnimationPreferences()
        )

    fun toggleAllAnimations(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAllAnimationsEnabled(enabled)
        }
    }

    fun toggleFluidSpringBubble(enabled: Boolean) {
        viewModelScope.launch {
            repository.setFluidSpringBubbleEnabled(enabled)
        }
    }

    fun toggleNeonGlowPulse(enabled: Boolean) {
        viewModelScope.launch {
            repository.setNeonGlowPulseEnabled(enabled)
        }
    }

    fun toggleGlassmorphicBlur(enabled: Boolean) {
        viewModelScope.launch {
            repository.setGlassmorphicBlurEnabled(enabled)
        }
    }

    fun toggleParallaxDepth(enabled: Boolean) {
        viewModelScope.launch {
            repository.setParallaxDepthEnabled(enabled)
        }
    }

    fun toggleLiquidRipple(enabled: Boolean) {
        viewModelScope.launch {
            repository.setLiquidRippleEnabled(enabled)
        }
    }

    fun toggleShimmerSkeleton(enabled: Boolean) {
        viewModelScope.launch {
            repository.setShimmerSkeletonEnabled(enabled)
        }
    }

    fun toggleFloatingParticles(enabled: Boolean) {
        viewModelScope.launch {
            repository.setFloatingParticlesEnabled(enabled)
        }
    }

    fun toggleConfettiBurst(enabled: Boolean) {
        viewModelScope.launch {
            repository.setConfettiBurstEnabled(enabled)
        }
    }

    fun toggleCard3DTilt(enabled: Boolean) {
        viewModelScope.launch {
            repository.setCard3DTiltEnabled(enabled)
        }
    }

    fun toggleMorphingFab(enabled: Boolean) {
        viewModelScope.launch {
            repository.setMorphingFabEnabled(enabled)
        }
    }
}
