package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.CallLog
import com.example.ui.theme.PlenxoColors
import com.example.ui.theme.PlenxoTypography
import com.example.viewmodel.PlenxoViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    viewModel: PlenxoViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val callLogs by viewModel.callLogs.collectAsState()
    var selectedLogForCall by remember { mutableStateOf<CallLog?>(null) }

    LaunchedEffect(Unit) {
        viewModel.startListeningForCallLogs()
    }

    Box(modifier = modifier.fillMaxSize().background(PlenxoColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("call_history_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Call History",
                    style = PlenxoTypography.Title.copy(fontSize = 22.sp, color = Color.White),
                    fontWeight = FontWeight.Bold
                )
            }

            if (callLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "No Calls",
                            tint = Color.Gray.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Call Logs Found",
                            style = PlenxoTypography.Title.copy(color = Color.White, fontSize = 16.sp),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tapping on friends will allow you to make secure audio and video calls.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(items = callLogs, key = { it.callId }) { log ->
                        CallLogItemRow(
                            log = log,
                            onClick = { selectedLogForCall = log }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            thickness = 0.5.dp,
                            color = PlenxoColors.Divider
                        )
                    }
                }
            }
        }

        // Call Initiation Bottom Sheet/Dialog
        selectedLogForCall?.let { log ->
            AlertDialog(
                onDismissRequest = { selectedLogForCall = null },
                containerColor = Color(0xFF161B22),
                title = {
                    Text(
                        text = "Call ${log.peerName}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = "Would you like to start a call with ${log.peerName} (@${log.peerPlenxoId.ifBlank { "PX-xxxxxx" }})?",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = {
                                viewModel.initiateCall(log.peerUid, "AUDIO")
                                selectedLogForCall = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PlenxoColors.Primary),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Audio Call", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Audio", color = Color.White)
                        }

                        Button(
                            onClick = {
                                viewModel.initiateCall(log.peerUid, "VIDEO")
                                selectedLogForCall = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2EA043)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Video", color = Color.White)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedLogForCall = null }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }
    }
}

@Composable
fun CallLogItemRow(
    log: CallLog,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E2230))
                .border(1.dp, Color(0xFF30363D), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (log.peerPhotoUrl.isNotEmpty() && (log.peerPhotoUrl.startsWith("http") || log.peerPhotoUrl.startsWith("content://"))) {
                AsyncImage(
                    model = log.peerPhotoUrl,
                    contentDescription = "Peer Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(PlenxoColors.Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = log.peerName.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.peerName,
                style = PlenxoTypography.Body.copy(color = Color.White, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Direction Indicator
                val (directionIcon, directionColor) = when (log.direction) {
                    "INCOMING" -> Icons.Default.CallReceived to Color(0xFF2EA043) // Green
                    "OUTGOING" -> Icons.Default.CallMade to Color(0xFF58A6FF) // Blue
                    else -> Icons.Default.CallMissed to Color(0xFFFF7B72) // Red
                }

                Icon(
                    imageVector = directionIcon,
                    contentDescription = log.direction,
                    tint = directionColor,
                    modifier = Modifier.size(14.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = formatCallTimestamp(log.timestamp),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Icon(
                imageVector = if (log.callType == "VIDEO") Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = log.callType,
                tint = Color.LightGray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (log.direction == "MISSED") "Missed" else formatDuration(log.durationSeconds),
                color = if (log.direction == "MISSED") Color(0xFFFF7B72) else Color.LightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatCallTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02dm %02ds", m, s)
}
