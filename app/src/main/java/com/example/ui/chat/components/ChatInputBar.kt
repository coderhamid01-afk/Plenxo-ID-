package com.example.ui.chat.components

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.VoiceRecorderManager
import java.io.File

private const val MAX_BYTE_SIZE_150_MB = 150 * 1024 * 1024L // 150 MB
private const val MAX_BYTE_SIZE_170_MB = 170 * 1024 * 1024L // 170 MB
private const val MAX_IMAGES_BATCH = 15

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    modifier: Modifier = Modifier,
    primaryColor: Color = Color(0xFF1F6FEB),
    onSendText: (String) -> Unit,
    onSendImages: (List<Uri>) -> Unit,
    onSendVideo: (Uri) -> Unit = {},
    onSendFile: (Uri, String, Long) -> Unit,
    onSendVoiceNote: (File) -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }

    val recorderManager = remember(context) {
        VoiceRecorderManager(context).apply {
            onAutoSendVoiceNote = { voiceFile ->
                isRecording = false
                Toast.makeText(context, "Voice note auto-sent (100 MB limit reached)", Toast.LENGTH_SHORT).show()
                onSendVoiceNote(voiceFile)
            }
            onError = { errMsg ->
                isRecording = false
                Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 1. Multiple Image Picker Launcher (Max 15)
    val multipleImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_IMAGES_BATCH)
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        if (uris.size > MAX_IMAGES_BATCH) {
            Toast.makeText(context, "Maximum 15 images allowed per batch", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }

        onSendImages(uris)
    }

    // 2. Video Picker Launcher (Max 150 MB)
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        val fileSize = getFileSize(context, uri)
        if (fileSize > MAX_BYTE_SIZE_150_MB) {
            Toast.makeText(context, "Video size exceeds 150 MB limit.", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }

        onSendVideo(uri)
    }

    // Generic File Picker Launcher (Max 150 MB)
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        val fileSize = getFileSize(context, uri)
        val fileName = getFileName(context, uri)

        if (fileSize > MAX_BYTE_SIZE_150_MB) {
            Toast.makeText(context, "File size exceeds 150 MB limit.", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }

        onSendFile(uri, fileName, fileSize)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val tempFile = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.mp4")
            recorderManager.startRecording(tempFile)
            isRecording = true
        } else {
            Toast.makeText(context, "Microphone permission is required to record voice notes.", Toast.LENGTH_LONG).show()
        }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            multipleImagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            Toast.makeText(context, "Storage/Media permission is required for images.", Toast.LENGTH_LONG).show()
        }
    }

    val videoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            videoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        } else {
            Toast.makeText(context, "Storage/Media permission is required for videos.", Toast.LENGTH_LONG).show()
        }
    }

    val filePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            filePicker.launch("*/*")
        } else {
            Toast.makeText(context, "Storage permission is required for files.", Toast.LENGTH_LONG).show()
        }
    }

    var isFocused by remember { mutableStateOf(false) }

    val animatedElevation by animateDpAsState(
        targetValue = if (isFocused) 12.dp else 4.dp,
        animationSpec = tween(durationMillis = 250),
        label = "glass_elevation"
    )

    val animatedBorderAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.55f else 0.35f,
        animationSpec = tween(durationMillis = 250),
        label = "glass_border_alpha"
    )

    val glassBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = animatedBorderAlpha),
            Color.White.copy(alpha = 0.08f)
        )
    )

    val isAndroid12Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val glassContainerColor = if (isAndroid12Plus) {
        Color(0xFF1E1E2E).copy(alpha = 0.50f)
    } else {
        Color(0xFF1E1E2E).copy(alpha = 0.82f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(
                elevation = animatedElevation,
                shape = RoundedCornerShape(28.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = primaryColor.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(28.dp))
            .then(
                if (isAndroid12Plus) {
                    Modifier.blur(20.dp)
                } else {
                    Modifier
                }
            )
            .background(glassContainerColor)
            .border(
                width = 1.dp,
                brush = glassBorderBrush,
                shape = RoundedCornerShape(28.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        if (isRecording) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Recording voice note... (Max 100 MB)",
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 12.dp)
                )

                Row {
                    IconButton(
                        onClick = {
                            recorderManager.cancelRecording()
                            isRecording = false
                        }
                    ) {
                        Text("Cancel", color = Color.Gray, fontSize = 12.sp)
                    }

                    FloatingActionButton(
                        onClick = {
                            val recordedFile = recorderManager.stopRecording()
                            isRecording = false
                            if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                                onSendVoiceNote(recordedFile)
                            }
                        },
                        containerColor = Color.Red,
                        contentColor = Color.White,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop & Send")
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attach File Button (150 MB limit)
                IconButton(
                    onClick = { 
                        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_IMAGES
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                            filePicker.launch("*/*")
                        } else {
                            filePermissionLauncher.launch(permission)
                        }
                    },
                    modifier = Modifier.testTag("attach_file_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach File (Max 150 MB)",
                        tint = Color.White.copy(alpha = 0.90f)
                    )
                }

                // Pick Video Button (Max 150 MB)
                IconButton(
                    onClick = {
                        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_VIDEO
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                            videoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        } else {
                            videoPermissionLauncher.launch(permission)
                        }
                    },
                    modifier = Modifier.testTag("attach_video_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Attach Video (Max 150 MB)",
                        tint = Color.White.copy(alpha = 0.90f)
                    )
                }

                // Pick Multiple Images Button (Max 15 images)
                IconButton(
                    onClick = {
                        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            Manifest.permission.READ_MEDIA_IMAGES
                        } else {
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                            multipleImagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        } else {
                            mediaPermissionLauncher.launch(permission)
                        }
                    },
                    modifier = Modifier.testTag("attach_images_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Attach Images (Max 15)",
                        tint = Color.White.copy(alpha = 0.90f)
                    )
                }

                // Input Text Field
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Message...", color = Color.White.copy(alpha = 0.60f)) },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focusState ->
                            isFocused = focusState.isFocused
                        }
                        .testTag("chat_input_field"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = primaryColor
                    ),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Send Text OR Record Voice Button
                if (inputText.isNotBlank()) {
                    FloatingActionButton(
                        onClick = {
                            val rawText = inputText
                            val textBytes = rawText.toByteArray(Charsets.UTF_8).size

                            if (textBytes > MAX_BYTE_SIZE_170_MB) {
                                Toast.makeText(
                                    context,
                                    "Text message exceeds maximum size of 170 MB",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@FloatingActionButton
                            }

                            inputText = ""
                            onSendText(rawText)
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("send_message_button"),
                        containerColor = primaryColor,
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                val tempFile = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.mp4")
                                recorderManager.startRecording(tempFile)
                                isRecording = true
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.testTag("voice_record_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Record Voice Note",
                            tint = primaryColor
                        )
                    }
                }
            }
        }
    }
}

private fun getFileSize(context: Context, uri: Uri): Long {
    var size: Long = 0
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (sizeIndex != -1 && cursor.moveToFirst()) {
            size = cursor.getLong(sizeIndex)
        }
    }
    return size
}

private fun getFileName(context: Context, uri: Uri): String {
    var name = "file_${System.currentTimeMillis()}"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && cursor.moveToFirst()) {
            name = cursor.getString(nameIndex) ?: name
        }
    }
    return name
}
