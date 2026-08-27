package com.example.ui.screens.auth

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.CaptchaComponent
import com.example.ui.components.PlenxoLoaderOverlay
import com.example.viewmodel.PlenxoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: PlenxoViewModel,
    onNavigateToSignUp: () -> Unit
) {
    val context = LocalContext.current

    var emailText by remember { mutableStateOf("") }
    var passwordText by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var isCaptchaVerified by remember { mutableStateOf(false) }
    var rememberMeChecked by remember { mutableStateOf(true) }

    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(errorMessage) {
        val msg = errorMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    PlenxoLoaderOverlay(
        isLoading = isLoading,
        statusText = "Signing in to Plenxo..."
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0D1117),
                            Color(0xFF161B22),
                            Color(0xFF0D1117)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Header Logo & Title
                Image(
                    painter = painterResource(id = R.drawable.ic_plenxo_logo),
                    contentDescription = "Plenxo Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Welcome Back",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Sign in to access your chats and profile",
                    fontSize = 14.sp,
                    color = Color(0xFF8B949E),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Card Container
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF30363D), Color(0xFF1F6FEB))
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // Email Field
                        OutlinedTextField(
                            value = emailText,
                            onValueChange = { emailText = it },
                            label = { Text("Email Address") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = Color(0xFF58A6FF)
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_email_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF58A6FF),
                                unfocusedBorderColor = Color(0xFF30363D),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color(0xFF58A6FF),
                                unfocusedLabelColor = Color(0xFF8B949E)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Password Field
                        OutlinedTextField(
                            value = passwordText,
                            onValueChange = { passwordText = it },
                            label = { Text("Password") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF58A6FF)
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Password Visibility",
                                        tint = Color(0xFF8B949E)
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_password_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF58A6FF),
                                unfocusedBorderColor = Color(0xFF30363D),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color(0xFF58A6FF),
                                unfocusedLabelColor = Color(0xFF8B949E)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Captcha Component
                        CaptchaComponent(
                            onCaptchaVerifiedChanged = { isVerified ->
                                isCaptchaVerified = isVerified
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Remember Me / Terms Checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { rememberMeChecked = !rememberMeChecked }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = rememberMeChecked,
                                onCheckedChange = { rememberMeChecked = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF1F6FEB),
                                    uncheckedColor = Color(0xFF8B949E),
                                    checkmarkColor = Color.White
                                ),
                                modifier = Modifier.testTag("login_remember_checkbox")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Remember me & agree to Terms",
                                color = Color(0xFFC9D1D9),
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        val isFormValid = emailText.isNotBlank() &&
                                passwordText.isNotBlank() &&
                                isCaptchaVerified

                        // Login Button
                        Button(
                            onClick = {
                                if (!isCaptchaVerified) {
                                    Toast.makeText(context, "Please complete CAPTCHA verification.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.loginUser(
                                    emailInput = emailText.trim(),
                                    passwordInput = passwordText
                                )
                            },
                            enabled = isFormValid,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1F6FEB),
                                disabledContainerColor = Color(0xFF1F6FEB).copy(alpha = 0.4f),
                                contentColor = Color.White,
                                disabledContentColor = Color.White.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("login_submit_button")
                        ) {
                            Text(
                                text = "Sign In",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Link to Sign Up
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Don't have an account? ",
                        color = Color(0xFF8B949E),
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Sign Up",
                        color = Color(0xFF58A6FF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onNavigateToSignUp() }
                            .testTag("navigate_to_signup_link")
                    )
                }
            }
        }
    }
}
