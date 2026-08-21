package com.example.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

object LocaleHelper {

    private const val SELECTED_LANGUAGE = "Locale.Helper.Selected.Language"

    private val _currentLanguage = MutableStateFlow("en")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    fun onAttach(context: Context): Context {
        val lang = getPersistedLanguage(context)
        _currentLanguage.value = lang
        try {
            val appLocales = AppCompatDelegate.getApplicationLocales()
            if (appLocales.isEmpty) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return updateResources(context, lang)
    }

    fun getLanguage(context: Context): String {
        return getPersistedLanguage(context)
    }

    fun setLocale(context: Context, language: String): Context {
        val cleanLang = language.trim().lowercase()
        _currentLanguage.value = cleanLang
        persistLanguage(context, cleanLang)
        try {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(cleanLang))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return updateResources(context, cleanLang)
    }

    fun getLocalizedContext(context: Context, language: String): Context {
        return updateResources(context, language)
    }

    fun isRtlLanguage(language: String): Boolean {
        val cleanCode = language.lowercase().split("-", "_").firstOrNull() ?: ""
        return cleanCode in setOf("ar", "ur", "he", "iw", "fa", "ps", "yi", "ji", "ug", "ckb", "sd", "dv", "arc")
    }

    fun getPersistedLanguage(context: Context): String {
        try {
            val appLocales = AppCompatDelegate.getApplicationLocales()
            if (!appLocales.isEmpty) {
                val primaryLocale = appLocales.get(0)
                if (primaryLocale != null && !primaryLocale.language.isNullOrEmpty()) {
                    val code = primaryLocale.toLanguageTag()
                    if (code.isNotBlank()) return code.lowercase()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val saved1 = preferences.getString(SELECTED_LANGUAGE, null)
        if (!saved1.isNullOrBlank()) return saved1.lowercase()

        val plenxoPrefs = context.getSharedPreferences("plenxo_settings", Context.MODE_PRIVATE)
        val saved2 = plenxoPrefs.getString("app_language_code", null)
        if (!saved2.isNullOrBlank()) return saved2.lowercase()

        return "en"
    }

    private fun persistLanguage(context: Context, language: String) {
        val cleanLang = language.lowercase()
        val preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        preferences.edit().putString(SELECTED_LANGUAGE, cleanLang).apply()

        val plenxoPrefs = context.getSharedPreferences("plenxo_settings", Context.MODE_PRIVATE)
        plenxoPrefs.edit().putString("app_language_code", cleanLang).apply()

        val langName = when (cleanLang.split("-", "_").first()) {
            "es" -> "Spanish"
            "fr" -> "French"
            "ur" -> "Urdu"
            "hi" -> "Hindi"
            "ar" -> "Arabic"
            "zh" -> "Chinese"
            "ja" -> "Japanese"
            "de" -> "German"
            "ru" -> "Russian"
            "pt" -> "Portuguese"
            "it" -> "Italian"
            "bn" -> "Bengali"
            "pa" -> "Punjabi"
            "tr" -> "Turkish"
            "ko" -> "Korean"
            "vi" -> "Vietnamese"
            "id" -> "Indonesian"
            "fa" -> "Persian"
            "pl" -> "Polish"
            else -> "English"
        }
        preferences.edit().putString(SettingsManager.KEY_LANGUAGE_SELECTION, langName).apply()
    }

    private fun updateResources(context: Context, language: String): Context {
        val parts = language.split("-", "_")
        val locale = if (parts.size > 1) {
            Locale(parts[0], parts[1].uppercase())
        } else {
            Locale(language)
        }
        Locale.setDefault(locale)

        val resources = context.resources
        val configuration = Configuration(resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            configuration.setLocale(locale)
            configuration.setLayoutDirection(locale)
            return context.createConfigurationContext(configuration)
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
            @Suppress("DEPRECATION")
            resources.updateConfiguration(configuration, resources.displayMetrics)
            return context
        }
    }
}
