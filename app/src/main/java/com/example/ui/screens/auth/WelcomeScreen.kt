package com.example.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@Composable
fun WelcomeScreen(
    onNavigateToProfileSetup: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D1117),
                        Color(0xFF161B22),
                        Color(0xFF0D1117)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Image(
                    painter = painterResource(id = R.drawable.ic_plenxo_logo),
                    contentDescription = "Plenxo Logo",
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(24.dp))
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Welcome to Plenxo!",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Your email has been verified. Here is what makes Plenxo powerful:",
                    fontSize = 14.sp,
                    color = Color(0xFF8B949E),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )

                // Feature Card 1
                FeatureCard(
                    icon = Icons.Default.Fingerprint,
                    iconColor = Color(0xFF58A6FF),
                    title = "Permanent Plenxo ID",
                    description = "A unique PX-XXXXXX identifier for effortless, secure connecting with friends."
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Feature Card 2
                FeatureCard(
                    icon = Icons.Default.Lock,
                    iconColor = Color(0xFF10B981),
                    title = "End-to-End Encryption",
                    description = "Your messages and media are strictly private and protected."
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Feature Card 3
                FeatureCard(
                    icon = Icons.Default.AutoAwesome,
                    iconColor = Color(0xFFA855F7),
                    title = "Customizable Profile & Rings",
                    description = "Personalize your bio, dynamic avatars, status fonts, and profile rings."
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Action Button
            Button(
                onClick = onNavigateToProfileSetup,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1F6FEB),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("welcome_next_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Next: Set Up Profile",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Next",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF30363D), iconColor.copy(alpha = 0.5f))
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    color = Color(0xFF8B949E),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
