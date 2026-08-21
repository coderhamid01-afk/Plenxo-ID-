package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.components.bounceClick

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.ChatRoom
import com.example.model.User
import com.example.ui.theme.PlenxoColors
import com.example.ui.theme.PlenxoSpacing
import com.example.ui.theme.PlenxoTypography
import com.example.viewmodel.PlenxoScreen
import com.example.viewmodel.PlenxoViewModel
import java.text.SimpleDateFormat
import java.util.*

import com.example.ui.components.ProfileRing
import com.example.ui.components.ProfileRingBox
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialDashboardScreen(
    viewModel: PlenxoViewModel
) {
    val chats by viewModel.chats.collectAsState()
    val usersCache by viewModel.usersCache.collectAsState()
    val currentUserProfile by viewModel.currentUserProfile.collectAsState()
    val pendingInvitations by viewModel.pendingInvitations.collectAsState()
    val currentUserId = viewModel.currentUserId

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    val contextLocal = androidx.compose.ui.platform.LocalContext.current
                    val localRingId = com.example.util.SessionManager.getProfileRingId(contextLocal)
                    val userRingId = if (localRingId != "none") localRingId else (currentUserProfile?.profileRingId ?: "none")
                    val userPic = currentUserProfile?.profilePicUrl ?: ""
                    val displayName = currentUserProfile?.displayName?.takeIf { it.isNotBlank() } ?: "User"

                    IconButton(
                        onClick = { viewModel.navigateToScreen(PlenxoScreen.SETTINGS_PROFILE) },
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(44.dp)
                    ) {
                        ProfileRingBox(
                            ringId = userRingId,
                            ringPadding = 1.dp,
                            borderWidth = 3.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1C2234)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (userPic.isNotEmpty() && (userPic.startsWith("http") || userPic.startsWith("content://") || userPic.startsWith("file://"))) {
                                    coil.compose.AsyncImage(
                                        model = coil.request.ImageRequest.Builder(contextLocal)
                                            .data(userPic)
                                            .crossfade(true)
                                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                            .build(),
                                        placeholder = null,
                                        error = null,
                                        contentDescription = "Your Profile Picture",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(PlenxoColors.Primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = displayName.take(1).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    IconButton(
                        onClick = {
                            try {
                                val intent = android.content.Intent(context, com.example.ui.SettingsActivity::class.java)
                                if (context !is android.app.Activity) {
                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.util.Log.e("SocialDashboard", "Failed to start SettingsActivity, fallback to compose navigation", e)
                                viewModel.navigateToScreen(PlenxoScreen.SETTINGS_NORMAL)
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = PlenxoColors.Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PlenxoColors.Background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.navigateToScreen(com.example.viewmodel.PlenxoScreen.DISCOVERY) },
                containerColor = PlenxoColors.Primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("add_user_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add User / New Chat"
                )
            }
        },
        containerColor = PlenxoColors.Background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Pending Friend Requests
            val pendingFriendRequests by viewModel.pendingFriendRequests.collectAsState()
            if (pendingFriendRequests.isNotEmpty()) {
                pendingFriendRequests.forEach { request ->
                    PendingRequestCard(
                        request = request,
                        onAccept = { viewModel.acceptFriendRequest(request) },
                        onReject = { viewModel.rejectFriendRequest(request) }
                    )
                }
            }

            if (chats.isEmpty()) {
                EmptyDashboardState(onDiscoveryClick = { viewModel.navigateToScreen(com.example.viewmodel.PlenxoScreen.DISCOVERY) })
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(chats) { chat ->
                        val recipientUid = chat.participantUids.find { it != currentUserId } ?: ""
                        val recipientUser = usersCache[recipientUid]
                        
                        ChatItemRow(
                            chat = chat,
                            recipientUser = recipientUser,
                            onClick = { viewModel.openChatRoom(chat) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = PlenxoColors.Divider
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PendingRequestCard(
    request: com.example.model.FriendRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = PlenxoColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color.Gray)
            ) {
                if (request.senderProfilePic.isNotEmpty()) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(request.senderProfilePic)
                            .crossfade(true)
                            .build(),
                        placeholder = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_gallery),
                        error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image),
                        contentDescription = "Profile Pic",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center),
                        tint = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = request.senderName.ifEmpty { "Someone" }, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PlenxoColors.TextPrimary)
                Text(stringResource(id = R.string.str_sent_you_a_friend_request), fontSize = 14.sp, color = PlenxoColors.TextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(stringResource(R.string.str_accept), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onReject,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(stringResource(R.string.str_reject), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ChatItemRow(chat: ChatRoom, recipientUser: User?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        val ringTier = recipientUser?.selectedRingId?.split("_")?.first()?.replaceFirstChar { it.uppercase() } ?: "None"
        ProfileRing(tier = ringTier) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(PlenxoColors.Surface)
            ) {
                if (recipientUser?.profilePicUrl?.startsWith("http") == true) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(recipientUser.profilePicUrl)
                            .crossfade(true)
                            .build(),
                        placeholder = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_gallery),
                        error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Fallback for placeholder or emoji
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = recipientUser?.displayName?.take(1)?.uppercase() ?: "?",
                            color = PlenxoColors.Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = recipientUser?.displayName ?: "Unknown User",
                    style = PlenxoTypography.Body.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                chat.lastMessageTimestamp?.let {
                    val date = java.util.Date(it)
                    val format = SimpleDateFormat("HH:mm", Locale.getDefault())
                    Text(
                        text = format.format(date),
                        style = PlenxoTypography.Caption,
                        color = PlenxoColors.TextSecondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = chat.lastMessage,
                style = PlenxoTypography.Label,
                color = PlenxoColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EmptyDashboardState(onDiscoveryClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = PlenxoColors.TextSecondary.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(id = R.string.str_no_conversations_yet),
            style = PlenxoTypography.Title.copy(fontSize = 20.sp),
            color = PlenxoColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(id = R.string.str_start_connecting_with_friends_by),
            style = PlenxoTypography.Body,
            color = PlenxoColors.TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onDiscoveryClick,
            colors = ButtonDefaults.buttonColors(containerColor = PlenxoColors.Primary),
            shape = RoundedCornerShape(25.dp),
            modifier = Modifier.height(48.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.str_discover_friends), fontWeight = FontWeight.Bold)
        }
    }
}


