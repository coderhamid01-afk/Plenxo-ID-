package com.example.ui.screens.auth

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.CaptchaStage
import com.example.ui.components.SecurityKeypad
import com.example.ui.components.SecurityPinDisplay
import com.example.ui.theme.*
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.ResetStep
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Enterprise-grade Forgot Password & Security Challenge Wizard Screen.
 * Implements smooth 8-stage step wizard with zero-tolerance 24-hour account lockdown UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    authViewModel: AuthViewModel = viewModel(),
    onBackToLogin: () -> Unit = {},
    onCompleteReset: (email: String) -> Unit = {}
) {
    val currentStep by authViewModel.currentResetStep.collectAsState()
    val isAccountLocked by authViewModel.isAccountLocked.collectAsState()
    val lockoutTimeRemainingMs by authViewModel.lockoutTimeRemainingMs.collectAsState()
    val securityErrorMessage by authViewModel.securityErrorMessage.collectAsState()
    val targetUserSecurityModel by authViewModel.targetUserSecurityModel.collectAsState()
    val isLoading by authViewModel.isLoading.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()

    val captchaStage by authViewModel.captchaStage.collectAsState()
    val textCaptchaCode by authViewModel.textCaptchaCode.collectAsState()
    val textCaptchaInput by authViewModel.textCaptchaInput.collectAsState()

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Internal state variables for wizard forms
    var emailOrIdInput by remember { mutableStateOf("") }
    var plenxoIdInput by remember { mutableStateOf("") }
    var emailOtpInput by remember { mutableStateOf("") }
    var masterPinInput by remember { mutableStateOf("") }
    var newPasswordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var isNewPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }

    // Sent OTP string
    var sentOtp by remember { mutableStateOf("") }
    var otpCooldownSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(sentOtp) {
        if (sentOtp.isNotBlank()) {
            authViewModel.setGeneratedOtp(sentOtp)
        }
    }

    // Initialize step to EMAIL_INPUT if IDLE
    LaunchedEffect(Unit) {
        if (currentStep == ResetStep.IDLE) {
            authViewModel.setResetStep(ResetStep.EMAIL_INPUT)
        }
    }

    // OTP Cooldown countdown
    LaunchedEffect(otpCooldownSeconds) {
        if (otpCooldownSeconds > 0) {
            delay(1000L)
            otpCooldownSeconds -= 1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        PlenxoDeepSpace,
                        Color(0xFF0D121F),
                        PlenxoDeepSpace
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        authViewModel.resetPasswordResetFlow()
                        onBackToLogin()
                    },
                    modifier = Modifier.testTag("forgot_password_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = PlenxoCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PLENXO RECOVERY",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    )
                }

                // Empty space balancer for symmetry
                Spacer(modifier = Modifier.width(48.dp))
            }

            // -----------------------------------------------------------------
            // 1. ACCOUNT LOCKOUT WARNING SCREEN (24-Hour Lockdown Active)
            // -----------------------------------------------------------------
            if (isAccountLocked) {
                AccountLockoutWarningView(
                    lockoutMs = lockoutTimeRemainingMs,
                    reason = securityErrorMessage
                        ?: targetUserSecurityModel?.lastSecurityViolationReason
                        ?: "24-Hour Security Violation Active",
                    onBackToLogin = {
                        authViewModel.resetPasswordResetFlow()
                        onBackToLogin()
                    }
                )
            } else {
                // -------------------------------------------------------------
                // 2. ACTIVE WIZARD FLOW (ResetSteps)
                // -------------------------------------------------------------

                // Stepper Progress Header
                WizardStepHeader(currentStep = currentStep)

                Spacer(modifier = Modifier.height(20.dp))

                // Error Message Banner if present
                if (errorMessage != null) {
                    ErrorMessageCard(
                        message = errorMessage ?: "",
                        onDismiss = { authViewModel.clearError() }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Step Content Switcher
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) + slideInHorizontally { it / 2 } togetherWith
                                fadeOut(animationSpec = tween(300)) + slideOutHorizontally { -it / 2 }
                    },
                    label = "WizardStepTransition"
                ) { step ->
                    when (step) {
                        ResetStep.IDLE, ResetStep.EMAIL_INPUT, ResetStep.PLENXO_ID_CHECK -> {
                            EmailAndPlenxoIdStepView(
                                emailInput = emailOrIdInput,
                                plenxoIdInput = plenxoIdInput,
                                onEmailChange = { emailOrIdInput = it },
                                onPlenxoIdChange = { plenxoIdInput = it },
                                isLoading = isLoading,
                                onSubmit = {
                                    if (emailOrIdInput.isBlank() || plenxoIdInput.isBlank()) {
                                        Toast.makeText(context, "Please enter both Email and Plenxo ID", Toast.LENGTH_SHORT).show()
                                        return@EmailAndPlenxoIdStepView
                                    }
                                    authViewModel.validateEmailAndPlenxoId(emailOrIdInput, plenxoIdInput) { success, _ ->
                                        if (success) {
                                            authViewModel.requestNetlifyEmailOtp(emailOrIdInput, purpose = "forgot_password") { newOtp ->
                                                if (!newOtp.isNullOrBlank()) {
                                                    sentOtp = newOtp
                                                    otpCooldownSeconds = 60
                                                    Toast.makeText(context, "Verification code sent to your email", Toast.LENGTH_LONG).show()
                                                    authViewModel.setResetStep(ResetStep.EMAIL_OTP)
                                                } else {
                                                    Toast.makeText(context, "Failed to send verification code. Please try again.", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        ResetStep.EMAIL_OTP -> {
                            EmailOtpStepView(
                                otpInput = emailOtpInput,
                                onOtpChange = { if (it.length <= 6) emailOtpInput = it },
                                cooldownSeconds = otpCooldownSeconds,
                                isLoading = isLoading,
                                onResend = {
                                    authViewModel.requestNetlifyEmailOtp(emailOrIdInput, purpose = "forgot_password") { newOtp ->
                                        if (!newOtp.isNullOrBlank()) {
                                            sentOtp = newOtp
                                            otpCooldownSeconds = 60
                                            Toast.makeText(context, "New verification code sent to your email", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Failed to send new code. Please try again.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onSubmit = {
                                    if (emailOtpInput.length < 6) {
                                        Toast.makeText(context, "Please enter 6-digit OTP", Toast.LENGTH_SHORT).show()
                                        return@EmailOtpStepView
                                    }
                                    authViewModel.verifyEmailOtp(emailOtpInput, sentOtp) { verified ->
                                        if (verified) {
                                            val model = targetUserSecurityModel
                                            if (model?.is2FAEnabled == true) {
                                                // 2FA Branch: Route to Security PIN Verification
                                                authViewModel.setResetStep(ResetStep.SECURITY_PIN_VERIFY)
                                            } else {
                                                // Non-2FA Branch: Route directly to Set New Password
                                                authViewModel.setResetStep(ResetStep.NEW_PASSWORD)
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        ResetStep.SECURITY_PIN_VERIFY, ResetStep.MASTER_PIN_CHALLENGE -> {
                            MasterPinStepView(
                                pinValue = masterPinInput,
                                onDigitClick = { digit ->
                                    if (masterPinInput.length < 6) masterPinInput += digit
                                },
                                onBackspaceClick = {
                                    if (masterPinInput.isNotEmpty()) masterPinInput = masterPinInput.dropLast(1)
                                },
                                onClearClick = { masterPinInput = "" },
                                onSubmit = {
                                    if (masterPinInput.length == 6) {
                                        authViewModel.verifySecurityPin(masterPinInput) { verified ->
                                            if (verified) {
                                                authViewModel.setResetStep(ResetStep.NEW_PASSWORD)
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "Enter 6-digit Security PIN", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        ResetStep.CAPTCHA_VERIFY -> {
                            CaptchaVerifyStepView(
                                captchaStage = captchaStage,
                                captchaCode = textCaptchaCode,
                                captchaInput = textCaptchaInput,
                                onCaptchaInputChange = { authViewModel.textCaptchaInput.value = it },
                                onVerifyStage1 = { authViewModel.verifyStage1Text() },
                                onVerifyStage2 = { authViewModel.verifyStage2Slider(it) },
                                onResetCaptcha = { authViewModel.resetCaptcha() },
                                onContinue = {
                                    authViewModel.setResetStep(ResetStep.NEW_PASSWORD)
                                }
                            )
                        }

                        ResetStep.SCANNING_TIMER -> {
                            ScanningTimerStepView(
                                onScanComplete = {
                                    authViewModel.setResetStep(ResetStep.NEW_PASSWORD)
                                }
                            )
                        }

                        ResetStep.NEW_PASSWORD -> {
                            NewPasswordStepView(
                                newPassword = newPasswordInput,
                                confirmPassword = confirmPasswordInput,
                                isNewVisible = isNewPasswordVisible,
                                isConfirmVisible = isConfirmPasswordVisible,
                                isLoading = isLoading,
                                onNewPasswordChange = { newPasswordInput = it },
                                onConfirmPasswordChange = { confirmPasswordInput = it },
                                onToggleNewVisible = { isNewPasswordVisible = !isNewPasswordVisible },
                                onToggleConfirmVisible = { isConfirmPasswordVisible = !isConfirmPasswordVisible },
                                onSubmit = {
                                    if (newPasswordInput.length < 6) {
                                        Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                                        return@NewPasswordStepView
                                    }
                                    if (newPasswordInput != confirmPasswordInput) {
                                        Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                                        return@NewPasswordStepView
                                    }
                                    authViewModel.submitNewPassword(newPasswordInput) { success, _ ->
                                        if (success) {
                                            val finalEmail = targetUserSecurityModel?.email.orEmpty().ifBlank { emailOrIdInput }
                                            onCompleteReset(finalEmail)
                                        }
                                    }
                                }
                            )
                        }

                        ResetStep.SUCCESS -> {
                            SuccessStepView(
                                onReturnToLogin = {
                                    authViewModel.resetPasswordResetFlow()
                                    onBackToLogin()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// SUB-COMPONENTS & WIZARD STEP VIEWS
// =============================================================================

@Composable
private fun AccountLockoutWarningView(
    lockoutMs: Long,
    reason: String,
    onBackToLogin: () -> Unit
) {
    val totalSeconds = (lockoutMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val timeFormatted = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

    val infiniteTransition = rememberInfiniteTransition(label = "ShieldPulse")
    val shieldScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shieldScale"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = PlenxoCardSurfaceDark),
        shape = RoundedCornerShape(24.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(PlenxoError, Color(0xFFFF8800)))),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("account_lockout_warning_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .scale(shieldScale)
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(PlenxoError.copy(alpha = 0.15f))
                    .border(2.dp, PlenxoError, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = PlenxoError,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "ACCOUNT LOCKED FOR 24 HOURS",
                color = PlenxoError,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Zero-Tolerance Security Violation Triggered",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Lockdown Timer Box
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A0E1A), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(vertical = 16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "TIME REMAINING UNTIL UNLOCK",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = timeFormatted,
                        color = PlenxoWarning,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 32.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Violation Reason Card
            Surface(
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "VIOLATION REASON:",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = reason,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "All authentication, password reset attempts, and active logins are suspended. Incoming messages will continue to queue silently.",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onBackToLogin,
                colors = ButtonDefaults.buttonColors(containerColor = PlenxoError),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("lockout_return_login_button")
            ) {
                Text(
                    text = "Return to Login",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun WizardStepHeader(currentStep: ResetStep) {
    val stepIndex = when (currentStep) {
        ResetStep.IDLE, ResetStep.EMAIL_INPUT, ResetStep.PLENXO_ID_CHECK -> 1
        ResetStep.EMAIL_OTP -> 2
        ResetStep.SECURITY_PIN_VERIFY, ResetStep.MASTER_PIN_CHALLENGE -> 3
        ResetStep.CAPTCHA_VERIFY -> 4
        ResetStep.SCANNING_TIMER -> 5
        ResetStep.NEW_PASSWORD -> 6
        ResetStep.SUCCESS -> 7
    }

    val stepName = when (currentStep) {
        ResetStep.IDLE, ResetStep.EMAIL_INPUT, ResetStep.PLENXO_ID_CHECK -> "Identity Verification"
        ResetStep.EMAIL_OTP -> "Email OTP Verification"
        ResetStep.SECURITY_PIN_VERIFY, ResetStep.MASTER_PIN_CHALLENGE -> "2FA Security PIN"
        ResetStep.CAPTCHA_VERIFY -> "Human Verification"
        ResetStep.SCANNING_TIMER -> "Cryptographic Scan"
        ResetStep.NEW_PASSWORD -> "Set New Password"
        ResetStep.SUCCESS -> "Reset Complete"
    }

    val progress = stepIndex / 8f

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STEP $stepIndex OF 8",
                color = PlenxoCyan,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            )
            Text(
                text = stepName,
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = PlenxoCyan,
            trackColor = Color.White.copy(alpha = 0.15f)
        )
    }
}

@Composable
private fun ErrorMessageCard(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PlenxoError.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(PlenxoError, PlenxoError))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = PlenxoError,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// STEP 1: EMAIL & PLENXO ID INPUT
// -----------------------------------------------------------------------------
@Composable
private fun EmailAndPlenxoIdStepView(
    emailInput: String,
    plenxoIdInput: String,
    onEmailChange: (String) -> Unit,
    onPlenxoIdChange: (String) -> Unit,
    isLoading: Boolean,
    onSubmit: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PlenxoCardSurfaceDark),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(PlenxoElectricViolet, PlenxoCyan))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Account Identity Verification",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Please enter both your registered Gmail address and unique Plenxo ID (PX-XXXXXX) to request password recovery.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Email Input
            OutlinedTextField(
                value = emailInput,
                onValueChange = onEmailChange,
                label = { Text("Gmail Address") },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null, tint = PlenxoCyan)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PlenxoCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedLabelColor = PlenxoCyan,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("forgot_email_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Plenxo ID Input
            OutlinedTextField(
                value = plenxoIdInput,
                onValueChange = onPlenxoIdChange,
                label = { Text("Plenxo ID (e.g. PX-123456)") },
                leadingIcon = {
                    Icon(Icons.Default.Badge, contentDescription = null, tint = PlenxoCyan)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PlenxoCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedLabelColor = PlenxoCyan,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("forgot_plenxo_id_input")
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSubmit,
                enabled = !isLoading && emailInput.isNotBlank() && plenxoIdInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PlenxoElectricViolet),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("verify_account_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text(
                        text = "Verify & Send OTP",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// STEP 2: PLENXO ID CHECK
// -----------------------------------------------------------------------------
@Composable
private fun PlenxoIdCheckStepView(
    plenxoId: String,
    onPlenxoIdChange: (String) -> Unit,
    isLoading: Boolean,
    onSubmit: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PlenxoCardSurfaceDark),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(PlenxoCyan, PlenxoElectricViolet))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = PlenxoCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Identity Cross-Verification",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enter your unique Plenxo System ID (formatted as 'PX-XXXXXX'). Zero tolerance policy: an incorrect ID triggers an immediate 24-hour lockdown.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = plenxoId,
                onValueChange = onPlenxoIdChange,
                label = { Text("Plenxo System ID (PX-XXXXXX)") },
                leadingIcon = {
                    Icon(Icons.Default.Badge, contentDescription = null, tint = PlenxoCyan)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PlenxoCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedLabelColor = PlenxoCyan,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("plenxo_id_verification_input")
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSubmit,
                enabled = !isLoading && plenxoId.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PlenxoCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("confirm_plenxo_id_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                } else {
                    Text(
                        text = "Verify Plenxo ID",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// STEP 3: EMAIL OTP
// -----------------------------------------------------------------------------
@Composable
private fun EmailOtpStepView(
    otpInput: String,
    onOtpChange: (String) -> Unit,
    cooldownSeconds: Int,
    isLoading: Boolean,
    onResend: () -> Unit,
    onSubmit: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PlenxoCardSurfaceDark),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(PlenxoElectricViolet, PlenxoCyan))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.MarkEmailRead,
                contentDescription = null,
                tint = PlenxoCyan,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Email OTP Verification",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Enter the 6-digit cryptographic security code sent to your Gmail inbox.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = otpInput,
                onValueChange = onOtpChange,
                label = { Text("6-Digit Security OTP") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PlenxoCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedLabelColor = PlenxoCyan,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .testTag("otp_code_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onResend,
                enabled = cooldownSeconds <= 0
            ) {
                Text(
                    text = if (cooldownSeconds > 0) "Resend Code ($cooldownSeconds s)" else "Resend OTP Code",
                    color = if (cooldownSeconds <= 0) PlenxoCyan else Color.White.copy(alpha = 0.4f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onSubmit,
                enabled = !isLoading && otpInput.length == 6,
                colors = ButtonDefaults.buttonColors(containerColor = PlenxoElectricViolet),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("verify_otp_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text(
                        text = "Verify Security OTP",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// STEP 4: MASTER PIN CHALLENGE (Using SecurityKeypad)
// -----------------------------------------------------------------------------
@Composable
private fun MasterPinStepView(
    pinValue: String,
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit,
    onSubmit: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PlenxoCardSurfaceDark),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(PlenxoError, PlenxoCyan))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = PlenxoWarning,
                modifier = Modifier.size(44.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Master PIN Challenge",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Enter your 6-digit Master Security PIN. Warning: Zero tolerance policy applies.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Animated PIN display
            SecurityPinDisplay(pinValue = pinValue, maxLength = 6)

            Spacer(modifier = Modifier.height(24.dp))

            // Secure Keypad
            SecurityKeypad(
                pinValue = pinValue,
                maxLength = 6,
                onDigitClick = onDigitClick,
                onBackspaceClick = onBackspaceClick,
                onClearClick = onClearClick,
                onConfirmClick = onSubmit
            )
        }
    }
}

// -----------------------------------------------------------------------------
// STEP 5: CAPTCHA VERIFY
// -----------------------------------------------------------------------------
@Composable
private fun CaptchaVerifyStepView(
    captchaStage: CaptchaStage,
    captchaCode: String,
    captchaInput: String,
    onCaptchaInputChange: (String) -> Unit,
    onVerifyStage1: () -> Boolean,
    onVerifyStage2: (Boolean) -> Unit,
    onResetCaptcha: () -> Unit,
    onContinue: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PlenxoCardSurfaceDark),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(PlenxoCyan, PlenxoElectricViolet))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = null,
                tint = PlenxoCyan,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Human Identity Verification",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Complete the dual-stage verification to confirm you are human.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Stage 1: Text CAPTCHA
            if (captchaStage == CaptchaStage.LOCKED) {
                Surface(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(PlenxoCyan, PlenxoElectricViolet))),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = captchaCode,
                        color = PlenxoCyan,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }

                OutlinedTextField(
                    value = captchaInput,
                    onValueChange = onCaptchaInputChange,
                    label = { Text("Enter Captcha Text") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PlenxoCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(0.8f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { onVerifyStage1() },
                    colors = ButtonDefaults.buttonColors(containerColor = PlenxoCyan, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Verify Stage 1 Text", fontWeight = FontWeight.Bold)
                }
            } else if (captchaStage == CaptchaStage.STAGE_1_CLEARED) {
                // Stage 2: Slider Puzzle
                Text(
                    text = "Stage 1 Cleared! Click to align puzzle lock.",
                    color = PlenxoSuccess,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onVerifyStage2(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = PlenxoElectricViolet),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(48.dp)
                ) {
                    Text("Align & Lock Puzzle", fontWeight = FontWeight.Bold)
                }
            } else if (captchaStage == CaptchaStage.FULLY_VERIFIED) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = PlenxoSuccess,
                    modifier = Modifier.size(56.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Dual-Stage Verification Passed!",
                    color = PlenxoSuccess,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(containerColor = PlenxoSuccess, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("captcha_continue_button")
                ) {
                    Text("Proceed to Security Scan", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// STEP 6: SCANNING TIMER (60s Diagnostic Countdown)
// -----------------------------------------------------------------------------
@Composable
private fun ScanningTimerStepView(
    onScanComplete: () -> Unit
) {
    var secondsLeft by remember { mutableIntStateOf(60) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
        onScanComplete()
    }

    val scanLabel = when {
        secondsLeft > 45 -> "Verifying identity tokens & session hashes..."
        secondsLeft > 30 -> "Scanning account audit logs for security anomalies..."
        secondsLeft > 15 -> "Checking SHA-256 cryptographic signatures..."
        else -> "Preparing secure password update pipe..."
    }

    val progress = (60 - secondsLeft) / 60f

    Card(
        colors = CardDefaults.cardColors(containerColor = PlenxoCardSurfaceDark),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(PlenxoCyan, PlenxoElectricViolet))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Cryptographic Security Scan",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = PlenxoCyan,
                    strokeWidth = 8.dp,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${secondsLeft}s",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Text(
                        text = "REMAINING",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = scanLabel,
                color = PlenxoCyan,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onScanComplete) {
                Text("Bypass Scan Countdown", color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

// -----------------------------------------------------------------------------
// STEP 7: NEW PASSWORD
// -----------------------------------------------------------------------------
@Composable
private fun NewPasswordStepView(
    newPassword: String,
    confirmPassword: String,
    isNewVisible: Boolean,
    isConfirmVisible: Boolean,
    isLoading: Boolean,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onToggleNewVisible: () -> Unit,
    onToggleConfirmVisible: () -> Unit,
    onSubmit: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PlenxoCardSurfaceDark),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(PlenxoElectricViolet, PlenxoCyan))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Set New Password",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Choose a strong password with at least 6 characters.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = newPassword,
                onValueChange = onNewPasswordChange,
                label = { Text("New Password") },
                singleLine = true,
                visualTransformation = if (isNewVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleNewVisible) {
                        Icon(
                            imageVector = if (isNewVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PlenxoCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("new_password_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = { Text("Confirm New Password") },
                singleLine = true,
                visualTransformation = if (isConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleConfirmVisible) {
                        Icon(
                            imageVector = if (isConfirmVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PlenxoCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("confirm_password_input")
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSubmit,
                enabled = !isLoading && newPassword.length >= 6 && newPassword == confirmPassword,
                colors = ButtonDefaults.buttonColors(containerColor = PlenxoElectricViolet),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_new_password_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("Update Password", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// STEP 8: SUCCESS
// -----------------------------------------------------------------------------
@Composable
private fun SuccessStepView(
    onReturnToLogin: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PlenxoCardSurfaceDark),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(PlenxoSuccess, PlenxoCyan))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = PlenxoSuccess,
                modifier = Modifier.size(72.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Password Reset Complete!",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your Plenxo security credentials have been updated successfully. You can now sign in with your new password.",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onReturnToLogin,
                colors = ButtonDefaults.buttonColors(containerColor = PlenxoSuccess, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("success_return_login_button")
            ) {
                Text("Return to Login", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}
