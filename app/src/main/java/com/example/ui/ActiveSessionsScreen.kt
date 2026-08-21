package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ActiveSession
import com.example.viewmodel.PlenxoViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionsScreen(viewModel: PlenxoViewModel) {
    val sessions by viewModel.activeSessions.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.observeActiveSessions()
    }
    
    val darkBg = Color(0xFF0D1117)
    val cardBg = Color(0xFF161B22)
    val strokeBorder = Color(0xFF30363D)
    val textWhite = Color(0xFFF0F6FC)
    val textMuted = Color(0xFF8B949E)
    val accentBlue = Color(0xFF58A6FF)
    val greenCurrent = Color(0xFF2EA043)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Management", color = textWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = accentBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = darkBg)
            )
        },
        containerColor = darkBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Limits Info Card
            Surface(
                color = Color(0xFF1F242C),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, strokeBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = accentBlue, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Simultaneous Devices Limit", color = textWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Maximum 3 active logged-in devices allowed.", color = textMuted, fontSize = 12.sp)
                    }
                }
            }

            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No other active devices", color = textMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sessions, key = { it.sessionId }) { session ->
                        SessionCard(
                            session = session,
                            cardBg = cardBg,
                            strokeBorder = strokeBorder,
                            textWhite = textWhite,
                            textMuted = textMuted,
                            accentBlue = accentBlue,
                            greenCurrent = greenCurrent,
                            onLogout = { viewModel.terminateSession(session.sessionId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SessionCard(
    session: ActiveSession,
    cardBg: Color,
    strokeBorder: Color,
    textWhite: Color,
    textMuted: Color,
    accentBlue: Color,
    greenCurrent: Color,
    onLogout: () -> Unit
) {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    val lastActive = if (session.lastActiveTime > 0) session.lastActiveTime else session.timestamp
    val dateStr = if (lastActive > 0) sdf.format(Date(lastActive)) else "Just now"

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, strokeBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("device_card_${session.sessionId}")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFF21262D), shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Devices, contentDescription = null, tint = accentBlue, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val displayName = session.deviceName.ifBlank { session.deviceModel.ifBlank { "Android Device" } }
                    Text(displayName, color = textWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (session.isCurrentDevice) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = greenCurrent.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = greenCurrent, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("This Device", color = greenCurrent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                val osInfo = session.operatingSystem.ifBlank { "Android OS" }
                Text("OS: $osInfo", color = textMuted, fontSize = 12.sp)
                Text("Last active: $dateStr", color = textMuted, fontSize = 12.sp)
            }

            if (!session.isCurrentDevice) {
                IconButton(
                    onClick = onLogout,
                    modifier = Modifier.testTag("logout_device_${session.sessionId}")
                ) {
                    Icon(Icons.Default.Logout, contentDescription = "Log Out Device", tint = Color(0xFFF85149))
                }
            }
        }
    }
}
