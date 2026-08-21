package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.viewmodel.PlenxoViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagementScreen(
    viewModel: PlenxoViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentProfilePicUrl by viewModel.galleryImageUriString.collectAsState()
    val isUploading by viewModel.isProfilePicUploading.collectAsState()
    val currentDisplayName by viewModel.displayName.collectAsState()
    val currentUserCode by viewModel.userCode.collectAsState()
    val currentStatusMessage by viewModel.aboutText.collectAsState()
    val profileShareState by viewModel.profileShareState.collectAsState()

    var displayName by remember(currentDisplayName) { mutableStateOf(currentDisplayName) }
    var statusMessage by remember(currentStatusMessage) { mutableStateOf(currentStatusMessage) }
    var contactDetails by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uploadProfilePicture(uri)
            Toast.makeText(context, "Uploading profile picture...", Toast.LENGTH_SHORT).show()
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

    // Premium Color Palette
    val darkBg = Color(0xFF131824)       // Dark Blue-Black Background
    val cardBg = Color(0xFF1C2234)       // Deep Slate Blue for Container Cards
    val accentBlue = Color(0xFF58A6FF)   // Sleek Royal Blue Accent
    val textWhite = Color(0xFFFFFFFF)    // High contrast primary text
    val textMuted = Color(0xFF9CA5BE)    // Elegant muted blue-gray for labels/secondary text

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.str_profile_management), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("profile_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = accentBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = darkBg)
            )
        },
        containerColor = darkBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Center circular image view
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .testTag("profile_avatar_container")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(cardBg)
                        .border(2.dp, accentBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!currentProfilePicUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = currentProfilePicUrl,
                            contentDescription = "Profile Photo",
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

                    if (isUploading) {
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
                }

                // Floating Camera icon at bottom-right edge
                SmallFloatingActionButton(
                    onClick = {
                        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            android.Manifest.permission.READ_MEDIA_IMAGES
                        } else {
                            android.Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        } else {
                            mediaPermissionLauncher.launch(permission)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(36.dp)
                        .testTag("change_avatar_button"),
                    containerColor = accentBlue,
                    contentColor = darkBg,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Change Photo",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Display Name
            PlenxoTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = "Display Name",
                isDark = true,
                modifier = Modifier.testTag("profile_display_name_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Permanent User Code (Read Only)
            PlenxoTextField(
                value = currentUserCode,
                onValueChange = { },
                label = "Permanent User Code",
                isDark = true,
                readOnly = true,
                modifier = Modifier.testTag("profile_user_code_display")
            )
            Text(stringResource(id = R.string.str_this_code_is_permanent_and),
                fontSize = 11.sp,
                color = textMuted,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = 12.dp, top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Biography Status / About status message
            PlenxoTextField(
                value = statusMessage,
                onValueChange = { statusMessage = it },
                label = "Biography Status",
                isDark = true,
                modifier = Modifier.testTag("profile_bio_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Personal details
            PlenxoTextField(
                value = contactDetails,
                onValueChange = { contactDetails = it },
                label = "Personal Details (Contact Info)",
                isDark = true,
                modifier = Modifier.testTag("profile_contact_input")
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Save Changes button
            Button(
                onClick = {
                    if (displayName.isBlank()) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Display Name cannot be empty")
                        }
                    } else {
                        viewModel.updateProfile(displayName, statusMessage)
                        
                        scope.launch {
                            snackbarHostState.showSnackbar("Profile saved successfully")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("profile_save_button")
            ) {
                Text(stringResource(id = R.string.str_save_changes),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = darkBg
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Share Profile button
            OutlinedButton(
                onClick = {
                    viewModel.shareProfileLink(context)
                },
                enabled = profileShareState !is com.example.viewmodel.ProfileShareState.Generating,
                border = BorderStroke(1.dp, accentBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("profile_share_button")
            ) {
                if (profileShareState is com.example.viewmodel.ProfileShareState.Generating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = accentBlue,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = accentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = R.string.str_share_my_profile),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentBlue
                    )
                }
            }
        }
    }
}
