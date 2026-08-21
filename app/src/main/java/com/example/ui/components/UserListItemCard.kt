package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Action states for [UserListItemCard]
 */
sealed class UserActionState {
    object None : UserActionState()
    object Chevron : UserActionState()
    data class Add(val onClick: () -> Unit) : UserActionState()
    object Pending : UserActionState()
    object Accepted : UserActionState()
    data class AcceptReject(val onAccept: () -> Unit, val onReject: () -> Unit) : UserActionState()
}

/**
 * Reusable User Item Card Component for Search Results, Main Chat List, and Chat Requests.
 */
@Composable
fun UserListItemCard(
    displayName: String,
    plenxoId: String,
    profilePicUrl: String?,
    modifier: Modifier = Modifier,
    isOnline: Boolean = false,
    profileRingId: String = "none",
    avatarSize: Dp = 52.dp,
    onClick: (() -> Unit)? = null,
    actionSlot: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Side: Profile Picture with Ring + Fallback Initials + Online Dot
        ProfileRingBox(
            ringId = profileRingId,
            ringPadding = 2.dp,
            borderWidth = 3.dp
        ) {
            Box(
                modifier = Modifier.size(avatarSize),
                contentAlignment = Alignment.Center
            ) {
                val hasValidPic = !profilePicUrl.isNullOrBlank() && (profilePicUrl.startsWith("http://") || profilePicUrl.startsWith("https://"))

                if (hasValidPic) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(profilePicUrl)
                            .crossfade(true)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .build(),
                        contentDescription = "$displayName's Profile Picture",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Fallback Initials Circle
                    val initials = getInitials(displayName, plenxoId)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            fontSize = (avatarSize.value * 0.38f).sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Online Indicator Dot
                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .testTag("online_indicator")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Middle Section: User Info Column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = displayName.ifBlank { "Plenxo User" },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            val formattedId = if (plenxoId.isBlank()) "" else if (plenxoId.startsWith("@")) plenxoId else "@$plenxoId"
            if (formattedId.isNotEmpty()) {
                Text(
                    text = formattedId,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right Side: Action Slot
        Box(
            contentAlignment = Alignment.Center
        ) {
            actionSlot()
        }
    }
}

/**
 * Overload helper for [UserListItemCard] with structured [UserActionState].
 */
@Composable
fun UserListItemCard(
    displayName: String,
    plenxoId: String,
    profilePicUrl: String?,
    actionState: UserActionState,
    modifier: Modifier = Modifier,
    isOnline: Boolean = false,
    profileRingId: String = "none",
    avatarSize: Dp = 52.dp,
    onClick: (() -> Unit)? = null
) {
    UserListItemCard(
        displayName = displayName,
        plenxoId = plenxoId,
        profilePicUrl = profilePicUrl,
        modifier = modifier,
        isOnline = isOnline,
        profileRingId = profileRingId,
        avatarSize = avatarSize,
        onClick = onClick,
        actionSlot = {
            when (actionState) {
                is UserActionState.None -> {}
                is UserActionState.Chevron -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Navigate",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                is UserActionState.Add -> {
                    UserAddActionButton(onClick = actionState.onClick)
                }
                is UserActionState.Pending -> {
                    UserPendingActionBadge()
                }
                is UserActionState.Accepted -> {
                    UserAcceptedActionBadge()
                }
                is UserActionState.AcceptReject -> {
                    UserAcceptRejectActionButtons(
                        onAccept = actionState.onAccept,
                        onReject = actionState.onReject
                    )
                }
            }
        }
    )
}

/**
 * Dynamic Action Component: ADD Button
 */
@Composable
fun UserAddActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "+ Add"
) {
    Button(
        onClick = onClick,
        modifier = modifier.testTag("add_user_button"),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF58A6FF),
            contentColor = Color.White
        )
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label.removePrefix("+").trim().ifEmpty { "Add" },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Dynamic Action Component: PENDING Badge
 */
@Composable
fun UserPendingActionBadge(
    modifier: Modifier = Modifier,
    label: String = "Pending"
) {
    Surface(
        modifier = modifier.testTag("pending_user_badge"),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF30363D),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

/**
 * Dynamic Action Component: ACCEPTED Badge
 */
@Composable
fun UserAcceptedActionBadge(
    modifier: Modifier = Modifier,
    label: String = "Accepted"
) {
    Surface(
        modifier = modifier.testTag("accepted_user_badge"),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF238636).copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF238636).copy(alpha = 0.6f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF3FB950),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = Color(0xFF3FB950),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Dynamic Action Component: ACCEPT / REJECT Buttons
 */
@Composable
fun UserAcceptRejectActionButtons(
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onAccept,
            modifier = Modifier.testTag("accept_user_button"),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF238636),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Accept",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        OutlinedButton(
            onClick = onReject,
            modifier = Modifier.testTag("reject_user_button"),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDA3633)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFFF85149)
            )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Reject",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Helper to compute uppercase initials from display name or plenxo ID
 */
private fun getInitials(displayName: String, plenxoId: String): String {
    val cleanName = displayName.trim().ifBlank { plenxoId.trim().removePrefix("@") }
    if (cleanName.isBlank()) return "P"

    val parts = cleanName.split(" ").filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
        parts.isNotEmpty() -> parts[0].take(2).uppercase()
        else -> "P"
    }
}

// ==================== PREVIEWS ====================

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
fun UserListItemCardPreview_Add() {
    MaterialTheme {
        UserListItemCard(
            displayName = "Alex Rivers",
            plenxoId = "alex_rivers",
            profilePicUrl = null,
            isOnline = true,
            actionState = UserActionState.Add(onClick = {})
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
fun UserListItemCardPreview_Pending() {
    MaterialTheme {
        UserListItemCard(
            displayName = "Samantha Chen",
            plenxoId = "sam_chen",
            profilePicUrl = null,
            isOnline = false,
            actionState = UserActionState.Pending
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
fun UserListItemCardPreview_AcceptReject() {
    MaterialTheme {
        UserListItemCard(
            displayName = "Jordan Vance",
            plenxoId = "jvance",
            profilePicUrl = null,
            isOnline = true,
            actionState = UserActionState.AcceptReject(onAccept = {}, onReject = {})
        )
    }
}
