package com.example.ui
import com.example.ui.components.bounceClick

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import com.example.util.LegalWebUtils
import com.example.viewmodel.NormalSettingsViewModel
import com.example.viewmodel.PlenxoViewModel
import com.example.ui.components.WallpaperItem
import com.example.util.AnimationManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NormalSettingsScreen(
    viewModel: NormalSettingsViewModel,
    weChatViewModel: PlenxoViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }

    // Collect States from ViewModel
    val themeMode by viewModel.themeState.collectAsState()
    val hapticEnabled by viewModel.hapticFeedbackState.collectAsState()
    val notificationSoundsEnabled by viewModel.notificationSoundsState.collectAsState()
    val selectedRingtone by viewModel.notificationRingtoneState.collectAsState()
    val currentLanguage by viewModel.languageState.collectAsState()

    // Premium Color Palette
    val darkBg = Color(0xFF0D1117)
    val cardBg = Color(0xFF161B22)
    val strokeBorder = Color(0xFF30363D)
    val accentBlue = Color(0xFF58A6FF)
    val textWhite = Color(0xFFF0F6FC)
    val textMuted = Color(0xFF8B949E)

    val languagesList = remember {
        com.example.util.appLanguages.map { it.code to it.name }
    }

    // Curated ringtones: "Best" + 5 new ones requested by user
    // Curated ringtones: All 18 available files in raw folder mapped elegantly
    val ringtoneList = remember {
        listOf(
            "minimal_ping" to "Minimal Ping",
            "minimal_pop" to "Minimal Pop",
            "zen_ping" to "Zen Ping",
            "crystal_drop" to "Crystal Drop",
            "echo_drop" to "Echo Drop",
            "cyber_spark" to "Cyber Spark",
            "cyber_alert" to "Cyber Alert",
            "retro_synth" to "Retro Synth",
            "midnight_pulse" to "Midnight Pulse",
            "lunar_chime" to "Chime",
            "ethereal_echo" to "Ethereal Echo",
            "ambient_breeze" to "Ambient Breeze",
            "soft_breeze" to "Soft Breeze",
            "obsidian_strike" to "Obsidian Strike",
            "royal_bell" to "Royal Bell",
            "velvet_tap" to "Velvet Tap",
            "tone_one" to "Tone One",
            "tone_two" to "Tone Two"
        )
    }

    // Function to trigger haptic feedback safely
    val triggerHaptic = {
        if (hapticEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(30)
                }
            } catch (e: Exception) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    // Bottom Sheet state for sound selection
    val sheetState = rememberModalBottomSheetState()
    var showSoundSheet by remember { mutableStateOf(false) }
    var showBugReportSheet by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    // Read selected sound from SharedPreferences as starting local state when sheet opens
    var selectedRingtoneLocal by remember(showSoundSheet) {
        mutableStateOf(com.example.util.NotificationHelper.getSelectedSoundName(context))
    }

    // Safely stop and release MediaPlayer previewer
    val releaseMediaPlayer = {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            releaseMediaPlayer()
        }
    }

    if (showSoundSheet) {
        ModalBottomSheet(
            onDismissRequest = { 
                showSoundSheet = false
                releaseMediaPlayer()
            },
            sheetState = sheetState,
            containerColor = cardBg,
            scrimColor = Color.Black.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    stringResource(id = R.string.settings_notification_ringtone),
                    color = textWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                Text(
                    stringResource(id = R.string.settings_ringtone_sub),
                    color = textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 320.dp)) {
                    items(ringtoneList.size) { index ->
                        val (ringtoneRes, ringtoneLabel) = ringtoneList[index]
                        val isSelected = selectedRingtoneLocal == ringtoneRes
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick {
                                    triggerHaptic()
                                    selectedRingtoneLocal = ringtoneRes
                                    
                                    // Play preview using MediaPlayer
                                    try {
                                        releaseMediaPlayer()
                                        val resId = context.resources.getIdentifier(ringtoneRes, "raw", context.packageName)
                                        if (resId != 0) {
                                            mediaPlayer = android.media.MediaPlayer.create(context, resId).apply {
                                                setOnCompletionListener {
                                                    releaseMediaPlayer()
                                                }
                                                start()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = accentBlue,
                                    unselectedColor = textMuted
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = ringtoneLabel,
                                color = if (isSelected) textWhite else textMuted,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Playing preview",
                                    tint = accentBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = strokeBorder, modifier = Modifier.padding(vertical = 12.dp))

                // Action buttons: Cancel and Save
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            triggerHaptic()
                            releaseMediaPlayer()
                            showSoundSheet = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = textWhite
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, strokeBorder)
                    ) {
                        Text(stringResource(id = R.string.cancel), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            triggerHaptic()
                            releaseMediaPlayer()
                            // Save to SharedPreferences and recreate dynamic Notification Channel
                            com.example.util.NotificationHelper.saveSelectedSound(context, selectedRingtoneLocal)
                            // Sync state with ViewModel
                            viewModel.setNotificationRingtone(selectedRingtoneLocal)
                            showSoundSheet = false
                            Toast.makeText(context, "Notification ringtone updated successfully!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentBlue,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(stringResource(id = R.string.save), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showBugReportSheet) {
        com.example.ui.components.ReportBugBottomSheet(
            onDismiss = { showBugReportSheet = false }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.settings_select_theme_title),
                    color = textWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            containerColor = cardBg,
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    listOf(
                        "light" to "Light Theme",
                        "dark" to "Dark Theme",
                        "system" to "System Default"
                    ).forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    triggerHaptic()
                                    viewModel.setTheme(value)
                                    weChatViewModel.updateAppThemeMode(value)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == value,
                                onClick = {
                                    triggerHaptic()
                                    viewModel.setTheme(value)
                                    weChatViewModel.updateAppThemeMode(value)
                                    showThemeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = accentBlue,
                                    unselectedColor = textMuted
                                ),
                                modifier = Modifier.testTag("theme_dialog_radio_$value")
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                color = if (themeMode == value) textWhite else textMuted,
                                fontSize = 15.sp,
                                fontWeight = if (themeMode == value) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showThemeDialog = false }
                ) {
                    Text(stringResource(id = R.string.cancel), color = accentBlue, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showLanguageDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredLanguages = remember(searchQuery) {
            if (searchQuery.isBlank()) {
                languagesList
            } else {
                val q = searchQuery.trim().lowercase()
                languagesList.filter {
                    it.second.lowercase().contains(q) || it.first.lowercase().contains(q)
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.settings_select_language_title),
                    color = textWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            containerColor = cardBg,
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search languages...", color = textMuted, fontSize = 14.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0D1117),
                            unfocusedContainerColor = Color(0xFF0D1117),
                            focusedBorderColor = accentBlue,
                            unfocusedBorderColor = strokeBorder,
                            focusedTextColor = textWhite,
                            unfocusedTextColor = textWhite
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("dialog_lang_search_input")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val scrollState = rememberScrollState()
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(scrollState)
                    ) {
                        if (filteredLanguages.isEmpty()) {
                            Text(
                                text = "No languages found",
                                color = textMuted,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            filteredLanguages.forEach { (code, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            triggerHaptic()
                                            viewModel.setLanguage(code)
                                            showLanguageDialog = false
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = currentLanguage == code,
                                        onClick = {
                                            triggerHaptic()
                                            viewModel.setLanguage(code)
                                            showLanguageDialog = false
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = accentBlue,
                                            unselectedColor = textMuted
                                        ),
                                        modifier = Modifier.testTag("lang_dialog_radio_$code")
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = label,
                                        color = if (currentLanguage == code) textWhite else textMuted,
                                        fontSize = 15.sp,
                                        fontWeight = if (currentLanguage == code) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showLanguageDialog = false }
                ) {
                    Text(stringResource(id = R.string.cancel), color = accentBlue, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(id = R.string.settings_title), 
                        fontSize = 20.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = textWhite
                    ) 
                },
                navigationIcon = {
                    IconButton(
                        onClick = { 
                            triggerHaptic()
                            onBack() 
                        },
                        modifier = Modifier.testTag("app_settings_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: App Theme
            item {
                Spacer(modifier = Modifier.height(8.dp))
                LocalSettingsCategoryCard(
                    title = stringResource(id = R.string.settings_appearance),
                    icon = Icons.Default.Palette,
                    tintColor = accentBlue
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    triggerHaptic()
                                    showThemeDialog = true
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(id = R.string.settings_theme_mode),
                                    color = textWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val activeLabel = when (themeMode) {
                                    "light" -> "Light Theme"
                                    "dark" -> "Dark Theme"
                                    else -> "System Default"
                                }
                                Text(
                                    text = activeLabel,
                                    color = textMuted,
                                    fontSize = 13.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Select Theme",
                                tint = textMuted
                            )
                        }

                        HorizontalDivider(color = strokeBorder)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    triggerHaptic()
                                    weChatViewModel.navigateToScreen(com.example.viewmodel.PlenxoScreen.LANGUAGE_SELECTION)
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(id = R.string.settings_language),
                                    color = textWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val activeLangLabel = languagesList.find { it.first == currentLanguage }?.second ?: "English"
                                Text(
                                    text = activeLangLabel,
                                    color = textMuted,
                                    fontSize = 13.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Select Language",
                                tint = textMuted
                            )
                        }

                        HorizontalDivider(color = strokeBorder)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    triggerHaptic()
                                    showBugReportSheet = true
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(id = R.string.settings_report_bug),
                                    color = textWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(id = R.string.settings_report_bug_sub),
                                    color = textMuted,
                                    fontSize = 13.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Report a Bug",
                                tint = textMuted
                            )
                        }
                    }
                }
            }

            // Section 2: Notifications
            item {
                LocalSettingsCategoryCard(
                    title = stringResource(id = R.string.settings_notifications),
                    icon = Icons.Default.NotificationsActive,
                    tintColor = accentBlue
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(id = R.string.settings_enable_notifications), color = textWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Switch(
                                checked = notificationSoundsEnabled,
                                onCheckedChange = { 
                                    triggerHaptic()
                                    viewModel.setNotificationSoundsEnabled(it) 
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = accentBlue,
                                    checkedTrackColor = accentBlue.copy(alpha = 0.5f)
                                )
                            )
                        }

                        HorizontalDivider(color = strokeBorder)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick { showSoundSheet = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(stringResource(id = R.string.settings_notification_ringtone), color = textWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                val friendlyRingtoneLabel = ringtoneList.find { it.first == selectedRingtone }?.second ?: selectedRingtone.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                Text(friendlyRingtoneLabel, color = textMuted, fontSize = 13.sp)
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Select",
                                tint = textMuted
                            )
                        }
                    }
                }
            }

            // Section 3: Chat Wallpaper
            item {
                val selectedWallpaper by weChatViewModel.selectedChatWallpaper.collectAsState()
                LocalSettingsCategoryCard(
                    title = stringResource(id = R.string.settings_chat_wallpaper),
                    icon = Icons.Default.Wallpaper,
                    tintColor = accentBlue
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Category 1: Static & Color Backgrounds
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(id = R.string.settings_static_wallpapers),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentBlue
                                )
                                Text(
                                    text = stringResource(id = R.string.settings_classic_styles),
                                    fontSize = 11.sp,
                                    color = textMuted
                                )
                            }
                            
                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val staticList = listOf(
                                    "DEFAULT" to "Default",
                                    "COSMIC_VOID" to "Cosmic",
                                    "NATURE_ZEN" to "Nature",
                                    "AI_NEON_WAVE" to "Neon Wave",
                                    "AI_DARK_GEOMETRIC" to "Geometric",
                                    "BG_OBSIDIAN_STRIKE" to "Obsidian",
                                    "BG_BOKEH_REST" to "Bokeh Rest",
                                    "BG_ZEN_GLOW" to "Zen Glow",
                                    "BG_ETHEREAL_LUNAR" to "Lunar",
                                    "BG_ROYAL_VELVET" to "Velvet",
                                    "BG_HACKERS_MATRIX" to "Hackers",
                                    "BG_DEEP_OCEAN" to "Deep Sea"
                                )
                                items(staticList.size) { index ->
                                    val (id, label) = staticList[index]
                                    ScrollableWallpaperItem(
                                        id = id,
                                        label = label,
                                        selectedWallpaper = selectedWallpaper,
                                        accentBlue = accentBlue,
                                        strokeBorder = strokeBorder,
                                        textWhite = textWhite,
                                        textMuted = textMuted,
                                        triggerHaptic = triggerHaptic,
                                        weChatViewModel = weChatViewModel
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = strokeBorder.copy(alpha = 0.5f))

                        // Category 2: 6 NEW MOTION BACKGROUNDS
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(id = R.string.settings_motion_wallpapers),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF8BC34A)
                                )
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF8BC34A).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.settings_live_motion),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF8BC34A)
                                    )
                                }
                            }
                            
                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val motionList = listOf(
                                    "MOTION_CYBER_MATRIX" to "Cyber Matrix",
                                    "MOTION_DEEP_OCEAN" to "Deep Ocean",
                                    "MOTION_STARRY_NIGHT" to "Starry Night",
                                    "MOTION_NEON_PULSE" to "Neon Pulse",
                                    "MOTION_LAVA_LAMP" to "Lava Lamp",
                                    "MOTION_FLOATING_BOKEH" to "Bokeh Drift"
                                )
                                items(motionList.size) { index ->
                                    val (id, label) = motionList[index]
                                    ScrollableWallpaperItem(
                                        id = id,
                                        label = label,
                                        selectedWallpaper = selectedWallpaper,
                                        accentBlue = accentBlue,
                                        strokeBorder = strokeBorder,
                                        textWhite = textWhite,
                                        textMuted = textMuted,
                                        triggerHaptic = triggerHaptic,
                                        weChatViewModel = weChatViewModel
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 4: System Animations removed

            // Section 5: Haptic Feedback
            item {
                LocalSettingsCategoryCard(
                    title = stringResource(id = R.string.settings_system_feedback),
                    icon = Icons.Default.Vibration,
                    tintColor = Color(0xFFFFC107)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(id = R.string.settings_haptic_feedback), color = textWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Switch(
                                checked = hapticEnabled,
                                onCheckedChange = { 
                                    triggerHaptic()
                                    viewModel.setHapticFeedbackEnabled(it) 
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFFC107),
                                    checkedTrackColor = Color(0xFFFFC107).copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
            }

            // Section 6: Security & Display
            item {
                val blockScreenshots by weChatViewModel.blockScreenshots.collectAsState()
                LocalSettingsCategoryCard(
                    title = stringResource(id = R.string.settings_security_display),
                    icon = Icons.Default.Security,
                    tintColor = Color(0xFF00BCD4)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(id = R.string.settings_allow_screenshots), color = textWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Switch(
                                checked = blockScreenshots,
                                onCheckedChange = { 
                                    triggerHaptic()
                                    weChatViewModel.saveBlockScreenshots(it) 
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF00BCD4),
                                    checkedTrackColor = Color(0xFF00BCD4).copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
            }

            // Section 7: Maintenance
            item {
                LocalSettingsCategoryCard(
                    title = stringResource(id = R.string.settings_maintenance),
                    icon = Icons.Default.Storage,
                    tintColor = Color(0xFFE91E63)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = {
                                triggerHaptic()
                                viewModel.clearCache {
                                    val successMsg = context.getString(R.string.settings_cache_cleared_toast)
                                    Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .border(1.dp, strokeBorder, RoundedCornerShape(10.dp)),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(stringResource(id = R.string.settings_clear_cache), color = Color(0xFFE91E63), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Section 8: Legal & Policies
            item {
                LocalSettingsCategoryCard(
                    title = "Legal & Policies",
                    icon = Icons.Default.Gavel,
                    tintColor = Color(0xFF9C27B0)
                ) {
                    Column {
                        // Privacy Policy Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    triggerHaptic()
                                    LegalWebUtils.openUrl(context, LegalWebUtils.PRIVACY_POLICY_URL)
                                }
                                .padding(16.dp)
                                .testTag("normal_privacy_policy_item"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Privacy Policy",
                                    tint = accentBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = "Privacy Policy",
                                        color = textWhite,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Read our privacy policy and data protection guidelines",
                                        color = textMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = textMuted
                            )
                        }

                        HorizontalDivider(color = strokeBorder, thickness = 1.dp)

                        // Terms & Conditions Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    triggerHaptic()
                                    LegalWebUtils.openUrl(context, LegalWebUtils.TERMS_CONDITIONS_URL)
                                }
                                .padding(16.dp)
                                .testTag("normal_terms_conditions_item"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Assignment,
                                    contentDescription = "Terms & Conditions",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = "Terms & Conditions",
                                        color = textWhite,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Read our terms of service and usage rules",
                                        color = textMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = textMuted
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun LocalSettingsCategoryCard(
    title: String,
    icon: ImageVector,
    tintColor: Color,
    content: @Composable () -> Unit
) {
    val cardBg = Color(0xFF161B22)
    val strokeBorder = Color(0xFF2D333B)
    val textWhite = Color(0xFFF0F6FC)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, strokeBorder, RoundedCornerShape(16.dp)),
        color = cardBg,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tintColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = textWhite
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ScrollableWallpaperItem(
    id: String,
    label: String,
    selectedWallpaper: String,
    accentBlue: Color,
    strokeBorder: Color,
    textWhite: Color,
    textMuted: Color,
    triggerHaptic: () -> Unit,
    weChatViewModel: PlenxoViewModel
) {
    val isSelected = selectedWallpaper == id
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .bounceClick {
                triggerHaptic()
                weChatViewModel.updateSelectedChatWallpaper(id)
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) accentBlue else strokeBorder,
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            WallpaperRenderer(id)
            if (id == "DEFAULT") {
                Box(modifier = Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.2f)))
            }

            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = accentBlue,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            color = if (isSelected) textWhite else textMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun RowScope.WallpaperItem(
    id: String,
    label: String,
    selectedWallpaper: String,
    accentBlue: Color,
    strokeBorder: Color,
    textWhite: Color,
    textMuted: Color,
    triggerHaptic: () -> Unit,
    weChatViewModel: PlenxoViewModel
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .bounceClick {
                triggerHaptic()
                weChatViewModel.updateSelectedChatWallpaper(id)
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (selectedWallpaper == id) 2.dp else 1.dp,
                    color = if (selectedWallpaper == id) accentBlue else strokeBorder,
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            WallpaperRenderer(id)
            if (id == "DEFAULT") {
                Box(modifier = Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.2f)))
            }

            if (selectedWallpaper == id) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = accentBlue,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            color = if (selectedWallpaper == id) textWhite else textMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}
