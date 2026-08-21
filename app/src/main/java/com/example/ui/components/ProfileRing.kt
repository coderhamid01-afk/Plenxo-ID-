package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ProfileRing(
    tier: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val ringColor = when (tier) {
        "Bronze" -> Color(0xFFCD7F32)
        "Silver" -> Color(0xFFC0C0C0)
        "Gold" -> Color(0xFFFFD700)
        "Diamond" -> Color(0xFFB9F2FF)
        "Platinum" -> Color(0xFFE5E4E2)
        "Red Ruby" -> Color(0xFFFF0000)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .padding(4.dp)
            .border(
                width = if (tier != "None" && tier != "") 3.dp else 0.dp,
                color = ringColor,
                shape = CircleShape
            )
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
        
        if (tier != "None" && tier != "") {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.BottomEnd)
                    .background(ringColor, CircleShape)
                    .border(1.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Stars,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
fun ProfileRingBox(
    ringId: String?,
    modifier: Modifier = Modifier,
    ringPadding: Dp = 4.dp,
    borderWidth: Dp = 5.dp,
    content: @Composable () -> Unit
) {
    val normalizedId = ringId?.lowercase()?.trim() ?: "none"
    
    val brush = when (normalizedId) {
        "ring_neon" -> Brush.linearGradient(colors = listOf(Color(0xFF00F0FF), Color(0xFF0072FF)))
        "ring_gold" -> Brush.linearGradient(colors = listOf(Color(0xFFFFD700), Color(0xFFFFA500), Color(0xFFFF8C00)))
        "ring_ruby" -> Brush.linearGradient(colors = listOf(Color(0xFFFF007F), Color(0xFFE0115F), Color(0xFF8B0000)))
        "ring_emerald" -> Brush.linearGradient(colors = listOf(Color(0xFF38EF7D), Color(0xFF11998E)))
        "ring_dark" -> Brush.linearGradient(colors = listOf(Color(0xFF4B5563), Color(0xFF1F2937), Color(0xFF111827)))
        "ring_tier_6" -> Brush.sweepGradient(colors = listOf(Color(0xFF00FFFF), Color(0xFFFF00FF)))
        "ring_tier_7" -> Brush.linearGradient(colors = listOf(Color(0xFFFFD700), Color(0xFFFFFFFF)))
        "ring_tier_8" -> Brush.linearGradient(colors = listOf(Color(0xFF8A2BE2), Color(0xFF000000)))
        "ring_tier_9" -> Brush.linearGradient(colors = listOf(Color(0xFFFF0000), Color(0xFFFF4500), Color(0xFFFFD700)))
        "ring_tier_10" -> Brush.linearGradient(colors = listOf(Color(0xFFE0FFFF), Color(0xFFFFFFFF)))
        "ring_tier_11" -> Brush.linearGradient(colors = listOf(Color(0xFF8A2BE2), Color(0xFFFF007F), Color(0xFFFFD700)))
        "ring_tier_12" -> Brush.sweepGradient(colors = listOf(Color(0xFFFF0000), Color(0xFFFF7F00), Color(0xFFFFFF00), Color(0xFF00FF00), Color(0xFF0000FF), Color(0xFF8B00FF), Color(0xFFFF0000)))
        "ring_tier_13" -> Brush.linearGradient(colors = listOf(Color(0xFF2E0854), Color(0xFF180B26), Color(0xFF000000)))
        "ring_tier_14" -> Brush.linearGradient(colors = listOf(Color(0xFFFF0000), Color(0xFFFF4500), Color(0xFFFFD700)))
        "ring_tier_15" -> Brush.linearGradient(colors = listOf(Color(0xFF00FFFF), Color(0xFFE0FFFF), Color(0xFFFFFFFF)))
        "none", "" -> null
        else -> Brush.linearGradient(colors = listOf(Color(0xFF8B949E), Color(0xFF8B949E)))
    }

    if (brush != null) {
        Box(
            modifier = modifier
                .border(width = borderWidth, brush = brush, shape = CircleShape)
                .padding(ringPadding),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
