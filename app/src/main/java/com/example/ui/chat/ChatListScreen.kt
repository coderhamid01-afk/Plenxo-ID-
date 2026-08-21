package com.example.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.model.ChatRequest
import com.example.model.ChatRoom
import com.example.viewmodel.ChatRequestViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.text.SimpleDateFormat
import java.util.*

sealed class ChatListItem {
    data class ActiveChat(val room: ChatRoom, val recipientUid: String, val recipientName: String, val recipientPic: String) : ChatListItem()
    data class PendingRequest(val request: ChatRequest, val recipientUid: String, val recipientName: String, val recipientPic: String, val recipientPlenxoId: String) : ChatListItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onOpenChat: (chatId: String, recipientUid: String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToRequests: () -> Unit,
    chatRequestViewModel: ChatRequestViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentUid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    val snackbarHostState = remember { SnackbarHostState() }

    // Observe real-time chat requests
    val allRequests by chatRequestViewModel.allRequests.collectAsState()

    // Real-time active chat rooms from Firestore
    val activeChatRoomsState = remember(currentUid) { observeActiveChatRooms(currentUid) }
    val activeChatRooms by activeChatRoomsState.collectAsState(initial = emptyList())

    val darkBg = Color(0xFF0D1117)
    val cardBg = Color(0xFF161B22)
    val strokeBorder = Color(0xFF30363D)
    val accentBlue = Color(0xFF58A6FF)
    val textWhite = Color(0xFFF0F6FC)
    val textMuted = Color(0xFF8B949E)
    val amberPending = Color(0xFFD29922)

    val pendingSentRequests = remember(allRequests, currentUid) {
        allRequests.filter { it.senderId == currentUid && it.status == "PENDING" }
    }

    val incomingPendingRequests = remember(allRequests, currentUid) {
        allRequests.filter { it.receiverId == currentUid && it.status == "PENDING" }
    }

    val combinedList = remember(activeChatRooms, pendingSentRequests) {
        val items = mutableListOf<ChatListItem>()

        // 1. Add active chat rooms
        activeChatRooms.forEach { room ->
            val recipientUid = room.participantUids.firstOrNull { it != currentUid } ?: ""
            items.add(
                ChatListItem.ActiveChat(
                    room = room,
                    recipientUid = recipientUid,
                    recipientName = "Chat Partner",
                    recipientPic = ""
                )
            )
        }

        // 2. Add pending sent requests (User B pending acceptance)
        pendingSentRequests.forEach { req ->
            val recipientUid = req.receiverId
            // Don't duplicate if already active
            val alreadyActive = activeChatRooms.any { room ->
                room.participantUids.contains(recipientUid)
            }
            if (!alreadyActive) {
                items.add(
                    ChatListItem.PendingRequest(
                        request = req,
                        recipientUid = recipientUid,
                        recipientName = "Plenxo User",
                        recipientPic = "",
                        recipientPlenxoId = req.id
                    )
                )
            }
        }

        items
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Plenxo Chats",
                        fontWeight = FontWeight.Bold,
                        color = textWhite,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    // Incoming requests badge button
                    IconButton(
                        onClick = onNavigateToRequests,
                        modifier = Modifier.testTag("incoming_requests_icon_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (incomingPendingRequests.isNotEmpty()) {
                                    Badge(containerColor = Color.Red, contentColor = Color.White) {
                                        Text("${incomingPendingRequests.size}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Requests",
                                tint = accentBlue
                            )
                        }
                    }

                    // Search by Plenxo ID button
                    IconButton(
                        onClick = onNavigateToSearch,
                        modifier = Modifier.testTag("search_plenxo_id_nav_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Users",
                            tint = accentBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = darkBg)
            )
        },
        containerColor = darkBg
    ) { paddingValues ->
        if (combinedList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No active chats yet",
                        color = textWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tap the search icon to find users by Plenxo ID.",
                        color = textMuted,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onNavigateToSearch,
                        colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Search Plenxo ID", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(combinedList, key = { item ->
                    when (item) {
                        is ChatListItem.ActiveChat -> "chat_${item.room.chatId}"
                        is ChatListItem.PendingRequest -> "request_${item.request.id}"
                    }
                }) { item ->
                    when (item) {
                        is ChatListItem.ActiveChat -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenChat(item.room.chatId, item.recipientUid) }
                                    .testTag("active_chat_item_${item.room.chatId}"),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, strokeBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(accentBlue.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = accentBlue,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Chat",
                                            fontWeight = FontWeight.Bold,
                                            color = textWhite,
                                            fontSize = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.room.lastMessage.ifEmpty { "Start conversation..." },
                                            color = textMuted,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        is ChatListItem.PendingRequest -> {
                            // HARD CLICK LOCK FOR PENDING REQUESTS
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // TRIGGER HARD CLICK LOCK SNACKBAR
                                        Toast.makeText(
                                            context,
                                            "Chat request is pending acceptance from the user.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    .testTag("pending_chat_item_${item.request.id}"),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, strokeBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(amberPending.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = amberPending,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.recipientName,
                                            fontWeight = FontWeight.Bold,
                                            color = textWhite,
                                            fontSize = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Waiting for request approval",
                                            color = textMuted,
                                            fontSize = 13.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // VISIBLE PENDING BADGE
                                    Surface(
                                        color = amberPending.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, amberPending)
                                    ) {
                                        Text(
                                            text = "PENDING",
                                            color = amberPending,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun observeActiveChatRooms(uid: String): Flow<List<ChatRoom>> = callbackFlow {
    if (uid.isBlank()) {
        trySend(emptyList())
        close()
        return@callbackFlow
    }

    val db = FirebaseFirestore.getInstance()
    val listener = db.collection("chats")
        .whereArrayContains("participantUids", uid)
        .addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                trySend(emptyList())
                return@addSnapshotListener
            }

            val rooms = snapshot.documents.mapNotNull { doc ->
                try {
                    val chatId = doc.getString("chatId") ?: doc.id
                    @Suppress("UNCHECKED_CAST")
                    val participantUids = doc.get("participantUids") as? List<String> ?: emptyList()
                    val lastMessage = doc.getString("lastMessage") ?: ""
                    val lastMessageTimestamp = doc.getLong("lastMessageTimestamp")

                    ChatRoom(
                        chatId = chatId,
                        participantUids = participantUids,
                        lastMessage = lastMessage,
                        lastMessageTimestamp = lastMessageTimestamp
                    )
                } catch (e: Exception) {
                    null
                }
            }
            trySend(rooms)
        }

    awaitClose { listener.remove() }
}
