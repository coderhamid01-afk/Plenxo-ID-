package com.example.ui.settings

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.components.PlenxoTopAppBar
import com.example.ui.components.ProfileRingBox
import com.example.ui.components.bounceClick
import com.example.util.LegalWebUtils
import com.example.viewmodel.PlenxoScreen
import com.example.viewmodel.PlenxoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PlenxoViewModel,
    primaryColor: Color
) {
    val context = LocalContext.current
    val liveProfile by viewModel.currentUserProfile.collectAsState()
    val galleryPic by viewModel.galleryImageUriString.collectAsState()
    val profilePicUrl = liveProfile?.profilePicUrl?.takeIf { it.isNotEmpty() } ?: galleryPic
    val ringIdFromLive = liveProfile?.profileRingId?.takeIf { it.isNotEmpty() && it != "none" }
    val localRingId by viewModel.profileRingId.collectAsState()
    val profileRingId = ringIdFromLive ?: localRingId

    val blockScreenshots = remember { mutableStateOf(com.example.util.SessionManager.isScreenshotsBlocked(context)) }
    val globalSpeed by com.example.util.AnimationManager.globalSpeed.collectAsState()
    val appThemeMode by viewModel.appThemeMode.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }

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
