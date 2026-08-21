package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.CaptchaStage
import com.example.ui.theme.*
import com.example.viewmodel.PlenxoViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Sleek compact trigger row placed above the Terms & Conditions checkbox.
 * Replaces bulky blocky card with a clean, single-line horizontal Row (height 50.dp, rounded corners 12.dp).
 * UNVERIFIED: Shield icon + "Security Check" (14.sp) on left, clean OutlinedButton "Verify" (maxLines=1) on right.
 * VERIFIED: CheckCircle icon + "Security Check Passed" on left, "Passed" badge with green glow on right.
 */
@Composable
fun CaptchaTriggerRow(
    isVerified: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val successGreen = Color(0xFF34D399)
    val accentCyan = Color(0xFF38BDF8)
    val accentPurple = Color(0xFF818CF8)
    val cardBg = Color(0xFF131825)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                brush = if (isVerified) {
                    Brush.linearGradient(listOf(successGreen.copy(alpha = 0.8f), successGreen.copy(alpha = 0.3f)))
                } else {
                    Brush.linearGradient(listOf(accentPurple.copy(alpha = 0.6f), accentCyan.copy(alpha = 0.4f)))
                },
                shape = RoundedCornerShape(12.dp)
            )
            .then(
                if (!isVerified) Modifier.clickable { onClick() } else Modifier
            )
            .testTag("captcha_trigger_row"),
        color = cardBg,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left side: Shield Icon + Text "Security Check" (Single line, 14.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Icon(
                    imageVector = if (isVerified) Icons.Default.CheckCircle else Icons.Default.Shield,
                    contentDescription = "Security Check",
                    tint = if (isVerified) successGreen else accentCyan,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (isVerified) "Security Check Passed" else "Security Check",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isVerified) successGreen else Color(0xFFF1F5F9),
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Right side: OutlinedButton with "Verify" or Verified status badge
            if (isVerified) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = successGreen.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, successGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = successGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Passed",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = successGreen,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Brush.linearGradient(listOf(accentPurple, accentCyan))
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = accentPurple.copy(alpha = 0.12f),
                        contentColor = accentCyan
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("captcha_trigger_verify_btn")
                ) {
                    Text(
                        text = "Verify",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentCyan,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/**
 * Modal Popup Dialog for Dual-Stage CAPTCHA verification in Plenxo.
 */
@Composable
fun DualStageCaptchaDialog(
    isOpen: Boolean,
    onDismissRequest: () -> Unit,
    viewModel: PlenxoViewModel
) {
    if (!isOpen) return

    val stage by viewModel.captchaStage.collectAsState()
    val textCode by viewModel.textCaptchaCode.collectAsState()
    val textInput by viewModel.textCaptchaInput.collectAsState()

    DualStageCaptchaDialog(
        isOpen = isOpen,
        onDismissRequest = onDismissRequest,
        captchaStage = stage,
        textCaptchaCode = textCode,
        textCaptchaInput = textInput,
        onTextInputChange = { viewModel.textCaptchaInput.value = it },
        onVerifyText = { viewModel.verifyStage1Text() },
        onRefreshCaptcha = { viewModel.resetCaptcha() },
        onVerifySlider = { isAligned -> viewModel.verifyStage2Slider(isAligned) },
        onDone = {
            onDismissRequest()
        }
    )
}

/**
 * Presentation composable for the Dual-Stage CAPTCHA Modal Dialog.
 */
@Composable
fun DualStageCaptchaDialog(
    isOpen: Boolean,
    onDismissRequest: () -> Unit,
    captchaStage: CaptchaStage,
    textCaptchaCode: String,
    textCaptchaInput: String,
    onTextInputChange: (String) -> Unit,
    onVerifyText: () -> Unit,
    onRefreshCaptcha: () -> Unit,
    onVerifySlider: (Boolean) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val darkCardBg = Color(0xFF131825)
    val darkSurfaceBg = Color(0xFF0B0F19)
    val strokeBorder = Color(0xFF263045)
    val accentCyan = Color(0xFF38BDF8)
    val accentPurple = Color(0xFF818CF8)
    val successGreen = Color(0xFF34D399)
    val textMuted = Color(0xFF94A3B8)
    val textLight = Color(0xFFF1F5F9)

    var stage1Error by remember { mutableStateOf<String?>(null) }
    var targetFraction by remember(textCaptchaCode) {
        mutableFloatStateOf(0.82f + Random.nextFloat() * 0.10f)
    }

    Dialog(
        onDismissRequest = {
            if (captchaStage == CaptchaStage.FULLY_VERIFIED) {
                onDone()
            } else {
                onDismissRequest()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = captchaStage != CaptchaStage.STAGE_1_CLEARED,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(24.dp))
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        if (captchaStage == CaptchaStage.FULLY_VERIFIED) {
                            listOf(successGreen.copy(alpha = 0.9f), successGreen.copy(alpha = 0.4f))
                        } else {
                            listOf(accentPurple.copy(alpha = 0.8f), accentCyan.copy(alpha = 0.5f))
                        }
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .testTag("captcha_dialog_container"),
            color = darkCardBg,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Dialog Header: Title, Subtext & Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (captchaStage == CaptchaStage.FULLY_VERIFIED) successGreen.copy(alpha = 0.16f)
                                    else accentCyan.copy(alpha = 0.16f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (captchaStage == CaptchaStage.FULLY_VERIFIED) Icons.Default.VerifiedUser else Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (captchaStage == CaptchaStage.FULLY_VERIFIED) successGreen else accentCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Security Check",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = textLight
                            )
                            Text(
                                text = "Complete both steps to continue",
                                fontSize = 12.sp,
                                color = textMuted
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (captchaStage == CaptchaStage.FULLY_VERIFIED) onDone()
                            else onDismissRequest()
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("captcha_dialog_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // STAGE 1: Distorted Text CAPTCHA
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(darkSurfaceBg)
                        .border(1.dp, strokeBorder.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (captchaStage != CaptchaStage.LOCKED) successGreen.copy(alpha = 0.2f)
                                        else accentCyan.copy(alpha = 0.2f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (captchaStage != CaptchaStage.LOCKED) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = successGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                } else {
                                    Text(
                                        text = "1",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accentCyan
                                    )
                                }
                            }
                            Text(
                                text = "Step 1: Solve Text Code",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (captchaStage != CaptchaStage.LOCKED) successGreen else textLight
                            )
                        }

                        if (captchaStage == CaptchaStage.LOCKED) {
                            IconButton(
                                onClick = {
                                    stage1Error = null
                                    onRefreshCaptcha()
                                },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("captcha_stage1_refresh")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Regenerate code",
                                    tint = accentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Distorted Noise Canvas
                    DistortedCaptchaCanvas(
                        code = textCaptchaCode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .testTag("captcha_stage1_canvas")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (captchaStage == CaptchaStage.LOCKED) {
                        // Input Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = textCaptchaInput,
                                onValueChange = {
                                    stage1Error = null
                                    onTextInputChange(it.filter { char -> char.isLetter() }.take(6).uppercase())
                                },
                                placeholder = {
                                    Text("6-letter code", fontSize = 12.sp, color = textMuted)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (textCaptchaInput.trim().isNotEmpty()) {
                                            val matched = textCaptchaInput.trim().equals(textCaptchaCode.trim(), ignoreCase = true)
                                            if (matched) {
                                                stage1Error = null
                                                onVerifyText()
                                            } else {
                                                stage1Error = "Incorrect code. Regenerated a new code."
                                                onVerifyText()
                                            }
                                        }
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentCyan,
                                    unfocusedBorderColor = strokeBorder,
                                    focusedContainerColor = darkCardBg,
                                    unfocusedContainerColor = darkCardBg,
                                    focusedTextColor = textLight,
                                    unfocusedTextColor = textLight
                                ),
                                textStyle = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 2.sp
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("captcha_stage1_input")
                            )

                            Button(
                                onClick = {
                                    if (textCaptchaInput.trim().isNotEmpty()) {
                                        val matched = textCaptchaInput.trim().equals(textCaptchaCode.trim(), ignoreCase = true)
                                        if (matched) {
                                            stage1Error = null
                                            onVerifyText()
                                        } else {
                                            stage1Error = "Incorrect code. Regenerated a new code."
                                            onVerifyText()
                                        }
                                    } else {
                                        stage1Error = "Please enter the code above."
                                    }
                                },
                                enabled = textCaptchaInput.isNotBlank(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentCyan,
                                    disabledContainerColor = accentCyan.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("captcha_stage1_verify_btn")
                            ) {
                                Text(
                                    "Verify",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }

                        AnimatedVisibility(visible = stage1Error != null) {
                            Text(
                                text = stage1Error.orEmpty(),
                                color = Color(0xFFF87171),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    } else {
                        // Stage 1 Cleared Banner
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = successGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Stage 1 Cleared Successfully",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = successGreen
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ==========================================
                // STAGE 2: Interactive Slider Puzzle
                // ==========================================
                val isStage2Unlocked = captchaStage != CaptchaStage.LOCKED
                val isStage2Completed = captchaStage == CaptchaStage.FULLY_VERIFIED

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(darkSurfaceBg)
                        .border(
                            1.dp,
                            if (isStage2Completed) successGreen.copy(alpha = 0.5f) else strokeBorder.copy(alpha = 0.7f),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isStage2Completed -> successGreen.copy(alpha = 0.2f)
                                            isStage2Unlocked -> accentPurple.copy(alpha = 0.2f)
                                            else -> Color(0xFF334155).copy(alpha = 0.3f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isStage2Completed) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = successGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                } else if (!isStage2Unlocked) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = textMuted,
                                        modifier = Modifier.size(11.dp)
                                    )
                                } else {
                                    Text(
                                        text = "2",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accentPurple
                                    )
                                }
                            }

                            Text(
                                text = "Step 2: Drag Slider to Target",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    isStage2Completed -> successGreen
                                    isStage2Unlocked -> textLight
                                    else -> textMuted
                                }
                            )
                        }

                        Text(
                            text = when {
                                isStage2Completed -> "100% Aligned"
                                isStage2Unlocked -> "Align Target"
                                else -> "Locked"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = when {
                                isStage2Completed -> successGreen
                                isStage2Unlocked -> accentPurple
                                else -> textMuted
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Interactive Drag Slider Track
                    InteractivePuzzleSlider(
                        isUnlocked = isStage2Unlocked,
                        isCompleted = isStage2Completed,
                        targetFraction = targetFraction,
                        onAligned = { onVerifySlider(true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("captcha_stage2_slider_track")
                    )
                }

                // ==========================================
                // Completion / Continue Action Button
                // ==========================================
                Spacer(modifier = Modifier.height(18.dp))

                AnimatedVisibility(
                    visible = captchaStage == CaptchaStage.FULLY_VERIFIED,
                    enter = fadeIn() + expandVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("captcha_fully_verified_badge"),
                            shape = RoundedCornerShape(12.dp),
                            color = successGreen.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, successGreen.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = successGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Security Verification Complete!",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = successGreen
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = onDone,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("captcha_done_continue_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = successGreen,
                                contentColor = Color(0xFF0F172A)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Continue",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
