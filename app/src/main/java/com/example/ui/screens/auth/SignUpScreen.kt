package com.example.ui.screens.auth

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.util.EmailUtils
import com.example.viewmodel.PlenxoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    viewModel: PlenxoViewModel,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current

    var emailText by remember { mutableStateOf("") }
    var passwordText by remember { mutableStateOf("") }
    var confirmPasswordText by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }

    var isCaptchaVerified by remember { mutableStateOf(false) }
    var isTermsAccepted by remember { mutableStateOf(false) }

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
        statusText = "Sending verification code..."
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
                    text = "Create Plenxo Account",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Sign up to get your unique permanent Plenxo ID",
                    fontSize = 14.sp,
                    color = Color(0xFF8B949E),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Form Card
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
                                .testTag("signup_email_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (emailText.contains("@") && !EmailUtils.isAllowedEmailDomain(emailText)) Color(0xFFEF4444) else Color(0xFF58A6FF),
                                unfocusedBorderColor = if (emailText.contains("@") && !EmailUtils.isAllowedEmailDomain(emailText)) Color(0xFFEF4444) else Color(0xFF30363D),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color(0xFF58A6FF),
                                unfocusedLabelColor = Color(0xFF8B949E)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (emailText.contains("@") && !EmailUtils.isAllowedEmailDomain(emailText)) {
                            Text(
                                text = "Allowed domains: @gmail.com, @outlook.com, @hotmail.com, @icloud.com, @me.com, @protonmail.com, @zoho.com, @mail.com",
                                color = Color(0xFFEF4444),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                            )
                        }

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
                                .testTag("signup_password_input"),
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

                        // Confirm Password Field
                        OutlinedTextField(
                            value = confirmPasswordText,
                            onValueChange = { confirmPasswordText = it },
                            label = { Text("Confirm Password") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF58A6FF)
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isConfirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Confirm Password Visibility",
                                        tint = Color(0xFF8B949E)
                                    )
                                }
                            },
                            visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("signup_confirm_password_input"),
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

                        // Terms & Services Checkbox
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isTermsAccepted = !isTermsAccepted }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = isTermsAccepted,
                                onCheckedChange = { isTermsAccepted = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF1F6FEB),
                                    uncheckedColor = Color(0xFF8B949E),
                                    checkmarkColor = Color.White
                                ),
                                modifier = Modifier.testTag("signup_terms_checkbox")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "I agree to the Terms & Services and Privacy Policy",
                                color = Color(0xFFC9D1D9),
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        val isEmailDomainValid = EmailUtils.isAllowedEmailDomain(emailText)

                        val isFormValid = emailText.isNotBlank() &&
                                android.util.Patterns.EMAIL_ADDRESS.matcher(emailText.trim()).matches() &&
                                isEmailDomainValid &&
                                passwordText.isNotBlank() &&
                                passwordText.length >= 6 &&
                                confirmPasswordText == passwordText &&
                                isCaptchaVerified &&
                                isTermsAccepted

                        // Sign Up Button
                        Button(
                            onClick = {
                                if (!EmailUtils.isAllowedEmailDomain(emailText.trim())) {
                                    Toast.makeText(context, EmailUtils.INVALID_DOMAIN_ERROR_MESSAGE, Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                if (passwordText != confirmPasswordText) {
                                    Toast.makeText(context, "Passwords do not match!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (!isCaptchaVerified) {
                                    Toast.makeText(context, "Please complete CAPTCHA verification.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (!isTermsAccepted) {
                                    Toast.makeText(context, "Please accept Terms & Services.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.sendOtpForSignup(
                                    targetEmail = emailText.trim(),
                                    targetPassword = passwordText
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
                                .testTag("signup_submit_button")
                        ) {
                            Text(
                                text = "Sign Up & Send Code",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Link to Login
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Already have an account? ",
                        color = Color(0xFF8B949E),
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Sign In",
                        color = Color(0xFF58A6FF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onNavigateToLogin() }
                            .testTag("navigate_to_login_link")
                    )
                }
            }
        }
    }
}
