package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptchaComponent(
    modifier: Modifier = Modifier,
    onCaptchaVerifiedChanged: (Boolean) -> Unit
) {
    var captchaText by remember { mutableStateOf(generateCaptchaCode()) }
    var userEntry by remember { mutableStateOf("") }
    val isVerified = userEntry.trim().equals(captchaText.trim(), ignoreCase = true)

    LaunchedEffect(isVerified) {
        onCaptchaVerifiedChanged(isVerified)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF131927))
            .border(1.dp, Color(0xFF2B354D), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Captcha Security",
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Security Verification",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (isVerified) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Verified",
                        color = Color(0xFF10B981),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Styled Captcha Visual Box
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Background noise lines
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val rnd = Random(captchaText.hashCode())
                    for (i in 0..5) {
                        drawLine(
                            color = Color(0x3338BDF8),
                            start = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height),
                            end = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height),
                            strokeWidth = 2f
                        )
                    }
                }

                Text(
                    text = captchaText,
                    color = Color(0xFF38BDF8),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 6.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            IconButton(
                onClick = {
                    captchaText = generateCaptchaCode()
                    userEntry = ""
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                    .testTag("refresh_captcha_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Captcha",
                    tint = Color(0xFF94A3B8)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = userEntry,
            onValueChange = { userEntry = it },
            placeholder = { Text("Enter 4-character code above", color = Color(0xFF64748B), fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("captcha_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isVerified) Color(0xFF10B981) else Color(0xFF60A5FA),
                unfocusedBorderColor = Color(0xFF334155),
                focusedContainerColor = Color(0xFF0F172A),
                unfocusedContainerColor = Color(0xFF0F172A),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

private fun generateCaptchaCode(): String {
    val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    return (1..4)
        .map { chars.random() }
        .joinToString("")
}
