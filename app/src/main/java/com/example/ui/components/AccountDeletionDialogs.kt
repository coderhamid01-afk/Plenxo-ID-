package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * DIALOG 1: RE-AUTHENTICATION (PASSWORD VERIFICATION)
 * Prompts user for password before proceeding to final deletion warning.
 */
@Composable
fun AccountDeletionReAuthDialog(
    show: Boolean,
    errorMessage: String?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onContinue: (password: String) -> Unit
) {
    if (!show) return

    var passwordText by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val darkBg = Color(0xFF1C2234)
    val textWhite = Color(0xFFFFFFFF)
    val textMuted = Color(0xFF9CA5BE)
    val accentBlue = Color(0xFF58A6FF)
    val errorRed = Color(0xFFEF4444)

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        modifier = Modifier.testTag("account_deletion_reauth_dialog"),
        containerColor = darkBg,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = accentBlue,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Confirm Your Password",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textWhite
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "For security reasons, please enter your current account password to proceed with account deletion.",
                    fontSize = 14.sp,
                    color = textMuted,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = passwordText,
                    onValueChange = {
                        passwordText = it
                        localError = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_deletion_password_input"),
                    label = { Text("Account Password", color = textMuted) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = "Toggle password visibility", tint = textMuted)
                        }
                    },
                    isError = (errorMessage != null || localError != null),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentBlue,
                        unfocusedBorderColor = Color(0xFF2E3B5E),
                        errorBorderColor = errorRed,
                        focusedTextColor = textWhite,
                        unfocusedTextColor = textWhite
                    )
                )

                val displayError = errorMessage ?: localError
                if (!displayError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = displayError,
                        color = errorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("account_deletion_password_error")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (passwordText.isBlank()) {
                        localError = "Password cannot be empty"
                    } else {
                        onContinue(passwordText)
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("account_deletion_continue_btn")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Continue", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = textMuted),
                modifier = Modifier.testTag("account_deletion_cancel_reauth_btn")
            ) {
                Text("Cancel")
            }
        }
    )
}

/**
 * DIALOG 2: PERMANENT DATA LOSS WARNING & FINAL CONSENT
 * App-themed warning dialog with Red accent confirmation button.
 */
@Composable
fun AccountDeletionConfirmationDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    if (!show) return

    val darkBg = Color(0xFF1C2234)
    val textWhite = Color(0xFFFFFFFF)
    val textMuted = Color(0xFF9CA5BE)
    val warningRed = Color(0xFFEF4444)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("account_deletion_confirm_dialog"),
        containerColor = darkBg,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = warningRed,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = "Delete Account Permanently?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = warningRed
                )
            }
        },
        text = {
            Text(
                text = "WARNING: This action is permanent and CANNOT be undone. All your chat history, profile information, friend requests, uploaded media, and settings will be erased forever.",
                fontSize = 14.sp,
                color = textWhite,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(containerColor = warningRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("account_deletion_final_delete_btn")
            ) {
                Text("Delete My Account", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = textMuted),
                modifier = Modifier.testTag("account_deletion_cancel_final_btn")
            ) {
                Text("Cancel")
            }
        }
    )
}

/**
 * LOADING STATE OVERLAY
 * Non-dismissible loading dialog displayed while account deletion executes in background.
 */
@Composable
fun AccountDeletionLoadingOverlay(show: Boolean) {
    if (!show) return

    Dialog(
        onDismissRequest = { /* Non-dismissible */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1C2234),
            shadowElevation = 12.dp,
            modifier = Modifier.testTag("account_deletion_loading_overlay")
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFEF4444),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Deleting account and cleaning up data...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Please do not close the app.",
                    fontSize = 13.sp,
                    color = Color(0xFF9CA5BE),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
