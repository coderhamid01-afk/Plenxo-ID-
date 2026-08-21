package com.example.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.SessionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlenxoIdRevealScreen(
    plenxoId: String,
    onEnterPlenxo: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(1) }
    var isCopied by remember { mutableStateOf(false) }

    val darkBg = Color(0xFF0D1117)
    val cardBg = Color(0xFF161B22)
    val strokeBorder = Color(0xFF30363D)
    val accentBlue = Color(0xFF58A6FF)
    val accentGlow = Color(0xFF1F6FEB)
    val textWhite = Color(0xFFF0F6FC)
    val textMuted = Color(0xFF8B949E)

    var scaleState by remember { mutableStateOf(0.85f) }
    val animatedScale by animateFloatAsState(
        targetValue = scaleState,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "idScale"
    )

    LaunchedEffect(currentStep) {
        scaleState = 1.0f
    }

    Scaffold(
        containerColor = darkBg
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            darkBg,
                            Color(0xFF090D16),
                            darkBg
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Indicator Bar (Step 1 of 2 vs Step 2 of 2)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(40.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (currentStep >= 1) accentBlue else strokeBorder)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(40.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (currentStep >= 2) accentBlue else strokeBorder)
                    )
                }

                // Step Content with Slide Transitions
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(400)) + fadeIn()) togetherWith
                            (slideOutHorizontally(targetOffsetX = { -it / 2 }, animationSpec = tween(400)) + fadeOut())
                        } else {
                            (slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(400)) + fadeIn()) togetherWith
                            (slideOutHorizontally(targetOffsetX = { it / 2 }, animationSpec = tween(400)) + fadeOut())
                        }
                    },
                    label = "onboarding_step_transition",
                    modifier = Modifier.weight(1f, fill = false)
                ) { step ->
                    when (step) {
                        1 -> {
                            // STEP 1: "Welcome to Plenxo"
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Hero Badge
                                Box(
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(CircleShape)
                                        .background(accentGlow.copy(alpha = 0.2f))
                                        .border(1.5.dp, accentBlue.copy(alpha = 0.6f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RocketLaunch,
                                        contentDescription = "Welcome",
                                        tint = accentBlue,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(28.dp))

                                Text(
                                    text = "Welcome to Plenxo",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textWhite,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardBg),
                                    border = BorderStroke(1.dp, strokeBorder)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Text(
                                            text = "Plenxo is built to deliver fast, secure, and seamless real-time communication. Experience high-quality messaging, direct audio/video connections, and custom profile sharing all in one place.",
                                            fontSize = 14.sp,
                                            lineHeight = 22.sp,
                                            color = textWhite.copy(alpha = 0.9f)
                                        )

                                        Text(
                                            text = "Connect with your friends instantly, share moments, and keep your conversations private with our modern communication engine.",
                                            fontSize = 14.sp,
                                            lineHeight = 22.sp,
                                            color = textWhite.copy(alpha = 0.9f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(32.dp))

                                Button(
                                    onClick = { currentStep = 2 },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("onboarding_next_button"),
                                    shape = RoundedCornerShape(26.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentBlue)
                                ) {
                                    Text(
                                        text = "Next",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        2 -> {
                            // STEP 2: "Your Identity & Plenxo ID Display"
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(accentGlow.copy(alpha = 0.2f))
                                        .border(1.5.dp, accentBlue.copy(alpha = 0.6f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Verified Identity",
                                        tint = accentBlue,
                                        modifier = Modifier.size(42.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Text(
                                    text = "Welcome! Here is your Plenxo ID:",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textWhite,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                // HIGH LIGHTING CARD WITH PERMANENT PLENXO ID
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .scale(animatedScale)
                                        .testTag("plenxo_id_reveal_card"),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(containerColor = cardBg),
                                    border = BorderStroke(2.dp, accentBlue)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Surface(
                                            color = accentBlue.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = "PERMANENT PLENXO IDENTITY",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = accentBlue,
                                                letterSpacing = 2.sp,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Text(
                                            text = plenxoId.ifEmpty { "PX-892104" },
                                            fontSize = 36.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            color = textWhite,
                                            letterSpacing = 3.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.testTag("plenxo_id_text")
                                        )

                                        Spacer(modifier = Modifier.height(18.dp))

                                        OutlinedButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("Plenxo ID", plenxoId)
                                                clipboard.setPrimaryClip(clip)
                                                isCopied = true
                                                Toast.makeText(context, "Plenxo ID copied to clipboard!", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(20.dp),
                                            border = BorderStroke(1.dp, strokeBorder),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = Color(0xFF21262D)
                                            ),
                                            modifier = Modifier.testTag("copy_plenxo_id_button")
                                        ) {
                                            Icon(
                                                imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                                contentDescription = "Copy ID",
                                                tint = if (isCopied) Color(0xFF2EA043) else accentBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isCopied) "Copied!" else "Copy ID",
                                                color = if (isCopied) Color(0xFF2EA043) else textWhite,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Identity Notice Text
                                Text(
                                    text = "This is your permanent Plenxo Identity. Please keep it safe. Other users will use this unique ID to search for you, send invites, and connect with you across Plenxo.",
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = textMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                Spacer(modifier = Modifier.height(28.dp))

                                // Action Button: "Get Started"
                                Button(
                                    onClick = {
                                        SessionManager.saveOnboardingCompleted(context, true)
                                        onEnterPlenxo()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("get_started_button"),
                                    shape = RoundedCornerShape(26.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentBlue)
                                ) {
                                    Text(
                                        text = "Get Started",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
