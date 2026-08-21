package com.example.ui.chat.components

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Message
import com.example.model.MessageStatus
import com.example.ui.components.VoiceNoteBubble
import java.text.SimpleDateFormat
import java.util.*

/**
 * Modern ChatBubble component with:
 * 1. Inline message status indicators (SENDING clock, SENT ✓, DELIVERED ✓✓, READ ✓✓ blue, FAILED ⚠).
 * 2. Tap-to-retry logic for failed message dispatches.
 * 3. Support for Text, Image (local Uri & remote URL), and Voice Note media types.
 * 4. Zero blocking modal overlays.
 */
@Composable
fun ChatBubble(
    message: Message,
    isOutgoing: Boolean,
    primaryColor: Color = Color(0xFF58A6FF),
    onRetryClick: ((Message) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onImageClick: ((String) -> Unit)? = null
) {
    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }

    val bubbleBg = if (isOutgoing) {
        if (message.effectiveStatus == MessageStatus.FAILED) Color(0xFF451A1D) else primaryColor
    } else {
        Color(0xFF1F2937)
    }

    val textColor = Color.White
    val isFailed = message.effectiveStatus == MessageStatus.FAILED

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 10.dp),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .testTag("chat_bubble_${message.messageId}")
                .clip(bubbleShape)
                .then(
                    if (isFailed) Modifier.border(1.dp, Color(0xFFEF4444), bubbleShape)
                    else if (!isOutgoing) Modifier.border(1.dp, Color(0xFF374151), bubbleShape)
                    else Modifier
                )
                .clickable {
                    if (isFailed && onRetryClick != null) {
                        onRetryClick(message)
                    } else if (onLongClick != null) {
                        onLongClick()
                    }
                },
            shape = bubbleShape,
            color = bubbleBg,
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Content based on messageType
                when (message.messageType.uppercase()) {
                    "IMAGE" -> {
                        val imageSource = message.localUri ?: message.mediaUrl
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.3f))
                                .clickable {
                                    if (!imageSource.isNullOrEmpty() && onImageClick != null) {
                                        onImageClick(imageSource)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!imageSource.isNullOrEmpty()) {
                                AsyncImage(
                                    model = imageSource,
                                    contentDescription = "Shared Image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            
                            // Inline upload overlay on top of image
                            if (message.effectiveStatus == MessageStatus.SENDING) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        progress = { message.uploadProgress / 100f },
                                        modifier = Modifier.size(36.dp),
                                        color = Color.White,
                                        trackColor = Color.White.copy(alpha = 0.3f),
                                    )
                                }
                            }
                        }
                        if (message.messageText.isNotBlank() && message.messageText != "📷 Photo") {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = message.messageText,
                                color = textColor,
                                fontSize = 15.sp,
                                lineHeight = 21.sp
                            )
                        }
                    }
                    "VIDEO" -> {
                        val videoSource = message.localUri ?: message.mediaUrl
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F172A))
                                .clickable {
                                    if (!videoSource.isNullOrEmpty()) {
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                setDataAndType(Uri.parse(videoSource), "video/*")
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Cannot play video: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!videoSource.isNullOrEmpty()) {
                                AsyncImage(
                                    model = videoSource,
                                    contentDescription = "Video Thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            if (message.effectiveStatus == MessageStatus.SENDING) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(
                                            progress = { message.uploadProgress / 100f },
                                            modifier = Modifier.size(36.dp),
                                            color = Color.White,
                                            trackColor = Color.White.copy(alpha = 0.3f),
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("${message.uploadProgress}%", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    "FILE" -> {
                        val fileUrl = message.mediaUrl.ifBlank { message.localUri ?: "" }
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.25f))
                                .clickable {
                                    if (fileUrl.isNotBlank()) {
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(fileUrl))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Opening file link...", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = primaryColor.copy(alpha = 0.3f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.InsertDriveFile,
                                        contentDescription = "File Attachment",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.messageText.ifBlank { "Attachment File" },
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (message.effectiveStatus == MessageStatus.SENDING) "Uploading... ${message.uploadProgress}%" else "Tap to download/view",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    "TEXT_ASSET" -> {
                        val fileUrl = message.mediaUrl
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.25f))
                                .clickable {
                                    if (fileUrl.isNotBlank()) {
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(fileUrl))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Opening document...", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Large Document",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.messageText,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (message.effectiveStatus == MessageStatus.SENDING) "Uploading text asset... ${message.uploadProgress}%" else "Stored on Catbox • Tap to open",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    "VOICE", "AUDIO", "VOICE_NOTE" -> {
                        val audioUrl = message.localUri ?: message.mediaUrl
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val audioPlayerManager = remember(context) { com.example.media.AudioPlayerManager(context) }
                        VoiceNoteBubble(
                            audioUrl = audioUrl,
                            isSentByCurrentUser = isOutgoing,
                            audioPlayerManager = audioPlayerManager
                        )
                    }
                    else -> {
                        // Standard Text Message
                        Text(
                            text = message.messageText,
                            color = textColor,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Footer row: Timestamp & Inline Status Indicator
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val formattedTime = remember(message.timestamp) {
                        message.timestamp?.let {
                            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it))
                        } ?: ""
                    }

                    Text(
                        text = formattedTime,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )

                    if (isOutgoing) {
                        MessageStatusIcon(
                            status = message.effectiveStatus,
                            size = 12.dp
                        )
                    }
                }
            }
        }

        // Tap to retry caption for failed messages
        if (isOutgoing && isFailed) {
            Row(
                modifier = Modifier
                    .padding(top = 2.dp, end = 4.dp)
                    .clickable { onRetryClick?.invoke(message) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "Failed to send. Tap to retry",
                    color = Color(0xFFEF4444),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Custom Message Status Indicator component for chat bubbles with Canvas rendering:
 * 1. SENDING: Subtle hollow gray circle outline.
 * 2. SENT (Delivered): Clean gray circle ring (hollow center, thin gray border).
 * 3. SEEN (Read): Bright blue circle ring with a solid blue dot filled right in the center.
 * 4. FAILED: Red circle ring with red dot indicator.
 */
@Composable
fun MessageStatusIcon(
    status: MessageStatus,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp
) {
    val isRead = status == MessageStatus.READ
    val isSentOrDelivered = status == MessageStatus.SENT || status == MessageStatus.DELIVERED
    val isFailed = status == MessageStatus.FAILED

    // Smooth color transitions
    val ringColor by animateColorAsState(
        targetValue = when {
            isFailed -> Color(0xFFEF4444)
            isRead -> Color(0xFF2997FF) // Bright blue for SEEN / READ
            isSentOrDelivered -> Color(0xFF9CA3AF) // Clean gray ring for SENT / DELIVERED
            else -> Color(0xFF8E8E93).copy(alpha = 0.5f) // Subtle hollow gray outline for SENDING
        },
        animationSpec = tween(durationMillis = 300),
        label = "status_ring_color"
    )

    // Smooth spring scale for inner dot
    val innerDotScale by animateFloatAsState(
        targetValue = if (isRead) 1f else if (isFailed) 0.6f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "inner_dot_scale"
    )

    val strokeWidthDp = 1.3.dp

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Message status: ${status.name}" }
    ) {
        val strokeWidthPx = strokeWidthDp.toPx()
        val radius = (this.size.minDimension - strokeWidthPx) / 2f
        val centerPoint = center

        // 1. Outer Ring
        drawCircle(
            color = ringColor,
            radius = radius,
            center = centerPoint,
            style = Stroke(width = strokeWidthPx)
        )

        // 2. Solid Inner Center Dot (for SEEN / READ state or FAILED state)
        if (innerDotScale > 0f) {
            val maxInnerRadius = radius - strokeWidthPx * 1.5f
            val currentInnerRadius = (maxInnerRadius * innerDotScale).coerceAtLeast(0f)
            drawCircle(
                color = ringColor,
                radius = currentInnerRadius,
                center = centerPoint
            )
        }
    }
}
