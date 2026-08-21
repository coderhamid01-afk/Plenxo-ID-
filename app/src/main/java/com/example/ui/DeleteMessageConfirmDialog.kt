package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun DeleteMessageConfirmDialog(
    isSentByCurrentUser: Boolean,
    primaryColor: Color,
    onDismiss: () -> Unit,
    onDeleteLocalOnly: () -> Unit,
    onDeleteForEveryone: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("delete_message_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Text(stringResource(id = R.string.str_delete_message),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = if (isSentByCurrentUser) {
                        "Do you want to delete this message for everyone or just for yourself?"
                    } else {
                        "Are you sure you want to delete this message from your device history?"
                    },
                    fontSize = 14.sp,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("delete_cancel")
                    ) {
                        Text(stringResource(R.string.cancel), color = Color.Gray)
                    }

                    if (isSentByCurrentUser) {
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDeleteLocalOnly()
                                onDismiss()
                            },
                            modifier = Modifier.testTag("delete_for_me")
                        ) {
                            Text(stringResource(R.string.str_delete_for_me), color = primaryColor, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDeleteForEveryone()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("delete_for_everyone")
                        ) {
                            Text(stringResource(R.string.str_delete_for_everyone), color = Color.White)
                        }
                    } else {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDeleteLocalOnly()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("delete_confirm")
                        ) {
                            Text(stringResource(R.string.str_delete), color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
