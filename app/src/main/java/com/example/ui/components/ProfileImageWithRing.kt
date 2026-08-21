package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun ProfileImageWithRing(
    imageUrl: String?,
    profileRingId: String?,
    modifier: Modifier = Modifier,
    ringBorderWidth: Int = 5, // in dp
    onClick: (() -> Unit)? = null
) {
    val ringId = profileRingId ?: "none"
    val hasRing = ringId.isNotEmpty() && ringId != "none" && ringId != "NONE"

    val ringBrush = when (ringId.lowercase()) {
        "ring_neon" -> Brush.sweepGradient(listOf(Color(0xFF00E5FF), Color(0xFF007BFF), Color(0xFF00E5FF)))
        "ring_gold" -> Brush.linearGradient(listOf(Color(0xFFFFE875), Color(0xFFC59B27), Color(0xFFFFE875)))
        "ring_ruby" -> Brush.linearGradient(listOf(Color(0xFFFF0844), Color(0xFFFFA07A), Color(0xFFFF0844)))
        "ring_emerald" -> Brush.linearGradient(listOf(Color(0xFF11998E), Color(0xFF38EF7D), Color(0xFF11998E)))
        "ring_dark" -> Brush.linearGradient(listOf(Color(0xFF0F2027), Color(0xFF2C5364), Color(0xFF0F2027)))
        "ring_tier_6" -> Brush.sweepGradient(listOf(Color(0xFF00FFFF), Color(0xFFFF00FF)))
        "ring_tier_7" -> Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFFFFF)))
        "ring_tier_8" -> Brush.linearGradient(listOf(Color(0xFF8A2BE2), Color(0xFF000000)))
        "ring_tier_9" -> Brush.linearGradient(listOf(Color(0xFFFF0000), Color(0xFFFF4500), Color(0xFFFFD700)))
        "ring_tier_10" -> Brush.linearGradient(listOf(Color(0xFFE0FFFF), Color(0xFFFFFFFF)))
        "ring_tier_11" -> Brush.linearGradient(listOf(Color(0xFF8A2BE2), Color(0xFFFF007F), Color(0xFFFFD700)))
        "ring_tier_12" -> Brush.sweepGradient(listOf(Color(0xFFFF0000), Color(0xFFFF7F00), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF0000FF), Color(0xFF8B00FF), Color(0xFFFF0000)))
        "ring_tier_13" -> Brush.linearGradient(listOf(Color(0xFF2E0854), Color(0xFF180B26), Color(0xFF000000)))
        "ring_tier_14" -> Brush.linearGradient(listOf(Color(0xFFFF0000), Color(0xFFFF4500), Color(0xFFFFD700)))
        "ring_tier_15" -> Brush.linearGradient(listOf(Color(0xFF00FFFF), Color(0xFFE0FFFF), Color(0xFFFFFFFF)))
        "none", "" -> null
        else -> Brush.linearGradient(listOf(Color(0xFF8B949E), Color(0xFF8B949E)))
    }

    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Box(
        modifier = modifier.then(clickableModifier),
        contentAlignment = Alignment.Center
    ) {
        // Pad the image inside to make sure the ring circles it beautifully without cutting face
        val imagePadding = if (hasRing) (ringBorderWidth + 1).dp else 0.dp
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(imagePadding)
                .clip(CircleShape)
                .background(Color.LightGray.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            if (!imageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    placeholder = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_gallery),
                    error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image),
                    contentDescription = "Profile Picture",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Placeholder",
                    tint = Color.Gray,
                    modifier = Modifier.fillMaxSize(0.6f)
                )
            }
        }

        if (hasRing && ringBrush != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = ringBorderWidth.dp,
                        brush = ringBrush,
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * Universal UserAvatar composable supporting profileRing and online indicator.
 */
@Composable
fun UserAvatar(
    profilePicUrl: String?,
    profileRing: String? = null,
    profileRingId: String? = null,
    displayName: String = "",
    plenxoId: String = "",
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    isOnline: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val ringId = profileRing?.takeIf { it.isNotBlank() && it != "none" }
        ?: profileRingId?.takeIf { it.isNotBlank() && it != "none" }
        ?: "none"

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        ProfileRingBox(
            ringId = ringId,
            ringPadding = 2.dp,
            borderWidth = 3.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.LightGray.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (!profilePicUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(profilePicUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "$displayName Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val initials = if (displayName.isNotBlank()) {
                        displayName.trim().take(2).uppercase()
                    } else if (plenxoId.isNotBlank()) {
                        plenxoId.trim().removePrefix("@").take(2).uppercase()
                    } else "P"

                    Text(
                        text = initials,
                        fontSize = (size.value * 0.38f).sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(size * 0.26f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50))
                    .border(1.5.dp, Color.White, CircleShape)
            )
        }
    }
}

