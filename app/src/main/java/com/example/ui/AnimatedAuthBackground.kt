package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun AnimatedAuthBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        AuthBackgroundAnimationCanvas(modifier = Modifier.matchParentSize())
        content()
    }
}

@Composable
fun AuthBackgroundAnimationCanvas(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "AuthBackgroundTransition")

    val animationPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "animationPhase"
    )

    // Classic deep galaxy colors: rich blacks and deep blues
    val colorBackground = Color(0xFF04060F)
    val colorGlow1 = Color(0xFF0A1931)
    val colorGlow2 = Color(0xFF150E28)
    val colorAccentBlue = Color(0xFF1976D2).copy(alpha = 0.15f)
    
    // Generate static stars once
    val stars = remember {
        List(70) {
            Star(
                xOffset = Random.nextFloat(),
                yOffset = Random.nextFloat(),
                size = Random.nextFloat() * 3f + 1f,
                alphaBase = Random.nextFloat() * 0.5f + 0.2f
            )
        }
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width == 0f || height == 0f) return@Canvas

        // 1. Deep space base
        drawRect(color = colorBackground)

        // 2. Slow moving nebulous gradients
        val phaseSin = sin(animationPhase)
        val phaseCos = cos(animationPhase)

        val brush1 = Brush.radialGradient(
            colors = listOf(colorGlow1, Color.Transparent),
            center = Offset(width * (0.3f + 0.1f * phaseCos), height * (0.4f + 0.1f * phaseSin)),
            radius = width * 0.8f
        )
        drawRect(brush = brush1)

        val brush2 = Brush.radialGradient(
            colors = listOf(colorGlow2, Color.Transparent),
            center = Offset(width * (0.7f - 0.1f * phaseSin), height * (0.7f + 0.1f * phaseCos)),
            radius = width * 0.9f
        )
        drawRect(brush = brush2)
        
        val brush3 = Brush.radialGradient(
            colors = listOf(colorAccentBlue, Color.Transparent),
            center = Offset(width * (0.5f + 0.2f * phaseSin), height * (0.2f - 0.1f * phaseCos)),
            radius = width * 0.7f
        )
        drawRect(brush = brush3)

        // 3. Twinkling stars
        stars.forEachIndexed { index, star ->
            val twinkle = (sin(animationPhase * (3f + index % 5) + index) + 1f) / 2f
            val currentAlpha = star.alphaBase * (0.4f + 0.6f * twinkle)
            drawCircle(
                color = Color.White.copy(alpha = currentAlpha),
                radius = star.size,
                center = Offset(width * star.xOffset, height * star.yOffset)
            )
        }
    }
}

private data class Star(
    val xOffset: Float,
    val yOffset: Float,
    val size: Float,
    val alphaBase: Float
)
