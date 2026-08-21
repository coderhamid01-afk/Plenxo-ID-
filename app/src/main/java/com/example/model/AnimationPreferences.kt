package com.example.model

data class AnimationPreferences(
    val allAnimationsEnabled: Boolean = true,
    val fluidSpringBubbleEnabled: Boolean = true,
    val neonGlowPulseEnabled: Boolean = true,
    val glassmorphicBlurEnabled: Boolean = true,
    val parallaxDepthEnabled: Boolean = true,
    val liquidRippleButtonEnabled: Boolean = true,
    val shimmerSkeletonEnabled: Boolean = true,
    val floatingParticlesEnabled: Boolean = true,
    val confettiBurstEnabled: Boolean = true,
    val card3DTiltEnabled: Boolean = true,
    val morphingFabEnabled: Boolean = true
) {
    fun isEnabled(featureEnabled: Boolean): Boolean = allAnimationsEnabled && featureEnabled
}
