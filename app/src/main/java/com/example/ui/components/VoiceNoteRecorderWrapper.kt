package com.example.ui.components

import com.example.R

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.media.AudioVoiceRecorder
import com.example.repository.VoiceNoteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * UI State of the Voice Note Recorder.
 */
sealed interface VoiceNoteState {
    object Idle : VoiceNoteState
    object Recording : VoiceNoteState
    object Uploading : VoiceNoteState
    data class Success(val url: String) : VoiceNoteState
    data class Error(val message: String) : VoiceNoteState
}

/**
 * A highly interactive, premium Material 3 Microphone Button Wrapper for voice note recording.
 * Integrates permissions, AudioVoiceRecorder, Catbox hosting, and Firestore message persistence.
 */
@Composable
fun VoiceNoteRecorderWrapper(
    modifier: Modifier = Modifier,
    chatId: String,
    receiverId: String,
    onStateChanged: (VoiceNoteState) -> Unit = {},
    onVoiceNoteSent: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Core states
    var uiState by remember { mutableStateOf<VoiceNoteState>(VoiceNoteState.Idle) }
    var recordingDurationSeconds by remember { mutableStateOf(0) }
    var isHoldToRecordMode by remember { mutableStateOf(true) }
    
    val voiceRecorder = remember { AudioVoiceRecorder(context) }
    val repository = remember { VoiceNoteRepository() }
    
    var currentFile by remember { mutableStateOf<File?>(null) }
    var timerJob by remember { mutableStateOf<Job?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Synchronize external state listener
    LaunchedEffect(uiState) {
        onStateChanged(uiState)
    }

    // Timer logic when recording
    fun startTimer() {
        recordingDurationSeconds = 0
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                delay(1000)
                recordingDurationSeconds++
            }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    // Recording lifecycle triggers
    fun initiateStartRecording() {
        try {
            val cacheFile = File.createTempFile("voice_note_", ".m4a", context.cacheDir)
            currentFile = cacheFile
            voiceRecorder.startRecording(cacheFile)
            uiState = VoiceNoteState.Recording
            startTimer()
        } catch (e: Exception) {
            Log.e("VoiceNoteRecorder", "Failed to start voice note recording", e)
            Toast.makeText(context, "Failed to start recording: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            uiState = VoiceNoteState.Idle
        }
    }

    fun stopAndUploadVoiceNote() {
        stopTimer()
        voiceRecorder.stopRecording()
        
        val file = currentFile
        if (file == null || !file.exists() || file.length() == 0L) {
            uiState = VoiceNoteState.Idle
            Toast.makeText(context, "Voice note was too short or empty.", Toast.LENGTH_SHORT).show()
            return
        }

        uiState = VoiceNoteState.Uploading
        scope.launch {
            try {
                // Upload to Catbox & sync with Firestore
                val directUrl = repository.uploadAndSendVoiceNote(context, file, chatId, receiverId)
                uiState = VoiceNoteState.Success(directUrl)
                onVoiceNoteSent(directUrl)
                Toast.makeText(context, "Voice Note uploaded & sent successfully!", Toast.LENGTH_SHORT).show()
                uiState = VoiceNoteState.Idle
            } catch (e: Exception) {
                Log.e("VoiceNoteRecorder", "Upload or Database sync failed", e)
                uiState = VoiceNoteState.Error(e.localizedMessage ?: "Unknown error")
                Toast.makeText(context, "Failed to send: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                uiState = VoiceNoteState.Idle
            } finally {
                currentFile = null
            }
        }
    }

    fun cancelActiveRecording() {
        stopTimer()
        try {
            voiceRecorder.stopRecording()
        } catch (e: Exception) {
            // Ignore if already stopped
        }
        currentFile?.let {
            if (it.exists()) {
                it.delete()
            }
        }
        currentFile = null
        uiState = VoiceNoteState.Idle
        Toast.makeText(context, "Recording discarded", Toast.LENGTH_SHORT).show()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                if (uiState == VoiceNoteState.Recording) {
                    cancelActiveRecording()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (uiState == VoiceNoteState.Recording) {
                cancelActiveRecording()
            }
        }
    }

    // Permission Launcher
    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initiateStartRecording()
        } else {
            Toast.makeText(context, "Microphone permission required for recording voice notes", Toast.LENGTH_LONG).show()
        }
    }

    fun checkAndStartRecording() {
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            initiateStartRecording()
        } else {
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Pulsing animations for active recording state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Upper display zone showing Recording/Uploading telemetry
        AnimatedVisibility(
            visible = uiState != VoiceNoteState.Idle,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF161B22),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (uiState == VoiceNoteState.Recording) {
                        // Blinking red indicator dot
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(Color.Red)
                        )
                        
                        Text(
                            text = String.format("Recording: %02d:%02d", recordingDurationSeconds / 60, recordingDurationSeconds % 60),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // Simulated visual equalizer waveform
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(14.dp)
                        ) {
                            val animHeights = List(5) { index ->
                                val waveAnim = rememberInfiniteTransition(label = "wave_$index")
                                waveAnim.animateFloat(
                                    initialValue = 0.2f,
                                    targetValue = 1.0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(250 + (index * 80), easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "wave_height_$index"
                                )
                            }
                            animHeights.forEach { heightVal ->
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .fillMaxHeight(heightVal.value)
                                        .background(Color(0xFF58A6FF))
                                )
                            }
                        }
                    } else if (uiState == VoiceNoteState.Uploading) {
                        CircularProgressIndicator(
                            color = Color(0xFF58A6FF),
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(stringResource(id = R.string.str_hosting_audio_syncing_db),
                            color = Color(0xFF8B949E),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Active control buttons (Stop / Cancel)
        if (uiState == VoiceNoteState.Recording) {
            Row(
                modifier = Modifier.padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel Button
                IconButton(
                    onClick = { cancelActiveRecording() },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFF21262D),
                        contentColor = Color.Red
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = "Discard Voice Note",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Stop & Send Button
                IconButton(
                    onClick = { stopAndUploadVoiceNote() },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFF58A6FF),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Voice Note",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Main Mic Click Trigger & Mode selector
        if (uiState != VoiceNoteState.Recording) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Large tactile interactive microphone button
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            when (uiState) {
                                is VoiceNoteState.Uploading -> Color(0xFF21262D)
                                else -> Color(0xFF58A6FF)
                            }
                        )
                        .pointerInput(uiState) {
                            detectTapGestures(
                                onLongPress = {
                                    if (uiState == VoiceNoteState.Idle) {
                                        isHoldToRecordMode = true
                                        checkAndStartRecording()
                                    }
                                },
                                onTap = {
                                    if (uiState == VoiceNoteState.Idle) {
                                        isHoldToRecordMode = false
                                        checkAndStartRecording()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState == VoiceNoteState.Uploading) {
                        CircularProgressIndicator(
                            color = Color(0xFF58A6FF),
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Record Voice Note",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(stringResource(id = R.string.str_tap_once_to_toggle_record),
                color = Color(0xFF8B949E),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
