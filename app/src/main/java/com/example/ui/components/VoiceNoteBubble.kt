package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.media.AudioPlayerManager

@Composable
fun VoiceNoteBubble(
    audioUrl: String,
    isSentByCurrentUser: Boolean,
    audioPlayerManager: AudioPlayerManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playbackState by audioPlayerManager.playbackState.collectAsState()

    val isCurrentAudio = playbackState.currentUrl == audioUrl
    val isPlaying = isCurrentAudio && playbackState.isPlaying
    val currentPosMs = if (isCurrentAudio) playbackState.currentPositionMs else 0L
    val totalDurMs = if (isCurrentAudio && playbackState.totalDurationMs > 0) playbackState.totalDurationMs else 1L

    val progress = (currentPosMs.toFloat() / totalDurMs.toFloat()).coerceIn(0f, 1f)

    val togglePlayback = {
        try {
            audioPlayerManager.playAudio(audioUrl)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to play voice message", Toast.LENGTH_SHORT).show()
        }
    }

    val bubbleBg = if (isSentByCurrentUser) Color(0xFF1F6FEB) else Color(0xFF21262D)
    val contentColor = Color.White

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bubbleBg,
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .width(240.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Play/Pause button
            IconButton(
                onClick = { togglePlayback() },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isSentByCurrentUser) Color.White.copy(alpha = 0.2f) else Color(0xFF30363D),
                    contentColor = contentColor
                ),
                modifier = Modifier
                    .size(40.dp)
                    .testTag("play_pause_voice_btn")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(24.dp)
                )
            }

            // Progress Slider and Duration Text
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Slider(
                    value = progress,
                    onValueChange = { newProgress ->
                        if (isCurrentAudio && playbackState.totalDurationMs > 0) {
                            val seekPos = (newProgress * playbackState.totalDurationMs).toLong()
                            audioPlayerManager.seekTo(seekPos)
                        }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFF58A6FF),
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.height(20.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val posSec = (currentPosMs / 1000).toInt()
                    val totalSec = (totalDurMs / 1000).toInt()
                    Text(
                        text = String.format("%02d:%02d", posSec / 60, posSec % 60),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = String.format("%02d:%02d", totalSec / 60, totalSec % 60),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
