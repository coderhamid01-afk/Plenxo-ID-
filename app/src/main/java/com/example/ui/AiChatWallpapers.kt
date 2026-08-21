package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin

private data class Particle(
    val xPercent: Float,
    val yPercent: Float,
    val radius: Float,
    val speed: Float,
    val alpha: Float,
    val horizontalDrift: Float
)

private data class MatrixStream(
    val xPercent: Float,
    var yPercent: Float,
    val speed: Float,
    val chars: List<Char>,
    val scale: Float
)

private data class WallpaperStar(
    val xPercent: Float,
    val yPercent: Float,
    val maxRadius: Float,
    val flickerSpeed: Float,
    val phaseOffset: Float
)

private data class ShootingStar(
    var startXPercent: Float,
    var startYPercent: Float,
    var length: Float,
    var speed: Float,
    var angle: Float,
    var delay: Float
)

private data class LavaBlob(
    val xPercent: Float,
    val yPercentOffset: Float,
    val sizePercent: Float,
    val speed: Float,
    val horizontalAmplitude: Float,
    val horizontalSpeed: Float
)

private data class BokehBubble(
    val xPercent: Float,
    val yPercent: Float,
    val radius: Float,
    val speedY: Float,
    val speedX: Float,
    val alpha: Float,
    val color: Color
)

@Composable
fun CyberMatrixWallpaper() {
    val infiniteTransition = rememberInfiniteTransition(label = "cyber_matrix")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "matrix_progress"
    )

    val streams = remember {
        List(25) {
            val streamChars = List(6) { ('A'..'Z').random() }
            MatrixStream(
                xPercent = (0..100).random() / 100f,
                yPercent = (0..100).random() / 100f,
                speed = 0.25f + (0..100).random() / 100f * 0.5f,
                chars = streamChars,
                scale = 0.5f + (0..100).random() / 100f * 0.7f
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        drawRect(color = Color(0xFF0D1117))

        streams.forEach { stream ->
            val currentYPercent = (stream.yPercent + progress * stream.speed) % 1.0f
            val startY = currentYPercent * height
            val x = stream.xPercent * width

            for (i in 0 until stream.chars.size) {
                val y = startY - (i * 20.dp.toPx() * stream.scale)
                val finalY = if (y < 0) y + height else y
                
                val alpha = (1f - (i.toFloat() / stream.chars.size)) * 0.65f
                val color = if (i == 0) Color(0xFFE0FFE0) else Color(0xFF00FF66)

                drawCircle(
                    color = color,
                    radius = 2.5f.dp.toPx() * stream.scale,
                    center = androidx.compose.ui.geometry.Offset(x, finalY),
                    alpha = alpha
                )
            }
        }
    }
}

@Composable
fun DeepOceanWallpaper() {
    val infiniteTransition = rememberInfiniteTransition(label = "deep_ocean")
    val animatedPhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )
    val animatedPhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0A192F),
                    Color(0xFF020C1B),
                    Color(0xFF010409)
                )
            )
        )

        val path1 = Path()
        path1.moveTo(0f, height)
        for (x in 0..width.toInt() step 12) {
            val y = height * 0.35f + sin(x * 0.004f + animatedPhase1) * 30f
            path1.lineTo(x.toFloat(), y)
        }
        path1.lineTo(width, height)
        path1.close()
        drawPath(
            path = path1,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF0F3A5F).copy(alpha = 0.22f), Color.Transparent)
            )
        )

        val path2 = Path()
        path2.moveTo(0f, height)
        for (x in 0..width.toInt() step 12) {
            val y = height * 0.55f + sin(x * 0.006f - animatedPhase2) * 40f
            path2.lineTo(x.toFloat(), y)
        }
        path2.lineTo(width, height)
        path2.close()
        drawPath(
            path = path2,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF172A45).copy(alpha = 0.15f), Color.Transparent)
            )
        )
    }
}

@Composable
fun StarryNightWallpaper() {
    val infiniteTransition = rememberInfiniteTransition(label = "starry_night")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "star_time"
    )

    val shootingStarProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shooting_star"
    )

    val stars = remember {
        List(35) {
            WallpaperStar(
                xPercent = (0..100).random() / 100f,
                yPercent = (0..70).random() / 100f,
                maxRadius = 1f + (0..100).random() / 100f * 2f,
                flickerSpeed = 1.2f + (0..100).random() / 100f * 3f,
                phaseOffset = (0..100).random() / 100f * 6.28f
            )
        }
    }

    val shootingStar = remember {
        ShootingStar(
            startXPercent = 0.2f,
            startYPercent = 0.1f,
            length = 70f,
            speed = 1.1f,
            angle = 30f,
            delay = 0f
        )
    }

    LaunchedEffect(shootingStarProgress) {
        if (shootingStarProgress < 0.05f) {
            shootingStar.startXPercent = 0.1f + (0..60).random() / 100f
            shootingStar.startYPercent = 0.05f + (0..30).random() / 100f
            shootingStar.length = 50f + (0..60).random()
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF030712),
                    Color(0xFF0B132B),
                    Color(0xFF1C2541)
                )
            )
        )

        stars.forEach { star ->
            val scale = 0.3f + 0.7f * sin(time * star.flickerSpeed + star.phaseOffset)
            val currentRadius = star.maxRadius * scale.coerceAtLeast(0.1f)
            drawCircle(
                color = Color.White,
                radius = currentRadius.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(
                    x = star.xPercent * width,
                    y = star.yPercent * height
                ),
                alpha = 0.3f + 0.7f * scale.coerceAtLeast(0f).coerceAtMost(1f)
            )
        }

        if (shootingStarProgress in 0f..1f) {
            val progress = shootingStarProgress
            val startX = shootingStar.startXPercent * width + (progress * width * 0.35f)
            val startY = shootingStar.startYPercent * height + (progress * height * 0.18f)
            
            val endX = startX - (shootingStar.length * kotlin.math.cos(Math.toRadians(shootingStar.angle.toDouble()))).toFloat()
            val endY = startY - (shootingStar.length * kotlin.math.sin(Math.toRadians(shootingStar.angle.toDouble()))).toFloat()

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.White),
                    start = androidx.compose.ui.geometry.Offset(endX, endY),
                    end = androidx.compose.ui.geometry.Offset(startX, startY)
                ),
                start = androidx.compose.ui.geometry.Offset(endX, endY),
                end = androidx.compose.ui.geometry.Offset(startX, startY),
                strokeWidth = 2f.dp.toPx(),
                alpha = (1f - progress).coerceIn(0f, 1f)
            )
        }
    }
}

@Composable
fun NeonPulseWallpaper() {
    val infiniteTransition = rememberInfiniteTransition(label = "neon_pulse")
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale1"
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 1.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale2"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        drawRect(color = Color(0xFF0F0B1E))

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFE040FB).copy(alpha = 0.12f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(width * 0.3f, height * 0.25f),
                radius = width * 0.65f * scale1
            ),
            radius = width * 0.65f * scale1,
            center = androidx.compose.ui.geometry.Offset(width * 0.3f, height * 0.25f)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF18FFFF).copy(alpha = 0.10f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(width * 0.75f, height * 0.7f),
                radius = width * 0.75f * scale2
            ),
            radius = width * 0.75f * scale2,
            center = androidx.compose.ui.geometry.Offset(width * 0.75f, height * 0.7f)
        )
    }
}

@Composable
fun LavaLampWallpaper() {
    val infiniteTransition = rememberInfiniteTransition(label = "lava_lamp")
    val animatedTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(35000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lava_time"
    )

    val blobs = remember {
        List(5) {
            LavaBlob(
                xPercent = 0.15f + (0..70).random() / 100f,
                yPercentOffset = (0..100).random() / 100f,
                sizePercent = 0.15f + (0..15).random() / 100f,
                speed = 0.35f + (0..50).random() / 100f * 0.5f,
                horizontalAmplitude = 12f + (0..30).random().toFloat(),
                horizontalSpeed = 0.8f + (0..100).random() / 100f * 2.5f
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF230303),
                    Color(0xFF120101)
                )
            )
        )

        blobs.forEach { blob ->
            val currentYPercent = (blob.yPercentOffset - animatedTime * blob.speed) % 1.0f
            val finalYPercent = if (currentYPercent < 0f) currentYPercent + 1.0f else currentYPercent
            val finalY = finalYPercent * height

            val drift = sin((animatedTime * 2 * Math.PI * blob.horizontalSpeed).toDouble()).toFloat() * blob.horizontalAmplitude.dp.toPx()
            val finalX = (blob.xPercent * width + drift).coerceIn(0f, width)

            val radius = blob.sizePercent * width

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF3D00).copy(alpha = 0.20f),
                        Color(0xFFFF9100).copy(alpha = 0.07f),
                        Color.Transparent
                    ),
                    center = androidx.compose.ui.geometry.Offset(finalX, finalY),
                    radius = radius
                ),
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(finalX, finalY)
            )
        }
    }
}

@Composable
fun FloatingBokehWallpaper() {
    val infiniteTransition = rememberInfiniteTransition(label = "floating_bokeh")
    val animatedTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bokeh_time"
    )

    val bokehColors = listOf(
        Color(0xFF80D8FF),
        Color(0xFFFF8A80),
        Color(0xFFB9F6CA),
        Color(0xFFFFE57F),
        Color(0xFFEA80FC)
    )

    val bubbles = remember {
        List(15) {
            BokehBubble(
                xPercent = (0..100).random() / 100f,
                yPercent = (0..100).random() / 100f,
                radius = 30f + (0..40).random(),
                speedY = 0.07f + (0..100).random() / 100f * 0.1f,
                speedX = 0.015f + (0..100).random() / 100f * 0.05f,
                alpha = 0.05f + (0..100).random() / 100f * 0.12f,
                color = bokehColors.random()
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF131722),
                    Color(0xFF0F111A)
                )
            )
        )

        bubbles.forEach { bubble ->
            val currentYPercent = (bubble.yPercent - animatedTime * bubble.speedY) % 1.0f
            val finalY = if (currentYPercent < 0f) (currentYPercent + 1.0f) * height else currentYPercent * height

            val currentXPercent = (bubble.xPercent + animatedTime * bubble.speedX) % 1.0f
            val finalX = currentXPercent * width

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        bubble.color.copy(alpha = bubble.alpha),
                        bubble.color.copy(alpha = bubble.alpha * 0.35f),
                        Color.Transparent
                    ),
                    center = androidx.compose.ui.geometry.Offset(finalX, finalY),
                    radius = bubble.radius.dp.toPx()
                ),
                radius = bubble.radius.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(finalX, finalY)
            )
        }
    }
}

@Composable
fun CosmicVoidWallpaper() {
    val infiniteTransition = rememberInfiniteTransition(label = "cosmic")
    val animatedProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradient_shift"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Compute oscillating offsets to drive smoothly changing gradient coordinates
        val dx = kotlin.math.cos(animatedProgress.toDouble()).toFloat() * 0.35f
        val dy = kotlin.math.sin(animatedProgress.toDouble()).toFloat() * 0.35f

        val startX = width * (0.5f + dx)
        val startY = height * (0.2f + dy)
        val endX = width * (0.5f - dx)
        val endY = height * (0.8f - dy)

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF070B19), // Midnight space void
                    Color(0xFF140D24), // Ultra dark violet
                    Color(0xFF29113B), // Soft deep cosmic purple
                    Color(0xFF0F0B1E)  // Dark deep navy
                ),
                start = androidx.compose.ui.geometry.Offset(startX, startY),
                end = androidx.compose.ui.geometry.Offset(endX, endY)
            )
        )
    }
}

@Composable
fun NatureZenWallpaper() {
    val infiniteTransition = rememberInfiniteTransition(label = "nature")
    val animatedTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_time"
    )

    // Generate a stable list of 25 soft floating particles
    val particles = remember {
        List(25) {
            Particle(
                xPercent = (0..100).random() / 100f,
                yPercent = (0..100).random() / 100f,
                radius = (8..24).random().toFloat(),
                speed = (0.2f + (0..100).random() / 100f * 0.8f) * 0.12f,
                alpha = 0.04f + (0..100).random() / 100f * 0.12f,
                horizontalDrift = (0..100).random() / 100f * 35f
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Serene dark-green nature background gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF05110E),
                    Color(0xFF0E1F1A),
                    Color(0xFF061412)
                )
            )
        )

        particles.forEach { p ->
            // Floating upwards logic
            val currentYPercent = (p.yPercent - animatedTime * p.speed) % 1.0f
            val finalY = if (currentYPercent < 0f) (currentYPercent + 1.0f) * height else currentYPercent * height

            // Horizontal breeze/drift using sine wave
            val drift = sin((animatedTime * 2 * Math.PI + p.radius).toDouble()).toFloat() * p.horizontalDrift
            val finalX = (p.xPercent * width + drift) % width

            drawCircle(
                color = Color(0xFF66BB6A), // Emerald/Leaf Green
                radius = p.radius,
                center = androidx.compose.ui.geometry.Offset(
                    x = if (finalX < 0f) finalX + width else finalX,
                    y = finalY
                ),
                alpha = p.alpha
            )
        }
    }
}

@Composable
fun NeonWaveWallpaper() {
    val infiniteTransition = rememberInfiniteTransition(label = "neon_wave")
    val animatedTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "neon_time"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Deep background
        drawRect(color = Color(0xFF0A0E14))

        val width = size.width
        val height = size.height

        // Draw multiple glowing waves
        val colors = listOf(Color(0xFF00D2FF), Color(0xFF92FE9D), Color(0xFF0072FF), Color(0xFF00C6FF))
        
        for (i in 0 until 3) {
            val path = Path()
            path.moveTo(0f, height * (0.4f + i * 0.1f))
            
            for (x in 0..width.toInt() step 15) {
                val y = height * (0.4f + i * 0.1f) + 
                        sin(x * 0.01f + i * 1.5f + animatedTime) * 35f
                path.lineTo(x.toFloat(), y)
            }
            
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(colors),
                style = Stroke(width = 3.dp.toPx()),
                alpha = 0.35f - i * 0.08f
            )
        }
    }
}

@Composable
fun DarkGeometricWallpaper() {
    val infiniteTransition = rememberInfiniteTransition(label = "geometric")
    val animatedRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Subtle gradient background
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF141E30), Color(0xFF243B55))
            )
        )

        val width = size.width
        val height = size.height

        // Draw geometric polygons with slight rotation logic mapped to simple coordinates
        val polyColor = Color(0xFF58A6FF).copy(alpha = 0.04f)
        val angleRad = Math.toRadians(animatedRotation.toDouble())
        val cosA = kotlin.math.cos(angleRad).toFloat()
        val sinA = kotlin.math.sin(angleRad).toFloat()
        
        for (i in 0 until 5) {
            val path = Path().apply {
                val x1 = width * (0.1f * i)
                val y1 = height * (0.2f * i)
                val x2 = width * (0.5f + 0.1f * i)
                val y2 = height * (0.1f * i)
                val x3 = width * (0.8f - 0.1f * i)
                val y3 = height * (0.5f + 0.1f * i)
                val x4 = width * (0.3f * i)
                val y4 = height * 0.8f

                // Rotate slightly around center to add organic dynamic motion
                val cx = width / 2
                val cy = height / 2

                fun rotX(px: Float, py: Float) = cx + (px - cx) * cosA - (py - cy) * sinA
                fun rotY(px: Float, py: Float) = cy + (px - cx) * sinA + (py - cy) * cosA

                moveTo(rotX(x1, y1), rotY(x1, y1))
                lineTo(rotX(x2, y2), rotY(x2, y2))
                lineTo(rotX(x3, y3), rotY(x3, y3))
                lineTo(rotX(x4, y4), rotY(x4, y4))
                close()
            }
            drawPath(path = path, color = polyColor)
        }
    }
}

@Composable
fun WallpaperRenderer(wallpaperId: String) {
    when (wallpaperId) {
        "COSMIC_VOID" -> CosmicVoidWallpaper()
        "NATURE_ZEN" -> NatureZenWallpaper()
        "AI_NEON_WAVE" -> NeonWaveWallpaper()
        "AI_DARK_GEOMETRIC" -> DarkGeometricWallpaper()
        
        // 6 New Pure Code-Based Motion Backgrounds
        "MOTION_CYBER_MATRIX" -> CyberMatrixWallpaper()
        "MOTION_DEEP_OCEAN" -> DeepOceanWallpaper()
        "MOTION_STARRY_NIGHT" -> StarryNightWallpaper()
        "MOTION_NEON_PULSE" -> NeonPulseWallpaper()
        "MOTION_LAVA_LAMP" -> LavaLampWallpaper()
        "MOTION_FLOATING_BOKEH" -> FloatingBokehWallpaper()
        "MOTION_MINIMALIST" -> MinimalistMotionWallpaper()
        "MOTION_SAD" -> SadMotionWallpaper()
        "MOTION_ROMANTIC" -> RomanticMotionWallpaper()

        "BG_OBSIDIAN_STRIKE" -> WallpaperImage(com.example.R.drawable.bg_phantom_veil)
        "BG_BOKEH_REST" -> WallpaperImage(com.example.R.drawable.bg_bokeh_rest)
        "BG_ZEN_GLOW" -> WallpaperImage(com.example.R.drawable.bg_zen_glow)
        "BG_ETHEREAL_LUNAR" -> WallpaperImage(com.example.R.drawable.bg_ethereal_lunar)
        "BG_ROYAL_VELVET" -> WallpaperImage(com.example.R.drawable.bg_royal_velvet)
        "BG_HACKERS_MATRIX" -> WallpaperImage(com.example.R.drawable.bg_hackers_matrix)
        "BG_DEEP_OCEAN" -> WallpaperImage(com.example.R.drawable.bg_deep_ocean)
        "BG_ABSTRACT_GLASS" -> WallpaperImage(com.example.R.drawable.bg_liquid_metallic)
        "BG_LUNAR_SURFACE" -> WallpaperImage(com.example.R.drawable.bg_ethereal_lunar)
        "BG_GOLDEN_AURA" -> WallpaperImage(com.example.R.drawable.bg_golden_aura)
        "BG_KISWA_REVERENCE" -> WallpaperImage(com.example.R.drawable.bg_kiswa_reverence)
        "BG_GEOMETRIC_MATTE" -> WallpaperImage(com.example.R.drawable.bg_calligraphy_matte)
        "BG_SILHOUETTE_TWILIGHT" -> WallpaperImage(com.example.R.drawable.bg_silhouette_twilight)
        "BG_CALLIGRAPHY_MATTE" -> WallpaperImage(com.example.R.drawable.bg_calligraphy_matte)
        "BG_TASBIH_REFLECTION" -> WallpaperImage(com.example.R.drawable.bg_tasbih_reflection)
        "BG_TANTRIC_ECLIPSE" -> WallpaperImage(com.example.R.drawable.bg_tantric_eclipse)
        "BG_SOVEREIGN_CROWN" -> WallpaperImage(com.example.R.drawable.bg_sovereign_crown)
        "BG_CIPHER_CORE" -> WallpaperImage(com.example.R.drawable.bg_hackers_matrix)
        "BG_PHANTOM_VEIL" -> WallpaperImage(com.example.R.drawable.bg_phantom_veil)
        "BG_LIQUID_METALLIC" -> WallpaperImage(com.example.R.drawable.bg_liquid_metallic)
        else -> {
            // Default background color or pattern
        }
    }
}

@Composable
private fun WallpaperImage(resId: Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val painter = coil.compose.rememberAsyncImagePainter(
        model = coil.request.ImageRequest.Builder(context)
            .data(resId)
            .crossfade(true)
            .build()
    )
    androidx.compose.foundation.Image(
        painter = painter,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = androidx.compose.ui.layout.ContentScale.Crop
    )
}

@Composable
fun MinimalistMotionWallpaper() {
    val infiniteTransition = rememberInfiniteTransition(label = "minimalist")
    val scaleFactor by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(color = Color(0xFF13151A))
        
        drawCircle(
            color = Color(0xFF30363D),
            radius = 120.dp.toPx() * scaleFactor,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.3f, size.height * 0.4f),
            alpha = 0.15f,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = Color(0xFF58A6FF),
            radius = 180.dp.toPx() / scaleFactor,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.7f, size.height * 0.6f),
            alpha = 0.08f,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f.dp.toPx())
        )
    }
}

@Composable
fun SadMotionWallpaper() {
    val infiniteTransition = rememberInfiniteTransition(label = "sad")
    val rainProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rain"
    )
    
    val raindrops = remember {
        List(30) {
            androidx.compose.ui.geometry.Offset(
                x = (0..100).random() / 100f,
                y = (0..100).random() / 100f
            )
        }
    }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(color = Color(0xFF181C26))
        
        raindrops.forEach { drop ->
            val currentY = ((drop.y + rainProgress) % 1.0f) * size.height
            val currentX = drop.x * size.width
            
            drawLine(
                color = Color(0xFF8B949E),
                start = androidx.compose.ui.geometry.Offset(currentX, currentY),
                end = androidx.compose.ui.geometry.Offset(currentX, currentY + 15.dp.toPx()),
                strokeWidth = 1.5f.dp.toPx(),
                alpha = 0.25f
            )
        }
    }
}

@Composable
fun RomanticMotionWallpaper() {
    val infiniteTransition = rememberInfiniteTransition(label = "romantic")
    val floatProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "romantic_float"
    )
    
    val bubbles = remember {
        List(15) {
            Triple(
                (0..100).random() / 100f,
                (0..100).random() / 100f,
                10.dp + (5..25).random().dp
            )
        }
    }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(color = Color(0xFF1E1117))
        
        bubbles.forEach { bubble ->
            val currentY = ((bubble.second - floatProgress + 1.0f) % 1.0f) * size.height
            val currentX = bubble.first * size.width + (kotlin.math.sin(floatProgress.toDouble() * 2 * Math.PI + bubble.second * 100).toFloat() * 15.dp.toPx())
            
            drawCircle(
                color = Color(0xFFFF5F85),
                radius = bubble.third.toPx(),
                center = androidx.compose.ui.geometry.Offset(currentX, currentY),
                alpha = 0.15f
            )
        }
    }
}
