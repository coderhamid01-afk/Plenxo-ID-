package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.components.bounceClick

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import com.example.ui.components.SetMasterPinDialog
import com.example.ui.components.Disable2FADialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.util.LegalWebUtils
import com.example.viewmodel.PlenxoScreen
import com.example.ui.components.ProfileRingBox
import com.example.viewmodel.PlenxoViewModel
import com.example.ui.theme.PlenxoColors
import com.example.ui.theme.PlenxoSpacing
import com.example.ui.theme.PlenxoTypography

import com.example.ui.components.PlenxoTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PlenxoViewModel,
    primaryColor: Color
) {
    val context = LocalContext.current
    val userDisplayName by viewModel.displayName.collectAsState()
    val aboutStr by viewModel.aboutText.collectAsState()
    val liveProfile by viewModel.currentUserProfile.collectAsState()
    val galleryPic by viewModel.galleryImageUriString.collectAsState()
    val profilePicUrl = liveProfile?.profilePicUrl?.takeIf { it.isNotEmpty() } ?: galleryPic
    val ringIdFromLive = liveProfile?.profileRingId?.takeIf { it.isNotEmpty() && it != "none" }
    val localRingId by viewModel.profileRingId.collectAsState()
    val profileRingId = ringIdFromLive ?: localRingId
    
    var bioExpanded by remember { mutableStateOf(false) }
    val darkModeEnabled by viewModel.darkModeEnabled.collectAsState()
    
    val blockScreenshots = remember { mutableStateOf(com.example.util.SessionManager.isScreenshotsBlocked(context)) }
    val globalSpeed by com.example.util.AnimationManager.globalSpeed.collectAsState()
    val appThemeMode by viewModel.appThemeMode.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }

    val is2FAEnabled by viewModel.is2FAEnabled.collectAsState()
    var showSetMasterPinDialog by remember { mutableStateOf(false) }
    var showDisable2FADialog by remember { mutableStateOf(false) }

    // Premium Color Palette
    val darkBg = Color(0xFF131824)       // Dark Blue-Black Background
    val cardBg = Color(0xFF1C2234)       // Deep Slate Blue for Container Cards
    val accentBlue = Color(0xFF58A6FF)   // Sleek Royal Blue Accent
    val textWhite = Color(0xFFFFFFFF)    // High contrast primary text
    val textMuted = Color(0xFF9CA5BE)    // Elegant muted blue-gray for labels/secondary text
    val dividerColor = Color(0xFF2E3B5E) // Modern high-contrast navy divider

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Text(
                    text = "Theme / Appearance",
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
                        "LIGHT" to "Light Theme",
                        "DARK" to "Dark Theme",
                        "SYSTEM_DEFAULT" to "System Default"
                    ).forEach { (value, label) ->
                        val isSelected = appThemeMode.equals(value, ignoreCase = true) || 
                            (value == "SYSTEM_DEFAULT" && (appThemeMode.isBlank() || appThemeMode.equals("system", ignoreCase = true)))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateAppThemeMode(value)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.updateAppThemeMode(value)
                                    showThemeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = accentBlue,
                                    unselectedColor = textMuted
                                ),
                                modifier = Modifier.testTag("theme_dialog_radio_${value.lowercase()}")
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                color = if (isSelected) textWhite else textMuted,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showThemeDialog = false }
                ) {
                    Text("Cancel", color = accentBlue, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            PlenxoTopAppBar(
                title = stringResource(R.string.str_settings),
                onBackClick = {
                    if (!viewModel.navigateBack()) {
                        viewModel.navigateToScreen(PlenxoScreen.HOME, addToHistory = false, clearHistory = true)
                    }
                }
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
            item {
                Spacer(modifier = Modifier.height(16.dp))
                
                SettingsCategoryHeader("Privacy & Security")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = cardBg,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.settings_allow_screenshots), color = textWhite)
                        Switch(
                            checked = blockScreenshots.value,
                            onCheckedChange = {
                                blockScreenshots.value = it
                                com.example.util.SessionManager.saveScreenshotsBlocked(context, it)
                                (context as? Activity)?.recreate()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, dividerColor, RoundedCornerShape(20.dp)),
                    color = cardBg,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF34C759).copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF34C759),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "End-to-End Encryption",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = textWhite
                            )
                            Text(
                                text = "All chats and calls are secured with 256-bit client-side encryption. Keys are stored locally inside Hardware KeyStore.",
                                fontSize = 12.sp,
                                color = textMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Two-Factor Authentication (2FA) Tile
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, dividerColor, RoundedCornerShape(20.dp))
                        .bounceClick {
                            if (is2FAEnabled) {
                                showDisable2FADialog = true
                            } else {
                                showSetMasterPinDialog = true
                            }
                        }
                        .testTag("two_factor_auth_tile"),
                    color = cardBg,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(accentBlue.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = accentBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Two-Factor Authentication",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textWhite
                                )
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (is2FAEnabled) Color(0xFF238636).copy(alpha = 0.2f) else Color(0xFF8B949E).copy(alpha = 0.2f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (is2FAEnabled) "ENABLED" else "DISABLED",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (is2FAEnabled) Color(0xFF3FB950) else textMuted
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Require a 6-digit Secret Master PIN for advanced account security.",
                                fontSize = 12.sp,
                                color = textMuted
                            )
                        }
                        Switch(
                            checked = is2FAEnabled,
                            onCheckedChange = { targetChecked ->
                                if (targetChecked) {
                                    showSetMasterPinDialog = true
                                } else {
                                    showDisable2FADialog = true
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = accentBlue,
                                checkedThumbColor = darkBg
                            ),
                            modifier = Modifier.testTag("switch_settings_2fa_toggle")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                // Logged-in Devices Tile
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, dividerColor, RoundedCornerShape(20.dp))
                        .bounceClick { viewModel.navigateToScreen(PlenxoScreen.ACTIVE_SESSIONS) }
                        .testTag("logged_in_devices_tile"),
                    color = cardBg,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(accentBlue.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Devices,
                                contentDescription = null,
                                tint = accentBlue,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Logged-in Devices",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = textWhite
                            )
                            Text("Manage and terminate active sessions",
                                fontSize = 13.sp,
                                color = textMuted
                            )
                        }
                        
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                SettingsCategoryHeader("Animations")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = cardBg,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Global Animation Speed: ${globalSpeed}%", color = textWhite)
                        Slider(
                            value = globalSpeed.toFloat(),
                            onValueChange = { com.example.util.AnimationManager.setGlobalSpeed(it.toInt()) },
                            valueRange = 0f..100f
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Profile Settings Tile
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, dividerColor, RoundedCornerShape(20.dp))
                        .bounceClick { viewModel.navigateToScreen(PlenxoScreen.SETTINGS_PROFILE) }
                        .testTag("profile_settings_tile"),
                    color = cardBg,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(74.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ProfileRingBox(ringId = profileRingId, ringPadding = 2.dp, borderWidth = 3.dp) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(darkBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!profilePicUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = profilePicUrl,
                                            contentDescription = "Profile Picture",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "Avatar",
                                            tint = textMuted,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.str_profile_settings),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = textWhite
                            )
                            Text(stringResource(id = R.string.str_identity_cloud_sync),
                                fontSize = 13.sp,
                                color = textMuted
                            )
                        }
                        
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = textMuted
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                
                SettingsCategoryHeader("App & General")
                
                // Theme / Appearance Tile
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, dividerColor, RoundedCornerShape(20.dp))
                        .bounceClick { showThemeDialog = true }
                        .testTag("theme_appearance_tile"),
                    color = cardBg,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(accentBlue.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = accentBlue,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Theme / Appearance",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = textWhite
                            )
                            val subtitleText = when {
                                appThemeMode.equals("LIGHT", ignoreCase = true) || appThemeMode.equals("light", ignoreCase = true) -> "Light Theme"
                                appThemeMode.equals("DARK", ignoreCase = true) || appThemeMode.equals("dark", ignoreCase = true) -> "Dark Theme"
                                else -> "System Default"
                            }
                            Text(
                                text = subtitleText,
                                fontSize = 13.sp,
                                color = textMuted
                            )
                        }
                        
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // App Settings Tile
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, dividerColor, RoundedCornerShape(20.dp))
                        .bounceClick { viewModel.navigateToScreen(PlenxoScreen.SETTINGS_NORMAL) }
                        .testTag("app_settings_tile"),
                    color = cardBg,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(accentBlue.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = accentBlue,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.settings_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = textWhite
                            )
                            Text(stringResource(id = R.string.str_theme_notifications_local_config),
                                fontSize = 13.sp,
                                color = textMuted
                            )
                        }
                        
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = textMuted
                        )
                    }
                }
            }

            item {
                // Language Selection Tile
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, dividerColor, RoundedCornerShape(20.dp))
                        .bounceClick { viewModel.navigateToScreen(PlenxoScreen.LANGUAGE_SELECTION) }
                        .testTag("language_selection_tile"),
                    color = cardBg,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFF4CAF50).copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Language",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = textWhite
                            )
                            Text("Change app language settings",
                                fontSize = 13.sp,
                                color = textMuted
                            )
                        }
                        
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = textMuted
                        )
                    }
                }
            }



            item {
                // Chat Requests Tile
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, dividerColor, RoundedCornerShape(20.dp))
                        .bounceClick { viewModel.navigateToScreen(PlenxoScreen.CHAT_REQUESTS) }
                        .testTag("settings_chat_requests_tile"),
                    color = cardBg,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        BadgedBox(
                            badge = {
                                val count = viewModel.pendingFriendRequests.collectAsState().value.size
                                if (count > 0) {
                                    Badge(
                                        containerColor = Color(0xFFEF4444),
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = count.toString(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(accentBlue.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    tint = accentBlue,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Chat Requests",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = textWhite
                            )
                            Text("View pending friend and chat requests",
                                fontSize = 13.sp,
                                color = textMuted
                            )
                        }
                        
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = textMuted
                        )
                    }
                }
            }

            // Legal & Policies Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsCategoryHeader("Legal & Policies")
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, dividerColor, RoundedCornerShape(20.dp)),
                    color = cardBg,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column {
                        // Privacy Policy Item
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LegalWebUtils.openUrl(context, LegalWebUtils.PRIVACY_POLICY_URL)
                                }
                                .padding(20.dp)
                                .testTag("privacy_policy_item"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(accentBlue.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Privacy Policy",
                                    tint = accentBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Privacy Policy",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textWhite
                                )
                                Text(
                                    text = "Read our privacy policy and data protection guidelines",
                                    fontSize = 12.sp,
                                    color = textMuted
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = textMuted
                            )
                        }

                        HorizontalDivider(color = dividerColor, thickness = 1.dp)

                        // Terms & Conditions Item
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LegalWebUtils.openUrl(context, LegalWebUtils.TERMS_CONDITIONS_URL)
                                }
                                .padding(20.dp)
                                .testTag("terms_conditions_item"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFF4CAF50).copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Assignment,
                                    contentDescription = "Terms & Conditions",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Terms & Conditions",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textWhite
                                )
                                Text(
                                    text = "Read our terms of service and usage rules",
                                    fontSize = 12.sp,
                                    color = textMuted
                                )
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

            // Footer Branding / Sign Out Action
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        viewModel.logout()
                        Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("logout_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Logout",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Logout",
                        color = Color(0xFFEF4444),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (showSetMasterPinDialog) {
            SetMasterPinDialog(
                viewModel = viewModel,
                onDismiss = { showSetMasterPinDialog = false }
            )
        }

        if (showDisable2FADialog) {
            Disable2FADialog(
                viewModel = viewModel,
                onDismiss = { showDisable2FADialog = false }
            )
        }
    }
}

@Composable
fun SettingsCategoryHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF9CA5BE),
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 6.dp)
    )
}

@Composable
fun PremiumSettingsRow(
    icon: ImageVector,
    title: String,
    onClick: (() -> Unit)? = null,
    action: @Composable () -> Unit
) {
    val rowModifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .bounceClick { onClick() }
            .padding(16.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF58A6FF),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        action()
    }
}
