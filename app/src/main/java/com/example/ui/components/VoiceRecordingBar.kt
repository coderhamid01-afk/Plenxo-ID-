package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.media.AndroidAudioRecorder
import com.example.network.CatboxStorageManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun VoiceRecordingBar(
    modifier: Modifier = Modifier,
    chatId: String,
    receiverId: String,
    onVoiceRecorded: (android.net.Uri) -> Unit = {},
    onVoiceSent: (String, Int) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val recorder = remember { AndroidAudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var amplitudeValue by remember { mutableStateOf(0) }

    var timerJob by remember { mutableStateOf<Job?>(null) }
    var amplitudeJob by remember { mutableStateOf<Job?>(null) }

    fun startRecording() {
        try {
            val audioFile = recorder.createAudioFile()
            recordedFile = audioFile
            recorder.start(audioFile)
            isRecording = true
            recordingDuration = 0

            // Start duration timer
            timerJob?.cancel()
            timerJob = scope.launch {
                while (isRecording) {
                    delay(1000)
                    recordingDuration++
                }
            }

            // Start amplitude polling for live waveform
            amplitudeJob?.cancel()
            amplitudeJob = scope.launch {
                while (isRecording) {
                    amplitudeValue = recorder.getAmplitude()
                    delay(100)
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceRecordingBar", "Failed to start recording", e)
            Toast.makeText(context, "Microphone error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            isRecording = false
        }
    }

    fun stopAndSend() {
        timerJob?.cancel()
        amplitudeJob?.cancel()
        recorder.stop()
        isRecording = false

        val file = recordedFile
        val duration = recordingDuration
        if (file == null || !file.exists() || file.length() == 0L || duration < 1) {
            Toast.makeText(context, "Voice recording too short", Toast.LENGTH_SHORT).show()
            file?.delete()
            recordedFile = null
            return
        }

        val fileUri = android.net.Uri.fromFile(file)
        onVoiceRecorded(fileUri)
        onVoiceSent(fileUri.toString(), duration)
        recordedFile = null
    }

    fun cancelRecording() {
        timerJob?.cancel()
        amplitudeJob?.cancel()
        try {
            recorder.stop()
        } catch (e: Exception) {
            Log.w("VoiceRecordingBar", "Stop error on cancel: ${e.message}")
        }
        recordedFile?.delete()
        recordedFile = null
        isRecording = false
        Toast.makeText(context, "Recording discarded", Toast.LENGTH_SHORT).show()
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecording()
        } else {
            Toast.makeText(context, "Microphone permission is required to record audio", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkAndStartRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "scale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (isRecording) {
            // Live Recording Telemetry Bar
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF161B22),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Pulsing Red Dot
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Color.Red)
                    )

                    // Timer Display (e.g. 00:05)
                    val minutes = recordingDuration / 60
                    val seconds = recordingDuration % 60
                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    // Dynamic Waveform Indicator
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(8) { idx ->
                            val heightFraction = remember(amplitudeValue, idx) {
                                val base = ((amplitudeValue / 3000f) + (idx * 0.1f)).coerceIn(0.2f, 1.0f)
                                base
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(heightFraction)
                                    .background(Color(0xFF58A6FF), RoundedCornerShape(2.dp))
                            )
                        }
                    }

                    // Trash / Cancel Button
                    IconButton(
                        onClick = { cancelRecording() },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("trash_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Cancel recording",
                            tint = Color(0xFFDA3633)
                        )
                    }
                }
            }

            // Send Button
            FloatingActionButton(
                onClick = { stopAndSend() },
                containerColor = Color(0xFF238636),
                contentColor = Color.White,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("send_voice_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send voice message"
                )
            }
        } else {
            // Idle / Mic Button
            if (isUploading) {
                CircularProgressIndicator(
                    color = Color(0xFF58A6FF),
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF238636))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { checkAndStartRecording() },
                                onLongPress = { checkAndStartRecording() }
                            )
                        }
                        .testTag("mic_record_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Record Voice Note",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
