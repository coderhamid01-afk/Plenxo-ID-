package com.example.ui.theme

import android.graphics.Bitmap
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = PlenxoElectricViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B0764),
    onPrimaryContainer = Color(0xFFF3E8FF),
    secondary = PlenxoNeonCyan,
    onSecondary = Color(0xFF00363A),
    secondaryContainer = Color(0xFF004F56),
    onSecondaryContainer = Color(0xFF97F0FF),
    tertiary = Color(0xFFD8B4FE),
    onTertiary = Color(0xFF4C1D95),
    background = Color(0xFF121212),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF1E1E2E),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF282A36),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0x4DFFFFFF),
    error = PlenxoError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PlenxoElectricViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3E8FF),
    onPrimaryContainer = Color(0xFF3B0764),
    secondary = Color(0xFF0097A7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F7FA),
    onSecondaryContainer = Color(0xFF006064),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color.White,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF121212),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121212),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1),
    error = PlenxoError,
    onError = Color.White
)

@Composable
fun PlenxoTheme(
    themeMode: String = "SYSTEM_DEFAULT",
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode.uppercase()) {
        "LIGHT" -> false
        "DARK" -> true
        else -> systemInDark
    }

    val view = LocalView.current
    var previousIsDark by remember { mutableStateOf<Boolean?>(null) }
    var snapshotBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val transitionProgress = remember { Animatable(1f) }
    var isAnimating by remember { mutableStateOf(false) }

    LaunchedEffect(isDark) {
        if (previousIsDark != null && previousIsDark != isDark) {
            val bitmap = captureViewToBitmap(view)
            if (bitmap != null) {
                snapshotBitmap = bitmap
                isAnimating = true
                transitionProgress.snapTo(0f)
            }
            previousIsDark = isDark

            if (isAnimating) {
                // Top-to-bottom sweep over 500ms with FastOutSlowInEasing
                transitionProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 500,
                        easing = FastOutSlowInEasing
                    )
                )
                isAnimating = false
                snapshotBitmap = null
            }
        } else {
            previousIsDark = isDark
        }
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    val currentLang by com.example.util.LocaleHelper.currentLanguage.collectAsState()
    val baseContext = androidx.compose.ui.platform.LocalContext.current
    val localizedContext = remember(currentLang, baseContext) {
        com.example.util.LocaleHelper.getLocalizedContext(baseContext, currentLang)
    }
    val layoutDirection = if (com.example.util.LocaleHelper.isRtlLanguage(currentLang)) {
        androidx.compose.ui.unit.LayoutDirection.Rtl
    } else {
        androidx.compose.ui.unit.LayoutDirection.Ltr
    }

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalContext provides localizedContext,
        androidx.compose.ui.platform.LocalLayoutDirection provides layoutDirection
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = androidx.compose.material3.Typography()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                content()

                val currentSnapshot = snapshotBitmap
                val progress = transitionProgress.value

                if (isAnimating && currentSnapshot != null && progress < 1f) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {} // Block touch interactions during 500ms theme transition
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val wipeY = canvasHeight * progress

                    // Draw the OLD theme snapshot clipped from wipeY down to canvasHeight
                    clipRect(
                        left = 0f,
                        top = wipeY,
                        right = canvasWidth,
                        bottom = canvasHeight
                    ) {
                        drawImage(
                            image = currentSnapshot,
                            dstSize = IntSize(canvasWidth.toInt(), canvasHeight.toInt())
                        )
                    }

                    // Dynamic top-to-bottom glowing dividing ribbon at wipeY
                    val ribbonHeight = 6.dp.toPx()
                    val glowColor = if (isDark) Color(0xFF00E5FF) else Color(0xFF7C3AED)

                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                glowColor.copy(alpha = 0f),
                                glowColor,
                                glowColor.copy(alpha = 0f)
                            ),
                            startY = (wipeY - ribbonHeight).coerceAtLeast(0f),
                            endY = wipeY + ribbonHeight
                        ),
                        topLeft = Offset(0f, (wipeY - ribbonHeight / 2).coerceAtLeast(0f)),
                        size = Size(canvasWidth, ribbonHeight)
                    )
                }
            }
        }
    }
}
}

@Composable
fun PlenxoAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    PlenxoTheme(themeMode = if (darkTheme) "DARK" else "LIGHT", content = content)
}

private fun captureViewToBitmap(view: View): ImageBitmap? {
    if (view.width <= 0 || view.height <= 0) return null
    return try {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        view.draw(canvas)
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}


