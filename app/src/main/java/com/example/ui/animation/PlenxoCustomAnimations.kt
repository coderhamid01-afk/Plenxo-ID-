package com.example.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * PLENXO HIGH-END CUSTOM COMPOSE ANIMATION SUITE
 * 10 Professional, Human-Crafted Animations with Instant Toggle Support
 */

// =========================================================================
// 1. FLUID SPRING BUBBLE ENTRANCE
// Chat message bubbles slide with custom spring damping and scale-up on enter
// =========================================================================
fun Modifier.fluidSpringBubbleEntrance(
    enabled: Boolean = true,
    initialOffsetY: Float = 60f
): Modifier = composed {
    if (!enabled) return@composed this

    val scaleAnim = remember { Animatable(0.7f) }
    val offsetYAnim = remember { Animatable(initialOffsetY) }
    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            offsetYAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
            )
        }
    }

    this.graphicsLayer {
        scaleX = scaleAnim.value
        scaleY = scaleAnim.value
        translationY = offsetYAnim.value
        alpha = alphaAnim.value
    }
}

@Composable
fun FluidSpringBubbleWrapper(
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fluidSpringBubbleEntrance(enabled)) {
        content()
    }
}

// =========================================================================
// 2. NEON GLOW PULSE AVATAR
// Canvas-rendered animated gradient aura border slowly pulsing around avatars
// =========================================================================
fun Modifier.neonGlowPulseAvatar(
    enabled: Boolean = true,
    glowColor: Color = Color(0xFF00E5FF),
    secondaryColor: Color = Color(0xFF7C4DFF)
): Modifier = composed {
    if (!enabled) return@composed this

    val infiniteTransition = rememberInfiniteTransition(label = "NeonGlow")
    val pulseRatio by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseRatio"
    )

    val alphaRatio by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlphaRatio"
    )

    this.drawBehind {
        val radius = (size.minDimension / 2f) * pulseRatio
        val center = Offset(size.width / 2f, size.height / 2f)

        // Outer Aura Radial Gradient Ring
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowColor.copy(alpha = alphaRatio * 0.6f),
                    secondaryColor.copy(alpha = alphaRatio * 0.2f),
                    Color.Transparent
                ),
                center = center,
                radius = radius * 1.25f
            ),
            radius = radius * 1.25f,
            center = center
        )

        // Pulsing Stroke Ring
        drawCircle(
            color = glowColor.copy(alpha = alphaRatio),
            radius = size.minDimension / 2f + 4.dp.toPx(),
            center = center,
            style = Stroke(width = 2.5.dp.toPx())
        )
    }
}

@Composable
fun NeonGlowPulseAvatarWrapper(
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    glowColor: Color = Color(0xFF00E5FF),
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.neonGlowPulseAvatar(enabled, glowColor)) {
        content()
    }
}

// =========================================================================
// 3. GLASSMORPHIC BACKDROP BLUR EXPANSION
// Scale, opacity, and glassmorphic backdrop transformation for dialogs
// =========================================================================
@Composable
fun GlassmorphicExpansionContainer(
    enabled: Boolean = true,
    visible: Boolean = true,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xCC131824),
    content: @Composable BoxScope.() -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.82f,
        animationSpec = if (enabled) spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ) else tween(0),
        label = "GlassmorphicScale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (enabled) tween(300) else tween(0),
        label = "GlassmorphicAlpha"
    )

    if (alpha > 0.01f) {
        Box(
            modifier = modifier
                .graphicsLayer {
                    scaleX = if (enabled) scale else 1f
                    scaleY = if (enabled) scale else 1f
                    this.alpha = if (enabled) alpha else 1f
                }
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            backgroundColor.copy(alpha = 0.92f),
                            backgroundColor.copy(alpha = 0.85f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .clip(RoundedCornerShape(24.dp))
                .padding(20.dp),
            content = content
        )
    }
}

// =========================================================================
// 4. PARALLAX DEPTH SCREEN TRANSITIONS
// Smooth 3D depth tilt and horizontal spring slide when switching screens
// =========================================================================
fun Modifier.parallaxDepthTransition(
    enabled: Boolean = true,
    isEntering: Boolean = true
): Modifier = composed {
    if (!enabled) return@composed this

    val animProgress = remember { Animatable(if (isEntering) 1f else 0f) }

    LaunchedEffect(isEntering) {
        animProgress.animateTo(
            targetValue = if (isEntering) 0f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    val density = LocalDensity.current.density

    this.graphicsLayer {
        val factor = animProgress.value
        translationX = factor * 120f * density
        rotationY = factor * -8f
        cameraDistance = 16f * density
        scaleX = 1f - (factor * 0.06f)
        scaleY = 1f - (factor * 0.06f)
        alpha = 1f - (factor * 0.4f)
    }
}

// =========================================================================
// 5. INTERACTIVE LIQUID RIPPLE BUTTON
// Touch-down morphing liquid ripple effect with press scale feedback
// =========================================================================
fun Modifier.liquidRippleButton(
    enabled: Boolean = true,
    rippleColor: Color = Color(0x6658A6FF),
    onClick: () -> Unit = {}
): Modifier = composed {
    if (!enabled) {
        return@composed this.clickable { onClick() }
    }

    val coroutineScope = rememberCoroutineScope()
    var touchOffset by remember { mutableStateOf<Offset?>(null) }
    val rippleRadius = remember { Animatable(0f) }
    val rippleAlpha = remember { Animatable(0.6f) }
    val buttonScale = remember { Animatable(1f) }

    this
        .graphicsLayer {
            scaleX = buttonScale.value
            scaleY = buttonScale.value
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = { offset ->
                    touchOffset = offset
                    coroutineScope.launch {
                        buttonScale.animateTo(
                            0.94f,
                            spring(stiffness = Spring.StiffnessHigh)
                        )
                    }
                    coroutineScope.launch {
                        rippleRadius.snapTo(0f)
                        rippleAlpha.snapTo(0.6f)
                        launch {
                            rippleRadius.animateTo(
                                targetValue = size.width.toFloat() * 1.5f,
                                animationSpec = tween(400, easing = FastOutSlowInEasing)
                            )
                        }
                        launch {
                            rippleAlpha.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(400, easing = LinearEasing)
                            )
                        }
                    }
                    tryAwaitRelease()
                    coroutineScope.launch {
                        buttonScale.animateTo(
                            1f,
                            spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    onClick()
                }
            )
        }
        .drawWithContent {
            drawContent()
            touchOffset?.let { offset ->
                if (rippleAlpha.value > 0.01f) {
                    drawCircle(
                        color = rippleColor.copy(alpha = rippleAlpha.value),
                        radius = rippleRadius.value,
                        center = offset
                    )
                }
            }
        }
}

// =========================================================================
// 6. MULTI-LAYER SHIMMER SKELETON LOADER
// Metallic gradient sweep across list loading placeholders
// =========================================================================
fun Modifier.shimmerSkeletonLoader(
    enabled: Boolean = true,
    baseColor: Color = Color(0xFF1E2638),
    highlightColor: Color = Color(0xFF3A4866)
): Modifier = composed {
    if (!enabled) return@composed this.background(baseColor)

    val transition = rememberInfiniteTransition(label = "Shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Translate"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            baseColor,
            highlightColor,
            baseColor
        ),
        start = Offset(translateAnim, translateAnim),
        end = Offset(translateAnim + 300f, translateAnim + 300f)
    )

    this.background(brush = brush)
}

// =========================================================================
// 7. FLOATING PHYSICS PARTICLE BACKGROUND
// Interactive Canvas particle system floating background for auth/welcome
// =========================================================================
private data class Particle(
    var x: Float,
    var y: Float,
    var radius: Float,
    var vx: Float,
    var vy: Float,
    var alpha: Float,
    var color: Color
)

@Composable
fun FloatingParticleBackground(
    enabled: Boolean = true,
    particleCount: Int = 28,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    if (!enabled) {
        Box(modifier = modifier, content = content)
        return
    }

    val particles = remember { mutableStateListOf<Particle>() }
    val particleColors = remember {
        listOf(
            Color(0xFF58A6FF),
            Color(0xFF7928CA),
            Color(0xFF00E5FF),
            Color(0xFFFF0080)
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ParticleLoop")
    val frameStep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "FrameStep"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (particles.isEmpty()) {
                repeat(particleCount) {
                    particles.add(
                        Particle(
                            x = Random.nextFloat() * size.width,
                            y = Random.nextFloat() * size.height,
                            radius = Random.nextFloat() * 4.5f + 1.5f,
                            vx = (Random.nextFloat() - 0.5f) * 1.2f,
                            vy = (Random.nextFloat() - 0.5f) * 1.2f,
                            alpha = Random.nextFloat() * 0.5f + 0.2f,
                            color = particleColors.random()
                        )
                    )
                }
            }

            // Update particle physics frame
            particles.forEach { p ->
                p.x += p.vx
                p.y += p.vy

                if (p.x < 0) p.x = size.width
                if (p.x > size.width) p.x = 0f
                if (p.y < 0) p.y = size.height
                if (p.y > size.height) p.y = 0f

                drawCircle(
                    color = p.color.copy(alpha = p.alpha),
                    radius = p.radius.dp.toPx(),
                    center = Offset(p.x, p.y)
                )
            }
        }
        content()
    }
}

// =========================================================================
// 8. EXPLOSIVE PARTICLE CONFETTI BURST
// Canvas-based physics particle explosion effect triggered on success actions
// =========================================================================
private data class ConfettiParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    var color: Color,
    var rotation: Float,
    var rotationSpeed: Float,
    var alpha: Float = 1f
)

@Composable
fun ConfettiBurstEffect(
    trigger: Boolean,
    modifier: Modifier = Modifier,
    particleCount: Int = 45,
    onFinished: () -> Unit = {}
) {
    if (!trigger) return

    val confettiList = remember { mutableStateListOf<ConfettiParticle>() }
    val animProgress = remember { Animatable(0f) }

    val colors = remember {
        listOf(
            Color(0xFF58A6FF),
            Color(0xFF10B981),
            Color(0xFFF59E0B),
            Color(0xFFEF4444),
            Color(0xFF8B5CF6)
        )
    }

    LaunchedEffect(trigger) {
        confettiList.clear()
        repeat(particleCount) {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = Random.nextFloat() * 18f + 8f
            confettiList.add(
                ConfettiParticle(
                    x = 0f,
                    y = 0f,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed - 6f, // Initial upward burst
                    size = Random.nextFloat() * 12f + 6f,
                    color = colors.random(),
                    rotation = Random.nextFloat() * 360f,
                    rotationSpeed = (Random.nextFloat() - 0.5f) * 20f
                )
            )
        }

        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing)
        )
        onFinished()
    }

    if (animProgress.value < 1f) {
        Canvas(modifier = modifier.fillMaxSize().testTag("confetti_burst_canvas")) {
            val center = Offset(size.width / 2f, size.height / 2f)

            confettiList.forEach { p ->
                val progress = animProgress.value
                val currentX = center.x + (p.vx * progress * 35f)
                val currentY = center.y + (p.vy * progress * 35f) + (progress * progress * 200f) // Gravity
                val currentAlpha = (1f - progress).coerceIn(0f, 1f)

                drawCircle(
                    color = p.color.copy(alpha = currentAlpha),
                    radius = p.size.dp.toPx() / 2f,
                    center = Offset(currentX, currentY)
                )
            }
        }
    }
}

// =========================================================================
// 9. 3D CARD GYRO / TOUCH TILT
// Interactive 3D rotation tilt on user profile cards based on drag position
// =========================================================================
fun Modifier.card3DTilt(
    enabled: Boolean = true,
    maxRotationDegrees: Float = 14f
): Modifier = composed {
    if (!enabled) return@composed this

    var rotX by remember { mutableFloatStateOf(0f) }
    var rotY by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    val smoothRotX by animateFloatAsState(
        targetValue = rotX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "RotX"
    )

    val smoothRotY by animateFloatAsState(
        targetValue = rotY,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "RotY"
    )

    val density = LocalDensity.current.density

    this
        .graphicsLayer {
            rotationX = smoothRotX
            rotationY = smoothRotY
            cameraDistance = 18f * density
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { },
                onDragEnd = {
                    rotX = 0f
                    rotY = 0f
                },
                onDragCancel = {
                    rotX = 0f
                    rotY = 0f
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    rotX = (rotX - (dragAmount.y * 0.15f)).coerceIn(-maxRotationDegrees, maxRotationDegrees)
                    rotY = (rotY + (dragAmount.x * 0.15f)).coerceIn(-maxRotationDegrees, maxRotationDegrees)
                }
            )
        }
}

// =========================================================================
// 10. MORPHING FAB-TO-BAR TRANSFORMATION
// Dynamic shape morphing expanding FloatingActionButton into an action bar
// =========================================================================
@Composable
fun MorphingFabToBar(
    enabled: Boolean = true,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
    fabColor: Color = Color(0xFF58A6FF),
    fabContent: @Composable () -> Unit,
    barContent: @Composable () -> Unit
) {
    val animatedWidth by animateFloatAsState(
        targetValue = if (isExpanded) 320f else 56f,
        animationSpec = if (enabled) spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ) else tween(0),
        label = "FabWidth"
    )

    val animatedCornerRadius by animateFloatAsState(
        targetValue = if (isExpanded) 16f else 28f,
        animationSpec = if (enabled) spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ) else tween(0),
        label = "FabCorner"
    )

    Surface(
        modifier = modifier
            .width(animatedWidth.dp)
            .height(56.dp)
            .clickable { if (!isExpanded) onToggleExpand() },
        shape = RoundedCornerShape(animatedCornerRadius.dp),
        color = fabColor,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isExpanded) {
                barContent()
            } else {
                fabContent()
            }
        }
    }
}
