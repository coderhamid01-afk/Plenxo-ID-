package com.example.ui

import com.example.R

import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PlenxoColors
import com.example.ui.theme.PlenxoSpacing
import com.example.ui.theme.PlenxoTypography

@Composable
fun EmailVerificationWaitingScreen(
    email: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)) // Deep Space Black background
    ) {
        // Subtle background gradient for premium feel
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x228A2BE2), Color(0x000A0A0A)),
                        radius = 800f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row with Back Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(id = R.string.str_email_verification),
                    style = PlenxoTypography.Title.copy(color = Color.White, fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(60.dp))

            // Glassmorphism Card Info
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)),
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(Color(0x338A2BE2), Color(0x3300FFFF))
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Accent Icon
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color(0x1A8A2BE2), RoundedCornerShape(20.dp))
                            .border(1.dp, Color(0x4D8A2BE2), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MarkEmailUnread,
                            contentDescription = "Check Email",
                            tint = Color(0xFF00FFFF), // Cyan Accent
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(stringResource(id = R.string.str_we_ve_sent_a_verification),
                        style = PlenxoTypography.Title.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(stringResource(id = R.string.str_a_secure_redirection_link_has),
                        style = PlenxoTypography.Body.copy(color = Color.Gray, fontSize = 14.sp),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = email,
                        style = PlenxoTypography.Body.copy(
                            color = Color(0xFF00FFFF), // Cyan color highlighting the email
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(stringResource(id = R.string.str_click_the_link_in_the),
                        style = PlenxoTypography.Body.copy(
                            color = Color.LightGray,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Launch Email App button
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_APP_EMAIL)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No default email app found.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A2BE2)), // Electric Violet
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Open Email",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = R.string.str_open_mail_client),
                            color = Color.White,
                            style = PlenxoTypography.Body.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}
