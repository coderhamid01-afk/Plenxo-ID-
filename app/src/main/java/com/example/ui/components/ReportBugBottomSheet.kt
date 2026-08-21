package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.example.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportBugBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Auto-fetch user email from FirebaseAuth or SessionManager
    val userEmail = remember {
        try {
            val emailFromFirebase = FirebaseAuth.getInstance().currentUser?.email
            if (!emailFromFirebase.isNullOrBlank()) {
                emailFromFirebase
            } else {
                SessionManager.getLoginState(context).email ?: "Not available"
            }
        } catch (e: Exception) {
            SessionManager.getLoginState(context).email ?: "Not available"
        }
    }

    var phoneNumber by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    var phoneError by remember { mutableStateOf<String?>(null) }
    var descriptionError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState,
        containerColor = Color(0xFF16161A),
        contentColor = Color.White,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0xFF33333D))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = Color(0xFFFF3B30),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Report a Bug",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Send a bug report directly to the Plenxo support team.",
                        fontSize = 12.sp,
                        color = Color(0xFF8E8E93)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF26262F), modifier = Modifier.padding(bottom = 20.dp))

            // 1. Auto-fetched Email (Read-Only)
            Text(
                text = "Registered Email Address",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = userEmail,
                onValueChange = {},
                readOnly = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = Color(0xFF8E8E93)
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Read only",
                        tint = Color(0xFF8E8E93)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor = Color(0xFF33333D),
                    disabledTextColor = Color(0xFFE5E5EA),
                    disabledContainerColor = Color(0xFF0F0F11),
                    disabledLeadingIconColor = Color(0xFF8E8E93),
                    disabledTrailingIconColor = Color(0xFF8E8E93),
                    disabledLabelColor = Color(0xFF8E8E93)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. 6-Digit App Phone Number / ID
            Text(
                text = "6-Digit App Phone Number / ID",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { newValue ->
                    if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
                        phoneNumber = newValue
                        if (phoneError != null) phoneError = null
                    }
                },
                enabled = !isSubmitting,
                placeholder = { Text("Enter 6-digit number (e.g. 123456)", color = Color(0xFF636366)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = Color(0xFF8A2BE2)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = phoneError != null,
                supportingText = {
                    if (phoneError != null) {
                        Text(text = phoneError!!, color = Color(0xFFFF3B30), fontSize = 12.sp)
                    } else {
                        Text(text = "${phoneNumber.length}/6 digits required", color = Color(0xFF8E8E93), fontSize = 11.sp)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8A2BE2),
                    unfocusedBorderColor = Color(0xFF33333D),
                    focusedContainerColor = Color(0xFF0F0F11),
                    unfocusedContainerColor = Color(0xFF0F0F11),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Describe the Bug
            Text(
                text = "Describe the Bug",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it
                    if (descriptionError != null) descriptionError = null
                },
                enabled = !isSubmitting,
                placeholder = { Text("Describe what happened or steps to reproduce...", color = Color(0xFF636366)) },
                minLines = 4,
                maxLines = 8,
                isError = descriptionError != null,
                supportingText = {
                    if (descriptionError != null) {
                        Text(text = descriptionError!!, color = Color(0xFFFF3B30), fontSize = 12.sp)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8A2BE2),
                    unfocusedBorderColor = Color(0xFF33333D),
                    focusedContainerColor = Color(0xFF0F0F11),
                    unfocusedContainerColor = Color(0xFF0F0F11),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Submit Button
            Button(
                onClick = {
                    val cleanPhone = phoneNumber.trim()
                    val cleanDesc = description.trim()

                    var hasError = false
                    if (cleanPhone.length != 6 || !cleanPhone.all { it.isDigit() }) {
                        phoneError = "Please enter exactly 6 digits."
                        hasError = true
                    }
                    if (cleanDesc.isEmpty()) {
                        descriptionError = "Bug description cannot be empty."
                        hasError = true
                    }

                    if (hasError) return@Button

                    isSubmitting = true
                    coroutineScope.launch {
                        try {
                            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
                            val reportMap = mapOf(
                                "phoneNumber" to cleanPhone,
                                "description" to cleanDesc,
                                "userId" to userId,
                                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )
                            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("bug_reports")
                                .add(reportMap)
                                .await()

                            withContext(Dispatchers.Main) {
                                isSubmitting = false
                                Toast.makeText(
                                    context,
                                    "Bug report submitted successfully! Thank you.",
                                    Toast.LENGTH_LONG
                                ).show()
                                onDismiss()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isSubmitting = false
                                Toast.makeText(
                                    context,
                                    "Failed to send report: ${e.localizedMessage}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8A2BE2),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF4A148C),
                    disabledContentColor = Color(0xFF8E8E93)
                )
            ) {
                if (isSubmitting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Submitting Report...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("Submit Bug Report", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
