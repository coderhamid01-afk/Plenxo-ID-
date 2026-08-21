package com.example.ui.call

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.webrtc.CallViewModel
import org.webrtc.SurfaceViewRenderer
import kotlinx.coroutines.delay

@Composable
fun OutgoingCallScreen(
    viewModel: CallViewModel,
    onCallEnded: () -> Unit
) {
    val callStatus by viewModel.callStatus.collectAsState()
    var isExiting by remember { mutableStateOf(false) }

    LaunchedEffect(callStatus) {
        if (callStatus == "rejected" || callStatus == "timeout" || callStatus == "busy") {
            delay(1500)
            isExiting = true
            delay(300)
            onCallEnded()
        } else if (callStatus == "accepted") {
            // Transition to active call screen handled by parent
        }
    }

    val displayStatus = when (callStatus) {
        "calling" -> "Calling..."
        "ringing" -> "Ringing..."
        "rejected" -> "Call Declined"
        "timeout" -> "No Answer"
        "busy" -> "User is busy"
        else -> callStatus.replaceFirstChar { it.uppercase() }
    }

    AnimatedVisibility(
        visible = !isExiting,
        enter = scaleIn(tween(300)) + fadeIn(tween(300)),
        exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(300))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF131824)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 100.dp)
            ) {
                AsyncImage(
                    model = viewModel.peerAvatar,
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = viewModel.peerName,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = displayStatus,
                    color = Color.LightGray,
                    fontSize = 18.sp
                )
            }

            IconButton(
                onClick = {
                    viewModel.endCall()
                    isExiting = true
                },
                modifier = Modifier
                    .padding(bottom = 60.dp)
                    .size(72.dp)
                    .background(Color.Red, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
fun IncomingCallScreen(
    viewModel: CallViewModel,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    var isExiting by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = !isExiting,
        enter = scaleIn(tween(300)) + fadeIn(tween(300)),
        exit = scaleOut(tween(200)) + fadeOut(tween(200))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF131824)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 100.dp)
            ) {
                AsyncImage(
                    model = viewModel.peerAvatar,
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = viewModel.peerName,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (viewModel.isVideoCall) "Incoming Video Call" else "Incoming Audio Call",
                    color = Color.LightGray,
                    fontSize = 18.sp
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 60.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = {
                        isExiting = true
                        viewModel.rejectIncomingCall(viewModel.callId)
                        onReject()
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color.Red, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Reject Call",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = {
                        viewModel.answerIncomingCall(
                            viewModel.callId,
                            viewModel.currentUserId,
                            viewModel.peerId,
                            viewModel.peerName,
                            viewModel.peerAvatar,
                            viewModel.isVideoCall
                        )
                        onAccept()
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(0xFF4CAF50), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Accept Call",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveCallScreen(
    viewModel: CallViewModel,
    onCallEnded: () -> Unit
) {
    val callStatus by viewModel.callStatus.collectAsState()
    val callDuration by viewModel.callDuration.collectAsState()
    val isMicMuted by viewModel.isMicMuted.collectAsState()
    val isVideoEnabled by viewModel.isVideoEnabled.collectAsState()
    val isSpeakerphoneOn by viewModel.isSpeakerphoneOn.collectAsState()
    
    var isExiting by remember { mutableStateOf(false) }

    LaunchedEffect(callStatus) {
        if (callStatus == "ended") {
            isExiting = true
            delay(300)
            onCallEnded()
        }
    }

    val formatDuration = { seconds: Long ->
        val m = seconds / 60
        val s = seconds % 60
        String.format("%02d:%02d", m, s)
    }

    AnimatedVisibility(
        visible = !isExiting,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (viewModel.isVideoCall && isVideoEnabled) {
                // Remote Video (Full Screen)
                AndroidView(
                    factory = { context ->
                        SurfaceViewRenderer(context).apply {
                            init(viewModel.initEglBaseContext(), null)
                            setEnableHardwareScaler(true)
                            setMirror(false)
                            viewModel.attachRemoteVideo(this)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Local Video (Overlay)
                AndroidView(
                    factory = { context ->
                        SurfaceViewRenderer(context).apply {
                            init(viewModel.initEglBaseContext(), null)
                            setZOrderMediaOverlay(true)
                            setEnableHardwareScaler(true)
                            setMirror(true)
                            viewModel.attachLocalVideo(this)
                        }
                    },
                    modifier = Modifier
                        .padding(16.dp)
                        .size(100.dp, 150.dp)
                        .align(Alignment.TopEnd)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                )
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = viewModel.peerAvatar,
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = viewModel.peerName,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatDuration(callDuration),
                        color = Color.LightGray,
                        fontSize = 18.sp
                    )
                }
            }

            // Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.toggleMic() },
                    modifier = Modifier
                        .size(56.dp)
                        .background(if (isMicMuted) Color.White else Color(0x33FFFFFF), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Toggle Mic",
                        tint = if (isMicMuted) Color.Black else Color.White
                    )
                }

                if (viewModel.isVideoCall) {
                    IconButton(
                        onClick = { viewModel.toggleVideo() },
                        modifier = Modifier
                            .size(56.dp)
                            .background(if (!isVideoEnabled) Color.White else Color(0x33FFFFFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (!isVideoEnabled) Icons.Default.VideocamOff else Icons.Default.Videocam,
                            contentDescription = "Toggle Video",
                            tint = if (!isVideoEnabled) Color.Black else Color.White
                        )
                    }

                    IconButton(
                        onClick = { viewModel.switchCamera() },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0x33FFFFFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Switch Camera",
                            tint = Color.White
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.toggleSpeakerphone() },
                    modifier = Modifier
                        .size(56.dp)
                        .background(if (isSpeakerphoneOn) Color.White else Color(0x33FFFFFF), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Toggle Speaker",
                        tint = if (isSpeakerphoneOn) Color.Black else Color.White
                    )
                }

                IconButton(
                    onClick = {
                        viewModel.endCall()
                        isExiting = true
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.Red, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
