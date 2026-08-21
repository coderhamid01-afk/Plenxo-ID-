package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.R
import com.example.model.ChatRoom
import com.example.model.FriendRequest
import com.example.ui.components.ProfileRingBox
import com.example.ui.components.bounceCombinedClickable
import com.example.viewmodel.PlenxoScreen
import com.example.viewmodel.PlenxoViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    viewModel: PlenxoViewModel,
    primaryColor: Color
) {
    val chats by viewModel.chats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val usersCache by viewModel.usersCache.collectAsState()
    val currentUserProfile by viewModel.currentUserProfile.collectAsState()
    val galleryImageUriString by viewModel.galleryImageUriString.collectAsState()
    val currentUserId = viewModel.currentUserId
    val pinnedChatIds by viewModel.pinnedChatIds.collectAsState()
    val lockedChatIds by viewModel.lockedChatIds.collectAsState()
    val userPresences by viewModel.userPresences.collectAsState()
    val pendingFriendRequests by viewModel.pendingFriendRequests.collectAsState()

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            viewModel.startListeningForChats()
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Unread", "Pinned"

    // Dialog state for locked chats
    var chatToUnlock by remember { mutableStateOf<ChatRoom?>(null) }
    var showUnlockPasswordDialog by remember { mutableStateOf(false) }
    var unlockPasswordText by remember { mutableStateOf("") }
    var unlockPasswordError by remember { mutableStateOf<String?>(null) }

    // Dialog state for deleting chats
    val context = LocalContext.current
    var chatToDelete by remember { mutableStateOf<ChatRoom?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDeletePasswordDialog by remember { mutableStateOf(false) }
    var deletePasswordText by remember { mutableStateOf("") }
    var deletePasswordError by remember { mutableStateOf<String?>(null) }

    // Security Unlock Dialog
    val localChatUnlock = chatToUnlock
    if (showUnlockPasswordDialog && localChatUnlock != null) {
        val targetChat = localChatUnlock
        val contextLocal = LocalContext.current
        val correctPin = remember(targetChat.chatId) {
            com.example.repository.SecurityRepository(contextLocal).getChatLock(targetChat.chatId)
        }
        AlertDialog(
            onDismissRequest = {
                showUnlockPasswordDialog = false
                chatToUnlock = null
                unlockPasswordText = ""
                unlockPasswordError = null
            },
            title = { Text("Unlock Chat", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column {
                    Text(
                        "This conversation is locked. Enter your security PIN to unlock.",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = unlockPasswordText,
                        onValueChange = {
                            unlockPasswordText = it
                            unlockPasswordError = null
                        },
                        placeholder = { Text("Enter Chat PIN", color = Color(0xFF64748B)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        isError = unlockPasswordError != null,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (unlockPasswordError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = unlockPasswordError ?: "", color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (unlockPasswordText == correctPin) {
                            showUnlockPasswordDialog = false
                            chatToUnlock = null
                            unlockPasswordText = ""
                            unlockPasswordError = null
                            viewModel.openChatRoom(targetChat)
                        } else {
                            unlockPasswordError = "Incorrect PIN."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text("Unlock", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnlockPasswordDialog = false
                    chatToUnlock = null
                    unlockPasswordText = ""
                    unlockPasswordError = null
                }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Delete Verification Dialog
    val localChat1 = chatToDelete
    if (showDeletePasswordDialog && localChat1 != null) {
        val targetChat = localChat1
        val contextLocal = LocalContext.current
        val correctPin = remember(targetChat.chatId) {
            com.example.repository.SecurityRepository(contextLocal).getChatLock(targetChat.chatId)
        }
        AlertDialog(
            onDismissRequest = {
                showDeletePasswordDialog = false
                deletePasswordText = ""
                deletePasswordError = null
            },
            title = { Text(stringResource(R.string.str_verify_chat_pin), fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column {
                    Text(
                        stringResource(id = R.string.str_this_chat_is_locked_sensitive),
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = deletePasswordText,
                        onValueChange = {
                            deletePasswordText = it
                            deletePasswordError = null
                        },
                        placeholder = { Text(stringResource(R.string.str_enter_chat_pin), color = Color(0xFF64748B)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        isError = deletePasswordError != null,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (deletePasswordError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = deletePasswordError ?: "", color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (deletePasswordText == correctPin) {
                            viewModel.deleteChat(targetChat.chatId)
                            showDeletePasswordDialog = false
                            chatToDelete = null
                            deletePasswordText = ""
                            deletePasswordError = null
                            Toast.makeText(contextLocal, "Chat deleted successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            deletePasswordError = "Incorrect PIN. Deletion denied."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text(stringResource(R.string.str_verify_purge), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeletePasswordDialog = false
                    deletePasswordText = ""
                    deletePasswordError = null
                }) {
                    Text(stringResource(R.string.cancel), color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Simple Delete Confirm Dialog
    val localChat2 = chatToDelete
    if (showDeleteConfirmDialog && localChat2 != null) {
        val targetChat = localChat2
        val contextLocal = LocalContext.current
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
            },
            title = { Text(stringResource(R.string.str_delete_chat), fontWeight = FontWeight.Bold, color = Color(0xFFEF4444)) },
            text = {
                Text(
                    stringResource(id = R.string.str_are_you_sure_you_want),
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteChat(targetChat.chatId)
                        showDeleteConfirmDialog = false
                        chatToDelete = null
                        Toast.makeText(contextLocal, "Chat deleted permanently", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text(stringResource(R.string.str_delete_permanently), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    chatToDelete = null
                }) {
                    Text(stringResource(R.string.cancel), color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Sort and filter conversations
    val sortedChats = remember(chats, pinnedChatIds) {
        chats.sortedWith(
            compareByDescending<ChatRoom> { pinnedChatIds.contains(it.chatId) }
                .thenByDescending { it.lastMessageTimestamp }
        )
    }

    val filteredChats = remember(sortedChats, searchQuery, selectedFilter, usersCache) {
        sortedChats.filter { chat ->
            val recipientUid = chat.participantUids.firstOrNull { it != currentUserId } ?: ""
            val recipientUser = usersCache[recipientUid]
            val unreadCount = chat.unreadCounts[currentUserId] ?: 0
            val isPinned = pinnedChatIds.contains(chat.chatId)

            val matchesFilter = when (selectedFilter) {
                "Unread" -> unreadCount > 0
                "Pinned" -> isPinned
                else -> true
            }

            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val nameMatch = recipientUser?.displayName?.contains(searchQuery, ignoreCase = true) == true
                val emailMatch = recipientUser?.email?.contains(searchQuery, ignoreCase = true) == true
                val userCodeMatch = recipientUser?.userCode?.contains(searchQuery, ignoreCase = true) == true
                val plenxoIdMatch = recipientUser?.plenxoId?.contains(searchQuery, ignoreCase = true) == true
                nameMatch || emailMatch || userCodeMatch || plenxoIdMatch
            }

            matchesFilter && matchesSearch
        }
    }

    val unreadTotalCount = remember(chats, currentUserId) {
        chats.count { (it.unreadCounts[currentUserId] ?: 0) > 0 }
    }
    val pinnedTotalCount = remember(chats, pinnedChatIds) {
        chats.count { pinnedChatIds.contains(it.chatId) }
    }

    val darkSurfaceBg = Color(0xFF090D16)
    val accentCyan = Color(0xFF38BDF8)

    Scaffold(
        topBar = {
            // Sleek Modern Top Bar with Profile on Left and Gear Settings on Right
            Surface(
                color = darkSurfaceBg,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Profile Picture with Small Circular Status Icon / Ring above it
                        val contextLocal = LocalContext.current
                        val localRingId = com.example.util.SessionManager.getProfileRingId(contextLocal)
                        val userRingId = if (localRingId != "none") localRingId else (currentUserProfile?.profileRingId ?: "none")
                        val displayAvatarUrl = currentUserProfile?.profilePicUrl?.takeIf { it.isNotEmpty() }
                            ?: galleryImageUriString?.takeIf { it.isNotEmpty() }

                        Box(
                            modifier = Modifier
                                .testTag("profile_settings_avatar_button")
                                .clickable {
                                    viewModel.navigateToScreen(PlenxoScreen.SETTINGS_PROFILE)
                                }
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ProfileRingBox(ringId = userRingId, ringPadding = 1.dp, borderWidth = 2.5.dp) {
                                if (!displayAvatarUrl.isNullOrEmpty() && (displayAvatarUrl.startsWith("http") || displayAvatarUrl.startsWith("content://") || displayAvatarUrl.startsWith("file://"))) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(contextLocal)
                                            .data(displayAvatarUrl)
                                            .crossfade(true)
                                            .diskCachePolicy(CachePolicy.ENABLED)
                                            .memoryCachePolicy(CachePolicy.ENABLED)
                                            .build(),
                                        placeholder = painterResource(android.R.drawable.ic_menu_gallery),
                                        error = painterResource(android.R.drawable.ic_menu_report_image),
                                        contentDescription = "Profile Settings",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(primaryColor, primaryColor.copy(alpha = 0.7f))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = currentUserProfile?.displayName?.takeIf { it.isNotBlank() }?.take(1)?.uppercase() ?: "P",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }

                            // Small circular indicator icon on the avatar
                            Box(
                                modifier = Modifier
                                    .size(13.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                                    .border(2.dp, darkSurfaceBg, CircleShape)
                                    .align(Alignment.TopEnd)
                            )
                        }

                        // Center: App Branding Centered Horizontally
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "PLENXO",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 19.sp,
                                letterSpacing = 2.sp,
                                color = Color.White
                            )
                        }

                        // Right: Notification & Gear Settings Icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Chat Requests / Notifications Button
                            IconButton(
                                onClick = { viewModel.navigateToScreen(PlenxoScreen.CHAT_REQUESTS) },
                                modifier = Modifier.testTag("chat_requests_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (pendingFriendRequests.isNotEmpty()) {
                                            Badge(
                                                containerColor = Color(0xFFEF4444),
                                                contentColor = Color.White
                                            ) {
                                                Text(pendingFriendRequests.size.toString(), fontSize = 10.sp)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Notifications,
                                        contentDescription = "Chat Requests",
                                        tint = if (pendingFriendRequests.isNotEmpty()) accentCyan else Color(0xFF94A3B8),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // Settings Gear Icon
                            IconButton(
                                onClick = { viewModel.navigateToScreen(PlenxoScreen.SETTINGS_NORMAL) },
                                modifier = Modifier.testTag("settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search Bar for Added Users
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Search added users & messages...",
                                color = Color(0xFF64748B),
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear Search",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp,
                            color = Color.White,
                            lineHeight = 18.sp,
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF131B2E),
                            unfocusedContainerColor = Color(0xFF131B2E),
                            focusedBorderColor = primaryColor.copy(alpha = 0.6f),
                            unfocusedBorderColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp, max = 52.dp)
                            .testTag("chats_search_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Modern Category Filter Pills
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            FilterPill(
                                label = "All",
                                count = chats.size,
                                isSelected = selectedFilter == "All",
                                activeColor = primaryColor,
                                onClick = { selectedFilter = "All" }
                            )
                        }
                        item {
                            FilterPill(
                                label = "Unread",
                                count = unreadTotalCount,
                                isSelected = selectedFilter == "Unread",
                                activeColor = primaryColor,
                                onClick = { selectedFilter = "Unread" }
                            )
                        }
                        item {
                            FilterPill(
                                label = "Pinned",
                                count = pinnedTotalCount,
                                isSelected = selectedFilter == "Pinned",
                                activeColor = primaryColor,
                                onClick = { selectedFilter = "Pinned" }
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            // Plus icon at the bottom right side to search & add users
            FloatingActionButton(
                onClick = { viewModel.navigateToScreen(PlenxoScreen.DISCOVERY) },
                containerColor = primaryColor,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 8.dp)
                    .testTag("fab_add_friend")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Search and Add Users",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        containerColor = darkSurfaceBg
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(darkSurfaceBg)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Pending Incoming Friend Requests Banner
                if (pendingFriendRequests.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF1E293B), Color(0xFF131B2E))
                                )
                            )
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.PersonAdd,
                                    contentDescription = "Requests",
                                    tint = accentCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Friend Requests (${pendingFriendRequests.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                            TextButton(
                                onClick = { viewModel.navigateToScreen(PlenxoScreen.CHAT_REQUESTS) },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("View All", fontSize = 12.sp, color = accentCyan)
                            }
                        }

                        pendingFriendRequests.take(2).forEach { request ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val senderRingId = usersCache[request.senderUid]?.profileRingId
                                    ProfileRingBox(ringId = senderRingId, ringPadding = 1.dp, borderWidth = 2.dp) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF334155)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (request.senderProfilePic.isNotEmpty()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(request.senderProfilePic)
                                                        .crossfade(true)
                                                        .diskCachePolicy(CachePolicy.ENABLED)
                                                        .memoryCachePolicy(CachePolicy.ENABLED)
                                                        .build(),
                                                    placeholder = painterResource(android.R.drawable.ic_menu_gallery),
                                                    error = painterResource(android.R.drawable.ic_menu_report_image),
                                                    contentDescription = "Sender avatar",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Text(
                                                    request.senderName.take(1).uppercase(),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = request.senderName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val reqSenderPx = usersCache[request.senderUid]?.plenxoId
                                            ?: usersCache[request.senderUid]?.userCode
                                            ?: ""
                                        if (reqSenderPx.isNotBlank()) {
                                            Text(
                                                text = if (reqSenderPx.startsWith("PX-")) reqSenderPx else "PX-$reqSenderPx",
                                                fontSize = 11.sp,
                                                color = accentCyan
                                            )
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = {
                                            viewModel.acceptFriendRequest(request) {
                                                Toast.makeText(context, "Added!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Accept", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.rejectFriendRequest(request) {
                                                Toast.makeText(context, "Declined", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Decline", fontSize = 11.sp, color = Color(0xFFEF4444))
                                    }
                                }
                            }
                        }
                    }
                }

                // Main Content List or Empty State
                Box(modifier = Modifier.weight(1f)) {
                    if (isLoading && chats.isEmpty()) {
                        // Shimmer Loading Skeleton
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            repeat(6) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(76.dp)
                                        .padding(vertical = 6.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF131B2E).copy(alpha = 0.6f))
                                )
                            }
                        }
                    } else if (filteredChats.isEmpty()) {
                        // EXACT USER SPECIFICATION: Display "Your list has been empty."
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                primaryColor.copy(alpha = 0.2f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                                    .border(1.5.dp, primaryColor.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = "Empty State",
                                    tint = primaryColor,
                                    modifier = Modifier.size(44.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Exact string requirement
                            Text(
                                text = "Your list has been empty.",
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = if (searchQuery.isNotEmpty()) {
                                    "No users found matching \"$searchQuery\"."
                                } else {
                                    "Search for friends using their 6-digit Plenxo ID to connect and chat securely."
                                },
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = { viewModel.navigateToScreen(PlenxoScreen.DISCOVERY) },
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .testTag("empty_state_add_user_button")
                                    .shadow(6.dp, RoundedCornerShape(24.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Search by Plenxo ID",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        // Display Main Users List
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            itemsIndexed(filteredChats, key = { _, chat -> chat.chatId }) { _, chat ->
                                val recipientUid = chat.participantUids.firstOrNull { it != currentUserId } ?: "Unknown"
                                val recipientUser = usersCache[recipientUid]
                                val displayName = recipientUser?.displayName?.takeIf { it.isNotBlank() } ?: "User"
                                val rawPlenxoId = recipientUser?.plenxoId?.ifBlank { recipientUser.userCode.orEmpty() } ?: ""
                                val unreadCount = chat.unreadCounts[currentUserId] ?: 0
                                val isPinned = pinnedChatIds.contains(chat.chatId)
                                val isLocked = lockedChatIds.contains(chat.chatId)

                                val presenceMap = userPresences[recipientUid] ?: emptyMap()
                                val presenceState = presenceMap["state"] as? String ?: "offline"

                                LaunchedEffect(recipientUid) {
                                    if (recipientUid.isNotEmpty()) {
                                        viewModel.startListeningToPresence(recipientUid)
                                    }
                                }

                                ModernChatCardItem(
                                    chat = chat,
                                    recipientName = displayName,
                                    plenxoId = rawPlenxoId,
                                    profilePicUrl = recipientUser?.profilePicUrl ?: "",
                                    profileRingId = recipientUser?.profileRingId ?: "none",
                                    unreadCount = unreadCount,
                                    primaryColor = primaryColor,
                                    isPinned = isPinned,
                                    isLocked = isLocked,
                                    presenceState = presenceState,
                                    onAvatarClick = {
                                        if (recipientUid.isNotBlank()) {
                                            viewModel.openUserProfile(recipientUid)
                                        }
                                    },
                                    onPinToggle = { viewModel.toggleChatPin(chat.chatId) },
                                    onLockToggle = {
                                        if (isLocked) {
                                            viewModel.toggleChatLock(chat.chatId)
                                            com.example.repository.SecurityRepository(context).setChatLock(chat.chatId, null)
                                            com.example.repository.SecurityRepository(context).setChatLockType(chat.chatId, null)
                                        } else {
                                            val intent = android.content.Intent(context, com.example.ui.AppLockSetupActivity::class.java).apply {
                                                putExtra("chatId", chat.chatId)
                                                if (context !is android.app.Activity) {
                                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            }
                                            context.startActivity(intent)
                                            viewModel.toggleChatLock(chat.chatId)
                                        }
                                    },
                                    onDeleteChat = {
                                        chatToDelete = chat
                                        if (isLocked) {
                                            showDeletePasswordDialog = true
                                        } else {
                                            showDeleteConfirmDialog = true
                                        }
                                    },
                                    onClick = {
                                        if (isLocked) {
                                            chatToUnlock = chat
                                            showUnlockPasswordDialog = true
                                            unlockPasswordText = ""
                                            unlockPasswordError = null
                                        } else {
                                            viewModel.openChatRoom(chat)
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Filter Pill Component for Top Bar Selection
 */
@Composable
private fun FilterPill(
    label: String,
    count: Int,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val containerBg = if (isSelected) activeColor else Color(0xFF131B2E)
    val textColor = if (isSelected) Color.White else Color(0xFF94A3B8)
    val borderColor = if (isSelected) activeColor else Color(0xFF1E293B)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(containerBg)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (isSelected) Color.White.copy(alpha = 0.25f) else Color(0xFF1E293B)
                        )
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = count.toString(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else Color(0xFF38BDF8)
                    )
                }
            }
        }
    }
}

/**
 * Modern Card Item for Main Screen User Conversations
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModernChatCardItem(
    chat: ChatRoom,
    recipientName: String,
    plenxoId: String = "",
    profilePicUrl: String,
    profileRingId: String,
    unreadCount: Int,
    primaryColor: Color,
    isPinned: Boolean,
    isLocked: Boolean,
    presenceState: String = "offline",
    onAvatarClick: () -> Unit = {},
    onPinToggle: () -> Unit,
    onLockToggle: () -> Unit,
    onDeleteChat: () -> Unit,
    onClick: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeString = remember(chat.lastMessageTimestamp) {
        chat.lastMessageTimestamp?.let { formatter.format(Date(it)) } ?: ""
    }
    var showMenu by remember { mutableStateOf(false) }

    val cardBg = if (isPinned) Color(0xFF16223B) else Color(0xFF111827)
    val cardBorder = if (isPinned) primaryColor.copy(alpha = 0.35f) else Color(0xFF1E293B)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bounceCombinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with Ring & Online Indicator
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center
            ) {
                ProfileRingBox(ringId = profileRingId, ringPadding = 1.5.dp, borderWidth = 2.5.dp) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (profilePicUrl.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(profilePicUrl)
                                    .crossfade(true)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .build(),
                                placeholder = painterResource(android.R.drawable.ic_menu_gallery),
                                error = painterResource(android.R.drawable.ic_menu_report_image),
                                contentDescription = "User Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = recipientName.take(1).uppercase(),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                        }
                    }
                }

                // Presence Dot
                val presenceColor = if (presenceState == "online") Color(0xFF10B981) else Color(0xFF64748B)
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(presenceColor)
                        .border(2.dp, cardBg, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // User Info and Last Message
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = recipientName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (isPinned) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = primaryColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        if (isLocked) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    // Timestamp
                    if (timeString.isNotBlank()) {
                        Text(
                            text = timeString,
                            fontSize = 11.sp,
                            color = if (unreadCount > 0) primaryColor else Color(0xFF64748B),
                            fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Plenxo ID Pill Badge
                if (plenxoId.isNotBlank()) {
                    val cleanPx = plenxoId.trim().removePrefix("@").removePrefix("#")
                    val displayPx = if (cleanPx.startsWith("PX-", ignoreCase = true)) cleanPx.uppercase() else "PX-$cleanPx"
                    Text(
                        text = displayPx,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF38BDF8)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Last Message Preview & Unread Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (chat.lastMessage.isBlank()) "Tap to start conversation" else chat.lastMessage,
                        fontSize = 13.sp,
                        color = if (unreadCount > 0) Color.White else Color(0xFF94A3B8),
                        fontWeight = if (unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(primaryColor)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Long Press Context Menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (isPinned) "Unpin Conversation" else "Pin Conversation") },
                leadingIcon = {
                    Icon(
                        imageVector = if (isPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                        contentDescription = null
                    )
                },
                onClick = {
                    onPinToggle()
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text(if (isLocked) "Unlock (Disable App Lock)" else "Lock Conversation") },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.Lock, contentDescription = null)
                },
                onClick = {
                    onLockToggle()
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.str_delete_chat_1), color = Color(0xFFEF4444)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444))
                },
                onClick = {
                    onDeleteChat()
                    showMenu = false
                }
            )
        }
    }
}
