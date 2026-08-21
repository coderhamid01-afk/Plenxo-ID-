package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.PlenxoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBlockedScreen(viewModel: PlenxoViewModel, primaryColor: Color) {
    val blockedIds by viewModel.blockedUserIds.collectAsState()
    val usersCache by viewModel.usersCache.collectAsState()

    val darkBg = Color(0xFF131824)
    val cardBg = Color(0xFF1C2234)
    val accentBlue = Color(0xFF58A6FF)
    val textWhite = Color(0xFFFFFFFF)
    val textMuted = Color(0xFF9CA5BE)
    val dividerColor = Color(0xFF2E3B5E)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.str_blocked_contacts), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textWhite) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = accentBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = darkBg)
            )
        },
        containerColor = darkBg
    ) { paddingValues ->
        if (blockedIds.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.str_no_blocked_contacts), fontSize = 15.sp, color = textMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(blockedIds.toList()) { uid ->
                    val userProfile = usersCache[uid]
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, dividerColor, RoundedCornerShape(12.dp)),
                        color = cardBg,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(userProfile?.displayName ?: "Unknown", fontSize = 15.sp, color = textWhite)
                            Button(
                                onClick = { viewModel.unblockUser(uid) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(stringResource(R.string.str_unblock), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
