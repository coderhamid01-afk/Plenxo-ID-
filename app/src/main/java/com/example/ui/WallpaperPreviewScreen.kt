package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.viewmodel.PlenxoScreen
import com.example.viewmodel.PlenxoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperPreviewScreen(
    viewModel: PlenxoViewModel
) {
    val wallpaperEntity by viewModel.selectedWallpaperForPreview.collectAsState()
    val derivedName = remember(wallpaperEntity) {
        wallpaperEntity?.wallpaperId?.replace("_", " ")?.uppercase() ?: "Wallpaper Preview"
    }
    val activeConversationId by viewModel.activeWallpaperConversationId.collectAsState()
    
    // We can also retrieve existing mapping to initialize the opacity slider
    val existingMappingFlow = remember(activeConversationId) {
        viewModel.getWallpaperMappingForConversation(activeConversationId ?: "GLOBAL_DEFAULT")
    }
    val existingMapping by existingMappingFlow.collectAsState(initial = null)
    
    var opacity by remember { mutableStateOf(0.5f) }
    
    // Set initial opacity when mapping loads
    LaunchedEffect(existingMapping) {
        existingMapping?.let {
            opacity = it.backgroundOpacity
        }
    }

    // Midnight Theme Colors
    val darkBg = Color(0xFF0D1117)       // Primary Surface Backdrop
    val cardBg = Color(0xFF161B22)       // Structural Elevated Cards
    val strokeBorder = Color(0xFF2D333B) // Secondary Stroke Borders
    val accentBlue = Color(0xFF58A6FF)   // Active Component Accents
    val textWhite = Color(0xFFF0F6FC)    // Core Typography Foreground
    val textMuted = Color(0xFF8B949E)    // Muted Labels / Descriptions

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = derivedName, 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = textWhite
                    ) 
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("wallpaper_preview_back")
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
        val wallpaper = wallpaperEntity
        if (wallpaper == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.str_no_wallpaper_selected), color = textMuted)
            }
        } else {
            // Check download status reactively
            val isDownloaded = wallpaper.isDownloaded && wallpaper.localFilePath != null

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Background Preview (Full Screen with active opacity scaling)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    // Use local file path if ready, otherwise use high-res cloud URL
                    val imageSource = if (isDownloaded) wallpaper.localFilePath else wallpaper.cloudUrl
                    
                    AsyncImage(
                        model = imageSource,
                        contentDescription = "Wallpaper Preview Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        alpha = opacity
                    )
                }

                // Bottom Styling Control Drawer Overlay
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(1.dp, strokeBorder, RoundedCornerShape(20.dp))
                        .testTag("wallpaper_control_card"),
                    color = cardBg.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = derivedName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textWhite
                                )
                                Text(
                                    text = "Category: ${wallpaper.category}",
                                    fontSize = 12.sp,
                                    color = textMuted
                                )
                            }
                            
                            // Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isDownloaded) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Downloaded",
                                        tint = Color(0xFF2EA44F),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(stringResource(id = R.string.str_cached_locally),
                                        fontSize = 12.sp,
                                        color = Color(0xFF2EA44F),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = "Cloud",
                                        tint = accentBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(stringResource(id = R.string.str_cloud_asset),
                                        fontSize = 12.sp,
                                        color = textMuted
                                    )
                                }
                            }
                        }

                        if (!isDownloaded) {
                            // Needs Downloading flow
                            Button(
                                onClick = { viewModel.downloadWallpaper(wallpaper) },
                                colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("wallpaper_download_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Download icon"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(id = R.string.str_download_4k_asset_offline),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = darkBg
                                )
                            }
                        } else {
                            // Active styling & continuous opacity slider
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(id = R.string.str_background_opacity_tint),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textMuted
                                    )
                                    Text(
                                        text = "${(opacity * 100).toInt()}%",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accentBlue
                                    )
                                }
                                
                                Slider(
                                    value = opacity,
                                    onValueChange = { opacity = it },
                                    valueRange = 0.1f..1.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = accentBlue,
                                        activeTrackColor = accentBlue,
                                        inactiveTrackColor = strokeBorder
                                    ),
                                    modifier = Modifier.testTag("opacity_slider")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Apply Wallpaper mapping
                                Button(
                                    onClick = {
                                        val chatId = activeConversationId
                                        viewModel.setWallpaperMappingForConversation(
                                            chatId ?: "GLOBAL_DEFAULT",
                                            wallpaper.wallpaperId,
                                            opacity
                                        )
                                        // Return back
                                        if (chatId != null) {
                                            viewModel.navigateBackTo(PlenxoScreen.CHAT_DETAIL)
                                        } else {
                                            viewModel.navigateBackTo(PlenxoScreen.SETTINGS_NORMAL)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("apply_wallpaper_btn"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = if (activeConversationId != null) "Apply to Chat" else "Apply Globally",
                                        fontWeight = FontWeight.Bold,
                                        color = darkBg,
                                        fontSize = 14.sp
                                    )
                                }

                                // Reset/Remove mapping
                                OutlinedButton(
                                    onClick = {
                                        viewModel.setWallpaperMappingForConversation(
                                            activeConversationId ?: "GLOBAL_DEFAULT",
                                            null,
                                            0.5f
                                        )
                                        if (activeConversationId != null) {
                                            viewModel.navigateBackTo(PlenxoScreen.CHAT_DETAIL)
                                        } else {
                                            viewModel.navigateBackTo(PlenxoScreen.SETTINGS_NORMAL)
                                        }
                                    },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("reset_wallpaper_btn"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(stringResource(id = R.string.str_reset),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
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
