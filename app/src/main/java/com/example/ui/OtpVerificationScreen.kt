package com.example.ui

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.PlenxoLoaderOverlay
import com.example.ui.theme.PlenxoColors
import com.example.ui.theme.PlenxoTypography
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.PlenxoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpVerificationScreen(
    viewModel: PlenxoViewModel,
    primaryColor: Color
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val email by viewModel.email.collectAsState()
    val enteredOtp by viewModel.enteredOtp.collectAsState()
    val secondsRemaining by viewModel.secondsRemaining.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isOtpFrozen by viewModel.isOtpButtonFrozen.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(errorMessage) {
        val msg = errorMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    // Auto-paste detection from clipboard
    LaunchedEffect(Unit) {
        val clipData = clipboardManager.getText()
        val clipText = clipData?.text?.trim() ?: ""
        if (clipText.length == 6 && clipText.all { it.isDigit() }) {
            viewModel.enteredOtp.value = clipText
            Toast.makeText(context, "OTP code detected & pasted from clipboard!", Toast.LENGTH_SHORT).show()
        }
    }

    PlenxoLoaderOverlay(
        isLoading = isLoading,
        statusText = "Verifying security code..."
    ) {
        androidx.activity.compose.BackHandler {
            viewModel.navigateToSignup()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0E1A)) // Sleek dark space canvas
        ) {
            // Background Radial Glow
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                PlenxoColors.Primary.copy(alpha = 0.25f),
                                Color(0xFF0A0E1A)
                            ),
                            radius = 900f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Navigation Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.navigateToSignup() },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back arrow",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Verification",
                        style = PlenxoTypography.Title.copy(color = Color.White)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Glassmorphic Card Container
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0x1AFFFFFF), // Glass translucent
                    border = BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                PlenxoColors.Primary.copy(alpha = 0.5f),
                                PlenxoColors.Secondary.copy(alpha = 0.3f)
                            )
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Resend Code Animated Circular Progress Timer Dial
                        Box(
                            modifier = Modifier.size(110.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val progressAnim by animateFloatAsState(
                                targetValue = (secondsRemaining / 150f).coerceIn(0f, 1f),
                                animationSpec = tween(500),
                                label = "otpTimer"
                            )

                            // Circular countdown background ring
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokePx = 6.dp.toPx()
                                drawCircle(
                                    color = Color(0x22FFFFFF),
                                    style = Stroke(width = strokePx)
                                )
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        listOf(PlenxoColors.Primary, PlenxoColors.Secondary)
                                    ),
                                    startAngle = -90f,
                                    sweepAngle = 360f * progressAnim,
                                    useCenter = false,
                                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Security Lock",
                                    tint = if (secondsRemaining > 10) PlenxoColors.Secondary else Color.Red,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "${secondsRemaining}s",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Enter 6-Digit Code",
                            style = PlenxoTypography.Title.copy(
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "We sent a 6-digit security code to:\n$email",
                            style = PlenxoTypography.Body.copy(
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // 6-Digit Segmented PIN Input View
                        SegmentedOtpInput(
                            otpValue = enteredOtp,
                            onOtpChanged = { newOtp ->
                                if (newOtp.length <= 6) {
                                    viewModel.enteredOtp.value = newOtp
                                    if (newOtp.length == 6) {
                                        keyboardController?.hide()
                                        viewModel.onVerifyOtpClicked()
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Auto-Paste Button helper if clipboard contains code
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val clipData = clipboardManager.getText()
                                    val clipText = clipData?.text?.trim() ?: ""
                                    if (clipText.length == 6 && clipText.all { it.isDigit() }) {
                                        viewModel.enteredOtp.value = clipText
                                        Toast.makeText(context, "Pasted $clipText", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No 6-digit code found in clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste OTP",
                                tint = PlenxoColors.Secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Paste Code",
                                color = PlenxoColors.Secondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // Verify Action Button
                        Button(
                            onClick = { viewModel.onVerifyOtpClicked() },
                            enabled = enteredOtp.length == 6 && !isOtpFrozen && !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PlenxoColors.Primary,
                                disabledContainerColor = PlenxoColors.Primary.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("verify_otp_button")
                        ) {
                            Text(
                                text = if (isOtpFrozen) "Too Many Attempts (Wait)" else "Verify & Continue",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Resend Code Button
                        TextButton(
                            onClick = { viewModel.resendOtp() },
                            enabled = secondsRemaining == 0,
                            modifier = Modifier.testTag("resend_otp_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Resend Code",
                                    tint = if (secondsRemaining == 0) PlenxoColors.Secondary else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (secondsRemaining == 0) "Resend Code" else "Resend code in ${secondsRemaining}s",
                                    color = if (secondsRemaining == 0) PlenxoColors.Secondary else Color.Gray,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedOtpInput(
    otpValue: String,
    onOtpChanged: (String) -> Unit
) {
    val focusRequesters = remember { List(6) { FocusRequester() } }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag("otp_input")
    ) {
        repeat(6) { index ->
            val charAtIndex = if (index < otpValue.length) otpValue[index].toString() else ""
            val isFocused = index == otpValue.length.coerceAtMost(5)

            OutlinedTextField(
                value = charAtIndex,
                onValueChange = { input ->
                    if (input.length <= 1) {
                        val newOtp = StringBuilder(otpValue)
                        if (index < newOtp.length) {
                            if (input.isNotEmpty()) {
                                newOtp[index] = input[0]
                            } else {
                                newOtp.deleteCharAt(index)
                            }
                        } else if (input.isNotEmpty()) {
                            newOtp.append(input[0])
                        }
                        val finalOtp = newOtp.toString()
                        onOtpChanged(finalOtp)

                        // Focus shifting logic
                        if (input.isNotEmpty() && index < 5) {
                            focusRequesters[index + 1].requestFocus()
                        }
                    } else if (input.length > 1) {
                        // User pasted multi-digit text into cell
                        val cleanInput = input.filter { it.isDigit() }.take(6)
                        if (cleanInput.isNotEmpty()) {
                            onOtpChanged(cleanInput)
                            val targetFocus = (cleanInput.length).coerceAtMost(5)
                            focusRequesters[targetFocus].requestFocus()
                        }
                    }
                },
                modifier = Modifier
                    .width(46.dp)
                    .height(58.dp)
                    .focusRequester(focusRequesters[index])
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.Backspace && charAtIndex.isEmpty() && index > 0) {
                            focusRequesters[index - 1].requestFocus()
                            true
                        } else {
                            false
                        }
                    },
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = if (index == 5) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        if (index < 5) focusRequesters[index + 1].requestFocus()
                    }
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = PlenxoColors.Secondary,
                    unfocusedBorderColor = if (charAtIndex.isNotEmpty()) PlenxoColors.Primary else Color(0x33FFFFFF),
                    focusedContainerColor = Color(0x338A2BE2),
                    unfocusedContainerColor = Color(0x1AFFFFFF)
                )
            )
        }
    }
}

/**
 * Universal VerificationScreen Composable bound with AuthViewModel
 */
@Deprecated(
    message = "Legacy VerificationScreen overload using AuthViewModel. The active primary screen is OtpVerificationScreen via PlenxoViewModel.",
    replaceWith = ReplaceWith("OtpVerificationScreen(viewModel, primaryColor)", "com.example.ui.OtpVerificationScreen")
)
@Composable
fun VerificationScreen(
    authViewModel: AuthViewModel,
    recipientEmail: String,
    onVerificationSuccess: () -> Unit,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var enteredOtp by remember { mutableStateOf("") }
    var secondsRemaining by remember { mutableIntStateOf(60) }
    val isLocked by authViewModel.isAccountLocked.collectAsState()
    val securityError by authViewModel.securityErrorMessage.collectAsState()

    // Timer countdown effect
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            if (secondsRemaining > 0) {
                secondsRemaining--
            }
        }
    }

    LaunchedEffect(securityError) {
        val err = securityError
        if (!err.isNullOrBlank()) {
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
        }
    }

    // Auto-paste detection from clipboard
    LaunchedEffect(Unit) {
        val clipData = clipboardManager.getText()
        val clipText = clipData?.text?.trim() ?: ""
        if (clipText.length == 6 && clipText.all { it.isDigit() }) {
            enteredOtp = clipText
            Toast.makeText(context, "OTP code detected & pasted from clipboard!", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E1A))
    ) {
        // Radial Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PlenxoColors.Primary.copy(alpha = 0.25f),
                            Color(0xFF0A0E1A)
                        ),
                        radius = 900f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back arrow",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Verification",
                    style = PlenxoTypography.Title.copy(color = Color.White)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0x1AFFFFFF),
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            PlenxoColors.Primary.copy(alpha = 0.5f),
                            PlenxoColors.Secondary.copy(alpha = 0.3f)
                        )
                    )
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val progressAnim by animateFloatAsState(
                            targetValue = (secondsRemaining / 150f).coerceIn(0f, 1f),
                            animationSpec = tween(500),
                            label = "authOtpTimer"
                        )

                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokePx = 6.dp.toPx()
                            drawCircle(
                                color = Color(0x22FFFFFF),
                                style = Stroke(width = strokePx)
                            )
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(PlenxoColors.Primary, PlenxoColors.Secondary)
                                ),
                                startAngle = -90f,
                                sweepAngle = 360f * progressAnim,
                                useCenter = false,
                                style = Stroke(width = strokePx, cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Security Lock",
                                tint = if (secondsRemaining > 10) PlenxoColors.Secondary else Color.Red,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "${secondsRemaining}s",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Enter 6-Digit Code",
                        style = PlenxoTypography.Title.copy(
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "We sent a 6-digit security code to:\n$recipientEmail",
                        style = PlenxoTypography.Body.copy(
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    SegmentedOtpInput(
                        otpValue = enteredOtp,
                        onOtpChanged = { newOtp ->
                            if (newOtp.length <= 6) {
                                enteredOtp = newOtp
                                if (newOtp.length == 6) {
                                    keyboardController?.hide()
                                    authViewModel.verifyOtp(newOtp) { verified ->
                                        if (verified) {
                                            onVerificationSuccess()
                                        }
                                    }
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                val clipData = clipboardManager.getText()
                                val clipText = clipData?.text?.trim() ?: ""
                                if (clipText.length == 6 && clipText.all { it.isDigit() }) {
                                    enteredOtp = clipText
                                    Toast.makeText(context, "Pasted $clipText", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No 6-digit code found in clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste OTP",
                            tint = PlenxoColors.Secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Paste Code",
                            color = PlenxoColors.Secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = {
                            authViewModel.verifyOtp(enteredOtp) { verified ->
                                if (verified) {
                                    onVerificationSuccess()
                                }
                            }
                        },
                        enabled = enteredOtp.length == 6 && !isLocked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PlenxoColors.Primary,
                            disabledContainerColor = PlenxoColors.Primary.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("verify_otp_button")
                    ) {
                        Text(
                            text = "Verify & Continue",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = {
                            secondsRemaining = 60
                            authViewModel.requestNetlifyEmailOtp(recipientEmail, purpose = "general") { code ->
                                if (!code.isNullOrBlank()) {
                                    Toast.makeText(context, "New security code sent to $recipientEmail", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Failed to send code. Please try again.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = secondsRemaining == 0,
                        modifier = Modifier.testTag("resend_otp_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Resend Code",
                                tint = if (secondsRemaining == 0) PlenxoColors.Secondary else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (secondsRemaining == 0) "Resend Code" else "Resend code in ${secondsRemaining}s",
                                color = if (secondsRemaining == 0) PlenxoColors.Secondary else Color.Gray,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
