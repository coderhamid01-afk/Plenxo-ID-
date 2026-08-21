package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.example.ui.theme.PlenxoColors
import com.example.ui.theme.PlenxoTypography
import com.example.viewmodel.PlenxoViewModel
import java.util.Random

private tailrec fun findActivity(context: android.content.Context?): android.app.Activity? {
    return when (context) {
        is android.app.Activity -> context
        is android.content.ContextWrapper -> findActivity(context.baseContext)
        else -> null
    }
}

@Composable
fun ObfuscatedCaptchaCanvas(
    text: String,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(
        color = Color.White,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
            .border(1.dp, Color(0xFF333333), shape = RoundedCornerShape(8.dp))
    ) {
        val width = size.width
        val height = size.height

        // 1. Draw noise background dots
        val random = Random(text.hashCode().toLong())
        for (i in 0 until 150) {
            val dotX = random.nextFloat() * width
            val dotY = random.nextFloat() * height
            val dotRadius = random.nextFloat() * 3f + 1f
            drawCircle(
                color = Color(0xFF58A6FF).copy(alpha = random.nextFloat() * 0.4f + 0.1f),
                radius = dotRadius,
                center = Offset(dotX, dotY)
            )
        }

        // 2. Draw characters individually with random offsets and rotations
        val charWidth = width / (text.length + 1)
        text.forEachIndexed { index, char ->
            val charStr = char.toString()
            val textLayoutResult = textMeasurer.measure(charStr, textStyle)
            
            val offsetX = charWidth * (index + 0.5f) + (random.nextFloat() * 12f - 6f)
            val offsetY = (height - textLayoutResult.size.height) / 2f + (random.nextFloat() * 16f - 8f)
            val rotationAngle = random.nextFloat() * 50f - 25f
            val scale = random.nextFloat() * 0.3f + 0.85f

            withTransform({
                translate(left = offsetX, top = offsetY)
                rotate(degrees = rotationAngle, pivot = Offset(textLayoutResult.size.width / 2f, textLayoutResult.size.height / 2f))
                scale(scaleX = scale, scaleY = scale, pivot = Offset(textLayoutResult.size.width / 2f, textLayoutResult.size.height / 2f))
            }) {
                drawText(textLayoutResult)
            }
        }

        // 3. Draw intersecting noise lines over the text
        for (i in 0 until 6) {
            val startX = random.nextFloat() * (width / 3)
            val startY = random.nextFloat() * height
            val endX = width - (random.nextFloat() * (width / 3))
            val endY = random.nextFloat() * height
            
            drawLine(
                color = Color(0xFF58A6FF).copy(alpha = 0.6f),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = random.nextFloat() * 2f + 1.5f
            )
        }

        // 4. Draw curve line to disrupt automated segmentation
        val path = androidx.compose.ui.graphics.Path()
        path.moveTo(0f, height / 2f)
        path.quadraticTo(
            width / 3f, height * 0.2f,
            width * 0.6f, height * 0.8f
        )
        path.lineTo(width, height / 2f)
        drawPath(
            path = path,
            color = Color(0xFFFF7B72).copy(alpha = 0.5f),
            style = Stroke(width = 2.5f)
        )
    }
}

@Composable
fun CaptchaDialogOverlay(
    viewModel: PlenxoViewModel,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        onDismiss()
    }
}
