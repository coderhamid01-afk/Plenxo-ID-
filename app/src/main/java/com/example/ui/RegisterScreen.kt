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
import com.example.R
import com.example.model.CaptchaStage
import com.example.ui.components.CaptchaTriggerRow
import com.example.ui.components.DualStageCaptchaDialog
import com.example.ui.theme.*
import com.example.viewmodel.AuthUiEvent
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.PlenxoViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    viewModel: PlenxoViewModel,
    primaryColor: Color = PlenxoPurple
) {
    SignUpScreen(viewModel = viewModel, primaryColor = primaryColor)
}

@Deprecated(
    message = "Legacy RegisterScreen overload using AuthViewModel. The active primary screen is SignUpScreen via PlenxoViewModel.",
    replaceWith = ReplaceWith("SignUpScreen(viewModel, primaryColor)", "com.example.ui.SignUpScreen")
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    authViewModel: AuthViewModel,
    onNavigateToOtp: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    primaryColor: Color = PlenxoPurple
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var emailFocused by remember { mutableStateOf(false) }
    var passwordFocused by remember { mutableStateOf(false) }
    var confirmPasswordFocused by remember { mutableStateOf(false) }

    val isLoading by authViewModel.isLoading.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()
    val captchaStage by authViewModel.captchaStage.collectAsState()
    val isTermsAccepted by authViewModel.isTermsAccepted.collectAsState()
    var showCaptchaDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        authViewModel.uiEvent.collectLatest { event ->
            when (event) {
                is AuthUiEvent.NavigateToOtpScreen -> onNavigateToOtp()
                else -> Unit
            }
        }
    }

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

    val passwordsMatch = password.isNotEmpty() && confirmPassword.isNotEmpty() && password == confirmPassword
    val showPasswordMismatch = confirmPassword.isNotEmpty() && !passwordsMatch

    val isEmailValid = email.isNotBlank() && email.contains("@")
    val isPasswordValid = password.isNotBlank() && confirmPassword.isNotBlank() && passwordsMatch
    val isSignUpButtonEnabled = captchaStage == CaptchaStage.FULLY_VERIFIED && isTermsAccepted && isEmailValid && isPasswordValid && !isLoading

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
                text = "Join Plenxo",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )
            )

            Text(
                text = "Create your account to get started",
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
                                .clickable { onNavigateToLogin() }
                                .padding(vertical = 10.dp)
                                .testTag("navigate_to_login"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Login",
                                color = Color.White.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(primaryGradient)
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sign Up",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Error Message Card
                    AnimatedVisibility(
                        visible = errorMessage != null,
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
                            authViewModel.clearError()
                        },
                        label = {
                            Text(
                                "Email Address",
                                color = if (emailFocused) PlenxoCyan else Color.White.copy(alpha = 0.6f)
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
                            .testTag("register_email_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            authViewModel.clearError()
                        },
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
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
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
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = PlenxoCyan
                        ),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { passwordFocused = it.isFocused }
                            .testTag("register_password_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Confirm Password Field
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            authViewModel.clearError()
                        },
                        label = {
                            Text(
                                "Confirm Password",
                                color = if (confirmPasswordFocused) PlenxoCyan else Color.White.copy(alpha = 0.6f)
                            )
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (confirmPasswordFocused) PlenxoCyan else Color.White.copy(alpha = 0.5f)
                            )
                        },
                        supportingText = {
                            if (showPasswordMismatch) {
                                Text(
                                    text = "Passwords do not match",
                                    color = Color(0xFFFF7875),
                                    fontSize = 12.sp
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (showPasswordMismatch) Color(0xFFFF4D4F) else PlenxoCyan,
                            unfocusedBorderColor = if (showPasswordMismatch) Color(0xFFFF4D4F).copy(alpha = 0.6f) else DarkCardBorder,
                            focusedContainerColor = DarkSurface.copy(alpha = 0.6f),
                            unfocusedContainerColor = DarkSurface.copy(alpha = 0.4f),
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
                                if (isSignUpButtonEnabled) {
                                    authViewModel.sendSignupOtp(email) {
                                        onNavigateToOtp()
                                    }
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { confirmPasswordFocused = it.isFocused }
                            .testTag("register_confirm_password_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sleek Compact Security Verification Trigger Row
                    CaptchaTriggerRow(
                        isVerified = captchaStage == CaptchaStage.FULLY_VERIFIED,
                        onClick = { showCaptchaDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Terms and conditions row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { authViewModel.isTermsAccepted.value = !isTermsAccepted }
                    ) {
                        Checkbox(
                            checked = isTermsAccepted,
                            onCheckedChange = { authViewModel.isTermsAccepted.value = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = PlenxoCyan,
                                uncheckedColor = Color.White.copy(alpha = 0.6f),
                                checkmarkColor = DarkBackground
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "I agree to the Terms of Service & Privacy Policy",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Primary Action Button with Gradient
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            authViewModel.sendSignupOtp(email) {
                                onNavigateToOtp()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("register_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = Color.White.copy(alpha = 0.12f)
                        ),
                        contentPadding = PaddingValues(0.dp),
                        enabled = isSignUpButtonEnabled
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isSignUpButtonEnabled) primaryGradient
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
                                        text = "Create Account",
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

            // Footer navigation hint
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Already have an account? ",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
                Text(
                    text = "Log In",
                    color = PlenxoCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clickable { onNavigateToLogin() }
                        .testTag("navigate_to_login")
                )
            }
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
    }
}
