package com.example.ui

import com.example.R

import android.widget.Toast
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.UserProfileDomainModel
import com.example.ui.theme.PlenxoColors
import com.example.ui.theme.PlenxoTypography
import com.example.viewmodel.ProfileSettingsViewModel
import com.example.viewmodel.ProfileUiState
import com.example.viewmodel.PlenxoViewModel

data class RingOption(
    val id: String,
    val name: String,
    val brush: Brush,
    val colors: List<Color>,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileRingsScreen(
    viewModel: ProfileSettingsViewModel,
    weChatViewModel: PlenxoViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.profileUiState.collectAsState()

    // Definition of the 5 premium rings
    val ringOptions = remember {
        listOf(
            RingOption(
                id = "none",
                name = "No Ring",
                brush = Brush.linearGradient(listOf(Color(0xFF8B949E), Color(0xFF8B949E))),
                colors = listOf(Color(0xFF8B949E)),
                description = "Classic default with no custom border."
            ),
            RingOption(
                id = "ring_neon",
                name = "Neon Blue Glow",
                brush = Brush.sweepGradient(listOf(Color(0xFF00E5FF), Color(0xFF007BFF), Color(0xFF00E5FF))),
                colors = listOf(Color(0xFF00E5FF), Color(0xFF007BFF)),
                description = "A futuristic electric neon cyan cyber glow."
            ),
            RingOption(
                id = "ring_gold",
                name = "Classic Royal Gold",
                brush = Brush.linearGradient(listOf(Color(0xFFFFE875), Color(0xFFC59B27), Color(0xFFFFE875))),
                colors = listOf(Color(0xFFFFD700), Color(0xFFC59B27)),
                description = "An exquisite, majestic golden royal border."
            ),
            RingOption(
                id = "ring_ruby",
                name = "Ruby Red Gradient",
                brush = Brush.linearGradient(listOf(Color(0xFFFF0844), Color(0xFFFFA07A), Color(0xFFFF0844))),
                colors = listOf(Color(0xFFFF1744), Color(0xFFFF5252)),
                description = "A fiery red and hot magenta star-forged ruby."
            ),
            RingOption(
                id = "ring_emerald",
                name = "Emerald Green",
                brush = Brush.linearGradient(listOf(Color(0xFF11998E), Color(0xFF38EF7D), Color(0xFF11998E))),
                colors = listOf(Color(0xFF00E676), Color(0xFF11998E)),
                description = "A vibrant bio-luminescent deep emerald forest."
            ),
            RingOption(
                id = "ring_dark",
                name = "Minimalist Dark",
                brush = Brush.linearGradient(listOf(Color(0xFF0F2027), Color(0xFF2C5364), Color(0xFF0F2027))),
                colors = listOf(Color(0xFF2C5364), Color(0xFF0F2027)),
                description = "Sleek, glossy graphite obsidian shade."
            ),
            // NEW RINGS
            RingOption(
                id = "ring_tier_6",
                name = "Neon Cyber Ring",
                brush = Brush.sweepGradient(listOf(Color(0xFF00FFFF), Color(0xFFFF00FF))),
                colors = listOf(Color(0xFF00FFFF), Color(0xFFFF00FF)),
                description = "Pulsing cyan and magenta gradient."
            ),
            RingOption(
                id = "ring_tier_7",
                name = "Plasma Gold Ring",
                brush = Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFFFFF))),
                colors = listOf(Color(0xFFFFD700), Color(0xFFFFFFFF)),
                description = "Rich gold and white gradient."
            ),
            RingOption(
                id = "ring_tier_8",
                name = "Amethyst Void Ring",
                brush = Brush.linearGradient(listOf(Color(0xFF8A2BE2), Color(0xFF000000))),
                colors = listOf(Color(0xFF8A2BE2), Color(0xFF000000)),
                description = "Deep purple and black void."
            ),
            RingOption(
                id = "ring_tier_9",
                name = "Crimson Inferno Ring",
                brush = Brush.linearGradient(listOf(Color(0xFFFF0000), Color(0xFFFF4500), Color(0xFFFFD700))),
                colors = listOf(Color(0xFFFF0000), Color(0xFFFF4500), Color(0xFFFFD700)),
                description = "Fiery red, orange, and yellow."
            ),
            RingOption(
                id = "ring_tier_10",
                name = "Diamond Frost Ring",
                brush = Brush.linearGradient(listOf(Color(0xFFE0FFFF), Color(0xFFFFFFFF))),
                colors = listOf(Color(0xFFE0FFFF), Color(0xFFFFFFFF)),
                description = "Icy blue and white frost."
            ),
            RingOption(
                id = "ring_tier_11",
                name = "Cyberpunk Neon",
                brush = Brush.linearGradient(listOf(Color(0xFF8A2BE2), Color(0xFFFF007F), Color(0xFFFFD700))),
                colors = listOf(Color(0xFF8A2BE2), Color(0xFFFF007F), Color(0xFFFFD700)),
                description = "Cyberpunk neon purple, pink and gold."
            ),
            RingOption(
                id = "ring_tier_12",
                name = "Rainbow Prism",
                brush = Brush.sweepGradient(listOf(Color(0xFFFF0000), Color(0xFFFF7F00), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF0000FF), Color(0xFF8B00FF), Color(0xFFFF0000))),
                colors = listOf(Color(0xFFFF0000), Color(0xFFFF7F00), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF0000FF), Color(0xFF8B00FF)),
                description = "Full spectrum rainbow prism gradient."
            ),
            RingOption(
                id = "ring_tier_13",
                name = "Cosmic Void",
                brush = Brush.linearGradient(listOf(Color(0xFF2E0854), Color(0xFF180B26), Color(0xFF000000))),
                colors = listOf(Color(0xFF2E0854), Color(0xFF180B26), Color(0xFF000000)),
                description = "Deep cosmic dark void gradient."
            ),
            RingOption(
                id = "ring_tier_14",
                name = "Blaze Fire",
                brush = Brush.linearGradient(listOf(Color(0xFFFF0000), Color(0xFFFF4500), Color(0xFFFFD700))),
                colors = listOf(Color(0xFFFF0000), Color(0xFFFF4500), Color(0xFFFFD700)),
                description = "Blazing red, orange and gold fire."
            ),
            RingOption(
                id = "ring_tier_15",
                name = "Diamond Ice",
                brush = Brush.linearGradient(listOf(Color(0xFF00FFFF), Color(0xFFE0FFFF), Color(0xFFFFFFFF))),
                colors = listOf(Color(0xFF00FFFF), Color(0xFFE0FFFF), Color(0xFFFFFFFF)),
                description = "Pristine ice diamond white and cyan."
            )
        )
    }

    when (val state = uiState) {
        is ProfileUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PlenxoColors.Background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PlenxoColors.Primary)
            }
        }
        is ProfileUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PlenxoColors.Background)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Error: ${state.message}",
                    color = PlenxoColors.Error,
                    style = PlenxoTypography.Body
                )
            }
        }
        is ProfileUiState.Success -> {
            val profile = state.profile
            var selectedRingId by remember { mutableStateOf(profile.profileRingId) }
            val currentSelectedRing = ringOptions.find { it.id == selectedRingId } ?: ringOptions.first()

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(stringResource(id = R.string.str_profile_rings),
                                color = PlenxoColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                style = PlenxoTypography.Title.copy(fontSize = 20.sp)
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = PlenxoColors.Surface),
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = PlenxoColors.Primary
                                )
                            }
                        }
                    )
                },
                containerColor = PlenxoColors.Background
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top: Preview Area
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(stringResource(id = R.string.str_preview),
                            color = PlenxoColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // Large Avatar Box with Ring
                        Box(
                            modifier = Modifier
                                .size(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Scale down image slightly if there is a ring to make space for the border
                            val hasActiveRing = selectedRingId != "none" && selectedRingId.isNotEmpty()
                            val imagePadding = if (hasActiveRing) 10.dp else 0.dp

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(imagePadding)
                                    .clip(CircleShape)
                                    .background(PlenxoColors.Surface),
                                contentAlignment = Alignment.Center
                            ) {
                                if (profile.profileUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = profile.profileUrl,
                                        contentDescription = "User Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Placeholder",
                                        tint = PlenxoColors.TextSecondary,
                                        modifier = Modifier.size(80.dp)
                                    )
                                }
                            }

                            // Active Ring Outer Border
                            if (hasActiveRing) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .border(
                                            width = 6.dp, // Premium 6dp circular border
                                            brush = currentSelectedRing.brush,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = profile.name,
                            color = PlenxoColors.TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (selectedRingId == "none") "Default Frame" else currentSelectedRing.name,
                            color = if (selectedRingId == "none") PlenxoColors.TextSecondary else currentSelectedRing.colors.first(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // Middle: Ring Selector List
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(stringResource(id = R.string.str_choose_your_aesthetic_ring),
                            color = PlenxoColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ring_options_row")
                        ) {
                            items(ringOptions) { option ->
                                val isSelected = option.id == selectedRingId
                                val ringScale by animateFloatAsState(if (isSelected) 1.05f else 1f)

                                Card(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .height(140.dp)
                                        .scale(ringScale)
                                        .clickable { selectedRingId = option.id }
                                        .testTag("ring_option_${option.id}"),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) PlenxoColors.Surface else PlenxoColors.Surface.copy(alpha = 0.5f)
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) option.colors.first() else PlenxoColors.Divider
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // Miniature Preview Ring
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(PlenxoColors.Background),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (option.id != "none") {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .border(
                                                            width = 4.dp,
                                                            brush = option.brush,
                                                            shape = CircleShape
                                                        )
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = PlenxoColors.TextSecondary.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = if (option.id == "none") PlenxoColors.Primary else option.colors.first(),
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .align(Alignment.BottomEnd)
                                                        .background(PlenxoColors.Surface, CircleShape)
                                                )
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = option.name,
                                                color = PlenxoColors.TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (option.id == "none") "Default" else "Premium Ring",
                                                color = PlenxoColors.TextSecondary,
                                                fontSize = 10.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Description text of selected ring
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                            color = PlenxoColors.Surface.copy(alpha = 0.4f),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (selectedRingId == "none") PlenxoColors.TextSecondary else currentSelectedRing.colors.first())
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = currentSelectedRing.description,
                                    color = PlenxoColors.TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bottom: Done / Apply Button
                    Button(
                        onClick = {
                            viewModel.updateProfileRing(selectedRingId) { success ->
                                if (success) {
                                    weChatViewModel.profileRingId.value = selectedRingId
                                    weChatViewModel.currentUserProfile.value = weChatViewModel.currentUserProfile.value?.copy(profileRingId = selectedRingId)
                                    Toast.makeText(context, "Profile ring updated successfully.", Toast.LENGTH_SHORT).show()
                                    onBack()
                                } else {
                                    Toast.makeText(context, "Failed to update profile ring.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("apply_ring_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedRingId == "none") PlenxoColors.Primary else currentSelectedRing.colors.first()
                        ),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Text(stringResource(id = R.string.str_apply_ring),
                            color = PlenxoColors.Background,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
