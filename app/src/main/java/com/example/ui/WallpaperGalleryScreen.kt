package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.viewmodel.PlenxoScreen
import com.example.viewmodel.PlenxoViewModel
import com.example.database.ChatWallpaperEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperGalleryScreen(
    viewModel: PlenxoViewModel
) {
    var selectedCategory by remember { mutableStateOf("MINIMALIST") }
    val categories = listOf("MINIMALIST", "AESTHETIC", "ROMANTIC", "SAD")
    
    val wallpapers by viewModel.allWallpapers.collectAsState(initial = emptyList())
    val filteredWallpapers = wallpapers.filter { it.category == selectedCategory }

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
                    Text(stringResource(R.string.str_chat_wallpapers), 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = textWhite
                    ) 
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.navigateBack()
                        },
                        modifier = Modifier.testTag("wallpaper_gallery_back")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Category Tabs
            ScrollableTabRow(
                selectedTabIndex = categories.indexOf(selectedCategory),
                containerColor = darkBg,
                contentColor = accentBlue,
                edgePadding = 16.dp,
                divider = { HorizontalDivider(color = strokeBorder) }
            ) {
                categories.forEach { category ->
                    Tab(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        text = { 
                            Text(
                                text = category, 
                                fontWeight = FontWeight.Bold,
                                color = if (selectedCategory == category) accentBlue else textMuted
                            ) 
                        },
                        modifier = Modifier.testTag("category_tab_$category")
                    )
                }
            }

            if (filteredWallpapers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading wallpapers in $selectedCategory...", 
                        color = textMuted, 
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(filteredWallpapers, key = { it.wallpaperId }) { wallpaper ->
                        val wallpaperName = wallpaper.wallpaperId.replace("_", " ").uppercase()
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clickable {
                                    viewModel.selectedWallpaperForPreview.value = wallpaper
                                    viewModel.navigateToScreen(PlenxoScreen.WALLPAPER_PREVIEW)
                                }
                                .border(1.dp, strokeBorder, RoundedCornerShape(12.dp))
                                .testTag("wallpaper_card_${wallpaper.wallpaperId}"),
                            color = cardBg,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Background preview thumbnail
                                AsyncImage(
                                    model = wallpaper.thumbnailCloudUrl,
                                    contentDescription = wallpaperName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Foreground gradient overlay
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.8f)
                                                )
                                            )
                                        )
                                )

                                // Text metadata and status badge
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = wallpaperName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textWhite
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (wallpaper.isDownloaded && wallpaper.localFilePath != null) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Ready",
                                                tint = Color(0xFF2EA44F),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(stringResource(id = R.string.str_ready_offline),
                                                fontSize = 11.sp,
                                                color = Color(0xFF2EA44F),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.CloudDownload,
                                                contentDescription = "Requires Download",
                                                tint = accentBlue,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(stringResource(id = R.string.str_requires_download),
                                                fontSize = 11.sp,
                                                color = textMuted
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
    }
}
