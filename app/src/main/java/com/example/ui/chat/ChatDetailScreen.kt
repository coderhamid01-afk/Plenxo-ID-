package com.example.ui.chat

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import com.example.util.VideoAutoDownloader
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.media.AndroidAudioRecorder
import com.example.model.Message
import com.example.ui.WallpaperRenderer
import com.example.ui.chat.components.ChatBubble
import com.example.ui.components.ProfileImageWithRing
import com.example.ui.theme.PlenxoColors
import com.example.util.PermissionManager
import com.example.viewmodel.PlenxoScreen
import com.example.viewmodel.PlenxoViewModel
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/* ============================================================================================
 *  ChatDetailScreen
 * --------------------------------------------------------------------------------------------
 *  Complete rebuild of the chat conversation screen. Highlights of this refactor:
 *
 *  1. INPUT BAR STABILITY  — the previous input bar stretched/distorted because the mic ↔ send
 *     toggle and the voice-recording telemetry row were unconstrained children living inside a
 *     `wrapContentHeight()` chain, so any internal content change (recording start/stop, the
 *     mic/send icon swap, the reply banner appearing) forced the whole bottom bar to reflow.
 *     Here, the trailing action button lives inside a fixed 44.dp box, the recording telemetry
 *     row is height-matched to a single text-field line, and `animateContentSize()` on the
 *     single outer container smooths every remaining size change instead of letting it snap.
 *
 *  2. NO MORE "INSTANT FAILED" MESSAGES — the optimistic SENDING → SENT flow in the ViewModel
 *     was already correct, but the local-DB listener used to hard-replace the in-memory message
 *     list on every emission, which could wipe out a just-sent optimistic message before it was
 *     persisted. See the companion fix in `PlenxoViewModel.startListeningForMessages`, which now
 *     merges persisted messages with any still-SENDING optimistic ones instead of overwriting.
 *
 *  3. NO MORE "PITCH BLACK VOID" — `WallpaperRenderer` silently draws nothing for the default/
 *     unset wallpaper id, which left a flat near-black `Color(0xFF0D1117)` box behind it. The
 *     screen now always renders a subtle two-tone dark gradient first, and only layers a custom
 *     wallpaper on top when the user actually picked one.
 *
 *  4. Self-contained modern input bar (rounded pill text field, dynamic mic/send button, inline
 *     voice recorder with live waveform) built directly in this file, and buttery auto-scroll to
 *     the newest message for both text and voice sends.
 * ============================================================================================ */

private val AccentGreen = Color(0xFF238636)
private val InputBarSurface = Color(0xFF161B22)
private val FieldSurface = Color(0xFF21262D)
private val MutedText = Color(0xFF8B949E)
private val DividerColor = Color(0xFF30363D)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ChatDetailScreen(
    viewModel: PlenxoViewModel,
    primaryColor: Color,
    permissionManager: PermissionManager
) {
    val context = LocalContext.current

    val messages by viewModel.messages.collectAsState()
    val currentUserId = viewModel.currentUserId
    val recipientName by viewModel.currentChatRecipientName.collectAsState()
    val chatId by viewModel.currentChatId.collectAsState()
    val recipientUid by viewModel.currentChatRecipientUid.collectAsState()
    val typingUsers by viewModel.typingUsers.collectAsState()
    val replyMessageState by viewModel.replyToMessage.collectAsState()
    val usersCache by viewModel.usersCache.collectAsState()
    val userPresences by viewModel.userPresences.collectAsState()
    val selectedWallpaperId by viewModel.selectedChatWallpaper.collectAsState()

    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    var showAttachmentSheet by remember { mutableStateOf(false) }

    // Photo picker launcher for non-blocking media send (Max 15)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                if (uris.size > 15) {
                    Toast.makeText(context, "You can send a maximum of 15 images at a time.", Toast.LENGTH_LONG).show()
                } else {
                    viewModel.sendMultipleImages(chatId = chatId, uris = uris, receiverId = recipientUid)
                }
            }
        }
    )

    // Video picker launcher (Max 150 MB)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                viewModel.sendVideoMessage(chatId = chatId, videoUri = uri, receiverId = recipientUid)
            }
        }
    )

    // File picker launcher (Max 150 MB)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                val fileSize = getFileSize(context, uri)
                val fileName = getFileName(context, uri)
                viewModel.sendFileMessage(
                    chatId = chatId,
                    fileUri = uri,
                    fileName = fileName,
                    fileSize = fileSize,
                    receiverId = recipientUid
                )
            }
        }
    )

    // Auto-save received incoming video messages to Camera Roll
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(messages) {
        messages.filter { 
            it.senderId != currentUserId && 
            it.messageType.equals("VIDEO", ignoreCase = true) && 
            it.mediaUrl.isNotBlank() 
        }.forEach { videoMsg ->
            VideoAutoDownloader.checkAndAutoSaveVideo(
                context = context,
                scope = coroutineScope,
                messageId = videoMsg.messageId,
                senderId = videoMsg.senderId,
                currentUserId = currentUserId,
                messageType = videoMsg.messageType,
                mediaUrl = videoMsg.mediaUrl
            )
        }
    }

    // Smooth auto-scroll to bottom whenever the list grows -- fires for outgoing text,
    // outgoing voice notes/images (optimistic append) and incoming messages alike.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val mappingFlow = remember(chatId) { viewModel.getWallpaperMappingForConversation(chatId) }
    val mapping by mappingFlow.collectAsState(initial = null)
    val activeWallpaperId = mapping?.activeWallpaperId ?: selectedWallpaperId
    val hasCustomWallpaper = activeWallpaperId.isNotBlank() &&
        activeWallpaperId != "DEFAULT" &&
        activeWallpaperId != "NONE"

    val recipientUser = usersCache[recipientUid]
    val profilePicUrl = recipientUser?.profilePicUrl ?: ""
    val profileRingId = recipientUser?.profileRingId ?: "none"
    val presenceMap = userPresences[recipientUid] ?: emptyMap()
    val presenceStatus = (presenceMap["status"] as? String) ?: (presenceMap["state"] as? String) ?: "offline"

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Polished dark-gradient fallback instead of a flat near-black box, so the
            // screen never looks like an empty void when no wallpaper is selected.
            .background(
                Brush.verticalGradient(
                    colors = listOf(PlenxoColors.Background, Color(0xFF131824), PlenxoColors.Background)
                )
            )
    ) {
        if (hasCustomWallpaper) {
            WallpaperRenderer(activeWallpaperId)
        }

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.navigateToScreen(PlenxoScreen.HOME) },
                            modifier = Modifier.testTag("chat_detail_back_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    title = {
                        val recipientPlenxoId = recipientUser?.plenxoId?.ifBlank { recipientUser?.userCode.orEmpty() } ?: ""
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (recipientUid.isNotBlank()) {
                                        viewModel.openUserProfile(recipientUid)
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("chat_recipient_avatar")
                                    .clickable {
                                        if (recipientUid.isNotBlank()) {
                                            viewModel.openUserProfile(recipientUid)
                                        }
                                    }
                            ) {
                                ProfileImageWithRing(
                                    imageUrl = profilePicUrl,
                                    profileRingId = profileRingId,
                                    modifier = Modifier.fillMaxSize(),
                                    ringBorderWidth = 5
                                )
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (presenceStatus == "online") Color(0xFF34C759) else Color(0xFF8E8E93))
                                        .border(1.5.dp, Color(0xFF131824), CircleShape)
                                        .align(Alignment.BottomEnd)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = recipientName.ifEmpty { "Chat" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "End-to-End Encrypted",
                                        tint = primaryColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                val isTyping = typingUsers.containsKey(chatId)
                                val cleanRecipientPxId = recipientPlenxoId.trim().removePrefix("@").removePrefix("#")
                                val plenxoIdDisplay = if (cleanRecipientPxId.isNotBlank()) {
                                    if (cleanRecipientPxId.startsWith("PX-", ignoreCase = true)) {
                                        "PX-${cleanRecipientPxId.removePrefix("PX-").removePrefix("px-")}"
                                    } else if (cleanRecipientPxId.length == 6 && cleanRecipientPxId.all { it.isDigit() }) {
                                        "PX-$cleanRecipientPxId"
                                    } else {
                                        "PX-$cleanRecipientPxId"
                                    }
                                } else ""
                                Text(
                                    text = when {
                                        isTyping -> "Typing..."
                                        plenxoIdDisplay.isNotEmpty() -> "$plenxoIdDisplay • ${if (presenceStatus == "online") "Online" else "Offline"}"
                                        presenceStatus == "online" -> "Online"
                                        else -> "Offline"
                                    },
                                    fontSize = 12.sp,
                                    color = if (isTyping) primaryColor else Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (recipientUid.isNotEmpty()) {
                                viewModel.initiateCall(recipientUid, "AUDIO")
                            } else {
                                Toast.makeText(context, "Error: recipient UID is empty", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Phone, contentDescription = "Voice Call", tint = Color.White)
                        }
                        IconButton(onClick = {
                            if (recipientUid.isNotEmpty()) {
                                viewModel.initiateCall(recipientUid, "VIDEO")
                            } else {
                                Toast.makeText(context, "Error: recipient UID is empty", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = Color.White)
                        }
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = InputBarSurface.copy(alpha = 0.95f)
                    )
                )
            },
            bottomBar = {
                ChatInputBar(
                    inputText = inputText,
                    onInputTextChange = {
                        inputText = it
                        viewModel.setTypingStatus(it.isNotBlank())
                    },
                    replyMessage = replyMessageState,
                    onCancelReply = { viewModel.replyToMessage.value = null },
                    primaryColor = primaryColor,
                    permissionManager = permissionManager,
                    onAttachClick = {
                        showAttachmentSheet = true
                    },
                    onSendText = {
                        val textToSend = inputText.trim()
                        if (textToSend.isNotEmpty()) {
                            inputText = "" // Instant optimistic UI clear
                            viewModel.setTypingStatus(false)
                            viewModel.sendMessage(
                                chatId = chatId,
                                text = textToSend,
                                receiverId = recipientUid
                            )
                        }
                    },
                    onVoiceRecorded = { voiceUri ->
                        viewModel.sendVoiceNoteMessage(
                            chatId = chatId,
                            voiceUri = voiceUri,
                            receiverId = recipientUid
                        )
                    }
                )
            }
        ) { paddingValues ->
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Secure Chat",
                            modifier = Modifier.size(64.dp),
                            tint = primaryColor.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "End-to-End Encrypted",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Messages, voice notes, and media are fully encrypted. Only you and $recipientName can read them.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
                ) {
                    items(
                        items = messages,
                        key = { it.messageId.ifBlank { "${it.senderId}_${it.timestamp ?: 0L}" } }
                    ) { msg ->
                        val isOutgoing = msg.senderId == currentUserId
                        Box(modifier = Modifier.animateItem()) {
                            ChatBubble(
                                message = msg,
                                isOutgoing = isOutgoing,
                                primaryColor = primaryColor,
                                onRetryClick = { failedMsg -> viewModel.retryFailedMessage(failedMsg) }
                            )
                        }
                    }
                }
            }
        }

        if (showAttachmentSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAttachmentSheet = false },
                containerColor = Color(0xFF161B22),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "Share Content",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAttachmentSheet = false
                                permissionManager.requestMediaPermission { isGranted ->
                                    if (isGranted) {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    } else {
                                        Toast.makeText(context, "Storage/Media permission is required for images.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Photos", tint = primaryColor, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Photos", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                            Text("Send up to 15 images at once", color = Color.Gray, fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAttachmentSheet = false
                                permissionManager.requestMediaPermission { isGranted ->
                                    if (isGranted) {
                                        videoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    } else {
                                        Toast.makeText(context, "Storage/Media permission is required for videos.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Video", tint = primaryColor, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Video", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                            Text("Send video file up to 150 MB", color = Color.Gray, fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAttachmentSheet = false
                                permissionManager.requestMediaPermission { isGranted ->
                                    if (isGranted) {
                                        filePickerLauncher.launch("*/*")
                                    } else {
                                        Toast.makeText(context, "Storage permission is required for files.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Document", tint = primaryColor, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Document or File", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                            Text("Send any file up to 150 MB", color = Color.Gray, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

/* ============================================================================================
 *  Bottom input bar
 * ============================================================================================ */

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ChatInputBar(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    replyMessage: Message?,
    onCancelReply: () -> Unit,
    primaryColor: Color,
    permissionManager: PermissionManager,
    onAttachClick: () -> Unit,
    onSendText: () -> Unit,
    onVoiceRecorded: (Uri) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val recorderState = rememberVoiceRecorderState()
    val isRecording = recorderState.phase == VoiceRecordingPhase.RECORDING

    // Duration timer while recording (1s ticks)
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (true) {
                delay(1000)
                recorderState.tickSecond()
            }
        }
    }
    // Live amplitude polling for the waveform while recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (true) {
                recorderState.pollAmplitude()
                delay(100)
            }
        }
    }

    fun beginRecording() {
        permissionManager.requestMicrophonePermission { granted ->
            if (granted) {
                try {
                    val file = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.m4a")
                    recorderState.start(file)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } catch (e: Exception) {
                    Log.e("ChatInputBar", "Failed to start recording", e)
                    Toast.makeText(context, "Microphone error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Microphone permission is required to record audio", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun stopAndSendRecording() {
        val file = recorderState.stopAndConsumeFile(minDurationSeconds = 1)
        if (file == null) {
            Toast.makeText(context, "Voice recording too short", Toast.LENGTH_SHORT).show()
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onVoiceRecorded(Uri.fromFile(file))
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

    // Liquid Glass Container with hardware backdrop blur & frosted gradient border
    Box(
        modifier = Modifier
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
            .animateContentSize(animationSpec = tween(180))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            AnimatedVisibility(
                visible = replyMessage != null,
                enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
            ) {
                replyMessage?.let { reply ->
                    ReplyPreviewBanner(reply = reply, primaryColor = primaryColor, onCancel = onCancelReply)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leading area: either the attach button + text field, or the recording
                // telemetry row. Both variants are height-matched so swapping between them
                // never distorts the surrounding row.
                AnimatedContent(
                    targetState = isRecording,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        (fadeIn(tween(150)) togetherWith fadeOut(tween(150)))
                    },
                    label = "input_or_recording"
                ) { recording ->
                    if (recording) {
                        RecordingTelemetryRow(
                            elapsedSeconds = recorderState.elapsedSeconds,
                            amplitude = recorderState.amplitude,
                            onCancel = {
                                recorderState.cancel()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onAttachClick,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("attach_media_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = "Attach Media",
                                    tint = Color.White.copy(alpha = 0.90f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(2.dp))

                            OutlinedTextField(
                                value = inputText,
                                onValueChange = onInputTextChange,
                                placeholder = {
                                    Text("Type a message...", color = Color.White.copy(alpha = 0.60f), fontSize = 14.sp)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { focusState ->
                                        isFocused = focusState.isFocused
                                    }
                                    .testTag("chat_input_field"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    cursorColor = AccentGreen
                                ),
                                shape = RoundedCornerShape(24.dp),
                                maxLines = 4
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Fixed-size slot: whatever AnimatedContent puts inside here is centered and
                // clipped to exactly 44.dp, so the mic ↔ send crossfade can never ripple out
                // into the rest of the row's measured size.
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = when {
                            isRecording -> InputAction.SEND_VOICE
                            inputText.isNotBlank() -> InputAction.SEND_TEXT
                            else -> InputAction.RECORD
                        },
                        transitionSpec = {
                            (fadeIn(tween(150)) + scaleIn(initialScale = 0.85f)) togetherWith
                                (fadeOut(tween(150)) + scaleOut(targetScale = 0.85f))
                        },
                        label = "send_mic_transition"
                    ) { action ->
                        when (action) {
                            InputAction.SEND_TEXT -> RoundActionButton(
                                icon = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                testTag = "send_message_button",
                                containerColor = AccentGreen,
                                onClick = onSendText
                            )
                            InputAction.SEND_VOICE -> RoundActionButton(
                                icon = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send voice message",
                                testTag = "send_voice_button",
                                containerColor = AccentGreen,
                                onClick = { stopAndSendRecording() }
                            )
                            InputAction.RECORD -> RoundActionButton(
                                icon = Icons.Default.Mic,
                                contentDescription = "Record Voice Note",
                                testTag = "mic_record_button",
                                containerColor = AccentGreen,
                                onClick = { beginRecording() }
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class InputAction { RECORD, SEND_TEXT, SEND_VOICE }

@Composable
private fun RoundActionButton(
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    containerColor: Color,
    onClick: () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .testTag(testTag),
        containerColor = containerColor,
        contentColor = Color.White,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ReplyPreviewBanner(
    reply: Message,
    primaryColor: Color,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(FieldSurface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .height(32.dp)
                .background(primaryColor, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Replying to message", fontSize = 11.sp, color = primaryColor, fontWeight = FontWeight.SemiBold)
            Text(reply.messageText, fontSize = 13.sp, color = Color.White, maxLines = 1)
        }
        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Cancel reply", tint = Color.Gray, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun RecordingTelemetryRow(
    elapsedSeconds: Int,
    amplitude: Int,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = FieldSurface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444))
            )

            val minutes = elapsedSeconds / 60
            val seconds = elapsedSeconds % 60
            Text(
                text = String.format(Locale.US, "%02d:%02d", minutes, seconds),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(16.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(8) { idx ->
                    val heightFraction = remember(amplitude, idx) {
                        ((amplitude / 3000f) + (idx * 0.1f)).coerceIn(0.2f, 1.0f)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(heightFraction)
                            .background(Color(0xFF58A6FF), RoundedCornerShape(2.dp))
                    )
                }
            }

            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("trash_voice_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Cancel recording",
                    tint = Color(0xFFDA3633),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/* ============================================================================================
 *  Inline voice-recorder state model
 * --------------------------------------------------------------------------------------------
 *  Isolating recording state in its own holder (rather than a handful of booleans scattered
 *  across the composable) is what lets the recording UI mount/unmount cleanly without ever
 *  touching the rest of the input bar's layout.
 * ============================================================================================ */

private enum class VoiceRecordingPhase { IDLE, RECORDING }

private class VoiceRecorderState(private val recorder: AndroidAudioRecorder) {
    var phase by mutableStateOf(VoiceRecordingPhase.IDLE)
        private set
    var elapsedSeconds by mutableIntStateOf(0)
        private set
    var amplitude by mutableIntStateOf(0)
        private set

    private var recordedFile: File? = null

    fun start(file: File) {
        recorder.start(file)
        recordedFile = file
        elapsedSeconds = 0
        amplitude = 0
        phase = VoiceRecordingPhase.RECORDING
    }

    fun tickSecond() {
        if (phase == VoiceRecordingPhase.RECORDING) elapsedSeconds++
    }

    fun pollAmplitude() {
        if (phase == VoiceRecordingPhase.RECORDING) amplitude = recorder.getAmplitude()
    }

    /** Stops recording and returns the finished file, or null if it was too short/invalid. */
    fun stopAndConsumeFile(minDurationSeconds: Int = 1): File? {
        recorder.stop()
        phase = VoiceRecordingPhase.IDLE
        val file = recordedFile
        recordedFile = null
        return if (file != null && file.exists() && file.length() > 0L && elapsedSeconds >= minDurationSeconds) {
            file
        } else {
            file?.delete()
            null
        }
    }

    fun cancel() {
        try {
            recorder.stop()
        } catch (e: Exception) {
            Log.w("VoiceRecorderState", "Stop-on-cancel failed: ${e.message}")
        }
        recordedFile?.delete()
        recordedFile = null
        phase = VoiceRecordingPhase.IDLE
    }
}

@Composable
private fun rememberVoiceRecorderState(): VoiceRecorderState {
    val context = LocalContext.current
    val recorder = remember(context) { AndroidAudioRecorder(context) }
    return remember(recorder) { VoiceRecorderState(recorder) }
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
