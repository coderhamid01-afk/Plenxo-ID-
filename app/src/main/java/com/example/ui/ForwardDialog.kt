package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.ChatRoom
import com.example.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardDialog(
    chats: List<ChatRoom>,
    usersCache: Map<String, User>,
    currentUserId: String,
    primaryColor: Color,
    onDismiss: () -> Unit,
    onConfirmForward: (List<String>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val selectedChatIds = remember { mutableStateListOf<String>() }
    val haptic = LocalHapticFeedback.current

    val filteredChats = remember(chats, searchQuery, usersCache) {
        if (searchQuery.isBlank()) {
            chats
        } else {
            chats.filter { chat ->
                val recipientUid = chat.participantUids.firstOrNull { it != currentUserId } ?: ""
                val recipientUser = usersCache[recipientUid]
                val displayName = recipientUser?.displayName ?: "User"
                displayName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .testTag("forward_message_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Forward Message",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.str_search_chats)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("forward_search_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        focusedLabelColor = primaryColor
                    )
                )

                if (filteredChats.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.str_no_matching_chats_found), color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(filteredChats) { chat ->
                            val recipientUid = chat.participantUids.firstOrNull { it != currentUserId } ?: "Unknown"
                            val recipientUser = usersCache[recipientUid]
                            val displayName = recipientUser?.displayName ?: "User"
                            val isSelected = selectedChatIds.contains(chat.chatId)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (isSelected) {
                                            selectedChatIds.remove(chat.chatId)
                                        } else {
                                            selectedChatIds.add(chat.chatId)
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                                    .testTag("forward_chat_item_${chat.chatId}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (isSelected) {
                                            selectedChatIds.remove(chat.chatId)
                                        } else {
                                            selectedChatIds.add(chat.chatId)
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = primaryColor)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = displayName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = Color.Black
                                    )
                                    Text(
                                        text = recipientUser?.email ?: "No email",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("forward_cancel")) {
                        Text(stringResource(R.string.cancel), color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (selectedChatIds.isNotEmpty()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onConfirmForward(selectedChatIds.toList())
                            }
                        },
                        enabled = selectedChatIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("forward_confirm")
                    ) {
                        Text("Forward (${selectedChatIds.size})")
                    }
                }
            }
        }
    }
}
