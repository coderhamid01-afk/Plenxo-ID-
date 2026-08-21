package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PlenxoCardSurfaceDark
import com.example.ui.theme.PlenxoError
import com.example.ui.theme.PlenxoWarning
import java.util.Locale

/**
 * Enterprise Lockout Banner Component for Plenxo.
 * Prominently displays a red alert banner with live ticking countdown (HH:MM:SS)
 * and the exact last security violation reason when an account is locked out for 24 hours.
 */
@Composable
fun LockoutBanner(
    lockoutTimeRemainingMs: Long,
    violationReason: String?,
    modifier: Modifier = Modifier
) {
    val totalSeconds = (lockoutTimeRemainingMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val formattedTime = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

    val infiniteTransition = rememberInfiniteTransition(label = "LockoutPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E0A0A)
        ),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                colors = listOf(
                    PlenxoError,
                    Color(0xFFFF6B6B),
                    PlenxoError
                )
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("lockout_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .scale(pulseScale)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PlenxoError.copy(alpha = 0.2f))
                        .border(1.5.dp, PlenxoError, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockClock,
                        contentDescription = "Lockout",
                        tint = PlenxoError,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "ACCOUNT LOCKED (24-HOUR LOCKDOWN)",
                        color = PlenxoError,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                    )
                    Text(
                        text = "Authentication & password resets are suspended",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Ticking Countdown Box
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF0D0505),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = PlenxoError.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(vertical = 10.dp, horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "REMAINING LOCKOUT:",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = formattedTime,
                        color = PlenxoWarning,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 22.sp
                        ),
                        modifier = Modifier.testTag("lockout_timer_text")
                    )
                }
            }

            if (!violationReason.isNullBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = PlenxoWarning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reason: $violationReason",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun String?.isNullBlank(): Boolean = this.isNullOrEmpty() || this.isBlank()
