package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DarkAmbientCanvas(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "ClassicAmbientTransition")

    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Phase1"
    )
    
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Phase2"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        if (width == 0f || height == 0f) return@Canvas

        // Classic deep dark background (pure black and dark slate)
        val colorBg = Color(0xFF000000)
        val colorMid = Color(0xFF020617)
        
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(colorBg, colorMid, colorBg),
                start = Offset(0f, 0f),
                end = Offset(width, height)
            )
        )

        // Large, soft, elegant gradient orbs for a classic mesh feel
        // Using only deep blues and blacks to maintain the requested color scheme
        val orbs = listOf(
            Triple(Color(0xFF0D47A1).copy(alpha = 0.2f), 0.6f, 0.4f), // Deep Blue
            Triple(Color(0xFF1565C0).copy(alpha = 0.15f), 0.3f, 0.7f), // Blue
            Triple(Color(0xFF002171).copy(alpha = 0.25f), 0.7f, 0.8f), // Darker Indigo
            Triple(Color(0xFF1E3A8A).copy(alpha = 0.15f), 0.4f, 0.2f)  // Dark Blue
        )

        for (i in orbs.indices) {
            val (color, baseRatioX, baseRatioY) = orbs[i]
            val phase = if (i % 2 == 0) phase1 else phase2
            val speed = 1f + i * 0.3f
            
            // Slow, elegant elliptical movement
            val posX = width * baseRatioX + width * 0.3f * cos(phase * speed + i)
            val posY = height * baseRatioY + height * 0.2f * sin(phase * speed * 0.8f + i)
            
            val radius = width * (0.8f + 0.15f * sin(phase * 1.5f + i))

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = Offset(posX, posY),
                    radius = radius
                ),
                center = Offset(posX, posY),
                radius = radius
            )
        }
    }
}
