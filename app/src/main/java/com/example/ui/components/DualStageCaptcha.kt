package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
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
import com.example.model.CaptchaStage
import com.example.viewmodel.PlenxoViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * High-level Composable binding DualStageCaptcha directly to [PlenxoViewModel].
 */
@Composable
fun DualStageCaptcha(
    viewModel: PlenxoViewModel,
    modifier: Modifier = Modifier
) {
    val stage by viewModel.captchaStage.collectAsState()
    val textCode by viewModel.textCaptchaCode.collectAsState()
    val textInput by viewModel.textCaptchaInput.collectAsState()

    DualStageCaptcha(
        captchaStage = stage,
        textCaptchaCode = textCode,
        textCaptchaInput = textInput,
        onTextInputChange = { viewModel.textCaptchaInput.value = it },
        onVerifyText = { viewModel.verifyStage1Text() },
        onRefreshCaptcha = { viewModel.resetCaptcha() },
        onVerifySlider = { isAligned -> viewModel.verifyStage2Slider(isAligned) },
        modifier = modifier
    )
}

/**
 * Production-ready Dual-Stage CAPTCHA component:
 * 1. Distorted Noise Canvas with alphanumeric rotation & noise lines.
 * 2. Precision Drag-Slider puzzle alignment to unlock final verification.
 */
@Composable
fun DualStageCaptcha(
    captchaStage: CaptchaStage,
    textCaptchaCode: String,
    textCaptchaInput: String,
    onTextInputChange: (String) -> Unit,
    onVerifyText: () -> Unit,
    onRefreshCaptcha: () -> Unit,
    onVerifySlider: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val darkCardBg = Color(0xFF161B26)
    val darkSurfaceBg = Color(0xFF0F131D)
    val strokeBorder = Color(0xFF2E384D)
    val accentCyan = Color(0xFF38BDF8)
    val accentPurple = Color(0xFF818CF8)
    val successGreen = Color(0xFF34D399)
    val textMuted = Color(0xFF94A3B8)
    val textLight = Color(0xFFF1F5F9)

    var stage1Error by remember { mutableStateOf<String?>(null) }
    var targetFraction by remember(textCaptchaCode) {
        // Target box placed between 80% and 92% of the slider track
        mutableFloatStateOf(0.82f + Random.nextFloat() * 0.10f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    if (captchaStage == CaptchaStage.FULLY_VERIFIED) {
                        listOf(successGreen.copy(alpha = 0.8f), successGreen.copy(alpha = 0.3f))
                    } else {
                        listOf(strokeBorder, strokeBorder.copy(alpha = 0.5f))
                    }
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("dual_stage_captcha_container"),
        color = darkCardBg,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title & Global Verification Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (captchaStage == CaptchaStage.FULLY_VERIFIED) successGreen.copy(alpha = 0.15f)
                                else accentCyan.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (captchaStage == CaptchaStage.FULLY_VERIFIED) Icons.Default.VerifiedUser else Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (captchaStage == CaptchaStage.FULLY_VERIFIED) successGreen else accentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Security Verification",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textLight
                        )
                        Text(
                            text = "Dual-Stage Anti-Bot Defense",
                            fontSize = 11.sp,
                            color = textMuted
                        )
                    }
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (captchaStage) {
                        CaptchaStage.FULLY_VERIFIED -> successGreen.copy(alpha = 0.15f)
                        CaptchaStage.STAGE_1_CLEARED -> accentPurple.copy(alpha = 0.15f)
                        CaptchaStage.LOCKED -> Color(0xFF334155).copy(alpha = 0.5f)
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when (captchaStage) {
                            CaptchaStage.FULLY_VERIFIED -> successGreen.copy(alpha = 0.4f)
                            CaptchaStage.STAGE_1_CLEARED -> accentPurple.copy(alpha = 0.4f)
                            CaptchaStage.LOCKED -> strokeBorder
                        }
                    )
                ) {
                    Text(
                        text = when (captchaStage) {
                            CaptchaStage.FULLY_VERIFIED -> "VERIFIED"
                            CaptchaStage.STAGE_1_CLEARED -> "STAGE 2 READY"
                            CaptchaStage.LOCKED -> "LOCKED"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (captchaStage) {
                            CaptchaStage.FULLY_VERIFIED -> successGreen
                            CaptchaStage.STAGE_1_CLEARED -> accentPurple
                            CaptchaStage.LOCKED -> textMuted
                        },
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("captcha_status_badge")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

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
                        .height(64.dp)
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
                                Text("Enter 6-letter code", fontSize = 12.sp, color = textMuted)
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
                                            stage1Error = "Incorrect code. A new code was generated."
                                            onVerifyText() // triggers reset & code regeneration
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
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
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
                                        stage1Error = "Incorrect code. A new code was generated."
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
                                .height(50.dp)
                                .testTag("captcha_stage1_verify_btn")
                        ) {
                            Text(
                                "Verify",
                                fontSize = 13.sp,
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
                        .height(52.dp)
                        .testTag("captcha_stage2_slider_track")
                )
            }

            // Verification Success Banner
            AnimatedVisibility(visible = captchaStage == CaptchaStage.FULLY_VERIFIED) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("captcha_fully_verified_badge"),
                    shape = RoundedCornerShape(10.dp),
                    color = successGreen.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, successGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = successGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "All Security Checks Cleared. You may proceed.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = successGreen
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compose Canvas rendering 6-character random alphanumeric text with:
 * - Noise dots
 * - Random line strikes & sine wave curve
 * - Individual character translation, rotation (-25° to +25°), and scaling.
 */
@Composable
fun DistortedCaptchaCanvas(
    code: String,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(
        color = Color(0xFFF8FAFC),
        fontSize = 26.sp,
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily.Monospace
    )

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0B0E14))
            .border(1.dp, Color(0xFF263045), RoundedCornerShape(10.dp))
    ) {
        val w = size.width
        val h = size.height

        // 1. Draw pseudo-random noise dots
        val seedRandom = Random(code.hashCode().toLong())
        for (i in 0 until 120) {
            val dotX = seedRandom.nextFloat() * w
            val dotY = seedRandom.nextFloat() * h
            val radius = seedRandom.nextFloat() * 2.5f + 0.8f
            drawCircle(
                color = Color(0xFF38BDF8).copy(alpha = seedRandom.nextFloat() * 0.35f + 0.1f),
                radius = radius,
                center = Offset(dotX, dotY)
            )
        }

        // 2. Draw character by character with rotation (-25° to +25°) and offset
        val charSlotWidth = w / (code.length + 1)
        code.forEachIndexed { index, char ->
            val charStr = char.toString()
            val textLayoutResult = textMeasurer.measure(charStr, textStyle)

            val rotationAngle = seedRandom.nextFloat() * 50f - 25f // -25° to +25°
            val scale = seedRandom.nextFloat() * 0.25f + 0.9f
            val charCenterX = charSlotWidth * (index + 1) + (seedRandom.nextFloat() * 10f - 5f)
            val charCenterY = h / 2f + (seedRandom.nextFloat() * 12f - 6f)

            val textOffsetX = charCenterX - (textLayoutResult.size.width / 2f)
            val textOffsetY = charCenterY - (textLayoutResult.size.height / 2f)

            withTransform({
                translate(left = textOffsetX, top = textOffsetY)
                rotate(
                    degrees = rotationAngle,
                    pivot = Offset(textLayoutResult.size.width / 2f, textLayoutResult.size.height / 2f)
                )
                scale(
                    scaleX = scale,
                    scaleY = scale,
                    pivot = Offset(textLayoutResult.size.width / 2f, textLayoutResult.size.height / 2f)
                )
            }) {
                drawText(textLayoutResult)
            }
        }

        // 3. Draw intersecting random noise lines
        for (i in 0 until 5) {
            val startX = seedRandom.nextFloat() * (w * 0.35f)
            val startY = seedRandom.nextFloat() * h
            val endX = w - (seedRandom.nextFloat() * (w * 0.35f))
            val endY = seedRandom.nextFloat() * h

            drawLine(
                color = Color(0xFF818CF8).copy(alpha = 0.55f),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = seedRandom.nextFloat() * 2f + 1.2f
            )
        }

        // 4. Draw anti-OCR disruptive bezier curve
        val path = Path()
        path.moveTo(0f, h * 0.5f)
        path.quadraticTo(
            w * 0.3f, h * 0.15f,
            w * 0.65f, h * 0.85f
        )
        path.lineTo(w, h * 0.45f)
        drawPath(
            path = path,
            color = Color(0xFFF472B6).copy(alpha = 0.45f),
            style = Stroke(width = 2.5f)
        )
    }
}

/**
 * Interactive puzzle slider track with draggable thumb and target zone.
 */
@Composable
fun InteractivePuzzleSlider(
    isUnlocked: Boolean,
    isCompleted: Boolean,
    targetFraction: Float,
    onAligned: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val thumbWidthDp = 48.dp
    val thumbWidthPx = with(density) { thumbWidthDp.toPx() }

    var sliderWidthPx by remember { mutableFloatStateOf(0f) }
    var rawThumbOffsetPx by remember { mutableFloatStateOf(0f) }

    val animatedThumbOffsetPx by animateFloatAsState(
        targetValue = when {
            isCompleted -> {
                val maxOffset = (sliderWidthPx - thumbWidthPx).coerceAtLeast(0f)
                maxOffset * targetFraction
            }
            else -> rawThumbOffsetPx
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "slider_thumb_offset"
    )

    val trackBg = Color(0xFF0B0E14)
    val strokeBorder = Color(0xFF2E384D)
    val accentPurple = Color(0xFF818CF8)
    val successGreen = Color(0xFF34D399)

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(trackBg)
            .border(1.dp, strokeBorder, RoundedCornerShape(12.dp))
    ) {
        sliderWidthPx = constraints.maxWidth.toFloat()
        val maxOffsetPx = (sliderWidthPx - thumbWidthPx).coerceAtLeast(0f)

        // Draw track decorations and target box
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Target box geometry
            val targetCenterPx = maxOffsetPx * targetFraction
            val targetBoxWidth = thumbWidthPx
            val targetBoxHeight = h - 8.dp.toPx()
            val targetBoxTop = 4.dp.toPx()

            // Draw dashed target slot
            drawRoundRect(
                color = if (isCompleted) successGreen.copy(alpha = 0.35f) else accentPurple.copy(alpha = 0.3f),
                topLeft = Offset(targetCenterPx, targetBoxTop),
                size = Size(targetBoxWidth, targetBoxHeight),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )
            )

            // Fill target background subtly
            drawRoundRect(
                color = if (isCompleted) successGreen.copy(alpha = 0.2f) else accentPurple.copy(alpha = 0.08f),
                topLeft = Offset(targetCenterPx, targetBoxTop),
                size = Size(targetBoxWidth, targetBoxHeight),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )
        }

        // Draggable Thumb / Slider Handle
        val currentOffset = if (isCompleted) {
            maxOffsetPx * targetFraction
        } else {
            animatedThumbOffsetPx
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(currentOffset.roundToInt(), 0) }
                .fillMaxHeight()
                .width(thumbWidthDp)
                .padding(3.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    when {
                        isCompleted -> Brush.linearGradient(listOf(successGreen, Color(0xFF059669)))
                        isUnlocked -> Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF4F46E5)))
                        else -> Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
                    }
                )
                .shadow(
                    elevation = if (isUnlocked) 6.dp else 0.dp,
                    shape = RoundedCornerShape(10.dp)
                )
                .then(
                    if (isUnlocked && !isCompleted) {
                        Modifier.pointerInput(sliderWidthPx, targetFraction) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val currentProgress = if (maxOffsetPx > 0f) rawThumbOffsetPx / maxOffsetPx else 0f
                                    val tolerance = 0.05f // ±5% tolerance
                                    if (abs(currentProgress - targetFraction) <= tolerance) {
                                        rawThumbOffsetPx = maxOffsetPx * targetFraction
                                        onAligned()
                                    } else {
                                        // Snap back on miss
                                        rawThumbOffsetPx = 0f
                                    }
                                },
                                onDragCancel = {
                                    rawThumbOffsetPx = 0f
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    val newOffset = (rawThumbOffsetPx + dragAmount).coerceIn(0f, maxOffsetPx)
                                    rawThumbOffsetPx = newOffset
                                    val currentProgress = if (maxOffsetPx > 0f) newOffset / maxOffsetPx else 0f
                                    if (abs(currentProgress - targetFraction) <= 0.05f) {
                                        onAligned()
                                    }
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .testTag("captcha_stage2_slider_thumb"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    isCompleted -> Icons.Default.Check
                    isUnlocked -> Icons.Default.Extension
                    else -> Icons.Default.Lock
                },
                contentDescription = if (isCompleted) "Aligned" else "Drag handle",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Instructional placeholder text when thumb is at start
        if (!isCompleted && animatedThumbOffsetPx < 40f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 56.dp, end = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (isUnlocked) "Slide right to fit puzzle slot ➔" else "Complete Step 1 to unlock slider",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isUnlocked) Color(0xFFCBD5E1) else Color(0xFF64748B)
                )
            }
        }
    }
}
