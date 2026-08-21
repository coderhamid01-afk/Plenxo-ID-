package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Custom Circular Message Status Indicator
 *
 * STATE 1 (Sending):
 *   - Visual: A faded grey hollow circle / outline ring (#8E8E93, stroke only).
 * STATE 2 (Sent / Delivered):
 *   - Visual: A complete solid filled grey circle (#8E8E93, solid filled).
 * STATE 3 (Seen / Read):
 *   - Visual: A solid grey circle (#8E8E93) with a bright blue dot (#007AFF) centered inside.
 */
@Composable
fun CircularStatusIndicator(
    status: String,
    modifier: Modifier = Modifier.size(11.dp),
    greyColor: Color = Color(0xFF8E8E93),
    blueDotColor: Color = Color(0xFF007AFF)
) {
    Canvas(modifier = modifier) {
        val sizePx = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = (sizePx / 2f) - 0.75.dp.toPx()

        when (status.uppercase()) {
            "SENDING", "SENDING_LOCAL", "PENDING" -> {
                // STATE 1 (Sending): Faded grey hollow circle / outline ring (#8E8E93, stroke only)
                drawCircle(
                    color = greyColor,
                    radius = outerRadius,
                    center = center,
                    style = Stroke(width = 1.25.dp.toPx())
                )
            }
            "SEEN", "READ" -> {
                // STATE 3 (Seen / Read): Solid grey circle (#8E8E93) with a bright blue dot (#007AFF) centered inside
                drawCircle(
                    color = greyColor,
                    radius = outerRadius,
                    center = center,
                    style = Fill
                )
                drawCircle(
                    color = blueDotColor,
                    radius = outerRadius * 0.45f,
                    center = center,
                    style = Fill
                )
            }
            else -> {
                // STATE 2 (Sent / Delivered): Complete solid filled grey circle (#8E8E93, solid filled)
                drawCircle(
                    color = greyColor,
                    radius = outerRadius,
                    center = center,
                    style = Fill
                )
            }
        }
    }
}
