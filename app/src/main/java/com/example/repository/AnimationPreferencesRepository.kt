package com.example.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AnimationPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.animationDataStore: DataStore<Preferences> by preferencesDataStore(name = "plenxo_animation_prefs")

class AnimationPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val ALL_ANIMATIONS_ENABLED = booleanPreferencesKey("all_animations_enabled")
        val FLUID_SPRING_BUBBLE = booleanPreferencesKey("fluid_spring_bubble")
        val NEON_GLOW_PULSE = booleanPreferencesKey("neon_glow_pulse")
        val GLASSMORPHIC_BLUR = booleanPreferencesKey("glassmorphic_blur")
        val PARALLAX_DEPTH = booleanPreferencesKey("parallax_depth")
        val LIQUID_RIPPLE = booleanPreferencesKey("liquid_ripple")
        val SHIMMER_SKELETON = booleanPreferencesKey("shimmer_skeleton")
        val FLOATING_PARTICLES = booleanPreferencesKey("floating_particles")
        val CONFETTI_BURST = booleanPreferencesKey("confetti_burst")
        val CARD_3D_TILT = booleanPreferencesKey("card_3d_tilt")
        val MORPHING_FAB = booleanPreferencesKey("morphing_fab")
    }

    val animationPreferencesFlow: Flow<AnimationPreferences> = context.animationDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AnimationPreferences(
                allAnimationsEnabled = preferences[PreferencesKeys.ALL_ANIMATIONS_ENABLED] ?: true,
                fluidSpringBubbleEnabled = preferences[PreferencesKeys.FLUID_SPRING_BUBBLE] ?: true,
                neonGlowPulseEnabled = preferences[PreferencesKeys.NEON_GLOW_PULSE] ?: true,
                glassmorphicBlurEnabled = preferences[PreferencesKeys.GLASSMORPHIC_BLUR] ?: true,
                parallaxDepthEnabled = preferences[PreferencesKeys.PARALLAX_DEPTH] ?: true,
                liquidRippleButtonEnabled = preferences[PreferencesKeys.LIQUID_RIPPLE] ?: true,
                shimmerSkeletonEnabled = preferences[PreferencesKeys.SHIMMER_SKELETON] ?: true,
                floatingParticlesEnabled = preferences[PreferencesKeys.FLOATING_PARTICLES] ?: true,
                confettiBurstEnabled = preferences[PreferencesKeys.CONFETTI_BURST] ?: true,
                card3DTiltEnabled = preferences[PreferencesKeys.CARD_3D_TILT] ?: true,
                morphingFabEnabled = preferences[PreferencesKeys.MORPHING_FAB] ?: true
            )
        }

    suspend fun setAllAnimationsEnabled(enabled: Boolean) {
        context.animationDataStore.edit { prefs ->
            prefs[PreferencesKeys.ALL_ANIMATIONS_ENABLED] = enabled
        }
    }

    suspend fun setFluidSpringBubbleEnabled(enabled: Boolean) {
        context.animationDataStore.edit { prefs ->
            prefs[PreferencesKeys.FLUID_SPRING_BUBBLE] = enabled
        }
    }

    suspend fun setNeonGlowPulseEnabled(enabled: Boolean) {
        context.animationDataStore.edit { prefs ->
            prefs[PreferencesKeys.NEON_GLOW_PULSE] = enabled
        }
    }

    suspend fun setGlassmorphicBlurEnabled(enabled: Boolean) {
        context.animationDataStore.edit { prefs ->
            prefs[PreferencesKeys.GLASSMORPHIC_BLUR] = enabled
        }
    }

    suspend fun setParallaxDepthEnabled(enabled: Boolean) {
        context.animationDataStore.edit { prefs ->
            prefs[PreferencesKeys.PARALLAX_DEPTH] = enabled
        }
    }

    suspend fun setLiquidRippleEnabled(enabled: Boolean) {
        context.animationDataStore.edit { prefs ->
            prefs[PreferencesKeys.LIQUID_RIPPLE] = enabled
        }
    }

    suspend fun setShimmerSkeletonEnabled(enabled: Boolean) {
        context.animationDataStore.edit { prefs ->
            prefs[PreferencesKeys.SHIMMER_SKELETON] = enabled
        }
    }

    suspend fun setFloatingParticlesEnabled(enabled: Boolean) {
        context.animationDataStore.edit { prefs ->
            prefs[PreferencesKeys.FLOATING_PARTICLES] = enabled
        }
    }

    suspend fun setConfettiBurstEnabled(enabled: Boolean) {
        context.animationDataStore.edit { prefs ->
            prefs[PreferencesKeys.CONFETTI_BURST] = enabled
        }
    }

    suspend fun setCard3DTiltEnabled(enabled: Boolean) {
        context.animationDataStore.edit { prefs ->
            prefs[PreferencesKeys.CARD_3D_TILT] = enabled
        }
    }

    suspend fun setMorphingFabEnabled(enabled: Boolean) {
        context.animationDataStore.edit { prefs ->
            prefs[PreferencesKeys.MORPHING_FAB] = enabled
        }
    }
}
