package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.AuthState
import com.example.ui.theme.*
import com.example.viewmodel.PlenxoScreen
import com.example.viewmodel.PlenxoViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private val DEFAULT_INTEREST_CATEGORIES = listOf(
    "Tech", "Gaming", "Coding", "Music", "Movies", "Anime", "AI", "Design", "Art", "Fitness", "Crypto", "Photography"
)

private val GENDER_OPTIONS = listOf("Prefer not to say", "Male", "Female", "Non-binary", "Other")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileSetupScreen(
    viewModel: PlenxoViewModel,
    onSetupComplete: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // Observe ViewModel states
    val plenxoId by viewModel.plenxoId.collectAsState()
    val isPlenxoIdAvailable by viewModel.isPlenxoIdAvailable.collectAsState()
    val isGeneratingPlenxoId by viewModel.isGeneratingPlenxoId.collectAsState()
    val isGlobalLoading by viewModel.isLoading.collectAsState()

    // Local screen states
    var displayName by remember { mutableStateOf(viewModel.displayName.value) }
    var bio by remember { mutableStateOf(viewModel.aboutText.value) }
    var selectedGender by remember { mutableStateOf("") }
    var genderDropdownExpanded by remember { mutableStateOf(false) }
    var selectedDobMillis by remember { mutableStateOf<Long?>(null) }
    var selectedInterests by remember { mutableStateOf(setOf<String>()) }

    var avatarUrl by remember { mutableStateOf(viewModel.galleryImageUriString.value?.ifBlank { viewModel.uploadedProfilePicUrl.value ?: "" } ?: viewModel.uploadedProfilePicUrl.value ?: "") }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var avatarUploadError by remember { mutableStateOf<String?>(null) }

    // Auto-generate numeric Plenxo ID behind the scenes during setup profile stage ONLY if not already assigned
    LaunchedEffect(Unit) {
        val currentPxId = viewModel.plenxoId.value.ifBlank { viewModel.revealedPlenxoId.value }
        if (currentPxId.isBlank() || (!Regex("^PX-\\d{6}$").matches(currentPxId) && !Regex("^\\d{6}$").matches(currentPxId))) {
            viewModel.generateUniquePlenxoId()
        }
    }

    // Dynamic Age calculation based on selected Date of Birth
    val calculatedAge = remember(selectedDobMillis) {
        selectedDobMillis?.let { dob ->
            val dobCal = Calendar.getInstance().apply { timeInMillis = dob }
            val nowCal = Calendar.getInstance()
            var age = nowCal.get(Calendar.YEAR) - dobCal.get(Calendar.YEAR)
            if (nowCal.get(Calendar.DAY_OF_YEAR) < dobCal.get(Calendar.DAY_OF_YEAR)) {
                age--
            }
            if (age >= 0) age else null
        }
    }

    // Date picker state
    var showDatePickerDialog by remember { mutableStateOf(false) }

    val formattedDob = remember(selectedDobMillis) {
        selectedDobMillis?.let {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it))
        } ?: "Select date of birth"
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingAvatar = true
            avatarUploadError = null
            viewModel.uploadProfilePictureToCatbox(
                context = context,
                imageUri = uri,
                onSuccess = { uploadedUrl ->
                    isUploadingAvatar = false
                    avatarUrl = uploadedUrl
                    avatarUploadError = null
                    Toast.makeText(context, "Avatar uploaded successfully!", Toast.LENGTH_SHORT).show()
                },
                onError = { errorMsg ->
                    isUploadingAvatar = false
                    avatarUploadError = errorMsg
                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    val isUsernameValid = displayName.trim().isNotBlank()
    val isBioValid = bio.trim().isNotBlank()
    val isAvatarValid = avatarUrl.trim().isNotBlank()
    val isGenderValid = selectedGender.trim().isNotBlank()
    val isHobbiesValid = selectedInterests.size in 2..3 || selectedInterests.size >= 2

    val isButtonEnabled = isUsernameValid &&
            isBioValid &&
            isAvatarValid &&
            isGenderValid &&
            isHobbiesValid &&
            !isGlobalLoading &&
            !isUploadingAvatar &&
            !isGeneratingPlenxoId

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        PlenxoColors.Background,
                        Color(0xFF0F172A),
                        Color(0xFF060913)
                    )
                )
            )
            .systemBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 28.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Set Up Your Profile",
                        style = PlenxoTypography.Title.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PlenxoColors.TextPrimary
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Customize your identity and personalize your Plenxo account",
                        style = PlenxoTypography.Body.copy(
                            fontSize = 14.sp,
                            color = PlenxoColors.TextSecondary
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 1. Profile Picture Picker & Preview
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(PlenxoColors.SurfaceCard)
                            .border(
                                width = 2.5.dp,
                                brush = Brush.sweepGradient(
                                    listOf(
                                        PlenxoColors.Primary,
                                        PlenxoColors.Secondary,
                                        PlenxoColors.Primary
                                    )
                                ),
                                shape = CircleShape
                            )
                            .clickable(enabled = !isUploadingAvatar) {
                                photoPickerLauncher.launch("image/*")
                            }
                            .testTag("avatar_picker_button")
                    ) {
                        if (avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = "Profile Avatar",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Default Avatar Placeholder",
                                modifier = Modifier.size(64.dp),
                                tint = PlenxoColors.TextSecondary.copy(alpha = 0.6f)
                            )
                        }

                        // Uploading overlay indicator
                        if (isUploadingAvatar) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = PlenxoColors.Secondary,
                                    strokeWidth = 3.dp
                                )
                            }
                        }

                        // Camera / Edit badge
                        if (!isUploadingAvatar) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(PlenxoColors.Primary)
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Change Avatar",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (avatarUrl.isNotBlank()) "Tap to change photo" else "Tap to upload profile picture *",
                        style = PlenxoTypography.Caption.copy(
                            fontSize = 12.sp,
                            color = if (avatarUploadError != null) PlenxoColors.Error else PlenxoColors.TextSecondary
                        )
                    )
                }
            }

            // 2. Display Name Input
            item {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { if (it.length <= 30) displayName = it },
                    label = { Text("Name *") },
                    placeholder = { Text("e.g., Alex Carter") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("display_name_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = PlenxoColors.SurfaceCard.copy(alpha = 0.6f),
                        unfocusedContainerColor = PlenxoColors.SurfaceCard.copy(alpha = 0.4f),
                        focusedBorderColor = PlenxoColors.Primary,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedTextColor = PlenxoColors.TextPrimary,
                        unfocusedTextColor = PlenxoColors.TextPrimary,
                        focusedLabelColor = PlenxoColors.Primary,
                        unfocusedLabelColor = PlenxoColors.TextSecondary
                    ),
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Name displayed across chats & profile", color = PlenxoColors.TextSecondary)
                            Text("${displayName.length}/30", color = PlenxoColors.TextSecondary)
                        }
                    }
                )
            }

            // 3. Bio Input
            item {
                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 150) bio = it },
                    label = { Text("Bio *") },
                    placeholder = { Text("Tell us a little bit about yourself...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bio_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = PlenxoColors.SurfaceCard.copy(alpha = 0.6f),
                        unfocusedContainerColor = PlenxoColors.SurfaceCard.copy(alpha = 0.4f),
                        focusedBorderColor = PlenxoColors.Primary,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedTextColor = PlenxoColors.TextPrimary,
                        unfocusedTextColor = PlenxoColors.TextPrimary,
                        focusedLabelColor = PlenxoColors.Primary,
                        unfocusedLabelColor = PlenxoColors.TextSecondary
                    ),
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text("${bio.length}/150", color = PlenxoColors.TextSecondary)
                        }
                    }
                )
            }

            // 4. Age & Date of Birth
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Date of Birth Trigger
                        OutlinedCard(
                            onClick = { showDatePickerDialog = true },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(64.dp)
                                .testTag("dob_picker_card"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = PlenxoColors.SurfaceCard.copy(alpha = 0.4f)
                            ),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x33FFFFFF)))
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = "Date of Birth",
                                    tint = PlenxoColors.Secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Date of Birth",
                                        style = PlenxoTypography.Caption.copy(
                                            fontSize = 11.sp,
                                            color = PlenxoColors.TextSecondary
                                        )
                                    )
                                    Text(
                                        text = formattedDob,
                                        style = PlenxoTypography.Body.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (selectedDobMillis != null) PlenxoColors.TextPrimary else PlenxoColors.TextSecondary
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Dynamic Age Display Box
                        OutlinedCard(
                            onClick = { showDatePickerDialog = true },
                            modifier = Modifier
                                .weight(0.7f)
                                .height(64.dp)
                                .testTag("age_display_card"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = PlenxoColors.SurfaceCard.copy(alpha = 0.4f)
                            ),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x33FFFFFF)))
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Age",
                                    style = PlenxoTypography.Caption.copy(
                                        fontSize = 11.sp,
                                        color = PlenxoColors.TextSecondary
                                    )
                                )
                                Text(
                                    text = if (calculatedAge != null) "$calculatedAge yrs" else "--",
                                    style = PlenxoTypography.Body.copy(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (calculatedAge != null) PlenxoColors.Secondary else PlenxoColors.TextSecondary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 5. Gender Dropdown
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedCard(
                        onClick = { genderDropdownExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .testTag("gender_dropdown_card"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = PlenxoColors.SurfaceCard.copy(alpha = 0.4f)
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x33FFFFFF)))
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wc,
                                    contentDescription = "Gender",
                                    tint = PlenxoColors.Secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Gender *",
                                        style = PlenxoTypography.Caption.copy(
                                            fontSize = 11.sp,
                                            color = PlenxoColors.TextSecondary
                                        )
                                    )
                                    Text(
                                        text = if (selectedGender.isNotBlank()) selectedGender else "Select Gender",
                                        style = PlenxoTypography.Body.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (selectedGender.isNotBlank()) PlenxoColors.TextPrimary else PlenxoColors.TextSecondary
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Gender",
                                tint = PlenxoColors.TextSecondary
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = genderDropdownExpanded,
                        onDismissRequest = { genderDropdownExpanded = false },
                        modifier = Modifier.background(PlenxoColors.SurfaceCard)
                    ) {
                        GENDER_OPTIONS.forEach { gender ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = gender,
                                        color = if (selectedGender == gender) PlenxoColors.Secondary else PlenxoColors.TextPrimary
                                    )
                                },
                                onClick = {
                                    selectedGender = gender
                                    genderDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // 6. Interesting Hobbies
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Interesting Hobbies (Choose 2 or 3) *",
                            style = PlenxoTypography.Body.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = PlenxoColors.TextPrimary
                            )
                        )
                        Text(
                            text = "${selectedInterests.size}/3 selected",
                            style = PlenxoTypography.Caption.copy(
                                color = if (selectedInterests.size in 2..3) PlenxoColors.Secondary else PlenxoColors.TextSecondary
                            )
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DEFAULT_INTEREST_CATEGORIES.forEach { category ->
                            val isSelected = selectedInterests.contains(category)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedInterests = if (isSelected) {
                                        selectedInterests - category
                                    } else {
                                        if (selectedInterests.size < 3) {
                                            selectedInterests + category
                                        } else {
                                            Toast.makeText(context, "Please choose 2 or 3 hobbies.", Toast.LENGTH_SHORT).show()
                                            selectedInterests
                                        }
                                    }
                                },
                                label = {
                                    Text(
                                        text = category,
                                        style = PlenxoTypography.Body.copy(
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else PlenxoColors.TextSecondary
                                        )
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = PlenxoColors.SurfaceCard.copy(alpha = 0.5f),
                                    selectedContainerColor = PlenxoColors.Primary,
                                    labelColor = PlenxoColors.TextSecondary,
                                    selectedLabelColor = Color.White
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = Color(0x33FFFFFF),
                                    selectedBorderColor = PlenxoColors.Primary
                                )
                            )
                        }
                    }
                }
            }



            // 8. Save & Continue Action Button
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (isButtonEnabled) {
                            val targetPxId = if (plenxoId.isNotBlank() && Regex("^PX-\\d{6}$").matches(plenxoId)) {
                                plenxoId
                            } else {
                                viewModel.plenxoId.value.ifBlank { "" }
                            }

                            viewModel.saveProfileStepOne(
                                displayName = displayName,
                                plenxoId = targetPxId,
                                avatarUrl = avatarUrl,
                                bio = bio,
                                gender = selectedGender,
                                dobMillis = selectedDobMillis,
                                interests = selectedInterests.toList(),
                                enable2FA = false,
                                masterPin = null,
                                onComplete = { success ->
                                    if (success) {
                                        val finalPxId = viewModel.plenxoId.value.ifBlank { targetPxId }
                                        viewModel.setRevealedPlenxoId(finalPxId)
                                        viewModel.navigateToScreen(
                                            screen = PlenxoScreen.PLENXO_ID_REVEAL,
                                            addToHistory = false,
                                            clearHistory = true
                                        )
                                    }
                                }
                            )
                        }
                    },
                    enabled = isButtonEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_profile_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PlenxoColors.Primary,
                        disabledContainerColor = PlenxoColors.Primary.copy(alpha = 0.35f),
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.5f)
                    )
                ) {
                    if (isGlobalLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Save & Continue",
                                style = PlenxoTypography.Body.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (!isButtonEnabled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val missingRequirements = mutableListOf<String>()
                    if (!isUsernameValid) missingRequirements.add("Name")
                    if (!isBioValid) missingRequirements.add("Bio")
                    if (!isAvatarValid) missingRequirements.add("Profile picture")
                    if (!isGenderValid) missingRequirements.add("Gender")
                    if (!isHobbiesValid) missingRequirements.add("2 or 3 Hobbies")

                    if (missingRequirements.isNotEmpty()) {
                        Text(
                            text = "Required: ${missingRequirements.joinToString(", ")}",
                            style = PlenxoTypography.Caption.copy(
                                fontSize = 12.sp,
                                color = PlenxoColors.TextSecondary.copy(alpha = 0.8f)
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    if (showDatePickerDialog) {
        val currentMillis = System.currentTimeMillis()
        val defaultMillis = remember(selectedDobMillis) {
            val cal = java.util.Calendar.getInstance()
            val dob = selectedDobMillis
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
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val sel = datePickerState.selectedDateMillis
                        if (sel != null && sel > 0L && sel <= currentMillis) {
                            selectedDobMillis = sel
                        }
                        showDatePickerDialog = false
                    }
                ) {
                    Text("OK", color = PlenxoColors.Primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel", color = PlenxoColors.TextSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
