package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.util.AppLanguages
import com.example.util.Language
import com.example.util.LocaleHelper
import com.example.util.plenxoLanguages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _selectedLanguage = MutableStateFlow(getCurrentLanguage())
    val selectedLanguage: StateFlow<Language> = _selectedLanguage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun getCurrentLanguage(): Language {
        val currentTag = LocaleHelper.getPersistedLanguage(getApplication())
        val allList = AppLanguages.list.ifEmpty { plenxoLanguages }
        return allList.find { it.code.equals(currentTag, ignoreCase = true) }
            ?: allList.find { it.code.startsWith(currentTag, ignoreCase = true) }
            ?: allList.find { it.code == "en" }
            ?: Language("English", "en")
    }

    fun selectLanguage(language: Language) {
        _selectedLanguage.value = language
        LocaleHelper.setLocale(getApplication(), language.code)
    }
}
