package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.viewmodel.PlenxoViewModel

/**
 * Modern Material 3 Dialog to set a 6-digit Secret Master PIN and enable 2FA.
 */
@Composable
fun SetMasterPinDialog(
    viewModel: PlenxoViewModel,
    onDismiss: () -> Unit
) {
    var masterPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showMasterPin by remember { mutableStateOf(false) }
    var showConfirmPin by remember { mutableStateOf(false) }
    var localValidationError by remember { mutableStateOf<String?>(null) }

    val isSettingUp by viewModel.isSettingUp2FA.collectAsState()
    val setupError by viewModel.setup2FAError.collectAsState()

    val darkBg = Color(0xFF0D1117)
    val cardBg = Color(0xFF161B22)
    val strokeBorder = Color(0xFF30363D)
    val accentBlue = Color(0xFF58A6FF)
    val errorRed = Color(0xFFF85149)
    val textWhite = Color(0xFFF0F6FC)
    val textMuted = Color(0xFF8B949E)

    Dialog(
        onDismissRequest = {
            if (!isSettingUp) {
                viewModel.clear2FAError()
                onDismiss()
            }
        },
        properties = DialogProperties(dismissOnBackPress = !isSettingUp, dismissOnClickOutside = !isSettingUp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, strokeBorder, RoundedCornerShape(24.dp))
                .testTag("set_master_pin_dialog"),
            color = cardBg,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(accentBlue.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, accentBlue.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = accentBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Set 6-Digit Master PIN",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = textWhite,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Enter a secure 6-digit Secret Master PIN to enable Two-Factor Authentication (2FA) for your Plenxo account.",
                    fontSize = 13.sp,
                    color = textMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Error message banner
                val displayedError = localValidationError ?: setupError
                AnimatedVisibility(visible = displayedError != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        color = errorRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, errorRed.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = errorRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = displayedError.orEmpty(),
                                color = errorRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // PIN Input Field
                OutlinedTextField(
                    value = masterPin,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() }.take(6)
                        masterPin = filtered
                        localValidationError = null
                        viewModel.clear2FAError()
                    },
                    label = { Text("6-Digit Master PIN", fontSize = 13.sp) },
                    placeholder = { Text("••••••", color = textMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Next
                    ),
                    visualTransformation = if (showMasterPin) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showMasterPin = !showMasterPin }) {
                            Icon(
                                imageVector = if (showMasterPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle PIN visibility",
                                tint = textMuted
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentBlue,
                        unfocusedBorderColor = strokeBorder,
                        focusedContainerColor = darkBg,
                        unfocusedContainerColor = darkBg,
                        focusedTextColor = textWhite,
                        unfocusedTextColor = textWhite,
                        focusedLabelColor = accentBlue,
                        unfocusedLabelColor = textMuted
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_master_pin")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Confirm PIN Input Field
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() }.take(6)
                        confirmPin = filtered
                        localValidationError = null
                        viewModel.clear2FAError()
                    },
                    label = { Text("Confirm 6-Digit Master PIN", fontSize = 13.sp) },
                    placeholder = { Text("••••••", color = textMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (masterPin.length == 6 && confirmPin.length == 6) {
                                if (masterPin != confirmPin) {
                                    localValidationError = "PINs do not match."
                                } else {
                                    viewModel.enable2FA(masterPin) { success, _ ->
                                        if (success) onDismiss()
                                    }
                                }
                            }
                        }
                    ),
                    visualTransformation = if (showConfirmPin) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showConfirmPin = !showConfirmPin }) {
                            Icon(
                                imageVector = if (showConfirmPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle confirm PIN visibility",
                                tint = textMuted
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentBlue,
                        unfocusedBorderColor = strokeBorder,
                        focusedContainerColor = darkBg,
                        unfocusedContainerColor = darkBg,
                        focusedTextColor = textWhite,
                        unfocusedTextColor = textWhite,
                        focusedLabelColor = accentBlue,
                        unfocusedLabelColor = textMuted
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_confirm_master_pin")
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.clear2FAError()
                            onDismiss()
                        },
                        enabled = !isSettingUp,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textMuted),
                        border = androidx.compose.foundation.BorderStroke(1.dp, strokeBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_cancel_master_pin")
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            if (masterPin.length != 6) {
                                localValidationError = "Master PIN must be exactly 6 numeric digits."
                            } else if (confirmPin.length != 6) {
                                localValidationError = "Please confirm the 6-digit Master PIN."
                            } else if (masterPin != confirmPin) {
                                localValidationError = "PIN confirmation does not match."
                            } else {
                                localValidationError = null
                                viewModel.enable2FA(masterPin) { success, _ ->
                                    if (success) onDismiss()
                                }
                            }
                        },
                        enabled = !isSettingUp && masterPin.length == 6 && confirmPin.length == 6,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentBlue,
                            disabledContainerColor = accentBlue.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_enable_2fa_submit")
                    ) {
                        if (isSettingUp) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Enable 2FA", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern Material 3 Dialog to verify 6-digit Master PIN and disable 2FA.
 */
@Composable
fun Disable2FADialog(
    viewModel: PlenxoViewModel,
    onDismiss: () -> Unit
) {
    var masterPin by remember { mutableStateOf("") }
    var showMasterPin by remember { mutableStateOf(false) }
    var localValidationError by remember { mutableStateOf<String?>(null) }

    val isSettingUp by viewModel.isSettingUp2FA.collectAsState()
    val setupError by viewModel.setup2FAError.collectAsState()

    val darkBg = Color(0xFF0D1117)
    val cardBg = Color(0xFF161B22)
    val strokeBorder = Color(0xFF30363D)
    val dangerRed = Color(0xFFF85149)
    val textWhite = Color(0xFFF0F6FC)
    val textMuted = Color(0xFF8B949E)

    Dialog(
        onDismissRequest = {
            if (!isSettingUp) {
                viewModel.clear2FAError()
                onDismiss()
            }
        },
        properties = DialogProperties(dismissOnBackPress = !isSettingUp, dismissOnClickOutside = !isSettingUp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, strokeBorder, RoundedCornerShape(24.dp))
                .testTag("disable_2fa_dialog"),
            color = cardBg,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(dangerRed.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, dangerRed.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = dangerRed,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Disable Two-Factor Authentication",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textWhite,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Enter your current 6-digit Secret Master PIN to turn off Two-Factor Authentication.",
                    fontSize = 13.sp,
                    color = textMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Error message banner
                val displayedError = localValidationError ?: setupError
                AnimatedVisibility(visible = displayedError != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        color = dangerRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, dangerRed.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = dangerRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = displayedError.orEmpty(),
                                color = dangerRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Master PIN Input Field
                OutlinedTextField(
                    value = masterPin,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() }.take(6)
                        masterPin = filtered
                        localValidationError = null
                        viewModel.clear2FAError()
                    },
                    label = { Text("Current 6-Digit Master PIN", fontSize = 13.sp) },
                    placeholder = { Text("••••••", color = textMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (masterPin.length == 6) {
                                viewModel.disable2FA(masterPin) { success, _ ->
                                    if (success) onDismiss()
                                }
                            }
                        }
                    ),
                    visualTransformation = if (showMasterPin) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showMasterPin = !showMasterPin }) {
                            Icon(
                                imageVector = if (showMasterPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle PIN visibility",
                                tint = textMuted
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = dangerRed,
                        unfocusedBorderColor = strokeBorder,
                        focusedContainerColor = darkBg,
                        unfocusedContainerColor = darkBg,
                        focusedTextColor = textWhite,
                        unfocusedTextColor = textWhite,
                        focusedLabelColor = dangerRed,
                        unfocusedLabelColor = textMuted
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_current_master_pin")
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.clear2FAError()
                            onDismiss()
                        },
                        enabled = !isSettingUp,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textMuted),
                        border = androidx.compose.foundation.BorderStroke(1.dp, strokeBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_cancel_disable_2fa")
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            if (masterPin.length != 6) {
                                localValidationError = "Please enter your 6-digit Master PIN."
                            } else {
                                localValidationError = null
                                viewModel.disable2FA(masterPin) { success, _ ->
                                    if (success) onDismiss()
                                }
                            }
                        },
                        enabled = !isSettingUp && masterPin.length == 6,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = dangerRed,
                            disabledContainerColor = dangerRed.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_confirm_disable_2fa")
                    ) {
                        if (isSettingUp) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Disable 2FA", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
