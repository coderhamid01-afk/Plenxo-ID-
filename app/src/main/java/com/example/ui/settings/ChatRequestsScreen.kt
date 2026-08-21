package com.example.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.ui.components.UserActionState
import com.example.ui.components.UserListItemCard
import com.example.viewmodel.ChatRequestViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRequestsScreen(
    onBack: () -> Unit,
    viewModel: ChatRequestViewModel = viewModel()
) {
    val context = LocalContext.current
    val incomingRequests by viewModel.incomingRequests.collectAsState()
    val sentRequests by viewModel.sentRequests.collectAsState()
    val toastMsg by viewModel.toastMessage.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(toastMsg) {
        toastMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    val darkBg = Color(0xFF0D1117)
    val cardBg = Color(0xFF161B22)
    val strokeBorder = Color(0xFF30363D)
    val accentBlue = Color(0xFF58A6FF)
    val textWhite = Color(0xFFF0F6FC)
    val textMuted = Color(0xFF8B949E)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Requests & Connections",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textWhite
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_requests_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = accentBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = darkBg)
            )
        },
        containerColor = darkBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = darkBg,
                contentColor = accentBlue,
                divider = { HorizontalDivider(color = strokeBorder) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Received", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                            if (incomingRequests.isNotEmpty()) {
                                Badge(containerColor = Color(0xFFEF4444), contentColor = Color.White) {
                                    Text("${incomingRequests.size}", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Sent", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                            if (sentRequests.isNotEmpty()) {
                                Badge(containerColor = accentBlue, contentColor = Color.White) {
                                    Text("${sentRequests.size}", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                )
            }

            when (selectedTab) {
                0 -> {
                    // INCOMING REQUESTS
                    if (incomingRequests.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.PersonOff,
                                    contentDescription = null,
                                    tint = textMuted,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No received requests",
                                    color = textMuted,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag("empty_chat_requests_text")
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "When someone sends you a friend request via PX ID, it will appear here.",
                                    color = textMuted.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 40.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(incomingRequests, key = { it.id }) { request ->
                                val isAccepted = request.status.equals("ACCEPTED", ignoreCase = true)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("chat_request_card_${request.id}"),
                                    colors = CardDefaults.cardColors(containerColor = cardBg),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, strokeBorder)
                                ) {
                                    UserListItemCard(
                                        displayName = request.senderName.ifBlank { "Plenxo User" },
                                        plenxoId = request.senderPlenxoId,
                                        profilePicUrl = request.senderProfilePic,
                                        actionState = if (isAccepted) {
                                            UserActionState.Accepted
                                        } else {
                                            UserActionState.AcceptReject(
                                                onAccept = { viewModel.acceptRequest(request) },
                                                onReject = { viewModel.rejectRequest(request) }
                                            )
                                        },
                                        onClick = null
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // SENT REQUESTS
                    if (sentRequests.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = null,
                                    tint = textMuted,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No outgoing requests",
                                    color = textMuted,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Requests you send to others via their PX ID will show here until accepted.",
                                    color = textMuted.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 40.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(sentRequests, key = { it.id }) { req ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, strokeBorder, RoundedCornerShape(12.dp)),
                                    color = cardBg,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(strokeBorder),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (req.receiverProfilePic.isNotBlank()) {
                                                    AsyncImage(
                                                        model = req.receiverProfilePic,
                                                        contentDescription = null,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Icon(Icons.Default.Person, contentDescription = null, tint = textMuted)
                                                }
                                            }
                                            Column {
                                                Text(
                                                    text = req.receiverName.ifBlank { req.receiverPlenxoId.ifBlank { "Contact" } },
                                                    color = textWhite,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (req.receiverPlenxoId.isNotBlank()) {
                                                    Text(
                                                        text = req.receiverPlenxoId,
                                                        color = accentBlue,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }

                                        val isAcc = req.status.equals("ACCEPTED", ignoreCase = true)
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (isAcc) Color(0xFF238636).copy(alpha = 0.2f) else Color(0xFFD29922).copy(alpha = 0.2f),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = if (isAcc) "Accepted" else "Pending...",
                                                color = if (isAcc) Color(0xFF3FB950) else Color(0xFFD29922),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
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
}

