@file:Suppress("DEPRECATION")
package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R

import com.example.ui.theme.PlenxoColors
import com.example.ui.theme.PlenxoSpacing
import com.example.ui.theme.PlenxoTypography
import com.example.ui.components.PlenxoAdvancedLoader
import android.net.Uri
import android.app.Application
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.util.PermissionManager
import com.example.util.NetworkConnectivityObserver
import com.example.util.NetworkStatus
import com.example.viewmodel.PlenxoScreen
import com.example.viewmodel.PlenxoViewModel
import com.example.viewmodel.ChatRequestViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viewmodel.NormalSettingsViewModel
import com.example.viewmodel.ProfileSettingsViewModel
import com.example.ui.NormalSettingsScreen
import com.example.ui.ProfileSettingsScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.profile.SetupProfileScreen
import com.example.ui.profile.PlenxoIdRevealScreen
import com.example.viewmodel.ProfileSetupViewModel
import com.example.ui.settings.LanguageSelectionScreen
import com.example.viewmodel.SettingsViewModel
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith

// Define clean Plenxo branding colors
val PlenxoGreen = Color(0xFF07C160)
val PlenxoDarkGreen = Color(0xFF06A752)
val PlenxoLightGreen = Color(0xFFE8F8F0)
val PlenxoBackground = Color(0xFFF7F7F7)

// Dynamic theme color mapper
@Composable
fun getThemeColors(themeName: String): Pair<Color, Color> {
    return when (themeName) {
        "Red" -> Color(0xFFE53935) to Color(0xFFB71C1C)
        "Blue" -> Color(0xFF1E88E5) to Color(0xFF0D47A1)
        "Purple" -> Color(0xFF8E24AA) to Color(0xFF4A148C)
        "Black" -> Color(0xFF212121) to Color(0xFF000000)
        "Golden" -> Color(0xFFFFB300) to Color(0xFFFF6F00)
        else -> PlenxoGreen to PlenxoDarkGreen
    }
}

@Composable
fun PresenceLifecycleTracker(viewModel: PlenxoViewModel) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val currentUserId = viewModel.currentUserId
    
    DisposableEffect(lifecycleOwner, viewModel, currentUserId) {
        if (currentUserId.isEmpty()) return@DisposableEffect onDispose {}
        
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                viewModel.setupPresenceSystem()
                viewModel.setPresenceState("online")
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.setPresenceState("offline")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setPresenceState("offline")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlenxoAppContent(viewModel: PlenxoViewModel, permissionManager: PermissionManager) {
    PresenceLifecycleTracker(viewModel = viewModel)
    val currentScreen by viewModel.currentScreen.collectAsState()
    val application = LocalContext.current.applicationContext as Application

    val normalSettingsViewModel: NormalSettingsViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )

    val profileSettingsViewModel: ProfileSettingsViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )
    
    val isLoading by viewModel.isLoading.collectAsState()

    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedThemeName by viewModel.selectedTheme.collectAsState()

    val (primaryColor, darkPrimaryColor) = getThemeColors(selectedThemeName)

    val deepLinkResolutionState by viewModel.deepLinkResolutionState.collectAsState()
    val context = LocalContext.current

    // Spectacular Deep Link Resolution Dialog
    DeepLinkResolutionDialog(
        state = deepLinkResolutionState,
        onDismiss = { viewModel.clearDeepLinkResult() },
        onAddFriend = { viewModel.sendDeepLinkFriendRequest() },
        primaryColor = primaryColor
    )

    // Global Back Handler
    androidx.activity.compose.BackHandler(enabled = currentScreen != PlenxoScreen.HOME && currentScreen != PlenxoScreen.LOGIN && currentScreen != PlenxoScreen.PROFILE_SETUP) {
        if (!viewModel.navigateBack()) {
            if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) {
                viewModel.navigateToScreen(PlenxoScreen.HOME, addToHistory = false, clearHistory = true)
            } else {
                viewModel.navigateToScreen(PlenxoScreen.LOGIN, addToHistory = false, clearHistory = true)
            }
        }
    }

    val networkObserver = remember { NetworkConnectivityObserver(context) }
    val networkStatus by networkObserver.status.collectAsState()
    var previousStatus by remember { mutableStateOf<NetworkStatus?>(null) }

    LaunchedEffect(networkStatus) {
        if (previousStatus == NetworkStatus.Lost && networkStatus == NetworkStatus.Available) {
            Toast.makeText(context, "Back Online", Toast.LENGTH_SHORT).show()
        }
        previousStatus = networkStatus
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (initialState == PlenxoScreen.LOGIN && targetState == PlenxoScreen.SIGNUP) {
                    (slideInVertically(initialOffsetY = { it }, animationSpec = androidx.compose.animation.core.tween(450, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + fadeIn(animationSpec = androidx.compose.animation.core.tween(400))) togetherWith
                    (slideOutVertically(targetOffsetY = { -it / 3 }, animationSpec = androidx.compose.animation.core.tween(450, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + fadeOut(animationSpec = androidx.compose.animation.core.tween(400)))
                } else if (initialState == PlenxoScreen.SIGNUP && targetState == PlenxoScreen.LOGIN) {
                    (slideInVertically(initialOffsetY = { -it / 3 }, animationSpec = androidx.compose.animation.core.tween(450, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + fadeIn(animationSpec = androidx.compose.animation.core.tween(400))) togetherWith
                    (slideOutVertically(targetOffsetY = { it }, animationSpec = androidx.compose.animation.core.tween(450, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + fadeOut(animationSpec = androidx.compose.animation.core.tween(400)))
                } else if (targetState == PlenxoScreen.CHAT_DETAIL) {
                    (slideInHorizontally(initialOffsetX = { it }, animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + fadeIn(animationSpec = androidx.compose.animation.core.tween(220))) togetherWith
                    (slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + fadeOut(animationSpec = androidx.compose.animation.core.tween(220)))
                } else if (initialState == PlenxoScreen.CHAT_DETAIL) {
                    (slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + fadeIn(animationSpec = androidx.compose.animation.core.tween(220))) togetherWith
                    (slideOutHorizontally(targetOffsetX = { it }, animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + fadeOut(animationSpec = androidx.compose.animation.core.tween(220)))
                } else {
                    androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(250)) + 
                    androidx.compose.animation.scaleIn(initialScale = 0.95f, animationSpec = androidx.compose.animation.core.tween(250)) togetherWith
                    androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(250))
                }
            },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
            PlenxoScreen.LOGIN -> {
                LoginScreen(
                    viewModel = viewModel, 
                    primaryColor = primaryColor
                )
            }
            PlenxoScreen.SIGNUP -> {
                SignupScreen(
                    viewModel = viewModel, 
                    primaryColor = primaryColor
                )
            }
            PlenxoScreen.OTP_VERIFICATION -> {
                OtpVerificationScreen(viewModel = viewModel, primaryColor = primaryColor)
            }
            PlenxoScreen.EMAIL_VERIFICATION_WAIT -> {
                EmailVerificationWaitingScreen(
                    email = viewModel.email.value,
                    onNavigateBack = { viewModel.navigateToScreen(PlenxoScreen.LOGIN) }
                )
            }
            PlenxoScreen.WELCOME -> {
                WelcomeScreen(viewModel = viewModel, primaryColor = primaryColor)
            }
            PlenxoScreen.PROFILE_SETUP, PlenxoScreen.AVATAR_SETUP, PlenxoScreen.FINAL_DETAILS -> {
                ProfileSetupScreen(
                    viewModel = viewModel,
                    onSetupComplete = {
                        viewModel.setAuthState(com.example.model.AuthState.AUTHENTICATED)
                        val idToReveal = viewModel.plenxoId.value.ifBlank { viewModel.revealedPlenxoId.value }
                        if (idToReveal.isNotBlank()) {
                            viewModel.setRevealedPlenxoId(idToReveal)
                        }
                        viewModel.navigateToScreen(PlenxoScreen.PLENXO_ID_REVEAL, addToHistory = false, clearHistory = true)
                    }
                )
            }
            PlenxoScreen.PLENXO_ID_REVEAL -> {
                val plenxoId by viewModel.revealedPlenxoId.collectAsState()
                PlenxoIdRevealScreen(
                    plenxoId = plenxoId,
                    onEnterPlenxo = {
                        viewModel.navigateToScreen(PlenxoScreen.HOME, addToHistory = false, clearHistory = true)
                    }
                )
            }
            PlenxoScreen.PERMISSION_GATEWAY -> {
                LaunchedEffect(Unit) {
                    viewModel.navigateToScreen(PlenxoScreen.HOME, addToHistory = false, clearHistory = true)
                }
            }
            PlenxoScreen.HOME -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF131824))
                ) {
                    ChatsListScreen(viewModel = viewModel, primaryColor = primaryColor)
                }
            }
            PlenxoScreen.CHAT_DETAIL -> {
                ChatDetailScreen(viewModel = viewModel, primaryColor = primaryColor, permissionManager = permissionManager)
            }
            PlenxoScreen.USER_PROFILE -> {
                UserProfileScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.navigateBack() }
                )
            }
            PlenxoScreen.SETTINGS -> {
                SettingsScreen(viewModel = viewModel, primaryColor = primaryColor)
            }
            PlenxoScreen.SETTINGS_PRIVACY -> {
                SettingsPrivacyScreen(viewModel = viewModel, primaryColor = primaryColor)
            }
            PlenxoScreen.SETTINGS_BLOCKED -> {
                SettingsBlockedScreen(viewModel = viewModel, primaryColor = primaryColor)
            }
            PlenxoScreen.PROFILE_MANAGEMENT -> {
                ProfileManagementScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.navigateBack() }
                )
            }
            PlenxoScreen.WALLPAPER_GALLERY -> {
                WallpaperGalleryScreen(viewModel = viewModel)
            }
            PlenxoScreen.WALLPAPER_PREVIEW -> {
                WallpaperPreviewScreen(viewModel = viewModel)
            }
            PlenxoScreen.SETTINGS_NORMAL -> {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    NormalSettingsScreen(
                        viewModel = normalSettingsViewModel,
                        weChatViewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }
            }
            PlenxoScreen.SETTINGS_PROFILE -> {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                AnimatedVisibility(
                    visible = visible,
                    enter = scaleIn(initialScale = 0.82f) + fadeIn(),
                    exit = scaleOut(targetScale = 0.82f) + fadeOut()
                ) {
                    ProfileSettingsScreen(
                        viewModel = profileSettingsViewModel,
                        weChatViewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }
            }
            PlenxoScreen.CHAT_REQUESTS -> {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    com.example.ui.settings.ChatRequestsScreen(
                        onBack = { viewModel.navigateBack() }
                    )
                }
            }
            PlenxoScreen.PROFILE_RINGS -> {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    ProfileRingsScreen(
                        viewModel = profileSettingsViewModel,
                        weChatViewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }
            }
            PlenxoScreen.DISCOVERY -> {
                com.example.ui.search.UserSearchScreen(
                    onBack = { viewModel.navigateBack() },
                    plenxoViewModel = viewModel
                )
            }
            PlenxoScreen.ACTIVE_SESSIONS -> {
                ActiveSessionsScreen(viewModel = viewModel)
            }
            PlenxoScreen.APP_LOCK_SETUP -> {
                // This is handled by an activity but we need a branch for exhaustiveness
                // or we can just navigate to login if it ever reaches here
            }
            PlenxoScreen.LANGUAGE_SELECTION -> {
                val settingsViewModel: SettingsViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel(
                        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
                    )
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    LanguageSelectionScreen(
                        viewModel = settingsViewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }
            }
            PlenxoScreen.FORGOT_PASSWORD -> {
                ForgotPasswordScreen(viewModel = viewModel, primaryColor = primaryColor)
            }
            PlenxoScreen.CALL_HISTORY -> {
                CallHistoryScreen(viewModel = viewModel, onBack = { viewModel.navigateBack() })
            }
        }
        }

        // Automatic safety endpoint for global loading overlay (never freeze screen indefinitely)
        LaunchedEffect(isLoading) {
            if (isLoading) {
                kotlinx.coroutines.delay(6000L)
                if (viewModel.isLoading.value) {
                    viewModel.clearLoading()
                }
            }
        }

        // Full Screen Advanced Loading Indicator Overlay
        if (isLoading && currentScreen != PlenxoScreen.OTP_VERIFICATION) {
            Dialog(
                onDismissRequest = { viewModel.clearLoading() },
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF131824),
                    border = BorderStroke(
                        1.dp,
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(PlenxoColors.Primary, PlenxoColors.Secondary)
                        )
                    ),
                    modifier = Modifier.testTag("loading_dialog")
                ) {
                    PlenxoAdvancedLoader(
                        modifier = Modifier.padding(24.dp),
                        statusText = "Loading..."
                    )
                }
            }
        }

        // Error Dialog
            errorMessage?.let { error ->
            val safeError = error.ifEmpty { "An unknown error occurred. Please try again." }
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error Logo",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(stringResource(id = R.string.str_notification),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                text = {
                    Text(
                        text = safeError,
                        fontSize = 15.sp,
                        color = Color.DarkGray
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.clearError() },
                        modifier = Modifier.testTag("dismiss_error_button")
                    ) {
                        Text(stringResource(id = R.string.str_dismiss),
                            color = primaryColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = Color.White,
                modifier = Modifier.testTag("error_dialog")
            )
        }



        val callViewModel: com.example.webrtc.CallViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        val rtcCallStatus by callViewModel.callStatus.collectAsState()
        
        val activeCall by viewModel.activeSimulatedCall.collectAsState()
        val callState by viewModel.simulatedCallState.collectAsState()
        
        LaunchedEffect(activeCall) {
            val call = activeCall ?: return@LaunchedEffect
            if (call.direction == "OUTGOING" && callState == "Calling...") {
                viewModel.activeSimulatedCall.value = null
                
                callViewModel.startOutgoingCall(
                    callerId = viewModel.currentUserId,
                    callerName = viewModel.currentUserProfile.value?.displayName ?: "User",
                    callerAvatar = viewModel.currentUserProfile.value?.profilePicUrl ?: "",
                    receiverId = call.peerUid,
                    receiverName = call.peerName,
                    receiverAvatar = call.peerPhotoUrl,
                    isVideo = call.callType == "VIDEO"
                )
            } else if (call.direction == "INCOMING" && callState == "Ringing...") {
                viewModel.activeSimulatedCall.value = null
                
                callViewModel.handleIncomingCallRinging(
                    existingCallId = call.callId,
                    isVideo = call.callType == "VIDEO"
                )
                callViewModel.peerId = call.peerUid
                callViewModel.peerName = call.peerName
                callViewModel.peerAvatar = call.peerPhotoUrl
                callViewModel.currentUserId = viewModel.currentUserId
            }
        }
        
        if (rtcCallStatus != "idle") {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = {},
                properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
            ) {
                if (callViewModel.isCaller && (rtcCallStatus == "calling" || rtcCallStatus == "ringing" || rtcCallStatus == "rejected" || rtcCallStatus == "timeout" || rtcCallStatus == "busy")) {
                    com.example.ui.call.OutgoingCallScreen(
                        viewModel = callViewModel,
                        onCallEnded = { callViewModel.callId = ""; callViewModel.resetCall() }
                    )
                } else if (!callViewModel.isCaller && (rtcCallStatus == "ringing" || rtcCallStatus == "rejected")) {
                    com.example.ui.call.IncomingCallScreen(
                        viewModel = callViewModel,
                        onAccept = { },
                        onReject = { callViewModel.callId = ""; callViewModel.resetCall() }
                    )
                } else if (rtcCallStatus == "accepted" || rtcCallStatus == "ended") {
                    com.example.ui.call.ActiveCallScreen(
                        viewModel = callViewModel,
                        onCallEnded = { callViewModel.callId = ""; callViewModel.resetCall() }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = networkStatus == NetworkStatus.Lost || networkStatus == NetworkStatus.Weak,
            enter = fadeIn() + androidx.compose.animation.slideInVertically(),
            exit = fadeOut() + androidx.compose.animation.slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val bannerText = if (networkStatus == NetworkStatus.Lost) {
                "No Internet Connection - Working Offline"
            } else {
                "Weak Connection Detected"
            }
            val bannerColor = if (networkStatus == NetworkStatus.Lost) {
                Color(0xFFFF4D4F)
            } else {
                Color(0xFFFFC107)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bannerColor)
                    .padding(vertical = 4.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = bannerText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun TermsAndPrivacyCheckboxRow(
    viewModel: PlenxoViewModel,
    errorMessage: String?
) {
    val isTermsAccepted by viewModel.isTermsAccepted.collectAsState()
    val context = LocalContext.current
    val isError = errorMessage?.contains("Terms", ignoreCase = true) == true

    val annotatedString = buildAnnotatedString {
        append("I agree to Plenxo's ")
        pushStringAnnotation(tag = "URL", annotation = "https://coderhamid01-afk.github.io/Term/terms.html")
        withStyle(SpanStyle(color = PlenxoColors.Primary, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
            append("Terms & Conditions")
        }
        pop()
        append(" and ")
        pushStringAnnotation(tag = "URL", annotation = "https://coderhamid01-afk.github.io/Term/privacy.html")
        withStyle(SpanStyle(color = PlenxoColors.Primary, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
            append("Privacy Policy")
        }
        pop()
        append(".")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isError) {
                    Modifier
                        .background(Color(0x22FF4D4F), shape = RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFFF4D4F), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp)
                } else {
                    Modifier.padding(vertical = 4.dp)
                }
            )
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                viewModel.isTermsAccepted.value = !isTermsAccepted
                if (errorMessage != null) viewModel.clearError()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isTermsAccepted,
            onCheckedChange = { checked ->
                viewModel.isTermsAccepted.value = checked
                if (errorMessage != null) viewModel.clearError()
            },
            colors = CheckboxDefaults.colors(
                checkedColor = PlenxoColors.Primary,
                uncheckedColor = if (isError) Color(0xFFFF4D4F) else Color.LightGray,
                checkmarkColor = Color.White
            ),
            modifier = Modifier.testTag("terms_checkbox")
        )
        Spacer(modifier = Modifier.width(4.dp))
        ClickableText(
            text = annotatedString,
            style = PlenxoTypography.Body.copy(
                color = Color.White,
                fontSize = 13.sp
            ),
            onClick = { offset ->
                annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        com.example.util.LegalWebUtils.openUrl(context, annotation.item)
                    } ?: run {
                        viewModel.isTermsAccepted.value = !isTermsAccepted
                        if (errorMessage != null) viewModel.clearError()
                    }
            },
            modifier = Modifier
                .weight(1f)
                .testTag("terms_checkbox_text")
        )
    }
}

// TASK 1: PREMIUM WELCOME PAGE
@Composable
fun WelcomeScreen(viewModel: PlenxoViewModel, primaryColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Welcome Celebration Graphic Logo
        Box(
            modifier = Modifier
                .size(110.dp)
                .background(primaryColor.copy(alpha = 0.12f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Celebration,
                contentDescription = "Welcome Celebration Graphic",
                tint = primaryColor,
                modifier = Modifier.size(58.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(stringResource(id = R.string.str_welcome_to_plenxo),
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            color = Color.Black,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.5).sp
        )

        Text(stringResource(id = R.string.str_connect_share_and_secure_your),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = primaryColor,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            textAlign = TextAlign.Center
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Text(stringResource(id = R.string.str_experience_seamless_communication_with_top),
                fontSize = 15.sp,
                color = Color.DarkGray,
                lineHeight = 24.sp,
                modifier = Modifier.padding(24.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { viewModel.navigateToAvatarSetup() },
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .testTag("setup_profile_button"),
            shape = RoundedCornerShape(29.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Text(stringResource(id = R.string.str_set_up_my_profile),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// FINAL COMPLETED LANDING SCREEN
@Composable
fun HomeScreen(viewModel: PlenxoViewModel, primaryColor: Color) {
    val email by viewModel.email.collectAsState()
    val displayName by viewModel.displayName.collectAsState()
    val bDay by viewModel.birthDay.collectAsState()
    val bMonth by viewModel.birthMonth.collectAsState()
    val bYear by viewModel.birthYear.collectAsState()
    val userCode by viewModel.userCode.collectAsState()
    val avatarType by viewModel.avatarType.collectAsState()
    val selectedIndex by viewModel.selectedAvatarIndex.collectAsState()
    val galleryImageUriStr by viewModel.galleryImageUriString.collectAsState()
    val selectedEmoji by viewModel.selectedEmoji.collectAsState()
    val selectedThemeName by viewModel.selectedTheme.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Success Logo Banner
        Box(
            modifier = Modifier
                .size(90.dp)
                .background(primaryColor.copy(alpha = 0.12f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success tick logo",
                tint = primaryColor,
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(stringResource(id = R.string.str_welcome_to_plenxo_1),
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Color.Black
        )

        Text(stringResource(id = R.string.str_your_account_registration_has_been),
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Consolidated Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Avatar visual representation strictly inside circle frame
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.1f))
                        .border(2.dp, primaryColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    when (avatarType) {
                        "placeholder" -> {
                            val avatarList = viewModel.maleAvatars + viewModel.femaleAvatars
                            if (selectedIndex in avatarList.indices) {
                                Text(
                                    text = avatarList[selectedIndex].second,
                                    fontSize = 44.sp
                                )
                            }
                        }
                        "gallery" -> {
                            if (galleryImageUriStr != null) {
                                AsyncImage(
                                    model = galleryImageUriStr,
                                    contentDescription = "User profile image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Account profile icon placeholder",
                                    tint = primaryColor,
                                    modifier = Modifier.size(50.dp)
                                )
                            }
                        }
                        "emoji" -> {
                            Text(
                                text = selectedEmoji,
                                fontSize = 50.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // User display metadata
                Text(
                    text = displayName,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = Color.Black
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = "User Code tag",
                        tint = primaryColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Plenxo Code: $userCode",
                        fontSize = 13.sp,
                        color = Color(0xFF1E1E1E),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // Profile Details Key-Value structure
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfileRow(icon = Icons.Default.Email, label = "Email", value = email, accentColor = primaryColor)
                    ProfileRow(icon = Icons.Default.Cake, label = "Birthday (DOB)", value = "$bDay $bMonth, $bYear", accentColor = primaryColor)
                    ProfileRow(icon = Icons.Default.Palette, label = "App Theme Accent", value = "$selectedThemeName Choice", accentColor = primaryColor)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Back to signup exit button
        OutlinedButton(
            onClick = { viewModel.navigateBackToSignup() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("logout_button"),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryColor),
            border = BorderStroke(1.5.dp, primaryColor)
        ) {
            Text(stringResource(id = R.string.str_back_to_signup),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )
        }
    }
}

@Composable
fun ProfileRow(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(accentColor.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                fontSize = 14.sp,
                color = Color.Black,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * A spectacularly styled dialog for resolving deep-linked friend requests.
 */
@Composable
fun DeepLinkResolutionDialog(
    state: com.example.viewmodel.DeepLinkResolutionState,
    onDismiss: () -> Unit,
    onAddFriend: () -> Unit,
    primaryColor: Color
) {
    if (state == com.example.viewmodel.DeepLinkResolutionState.Idle) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(enabled = state !is com.example.viewmodel.DeepLinkResolutionState.Resolving) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (state) {
                        is com.example.viewmodel.DeepLinkResolutionState.Resolving -> {
                            CircularProgressIndicator(color = primaryColor)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(id = R.string.str_searching_for_friend),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Gray
                            )
                        }
                        is com.example.viewmodel.DeepLinkResolutionState.ValidProfileFound -> {
                            val profile = state.profile
                            
                            Box(
                                modifier = Modifier.size(110.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                com.example.ui.components.ProfileRingBox(ringId = profile.profileRingId, ringPadding = 4.dp, borderWidth = 5.dp) {
                                    Box(
                                        modifier = Modifier
                                            .size(90.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF0F0F0))
                                    ) {
                                        if (profile.profilePicUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = profile.profilePicUrl,
                                                contentDescription = "Profile Picture",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(0.6f).align(Alignment.Center),
                                                tint = Color.LightGray
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = profile.displayName.ifEmpty { "User ${profile.userCode}" },
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            
                            if (profile.userCode.isNotEmpty()) {
                                Text(
                                    text = profile.userCode,
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = profile.statusMessage.ifEmpty { "Hey there! I am using Plenxo." },
                                fontSize = 14.sp,
                                color = Color.DarkGray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Button(
                                onClick = onAddFriend,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.str_send_friend_request), fontWeight = FontWeight.Bold)
                            }
                        }
                        is com.example.viewmodel.DeepLinkResolutionState.InvalidOrExpired -> {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.Red
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(id = R.string.str_oops),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.easyMessage,
                                fontSize = 14.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                            ) {
                                Text(stringResource(R.string.str_okay), fontWeight = FontWeight.Bold)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}
