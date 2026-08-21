package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.PlenxoColors

@Composable
fun PlenxoAdvancedLoader(
    modifier: Modifier = Modifier,
    size: Dp = 90.dp,
    statusText: String = "Connecting securely..."
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loader")

    // 1. Rotation for glowing gradient outer ring
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // 2. Pulsing scale for logo aura
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraScale"
    )

    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraAlpha"
    )

    Column(
        modifier = modifier.testTag("plenxo_advanced_loader"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            // Glowing Pulsing Aura Background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(auraScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                PlenxoColors.Primary.copy(alpha = auraAlpha),
                                PlenxoColors.Secondary.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Dynamic Rotating Gradient Ring Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotation)
            ) {
                val strokeWidth = 5.dp.toPx()
                val sweepBrush = Brush.sweepGradient(
                    colors = listOf(
                        PlenxoColors.Primary,
                        PlenxoColors.Secondary,
                        Color.Transparent,
                        PlenxoColors.Primary
                    )
                )
                drawArc(
                    brush = sweepBrush,
                    startAngle = 0f,
                    sweepAngle = 280f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Center Logo container
            Surface(
                shape = CircleShape,
                color = Color(0xFF131824),
                shadowElevation = 8.dp,
                modifier = Modifier.size(size * 0.65f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_plenxo_logo),
                        contentDescription = "Plenxo Logo",
                        modifier = Modifier
                            .fillMaxSize(0.7f)
                            .clip(CircleShape)
                    )
                }
            }
        }

        if (statusText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = statusText,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtle Bouncing Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val dotOffset by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = -8f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(400, delayMillis = index * 120, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dotOffset_$index"
                    )

                    Box(
                        modifier = Modifier
                            .offset(y = dotOffset.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (index == 1) PlenxoColors.Secondary else PlenxoColors.Primary)
                    )
                }
            }
        }
    }
}

@Composable
fun PlenxoLoaderOverlay(
    isLoading: Boolean,
    statusText: String = "Processing...",
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()

        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .testTag("loader_overlay"),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1B2232),
                    tonalElevation = 12.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Brush.linearGradient(listOf(PlenxoColors.Primary.copy(alpha = 0.5f), PlenxoColors.Secondary.copy(alpha = 0.3f)))
                    ),
                    modifier = Modifier.padding(32.dp)
                ) {
                    PlenxoAdvancedLoader(
                        modifier = Modifier.padding(28.dp),
                        statusText = statusText
                    )
                }
            }
        }
    }
}
