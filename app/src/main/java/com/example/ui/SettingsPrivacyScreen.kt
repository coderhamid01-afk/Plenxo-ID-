package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Shield
import com.example.ui.components.SetMasterPinDialog
import com.example.ui.components.Disable2FADialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.util.LegalWebUtils
import com.example.viewmodel.PlenxoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPrivacyScreen(viewModel: PlenxoViewModel, primaryColor: Color) {
    val context = LocalContext.current
    val lastSeenVis by viewModel.lastSeenVisibility.collectAsState()
    val photoVis by viewModel.profilePhotoVisibility.collectAsState()
    val bioVis by viewModel.bioVisibility.collectAsState()
    val readReceipts by viewModel.readReceiptsEnabled.collectAsState()
    val is2FAEnabled by viewModel.is2FAEnabled.collectAsState()
    
    var showLastSeenMenu by remember { mutableStateOf(false) }
    var showPhotoMenu by remember { mutableStateOf(false) }
    var showBioMenu by remember { mutableStateOf(false) }
    var showSetMasterPinDialog by remember { mutableStateOf(false) }
    var showDisable2FADialog by remember { mutableStateOf(false) }

    val darkBg = Color(0xFF131824)
    val cardBg = Color(0xFF1C2234)
    val accentBlue = Color(0xFF58A6FF)
    val textWhite = Color(0xFFFFFFFF)
    val textMuted = Color(0xFF9CA5BE)
    val dividerColor = Color(0xFF2E3B5E)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.str_privacy_controls), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textWhite) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = accentBlue)
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsCategoryHeader("Information Visibility")
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, dividerColor, RoundedCornerShape(16.dp)),
                    color = cardBg,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        PremiumSettingsRow(
                            icon = Icons.Default.Visibility,
                            title = "Last Seen: $lastSeenVis",
                            onClick = { showLastSeenMenu = true }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = textMuted)
                        }
                        DropdownMenu(
                            expanded = showLastSeenMenu,
                            onDismissRequest = { showLastSeenMenu = false }
                        ) {
                            listOf("EVERYONE", "CONTACTS", "NOBODY").forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        viewModel.saveLastSeenVis(option)
                                        showLastSeenMenu = false
                                    }
                                )
                            }
                        }
                        
                        HorizontalDivider(color = dividerColor, thickness = 1.dp)

                        PremiumSettingsRow(
                            icon = Icons.Default.Image,
                            title = "Profile Photo: $photoVis",
                            onClick = { showPhotoMenu = true }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = textMuted)
                        }
                        DropdownMenu(
                            expanded = showPhotoMenu,
                            onDismissRequest = { showPhotoMenu = false }
                        ) {
                            listOf("EVERYONE", "CONTACTS", "NOBODY").forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        viewModel.savePhotoVis(option)
                                        showPhotoMenu = false
                                    }
                                )
                            }
                        }

                        HorizontalDivider(color = dividerColor, thickness = 1.dp)

                        PremiumSettingsRow(
                            icon = Icons.Default.Info,
                            title = "Bio Visibility: $bioVis",
                            onClick = { showBioMenu = true }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = textMuted)
                        }
                        DropdownMenu(
                            expanded = showBioMenu,
                            onDismissRequest = { showBioMenu = false }
                        ) {
                            listOf("EVERYONE", "CONTACTS", "NOBODY").forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        viewModel.saveBioVis(option)
                                        showBioMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsCategoryHeader("Interactions")
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, dividerColor, RoundedCornerShape(16.dp)),
                    color = cardBg,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        PremiumSettingsRow(
                            icon = Icons.Default.DoneAll,
                            title = "Read Receipts"
                        ) {
                            Switch(
                                checked = readReceipts,
                                onCheckedChange = { viewModel.saveReadReceipts(it) },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = accentBlue,
                                    checkedThumbColor = darkBg
                                )
                            )
                        }
                    }
                }
            }

            item {
                SettingsCategoryHeader("Advanced Security")
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, dividerColor, RoundedCornerShape(16.dp)),
                    color = cardBg,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        PremiumSettingsRow(
                            icon = Icons.Default.Shield,
                            title = "Two-Factor Authentication (2FA)",
                            onClick = {
                                if (is2FAEnabled) {
                                    showDisable2FADialog = true
                                } else {
                                    showSetMasterPinDialog = true
                                }
                            }
                        ) {
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
                                modifier = Modifier.testTag("switch_2fa_toggle")
                            )
                        }

                        HorizontalDivider(color = dividerColor, thickness = 1.dp)

                        PremiumSettingsRow(
                            icon = Icons.Default.Lock,
                            title = "App Lock Setup",
                            onClick = { viewModel.navigateToAppLockSetup() }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = textMuted)
                        }
                        
                        HorizontalDivider(color = dividerColor, thickness = 1.dp)

                        PremiumSettingsRow(
                            icon = Icons.Default.Devices,
                            title = "Active Sessions",
                            onClick = { viewModel.navigateToActiveSessions() }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = textMuted)
                        }
                    }
                }
            }

            item {
                SettingsCategoryHeader("Legal & Policies")
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, dividerColor, RoundedCornerShape(16.dp)),
                    color = cardBg,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column {
                        PremiumSettingsRow(
                            icon = Icons.Default.Security,
                            title = "Privacy Policy",
                            onClick = { LegalWebUtils.openUrl(context, LegalWebUtils.PRIVACY_POLICY_URL) }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = textMuted)
                        }
                        
                        HorizontalDivider(color = dividerColor, thickness = 1.dp)

                        PremiumSettingsRow(
                            icon = Icons.Default.Assignment,
                            title = "Terms & Conditions",
                            onClick = { LegalWebUtils.openUrl(context, LegalWebUtils.TERMS_CONDITIONS_URL) }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = textMuted)
                        }
                    }
                }
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
