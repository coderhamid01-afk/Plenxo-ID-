package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.components.bounceClick

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.UserProfileDomainModel
import com.example.viewmodel.ProfileSettingsViewModel
import com.example.viewmodel.PlenxoViewModel
import com.example.viewmodel.ProfileUiState
import com.example.viewmodel.UpdateUiState
import com.example.viewmodel.PlenxoScreen
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.example.ui.components.ProfileRing
import com.example.ui.AnimatedAuthBackground
import com.example.ui.components.AccountDeletionReAuthDialog
import com.example.ui.components.AccountDeletionConfirmationDialog
import com.example.ui.components.AccountDeletionLoadingOverlay
import com.example.repository.AccountDeletionUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    viewModel: ProfileSettingsViewModel,
    weChatViewModel: PlenxoViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val profileUiState by viewModel.profileUiState.collectAsState()
    val updateUiState by viewModel.updateState.collectAsState()
    val pendingRequestsCount by weChatViewModel.pendingFriendRequests.collectAsState()
    val connectedFriends by weChatViewModel.connectedFriends.collectAsState()
    val outgoingRequests by weChatViewModel.outgoingPendingRequests.collectAsState()

    LaunchedEffect(Unit) {
        weChatViewModel.observeConnectedFriendsAndRequests()
    }

    val accountDeletionState by viewModel.accountDeletionState.collectAsState()
    val deletionPasswordError by viewModel.deletionPasswordError.collectAsState()
    val isReauthenticating by viewModel.isReauthenticating.collectAsState()

    // Professional Color Palette
    val darkBg = Color.Transparent       // Transparent to let AnimatedAuthBackground shine through
    val textOnBtn = Color(0xFF0D1117)
    val cardBg = Color(0x99161B22)       // Semi-transparent Elevated Surfaces for glassmorphism
    val strokeBorder = Color(0xFF2D333B) // High Contrast Borders
    val accentBlue = Color(0xFF58A6FF)   // Tech Blue Highlights
    val textWhite = Color(0xFFF0F6FC)    // Core Text
    val textMuted = Color(0xFF8B949E)    // Sub-titles
    val dangerRed = Color(0xFFEF4444)

    // Handle account deletion state changes
    LaunchedEffect(accountDeletionState) {
        when (accountDeletionState) {
            is AccountDeletionUiState.Success -> {
                Toast.makeText(context, "Account deleted successfully.", Toast.LENGTH_LONG).show()
                viewModel.dismissAccountDeletion()
                weChatViewModel.navigateToScreen(PlenxoScreen.LOGIN, addToHistory = false, clearHistory = true)
            }
            is AccountDeletionUiState.Error -> {
                val errorMsg = (accountDeletionState as AccountDeletionUiState.Error).message
                Toast.makeText(context, "Deletion Error: $errorMsg", Toast.LENGTH_LONG).show()
                viewModel.dismissAccountDeletion()
            }
            else -> {}
        }
    }

    // Render Account Deletion Dialogs
    AccountDeletionReAuthDialog(
        show = (accountDeletionState is AccountDeletionUiState.ShowPasswordDialog),
        errorMessage = deletionPasswordError,
        isLoading = isReauthenticating,
        onDismiss = { viewModel.dismissAccountDeletion() },
        onContinue = { password -> viewModel.verifyPasswordAndProceed(password) }
    )

    AccountDeletionConfirmationDialog(
        show = (accountDeletionState is AccountDeletionUiState.ShowWarningDialog),
        onDismiss = { viewModel.dismissAccountDeletion() },
        onConfirmDelete = { viewModel.executeAccountDeletion() }
    )

    AccountDeletionLoadingOverlay(
        show = (accountDeletionState is AccountDeletionUiState.Deleting)
    )

    var showSetMasterPinDialog by remember { mutableStateOf(false) }
    var showDisable2FADialog by remember { mutableStateOf(false) }
    var showQRBottomSheet by remember { mutableStateOf(false) }
    val is2FAEnabled by weChatViewModel.is2FAEnabled.collectAsState()

    if (showSetMasterPinDialog) {
        com.example.ui.components.SetMasterPinDialog(
            viewModel = weChatViewModel,
            onDismiss = { showSetMasterPinDialog = false }
        )
    }

    if (showDisable2FADialog) {
        com.example.ui.components.Disable2FADialog(
            viewModel = weChatViewModel,
            onDismiss = { showDisable2FADialog = false }
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var newlyUploadedAvatarUrl by remember { mutableStateOf<String?>(null) }
    var isAvatarUploading by remember { mutableStateOf(false) }

    if (showQRBottomSheet) {
        val currentProfile = (profileUiState as? ProfileUiState.Success)?.profile
        com.example.ui.components.ProfileQRBottomSheet(
            displayName = currentProfile?.name ?: "",
            plenxoId = currentProfile?.plenxoId?.ifEmpty { currentProfile.userCode } ?: currentProfile?.userCode ?: "",
            avatarUrl = newlyUploadedAvatarUrl ?: currentProfile?.profilePicUrl,
            onDismissRequest = { showQRBottomSheet = false }
        )
    }

    // Photo Picker for Avatar - declared unconditionally at top level
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isAvatarUploading = true
            coroutineScope.launch {
                try {
                    val uploadedUrl = com.example.network.CatboxUploader.uploadImage(context, uri)
                    newlyUploadedAvatarUrl = uploadedUrl
                    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    if (uid.isNotEmpty()) {
                        val fs = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        val map = mapOf(
                            "profilePicUrl" to uploadedUrl,
                            "avatar_url" to uploadedUrl,
                            "photoUrl" to uploadedUrl,
                            "profileUrl" to uploadedUrl
                        )
                        fs.collection("users_data").document(uid)
                            .set(map, com.google.firebase.firestore.SetOptions.merge())
                        fs.collection("users").document(uid)
                            .set(map, com.google.firebase.firestore.SetOptions.merge())
                    }
                    Toast.makeText(context, "Profile picture uploaded successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("ProfileSettingsScreen", "Catbox upload error: ${e.message}", e)
                    Toast.makeText(context, "Failed to upload image to Catbox. Please try again.", Toast.LENGTH_LONG).show()
                } finally {
                    isAvatarUploading = false
                }
            }
        }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            Toast.makeText(context, "Storage/Media permission is required to update avatar.", Toast.LENGTH_LONG).show()
        }
    }

    // Handle updates successfully
    LaunchedEffect(updateUiState) {
        if (updateUiState is UpdateUiState.Error) {
            val errorMsg = (updateUiState as UpdateUiState.Error).message
            snackbarHostState.showSnackbar(
                message = "Couldn't save to the cloud: $errorMsg. Check your connection and try again.",
                duration = SnackbarDuration.Long
            )
            viewModel.resetUpdateState()
        }
    }

    AnimatedAuthBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { 
                        Text(stringResource(R.string.str_cloud_profile_settings), 
                            fontSize = 18.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = textWhite
                        ) 
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("profile_settings_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                                contentDescription = "Back", 
                                tint = accentBlue
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D1117))
                )
            },
            containerColor = darkBg
        ) { paddingValues ->
        when (profileUiState) {
            is ProfileUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentBlue)
                }
            }
            is ProfileUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Authentication failed or error: ${(profileUiState as ProfileUiState.Error).message}",
                        color = Color.Red,
                        textAlign = TextAlign.Center
                    )
                }
            }
            is ProfileUiState.Success -> {
                val profile = (profileUiState as ProfileUiState.Success).profile

                var nameInput by remember { mutableStateOf(profile.name) }
                var bioInput by remember { mutableStateOf(profile.bio) }
                var profileUrlInput by remember { mutableStateOf(profile.profileUrl) }

                LaunchedEffect(updateUiState) {
                    if (updateUiState is UpdateUiState.Success) {
                        val currentProf = weChatViewModel.currentUserProfile.value
                        if (currentProf != null) {
                            weChatViewModel.currentUserProfile.value = currentProf.copy(
                                displayName = nameInput,
                                bio = bioInput,
                                statusMessage = bioInput,
                                profilePicUrl = profileUrlInput.ifBlank { currentProf.profilePicUrl }
                            )
                        }
                        Toast.makeText(context, "Profile Synced to Cloud Successfully!", Toast.LENGTH_SHORT).show()
                        viewModel.resetUpdateState()
                        onBack()
                    }
                }

                LaunchedEffect(profile) {
                    if (nameInput.isBlank() && profile.name.isNotBlank()) nameInput = profile.name
                    if (bioInput.isBlank() && profile.bio.isNotBlank()) bioInput = profile.bio
                    if (profileUrlInput.isBlank() && profile.profileUrl.isNotBlank()) profileUrlInput = profile.profileUrl
                }

                LaunchedEffect(newlyUploadedAvatarUrl) {
                    newlyUploadedAvatarUrl?.let {
                        profileUrlInput = it
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Header Area with clean profile pic
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        val contextLocal = androidx.compose.ui.platform.LocalContext.current
                        val localRingId = com.example.util.SessionManager.getProfileRingId(contextLocal)
                        val userRingId = if (localRingId != "none") localRingId else (profile.profileRingId ?: "none")
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            com.example.ui.components.ProfileRingBox(ringId = userRingId, borderWidth = 6.dp) {
                                Box(
                                    modifier = Modifier
                                        .size(136.dp)
                                        .clip(CircleShape)
                                        .background(cardBg)
                                        .border(2.dp, accentBlue, CircleShape)
                                        .bounceClick {
                                            val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                Manifest.permission.READ_MEDIA_IMAGES
                                            } else {
                                                Manifest.permission.READ_EXTERNAL_STORAGE
                                            }
                                            if (ContextCompat.checkSelfPermission(contextLocal, permission) == PackageManager.PERMISSION_GRANTED) {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            } else {
                                                mediaPermissionLauncher.launch(permission)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (profileUrlInput.isNotEmpty()) {
                                        AsyncImage(
                                            model = profileUrlInput,
                                            contentDescription = "Profile Picture",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "Placeholder",
                                            modifier = Modifier.size(64.dp),
                                            tint = textMuted
                                        )
                                    }

                                    if (isAvatarUploading) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.6f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = accentBlue,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }
                                    
                                    // Edit overlay
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        Text(stringResource(R.string.str_edit),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // --- DISPLAY NAME ---
                            Text(
                                text = profile.name,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = textWhite
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // --- PLENXO ID with Copy Button ---
                            val displayPlenxoId = profile.plenxoId.ifEmpty { profile.userCode }
                            val formattedPlenxoId = if (displayPlenxoId.startsWith("@")) displayPlenxoId else "@$displayPlenxoId"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = formattedPlenxoId,
                                    fontSize = 14.sp,
                                    color = accentBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                            as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText(
                                            "Plenxo ID",
                                            displayPlenxoId
                                        )
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Plenxo ID copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Plenxo ID",
                                        tint = accentBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // --- SHARE / MY QR CODE BUTTON ---
                            Button(
                                onClick = { showQRBottomSheet = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentBlue.copy(alpha = 0.15f),
                                    contentColor = accentBlue
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, accentBlue.copy(alpha = 0.3f)),
                                modifier = Modifier.testTag("share_my_qr_code_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = "Share QR Code",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Share / My QR Code",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // --- PHONE NUMBER (if available) ---
                            if (profile.phoneNumber.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "📞 ${profile.phoneNumber}",
                                    fontSize = 13.sp,
                                    color = textMuted
                                )
                            }
                        }
                    }

                    // Fields: Display Name, Plenxo ID, Biography Status
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, strokeBorder, RoundedCornerShape(16.dp)),
                            color = cardBg,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Title header for Edit Fields
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Badge,
                                        contentDescription = null,
                                        tint = accentBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(stringResource(id = R.string.str_cloud_coordinates),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = textWhite
                                    )
                                }

                                HorizontalDivider(color = strokeBorder)

                                // Display Name
                                Column {
                                    Text(stringResource(id = R.string.str_display_name_1),
                                        fontSize = 11.sp,
                                        color = textMuted,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = nameInput,
                                        onValueChange = { nameInput = it },
                                        placeholder = { Text(stringResource(R.string.str_enter_public_name)) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = accentBlue,
                                            unfocusedBorderColor = strokeBorder,
                                            focusedContainerColor = darkBg,
                                            unfocusedContainerColor = darkBg,
                                            focusedTextColor = textWhite,
                                            unfocusedTextColor = textWhite,
                                            focusedPlaceholderColor = textMuted,
                                            unfocusedPlaceholderColor = textMuted
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("profile_settings_name_input")
                                    )
                                }

                                // Biography Status / Bio
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(stringResource(id = R.string.str_biography_status),
                                            fontSize = 11.sp,
                                            color = textMuted,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = "${150 - bioInput.length} chars left",
                                            fontSize = 11.sp,
                                            color = if (bioInput.length > 150) Color.Red else textMuted,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = bioInput,
                                        onValueChange = {
                                            if (it.length <= 150) {
                                                bioInput = it
                                            }
                                        },
                                        placeholder = { Text(stringResource(R.string.str_write_brief_status_message)) },
                                        minLines = 2,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = accentBlue,
                                            unfocusedBorderColor = strokeBorder,
                                            focusedContainerColor = darkBg,
                                            unfocusedContainerColor = darkBg,
                                            focusedTextColor = textWhite,
                                            unfocusedTextColor = textWhite,
                                            focusedPlaceholderColor = textMuted,
                                            unfocusedPlaceholderColor = textMuted
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("profile_settings_bio_input")
                                    )
                                }
                            }
                        }
                    }

                    // READ-ONLY: Auth Details (Phone/Email)
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, strokeBorder, RoundedCornerShape(16.dp)),
                            color = cardBg,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(stringResource(id = R.string.str_private_auth_signatures),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = textWhite
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = strokeBorder)
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(stringResource(R.string.str_email_addr_contacts), color = textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = profile.email.ifEmpty { "no-email@plenxopro.io" },
                                            color = textWhite,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(strokeBorder, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(stringResource(R.string.str_read_only), color = textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================
                    // WHO THEY'VE ADDED (ADDED CONNECTIONS)
                    // ==========================================
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, strokeBorder, RoundedCornerShape(16.dp)),
                            color = cardBg,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.People,
                                            contentDescription = null,
                                            tint = accentBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Added Connections",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = textWhite
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(accentBlue.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${connectedFriends.size}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = accentBlue
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = strokeBorder)
                                Spacer(modifier = Modifier.height(12.dp))

                                if (connectedFriends.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PersonSearch,
                                            contentDescription = null,
                                            tint = textMuted,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "No connections added yet",
                                            color = textWhite,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Search users by PX ID (e.g. PX-102938) to connect",
                                            color = textMuted,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp)
                                        )
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        connectedFriends.forEach { friend ->
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, strokeBorder.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                                                color = Color(0xFF0D1117).copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    // Avatar
                                                    Box(
                                                        modifier = Modifier
                                                            .size(42.dp)
                                                            .clip(CircleShape)
                                                            .background(strokeBorder),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (friend.profilePicUrl.isNotBlank()) {
                                                            AsyncImage(
                                                                model = friend.profilePicUrl,
                                                                contentDescription = friend.displayName,
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        } else {
                                                            Icon(
                                                                imageVector = Icons.Default.Person,
                                                                contentDescription = null,
                                                                tint = textMuted,
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                        }
                                                    }

                                                    // Info
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = friend.displayName.ifBlank { "User" },
                                                            color = textWhite,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        if (friend.plenxoId.isNotBlank()) {
                                                            Text(
                                                                text = friend.plenxoId,
                                                                color = accentBlue,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        }
                                                        if (friend.bio.isNotBlank()) {
                                                            Text(
                                                                text = friend.bio,
                                                                color = textMuted,
                                                                fontSize = 10.sp,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }

                                                    // Quick Actions
                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        IconButton(
                                                            onClick = {
                                                                weChatViewModel.openUserProfile(friend.uid)
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.AccountCircle,
                                                                contentDescription = "View Profile",
                                                                tint = textMuted,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                weChatViewModel.openChatWithUid(friend.uid)
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Chat,
                                                                contentDescription = "Message",
                                                                tint = accentBlue,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================
                    // WHAT REQUESTS THEY HAD (REQUESTS OVERVIEW)
                    // ==========================================
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, strokeBorder, RoundedCornerShape(16.dp)),
                            color = cardBg,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MarkChatUnread,
                                            contentDescription = null,
                                            tint = accentBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Connection & Chat Requests",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = textWhite
                                        )
                                    }
                                    val totalReqCount = pendingRequestsCount.size + outgoingRequests.size
                                    if (totalReqCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFEF4444).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "$totalReqCount",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFEF4444)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = strokeBorder)
                                Spacer(modifier = Modifier.height(12.dp))

                                // Incoming Requests List
                                if (pendingRequestsCount.isNotEmpty()) {
                                    Text(
                                        text = "INCOMING REQUESTS (${pendingRequestsCount.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accentBlue,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        pendingRequestsCount.forEach { req ->
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, strokeBorder.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                                                color = Color(0xFF0D1117).copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(CircleShape)
                                                            .background(strokeBorder),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (req.senderPhotoUrl.isNotBlank()) {
                                                            AsyncImage(
                                                                model = req.senderPhotoUrl,
                                                                contentDescription = req.senderName,
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        } else {
                                                            Icon(Icons.Default.Person, contentDescription = null, tint = textMuted, modifier = Modifier.size(20.dp))
                                                        }
                                                    }

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = req.senderName.ifBlank { "User" },
                                                            color = textWhite,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        if (req.senderPlenxoId.isNotBlank()) {
                                                            Text(
                                                                text = req.senderPlenxoId,
                                                                color = accentBlue,
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                    }

                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Button(
                                                            onClick = { weChatViewModel.acceptFriendRequest(req) },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.height(30.dp)
                                                        ) {
                                                            Text("Accept", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                        }
                                                        OutlinedButton(
                                                            onClick = { weChatViewModel.rejectFriendRequest(req) },
                                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.height(30.dp)
                                                        ) {
                                                            Text("Decline", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                // Outgoing Requests List
                                if (outgoingRequests.isNotEmpty()) {
                                    Text(
                                        text = "SENT REQUESTS (${outgoingRequests.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textMuted,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        outgoingRequests.forEach { outReq ->
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, strokeBorder.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                                                color = Color(0xFF0D1117).copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = outReq.receiverPlenxoId.ifBlank { "PX Contact" },
                                                            color = textWhite,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        Text("Request Sent", color = textMuted, fontSize = 10.sp)
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .background(Color(0xFFD29922).copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                                    ) {
                                                        Text("Pending...", color = Color(0xFFD29922), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                if (pendingRequestsCount.isEmpty() && outgoingRequests.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "No active requests",
                                            color = textMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                // Shortcut button to Requests Center
                                OutlinedButton(
                                    onClick = { weChatViewModel.navigateToScreen(PlenxoScreen.CHAT_REQUESTS) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentBlue),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, accentBlue.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .testTag("chat_requests_menu_option")
                                ) {
                                    Icon(Icons.Default.Inbox, contentDescription = null, tint = accentBlue, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open Full Requests Center", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accentBlue)
                                }
                            }
                        }
                    }

                    // PROFILE RINGS GATEWAY
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, strokeBorder, RoundedCornerShape(16.dp))
                                .bounceClick { weChatViewModel.navigateToScreen(PlenxoScreen.PROFILE_RINGS) }
                                .testTag("profile_rings_menu_option"),
                            color = cardBg,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Default.Stars, contentDescription = null, tint = accentBlue)
                                    Text(stringResource(R.string.str_profile_rings), color = textWhite, fontWeight = FontWeight.Bold)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = textMuted)
                            }
                        }
                    }

                    // TWO-FACTOR AUTHENTICATION (2FA) & MASTER PIN
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, if (is2FAEnabled) accentBlue.copy(alpha = 0.6f) else strokeBorder, RoundedCornerShape(16.dp))
                                .testTag("2fa_profile_settings_card"),
                            color = cardBg,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                if (is2FAEnabled) accentBlue.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = "2FA Security",
                                            tint = if (is2FAEnabled) accentBlue else textMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Two-Factor Auth (2FA)",
                                                color = textWhite,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (is2FAEnabled) Color(0xFF238636).copy(alpha = 0.25f) else Color(0xFF8B949E).copy(alpha = 0.2f),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (is2FAEnabled) "ENABLED" else "OFF",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (is2FAEnabled) Color(0xFF3FB950) else textMuted
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (is2FAEnabled) "Protected with 6-Digit Secret Master PIN" else "Require 6-digit Master PIN on sign-in",
                                            color = textMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Switch(
                                    checked = is2FAEnabled,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            showSetMasterPinDialog = true
                                        } else {
                                            showDisable2FADialog = true
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = accentBlue,
                                        uncheckedThumbColor = Color.LightGray,
                                        uncheckedTrackColor = Color(0x33FFFFFF)
                                    ),
                                    modifier = Modifier.testTag("2fa_toggle_switch_profile_settings")
                                )
                            }
                        }
                    }

                    // PRIVACY CONTROLS GATEWAY
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, strokeBorder, RoundedCornerShape(16.dp))
                                .bounceClick { weChatViewModel.navigateToScreen(PlenxoScreen.SETTINGS_PRIVACY) },
                            color = cardBg,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = accentBlue)
                                    Text(stringResource(R.string.str_privacy_controls), color = textWhite, fontWeight = FontWeight.Bold)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = textMuted)
                            }
                        }
                    }

                    // BLOCKED CONTACTS GATEWAY
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, strokeBorder, RoundedCornerShape(16.dp))
                                .bounceClick { weChatViewModel.navigateToScreen(PlenxoScreen.SETTINGS_BLOCKED) },
                            color = cardBg,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFEF4444))
                                    Text(stringResource(R.string.str_blocked_contacts), color = textWhite, fontWeight = FontWeight.Bold)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = textMuted)
                            }
                        }
                    }

                    // SAVE / SYNC BUTTON
                    item {
                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (nameInput.isBlank()) {
                                    Toast.makeText(context, "Display name is mandatory!", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.saveProfile(
                                        name = nameInput,
                                        bio = bioInput,
                                        profileUrl = profileUrlInput
                                    )
                                }
                            },
                            enabled = updateUiState !is UpdateUiState.Loading,
                            colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("profile_settings_save_btn")
                        ) {
                            if (updateUiState is UpdateUiState.Loading) {
                                CircularProgressIndicator(color = textOnBtn, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, tint = textOnBtn)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(stringResource(id = R.string.str_sync_save_to_cloud),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = textOnBtn
                                )
                            }
                        }
                    }

                    // DANGER ZONE / DELETE ACCOUNT BUTTON
                    item {
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { viewModel.initiateAccountDeletion() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerRed),
                            border = androidx.compose.foundation.BorderStroke(1.dp, dangerRed.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_delete_account")
                        ) {
                            Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, tint = dangerRed)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Delete Account",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = dangerRed
                            )
                        }

                        Spacer(modifier = Modifier.height(36.dp))
                    }
                }
            }
        }
    }
}
}
