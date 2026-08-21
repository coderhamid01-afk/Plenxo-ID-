package com.example.ui.profile

import android.net.Uri
import android.widget.Toast
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.ui.theme.*
import com.example.util.DateUtils
import com.example.util.plenxoLanguages
import com.example.viewmodel.PlenxoViewModel
import com.example.viewmodel.ProfileSetupUiState
import com.example.viewmodel.ProfileSetupViewModel

private val PRESET_AVATARS = listOf(
    "https://api.dicebear.com/7.x/bottts/svg?seed=Plenxo1",
    "https://api.dicebear.com/7.x/bottts/svg?seed=Plenxo2",
    "https://api.dicebear.com/7.x/bottts/svg?seed=Plenxo3",
    "https://api.dicebear.com/7.x/bottts/svg?seed=Plenxo4",
    "https://api.dicebear.com/7.x/bottts/svg?seed=Plenxo5",
    "https://api.dicebear.com/7.x/bottts/svg?seed=Plenxo6"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupProfileScreen(
    onSetupSuccess: (plenxoId: String) -> Unit,
    viewModel: ProfileSetupViewModel = viewModel(),
    mainViewModel: PlenxoViewModel? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val profileUri by viewModel.profilePictureUri.collectAsState()
    val displayName by viewModel.displayName.collectAsState()
    val bio by viewModel.bio.collectAsState()
    val dobMillis by viewModel.dobMillis.collectAsState()
    val selectedGender by viewModel.selectedGender.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val favouriteColorHex by viewModel.favouriteColorHex.collectAsState()
    val selectedTheme by viewModel.selectedTheme.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Observe user profile for Plenxo ID
    val currentUserProfile by mainViewModel?.currentUserProfile?.collectAsState() ?: remember { mutableStateOf(null) }
    val plenxoId = currentUserProfile?.plenxoId?.ifBlank { null } ?: "PX-Generating..."

    var selectedAvatarUrl by remember { mutableStateOf(profileUri?.toString() ?: PRESET_AVATARS[0]) }

    var showDatePicker by remember { mutableStateOf(false) }
    var genderExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setProfilePicture(uri)
            selectedAvatarUrl = uri.toString()
        }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            photoLauncher.launch("image/*")
        } else {
            Toast.makeText(context, "Storage/Media permission is required to select photos.", Toast.LENGTH_LONG).show()
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

    val colorSwatches = listOf(
        "#8A2BE2", // Violet
        "#58A6FF", // Blue
        "#2EA043", // Emerald
        "#A371F7", // Purple
        "#F0883E", // Orange
        "#33B3AE"  // Teal
    )

    val genderOptions = listOf("Male", "Female", "Other", "Prefer not to say")
    val themeOptions = listOf("System Default", "Light", "Dark")

    LaunchedEffect(uiState) {
        when (uiState) {
            is ProfileSetupUiState.Success -> {
                val id = (uiState as ProfileSetupUiState.Success).plenxoId
                onSetupSuccess(id)
            }
            is ProfileSetupUiState.Error -> {
                val err = (uiState as ProfileSetupUiState.Error).message
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = PlenxoCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Profile Customization",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 20.sp
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                // ==================== 1. GLOWING AVATAR & IDENTITY CARD ====================
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = PlenxoPurple, spotColor = PlenxoIndigo)
                            .testTag("basic_identity_card"),
                        shape = RoundedCornerShape(24.dp),
                        color = DarkCardBg.copy(alpha = 0.88f),
                        border = BorderStroke(width = 1.dp, color = PlenxoPurple.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Basic Identity",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PlenxoCyan,
                                modifier = Modifier.align(Alignment.Start)
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Glowing Ring Avatar Container
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .testTag("profile_picture_avatar"),
                                contentAlignment = Alignment.Center
                            ) {
                                // Glowing Ring Background
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(primaryGradient)
                                        .padding(4.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                        .background(DarkSurface)
                                        .clickable { 
                                            val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                android.Manifest.permission.READ_MEDIA_IMAGES
                                            } else {
                                                android.Manifest.permission.READ_EXTERNAL_STORAGE
                                            }
                                            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                                                photoLauncher.launch("image/*")
                                            } else {
                                                mediaPermissionLauncher.launch(permission)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (profileUri != null) {
                                        AsyncImage(
                                            model = profileUri,
                                            contentDescription = "Profile Picture",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else if (selectedAvatarUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = selectedAvatarUrl,
                                            contentDescription = "Preset Avatar",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(54.dp)
                                        )
                                    }
                                }

                                // Camera Badge
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(primaryGradient)
                                        .clickable { 
                                            val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                android.Manifest.permission.READ_MEDIA_IMAGES
                                            } else {
                                                android.Manifest.permission.READ_EXTERNAL_STORAGE
                                            }
                                            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                                                photoLauncher.launch("image/*")
                                            } else {
                                                mediaPermissionLauncher.launch(permission)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Change photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Choose an Avatar or upload custom photo",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Avatar Preset Selector Row
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(PRESET_AVATARS) { url ->
                                    val isSelected = selectedAvatarUrl == url && profileUri == null
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) PlenxoPurple else DarkSurface)
                                            .clickable {
                                                selectedAvatarUrl = url
                                                viewModel.setProfilePicture(Uri.parse(url))
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = "Avatar Preset",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.35f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = PlenxoCyan,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Plenxo ID Badge Box
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(plenxoId))
                                        Toast.makeText(context, "Plenxo ID copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    }
                                    .testTag("plenxo_id_badge"),
                                color = PlenxoPurple.copy(alpha = 0.15f),
                                border = BorderStroke(width = 1.dp, color = PlenxoCyan.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Your Plenxo ID",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PlenxoCyan
                                        )
                                        Text(
                                            text = plenxoId,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(plenxoId))
                                            Toast.makeText(context, "Plenxo ID copied!", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Plenxo ID",
                                            tint = PlenxoCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Display Name Input
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { viewModel.setDisplayName(it) },
                                label = { Text("Display Name *", color = Color.White.copy(alpha = 0.7f)) },
                                trailingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = PlenxoCyan)
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = DarkSurface.copy(alpha = 0.6f),
                                    unfocusedContainerColor = DarkSurface.copy(alpha = 0.4f),
                                    focusedBorderColor = PlenxoCyan,
                                    unfocusedBorderColor = DarkCardBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = PlenxoCyan
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("display_name_input")
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Bio Input
                            OutlinedTextField(
                                value = bio,
                                onValueChange = { viewModel.setBio(it) },
                                label = { Text("Bio / Status (Optional)", color = Color.White.copy(alpha = 0.7f)) },
                                trailingIcon = {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = PlenxoCyan)
                                },
                                supportingText = {
                                    Text(
                                        text = "${bio.length}/100",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.End
                                    )
                                },
                                shape = RoundedCornerShape(16.dp),
                                minLines = 2,
                                maxLines = 3,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = DarkSurface.copy(alpha = 0.6f),
                                    unfocusedContainerColor = DarkSurface.copy(alpha = 0.4f),
                                    focusedBorderColor = PlenxoCyan,
                                    unfocusedBorderColor = DarkCardBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = PlenxoCyan
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("bio_input")
                            )
                        }
                    }
                }

                // ==================== 2. PERSONAL DETAILS CARD ====================
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = PlenxoPurple, spotColor = PlenxoIndigo)
                            .testTag("personal_details_card"),
                        shape = RoundedCornerShape(24.dp),
                        color = DarkCardBg.copy(alpha = 0.88f),
                        border = BorderStroke(width = 1.dp, color = PlenxoPurple.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                text = "Personal Details",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PlenxoCyan
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Date of Birth Field
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = DateUtils.formatDateToDisplay(dobMillis),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Date of Birth", color = Color.White.copy(alpha = 0.7f)) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.CalendarToday,
                                            contentDescription = "Select Date",
                                            tint = PlenxoCyan
                                        )
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = DarkSurface.copy(alpha = 0.6f),
                                        unfocusedContainerColor = DarkSurface.copy(alpha = 0.4f),
                                        focusedBorderColor = PlenxoCyan,
                                        unfocusedBorderColor = DarkCardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showDatePicker = true }
                                        .testTag("dob_input")
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable { showDatePicker = true }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Gender Dropdown
                            ExposedDropdownMenuBox(
                                expanded = genderExpanded,
                                onExpandedChange = { genderExpanded = !genderExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedGender,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Gender", color = Color.White.copy(alpha = 0.7f)) },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded)
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = DarkSurface.copy(alpha = 0.6f),
                                        unfocusedContainerColor = DarkSurface.copy(alpha = 0.4f),
                                        focusedBorderColor = PlenxoCyan,
                                        unfocusedBorderColor = DarkCardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                        .testTag("gender_dropdown_input")
                                )

                                ExposedDropdownMenu(
                                    expanded = genderExpanded,
                                    onDismissRequest = { genderExpanded = false },
                                    modifier = Modifier.background(DarkSurface)
                                ) {
                                    genderOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option, color = Color.White) },
                                            onClick = {
                                                viewModel.setGender(option)
                                                genderExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ==================== 3. APP PREFERENCES & THEME ====================
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = PlenxoPurple, spotColor = PlenxoIndigo)
                            .testTag("app_preferences_card"),
                        shape = RoundedCornerShape(24.dp),
                        color = DarkCardBg.copy(alpha = 0.88f),
                        border = BorderStroke(width = 1.dp, color = PlenxoPurple.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                text = "App Preferences & Theme",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PlenxoCyan
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Language Dropdown
                            ExposedDropdownMenuBox(
                                expanded = languageExpanded,
                                onExpandedChange = { languageExpanded = !languageExpanded }
                            ) {
                                OutlinedTextField(
                                    value = "${selectedLanguage.name} (${selectedLanguage.code})",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("App Language", color = Color.White.copy(alpha = 0.7f)) },
                                    trailingIcon = {
                                        Icon(Icons.Default.Language, contentDescription = null, tint = PlenxoCyan)
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = DarkSurface.copy(alpha = 0.6f),
                                        unfocusedContainerColor = DarkSurface.copy(alpha = 0.4f),
                                        focusedBorderColor = PlenxoCyan,
                                        unfocusedBorderColor = DarkCardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                        .testTag("language_dropdown_input")
                                )

                                ExposedDropdownMenu(
                                    expanded = languageExpanded,
                                    onDismissRequest = { languageExpanded = false },
                                    modifier = Modifier
                                        .heightIn(max = 280.dp)
                                        .background(DarkSurface)
                                ) {
                                    plenxoLanguages.forEach { lang ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "${lang.name} (${lang.code})",
                                                    color = if (lang.code == selectedLanguage.code) PlenxoCyan else Color.White,
                                                    fontWeight = if (lang.code == selectedLanguage.code) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                viewModel.setLanguage(context, lang)
                                                languageExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Accent Swatches
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Palette, contentDescription = null, tint = PlenxoCyan, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Accent Colour",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                colorSwatches.forEach { hex ->
                                    val parsedColor = try {
                                        Color(android.graphics.Color.parseColor(hex))
                                    } catch (e: Exception) {
                                        PlenxoPurple
                                    }
                                    val isSelected = favouriteColorHex.equals(hex, ignoreCase = true)

                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(parsedColor)
                                            .clickable { viewModel.setFavouriteColor(hex) }
                                            .testTag("color_swatch_$hex"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Theme Selector Pills
                            Text(
                                text = "App Theme Mode",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkSurface, RoundedCornerShape(16.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                themeOptions.forEach { theme ->
                                    val isSelected = selectedTheme.equals(theme, ignoreCase = true) ||
                                            (theme == "System Default" && selectedTheme.equals("System", ignoreCase = true))

                                    Button(
                                        onClick = {
                                            viewModel.setTheme(theme)
                                            val modeKey = when (theme.lowercase()) {
                                                "light" -> "LIGHT"
                                                "dark" -> "DARK"
                                                else -> "SYSTEM_DEFAULT"
                                            }
                                            mainViewModel?.updateAppThemeMode(modeKey)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("theme_button_$theme"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSelected) PlenxoPurple else Color.Transparent,
                                            contentColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
                                        ),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = theme,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ==================== SAVE ACTION BUTTON ====================
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.saveProfile(context, mainViewModel) },
                        enabled = uiState !is ProfileSetupUiState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("save_and_continue_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = Color.White.copy(alpha = 0.12f)
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(primaryGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState is ProfileSetupUiState.Loading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Saving Profile...",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Save Profile & Continue",
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
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // DatePicker Dialog
            if (showDatePicker) {
                val currentMillis = System.currentTimeMillis()
                val defaultMillis = remember(dobMillis) {
                    val cal = java.util.Calendar.getInstance()
                    val dob = dobMillis
                    if (dob != null && dob > 0L && dob <= currentMillis) {
                        dob
                    } else {
                        cal.add(java.util.Calendar.YEAR, -18)
                        cal.timeInMillis
                    }
                }

                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = defaultMillis,
                    yearRange = 1920..java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                )

                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val sel = datePickerState.selectedDateMillis
                                if (sel != null && sel > 0L && sel <= currentMillis) {
                                    viewModel.setDob(sel)
                                }
                                showDatePicker = false
                            }
                        ) {
                            Text("OK", color = PlenxoCyan, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            // Fullscreen Loading Overlay during save
            if (uiState is ProfileSetupUiState.Loading) {
                val loadingMsg = (uiState as ProfileSetupUiState.Loading).message
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, PlenxoPurple.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = PlenxoCyan)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = loadingMsg,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
