package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.model.CaptchaStage
import com.example.ui.components.CaptchaTriggerRow
import com.example.ui.components.DualStageCaptchaDialog
import com.example.ui.components.LockoutBanner
import com.example.ui.theme.*
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.PlenxoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: PlenxoViewModel,
    primaryColor: Color = PlenxoPurple,
    authViewModel: AuthViewModel = viewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var emailFocused by remember { mutableStateOf(false) }
    var passwordFocused by remember { mutableStateOf(false) }

    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val captchaStage by viewModel.captchaStage.collectAsState()
    var showCaptchaDialog by remember { mutableStateOf(false) }

    val isAccountLocked by authViewModel.isAccountLocked.collectAsState()
    val lockoutTimeRemainingMs by authViewModel.lockoutTimeRemainingMs.collectAsState()
    val securityErrorMessage by authViewModel.securityErrorMessage.collectAsState()
    val targetUserSecurityModel by authViewModel.targetUserSecurityModel.collectAsState()

    LaunchedEffect(email) {
        val trimmed = email.trim()
        if (trimmed.isNotBlank() && (trimmed.contains("@") || trimmed.startsWith("PX-"))) {
            authViewModel.checkAccountLockoutStatus(trimmed)
        }
    }

    val isEmailValid = email.isNotBlank()
    val isPasswordValid = password.isNotBlank()
    val isLoginButtonEnabled = !isAccountLocked && captchaStage == CaptchaStage.FULLY_VERIFIED && isEmailValid && isPasswordValid && !isLoading
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    val bgGradient = Brush.verticalGradient(
        colors = listOf(
            DarkBackground,
            DarkSurface,
            Color(0xFF0F172A)
        )
    )

    val primaryGradient = Brush.horizontalGradient(
        colors = listOf(PlenxoPurple, PlenxoIndigo, PlenxoBlue)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        // Glowing Background Circles
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-80).dp, y = (-80).dp)
                .clip(CircleShape)
                .background(PlenxoPurple.copy(alpha = 0.15f))
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = 60.dp)
                .clip(CircleShape)
                .background(PlenxoBlue.copy(alpha = 0.15f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Brand Icon & Header
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(PlenxoPurple, PlenxoCyan)))
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "Plenxo Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome Back",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )
            )

            Text(
                text = "Sign in to connect on Plenxo",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.7f)
                ),
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
            )

            // Glassmorphism Card Container
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = PlenxoPurple,
                        spotColor = PlenxoIndigo
                    ),
                shape = RoundedCornerShape(28.dp),
                color = DarkCardBg.copy(alpha = 0.88f),
                border = BorderStroke(width = 1.dp, color = PlenxoPurple.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Tab Switcher (Login / Signup)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(primaryGradient)
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Login",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.navigateToSignup() }
                                .padding(vertical = 10.dp)
                                .testTag("navigate_to_signup"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sign Up",
                                color = Color.White.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isAccountLocked) {
                        LockoutBanner(
                            lockoutTimeRemainingMs = lockoutTimeRemainingMs,
                            violationReason = securityErrorMessage ?: targetUserSecurityModel?.lastSecurityViolationReason ?: "24-Hour Security Lockdown Active",
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    // Error Message Card
                    AnimatedVisibility(
                        visible = errorMessage != null && !isAccountLocked,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            color = Color(0x33FF4D4F),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFFF4D4F).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = Color(0xFFFF7875),
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                            )
                        }
                    }

                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            viewModel.email.value = it
                            viewModel.resetOtpState()
                            viewModel.clearError()
                        },
                        enabled = !isAccountLocked,
                        label = {
                            Text(
                                "Email or Plenxo ID",
                                color = if (emailFocused) PlenxoCyan else Color.White.copy(alpha = 0.6f)
                            )
                        },
                        placeholder = {
                            Text(
                                "e.g. user@gmail.com or PX-990007",
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 13.sp
                            )
                        },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = if (emailFocused) PlenxoCyan else Color.White.copy(alpha = 0.5f)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PlenxoCyan,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedContainerColor = DarkSurface.copy(alpha = 0.6f),
                            unfocusedContainerColor = DarkSurface.copy(alpha = 0.4f),
                            disabledContainerColor = DarkSurface.copy(alpha = 0.2f),
                            disabledTextColor = Color.White.copy(alpha = 0.4f),
                            disabledBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = PlenxoCyan
                        ),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { emailFocused = it.isFocused }
                            .testTag("login_email_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            viewModel.clearError()
                        },
                        enabled = !isAccountLocked,
                        label = {
                            Text(
                                "Password",
                                color = if (passwordFocused) PlenxoCyan else Color.White.copy(alpha = 0.6f)
                            )
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (passwordFocused) PlenxoCyan else Color.White.copy(alpha = 0.5f)
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                enabled = !isAccountLocked
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PlenxoCyan,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedContainerColor = DarkSurface.copy(alpha = 0.6f),
                            unfocusedContainerColor = DarkSurface.copy(alpha = 0.4f),
                            disabledContainerColor = DarkSurface.copy(alpha = 0.2f),
                            disabledTextColor = Color.White.copy(alpha = 0.4f),
                            disabledBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = PlenxoCyan
                        ),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (email.isNotBlank() && password.isNotBlank() && !isLoading) {
                                    viewModel.email.value = email
                                    viewModel.password.value = password
                                    viewModel.onLoginClicked()
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { passwordFocused = it.isFocused }
                            .testTag("login_password_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Forgot Password Link
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = "Forgot Password?",
                            color = PlenxoCyan,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable { viewModel.navigateToForgotPassword() }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sleek Compact Security Verification Trigger Row
                    CaptchaTriggerRow(
                        isVerified = captchaStage == CaptchaStage.FULLY_VERIFIED,
                        onClick = { showCaptchaDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TermsAndPrivacyCheckboxRow(
                        viewModel = viewModel,
                        errorMessage = errorMessage
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Primary Action Button with Gradient
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.email.value = email
                            viewModel.password.value = password
                            viewModel.onLoginClicked()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("login_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = Color.White.copy(alpha = 0.12f)
                        ),
                        contentPadding = PaddingValues(0.dp),
                        enabled = isLoginButtonEnabled
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isLoginButtonEnabled) primaryGradient
                                    else Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Login to Account",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        // Full Screen Loading Overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PlenxoCyan, strokeWidth = 3.dp)
            }
        }

        // Dual-Stage CAPTCHA Modal Dialog
        DualStageCaptchaDialog(
            isOpen = showCaptchaDialog,
            onDismissRequest = { showCaptchaDialog = false },
            viewModel = viewModel
        )
    }
}
