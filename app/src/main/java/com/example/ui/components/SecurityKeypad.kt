package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PlenxoCardSurfaceDark
import com.example.ui.theme.PlenxoCyan
import com.example.ui.theme.PlenxoElectricViolet

/**
 * Display row for PIN dots with animated fill states.
 */
@Composable
fun SecurityPinDisplay(
    pinValue: String,
    maxLength: Int = 6,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until maxLength) {
            val isFilled = i < pinValue.length
            val dotColor by animateColorAsState(
                targetValue = when {
                    isError -> Color(0xFFFF4D4D)
                    isFilled -> PlenxoCyan
                    else -> Color.White.copy(alpha = 0.2f)
                },
                animationSpec = tween(durationMillis = 200),
                label = "dotColor"
            )

            val scale by animateFloatAsState(
                targetValue = if (isFilled) 1.25f else 1.0f,
                animationSpec = tween(durationMillis = 150),
                label = "dotScale"
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(18.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(if (isFilled) dotColor else Color.Transparent)
                    .border(
                        width = 2.dp,
                        color = if (isFilled) dotColor else Color.White.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * Secure Numeric Keypad component for Master PIN verification.
 */
@Composable
fun SecurityKeypad(
    pinValue: String,
    maxLength: Int = 6,
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit,
    onConfirmClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val keypadGrid = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("CLEAR", "0", "DEL")
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        keypadGrid.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    KeypadButton(
                        key = key,
                        enabled = enabled,
                        onClick = {
                            if (!enabled) return@KeypadButton
                            when (key) {
                                "DEL" -> onBackspaceClick()
                                "CLEAR" -> onClearClick()
                                else -> {
                                    if (pinValue.length < maxLength) {
                                        onDigitClick(key)
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        if (onConfirmClick != null && pinValue.length == maxLength) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onConfirmClick,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PlenxoElectricViolet,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp)
                    .testTag("keypad_confirm_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Verify Master PIN",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun KeypadButton(
    key: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "keypadPressScale"
    )

    val isActionKey = key == "DEL" || key == "CLEAR"

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .scale(buttonScale)
            .clip(CircleShape)
            .background(
                brush = if (isPressed) {
                    Brush.linearGradient(
                        listOf(
                            PlenxoElectricViolet.copy(alpha = 0.6f),
                            PlenxoCyan.copy(alpha = 0.6f)
                        )
                    )
                } else if (isActionKey) {
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        listOf(
                            PlenxoCardSurfaceDark,
                            Color(0xFF1A2234)
                        )
                    )
                },
                shape = CircleShape
            )
            .border(
                width = 1.dp,
                color = if (isPressed) PlenxoCyan else Color.White.copy(alpha = 0.15f),
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .testTag("keypad_button_$key")
    ) {
        when (key) {
            "DEL" -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                    tint = if (enabled) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp)
                )
            }
            "CLEAR" -> {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear",
                    tint = if (enabled) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp)
                )
            }
            else -> {
                Text(
                    text = key,
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}
