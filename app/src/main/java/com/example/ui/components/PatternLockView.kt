package com.example.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PlenxoColors

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PatternLockView(
    modifier: Modifier = Modifier,
    onPatternComplete: (String) -> Unit
) {
    var selectedDots by remember { mutableStateOf(listOf<Int>()) }
    var currentTouchPos by remember { mutableStateOf<Offset?>(null) }
    var dotCenters by remember { mutableStateOf(mapOf<Int, Offset>()) }

    Box(modifier = modifier.aspectRatio(1f)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            currentTouchPos = Offset(event.x, event.y)
                            
                            // Check if touching any dot
                            dotCenters.forEach { (index, center) ->
                                val distance = Math.hypot(
                                    (center.x - event.x).toDouble(),
                                    (center.y - event.y).toDouble()
                                )
                                if (distance < 80f && !selectedDots.contains(index)) { // 80f is hit radius
                                    selectedDots = selectedDots + index
                                }
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            currentTouchPos = null
                            if (selectedDots.isNotEmpty()) {
                                onPatternComplete(selectedDots.joinToString(","))
                                selectedDots = emptyList()
                            }
                            true
                        }
                        else -> false
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val spacingX = width / 4
            val spacingY = height / 4

            // Update dot centers if needed
            if (dotCenters.isEmpty()) {
                val newCenters = mutableMapOf<Int, Offset>()
                for (row in 0..2) {
                    for (col in 0..2) {
                        newCenters[row * 3 + col] = Offset(
                            spacingX * (col + 1),
                            spacingY * (row + 1)
                        )
                    }
                }
                dotCenters = newCenters
            }

            // Draw lines between selected dots
            if (selectedDots.size > 1) {
                for (i in 0 until selectedDots.size - 1) {
                    val p1 = dotCenters[selectedDots[i]]
                    val p2 = dotCenters[selectedDots[i + 1]]
                    if (p1 != null && p2 != null) {
                        drawLine(
                            color = PlenxoColors.Primary,
                            start = p1,
                            end = p2,
                            strokeWidth = 10f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // Draw line from last dot to current touch position
            val touchPos = currentTouchPos
            if (selectedDots.isNotEmpty() && touchPos != null) {
                val lastDot = dotCenters[selectedDots.last()]
                if (lastDot != null) {
                    drawLine(
                        color = PlenxoColors.Primary.copy(alpha = 0.5f),
                        start = lastDot,
                        end = touchPos,
                        strokeWidth = 10f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Draw dots
            dotCenters.forEach { (index, center) ->
                val isSelected = selectedDots.contains(index)
                drawCircle(
                    color = if (isSelected) PlenxoColors.Primary else Color.Gray,
                    radius = if (isSelected) 24f else 16f,
                    center = center
                )
            }
        }
    }
}
