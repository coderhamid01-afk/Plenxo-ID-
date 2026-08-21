package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.util.plenxoLanguages
import com.example.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val darkBg = Color(0xFF0D1117)
    val cardBg = Color(0xFF161B22)
    val strokeBorder = Color(0xFF30363D)
    val accentBlue = Color(0xFF58A6FF)
    val textWhite = Color(0xFFF0F6FC)
    val textMuted = Color(0xFF8B949E)

    val allLangs = com.example.util.AppLanguages.list

    val filteredLanguages = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            allLangs
        } else {
            val q = searchQuery.trim().lowercase()
            allLangs.filter {
                it.name.lowercase().contains(q) || it.code.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Language (${plenxoLanguages.size})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textWhite
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("language_screen_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = accentBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = darkBg)
            )
        },
        containerColor = darkBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // SEARCH BAR FOR 200 LANGUAGES
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search 200 languages...", color = textMuted) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = textMuted)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = textMuted)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = cardBg,
                    unfocusedContainerColor = cardBg,
                    focusedBorderColor = accentBlue,
                    unfocusedBorderColor = strokeBorder,
                    focusedTextColor = textWhite,
                    unfocusedTextColor = textWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("language_search_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredLanguages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No languages match '$searchQuery'",
                        color = textMuted,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLanguages, key = { it.code }) { language ->
                        val isSelected = selectedLanguage.code.equals(language.code, ignoreCase = true)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectLanguage(language) }
                                .testTag("language_item_${language.code}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF1F6FEB).copy(alpha = 0.15f) else cardBg
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) accentBlue else strokeBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = null,
                                    tint = if (isSelected) accentBlue else textMuted,
                                    modifier = Modifier.size(20.dp)
                                )

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = language.name,
                                        fontSize = 16.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) accentBlue else textWhite
                                    )
                                    Text(
                                        text = "Code: ${language.code}",
                                        fontSize = 12.sp,
                                        color = textMuted
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = accentBlue,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .testTag("selected_checkmark_${language.code}")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
