package com.example.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AnimationManager {
    private val _globalSpeed = MutableStateFlow(100)
    val globalSpeed: StateFlow<Int> = _globalSpeed

    private val _animationsEnabled = MutableStateFlow(mutableMapOf(
        "List Load Fade-in" to true,
        "Navigation Slide-in" to true,
        "Button Click Ripple" to true,
        "Profile Icon Scale-up" to true,
        "Chat Bubble Pop" to true,
        "Toolbar Transition" to true,
        "Settings Tab Slide" to true,
        "Image Zoom-in Transition" to true,
        "ViewPager Swipe Animation" to true,
        "Error/Empty State Bounce" to true
    ))
    val animationsEnabled: StateFlow<MutableMap<String, Boolean>> = _animationsEnabled

    fun setGlobalSpeed(speed: Int) {
        _globalSpeed.value = speed
    }

    fun toggleAnimation(name: String, enabled: Boolean) {
        _animationsEnabled.value[name] = enabled
    }

    fun isAnimationEnabled(name: String): Boolean {
        return _animationsEnabled.value[name] ?: true
    }
}
