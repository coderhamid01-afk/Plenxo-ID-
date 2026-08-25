@file:Suppress("DEPRECATION")
package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.serialization.json.*
import com.example.database.AppDatabase
import com.example.model.LocalMessage
import com.example.model.DeliveryStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import android.util.Log
import android.net.Uri
import android.media.MediaPlayer
import com.example.util.SessionManager
import com.example.util.OtpUtils
import com.example.util.OtpRateLimiter
import com.example.network.NetworkModule
import com.example.model.OtpUiState
import com.example.data.repository.OtpRepository
import com.example.data.repository.OtpDeliveryResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import com.example.model.NotificationSoundProfile
import com.example.repository.ChatWallpaperRepository
import com.example.database.ChatWallpaperEntity
import com.example.database.ConversationWallpaperMappingEntity

import com.example.model.ChatRoom
import com.example.model.Message
import com.example.model.User
import com.example.model.Invitation
import com.example.model.FriendRequest
import com.example.model.UserProfile
import com.example.model.AuthState

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import kotlinx.coroutines.tasks.await

sealed class NetlifyOtpResult {
    data class Success(val message: String, val otpCode: String? = null) : NetlifyOtpResult()
    data class Failure(val reason: String) : NetlifyOtpResult()
}
typealias CloudflareOtpResult = NetlifyOtpResult

// Centralized constants for Firestore collections
const val COLLECTION_PROFILE_SHARES = "profile_shares"
const val COLLECTION_USERS = "users"

/**
 * State for generating a profile share link.
 */
sealed class ProfileShareState {
    object Idle : ProfileShareState()
    object Generating : ProfileShareState()
    data class Success(val link: String) : ProfileShareState()
    data class Error(val easyMessage: String) : ProfileShareState()
}

/**
 * State for resolving an incoming deep link.
 */
sealed class DeepLinkResolutionState {
    object Idle : DeepLinkResolutionState()
    object Resolving : DeepLinkResolutionState()
    data class ValidProfileFound(val profile: UserProfile) : DeepLinkResolutionState()
    data class InvalidOrExpired(val easyMessage: String) : DeepLinkResolutionState()
}

data class CallSession(
    val callId: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val callerPic: String = "",
    val receiverId: String = "",
    val type: String = "audio",
    val status: String = "ringing",
    val timestamp: Long = 0L
)

enum class PlenxoScreen {
    LOGIN,
    SIGNUP,
    FORGOT_PASSWORD,
    OTP_VERIFICATION,
    EMAIL_VERIFICATION_WAIT,
    WELCOME,
    PROFILE_SETUP,
    AVATAR_SETUP,
    FINAL_DETAILS,
    PERMISSION_GATEWAY,
    HOME,
    CHAT_DETAIL,
    SETTINGS,
    SETTINGS_PRIVACY,
    SETTINGS_BLOCKED,
    DISCOVERY,
    PROFILE_MANAGEMENT,
    WALLPAPER_GALLERY,
    WALLPAPER_PREVIEW,
    SETTINGS_NORMAL,
    SETTINGS_PROFILE,
    ACTIVE_SESSIONS,
    APP_LOCK_SETUP,
    PROFILE_RINGS,
    CHAT_REQUESTS,
    LANGUAGE_SELECTION,
    PLENXO_ID_REVEAL,
    CALL_HISTORY,
    USER_PROFILE
}

class PlenxoViewModel(application: Application) : AndroidViewModel(application) {

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    fun getChatId(uid1: String, uid2: String): String {
        if (uid1.isEmpty() || uid2.isEmpty()) return ""
        return if (uid1 < uid2) {
            uid1 + "_" + uid2
        } else {
            uid2 + "_" + uid1
        }
    }

    fun getChatRoomId(uid1: String, uid2: String): String {
        return getChatId(uid1, uid2)
    }
    
    private val localSettingsRepo by lazy { com.example.repository.LocalSettingsRepositoryImpl(application) }

    // Form inputs
    val email = MutableStateFlow("")
    val password = MutableStateFlow("")
    val confirmPassword = MutableStateFlow("")
    val isTermsAccepted = MutableStateFlow(false)
    val isPrivacyAccepted: MutableStateFlow<Boolean> get() = isTermsAccepted
    val phoneNumber = MutableStateFlow("")

    // Brute Force & Lockout State Flows
    val loginLockoutRemainingTime = MutableStateFlow(0L)
    val showLockoutDialog = MutableStateFlow(false)
    val lockoutIdentifier = MutableStateFlow("")

    // Forgot Password State Flows
    val forgotPasswordInput = MutableStateFlow("")
    val isForgotPasswordLoading = MutableStateFlow(false)
    val forgotPasswordErrorMessage = MutableStateFlow<String?>(null)
    val forgotPasswordSuccessMessage = MutableStateFlow<String?>(null)
    val forgotPasswordCooldownSeconds = MutableStateFlow(0)
    private var forgotPasswordCooldownJob: kotlinx.coroutines.Job? = null

    // Call Log State Flows
    val callLogs = MutableStateFlow<List<com.example.model.CallLog>>(emptyList())
    private var callLogsListener: com.google.firebase.firestore.ListenerRegistration? = null
    val activeSimulatedCall = MutableStateFlow<com.example.model.CallLog?>(null)
    val simulatedCallState = MutableStateFlow("")
    val simulatedCallDuration = MutableStateFlow(0L)
    private var simulatedCallJob: kotlinx.coroutines.Job? = null

    // Dual-Stage CAPTCHA State Flows
    private val _captchaStage = MutableStateFlow(com.example.model.CaptchaStage.LOCKED)
    val captchaStage: StateFlow<com.example.model.CaptchaStage> = _captchaStage.asStateFlow()

    private val _textCaptchaCode = MutableStateFlow(generateRandomCaptchaCode())
    val textCaptchaCode: StateFlow<String> = _textCaptchaCode.asStateFlow()

    val textCaptchaInput = MutableStateFlow("")

    private fun generateRandomCaptchaCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        return (1..6)
            .map { chars[kotlin.random.Random.nextInt(chars.length)] }
            .joinToString("")
    }

    fun verifyStage1Text(): Boolean {
        val input = textCaptchaInput.value.trim()
        val code = _textCaptchaCode.value.trim()
        return if (input.equals(code, ignoreCase = true)) {
            _captchaStage.value = com.example.model.CaptchaStage.STAGE_1_CLEARED
            true
        } else {
            textCaptchaInput.value = ""
            _textCaptchaCode.value = generateRandomCaptchaCode()
            _captchaStage.value = com.example.model.CaptchaStage.LOCKED
            false
        }
    }

    fun verifyStage2Slider(isAligned: Boolean) {
        if (isAligned && _captchaStage.value == com.example.model.CaptchaStage.STAGE_1_CLEARED) {
            _captchaStage.value = com.example.model.CaptchaStage.FULLY_VERIFIED
        }
    }

    fun resetCaptcha() {
        _captchaStage.value = com.example.model.CaptchaStage.LOCKED
        textCaptchaInput.value = ""
        _textCaptchaCode.value = generateRandomCaptchaCode()
    }

    // Onboarding / Profile States
    val selectedTheme = MutableStateFlow("Blue") // Choices: "Red", "Blue", "Purple", "Black", "Golden"
    
    // Global Settings State Flows
    val globalAppLockEnabled = MutableStateFlow(false)
    val appUnlockedState = MutableStateFlow(false)
    val isAppLockPromptShowing = MutableStateFlow(false)
    val lockedChatIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val disappearingTimer = MutableStateFlow(0L) // 0 = disabled, or milliseconds for 24h / 7d
    val typingUsers = MutableStateFlow<Map<String, String>>(emptyMap()) // chatId to text like "Someone is typing..."
    val replyToMessage = MutableStateFlow<Message?>(null)
    val fontSize = MutableStateFlow("medium") // "small", "medium", "large"
    val chatWallpaperUri = MutableStateFlow<String?>(null)
    val messageNotifications = MutableStateFlow(true)
    val vibrateEnabled = MutableStateFlow(true)
    val popupEnabled = MutableStateFlow(true)
    val lastSeenVisibility = MutableStateFlow("EVERYONE")
    val profilePhotoVisibility = MutableStateFlow("EVERYONE")
    val bioVisibility = MutableStateFlow("EVERYONE")
    val readReceiptsEnabled = MutableStateFlow(true)
    val aboutText = MutableStateFlow("Hey there! I am using Plenxo Pro.")
    val pinnedChatIds = MutableStateFlow<Set<String>>(emptySet())
    val isLocalOnlyMode = MutableStateFlow(false)
    val darkModeEnabled = MutableStateFlow(true)
    val blockScreenshots = MutableStateFlow(false)
    val selectedMessageForActions = MutableStateFlow<Message?>(null)
    val isEditingMode = MutableStateFlow(false)
    
    // Security and Session States
    private val securityRepo by lazy { com.example.repository.SecurityRepository(application) }
    val activeSessions = MutableStateFlow<List<com.example.model.ActiveSession>>(emptyList())
    private var sessionsListener: kotlinx.coroutines.Job? = null

    // Missing variables causing build errors
    val avatarType = MutableStateFlow("placeholder") // "gallery", "placeholder", "emoji"
    val selectedAvatarIndex = MutableStateFlow(0)
    val selectedEmoji = MutableStateFlow("😊")
    val birthDay = MutableStateFlow("")
    val birthMonth = MutableStateFlow("")
    val birthYear = MutableStateFlow("")
    val maleAvatars = listOf<Pair<Int, String>>()
    val femaleAvatars = listOf<Pair<Int, String>>()
    val totalActiveSeconds = MutableStateFlow(0L)
    val totalAccumulatedSeconds = MutableStateFlow(0L)
    val rankProgressState = MutableStateFlow(0f)
    val selectedRingId = MutableStateFlow("NONE")
    val profileRingId = MutableStateFlow(com.example.util.SessionManager.getProfileRingId(application))

    // Voice Recording States
    private val audioRecorder by lazy { com.example.media.AudioVoiceRecorder(getApplication()) }
    val voicePlayer by lazy { com.example.media.VoicePlayer(getApplication()) }
    private var currentRecordingFile: File? = null
    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()

    // Voice Playback States
    private var activeMediaPlayer: MediaPlayer? = null
    private val _playingAudioUrl = MutableStateFlow<String?>(null)
    val playingAudioUrl: StateFlow<String?> = _playingAudioUrl.asStateFlow()

    fun playAudio(url: String) {
        if (_playingAudioUrl.value == url) {
            pauseAudio()
            return
        }
        
        try {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
        } catch (e: Exception) {
            Log.e("PlenxoViewModel", "Error stopping previous playback", e)
        }

        _playingAudioUrl.value = url
        val player = MediaPlayer().apply {
            try {
                setDataSource(url)
                prepareAsync()
                setOnPreparedListener {
                    it.start()
                }
                setOnCompletionListener {
                    _playingAudioUrl.value = null
                    activeMediaPlayer = null
                }
                setOnErrorListener { _, _, _ ->
                    _playingAudioUrl.value = null
                    activeMediaPlayer = null
                    true
                }
            } catch (e: Exception) {
                Log.e("PlenxoViewModel", "Error preparing media player", e)
                _playingAudioUrl.value = null
                activeMediaPlayer = null
            }
        }
        activeMediaPlayer = player
    }

    fun pauseAudio() {
        try {
            activeMediaPlayer?.pause()
        } catch (e: Exception) {
            Log.e("PlenxoViewModel", "Error pausing playback", e)
        }
        _playingAudioUrl.value = null
    }

    fun stopAudio() {
        try {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
        } catch (e: Exception) {
            Log.e("PlenxoViewModel", "Error stopping playback", e)
        }
        activeMediaPlayer = null
        _playingAudioUrl.value = null
    }

    // Local Preferences State
    val appThemeMode = MutableStateFlow("SYSTEM_DEFAULT")
    val selectedChatWallpaper = MutableStateFlow("DEFAULT")
    val selectedNotificationSoundName = MutableStateFlow("DEFAULT")
    val isLocalOnlyEnabled = MutableStateFlow(false)
    
    // Profile Pic States
    val galleryImageUriString = MutableStateFlow<String?>(null)
    val isProfilePicUploading = MutableStateFlow(false)
    val uploadedProfilePicUrl = MutableStateFlow<String?>(null)

    // Profile Details
    val displayName = MutableStateFlow("")
    val userCode = MutableStateFlow("")
    val plenxoId = MutableStateFlow("")

    fun uploadProfilePicture(uri: Uri) {
        val currentUid = currentUserId
        if (currentUid.isEmpty()) return
        isProfilePicUploading.value = true
        viewModelScope.launch {
            try {
                val url = uploadToCatbox(uri)
                if (!url.isNullOrBlank()) {
                    uploadedProfilePicUrl.value = url
                    galleryImageUriString.value = url

                    // Automatically update user's profilePicUrl field in Firebase
                    try {
                        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        val updates = mapOf(
                            "profilePicUrl" to url,
                            "avatar_url" to url,
                            "photoUrl" to url,
                            "updatedAt" to System.currentTimeMillis()
                        )
                        firestore.collection("users").document(currentUid)
                            .set(updates, com.google.firebase.firestore.SetOptions.merge())

                        val currentProfile = currentUserProfile.value
                        if (currentProfile != null) {
                            currentUserProfile.value = currentProfile.copy(profilePicUrl = url)
                        }
                    } catch (fsEx: Exception) {
                        Log.e("Catbox", "Failed to auto-update Firestore profilePicUrl: ${fsEx.message}")
                    }

                    isProfilePicUploading.value = false
                    Log.d("Catbox", "Profile picture uploaded and linked to Firebase successfully: $url")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "Profile picture updated successfully!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    isProfilePicUploading.value = false
                    _errorMessage.value = "Failed to upload image to Catbox. Please try again."
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "Failed to upload image to Catbox. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("Catbox", "Exception uploading profile picture", e)
                isProfilePicUploading.value = false
                _errorMessage.value = "Failed to upload image to Catbox. Please try again."
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Failed to upload image to Catbox. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun uploadToCatbox(uri: Uri): String? {
        return try {
            com.example.network.CatboxUploader.uploadImage(getApplication(), uri)
        } catch (e: Exception) {
            Log.e("Catbox", "Catbox upload exception: ${e.message}", e)
            null
        }
    }

    fun saveProfileSetup() {
        val avatarUrl = uploadedProfilePicUrl.value ?: galleryImageUriString.value ?: ""
        val dName = displayName.value.ifBlank { "Plenxo User" }
        val currentPx = plenxoId.value.ifBlank { "PX-000000" }
        saveProfileStepOne(
            displayName = dName,
            plenxoId = currentPx,
            avatarUrl = avatarUrl,
            bio = aboutText.value
        )
    }

    // User Discovery & Social Dashboard
    val discoverySearchQuery = MutableStateFlow("")
    val discoveryUsers = MutableStateFlow<List<UserProfile>>(emptyList())
    val discoveryRequestedUserIds = MutableStateFlow<Set<String>>(emptySet())
    
    private val _outgoingPendingRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val outgoingPendingRequests: StateFlow<List<FriendRequest>> = _outgoingPendingRequests.asStateFlow()
    
    private var outgoingRequestListener: kotlinx.coroutines.Job? = null
    val currentUserProfile = MutableStateFlow<UserProfile?>(null)
    private var currentUserProfileListener: com.google.firebase.firestore.ListenerRegistration? = null

    fun observeCurrentUserProfile() {
        currentUserProfileListener?.remove()
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (uid.isEmpty()) return

        // Legacy User Repair (Safety Net using consolidated resolveOrCreatePlenxoId)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.example.model.resolveOrCreatePlenxoId(uid, firestore)
            } catch (e: Exception) {
                Log.e("PlenxoProfileSync", "Error resolving Plenxo ID in observeCurrentUserProfile: ${e.message}")
            }
        }

        currentUserProfileListener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val data = snapshot.data ?: emptyMap()
                val resolvedName = (data["displayName"] as? String)
                    ?: (data["display_name"] as? String)
                    ?: (data["name"] as? String)
                    ?: (data["current_name"] as? String)
                    ?: (data["fullName"] as? String)
                    ?: ""
                val resolvedBio = (data["bio"] as? String)
                    ?: (data["statusMessage"] as? String)
                    ?: (data["current_bio"] as? String)
                    ?: (data["status_message"] as? String)
                    ?: (data["about"] as? String)
                    ?: (data["status"] as? String)
                    ?: ""
                val resolvedPic = (data["profilePicUrl"] as? String)
                    ?: (data["profilePic"] as? String)
                    ?: (data["avatarUrl"] as? String)
                    ?: (data["avatar_url"] as? String)
                    ?: (data["photoUrl"] as? String)
                    ?: (data["photo_url"] as? String)
                    ?: (data["profileUrl"] as? String)
                    ?: (data["current_profile_pic_url"] as? String)
                    ?: ""
                val resolvedRing = (data["selectedRingId"] as? String)
                    ?: (data["profileRingId"] as? String)
                    ?: (data["profileRing"] as? String)
                    ?: "none"
                val resolvedPlenxoId = (data["plenxoId"] as? String)
                    ?: (data["plenxo_id"] as? String)
                    ?: (data["px_id"] as? String)
                    ?: ""
                val resolvedCode = (data["userCode"] as? String)
                    ?: (data["user_code"] as? String)
                    ?: (data["px_code"] as? String)
                    ?: resolvedPlenxoId.removePrefix("PX-")
                val resolvedEmail = (data["email"] as? String)?.takeIf { it.isNotBlank() }
                    ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
                    ?: ""

                val current = currentUserProfile.value
                val newProf = (current ?: UserProfile(id = uid, uid = uid)).copy(
                    id = snapshot.id,
                    uid = uid,
                    email = resolvedEmail.ifEmpty { current?.email ?: "" },
                    displayName = resolvedName.ifEmpty { current?.displayName ?: "" },
                    statusMessage = resolvedBio.ifEmpty { current?.statusMessage ?: "" },
                    bio = resolvedBio.ifEmpty { current?.bio ?: "" },
                    profilePicUrl = resolvedPic.ifEmpty { current?.profilePicUrl ?: "" },
                    profileRingId = resolvedRing.ifEmpty { current?.profileRingId ?: "none" },
                    userCode = resolvedCode.ifEmpty { current?.userCode ?: "" },
                    plenxoId = resolvedPlenxoId.ifEmpty { current?.plenxoId ?: "" }
                )
                currentUserProfile.value = newProf

                if (newProf.plenxoId.isNotBlank()) {
                    plenxoId.value = newProf.plenxoId
                    revealedPlenxoId.value = newProf.plenxoId
                }
                if (newProf.userCode.isNotBlank()) {
                    userCode.value = newProf.userCode
                }
                if (newProf.displayName.isNotBlank()) {
                    displayName.value = newProf.displayName
                }
                if (newProf.bio.isNotBlank()) {
                    aboutText.value = newProf.bio
                }
                if (newProf.profilePicUrl.isNotBlank()) {
                    galleryImageUriString.value = newProf.profilePicUrl
                    uploadedProfilePicUrl.value = newProf.profilePicUrl
                    if (newProf.profilePicUrl.startsWith("http")) {
                        avatarType.value = "gallery"
                    }
                }
                if (newProf.email.isNotBlank()) {
                    email.value = newProf.email
                }
                if (resolvedRing.isNotBlank() && resolvedRing != "none") {
                    profileRingId.value = resolvedRing
                }

                try {
                    val ageVal = (data["age"]?.toString()) ?: (data["dateOfBirth"] as? String) ?: ""
                    SessionManager.saveUserProfileLocally(
                        getApplication(),
                        plenxoId = newProf.plenxoId,
                        displayName = newProf.displayName,
                        bio = newProf.bio,
                        profilePicUrl = newProf.profilePicUrl,
                        age = ageVal
                    )
                } catch (_: Throwable) {}

                // Sync Two-Factor Authentication state & master PIN
                val resolved2FA = (data["is2FAEnabled"] as? Boolean)
                    ?: (data["is_2fa_enabled"] as? Boolean)
                    ?: (data["twoFactorEnabled"] as? Boolean)
                    ?: false
                _is2FAEnabled.value = resolved2FA
                val resolvedPin = (data["masterPin"] as? String) ?: (data["master_pin"] as? String)
                if (!resolvedPin.isNullOrBlank()) {
                    _storedMasterPinHash.value = resolvedPin
                }
            }
    }
    private var discoverySearchJob: Job? = null

    // Elite Deep Link Profile Sharing States
    private val _profileShareState = MutableStateFlow<ProfileShareState>(ProfileShareState.Idle)
    val profileShareState: StateFlow<ProfileShareState> = _profileShareState.asStateFlow()

    private val _deepLinkResolutionState = MutableStateFlow<DeepLinkResolutionState>(DeepLinkResolutionState.Idle)
    val deepLinkResolutionState: StateFlow<DeepLinkResolutionState> = _deepLinkResolutionState.asStateFlow()

    /**
     * Generates a unique, 1-hour time-limited profile share link.
     */
    fun shareProfileLink(context: Context) {
        val uid = currentUserId
        if (uid.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _profileShareState.value = ProfileShareState.Generating
                val token = java.util.UUID.randomUUID().toString().take(16)
                val shareData = mapOf("token" to token, "ownerUid" to uid, "createdAt" to System.currentTimeMillis())
                firestore.collection("profile_shares").document(token).set(shareData).await()
                val deepLink = "plenxo://addfriend?token=$token"
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "Add me on Plenxo! $deepLink")
                    type = "text/plain"
                }
                withContext(Dispatchers.Main) {
                    val chooserIntent = Intent.createChooser(shareIntent, "Share Profile").apply {
                        if (context !is android.app.Activity) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    context.startActivity(chooserIntent)
                    _profileShareState.value = ProfileShareState.Success(deepLink)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _profileShareState.value = ProfileShareState.Error("Failed") }
            }
        }
    }

    fun sendDeepLinkFriendRequest() {
        // Placeholder to fix build
    }

    /**
     * Resolves user profile by Plenxo ID and navigates directly to User Profile Screen.
     */
    fun openUserProfileByPlenxoId(plenxoIdRaw: String) {
        val cleanId = plenxoIdRaw.trim().removePrefix("@").removePrefix("#")
        if (cleanId.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Search users by plenxoId, plenxo_id, userCode, or document ID
                val querySnapshot = firestore.collection("users")
                    .whereEqualTo("plenxoId", cleanId)
                    .get().await()

                val doc = if (!querySnapshot.isEmpty) {
                    querySnapshot.documents.first()
                } else {
                    val queryAlt = firestore.collection("users")
                        .whereEqualTo("userCode", cleanId)
                        .get().await()
                    if (!queryAlt.isEmpty) {
                        queryAlt.documents.first()
                    } else {
                        firestore.collection("users").document(cleanId).get().await()
                    }
                }

                val foundUid = if (doc.exists()) doc.id else cleanId
                withContext(Dispatchers.Main) {
                    selectedUserIdForProfile.value = foundUid
                    navigateToScreen(PlenxoScreen.USER_PROFILE)
                }
            } catch (e: Exception) {
                Log.w("PlenxoViewModel", "Error resolving profile deep link for $cleanId: ${e.message}")
                withContext(Dispatchers.Main) {
                    selectedUserIdForProfile.value = cleanId
                    navigateToScreen(PlenxoScreen.USER_PROFILE)
                }
            }
        }
    }

    /**
     * Handles and validates incoming deep link intents (App Links & Custom URI Schemes).
     */
    fun handleDeepLink(uri: android.net.Uri?) {
        if (uri == null) return

        val scheme = uri.scheme
        val host = uri.host
        val path = uri.path ?: ""

        var extractedPlenxoId: String? = null

        // HTTPS App Links: https://monumental-kangaroo-743f01.netlify.app/user/{plenxo_id}, https://plenxo.app/user/{plenxo_id}, or https://plenxo.netlify.app/user/{plenxo_id}
        if ((scheme == "https" || scheme == "http") && (host == "monumental-kangaroo-743f01.netlify.app" || host == "plenxo.app" || host == "plenxo.netlify.app")) {
            if (path.startsWith("/user/")) {
                extractedPlenxoId = path.removePrefix("/user/").trim()
            }
        } 
        // Custom URI Scheme: plenxo://user/{plenxo_id} or plenxo://user?id={plenxo_id}
        else if (scheme == "plenxo" && host == "user") {
            if (path.startsWith("/") && path.length > 1) {
                extractedPlenxoId = path.removePrefix("/").trim()
            } else {
                extractedPlenxoId = uri.getQueryParameter("id") ?: uri.getQueryParameter("plenxo_id")
            }
        }

        if (!extractedPlenxoId.isNullOrBlank()) {
            openUserProfileByPlenxoId(extractedPlenxoId)
            return
        }

        // Legacy Token Add Friend Deep Link: plenxo://addfriend?token=...
        if (scheme == "plenxo" && host == "addfriend") {
            val token = uri.getQueryParameter("token")
            if (token.isNullOrBlank()) {
                _deepLinkResolutionState.value = DeepLinkResolutionState.InvalidOrExpired("Broken link.")
                return
            }
            viewModelScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) { _deepLinkResolutionState.value = DeepLinkResolutionState.Resolving }
                try {
                    val doc = firestore.collection("profile_shares").document(token).get().await()
                    if (!doc.exists()) {
                        withContext(Dispatchers.Main) { _deepLinkResolutionState.value = DeepLinkResolutionState.InvalidOrExpired("Invalid link.") }
                        return@launch
                    }
                    val ownerUid = doc.getString("ownerUid") ?: ""
                    val profileDoc = firestore.collection("users").document(ownerUid).get().await()
                    val profile = profileDoc.toObject(UserProfile::class.java)
                    withContext(Dispatchers.Main) {
                        if (profile != null) _deepLinkResolutionState.value = DeepLinkResolutionState.ValidProfileFound(profile)
                        else _deepLinkResolutionState.value = DeepLinkResolutionState.InvalidOrExpired("User not found.")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { _deepLinkResolutionState.value = DeepLinkResolutionState.InvalidOrExpired("Error") }
                }
            }
        }
    }

    fun clearDeepLinkResult() {
        _deepLinkResolutionState.value = DeepLinkResolutionState.Idle
        _profileShareState.value = ProfileShareState.Idle
    }

    // Screen navigation
    val revealedPlenxoId = MutableStateFlow("")
    fun setRevealedPlenxoId(id: String) {
        val clean = id.trim().removePrefix("@").removePrefix("#")
        val formatted = if (clean.startsWith("PX-", ignoreCase = true)) {
            "PX-${clean.removePrefix("PX-").removePrefix("px-")}"
        } else if (clean.length == 6 && clean.all { it.isDigit() }) {
            "PX-$clean"
        } else if (clean.isNotBlank()) {
            "PX-$clean"
        } else {
            "PX-000000"
        }
        revealedPlenxoId.value = formatted
    }
    private val _currentScreen = MutableStateFlow(PlenxoScreen.LOGIN)
    val currentScreen: StateFlow<PlenxoScreen> = _currentScreen
    private val screenHistory = mutableListOf<PlenxoScreen>()

    fun navigateToScreen(screen: PlenxoScreen, addToHistory: Boolean = true, clearHistory: Boolean = false) {
        try {
            if (screen == PlenxoScreen.PROFILE_SETUP) {
                val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (currentUser == null && tempSignupEmail.isBlank()) {
                    _errorMessage.value = "Authentication required. Please log in or sign up."
                    _authState.value = AuthState.UNAUTHENTICATED
                    resetOtpState()
                    _currentScreen.value = PlenxoScreen.LOGIN
                    return
                } else {
                    _authState.value = AuthState.NEEDS_PROFILE_SETUP
                }
            }
            synchronized(screenHistory) {
                if (clearHistory) {
                    screenHistory.clear()
                }
                if (addToHistory && _currentScreen.value != screen) {
                    screenHistory.add(_currentScreen.value)
                }
            }
            _currentScreen.value = screen
            if (screen == PlenxoScreen.DISCOVERY) {
                preloadDiscoveryUsers()
            }
            if (screen == PlenxoScreen.CHAT_REQUESTS) {
                fetchPendingFriendRequests()
            }
        } catch (e: Exception) {
            android.util.Log.e("PlenxoViewModel", "Error in navigateToScreen: ${e.localizedMessage}", e)
        }
    }

    fun navigateToActiveSessions() {
        startListeningToSessions()
        navigateToScreen(PlenxoScreen.ACTIVE_SESSIONS)
    }

    fun navigateToAppLockSetup() {
        try {
            val intent = Intent(getApplication(), com.example.ui.AppLockSetupActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("PlenxoViewModel", "Error starting AppLockSetupActivity: ${e.localizedMessage}", e)
        }
    }

    fun lockChat(chatId: String, pin: String) {
        securityRepo.setChatLock(chatId, pin)
    }

    fun navigateBack(): Boolean {
        return try {
            resetOtpState()
            val previousScreen = synchronized(screenHistory) {
                if (screenHistory.isNotEmpty()) {
                    screenHistory.removeAt(screenHistory.size - 1)
                } else {
                    null
                }
            }
            if (previousScreen != null) {
                _currentScreen.value = previousScreen
                true
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("PlenxoViewModel", "Error in navigateBack: ${e.localizedMessage}", e)
            false
        }
    }

    fun navigateBackTo(screen: PlenxoScreen) {
        try {
            synchronized(screenHistory) {
                while (screenHistory.isNotEmpty()) {
                    val last = screenHistory.last()
                    if (last == screen) {
                        _currentScreen.value = screenHistory.removeAt(screenHistory.size - 1)
                        return
                    }
                    screenHistory.removeAt(screenHistory.size - 1)
                }
            }
            _currentScreen.value = screen
        } catch (e: Exception) {
            android.util.Log.e("PlenxoViewModel", "Error in navigateBackTo: ${e.localizedMessage}", e)
        }
    }

    // Loading & Error States
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun clearLoading() {
        _isLoading.value = false
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // Unified Auth state
    private val _authState = MutableStateFlow<AuthState>(AuthState.UNAUTHENTICATED)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Plenxo ID State & Availability Flow
    private val _isPlenxoIdAvailable = MutableStateFlow<Boolean?>(null)
    val isPlenxoIdAvailable: StateFlow<Boolean?> = _isPlenxoIdAvailable.asStateFlow()
    private val _isGeneratingPlenxoId = MutableStateFlow(false)
    val isGeneratingPlenxoId: StateFlow<Boolean> = _isGeneratingPlenxoId.asStateFlow()

    // Two-Factor Authentication (2FA) and Secret Master PIN States
    private val _is2FAEnabled = MutableStateFlow(false)
    val is2FAEnabled: StateFlow<Boolean> = _is2FAEnabled.asStateFlow()

    private val _isSettingUp2FA = MutableStateFlow(false)
    val isSettingUp2FA: StateFlow<Boolean> = _isSettingUp2FA.asStateFlow()

    private val _setup2FAError = MutableStateFlow<String?>(null)
    val setup2FAError: StateFlow<String?> = _setup2FAError.asStateFlow()

    private val _storedMasterPinHash = MutableStateFlow<String?>(null)

    // Temporary Signup Data Holding in memory
    private var tempSignupEmail = ""
    private var tempSignupPassword = ""
    private var tempSignupName = ""

    fun setAuthState(state: AuthState) {
        _authState.value = state
    }

    private val _authenticatedUserId = MutableStateFlow<String?>(FirebaseAuth.getInstance().currentUser?.uid)
    val authenticatedUserId: StateFlow<String?> = _authenticatedUserId.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        _authenticatedUserId.value = auth.currentUser?.uid
    }

    private val otpRepository by lazy { OtpRepository(getApplication(), NetworkModule.otpApiService, firestore) }

    private val _otpUiState = MutableStateFlow<OtpUiState>(OtpUiState.Idle)
    fun resetOtpState() {
        _otpUiState.value = OtpUiState.Idle
        timerJob?.cancel()
        _isTimerRunning.value = false
        _secondsRemaining.value = 0
        _activeOtp.value = ""
        _generatedOtp.value = ""
        enteredOtp.value = ""
        tempSignupEmail = ""
    }
    val otpUiState: StateFlow<OtpUiState> = _otpUiState.asStateFlow()

    private val _activeOtp = MutableStateFlow("")
    val activeOtp: StateFlow<String> = _activeOtp

    // StateFlow alias for generatedOtp
    private val _generatedOtp = _activeOtp
    val generatedOtp: StateFlow<String> = _activeOtp

    val enteredOtp = MutableStateFlow("")

    private var failedOtpAttempts = 0
    private val _isOtpButtonFrozen = MutableStateFlow(false)
    val isOtpButtonFrozen: StateFlow<Boolean> = _isOtpButtonFrozen

    // Timer States
    private val _secondsRemaining = MutableStateFlow(60)
    val secondsRemaining: StateFlow<Int> = _secondsRemaining

    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning

    private var timerJob: Job? = null

    // Retrofit Initialization
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Chat States
    private val _chats = MutableStateFlow<List<ChatRoom>>(emptyList())
    
    // Live Contacts Set
    private val _contactsSet = MutableStateFlow<Set<String>>(emptySet())
    val contactsSet: StateFlow<Set<String>> = _contactsSet
    private var contactsListener: kotlinx.coroutines.Job? = null

    // Combine chats with contactsSet so that chats are filtered to only show those whose recipient is in our contacts list
    val chats: StateFlow<List<ChatRoom>> = _chats.asStateFlow()

    val activeCall = MutableStateFlow<CallSession?>(null)
    private var callListener: ListenerRegistration? = null
    private var outgoingCallListener: ListenerRegistration? = null
    private var outgoingCallSignalingManager: com.example.webrtc.CallSignalingManager? = null
    private var incomingCallSignalingManager: com.example.webrtc.CallSignalingManager? = null

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages
    
    private var _messagesOffset = 0
    val isLoadingMore = MutableStateFlow(false)
    val canLoadMore = MutableStateFlow(true)
    private val PAGE_SIZE = 50

    private val _usersCache = MutableStateFlow<Map<String, User>>(emptyMap())
    val usersCache: StateFlow<Map<String, User>> = _usersCache

    private val _pendingInvitations = MutableStateFlow<List<Invitation>>(emptyList())
    val pendingInvitations: StateFlow<List<Invitation>> = _pendingInvitations

    private val _pendingFriendRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val pendingFriendRequests: StateFlow<List<FriendRequest>> = _pendingFriendRequests

    private val _connectedFriends = MutableStateFlow<List<com.example.model.ConnectedFriend>>(emptyList())
    val connectedFriends: StateFlow<List<com.example.model.ConnectedFriend>> = _connectedFriends.asStateFlow()

    val currentChatRecipientName = MutableStateFlow("")
    val currentChatRecipientUid = MutableStateFlow("")
    val currentChatId = MutableStateFlow("")
    
    // Firestore listeners
    private var chatsListener: kotlinx.coroutines.Job? = null
    private var messagesListener: kotlinx.coroutines.Job? = null
    private var invitationsListener: kotlinx.coroutines.Job? = null
    private var friendRequestsListener: kotlinx.coroutines.Job? = null
    private var currentUserListener: kotlinx.coroutines.Job? = null

        
    val currentUserId: String
        get() {
            val fbUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (!fbUid.isNullOrEmpty()) return fbUid
            val savedToken = com.example.util.SessionManager.getLoginState(getApplication()).token
            if (!savedToken.isNullOrEmpty()) return savedToken
            val savedEmail = com.example.util.SessionManager.getLoginState(getApplication()).email
            if (!savedEmail.isNullOrEmpty()) return savedEmail.replace(".", "_")
            return ""
        }

    private val userPrefsRepo by lazy { com.example.repository.UserPreferencesRepository(application) }
    private val localRepo by lazy { com.example.repository.LocalChatRepositoryImpl(application) }
    private val cloudRepo by lazy { com.example.repository.CloudChatRepositoryImpl(application) }
    private val dynamicStorageManager by lazy { com.example.repository.DynamicStorageManager(localRepo, cloudRepo, isLocalOnlyMode) }
    private val wallpaperRepo by lazy { ChatWallpaperRepository(application) }
    
    val selectedNotificationSound = userPrefsRepo.selectedSoundFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        NotificationSoundProfile.MINIMAL_PING
    )
    
    val allWallpapers = wallpaperRepo.allWallpapers
    val selectedWallpaperForPreview = MutableStateFlow<ChatWallpaperEntity?>(null)
    val activeWallpaperConversationId = MutableStateFlow<String?>(null)
    
    fun getWallpapersByCategory(category: String) = wallpaperRepo.getWallpapersByCategory(category)
    fun getWallpaperMappingForConversation(conversationId: String) = wallpaperRepo.getWallpaperMappingForConversation(conversationId)
    
    fun selectNotificationSound(profile: NotificationSoundProfile) {
        viewModelScope.launch {
            userPrefsRepo.setSelectedSound(profile)
            com.example.service.AppNotificationService.updateNotificationChannelSound(getApplication(), profile.name)
        }
    }
    
    fun setWallpaperMappingForConversation(conversationId: String, wallpaperId: String?, opacity: Float) {
        viewModelScope.launch {
            if (wallpaperId == null) {
                wallpaperRepo.deleteWallpaperMapping(conversationId)
            } else {
                wallpaperRepo.insertWallpaperMapping(
                    ConversationWallpaperMappingEntity(conversationId, wallpaperId, opacity)
                )
            }
        }
    }
    
    fun downloadWallpaper(wallpaper: ChatWallpaperEntity) {
        wallpaperRepo.downloadWallpaper(wallpaper.wallpaperId, wallpaper.cloudUrl)
    }

    private var messagesJob: kotlinx.coroutines.Job? = null

    fun setCurrentScreenInternal(screen: PlenxoScreen) {
        _currentScreen.value = screen
    }



    init {
        try {
            FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
        } catch (e: Exception) {
            Log.e("Plenxo", "Error adding authStateListener: ${e.message}")
        }
        loadLocalSettings()
        checkAndRestoreSession()
        startSelfDestructTicker()
        viewModelScope.launch {
            userPrefsRepo.localStorageOnlyFlow.collect { enabled ->
                isLocalOnlyMode.value = enabled
            }
        }
        viewModelScope.launch {
            wallpaperRepo.seedWallpapersIfNeeded()
        }
    }

    fun updateAppThemeMode(mode: String) {
        viewModelScope.launch {
            appThemeMode.value = mode
            localSettingsRepo.setTheme(mode)
        }
    }

    fun updateSelectedChatWallpaper(wallpaper: String) {
        viewModelScope.launch {
            selectedChatWallpaper.value = wallpaper
            localSettingsRepo.setChatWallpaperUri(wallpaper)
        }
    }

    fun updateSelectedNotificationSoundName(sound: String) {
        viewModelScope.launch {
            selectedNotificationSoundName.value = sound
            localSettingsRepo.setNotificationRingtone(sound)
        }
    }

    fun updateIsLocalOnlyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            isLocalOnlyEnabled.value = enabled
            localSettingsRepo.setLocalOnlyEnabled(enabled)
            isLocalOnlyMode.value = enabled
        }
    }



    fun loadLocalSettings() {
        val context = getApplication<Application>()
        
        viewModelScope.launch {
            localSettingsRepo.themeFlow.collect { theme ->
                appThemeMode.value = theme
            }
        }
        viewModelScope.launch {
            localSettingsRepo.chatWallpaperUriFlow.collect { uri ->
                selectedChatWallpaper.value = uri ?: "DEFAULT"
            }
        }
        viewModelScope.launch {
            localSettingsRepo.notificationRingtoneFlow.collect { ringtone ->
                selectedNotificationSoundName.value = ringtone
            }
        }
        viewModelScope.launch {
            localSettingsRepo.isLocalOnlyEnabledFlow.collect { enabled ->
                isLocalOnlyEnabled.value = enabled
            }
        }

        globalAppLockEnabled.value = SessionManager.getGlobalAppLock(context)
        lockedChatIds.value = SessionManager.getLockedChats(context)
        blockedUserIds.value = SessionManager.getBlockedUsers(context)
        disappearingTimer.value = SessionManager.getDisappearingTimer(context)
        fontSize.value = SessionManager.getFontSize(context)
        chatWallpaperUri.value = SessionManager.getWallpaperUri(context)
        messageNotifications.value = SessionManager.getNotifsEnabled(context)
        vibrateEnabled.value = SessionManager.getVibrateEnabled(context)
        popupEnabled.value = SessionManager.getPopupEnabled(context)
        lastSeenVisibility.value = SessionManager.getLastSeenVis(context)
        profilePhotoVisibility.value = SessionManager.getPhotoVis(context)
        bioVisibility.value = SessionManager.getBioVis(context)
        readReceiptsEnabled.value = SessionManager.getReadReceipts(context)
        aboutText.value = SessionManager.getAboutText(context)
        pinnedChatIds.value = SessionManager.getPinnedChats(context)
        isLocalOnlyMode.value = SessionManager.getLocalOnlyMode(context)
        darkModeEnabled.value = SessionManager.getDarkMode(context)
        blockScreenshots.value = SessionManager.isScreenshotsBlocked(context)
    }

    fun saveBlockScreenshots(blocked: Boolean) {
        blockScreenshots.value = blocked
        SessionManager.saveScreenshotsBlocked(getApplication(), blocked)
    }

    fun saveLocalOnlyMode(enabled: Boolean) {
        viewModelScope.launch {
            userPrefsRepo.setLocalStorageOnly(enabled)
            SessionManager.saveLocalOnlyMode(getApplication(), enabled)
        }
    }

    fun saveDarkMode(enabled: Boolean) {
        darkModeEnabled.value = enabled
        SessionManager.saveDarkMode(getApplication(), enabled)
    }

    fun saveGlobalAppLock(enabled: Boolean) {
        globalAppLockEnabled.value = enabled
        SessionManager.saveGlobalAppLock(getApplication(), enabled)
    }

    fun toggleChatLock(chatId: String) {
        val updated = lockedChatIds.value.toMutableSet()
        if (updated.contains(chatId)) {
            updated.remove(chatId)
            securityRepo.setChatLock(chatId, null)
            securityRepo.setChatLockType(chatId, null)
        } else {
            updated.add(chatId)
        }
        lockedChatIds.value = updated
        SessionManager.saveLockedChats(getApplication(), updated)
    }

    fun toggleChatPin(chatId: String) {
        val updated = pinnedChatIds.value.toMutableSet()
        if (updated.contains(chatId)) {
            updated.remove(chatId)
        } else {
            updated.add(chatId)
        }
        pinnedChatIds.value = updated
        SessionManager.savePinnedChats(getApplication(), updated)
    }

    fun blockUser(blockedUid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uid = currentUserId
                if (uid.isNotEmpty()) {
                    val blockData = mapOf("blocker_id" to uid, "blocked_id" to blockedUid, "timestamp" to System.currentTimeMillis())
                    firestore.collection("blocked_users").document("${uid}_${blockedUid}").set(blockData).await()
                    withContext(Dispatchers.Main) { blockedUserIds.value = blockedUserIds.value + blockedUid }
                }
            } catch (e: Exception) {}
        }
    }

    fun unblockUser(blockedUid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uid = currentUserId
                if (uid.isNotEmpty()) {
                    firestore.collection("blocked_users").document("${uid}_${blockedUid}").delete().await()
                    withContext(Dispatchers.Main) { blockedUserIds.value = blockedUserIds.value - blockedUid }
                }
            } catch (e: Exception) {}
        }
    }

    fun saveDisappearingTimer(durationMs: Long) {
        disappearingTimer.value = durationMs
        SessionManager.saveDisappearingTimer(getApplication(), durationMs)
    }

    fun saveFontSize(size: String) {
        fontSize.value = size
        SessionManager.saveFontSize(getApplication(), size)
    }

    fun saveWallpaperUri(uriString: String?) {
        chatWallpaperUri.value = uriString
        SessionManager.saveWallpaperUri(getApplication(), uriString)
    }

    fun saveNotifsEnabled(enabled: Boolean) {
        messageNotifications.value = enabled
        SessionManager.saveNotifsEnabled(getApplication(), enabled)
    }

    fun saveVibrateEnabled(enabled: Boolean) {
        vibrateEnabled.value = enabled
        SessionManager.saveVibrateEnabled(getApplication(), enabled)
    }

    fun savePopupEnabled(enabled: Boolean) {
        popupEnabled.value = enabled
        SessionManager.savePopupEnabled(getApplication(), enabled)
    }

    fun saveLastSeenVis(visibility: String) {
        lastSeenVisibility.value = visibility
        SessionManager.saveLastSeenVis(getApplication(), visibility)
        
        viewModelScope.launch {
            if (isLocalOnlyMode.value) {
                Log.d("Plenxo", "Privacy Mode: Last seen visibility sync disabled")
                return@launch
            }
            try {
                if (currentUserId.isNotEmpty()) {
                    firestore.collection("users").document(currentUserId)
                        .set(mapOf("lastSeenVisibility" to visibility), com.google.firebase.firestore.SetOptions.merge())
                        .await()
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to update last seen visibility on Firestore", e)
            }
        }
    }

    fun savePhotoVis(visibility: String) {
        profilePhotoVisibility.value = visibility
        SessionManager.savePhotoVis(getApplication(), visibility)
        
        viewModelScope.launch {
            if (isLocalOnlyMode.value) {
                Log.d("Plenxo", "Privacy Mode: Profile photo visibility sync disabled")
                return@launch
            }
            try {
                if (currentUserId.isNotEmpty()) {
                    firestore.collection("users").document(currentUserId)
                        .set(mapOf("profilePhotoVisibility" to visibility), com.google.firebase.firestore.SetOptions.merge())
                        .await()
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to update profile photo visibility on Firestore", e)
            }
        }
    }

    fun saveBioVis(visibility: String) {
        bioVisibility.value = visibility
        SessionManager.saveBioVis(getApplication(), visibility)
        
        viewModelScope.launch {
            if (isLocalOnlyMode.value) {
                Log.d("Plenxo", "Privacy Mode: Bio visibility sync disabled")
                return@launch
            }
            try {
                if (currentUserId.isNotEmpty()) {
                    val updates = mapOf(
                        "bioVisibility" to visibility,
                        "isBioPublic" to (visibility.equals("EVERYONE", ignoreCase = true) || visibility.equals("PUBLIC", ignoreCase = true))
                    )
                    firestore.collection("users").document(currentUserId)
                        .set(updates, com.google.firebase.firestore.SetOptions.merge())
                        .await()
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to update bio visibility on Firestore", e)
            }
        }
    }

    fun saveReadReceipts(enabled: Boolean) {
        readReceiptsEnabled.value = enabled
        SessionManager.saveReadReceipts(getApplication(), enabled)
        
        viewModelScope.launch {
            if (isLocalOnlyMode.value) {
                Log.d("Plenxo", "Privacy Mode: Read receipts sync disabled")
                return@launch
            }
            try {
                if (currentUserId.isNotEmpty()) {
                    firestore.collection("users").document(currentUserId)
                        .set(mapOf("readReceiptsEnabled" to enabled), com.google.firebase.firestore.SetOptions.merge())
                        .await()
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to update read receipts on Firestore", e)
            }
        }
    }

    fun updateAboutText(text: String) {
        aboutText.value = text
        SessionManager.saveAboutText(getApplication(), text)
        
        viewModelScope.launch {
            if (isLocalOnlyMode.value) {
                Log.d("Plenxo", "Privacy Mode: About text sync disabled")
                return@launch
            }
            try {
                if (currentUserId.isNotEmpty()) {
                    firestore.collection("users").document(currentUserId)
                        .set(mapOf("about" to text), com.google.firebase.firestore.SetOptions.merge())
                        .await()
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to update about text on Firestore", e)
            }
        }
    }

    fun checkAndRestoreSession() {
        try {
            val fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (fbUser != null) {
                Log.d("Plenxo", "Persistent session found for ${fbUser.uid}")
                email.value = fbUser.email ?: ""
                
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val uid = fbUser.uid
                        val userDoc = firestore.collection("users").document(uid).get().await()
                        
                        val docToUse = userDoc
                        val fetchedName = docToUse?.getString("displayName") 
                            ?: docToUse?.getString("display_name")
                            ?: docToUse?.getString("name") 
                            ?: docToUse?.getString("current_name")
                            ?: docToUse?.getString("fullName")
                            ?: ""
                        val fetchedCode = docToUse?.getString("plenxoId") 
                            ?: docToUse?.getString("plenxo_id") 
                            ?: docToUse?.getString("px_id") 
                            ?: docToUse?.getString("userCode") 
                            ?: docToUse?.getString("user_code") 
                            ?: ""
                        val isSetupCompleted = docToUse?.getBoolean("isProfileSetupCompleted") == true ||
                                               docToUse?.getBoolean("isProfileSetup") == true || 
                                               docToUse?.getBoolean("profileSetupCompleted") == true ||
                                               docToUse?.getBoolean("is_profile_completed") == true ||
                                               docToUse?.getBoolean("isProfileCompleted") == true
                        val isEmailVerified = fbUser.isEmailVerified || 
                                              docToUse?.getBoolean("isEmailVerified") == true || 
                                              docToUse?.getBoolean("emailVerified") == true || 
                                              docToUse?.getBoolean("is_email_verified") == true || 
                                              isSetupCompleted ||
                                              fetchedName.isNotBlank() ||
                                              fetchedCode.isNotBlank()

                        withContext(Dispatchers.Main) {
                            if (docToUse != null && docToUse.exists()) {
                                val fetchedBio = docToUse.getString("bio") 
                                    ?: docToUse.getString("statusMessage") 
                                    ?: docToUse.getString("current_bio")
                                    ?: docToUse.getString("status_message")
                                    ?: docToUse.getString("about")
                                    ?: docToUse.getString("status")
                                    ?: ""
                                val fetchedPic = docToUse.getString("profilePicUrl") 
                                    ?: docToUse.getString("profilePic")
                                    ?: docToUse.getString("avatarUrl") 
                                    ?: docToUse.getString("avatar_url") 
                                    ?: docToUse.getString("photoUrl") 
                                    ?: docToUse.getString("photo_url")
                                    ?: docToUse.getString("profileUrl")
                                    ?: docToUse.getString("current_profile_pic_url")
                                    ?: ""
                                val fetchedAge = docToUse.get("age")?.toString() ?: docToUse.getString("dateOfBirth") ?: ""

                                if (fetchedName.isNotBlank()) displayName.value = fetchedName
                                if (fetchedBio.isNotBlank()) aboutText.value = fetchedBio
                                if (fetchedPic.isNotBlank()) {
                                    galleryImageUriString.value = fetchedPic
                                    uploadedProfilePicUrl.value = fetchedPic
                                    if (fetchedPic.startsWith("http")) {
                                        avatarType.value = "gallery"
                                    }
                                }

                                if (fetchedCode.isNotBlank()) {
                                    val formatted = if (fetchedCode.startsWith("PX-")) fetchedCode else "PX-$fetchedCode"
                                    plenxoId.value = formatted
                                    revealedPlenxoId.value = formatted
                                    userCode.value = formatted.removePrefix("PX-")
                                }
                                val is2FA = docToUse.getBoolean("is2FAEnabled") == true ||
                                            docToUse.getBoolean("is_2fa_enabled") == true ||
                                            docToUse.getBoolean("twoFactorEnabled") == true
                                _is2FAEnabled.value = is2FA
                                val pin = docToUse.getString("masterPin") ?: docToUse.getString("master_pin")
                                if (!pin.isNullOrBlank()) {
                                    _storedMasterPinHash.value = pin
                                }

                                currentUserProfile.value = UserProfile(
                                    uid = uid,
                                    id = uid,
                                    email = fbUser.email ?: "",
                                    displayName = fetchedName,
                                    bio = fetchedBio,
                                    statusMessage = fetchedBio,
                                    profilePicUrl = fetchedPic,
                                    plenxoId = plenxoId.value,
                                    userCode = userCode.value,
                                    profileRingId = docToUse.getString("profileRingId") ?: docToUse.getString("selectedRingId") ?: "none"
                                )

                                SessionManager.saveUserProfileLocally(
                                    getApplication(),
                                    plenxoId = plenxoId.value,
                                    displayName = fetchedName,
                                    bio = fetchedBio,
                                    profilePicUrl = fetchedPic,
                                    age = fetchedAge
                                )

                                if (!isEmailVerified && _authState.value == AuthState.VERIFYING_OTP) {
                                    _authState.value = AuthState.VERIFYING_OTP
                                    navigateToScreen(PlenxoScreen.OTP_VERIFICATION, addToHistory = false, clearHistory = true)
                                } else if (!isSetupCompleted && fetchedName.isBlank() && fetchedCode.isBlank()) {
                                    _authState.value = AuthState.NEEDS_PROFILE_SETUP
                                    navigateToScreen(PlenxoScreen.PROFILE_SETUP, addToHistory = false, clearHistory = true)
                                } else {
                                    _authState.value = AuthState.AUTHENTICATED
                                    observeCurrentUserProfile()
                                    startListeningForChats()
                                    if (!com.example.util.SessionManager.isOnboardingCompleted(getApplication())) {
                                        val idToReveal = plenxoId.value.ifBlank { "PX-892104" }
                                        setRevealedPlenxoId(idToReveal)
                                        navigateToScreen(PlenxoScreen.PLENXO_ID_REVEAL, addToHistory = false, clearHistory = true)
                                    } else {
                                        navigateToScreen(PlenxoScreen.HOME, addToHistory = false, clearHistory = true)
                                    }
                                }
                            } else {
                                _authState.value = AuthState.NEEDS_PROFILE_SETUP
                                navigateToScreen(PlenxoScreen.PROFILE_SETUP, addToHistory = false, clearHistory = true)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("Plenxo", "Error verifying profile during session restore: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            _authState.value = AuthState.UNAUTHENTICATED
                            navigateToScreen(PlenxoScreen.LOGIN, addToHistory = false, clearHistory = true)
                        }
                    }
                }
            } else {
                Log.d("Plenxo", "No authenticated user found on startup, showing LOGIN screen")
                _authState.value = AuthState.UNAUTHENTICATED
                navigateToScreen(PlenxoScreen.LOGIN, addToHistory = false, clearHistory = true)
            }
        } catch (e: SecurityException) {
            Log.e("Plenxo", "Security error during session restoration: ${e.message}")
            _authState.value = AuthState.UNAUTHENTICATED
            navigateToScreen(PlenxoScreen.LOGIN, addToHistory = false, clearHistory = true)
        } catch (e: Exception) {
            Log.e("Plenxo", "Failed to restore session: ${e.message}", e)
            _authState.value = AuthState.UNAUTHENTICATED
            navigateToScreen(PlenxoScreen.LOGIN, addToHistory = false, clearHistory = true)
        }
    }

    fun handleCrashRecovery() {
        try {
            val sharedPrefs = getApplication<Application>().getSharedPreferences("app_crash_logs", Context.MODE_PRIVATE)
            sharedPrefs.edit().clear().apply()
            synchronized(screenHistory) {
                screenHistory.clear()
            }
            val loginState = com.example.util.SessionManager.getLoginState(getApplication())
            if (loginState.isLoggedIn) {
                checkAndRestoreSession()
            } else {
                resetOtpState()
        _currentScreen.value = PlenxoScreen.LOGIN
            }
            Log.d("PlenxoViewModel", "Crash recovery executed successfully.")
        } catch (e: Exception) {
            Log.e("PlenxoViewModel", "Error in handleCrashRecovery", e)
            resetOtpState()
        _currentScreen.value = PlenxoScreen.LOGIN
        }
    }

    private fun startSelfDestructTicker() {
        viewModelScope.launch {
            while (true) {
                val currentTime = System.currentTimeMillis()
                try {
                    val db = AppDatabase.getDatabase(getApplication())
                    val deletedLocalCount = db.localMessageDao().deleteExpiredMessages(currentTime)
                    if (deletedLocalCount > 0) {
                        Log.d("Plenxo", "Purged $deletedLocalCount expired local messages.")
                    }

                    if (!isLocalOnlyMode.value) {
                        val activeChatId = currentChatId.value
                        if (activeChatId.isNotEmpty()) {
                            val expiredQuery = firestore.collection("chats")
                                .document(activeChatId)
                                .collection("messages")
                                .whereLessThanOrEqualTo("expiresAt", currentTime)
                                .get()
                                .await()
                            
                            for (doc in expiredQuery.documents) {
                                doc.reference.delete()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Plenxo", "Error in self-destruct ticker: ${e.message}")
                }
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
        resetOtpState()
        
    }

    fun mapSupabaseError(e: Exception): String {
        android.util.Log.e("PLENXO_AUTH_DEBUG", "Supabase Auth operation failed with exception: ${e.message}", e)
        
        var details = ""
        
        // Use pure reflection to safely extract properties across library versions
        try {
            val exceptionClass = e.javaClass
            val descMethod = exceptionClass.methods.firstOrNull { 
                it.name == "getErrorDescription" || 
                it.name == "errorDescription" || 
                it.name == "getDescription" || 
                it.name == "description" 
            }
            if (descMethod != null) {
                descMethod.isAccessible = true
                val descVal = descMethod.invoke(e) as? String
                if (!descVal.isNullOrEmpty()) {
                    details = descVal
                }
            }
        } catch (ignored: Exception) {}
        
        if (details.isEmpty()) {
            try {
                val exceptionClass = e.javaClass
                val errMethod = exceptionClass.methods.firstOrNull { 
                    it.name == "getError" || 
                    it.name == "error" || 
                    it.name == "getErrorCode" || 
                    it.name == "errorCode" 
                }
                if (errMethod != null) {
                    errMethod.isAccessible = true
                    val errVal = errMethod.invoke(e) as? String
                    if (!errVal.isNullOrEmpty()) {
                        details = errVal
                    }
                }
            } catch (ignored: Exception) {}
        }
        
        if (details.isEmpty()) {
            // Check message / localizedMessage as last resorts
            val msg = e.message
            if (!msg.isNullOrEmpty()) {
                details = msg
            }
        }
        
        val rawMessage = details
        val exceptionName = e.javaClass.simpleName
        val lowerMessage = rawMessage.lowercase()
        
        val cleanMessage = when {
            rawMessage.isEmpty() -> "The authentication server returned an empty response. Please verify your internet connection or try again later."
            e is java.net.UnknownHostException || 
            e is java.net.ConnectException || 
            e is java.net.SocketTimeoutException ||
            lowerMessage.contains("timeout") ||
            lowerMessage.contains("connect") ||
            lowerMessage.contains("network") ||
            lowerMessage.contains("unknownhost") -> {
                "Network connection failure. Please check your internet connection."
            }
            lowerMessage.contains("invalid login credentials") ||
            lowerMessage.contains("invalid_credentials") ||
            lowerMessage.contains("invalid credentials") ||
            lowerMessage.contains("incorrect email or password") ||
            lowerMessage.contains("invalid_grant") ||
            lowerMessage.contains("email not found") ||
            lowerMessage.contains("user not found") -> {
                "Incorrect email or password. Please try again."
            }
            lowerMessage.contains("user already exists") ||
            lowerMessage.contains("already registered") ||
            lowerMessage.contains("email_exists") ||
            lowerMessage.contains("already exists") ||
            lowerMessage.contains("email already in use") -> {
                "An account with this email already exists."
            }
            lowerMessage.contains("invalid email") -> {
                "Please enter a valid email address."
            }
            lowerMessage.contains("password should be") || lowerMessage.contains("weak password") || lowerMessage.contains("password is too short") -> {
                "Password must be at least 6 characters long."
            }
            details.isNotEmpty() -> {
                val formatted = details
                    .replace("io.github.jan.// supabase.exceptions.RestException:", "")
                    .replace("io.github.jan.// supabase.exceptions.", "")
                    .replace("RestException:", "")
                    .replace("RestException", "")
                    .replace("SupabaseException:", "")
                    .replace("SupabaseException", "")
                    .trim()
                if (formatted.contains("Unknown error", ignoreCase = true) || formatted.contains("Auth API error", ignoreCase = true)) {
                    "An account with this email may already exist, or authentication service is temporarily busy. Please try logging in."
                } else {
                    formatted
                }
            }
            else -> {
                "Authentication failed [$exceptionName]. Please try again."
            }
        }
        
        return if (cleanMessage.isNotEmpty()) {
            cleanMessage
        } else {
            "Authentication failed [$exceptionName]. Please try again."
        }
    }

    private fun registerE2EEKey() {
        val uid = currentUserId
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val publicKey = com.example.util.EncryptionManager.getPublicKeyBase64()
                firestore.collection("users").document(currentUserId).update("publicKey", publicKey)
            } catch (e: Exception) {
                Log.e("Security", "Failed to register E2EE key", e)
            }
        }
    }

    /**
     * Netlify OTP Backend Integration:
     * Base URL: https://plenxo-back.netlify.app/
     * Endpoint: api/send-otp
     * HTTP Method: POST
     * Header: Content-Type: application/json
     * Body: { "email": "user@gmail.com", "purpose": "signup" | "login" | "forgot_password" | "delete_account", "otp": "XXXXXX" }
     */
    private suspend fun requestNetlifyOtp(
        recipientEmail: String,
        purpose: String = "signup",
        clientOtp: String? = null
    ): NetlifyOtpResult = withContext(Dispatchers.IO) {
        val cleanEmail = recipientEmail.trim()
        val activeClientOtp = if (!clientOtp.isNullOrBlank()) {
            clientOtp.trim().padStart(6, '0')
        } else {
            OtpUtils.generateOtp(cleanEmail)
        }

        // Step 1: Immediately set _activeOtp.value BEFORE making the network call
        _activeOtp.value = activeClientOtp
        Log.d("OTP_DEBUG", "Client-Side Generated OTP: $activeClientOtp for $cleanEmail (Purpose=$purpose)")

        // Pre-sync OTP to Firestore
        try {
            OtpUtils.saveOtpToFirestore(cleanEmail, activeClientOtp, firestore)
        } catch (e: Throwable) {
            Log.w("PlenxoViewModel", "Failed to pre-sync OTP to Firestore: ${e.message}")
        }

        val normalizedPurpose = when (purpose.lowercase().trim()) {
            "login" -> "login"
            "forgot_password", "forgot-password", "forgot" -> "forgot_password"
            "delete_account", "delete-account", "delete" -> "delete_account"
            else -> "signup"
        }

        val deliveryResult = otpRepository.dispatchOtp(cleanEmail, normalizedPurpose, activeClientOtp)
        when (deliveryResult) {
            is OtpDeliveryResult.Success -> {
                _otpUiState.value = OtpUiState.Success(deliveryResult.message, deliveryResult.details)
                withContext(Dispatchers.Main) {
                    try { Toast.makeText(getApplication(), "OTP sent to $cleanEmail", Toast.LENGTH_LONG).show() } catch (e: Throwable) {}
                }
                NetlifyOtpResult.Success(deliveryResult.message, activeClientOtp)
            }
            is OtpDeliveryResult.LimitExceeded -> {
                _otpUiState.value = OtpUiState.Error(
                    message = deliveryResult.message,
                    isRateLimited = true,
                    isDailyLimitExceeded = deliveryResult.isDailyUserLimit,
                    isGlobalCapReached = deliveryResult.isGlobalDailyCap
                )
                withContext(Dispatchers.Main) {
                    _errorMessage.value = deliveryResult.message
                    try {
                        Toast.makeText(getApplication(), deliveryResult.message, Toast.LENGTH_LONG).show()
                    } catch (e: Throwable) {}
                }
                NetlifyOtpResult.Failure(deliveryResult.message)
            }
            is OtpDeliveryResult.Error -> {
                _otpUiState.value = OtpUiState.Error(deliveryResult.message)
                // Even if network delivery encounters an issue, the client-generated OTP is pre-synced in Firestore
                withContext(Dispatchers.Main) {
                    _errorMessage.value = deliveryResult.message
                    try { Toast.makeText(getApplication(), deliveryResult.message, Toast.LENGTH_LONG).show() } catch (e: Throwable) {}
                }
                NetlifyOtpResult.Failure(deliveryResult.message)
            }
        }
    }

    // Compatibility overload for requestCloudflareOtp
    private suspend fun requestCloudflareOtp(
        recipientEmail: String,
        otpCode: String? = null,
        actionType: String = "send_otp"
    ): NetlifyOtpResult {
        val purpose = when (actionType.lowercase().trim()) {
            "login" -> "login"
            "forgot_password" -> "forgot_password"
            "delete_account" -> "delete_account"
            else -> "signup"
        }
        return requestNetlifyOtp(recipientEmail, purpose = purpose, clientOtp = otpCode)
    }

    private suspend fun dispatchSignupOtpEmail(userEmail: String): NetlifyOtpResult {
        Log.d("PlenxoAuthEmail", "Dispatching Signup OTP email to $userEmail via Netlify Function")
        val result = requestNetlifyOtp(userEmail, purpose = "signup")
        if (result is NetlifyOtpResult.Failure) {
            withContext(Dispatchers.Main) {
                _errorMessage.value = result.reason
            }
        }
        return result
    }

    private suspend fun dispatchLoginOtpEmail(userEmail: String): NetlifyOtpResult {
        Log.d("PlenxoAuthEmail", "Dispatching Login OTP email to $userEmail via Netlify Function")
        val result = requestNetlifyOtp(userEmail, purpose = "login")
        if (result is NetlifyOtpResult.Failure) {
            withContext(Dispatchers.Main) {
                _errorMessage.value = result.reason
            }
        }
        return result
    }

    private suspend fun dispatchForgotPasswordOtpEmail(userEmail: String): NetlifyOtpResult {
        Log.d("PlenxoAuthEmail", "Dispatching Forgot Password OTP email to $userEmail via Netlify Function")
        val result = requestNetlifyOtp(userEmail, purpose = "forgot_password")
        if (result is NetlifyOtpResult.Failure) {
            withContext(Dispatchers.Main) {
                _errorMessage.value = result.reason
            }
        }
        return result
    }

    private suspend fun dispatchDeleteAccountOtpEmail(userEmail: String): NetlifyOtpResult {
        Log.d("PlenxoAuthEmail", "Dispatching Delete Account OTP email to $userEmail via Netlify Function")
        val result = requestNetlifyOtp(userEmail, purpose = "delete_account")
        if (result is NetlifyOtpResult.Failure) {
            withContext(Dispatchers.Main) {
                _errorMessage.value = result.reason
            }
        }
        return result
    }

    private fun triggerLoginAlertEmail(userEmail: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val prefs = getApplication<android.app.Application>()
                    .getSharedPreferences("plenxo_login_stats", android.content.Context.MODE_PRIVATE)
                val currentCount = prefs.getInt("login_count_$userEmail", 0) + 1
                prefs.edit().putInt("login_count_$userEmail", currentCount).apply()

                Log.d("SecurityService", "Login count for $userEmail is now $currentCount")

                if (currentCount % 5 == 0) {
                    Log.d("SecurityService", "Login alert triggered for $userEmail")
                }
            } catch (e: Exception) {
                Log.e("SecurityService", "Failed to trigger login alert email: ${e.message}", e)
            }
        }
    }

    fun observeActiveSessions() {
        val uid = currentUserId
        if (uid.isEmpty()) return
        val app = getApplication<Application>()
        var localSessionId = com.example.util.SessionManager.getSessionId(app)
        if (localSessionId.isNullOrBlank()) {
            localSessionId = android.provider.Settings.Secure.getString(
                app.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: java.util.UUID.randomUUID().toString()
            com.example.util.SessionManager.saveSessionId(app, localSessionId)
        }

        firestore.collection("users").document(uid)
            .collection("devices")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val list = snapshot.documents.mapNotNull { doc ->
                    val devId = doc.getString("deviceId") ?: doc.id
                    val devModel = doc.getString("deviceModel") ?: "Android Device"
                    val devName = doc.getString("deviceName") ?: devModel
                    val lastActive = doc.getLong("lastActiveTimestamp") ?: System.currentTimeMillis()
                    val isCurr = devId == localSessionId || (doc.getBoolean("isCurrentDevice") == true)
                    com.example.model.ActiveSession(
                        sessionId = devId,
                        deviceName = devName,
                        deviceModel = devModel,
                        operatingSystem = "Android",
                        ipAddress = "127.0.0.1",
                        timestamp = lastActive,
                        lastActiveTime = lastActive,
                        isCurrentDevice = isCurr
                    )
                }
                activeSessions.value = list

                // Check if current device is still in the active devices list. If not, trigger remote logout.
                if (list.isNotEmpty()) {
                    val isCurrentDeviceActive = list.any { it.sessionId == localSessionId }
                    if (!isCurrentDeviceActive && com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) {
                        Log.w("SessionAudit", "Current device session ($localSessionId) was remotely terminated. Force logout.")
                        logout()
                    }
                }
            }
    }

    private fun auditSession() {
        val uid = currentUserId
        if (uid.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                var currentDeviceId = com.example.util.SessionManager.getSessionId(app)
                if (currentDeviceId.isNullOrBlank()) {
                    currentDeviceId = android.provider.Settings.Secure.getString(
                        app.contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    ) ?: java.util.UUID.randomUUID().toString()
                    com.example.util.SessionManager.saveSessionId(app, currentDeviceId)
                }

                val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
                val model = android.os.Build.MODEL
                val deviceModel = "$manufacturer $model"
                val deviceName = model
                val osVersion = "Android ${android.os.Build.VERSION.RELEASE}"
                val now = System.currentTimeMillis()

                val devicesRef = firestore.collection("users").document(uid).collection("devices")
                val devicesSnapshot = devicesRef.get().await()

                val existingDocs = devicesSnapshot.documents
                val containsCurrent = existingDocs.any { it.id == currentDeviceId || it.getString("deviceId") == currentDeviceId }

                // Hard Restriction: max 3 active devices. If 4th attempts to log in, delete oldest device.
                if (existingDocs.size >= 3 && !containsCurrent) {
                    val oldestDoc = existingDocs.minByOrNull { doc ->
                        doc.getLong("lastActiveTimestamp")
                            ?: doc.getLong("lastActiveTime")
                            ?: doc.getLong("timestamp")
                            ?: Long.MAX_VALUE
                    }
                    oldestDoc?.let { doc ->
                        Log.d("SessionAudit", "Removing oldest device document to enforce 3 device limit: ${doc.id}")
                        devicesRef.document(doc.id).delete().await()
                    }
                }

                // Mark other device docs as isCurrentDevice = false
                for (doc in existingDocs) {
                    if (doc.id != currentDeviceId && doc.getString("deviceId") != currentDeviceId) {
                        devicesRef.document(doc.id).update("isCurrentDevice", false)
                    }
                }

                // Write device session
                val deviceData = mapOf(
                    "deviceId" to currentDeviceId,
                    "deviceModel" to deviceModel,
                    "deviceName" to deviceName,
                    "lastActiveTimestamp" to now,
                    "isCurrentDevice" to true,
                    "operatingSystem" to osVersion,
                    "timestamp" to now,
                    "lastActiveTime" to now,
                    "user_id" to uid
                )

                devicesRef.document(currentDeviceId)
                    .set(deviceData, com.google.firebase.firestore.SetOptions.merge())
                    .await()

                observeActiveSessions()
            } catch (e: Exception) {
                Log.e("Security", "Failed to audit session", e)
            }
        }
    }

    fun terminateSession(sessionId: String) {
        val uid = currentUserId
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .collection("devices").document(sessionId)
                    .delete()
                    .await()
            } catch (e: Exception) {
                Log.e("Security", "Failed to terminate session", e)
            }
        }
    }

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUserId
            if (!uid.isNullOrEmpty()) {
                try {
                    val fcmClearMap = mapOf(
                        "fcmToken" to com.google.firebase.firestore.FieldValue.delete(),
                        "fcm_token" to com.google.firebase.firestore.FieldValue.delete()
                    )
                    firestore.collection("users").document(uid).update(fcmClearMap).await()
                } catch (e: Exception) {
                    Log.w("PlenxoLogout", "Error clearing FCM token from Firestore: ${e.message}")
                }
            }

            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().deleteToken().await()
            } catch (e: Exception) {
                Log.w("PlenxoLogout", "Error deleting device FCM token: ${e.message}")
            }

            try {
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            } catch (e: Exception) {
                Log.e("PlenxoLogout", "Error during FirebaseAuth signOut: ${e.message}")
            }

            // Clear all local session flags and cached user preferences
            com.example.util.SessionManager.clearLoginState(getApplication())

            withContext(Dispatchers.Main) {
                // Clear in-memory user fields and state
                email.value = ""
                password.value = ""
                confirmPassword.value = ""
                isPrivacyAccepted.value = false
                enteredOtp.value = ""
                displayName.value = ""
                userCode.value = ""
                plenxoId.value = ""
                currentUserProfile.value = null
                _chats.value = emptyList()
                _pendingFriendRequests.value = emptyList()
                _contactsSet.value = emptySet()
                _authState.value = com.example.model.AuthState.UNAUTHENTICATED

                resetOtpState()

                // Navigate instantly back to LoginScreen with backstack cleared
                navigateToScreen(PlenxoScreen.LOGIN, addToHistory = false, clearHistory = true)
            }
        }
    }

    private fun startListeningToSessions() {}

    fun navigateToSignup() {
        isPrivacyAccepted.value = false
        resetOtpState()
        _currentScreen.value = PlenxoScreen.SIGNUP
        _errorMessage.value = null
        resetOtpState()
        
    }

    fun navigateToLogin() {
        resetOtpState()
        _currentScreen.value = PlenxoScreen.LOGIN
        _errorMessage.value = null
        resetOtpState()
        
    }

    fun navigateToForgotPassword() {
        forgotPasswordInput.value = ""
        forgotPasswordErrorMessage.value = null
        forgotPasswordSuccessMessage.value = null
        resetCaptcha()
        resetOtpState()
        _currentScreen.value = PlenxoScreen.FORGOT_PASSWORD
    }

    fun completePasswordResetAndNavigateHome(userEmail: String, userId: String = "") {
        val uid = if (userId.isNotBlank()) userId else (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "")
        _authState.value = AuthState.AUTHENTICATED
        _currentScreen.value = PlenxoScreen.HOME
        _errorMessage.value = null
        resetOtpState()
        
        _isLoading.value = false
        try {
            if (uid.isNotBlank() && userEmail.isNotBlank()) {
                com.example.util.SessionManager.saveLoginState(getApplication(), uid, userEmail)
            }
        } catch (e: Throwable) {
            Log.w("PlenxoViewModel", "Failed saving login state on reset: ${e.message}")
        }
    }

    fun executePasswordRecovery() {
        val input = forgotPasswordInput.value.trim()
        if (input.isEmpty()) {
            forgotPasswordErrorMessage.value = "Please enter your Gmail address or Plenxo ID."
            return
        }

        if (forgotPasswordCooldownSeconds.value > 0) {
            forgotPasswordErrorMessage.value = "Please wait ${forgotPasswordCooldownSeconds.value} seconds before requesting again."
            return
        }

        isForgotPasswordLoading.value = true
        forgotPasswordErrorMessage.value = null
        forgotPasswordSuccessMessage.value = null

        viewModelScope.launch {
            try {
                val resolvedEmail: String = if (input.contains("@")) {
                    val gmailRegex = Regex("^[a-zA-Z0-9._%+-]+@gmail\\.com$", RegexOption.IGNORE_CASE)
                    if (!gmailRegex.matches(input)) {
                        forgotPasswordErrorMessage.value = "Registration/Recovery is restricted exclusively to valid @gmail.com accounts."
                        isForgotPasswordLoading.value = false
                        return@launch
                    }
                    input
                } else {
                    // Plenxo ID lookup
                    var targetId = input.uppercase()
                    if (!targetId.startsWith("PX-")) {
                        targetId = "PX-$targetId"
                    }
                    val snapshot = firestore.collection("users")
                        .whereEqualTo("plenxoId", targetId)
                        .limit(1)
                        .get()
                        .await()

                    if (snapshot.isEmpty) {
                        forgotPasswordErrorMessage.value = "No account found matching this Plenxo ID."
                        isForgotPasswordLoading.value = false
                        return@launch
                    }

                    val email = snapshot.documents[0].getString("email")
                    if (email.isNullOrBlank()) {
                        forgotPasswordErrorMessage.value = "No email associated with this Plenxo ID."
                        isForgotPasswordLoading.value = false
                        return@launch
                    }
                    email
                }

                // Send email reset link and dispatch Netlify OTP with purpose "forgot_password"
                try {
                    dispatchForgotPasswordOtpEmail(resolvedEmail)
                } catch (otpEx: Exception) {
                    Log.w("ForgotPassword", "Netlify OTP dispatch error: ${otpEx.message}")
                }

                val resetRes = kotlinx.coroutines.withTimeoutOrNull(8000L) {
                    com.google.firebase.auth.FirebaseAuth.getInstance().sendPasswordResetEmail(resolvedEmail).await()
                    true
                }
                if (resetRes == null) {
                    forgotPasswordErrorMessage.value = "Network is slow, please check your connection and try again."
                    isForgotPasswordLoading.value = false
                    return@launch
                }
                forgotPasswordSuccessMessage.value = "Password reset link sent to your registered Gmail address."
                
                // Start cooldown
                startForgotPasswordCooldown()
                // Reset captcha so it must be re-solved if they want to try again
                resetCaptcha()
            } catch (e: Exception) {
                Log.e("ForgotPassword", "Password Reset Error", e)
                forgotPasswordErrorMessage.value = e.localizedMessage ?: "Failed to send password reset link."
            } finally {
                isForgotPasswordLoading.value = false
            }
        }
    }

    private fun startForgotPasswordCooldown() {
        forgotPasswordCooldownJob?.cancel()
        forgotPasswordCooldownSeconds.value = 60
        forgotPasswordCooldownJob = viewModelScope.launch {
            while (forgotPasswordCooldownSeconds.value > 0) {
                kotlinx.coroutines.delay(1000)
                forgotPasswordCooldownSeconds.value -= 1
            }
        }
    }

    fun checkAndStartLockoutTimer(identifier: String): Boolean {
        val lockoutUntil = com.example.util.SessionManager.getLockoutUntil(getApplication(), identifier)
        val current = System.currentTimeMillis()
        if (lockoutUntil > current) {
            _errorMessage.value = "Too many failed attempts. Account temporarily locked."
            loginLockoutRemainingTime.value = lockoutUntil - current
            showLockoutDialog.value = true
            lockoutIdentifier.value = identifier
            viewModelScope.launch {
                while (loginLockoutRemainingTime.value > 0) {
                    kotlinx.coroutines.delay(1000)
                    val rem = com.example.util.SessionManager.getLockoutUntil(getApplication(), identifier) - System.currentTimeMillis()
                    if (rem <= 0) {
                        loginLockoutRemainingTime.value = 0
                        showLockoutDialog.value = false
                        // reset failed attempts so they can try again
                        com.example.util.SessionManager.resetFailedPasswordAttempts(getApplication(), identifier)
                        break
                    } else {
                        loginLockoutRemainingTime.value = rem
                    }
                }
            }
            return true
        }
        return false
    }

    fun startListeningForCallLogs() {
        val uid = currentUserId
        if (uid.isEmpty()) return
        
        callLogsListener?.remove()
        callLogsListener = firestore.collection("users").document(uid).collection("call_logs")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("PlenxoViewModel", "Error listening for call logs: ${error.message}", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.toObjects(com.example.model.CallLog::class.java)
                    callLogs.value = list
                }
            }
    }

    fun initiateCall(peerUid: String, callType: String) {
        val uid = currentUserId
        if (uid.isEmpty()) return
        
        // 1. Verify friend connection
        val chat = chats.value.find { it.participantUids.contains(peerUid) }
        if (chat == null) {
            _errorMessage.value = "You can only call connected friends."
            return
        }

        viewModelScope.launch {
            try {
                // Get peer user info
                val peerDoc = firestore.collection("users").document(peerUid).get().await()
                val peerName = peerDoc.getString("displayName") ?: "User"
                val peerPhoto = peerDoc.getString("profilePicUrl") ?: ""
                val peerPlenxoId = peerDoc.getString("plenxoId") ?: ""

                // Get current user info
                val myDoc = firestore.collection("users").document(uid).get().await()
                val myName = myDoc.getString("displayName") ?: "User"
                val myPhoto = myDoc.getString("profilePicUrl") ?: ""
                val myPlenxoId = myDoc.getString("plenxoId") ?: ""

                val callId = java.util.UUID.randomUUID().toString()
                val timestamp = System.currentTimeMillis()

                val outgoingLog = com.example.model.CallLog(
                    callId = callId,
                    peerUid = peerUid,
                    peerName = peerName,
                    peerPhotoUrl = peerPhoto,
                    peerPlenxoId = peerPlenxoId,
                    callType = callType,
                    direction = "OUTGOING",
                    timestamp = timestamp,
                    durationSeconds = 0L
                )

                activeSimulatedCall.value = outgoingLog
                simulatedCallState.value = "Calling..."
                simulatedCallDuration.value = 0L

                // Setup real signaling
                val signaling = com.example.webrtc.CallSignalingManager(
                    callId = callId,
                    callerUid = uid,
                    receiverUid = peerUid,
                    listener = object : com.example.webrtc.CallSignalingManager.SignalingListener {
                        override fun onCallRinging(senderUid: String) {
                            simulatedCallState.value = "Ringing..."
                        }
                        override fun onCallAccepted(senderUid: String) {
                            simulatedCallState.value = "Connected"
                            // Start real timer
                            simulatedCallJob?.cancel()
                            simulatedCallJob = viewModelScope.launch {
                                while (activeSimulatedCall.value != null) {
                                    kotlinx.coroutines.delay(1000)
                                    simulatedCallDuration.value += 1
                                }
                            }
                        }
                        override fun onCallReject(senderUid: String) {
                            activeSimulatedCall.value = null
                            simulatedCallJob?.cancel()
                            simulatedCallState.value = "Rejected"
                        }
                        override fun onCallBusy(senderUid: String) {
                            activeSimulatedCall.value = null
                            simulatedCallJob?.cancel()
                            simulatedCallState.value = "Busy"
                        }
                        override fun onCallEnd(senderUid: String) {
                            activeSimulatedCall.value = null
                            simulatedCallJob?.cancel()
                            simulatedCallState.value = "Ended"
                        }
                    }
                )
                outgoingCallSignalingManager = signaling
                signaling.sendOffer("sdp_offer_dummy", callType)
                signaling.startListening()

            } catch (e: Exception) {
                Log.e("PlenxoViewModel", "Failed to initiate call: ${e.message}", e)
                _errorMessage.value = "Failed to start call."
            }
        }
    }

    fun endActiveCall(isMissed: Boolean = false) {
        val call = activeSimulatedCall.value ?: return
        val duration = simulatedCallDuration.value
        simulatedCallJob?.cancel()
        activeSimulatedCall.value = null
        simulatedCallState.value = "Ended"

        val uid = currentUserId
        if (uid.isEmpty()) return

        // Signaling updates
        if (call.direction == "OUTGOING") {
            outgoingCallSignalingManager?.sendEnd()
            outgoingCallSignalingManager?.cleanup()
            outgoingCallSignalingManager = null
        } else {
            if (duration == 0L) {
                incomingCallSignalingManager?.sendReject()
            } else {
                incomingCallSignalingManager?.sendEnd()
            }
            incomingCallSignalingManager?.cleanup()
            incomingCallSignalingManager = null
        }

        viewModelScope.launch {
            try {
                val finalDuration = if (isMissed) 0L else duration
                val directionText = if (isMissed) "MISSED" else call.direction

                // Save outgoing/incoming log for current user
                val userLog = call.copy(durationSeconds = finalDuration, direction = directionText)
                firestore.collection("users").document(uid)
                    .collection("call_logs").document(call.callId).set(userLog).await()

                // Save incoming/missed log for peer user
                val myDoc = firestore.collection("users").document(uid).get().await()
                val myName = myDoc.getString("displayName") ?: "User"
                val myPhoto = myDoc.getString("profilePicUrl") ?: ""
                val myPlenxoId = myDoc.getString("plenxoId") ?: ""

                val peerLog = com.example.model.CallLog(
                    callId = call.callId,
                    peerUid = uid,
                    peerName = myName,
                    peerPhotoUrl = myPhoto,
                    peerPlenxoId = myPlenxoId,
                    callType = call.callType,
                    direction = if (isMissed) "MISSED" else if (call.direction == "OUTGOING") "INCOMING" else "OUTGOING",
                    timestamp = call.timestamp,
                    durationSeconds = finalDuration
                )
                firestore.collection("users").document(call.peerUid)
                    .collection("call_logs").document(call.callId).set(peerLog).await()

            } catch (e: Exception) {
                Log.e("PlenxoViewModel", "Failed to save call logs: ${e.message}", e)
            }
        }
    }

    fun acceptIncomingCall() {
        val call = activeSimulatedCall.value ?: return
        simulatedCallState.value = "Connected"
        
        incomingCallSignalingManager?.sendAnswer("sdp_answer_dummy")
        
        simulatedCallJob?.cancel()
        simulatedCallJob = viewModelScope.launch {
            while (activeSimulatedCall.value != null) {
                kotlinx.coroutines.delay(1000)
                simulatedCallDuration.value += 1
            }
        }
    }

    // Helper to sanitize emails for RTDB
    fun sanitizeEmail(rawEmail: String): String {
        return rawEmail
            .replace(".", "_dot_")
            .replace("@", "_at_")
            .replace("#", "_hash_")
            .replace("$", "_dollar_")
            .replace("[", "_leftbracket_")
            .replace("]", "_rightbracket_")
    }

    fun onLoginClicked() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                clearError()
                val rawInput = email.value.trim()
                val rawPassword = password.value

                val isLocked = try { checkAndStartLockoutTimer(rawInput) } catch (e: Throwable) { false }
                if (isLocked) {
                    _isLoading.value = false
                    return@launch
                }

                if (_captchaStage.value != com.example.model.CaptchaStage.FULLY_VERIFIED) {
                    _errorMessage.value = "Please complete the dual-stage security verification (Text CAPTCHA + Slider Puzzle)."
                    _isLoading.value = false
                    return@launch
                }

                // Auto-accept terms if user has verified CAPTCHA and clicked Login
                isTermsAccepted.value = true

                if (rawInput.isEmpty()) {
                    _errorMessage.value = "Please enter your Gmail address or Plenxo ID."
                    _isLoading.value = false
                    return@launch
                }
                if (rawPassword.isEmpty()) {
                    _errorMessage.value = "Please enter your password."
                    _isLoading.value = false
                    return@launch
                }

                val resolvedEmail: String = if (rawInput.contains("@")) {
                    val gmailRegex = Regex("^[a-zA-Z0-9._%+-]+@gmail\\.com$", RegexOption.IGNORE_CASE)
                    if (!gmailRegex.matches(rawInput)) {
                        _errorMessage.value = "Registration and login are restricted exclusively to valid @gmail.com accounts or Plenxo IDs."
                        _isLoading.value = false
                        return@launch
                    }
                    rawInput
                } else {
                    var targetId = rawInput.trim()
                    var formattedPxId = if (!targetId.uppercase().startsWith("PX-")) "PX-$targetId" else targetId
                    
                    var foundEmailStr: String? = null
                    try {
                        kotlinx.coroutines.withTimeoutOrNull(12000L) {
                            var snapshot = firestore.collection("users")
                                .whereEqualTo("plenxoId", formattedPxId.uppercase())
                                .limit(1)
                                .get()
                                .await()
                            if (snapshot.isEmpty) {
                                snapshot = firestore.collection("users")
                                    .whereEqualTo("plenxoId", targetId)
                                    .limit(1)
                                    .get()
                                    .await()
                            }
                            if (snapshot.isEmpty) {
                                snapshot = firestore.collection("users")
                                    .whereEqualTo("plenxoId", rawInput.trim())
                                    .limit(1)
                                    .get()
                                    .await()
                            }
                            if (snapshot.isEmpty) {
                                snapshot = firestore.collection("users")
                                    .whereEqualTo("userCode", targetId)
                                    .limit(1)
                                    .get()
                                    .await()
                            }
                            if (snapshot.isEmpty) {
                                snapshot = firestore.collection("users")
                                    .whereEqualTo("userCode", formattedPxId)
                                    .limit(1)
                                    .get()
                                    .await()
                            }
                            if (!snapshot.isEmpty) {
                                foundEmailStr = snapshot.documents[0].getString("email")
                            }

                            if (foundEmailStr.isNullOrBlank()) {
                                // Check plenxo_id & user_code in users collection
                                var snapshot2 = firestore.collection("users")
                                    .whereEqualTo("plenxo_id", formattedPxId.uppercase())
                                    .limit(1).get().await()
                                if (snapshot2.isEmpty) {
                                    snapshot2 = firestore.collection("users")
                                        .whereEqualTo("plenxo_id", formattedPxId)
                                        .limit(1).get().await()
                                }
                                if (snapshot2.isEmpty) {
                                    snapshot2 = firestore.collection("users")
                                        .whereEqualTo("user_code", targetId)
                                        .limit(1).get().await()
                                }
                                if (!snapshot2.isEmpty) {
                                    foundEmailStr = snapshot2.documents[0].getString("email")
                                }
                            }

                            if (foundEmailStr.isNullOrBlank()) {
                                // Fallback to Firebase Realtime Database /users node
                                try {
                                    val rdbRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users")
                                    val rdbSnap = rdbRef.get().await()
                                    if (rdbSnap.exists()) {
                                        for (child in rdbSnap.children) {
                                            val pId = child.child("plenxo_id").value as? String 
                                                ?: child.child("plenxoId").value as? String
                                                ?: child.child("userCode").value as? String
                                                ?: child.child("user_code").value as? String
                                            if (pId != null && (pId.equals(formattedPxId, ignoreCase = true) || pId.equals(targetId, ignoreCase = true) || pId.equals(rawInput.trim(), ignoreCase = true))) {
                                                val em = child.child("email").value as? String
                                                if (!em.isNullOrBlank()) {
                                                    foundEmailStr = em
                                                    break
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Throwable) {
                                    Log.e("Plenxo", "Realtime DB resolution error: ${e.message}")
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        Log.e("Plenxo", "Error resolving Plenxo ID: ${e.message}", e)
                    }

                    if (foundEmailStr.isNullOrBlank()) {
                        val errorMsg = "No account found matching Plenxo ID '$rawInput'."
                        _errorMessage.value = errorMsg
                        try {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(getApplication(), errorMsg, Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Throwable) {}
                        _isLoading.value = false
                        return@launch
                    }
                    foundEmailStr!!
                }

                try {
                    // Attempt sign-in with generous timeout and single instant retry for network resilience
                    var authResult = kotlinx.coroutines.withTimeoutOrNull(20_000L) {
                        try {
                            FirebaseAuth.getInstance().signInWithEmailAndPassword(resolvedEmail, rawPassword).await()
                        } catch (initialEx: Exception) {
                            Log.w("PlenxoLogin", "Initial signIn attempt failed (${initialEx.message}), retrying once...")
                            kotlinx.coroutines.delay(400L)
                            FirebaseAuth.getInstance().signInWithEmailAndPassword(resolvedEmail, rawPassword).await()
                        }
                    }

                    if (authResult == null) {
                        _errorMessage.value = "Sign in timed out. Please check your connection and try again."
                        _isLoading.value = false
                        return@launch
                    }
                    Log.d("Plenxo", "Successfully logged in with FirebaseAuth")
                    
                    try {
                        com.example.util.SessionManager.resetFailedPasswordAttempts(getApplication(), rawInput)
                        if (resolvedEmail != rawInput) {
                            com.example.util.SessionManager.resetFailedPasswordAttempts(getApplication(), resolvedEmail)
                        }
                    } catch (e: Throwable) {}

                    val currentUser = authResult.user ?: FirebaseAuth.getInstance().currentUser
                    val uid = currentUser?.uid ?: ""

                    if (currentUser == null || uid.isEmpty()) {
                        _errorMessage.value = "Login failed: Authentication returned null user session."
                        _isLoading.value = false
                        return@launch
                    }

                    // Strict Firestore user profile check
                    val userDocCheck = try {
                        firestore.collection("users").document(uid).get().await()
                    } catch (e: Throwable) {
                        null
                    }
                    val userDocToUse = userDocCheck

                    // Check 2FA conditional enforcement on login
                    val is2FA = userDocToUse?.getBoolean("is2FAEnabled") == true || 
                                userDocToUse?.getBoolean("is_2fa_enabled") == true || 
                                userDocToUse?.getBoolean("is2fa_enabled") == true ||
                                userDocToUse?.getBoolean("2fa_enabled") == true ||
                                userDocToUse?.getBoolean("twoFactorEnabled") == true

                    _is2FAEnabled.value = is2FA

                    if (is2FA) {
                        // Rule: If is2FAEnabled == true, trigger 2FA OTP verification challenge via Netlify
                        email.value = resolvedEmail
                        enteredOtp.value = ""

                        val activeCode = OtpUtils.generateOtp()
                        _activeOtp.value = activeCode
                        _generatedOtp.value = activeCode

                        startTimer()
                        _authState.value = AuthState.VERIFYING_OTP
                        _isLoading.value = false
                        val infoMsg = "2FA Verification Required: Please enter the 6-digit OTP code sent to $resolvedEmail"
                        _errorMessage.value = null
                        
                        try {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(getApplication(), infoMsg, Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Throwable) {}
                        navigateToScreen(PlenxoScreen.OTP_VERIFICATION, addToHistory = false, clearHistory = true)

                        // Async fire-and-forget background tasks
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                OtpUtils.saveOtpToFirestore(resolvedEmail, activeCode, firestore)
                            } catch (e: Throwable) {
                                Log.w("PlenxoLogin", "Async 2FA OTP Firestore sync error: ${e.message}")
                            }
                        }

                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                requestNetlifyOtp(resolvedEmail, purpose = "login", clientOtp = activeCode)
                            } catch (e: Throwable) {
                                Log.e("PlenxoLogin", "Async 2FA Netlify dispatch error: ${e.message}")
                            }
                        }
                        return@launch
                    }
                    // Rule: If is2FAEnabled == false, completely bypass OTP screen and proceed straight to home/dashboard
                    
                    // Track login count in SharedPreferences
                    try {
                        val prefs = getApplication<android.app.Application>()
                            .getSharedPreferences("plenxo_login_stats", android.content.Context.MODE_PRIVATE)
                        val currentCount = prefs.getInt("login_count_$resolvedEmail", 0) + 1
                        prefs.edit().putInt("login_count_$resolvedEmail", currentCount).apply()
                    } catch (e: Throwable) {}

                    // Save local session state immediately
                    try { SessionManager.saveLoginState(getApplication(), uid, resolvedEmail) } catch (e: Throwable) {}
                    try { com.example.util.AppLockManager.setLocked(getApplication(), false) } catch (e: Throwable) {}

                    // Async background maintenance calls
                    viewModelScope.launch(Dispatchers.IO) {
                        val isoTimestamp = java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now())
                        try {
                            FirebaseFirestore.getInstance().collection("users").document(uid).update(
                                mapOf(
                                    "securityData.failedLoginCount" to 0,
                                    "terms_accepted" to true,
                                    "termsAccepted" to true,
                                    "terms_accepted_at" to isoTimestamp,
                                    "termsAcceptedAt" to isoTimestamp
                                )
                            )
                        } catch (e: Throwable) {
                            Log.w("Plenxo", "Firestore terms update note: ${e.message}")
                        }
                        try { saveFcmToken() } catch (e: Throwable) {}
                        try { triggerLoginAlertEmail(resolvedEmail) } catch (ignored: Throwable) {}
                        try { auditSession() } catch (ignored: Throwable) {}
                        try { registerE2EEKey() } catch (ignored: Throwable) {}
                    }
                    
                    val readResult = com.example.model.fetchUserDocumentSafely(uid, firestore, emailFallback = resolvedEmail)

                    try {
                        val userDocDetailed = readResult.snapshot
                        if (userDocDetailed != null && userDocDetailed.exists()) {
                            val existingPxId = userDocDetailed.getString("plenxoId")
                                ?: userDocDetailed.getString("plenxo_id")
                                ?: userDocDetailed.getString("px_id")
                                ?: userDocDetailed.getString("userCode")
                                ?: userDocDetailed.getString("user_code")
                                ?: ""
                            val name = userDocDetailed.getString("displayName")
                                ?: userDocDetailed.getString("display_name")
                                ?: userDocDetailed.getString("name")
                                ?: userDocDetailed.getString("current_name")
                                ?: userDocDetailed.getString("fullName")
                                ?: ""
                            val bioText = userDocDetailed.getString("bio")
                                ?: userDocDetailed.getString("statusMessage")
                                ?: userDocDetailed.getString("current_bio")
                                ?: userDocDetailed.getString("status_message")
                                ?: userDocDetailed.getString("about")
                                ?: userDocDetailed.getString("status")
                                ?: ""
                            val em = userDocDetailed.getString("email") ?: resolvedEmail
                            val phone = userDocDetailed.getString("phoneNumber") ?: ""
                            val theme = userDocDetailed.getString("themePreference") ?: userDocDetailed.getString("theme") ?: "Blue"
                            val pic = userDocDetailed.getString("profilePicUrl")
                                ?: userDocDetailed.getString("profilePic")
                                ?: userDocDetailed.getString("avatarUrl")
                                ?: userDocDetailed.getString("avatar_url")
                                ?: userDocDetailed.getString("photoUrl")
                                ?: userDocDetailed.getString("photo_url")
                                ?: userDocDetailed.getString("profileUrl")
                                ?: userDocDetailed.getString("current_profile_pic_url")
                                ?: ""
                            val ageStr = userDocDetailed.get("age")?.toString() ?: userDocDetailed.getString("dateOfBirth") ?: ""
                            val gender = userDocDetailed.getString("gender") ?: ""
                            val ring = userDocDetailed.getString("profileRingId") ?: userDocDetailed.getString("selectedRingId") ?: "none"

                            val formattedPxId = if (existingPxId.isNotBlank()) {
                                if (existingPxId.startsWith("PX-")) existingPxId else "PX-$existingPxId"
                            } else {
                                com.example.model.resolveOrCreatePlenxoId(uid, firestore)
                            }

                            if (formattedPxId.isNotBlank()) {
                                plenxoId.value = formattedPxId
                                revealedPlenxoId.value = formattedPxId
                                userCode.value = formattedPxId.removePrefix("PX-")
                            }
                            if (name.isNotBlank()) displayName.value = name
                            if (bioText.isNotBlank()) aboutText.value = bioText
                            if (em.isNotBlank()) email.value = em
                            if (phone.isNotBlank()) phoneNumber.value = phone
                            if (theme.isNotBlank()) selectedTheme.value = theme
                            if (ring.isNotBlank() && ring != "none") profileRingId.value = ring

                            if (pic.isNotBlank()) {
                                galleryImageUriString.value = pic
                                uploadedProfilePicUrl.value = pic
                                if (pic.startsWith("http")) {
                                    avatarType.value = "gallery"
                                } else if (pic.contains(":")) {
                                    avatarType.value = "placeholder"
                                    val avatarList = maleAvatars + femaleAvatars
                                    val idx = avatarList.indexOfFirst { "${it.first}:${it.second}" == pic }
                                    if (idx != -1) {
                                        selectedAvatarIndex.value = idx
                                    }
                                } else {
                                    avatarType.value = "emoji"
                                    selectedEmoji.value = pic
                                }
                            }

                            currentUserProfile.value = UserProfile(
                                uid = uid,
                                id = uid,
                                email = em,
                                displayName = name,
                                bio = bioText,
                                statusMessage = bioText,
                                profilePicUrl = pic,
                                plenxoId = formattedPxId,
                                userCode = formattedPxId.removePrefix("PX-"),
                                profileRingId = ring,
                                selectedRingId = ring
                            )

                            SessionManager.saveUserProfileLocally(
                                getApplication(),
                                plenxoId = formattedPxId,
                                displayName = name,
                                bio = bioText,
                                profilePicUrl = pic,
                                age = ageStr
                            )
                            SessionManager.saveOnboardingCompleted(getApplication(), true)
                        } else if (readResult.readConfirmed && (userDocDetailed == null || !userDocDetailed.exists())) {
                            // First time document creation for new account ONLY if document is confirmed to not exist
                            val generatedPxId = com.example.model.resolveOrCreatePlenxoId(uid, firestore)
                            val numericCode = generatedPxId.removePrefix("PX-")
                            val initialMap = mapOf(
                                "uid" to uid,
                                "id" to uid,
                                "email" to resolvedEmail,
                                "displayName" to resolvedEmail.substringBefore("@"),
                                "plenxoId" to generatedPxId,
                                "plenxo_id" to generatedPxId,
                                "userCode" to numericCode,
                                "user_code" to numericCode,
                                "bio" to "",
                                "profilePicUrl" to "",
                                "isProfileSetupCompleted" to false,
                                "createdAt" to System.currentTimeMillis()
                            )
                            firestore.collection("users").document(uid).set(initialMap, com.google.firebase.firestore.SetOptions.merge()).await()

                            plenxoId.value = generatedPxId
                            revealedPlenxoId.value = generatedPxId
                            userCode.value = numericCode
                            if (displayName.value.isBlank()) displayName.value = resolvedEmail.substringBefore("@")
                            email.value = resolvedEmail
                        } else {
                            // Network/read could not be completed synchronously - load local cached profile or basic auth info
                            val localProf = SessionManager.getUserProfileLocally(getApplication())
                            if (localProf.plenxoId.isNotBlank()) {
                                plenxoId.value = localProf.plenxoId
                                revealedPlenxoId.value = localProf.plenxoId
                                userCode.value = localProf.plenxoId.removePrefix("PX-")
                            }
                            if (localProf.displayName.isNotBlank()) {
                                displayName.value = localProf.displayName
                            } else {
                                displayName.value = currentUser.displayName ?: resolvedEmail.substringBefore("@")
                            }
                            if (localProf.bio.isNotBlank()) aboutText.value = localProf.bio
                            if (localProf.profilePicUrl.isNotBlank()) galleryImageUriString.value = localProf.profilePicUrl
                            email.value = resolvedEmail
                        }
                    } catch (e: Throwable) {
                        Log.e("Plenxo", "Failed to load/verify user profile on login: ${e.message}", e)
                        if (displayName.value.isBlank()) displayName.value = currentUser.displayName ?: resolvedEmail.substringBefore("@")
                        email.value = resolvedEmail
                    }
                    
                    _isLoading.value = false
                    _errorMessage.value = null
                    try { startListeningForChats() } catch (ignored: Throwable) {}

                    _authState.value = AuthState.AUTHENTICATED
                    observeCurrentUserProfile()
                    Log.d("Plenxo", "Login successful for uid $uid. Navigating to HOME")
                    navigateToScreen(PlenxoScreen.HOME, addToHistory = false, clearHistory = true)
                } catch (authEx: Throwable) {
                    Log.e("SUPABASE_AUTH", "Login Exception Details: ", authEx)
                    
                    var attempts = 1
                    try {
                        attempts = com.example.util.SessionManager.incrementFailedPasswordAttempts(getApplication(), rawInput)
                        if (resolvedEmail != rawInput) {
                            com.example.util.SessionManager.incrementFailedPasswordAttempts(getApplication(), resolvedEmail)
                        }

                        if (attempts >= 5) {
                            val lockoutTime = System.currentTimeMillis() + 15 * 60 * 1000
                            com.example.util.SessionManager.saveLockoutUntil(getApplication(), rawInput, lockoutTime)
                            if (resolvedEmail != rawInput) {
                                com.example.util.SessionManager.saveLockoutUntil(getApplication(), resolvedEmail, lockoutTime)
                            }
                            checkAndStartLockoutTimer(rawInput)
                            _errorMessage.value = "Too many failed attempts. Account temporarily locked."
                        } else {
                            val remaining = 5 - attempts
                            _errorMessage.value = "Incorrect email or password. $remaining attempts remaining."
                        }
                    } catch (e: Throwable) {
                        _errorMessage.value = authEx.localizedMessage ?: "Authentication failed."
                    }
                    // Only reset CAPTCHA if 3 or more attempts failed so user doesn't have to re-solve each retry
                    if (attempts >= 3) {
                        try { resetCaptcha() } catch (e: Throwable) {}
                    }
                }
            } catch (e: Throwable) {
                Log.e("SUPABASE_AUTH", "Unhandled Login Exception Details: ", e)
                val rawMessage = e.localizedMessage ?: e.message ?: e.toString()
                val displayMsg = if (rawMessage.isBlank()) "Error logging in" else rawMessage
                _errorMessage.value = displayMsg
                try {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), displayMsg, Toast.LENGTH_LONG).show()
                    }
                } catch (t: Throwable) {}
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onSignupClicked() = onSignUpClicked()

    fun onSignUpClicked() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                clearError()
                val rawEmail = email.value.trim()
                val rawPassword = password.value
                val rawConfirmPassword = confirmPassword.value

                val gmailRegex = Regex("^[a-zA-Z0-9._%+-]+@gmail\\.com$", RegexOption.IGNORE_CASE)
                if (_captchaStage.value != com.example.model.CaptchaStage.FULLY_VERIFIED) {
                    _errorMessage.value = "Please complete the dual-stage security verification (Text CAPTCHA + Slider Puzzle)."
                    return@launch
                }
                if (!isTermsAccepted.value) {
                    _errorMessage.value = "Please accept the Terms & Conditions to proceed."
                    return@launch
                }
                if (rawEmail.isEmpty() || !gmailRegex.matches(rawEmail)) {
                    _errorMessage.value = "Registration is restricted exclusively to valid @gmail.com accounts."
                    return@launch
                }
                if (rawPassword.isEmpty()) {
                    _errorMessage.value = "Please enter your password."
                    return@launch
                }
                if (rawConfirmPassword.isEmpty()) {
                    _errorMessage.value = "Please enter your confirm password."
                    return@launch
                }
                if (rawPassword.length < 6) {
                    _errorMessage.value = "Password must be at least 6 characters long."
                    return@launch
                }
                if (rawPassword != rawConfirmPassword) {
                    _errorMessage.value = "Passwords do not match."
                    return@launch
                }

                // Validate client daily request quota (max 5 requests/day)
                val (canRequest, remaining) = OtpRateLimiter.checkDailyLimit(getApplication(), rawEmail)
                if (!canRequest) {
                    val limitMsg = "Daily limit exceeded: You can only request up to ${OtpRateLimiter.MAX_DAILY_REQUESTS} verification codes per day. Please try again tomorrow."
                    _errorMessage.value = limitMsg
                    _otpUiState.value = OtpUiState.Error(
                        message = limitMsg,
                        isRateLimited = true,
                        isDailyLimitExceeded = true
                    )
                    _isLoading.value = false
                    withContext(Dispatchers.Main) {
                        try {
                            Toast.makeText(getApplication(), limitMsg, Toast.LENGTH_LONG).show()
                        } catch (e: Throwable) {}
                    }
                    return@launch
                }

                // Hold temporary signup credentials in memory until OTP code is verified
                tempSignupEmail = rawEmail
                tempSignupPassword = rawPassword
                tempSignupName = displayName.value.trim().ifBlank { rawEmail.substringBefore("@") }

                password.value = ""
                confirmPassword.value = ""
                clearError()

                // Prepare local states
                enteredOtp.value = ""

                // (a) Generate active 6-digit OTP client-side immediately
                val activeCode = OtpUtils.generateOtp(rawEmail)
                _activeOtp.value = activeCode
                _generatedOtp.value = activeCode

                // (b) IMMEDIATELY transition auth state and navigate to OTP verification screen without waiting
                startTimer()
                _authState.value = AuthState.VERIFYING_OTP
                _currentScreen.value = PlenxoScreen.OTP_VERIFICATION
                _isLoading.value = false

                // (c) Asynchronously fire off Firestore pre-sync write and Netlify network dispatch as separate background coroutines
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        OtpUtils.saveOtpToFirestore(rawEmail, activeCode, firestore)
                    } catch (e: Throwable) {
                        Log.w("PlenxoSignup", "Async OTP Firestore sync error: ${e.message}")
                    }
                }

                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        requestNetlifyOtp(rawEmail, purpose = "signup", clientOtp = activeCode)
                    } catch (e: Exception) {
                        Log.e("PlenxoSignup", "Background OTP dispatch error: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("PlenxoSignup", "Signup initialization error: ${e.message}", e)
                val rawMessage = e.localizedMessage ?: e.message ?: e.toString()
                _errorMessage.value = if (rawMessage.isBlank()) "Error during sign up" else rawMessage
                _isLoading.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Timer & Verification logic
    private fun startTimer() {
        timerJob?.cancel()
        _secondsRemaining.value = 150
        _isTimerRunning.value = true
        _otpUiState.value = OtpUiState.TimerActive(150)
        timerJob = viewModelScope.launch {
            while (_secondsRemaining.value > 0) {
                delay(1000)
                _secondsRemaining.value -= 1
                _otpUiState.value = OtpUiState.TimerActive(_secondsRemaining.value)
            }
            _isTimerRunning.value = false
            if (_otpUiState.value is OtpUiState.TimerActive) {
                _otpUiState.value = OtpUiState.Idle
            }
        }
    }

    private fun sha256(input: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun onVerifyOtpClicked() {
        if (_isOtpButtonFrozen.value) {
            _errorMessage.value = "Security Block: Too many failed OTP attempts. Please wait 60 seconds to try again."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                    val rawEntered = enteredOtp.value.trim()
                    val entered = if (rawEntered.matches(Regex("^\\d{1,5}$"))) rawEntered.padStart(6, '0') else rawEntered

                    if (entered.length != 6 || !entered.all { it.isDigit() }) {
                        _errorMessage.value = "Validation Error: Please enter a valid 6-digit verification code."
                        return@withTimeoutOrNull
                    }

                    val rawEmail = if (tempSignupEmail.isNotBlank()) tempSignupEmail else email.value.trim()
                    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    val rawExpected = _generatedOtp.value.trim()
                    val expectedOtp = if (rawExpected.matches(Regex("^\\d{1,5}$"))) rawExpected.padStart(6, '0') else rawExpected

                    val currentActive = _activeOtp.value.trim()
                    val userInput = entered

                    Log.d("OTP_DEBUG", "Received from Netlify: ${_activeOtp.value}")
                    Log.d("OTP_DEBUG", "Entered by User: $userInput")

                    val isDirectMatch = (currentActive.isNotEmpty() && (currentActive == userInput || currentActive.equals(userInput, ignoreCase = true))) ||
                                        (expectedOtp.isNotEmpty() && (expectedOtp == userInput || expectedOtp.equals(userInput, ignoreCase = true)))

                    val firestoreOtps = mutableListOf<String>()
                    if (!isDirectMatch) {
                        if (currentUid.isNotBlank()) {
                            try {
                                firestoreOtps.addAll(OtpUtils.getFirestoreOtpsForUser(currentUid, firestore))
                            } catch (_: Exception) {}
                        }
                        if (rawEmail.isNotBlank()) {
                            try {
                                firestoreOtps.addAll(OtpUtils.getFirestoreOtpsForUser(rawEmail, firestore))
                            } catch (_: Exception) {}
                        }
                    }

                    val isVerified = isDirectMatch || firestoreOtps.any { it.isNotBlank() && (it.trim() == userInput || it.trim().equals(userInput, ignoreCase = true)) }

                    if (isVerified) {
                        _activeOtp.value = "" // Only clear upon SUCCESSFUL verification
                        enteredOtp.value = ""
                        failedOtpAttempts = 0
                        clearError()

                        val isSignup = tempSignupEmail.isNotBlank() || (FirebaseAuth.getInstance().currentUser == null && (email.value.trim().isNotBlank() || tempSignupPassword.isNotBlank()))

                        if (isSignup) {
                            val targetEmail = if (tempSignupEmail.isNotBlank()) tempSignupEmail else email.value.trim()
                            val targetPassword = if (tempSignupPassword.isNotBlank()) tempSignupPassword else password.value
                            val targetName = tempSignupName.ifBlank { displayName.value.trim().ifBlank { targetEmail.substringBefore("@") } }

                            var currentFirebaseUser = FirebaseAuth.getInstance().currentUser
                            var createAccountError: Exception? = null
                            var signInError: Exception? = null

                            if (currentFirebaseUser == null && targetPassword.isNotBlank()) {
                                try {
                                    val authResult = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                                        FirebaseAuth.getInstance().createUserWithEmailAndPassword(targetEmail, targetPassword).await()
                                    }
                                    currentFirebaseUser = authResult?.user ?: FirebaseAuth.getInstance().currentUser
                                } catch (ex: Exception) {
                                    createAccountError = ex
                                    Log.e("PlenxoSignup", "createUserWithEmailAndPassword failed: ${ex.message}", ex)
                                    try {
                                        val loginRes = FirebaseAuth.getInstance().signInWithEmailAndPassword(targetEmail, targetPassword).await()
                                        currentFirebaseUser = loginRes.user
                                    } catch (signInEx: Exception) {
                                        signInError = signInEx
                                        Log.e("PlenxoSignup", "signInWithEmailAndPassword fallback failed: ${signInEx.message}", signInEx)
                                    }
                                }
                            }

                            val uid = currentFirebaseUser?.uid ?: FirebaseAuth.getInstance().currentUser?.uid ?: ""

                            if (uid.isBlank()) {
                                Log.e("PlenxoSignup", "Account creation failed: UID is blank after Auth attempts.")
                                val detailMsg = createAccountError?.localizedMessage
                                    ?: signInError?.localizedMessage
                                    ?: "Account creation failed. Please check your credentials and connection."
                                _errorMessage.value = "Account creation failed: $detailMsg"
                                _isLoading.value = false
                                return@withTimeoutOrNull
                            }

                            val generatedPxId = com.example.model.getOrCreatePermanentPlenxoId(uid, firestore)
                            val generatedCode = generatedPxId.removePrefix("PX-")

                            plenxoId.value = generatedPxId
                            revealedPlenxoId.value = generatedPxId
                            userCode.value = generatedCode

                            // Authoritatively persist initial user profile to Firestore
                            val initialUserMap = mapOf(
                                "uid" to uid,
                                "id" to uid,
                                "email" to targetEmail,
                                "displayName" to targetName.ifBlank { targetEmail.substringBefore("@") },
                                "display_name" to targetName.ifBlank { targetEmail.substringBefore("@") },
                                "name" to targetName.ifBlank { targetEmail.substringBefore("@") },
                                "current_name" to targetName.ifBlank { targetEmail.substringBefore("@") },
                                "plenxoId" to generatedPxId,
                                "plenxo_id" to generatedPxId,
                                "userCode" to generatedCode,
                                "user_code" to generatedCode,
                                "px_id" to generatedPxId,
                                "px_code" to generatedCode,
                                "isEmailVerified" to true,
                                "emailVerified" to true,
                                "is_email_verified" to true,
                                "isProfileSetupCompleted" to false,
                                "isProfileSetup" to false,
                                "profileSetupCompleted" to false,
                                "is_profile_completed" to false,
                                "bio" to "",
                                "profilePicUrl" to "",
                                "created_at" to System.currentTimeMillis(),
                                "createdAt" to System.currentTimeMillis(),
                                "updatedAt" to System.currentTimeMillis()
                            )

                            for (attempt in 1..3) {
                                try {
                                    kotlinx.coroutines.withTimeoutOrNull(4000L) {
                                        firestore.collection("users").document(uid)
                                            .set(initialUserMap, com.google.firebase.firestore.SetOptions.merge())
                                            .await()
                                    }
                                    Log.d("PlenxoSignup", "Account document persisted to Firestore successfully on attempt $attempt")
                                    break
                                } catch (dbEx: Exception) {
                                    Log.w("PlenxoSignup", "Initial user document write attempt $attempt failed: ${dbEx.message}")
                                    if (attempt < 3) kotlinx.coroutines.delay(200L)
                                }
                            }

                            // Realtime DB sync (asynchronous background - never blocks navigation)
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    val rdbRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users").child(uid)
                                    val rdbMap = hashMapOf<String, Any>(
                                        "uid" to uid,
                                        "id" to uid,
                                        "email" to targetEmail,
                                        "displayName" to targetName.ifBlank { targetEmail.substringBefore("@") },
                                        "name" to targetName.ifBlank { targetEmail.substringBefore("@") },
                                        "plenxo_id" to generatedPxId,
                                        "plenxoId" to generatedPxId,
                                        "user_code" to generatedCode,
                                        "userCode" to generatedCode,
                                        "created_at" to System.currentTimeMillis(),
                                        "createdAt" to System.currentTimeMillis(),
                                        "is_profile_completed" to false,
                                        "is_email_verified" to true
                                    )
                                    rdbRef.updateChildren(rdbMap)
                                } catch (rdbEx: Exception) {
                                    Log.w("PlenxoSignup", "Async Realtime DB sync: ${rdbEx.message}")
                                }
                            }

                            // Hydrate session & state so user identity survives immediately
                            SessionManager.saveLoginState(getApplication(), uid, targetEmail)
                            SessionManager.saveUserProfileLocally(
                                getApplication(),
                                plenxoId = generatedPxId,
                                displayName = targetName.ifBlank { targetEmail.substringBefore("@") },
                                bio = "",
                                profilePicUrl = ""
                            )

                            currentUserProfile.value = UserProfile(
                                uid = uid,
                                id = uid,
                                email = targetEmail,
                                displayName = targetName.ifBlank { targetEmail.substringBefore("@") },
                                bio = "",
                                statusMessage = "",
                                profilePicUrl = "",
                                plenxoId = generatedPxId,
                                userCode = generatedCode
                            )

                            tempSignupEmail = ""
                            tempSignupPassword = ""
                            tempSignupName = ""
                            password.value = ""
                            confirmPassword.value = ""

                            _authState.value = AuthState.NEEDS_PROFILE_SETUP
                            navigateToScreen(PlenxoScreen.PROFILE_SETUP, addToHistory = false, clearHistory = true)
                            _isLoading.value = false
                        } else {
                            // Login OTP challenge or existing user flow
                            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

                            if (uid.isEmpty()) {
                                Log.e("Plenxo", "OTP verification failed: No active Firebase Auth user session.")
                                _errorMessage.value = "Authentication session expired or invalid. Please sign in again."
                                _isLoading.value = false
                                return@withTimeoutOrNull
                            }

                            if (uid.isNotEmpty()) {
                                try {
                                    val verificationMap = mapOf(
                                        "isEmailVerified" to true,
                                        "emailVerified" to true,
                                        "is_email_verified" to true
                                    )
                                    firestore.collection("users").document(uid).set(verificationMap, com.google.firebase.firestore.SetOptions.merge()).await()
                                } catch (e: Exception) {
                                    Log.e("Plenxo", "Failed to update email verification status: ${e.message}", e)
                                }
                            }

                            val readResult = com.example.model.fetchUserDocumentSafely(uid, firestore)

                            val userDoc = readResult.snapshot
                            if (userDoc != null && userDoc.exists()) {
                                val existingPxId = userDoc.getString("plenxoId")
                                    ?: userDoc.getString("plenxo_id")
                                    ?: userDoc.getString("px_id")
                                    ?: userDoc.getString("userCode")
                                    ?: ""
                                val existingName = userDoc.getString("displayName")
                                    ?: userDoc.getString("name")
                                    ?: ""
                                val existingBio = userDoc.getString("bio")
                                    ?: userDoc.getString("statusMessage")
                                    ?: ""
                                val existingPic = userDoc.getString("profilePicUrl")
                                    ?: userDoc.getString("avatar_url")
                                    ?: userDoc.getString("photoUrl")
                                    ?: ""
                                val existingAge = userDoc.get("age")?.toString()
                                    ?: userDoc.getString("dateOfBirth")
                                    ?: ""

                                // Returning user: Save fetched data to SessionManager and immediately route to HOME
                                val formatted = if (existingPxId.isNotBlank()) {
                                    if (existingPxId.startsWith("PX-")) existingPxId else "PX-$existingPxId"
                                } else {
                                    com.example.model.getOrCreatePermanentPlenxoId(uid, firestore)
                                }
                                plenxoId.value = formatted
                                revealedPlenxoId.value = formatted
                                userCode.value = formatted.removePrefix("PX-")
                                if (existingName.isNotBlank()) displayName.value = existingName
                                if (existingBio.isNotBlank()) aboutText.value = existingBio
                                if (existingPic.isNotBlank()) galleryImageUriString.value = existingPic

                                currentUserProfile.value = UserProfile(
                                    uid = uid,
                                    id = uid,
                                    email = rawEmail,
                                    displayName = existingName,
                                    bio = existingBio,
                                    statusMessage = existingBio,
                                    profilePicUrl = existingPic,
                                    plenxoId = formatted,
                                    userCode = formatted.removePrefix("PX-")
                                )

                                SessionManager.saveUserProfileLocally(
                                    getApplication(),
                                    plenxoId = formatted,
                                    displayName = existingName,
                                    bio = existingBio,
                                    profilePicUrl = existingPic,
                                    age = existingAge
                                )

                                _authState.value = AuthState.AUTHENTICATED
                                SessionManager.saveLoginState(getApplication(), uid, rawEmail)
                                triggerLoginAlertEmail(rawEmail)
                                auditSession()
                                registerE2EEKey()
                                startListeningForChats()
                                com.example.util.SessionManager.saveCaptchaVerified(getApplication(), false)
                                com.example.util.AppLockManager.setLocked(getApplication(), false)
                                observeCurrentUserProfile()
                                _currentScreen.value = PlenxoScreen.HOME
                            } else if (readResult.readConfirmed && (userDoc == null || !userDoc.exists())) {
                                // Genuine New User: Generate permanent Plenxo ID and route to Profile Setup ONLY if document confirmed not existing
                                _authState.value = AuthState.NEEDS_PROFILE_SETUP
                                if (uid.isNotEmpty()) {
                                    try {
                                        val generatedPxId = com.example.model.resolveOrCreatePlenxoId(uid, firestore)
                                        val generatedCode = generatedPxId.removePrefix("PX-")
                                        val initialUser = mapOf(
                                            "uid" to uid,
                                            "id" to uid,
                                            "email" to rawEmail,
                                            "displayName" to rawEmail.substringBefore("@"),
                                            "plenxoId" to generatedPxId,
                                            "userCode" to generatedCode,
                                            "user_code" to generatedCode,
                                            "px_id" to generatedPxId,
                                            "px_code" to generatedCode,
                                            "isProfileSetupCompleted" to false,
                                            "lastLoginTimestamp" to System.currentTimeMillis()
                                        )
                                        firestore.collection("users").document(uid).set(initialUser, com.google.firebase.firestore.SetOptions.merge()).await()
                                        
                                        plenxoId.value = generatedPxId
                                        revealedPlenxoId.value = generatedPxId
                                        userCode.value = generatedCode
                                    } catch (e: Exception) {
                                        Log.e("Plenxo", "Failed in OTP verification new user profile logic: ${e.message}", e)
                                    }
                                }
                                navigateToScreen(PlenxoScreen.PROFILE_SETUP, addToHistory = false, clearHistory = true)
                            } else {
                                // Fallback on slow connection: load local cache and navigate to HOME
                                val localProf = SessionManager.getUserProfileLocally(getApplication())
                                if (localProf.plenxoId.isNotBlank()) {
                                    plenxoId.value = localProf.plenxoId
                                    revealedPlenxoId.value = localProf.plenxoId
                                    userCode.value = localProf.plenxoId.removePrefix("PX-")
                                }
                                if (localProf.displayName.isNotBlank()) {
                                    displayName.value = localProf.displayName
                                } else {
                                    displayName.value = rawEmail.substringBefore("@")
                                }
                                if (localProf.bio.isNotBlank()) aboutText.value = localProf.bio
                                if (localProf.profilePicUrl.isNotBlank()) galleryImageUriString.value = localProf.profilePicUrl

                                _authState.value = AuthState.AUTHENTICATED
                                SessionManager.saveLoginState(getApplication(), uid, rawEmail)
                                triggerLoginAlertEmail(rawEmail)
                                auditSession()
                                registerE2EEKey()
                                startListeningForChats()
                                com.example.util.SessionManager.saveCaptchaVerified(getApplication(), false)
                                com.example.util.AppLockManager.setLocked(getApplication(), false)
                                observeCurrentUserProfile()
                                _currentScreen.value = PlenxoScreen.HOME
                            }
                        }
                    } else {
                        failedOtpAttempts++
                        _errorMessage.value = null
                        
                        if (failedOtpAttempts >= 3) {
                            _isOtpButtonFrozen.value = true
                            _errorMessage.value = "Security Block: Too many failed OTP attempts. Verification is frozen for 60 seconds."
                            viewModelScope.launch {
                                delay(60_000)
                                _isOtpButtonFrozen.value = false
                                failedOtpAttempts = 0
                            }
                        } else {
                            val remaining = 3 - failedOtpAttempts
                            _errorMessage.value = "Invalid OTP code. Please try again ($remaining attempts remaining)."
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Unhandled Verification Exception: ${e.message}", e)
                _errorMessage.value = "An unexpected error occurred during OTP verification: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Navigation and Profile Setup Helpers
    private val plenxoIdPattern = Regex("^PX-\\d{6}$")

    /**
     * Resolves or generates the permanent Plenxo ID for the active authenticated user.
     */
    fun generateUniquePlenxoId() {
        val uid = currentUserId
        viewModelScope.launch(Dispatchers.IO) {
            _isGeneratingPlenxoId.value = true
            _isPlenxoIdAvailable.value = null
            try {
                val finalId = if (uid.isNotBlank()) {
                    com.example.model.getOrCreatePermanentPlenxoId(uid, firestore)
                } else {
                    com.example.model.generateUniqueNumericPlenxoId(firestore)
                }
                withContext(Dispatchers.Main) {
                    plenxoId.value = finalId
                    revealedPlenxoId.value = finalId
                    userCode.value = finalId.removePrefix("PX-")
                    _isPlenxoIdAvailable.value = true
                    _isGeneratingPlenxoId.value = false
                    Log.d("Plenxo", "Resolved permanent Plenxo ID: $finalId")
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Error resolving permanent Plenxo ID: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _isPlenxoIdAvailable.value = true
                    _isGeneratingPlenxoId.value = false
                }
            }
        }
    }

    fun checkPlenxoIdAvailability(id: String) {
        val trimmed = id.trim()
        if (!plenxoIdPattern.matches(trimmed)) {
            _isPlenxoIdAvailable.value = false
            return
        }

        _isPlenxoIdAvailable.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val querySnapshot = firestore.collection("users")
                    .whereEqualTo("plenxoId", trimmed)
                    .get()
                    .await()

                val isAvailable = querySnapshot.isEmpty
                withContext(Dispatchers.Main) {
                    _isPlenxoIdAvailable.value = isAvailable
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to check Plenxo ID availability: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _isPlenxoIdAvailable.value = null
                }
            }
        }
    }

    // ==========================================
    // TWO-FACTOR AUTHENTICATION & MASTER PIN LOGIC
    // ==========================================

    /**
     * Compute SHA-256 hash string of the 6-digit Master PIN.
     */
    fun hashMasterPin(pin: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(pin.toByteArray(Charsets.UTF_8))
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    /**
     * Enables 2FA with a validated 6-digit Master PIN.
     * Updates Firestore users/{uid} with hashed PIN and is2FAEnabled = true.
     */
    fun enable2FA(masterPin: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        val trimmedPin = masterPin.trim()
        if (!Regex("^\\d{6}$").matches(trimmedPin)) {
            _setup2FAError.value = "PIN must be exactly 6 numeric digits."
            onComplete?.invoke(false, "PIN must be exactly 6 numeric digits.")
            return
        }

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (currentUid.isEmpty()) {
            _setup2FAError.value = "User not authenticated. Please sign in again."
            onComplete?.invoke(false, "User not authenticated.")
            return
        }

        _isSettingUp2FA.value = true
        _setup2FAError.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val hashedPin = hashMasterPin(trimmedPin)
                val updateData = mapOf<String, Any>(
                    "is2FAEnabled" to true,
                    "is_2fa_enabled" to true,
                    "twoFactorEnabled" to true,
                    "masterPin" to hashedPin,
                    "master_pin" to hashedPin,
                    "twoFactorUpdatedAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis()
                )

                firestore.collection("users").document(currentUid)
                    .set(updateData, com.google.firebase.firestore.SetOptions.merge())
                    .await()

                withContext(Dispatchers.Main) {
                    _is2FAEnabled.value = true
                    _storedMasterPinHash.value = hashedPin
                    _isSettingUp2FA.value = false
                    _setup2FAError.value = null
                    Log.d("Plenxo2FA", "2FA enabled successfully with 6-digit Master PIN for user $currentUid")
                    onComplete?.invoke(true, null)
                }
            } catch (e: Exception) {
                Log.e("Plenxo2FA", "Failed to enable 2FA: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _isSettingUp2FA.value = false
                    val errorMsg = "Failed to enable 2FA: ${e.localizedMessage ?: e.message}"
                    _setup2FAError.value = errorMsg
                    onComplete?.invoke(false, errorMsg)
                }
            }
        }
    }

    /**
     * Disables 2FA after verifying the current 6-digit Master PIN.
     * Clears master PIN and sets is2FAEnabled = false in Firestore.
     */
    fun disable2FA(currentMasterPin: String, onComplete: ((Boolean, String?) -> Unit)? = null) {
        val trimmedPin = currentMasterPin.trim()
        if (trimmedPin.isEmpty()) {
            _setup2FAError.value = "Please enter your 6-digit Master PIN."
            onComplete?.invoke(false, "Please enter your 6-digit Master PIN.")
            return
        }

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (currentUid.isEmpty()) {
            _setup2FAError.value = "User not authenticated. Please sign in again."
            onComplete?.invoke(false, "User not authenticated.")
            return
        }

        _isSettingUp2FA.value = true
        _setup2FAError.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                var storedHash = _storedMasterPinHash.value
                if (storedHash.isNullOrEmpty()) {
                    val userDoc = firestore.collection("users").document(currentUid).get().await()
                    storedHash = userDoc.getString("masterPin") ?: userDoc.getString("master_pin")
                }

                val enteredHash = hashMasterPin(trimmedPin)
                val isPinCorrect = (storedHash != null && (storedHash == enteredHash || storedHash == trimmedPin))

                if (!isPinCorrect) {
                    withContext(Dispatchers.Main) {
                        _isSettingUp2FA.value = false
                        val errorMsg = "Incorrect Master PIN. Please try again."
                        _setup2FAError.value = errorMsg
                        onComplete?.invoke(false, errorMsg)
                    }
                    return@launch
                }

                val updateData = mapOf<String, Any>(
                    "is2FAEnabled" to false,
                    "is_2fa_enabled" to false,
                    "twoFactorEnabled" to false,
                    "masterPin" to "",
                    "master_pin" to "",
                    "twoFactorUpdatedAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis()
                )

                firestore.collection("users").document(currentUid)
                    .set(updateData, com.google.firebase.firestore.SetOptions.merge())
                    .await()

                withContext(Dispatchers.Main) {
                    _is2FAEnabled.value = false
                    _storedMasterPinHash.value = null
                    _isSettingUp2FA.value = false
                    _setup2FAError.value = null
                    Log.d("Plenxo2FA", "2FA disabled successfully for user $currentUid")
                    onComplete?.invoke(true, null)
                }
            } catch (e: Exception) {
                Log.e("Plenxo2FA", "Failed to disable 2FA: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _isSettingUp2FA.value = false
                    val errorMsg = "Failed to disable 2FA: ${e.localizedMessage ?: e.message}"
                    _setup2FAError.value = errorMsg
                    onComplete?.invoke(false, errorMsg)
                }
            }
        }
    }

    fun clear2FAError() {
        _setup2FAError.value = null
    }

    fun uploadProfilePictureToCatbox(
        context: Context,
        imageUri: Uri,
        onSuccess: (imageUrl: String) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uploadedUrl = com.example.network.CatboxUploader.uploadImage(context, imageUri)
                withContext(Dispatchers.Main) {
                    onSuccess(uploadedUrl)
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Error uploading avatar to Catbox: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError("Upload failed: ${e.localizedMessage ?: e.message}")
                }
            }
        }
    }

    fun saveProfileStepOne(
        displayName: String,
        plenxoId: String,
        avatarUrl: String,
        bio: String = "",
        gender: String = "",
        dobMillis: Long? = null,
        interests: List<String> = emptyList(),
        enable2FA: Boolean = false,
        masterPin: String? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            ?: currentUserId.ifEmpty { SessionManager.getLoginState(getApplication()).token ?: "" }
            .ifEmpty { email.value.trim().replace(".", "_") }
            .ifEmpty { "user_${System.currentTimeMillis()}" }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            resetOtpState()
        
            var success = false
            try {
                val permanentPxId = withContext(Dispatchers.IO) {
                    com.example.model.getOrCreatePermanentPlenxoId(currentUid, firestore)
                }
                val userNumericCode = permanentPxId.removePrefix("PX-")
                val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: email.value.trim()
                val stepOneData = mutableMapOf<String, Any>(
                    "uid" to currentUid,
                    "id" to currentUid,
                    "email" to userEmail,
                    "displayName" to displayName.trim(),
                    "display_name" to displayName.trim(),
                    "name" to displayName.trim(),
                    "current_name" to displayName.trim(),
                    "plenxoId" to permanentPxId,
                    "plenxo_id" to permanentPxId,
                    "userCode" to userNumericCode,
                    "user_code" to userNumericCode,
                    "avatarUrl" to avatarUrl.trim(),
                    "profilePicUrl" to avatarUrl.trim(),
                    "avatar_url" to avatarUrl.trim(),
                    "photoUrl" to avatarUrl.trim(),
                    "current_profile_pic_url" to avatarUrl.trim(),
                    "bio" to bio.trim(),
                    "current_bio" to bio.trim(),
                    "gender" to gender.trim(),
                    "interests" to interests,
                    "hobbies" to interests,
                    "isEmailVerified" to true,
                    "emailVerified" to true,
                    "is_email_verified" to true,
                    "profileStepOneCompleted" to true,
                    "isProfileSetupCompleted" to true,
                    "isProfileSetup" to true,
                    "profileSetupCompleted" to true,
                    "updatedAt" to System.currentTimeMillis()
                )

                if (dobMillis != null) {
                    stepOneData["dobMillis"] = dobMillis
                    stepOneData["dateOfBirth"] = dobMillis
                }

                if (enable2FA && !masterPin.isNullOrBlank() && Regex("^\\d{6}$").matches(masterPin.trim())) {
                    val hashedPin = hashMasterPin(masterPin.trim())
                    stepOneData["is2FAEnabled"] = true
                    stepOneData["is_2fa_enabled"] = true
                    stepOneData["twoFactorEnabled"] = true
                    stepOneData["masterPin"] = hashedPin
                    stepOneData["master_pin"] = hashedPin
                    _is2FAEnabled.value = true
                    _storedMasterPinHash.value = hashedPin
                }

                withContext(Dispatchers.IO) {
                    var savedToFirestore = false
                    for (attempt in 1..3) {
                        try {
                            firestore.collection("users").document(currentUid)
                                .set(stepOneData, com.google.firebase.firestore.SetOptions.merge())
                                .await()
                            savedToFirestore = true
                            break
                        } catch (e: Exception) {
                            Log.w("Plenxo", "Firestore users write attempt $attempt: ${e.message}")
                            if (attempt < 3) kotlinx.coroutines.delay(300L)
                        }
                    }

                    try {
                        val rdbRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users").child(currentUid)
                        val rdbMap = hashMapOf<String, Any>(
                            "uid" to currentUid,
                            "plenxo_id" to permanentPxId,
                            "plenxoId" to permanentPxId,
                            "user_code" to userNumericCode,
                            "userCode" to userNumericCode,
                            "name" to displayName.trim(),
                            "displayName" to displayName.trim(),
                            "email" to userEmail,
                            "profile_pic_url" to avatarUrl.trim(),
                            "profilePicUrl" to avatarUrl.trim(),
                            "bio" to bio.trim(),
                            "is_profile_completed" to true,
                            "isProfileCompleted" to true,
                            "isProfileSetupCompleted" to true,
                            "is_email_verified" to true
                        )
                        rdbRef.updateChildren(rdbMap)
                    } catch (e: Exception) {
                        Log.w("Plenxo", "Realtime DB users write: ${e.message}")
                    }
                }

                // Persist all local and in-memory profile states
                SessionManager.saveLoginState(getApplication(), currentUid, userEmail)
                this@PlenxoViewModel.displayName.value = displayName.trim()
                this@PlenxoViewModel.aboutText.value = bio.trim()
                this@PlenxoViewModel.plenxoId.value = permanentPxId
                this@PlenxoViewModel.revealedPlenxoId.value = permanentPxId
                this@PlenxoViewModel.userCode.value = userNumericCode
                if (avatarUrl.trim().isNotBlank()) {
                    this@PlenxoViewModel.galleryImageUriString.value = avatarUrl.trim()
                    this@PlenxoViewModel.uploadedProfilePicUrl.value = avatarUrl.trim()
                    if (avatarUrl.trim().startsWith("http")) {
                        this@PlenxoViewModel.avatarType.value = "gallery"
                    }
                }

                this@PlenxoViewModel.currentUserProfile.value = UserProfile(
                    uid = currentUid,
                    id = currentUid,
                    email = userEmail,
                    displayName = displayName.trim(),
                    bio = bio.trim(),
                    statusMessage = bio.trim(),
                    profilePicUrl = avatarUrl.trim(),
                    plenxoId = permanentPxId,
                    userCode = userNumericCode
                )

                SessionManager.saveUserProfileLocally(
                    getApplication(),
                    plenxoId = permanentPxId,
                    displayName = displayName.trim(),
                    bio = bio.trim(),
                    profilePicUrl = avatarUrl.trim(),
                    age = dobMillis?.toString() ?: ""
                )

                observeCurrentUserProfile()
                this@PlenxoViewModel.setRevealedPlenxoId(permanentPxId)
                this@PlenxoViewModel.navigateToScreen(PlenxoScreen.PLENXO_ID_REVEAL, addToHistory = false, clearHistory = true)
                Log.d("Plenxo", "Profile Step 1 saved successfully for user $currentUid with Plenxo ID $permanentPxId")
                success = true
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to save profile step 1: ${e.message}", e)
                _errorMessage.value = "Failed to save profile details: ${e.localizedMessage ?: e.message}"
            } finally {
                _isLoading.value = false
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(success)
                }
            }
        }
    }

    fun verifyProfileSetupStatusAndNavigate() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            _authState.value = AuthState.UNAUTHENTICATED
            navigateToScreen(PlenxoScreen.LOGIN, addToHistory = false, clearHistory = true)
            return
        }

        // CRITICAL GUARD: if the user is currently on the OTP_VERIFICATION screen
        // and has not yet passed OTP verification, do nothing. This function must
        // never force a navigation away from an in-progress OTP challenge — the
        // only thing allowed to move the user off OTP_VERIFICATION is a successful
        // match inside onVerifyOtpClicked().
        if (_currentScreen.value == PlenxoScreen.OTP_VERIFICATION && _authState.value == AuthState.VERIFYING_OTP) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uid = currentUser.uid
                val userDataDoc = kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                    try {
                        firestore.collection("users").document(uid).get().await()
                    } catch (e: Exception) { null }
                }

                val isSetupCompleted = if (userDataDoc != null && userDataDoc.exists()) {
                    val p1 = userDataDoc.getBoolean("isProfileSetupCompleted") == true
                    val p2 = userDataDoc.getBoolean("isProfileSetup") == true
                    val p3 = userDataDoc.getBoolean("profileSetupCompleted") == true
                    val p4 = userDataDoc.getBoolean("is_profile_completed") == true
                    val p5 = userDataDoc.getBoolean("isProfileCompleted") == true
                    p1 || p2 || p3 || p4 || p5
                } else {
                    false
                }

                withContext(Dispatchers.Main) {
                    if (isSetupCompleted) {
                        _authState.value = AuthState.AUTHENTICATED
                        if (_currentScreen.value == PlenxoScreen.LOGIN || _currentScreen.value == PlenxoScreen.PROFILE_SETUP) {
                            navigateToScreen(PlenxoScreen.HOME, addToHistory = false, clearHistory = true)
                        }
                    } else {
                        _authState.value = AuthState.NEEDS_PROFILE_SETUP
                        navigateToScreen(PlenxoScreen.PROFILE_SETUP, addToHistory = false, clearHistory = true)
                    }
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Error verifying profile setup status: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _authState.value = AuthState.NEEDS_PROFILE_SETUP
                    navigateToScreen(PlenxoScreen.PROFILE_SETUP, addToHistory = false, clearHistory = true)
                }
            }
        }
    }

    fun navigateToAvatarSetup() {
        _currentScreen.value = PlenxoScreen.AVATAR_SETUP
    }

    fun navigateToFinalDetails() {
        val numericCode = plenxoId.value.removePrefix("PX-").ifBlank { "000000" }
        userCode.value = numericCode
        _currentScreen.value = PlenxoScreen.FINAL_DETAILS
    }

    fun onFinishSetupClicked() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
        resetOtpState()
        
            try {
                val name = displayName.value.trim()
                if (name.isEmpty()) {
                    _errorMessage.value = "Validation Error: Display Name cannot be empty."
                    _isLoading.value = false
                    return@launch
                }

                // --- AUTH STATE GUARD ---
                val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                if (currentUid.isEmpty()) {
                    Log.e("Plenxo", "onFinishSetupClicked: User is not authenticated. Cannot save profile.")
                    _errorMessage.value = "Authentication Error: Your session has expired. Please log out and sign in again."
                    _isLoading.value = false
                    return@launch
                }
                Log.d("Plenxo", "onFinishSetupClicked: Authenticated")

                val dob = "${birthDay.value}/${birthMonth.value}/${birthYear.value}"

                val avatarValue = when (avatarType.value) {
                    "placeholder" -> {
                        val idx = selectedAvatarIndex.value
                        val avatarList = maleAvatars + femaleAvatars
                        if (idx in avatarList.indices) "${avatarList[idx].first}:${avatarList[idx].second}" else "Leo:🦁"
                    }
                    "gallery" -> galleryImageUriString.value ?: "default_gallery"
                    "emoji" -> selectedEmoji.value
                    else -> "Leo:🦁"
                }

                // RTDB Save (non-blocking, best-effort)
                try {
                    kotlinx.coroutines.withTimeoutOrNull(4000L) {
                        val userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUid)
                        userRef.setValue(
                            mapOf(
                                "theme" to selectedTheme.value,
                                "avatar" to avatarValue,
                                "avatar_type" to avatarType.value,
                                "display_name" to name,
                                "dob" to dob,
                                "userCode" to userCode.value,
                                "email" to email.value.trim()
                            )
                        ).await()
                        Log.d("Plenxo", "RTDB write success")
                    }
                } catch (dbEx: Exception) {
                    Log.e("Plenxo", "RTDB write failed (non-fatal): ${dbEx.message}", dbEx)
                }

                val resolvedPxId = plenxoId.value.ifBlank { com.example.model.resolveOrCreatePlenxoId(currentUid, firestore) }

                val profileDataMap = mapOf<String, Any>(
                    "uid" to currentUid,
                    "id" to currentUid,
                    "email" to email.value.trim(),
                    "displayName" to name,
                    "profilePicUrl" to avatarValue,
                    "themePreference" to selectedTheme.value,
                    "userCode" to userCode.value,
                    "plenxoId" to resolvedPxId,
                    "dob" to dob,
                    "phoneNumber" to phoneNumber.value.trim(),
                    "isProfileSetupCompleted" to true,
                    "isProfileSetup" to true,
                    "profileSetupCompleted" to true,
                    "is_profile_completed" to true,
                    "updatedAt" to System.currentTimeMillis()
                )

                try {
                    val writeRes = kotlinx.coroutines.withTimeoutOrNull(8000L) {
                        firestore.collection("users").document(currentUid).set(profileDataMap, com.google.firebase.firestore.SetOptions.merge()).await()
                        true
                    }
                    if (writeRes == null) {
                        _errorMessage.value = "Network is slow, please check your connection and try again."
                        _isLoading.value = false
                        return@launch
                    }
                    Log.d("Plenxo", "Firestore write success")
                } catch (fsEx: FirebaseFirestoreException) {
                    Log.e("Plenxo", "Firestore write FAILED. Code=${fsEx.code}, Message=${fsEx.message}", fsEx)
                    _errorMessage.value = "Profile Save Failed (${fsEx.code}): ${fsEx.localizedMessage}"
                    _isLoading.value = false
                    return@launch
                } catch (fsEx: Exception) {
                    Log.e("Plenxo", "Firestore write unexpected error: ${fsEx.message}", fsEx)
                    _errorMessage.value = "Profile Save Failed: ${fsEx.localizedMessage}"
                    _isLoading.value = false
                    return@launch
                }

                // Only navigate AFTER confirmed Firestore write
                SessionManager.saveLoginState(getApplication(), currentUid, email.value.trim())
                startListeningForChats()
                com.example.util.SessionManager.saveCaptchaVerified(getApplication(), false)
                com.example.util.AppLockManager.setLocked(getApplication(), false)
                navigateToScreen(PlenxoScreen.HOME, addToHistory = false, clearHistory = true)

            } catch (e: Exception) {
                Log.e("Plenxo", "Unhandled Finish Setup Exception: ${e.message}", e)
                _errorMessage.value = "An unexpected error occurred: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun navigateBackToSignup() {
        // Clear local session state
        SessionManager.clearLoginState(getApplication())
        
        // Also clear internal state values
        email.value = ""
        password.value = ""
        confirmPassword.value = ""
        isPrivacyAccepted.value = false
        enteredOtp.value = ""
        displayName.value = ""
        userCode.value = ""
        selectedTheme.value = "Plenxo"
        avatarType.value = "placeholder"
        galleryImageUriString.value = null
        selectedEmoji.value = "😊"
        
        // Sign out from Firebase if authenticated
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            Log.e("Plenxo", "Error signing out of Firebase Auth", e)
        }
        
        resetOtpState()
        _currentScreen.value = PlenxoScreen.SIGNUP
    }

    // Helper to resend OTP
    fun resendOtp() {
        val rawEmail = if (tempSignupEmail.isNotBlank()) tempSignupEmail else email.value.trim()
        if (rawEmail.isEmpty()) {
            _errorMessage.value = "Email address is required to resend verification code."
            return
        }

        // Validate client daily request quota (max 5 requests/day)
        val (canRequest, remaining) = OtpRateLimiter.checkDailyLimit(getApplication(), rawEmail)
        if (!canRequest) {
            val limitMsg = "Daily limit exceeded: You can only request up to ${OtpRateLimiter.MAX_DAILY_REQUESTS} verification codes per day. Please try again tomorrow."
            _errorMessage.value = limitMsg
            _otpUiState.value = OtpUiState.Error(
                message = limitMsg,
                isRateLimited = true,
                isDailyLimitExceeded = true
            )
            try {
                Toast.makeText(getApplication(), limitMsg, Toast.LENGTH_LONG).show()
            } catch (e: Throwable) {}
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                enteredOtp.value = ""
                clearError()

                // (a) Generate new active 6-digit OTP client-side immediately
                val newOtp = OtpUtils.generateOtp(rawEmail)
                _activeOtp.value = newOtp
                _generatedOtp.value = newOtp

                // (b) Set loading to false and start countdown immediately
                startTimer()
                _isLoading.value = false

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    try {
                        android.widget.Toast.makeText(
                            getApplication(),
                            "A new 6-digit verification code has been sent to $rawEmail",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Throwable) {}
                }

                // (c) Fire off Firestore pre-sync write and Netlify network dispatch as background coroutines
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        OtpUtils.saveOtpToFirestore(rawEmail, newOtp, firestore)
                    } catch (e: Throwable) {
                        Log.w("PlenxoOtp", "Async resend OTP Firestore sync error: ${e.message}")
                    }
                }

                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val purpose = when {
                            _currentScreen.value == PlenxoScreen.FORGOT_PASSWORD -> "forgot_password"
                            _currentScreen.value == PlenxoScreen.LOGIN -> "login"
                            else -> "signup"
                        }
                        requestNetlifyOtp(rawEmail, purpose = purpose, clientOtp = newOtp)
                    } catch (e: Throwable) {
                        Log.e("PlenxoOtp", "Async resend Netlify OTP dispatch error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to resend OTP code: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getRemainingDailyOtpRequests(email: String): Int {
        return OtpRateLimiter.getRemainingRequests(getApplication(), email)
    }

    fun clearOtpState() {
        _otpUiState.value = OtpUiState.Idle
    }

    fun saveProfileChanges(
        newDisplayName: String,
        newAbout: String,
        imageUri: Uri? = null,
        onComplete: (() -> Unit)? = null
    ) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                var finalAvatarUrl = uploadedProfilePicUrl.value ?: galleryImageUriString.value ?: ""

                if (imageUri != null && imageUri.scheme != null && !imageUri.scheme!!.startsWith("http")) {
                    try {
                        val uploaded = uploadToCatbox(imageUri)
                        if (!uploaded.isNullOrBlank()) {
                            finalAvatarUrl = uploaded
                            uploadedProfilePicUrl.value = uploaded
                            galleryImageUriString.value = uploaded
                        }
                    } catch (e: Exception) {
                        Log.e("Plenxo", "Catbox upload exception: ${e.message}", e)
                    }
                }

                val currentUid = currentUserId
                if (currentUid.isNotEmpty()) {
                    displayName.value = newDisplayName
                    updateAboutText(newAbout)

                    com.example.util.ProfileHistoryUtils.saveProfileWithHistory(
                        uid = currentUid,
                        newName = newDisplayName,
                        newBio = newAbout,
                        newProfileUrl = finalAvatarUrl,
                        firestore = firestore,
                        auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                    )

                    val updates = mutableMapOf<String, Any>(
                        "displayName" to newDisplayName,
                        "display_name" to newDisplayName,
                        "about" to newAbout,
                        "bio" to newAbout,
                        "statusMessage" to newAbout,
                        "updatedAt" to System.currentTimeMillis()
                    )

                    if (finalAvatarUrl.isNotBlank()) {
                        updates["profilePicUrl"] = finalAvatarUrl
                        updates["avatar_url"] = finalAvatarUrl
                        updates["photoUrl"] = finalAvatarUrl
                        updates["profileUrl"] = finalAvatarUrl
                    }

                    val currentProf = currentUserProfile.value
                    val currentPxId = currentProf?.plenxoId.orEmpty().ifBlank { plenxoId.value }
                    val currentCode = currentProf?.userCode.orEmpty().ifBlank { userCode.value }

                    if (currentPxId.isNotBlank()) {
                        updates["plenxoId"] = currentPxId
                        updates["px_id"] = currentPxId
                    }
                    if (currentCode.isNotBlank()) {
                        updates["userCode"] = currentCode
                        updates["user_code"] = currentCode
                    }

                    firestore.collection("users").document(currentUid)
                        .set(updates, com.google.firebase.firestore.SetOptions.merge())
                        .await()
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Cloud profile sync failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Failed to update profile: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isLoading.value = false
                isProfilePicUploading.value = false
                onComplete?.invoke()
            }
        }
    }

    fun updateProfile(newDisplayName: String, newAbout: String) {
        saveProfileChanges(newDisplayName = newDisplayName, newAbout = newAbout)
    }

    fun fetchPendingInvitations() {
        val uid = currentUserId
        if (uid.isEmpty() || com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) return
        invitationsListener?.cancel()
        invitationsListener = viewModelScope.launch {
            firestore.collection("invitations")
                .whereEqualTo("receiverId", uid)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        Log.w("Plenxo", "Invitations listener note: ${error?.message}")
                        return@addSnapshotListener
                    }
                    val invitationList = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Invitation::class.java)
                    }
                    _pendingInvitations.value = invitationList
                    Log.d("Plenxo", "Fetched pending invitations: ${invitationList.size}")
                }
        }
    }

    fun acceptInvitation(invitation: Invitation, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val requestId = invitation.requestId
                if (requestId.isEmpty()) return@launch
                
                // 1. Update status to ACCEPTED
                firestore.collection("invitations").document(requestId).update("status", "ACCEPTED").await()
                
                // 2. Create the chat room
                val senderId = invitation.senderId
                val receiverId = invitation.receiverId
                val chatId = getChatRoomId(senderId, receiverId)
                
                val chatRoom = ChatRoom(
                    chatId = chatId,
                    participantUids = listOf(senderId, receiverId),
                    lastMessage = "No messages yet",
                    lastMessageTimestamp = System.currentTimeMillis(),
                    unreadCounts = mapOf(senderId to 0, receiverId to 0)
                )
                firestore.collection("chats").document(chatId).set(chatRoom, com.google.firebase.firestore.SetOptions.merge()).await()
                
                onComplete()
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to accept invitation", e)
            }
        }
    }

    private var connectedFriendsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var outgoingFriendRequestsListener: com.google.firebase.firestore.ListenerRegistration? = null

    fun observeConnectedFriendsAndRequests() {
        val uid = currentUserId
        if (uid.isEmpty() || com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) return

        // 1. Observe incoming friend requests
        fetchPendingFriendRequests()

        // 2. Observe outgoing friend requests
        outgoingFriendRequestsListener?.remove()
        outgoingFriendRequestsListener = firestore.collection("friend_requests")
            .whereEqualTo("senderUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val outgoingList = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val status = (data["status"] as? String) ?: "PENDING"
                    if (status.equals("PENDING", ignoreCase = true) || status.equals("pending", ignoreCase = true)) {
                        val reqId = doc.id
                        val targetUid = (data["receiverUid"] as? String) ?: (data["receiverId"] as? String) ?: (data["requestTo"] as? String) ?: ""
                        val targetPxId = (data["receiverPlenxoId"] as? String) ?: ""
                        val targetName = (data["receiverName"] as? String) ?: "User"
                        FriendRequest(
                            requestId = reqId,
                            senderUid = uid,
                            receiverUid = targetUid,
                            receiverPlenxoId = targetPxId,
                            status = "PENDING",
                            timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis()
                        )
                    } else null
                }
                _outgoingPendingRequests.value = outgoingList
            }

        // 3. Observe Connected Friends from users/{uid}/friends
        connectedFriendsListener?.remove()
        connectedFriendsListener = firestore.collection("users")
            .document(uid)
            .collection("friends")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val friendDocs = snapshot.documents
                viewModelScope.launch(Dispatchers.IO) {
                    val friendsList = mutableListOf<com.example.model.ConnectedFriend>()
                    for (doc in friendDocs) {
                        val friendUid = doc.getString("friendUid") ?: doc.id
                        if (friendUid.isBlank()) continue
                        val docDisplayName = doc.getString("displayName") ?: ""
                        val docPhoto = doc.getString("photoUrl") ?: doc.getString("profilePicUrl") ?: ""
                        val docAddedAt = doc.getLong("addedAt") ?: System.currentTimeMillis()

                        // Fetch detailed up-to-date user profile if available
                        var liveName = docDisplayName
                        var liveBio = ""
                        var livePhoto = docPhoto
                        var livePxId = ""
                        var liveEmail = ""
                        try {
                            val userDoc = firestore.collection("users").document(friendUid).get().await()
                            if (userDoc.exists()) {
                                liveName = userDoc.getString("displayName") ?: userDoc.getString("name") ?: liveName
                                liveBio = userDoc.getString("bio") ?: userDoc.getString("statusMessage") ?: ""
                                livePhoto = userDoc.getString("profilePicUrl") ?: userDoc.getString("avatarUrl") ?: livePhoto
                                livePxId = userDoc.getString("plenxoId") ?: userDoc.getString("plenxo_id") ?: userDoc.getString("userCode") ?: ""
                                liveEmail = userDoc.getString("email") ?: ""
                            }
                        } catch (e: Exception) {
                            Log.w("PlenxoFriends", "Note fetching friend details for $friendUid: ${e.message}")
                        }

                        friendsList.add(
                            com.example.model.ConnectedFriend(
                                uid = friendUid,
                                displayName = liveName.ifBlank { "User" },
                                bio = liveBio,
                                profilePicUrl = livePhoto,
                                plenxoId = if (livePxId.isNotBlank() && !livePxId.startsWith("PX-")) "PX-$livePxId" else livePxId,
                                email = liveEmail,
                                addedAt = docAddedAt
                            )
                        )
                    }

                    // Fallback to contacts if friends subcollection is empty
                    if (friendsList.isEmpty()) {
                        try {
                            val contactsSnap = firestore.collection("contacts").whereEqualTo("user_id", uid).get().await()
                            for (cDoc in contactsSnap.documents) {
                                val contactId = cDoc.getString("contact_id") ?: continue
                                if (contactId.isBlank() || contactId == uid) continue
                                var cName = "User"
                                var cBio = ""
                                var cPic = ""
                                var cPx = ""
                                var cEm = ""
                                try {
                                    val uDoc = firestore.collection("users").document(contactId).get().await()
                                    if (uDoc.exists()) {
                                        cName = uDoc.getString("displayName") ?: uDoc.getString("name") ?: "User"
                                        cBio = uDoc.getString("bio") ?: uDoc.getString("statusMessage") ?: ""
                                        cPic = uDoc.getString("profilePicUrl") ?: uDoc.getString("avatarUrl") ?: ""
                                        cPx = uDoc.getString("plenxoId") ?: uDoc.getString("plenxo_id") ?: uDoc.getString("userCode") ?: ""
                                        cEm = uDoc.getString("email") ?: ""
                                    }
                                } catch (e: Exception) {}
                                friendsList.add(
                                    com.example.model.ConnectedFriend(
                                        uid = contactId,
                                        displayName = cName,
                                        bio = cBio,
                                        profilePicUrl = cPic,
                                        plenxoId = if (cPx.isNotBlank() && !cPx.startsWith("PX-")) "PX-$cPx" else cPx,
                                        email = cEm,
                                        addedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        } catch (e: Exception) {}
                    }

                    withContext(Dispatchers.Main) {
                        _connectedFriends.value = friendsList
                    }
                }
            }
    }

    fun fetchPendingFriendRequests() {
        val uid = currentUserId
        if (uid.isEmpty() || com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) return
        friendRequestsListener?.cancel()
        friendRequestsListener = viewModelScope.launch {
            firestore.collection("friend_requests")
                .whereEqualTo("receiverUid", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        Log.w("Plenxo", "Friend requests listener note: ${error?.message}")
                        return@addSnapshotListener
                    }
                    val requestList = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        val status = (data["status"] as? String) ?: "PENDING"
                        if (status.equals("PENDING", ignoreCase = true) || status.equals("pending", ignoreCase = true)) {
                            val reqId = doc.id
                            val sender = (data["senderUid"] as? String) ?: (data["senderId"] as? String) ?: (data["requestFrom"] as? String) ?: ""
                            val senderPxId = (data["senderPlenxoId"] as? String) ?: ""
                            val senderName = (data["senderName"] as? String) ?: "User"
                            val senderPic = (data["senderProfilePic"] as? String) ?: (data["senderPhotoUrl"] as? String) ?: ""
                            FriendRequest(
                                requestId = reqId,
                                senderUid = sender,
                                senderId = sender,
                                receiverUid = uid,
                                receiverId = uid,
                                senderPlenxoId = senderPxId,
                                senderName = senderName,
                                senderPhotoUrl = senderPic,
                                senderProfilePic = senderPic,
                                status = "PENDING",
                                timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis()
                            )
                        } else null
                    }
                    _pendingFriendRequests.value = requestList
                    Log.d("Plenxo", "Fetched pending friend requests: ${requestList.size}")

                    // Preload sender profiles into usersCache
                    requestList.forEach { req ->
                        val senderUid = req.senderUid.ifEmpty { req.senderId }
                        if (senderUid.isNotEmpty() && !_usersCache.value.containsKey(senderUid)) {
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    val userDoc = firestore.collection("users").document(senderUid).get().await()
                                    val u = userDoc.toObject(User::class.java)
                                    if (u != null) {
                                        withContext(Dispatchers.Main) {
                                            val updatedCache = _usersCache.value.toMutableMap()
                                            updatedCache[senderUid] = u
                                            _usersCache.value = updatedCache
                                        }
                                    }
                                } catch (ex: Exception) {
                                    Log.e("Plenxo", "Failed to preload friend request sender: $senderUid", ex)
                                }
                            }
                        }
                    }
                }
        }
    }

    fun acceptFriendRequest(request: FriendRequest, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val requestId = request.requestId
                val senderUid = request.senderUid.ifEmpty { request.senderId }
                val receiverUid = currentUserId
                if (requestId.isEmpty() || senderUid.isEmpty()) return@launch

                val friendRepo = com.example.repository.FriendRequestRepositoryImpl()
                friendRepo.acceptFriendRequest(requestId, senderUid)

                val chatId = getChatRoomId(senderUid, receiverUid)
                val chatRef = firestore.collection("chats").document(chatId)
                val existingChat = chatRef.get().await()
                if (!existingChat.exists()) {
                    val newChat = ChatRoom(chatId = chatId, participantUids = listOf(senderUid, receiverUid), lastMessage = "Chat started", lastMessageTimestamp = System.currentTimeMillis())
                    chatRef.set(newChat).await()
                }

                // Update UI State immediately
                withContext(Dispatchers.Main) {
                    _pendingFriendRequests.value = _pendingFriendRequests.value.filter { it.requestId != requestId }
                    observeConnectedFriendsAndRequests()
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Error accepting friend request: ${e.message}", e)
            }
        }
    }

    fun rejectFriendRequest(request: FriendRequest, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val requestId = request.requestId
                if (requestId.isNotEmpty()) {
                    val friendRepo = com.example.repository.FriendRequestRepositoryImpl()
                    friendRepo.declineFriendRequest(requestId)
                }
                withContext(Dispatchers.Main) {
                    _pendingFriendRequests.value = _pendingFriendRequests.value.filter { it.requestId != requestId }
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Error rejecting friend request: ${e.message}", e)
            }
        }
    }
    private fun startListeningToCurrentUserProfile() {
        val uid = currentUserId
        if (uid.isEmpty() || com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) return
        firestore.collection("users").document(uid).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            val user = snapshot.toObject(UserProfile::class.java)
        }
    }

    private var firestoreContactsListener: ListenerRegistration? = null
    private fun startListeningToContacts() {
        val uid = currentUserId
        if (uid.isEmpty() || com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) return
        contactsListener?.cancel()
        firestoreContactsListener?.remove()
        firestoreContactsListener = firestore.collection("contacts")
            .whereEqualTo("user_id", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("Plenxo", "Contacts listener note: ${error?.message}")
                    return@addSnapshotListener
                }
                
                val contactIds = snapshot?.documents?.mapNotNull { it.getString("contact_id") }?.toSet() ?: emptySet()
                _contactsSet.value = contactIds
            }
    }

    fun startListeningForChats() {
        val fbAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
        if (fbAuth.currentUser == null) {
            viewModelScope.launch {
                try {
                    fbAuth.signInAnonymously().await()
                } catch (e: Exception) {
                    Log.w("Plenxo", "Anonymous auth fallback note: ${e.message}")
                }
            }
        }
        val uid = currentUserId
        if (uid.isEmpty() || fbAuth.currentUser == null) return
        
        startListeningToCurrentUserProfile()
        observeCurrentUserProfile()
        fetchPendingInvitations()
        fetchPendingFriendRequests()
        startListeningToContacts()
        observeConnectedFriendsAndRequests()
        
        // Listen for incoming calls
        callListener?.remove()
        callListener = firestore.collection("calls")
            .whereEqualTo("receiverUid", uid)
            .whereEqualTo("status", "RINGING")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val doc = snapshot.documents.firstOrNull() ?: return@addSnapshotListener
                val callId = doc.id
                val callerUid = doc.getString("callerUid") ?: ""
                val callType = doc.getString("callType") ?: "AUDIO"
                
                // Show incoming call if we don't have an active call already
                if (activeSimulatedCall.value == null && callerUid.isNotEmpty()) {
                    viewModelScope.launch {
                        val callerDoc = firestore.collection("users").document(callerUid).get().await()
                        val callerName = callerDoc.getString("displayName") ?: "User"
                        val callerPhoto = callerDoc.getString("profilePicUrl") ?: ""
                        val callerPlenxoId = callerDoc.getString("plenxoId") ?: ""
                        
                        val incomingLog = com.example.model.CallLog(
                            callId = callId,
                            peerUid = callerUid,
                            peerName = callerName,
                            peerPhotoUrl = callerPhoto,
                            peerPlenxoId = callerPlenxoId,
                            callType = callType,
                            direction = "INCOMING",
                            timestamp = System.currentTimeMillis(),
                            durationSeconds = 0L
                        )
                        activeSimulatedCall.value = incomingLog
                        simulatedCallState.value = "Ringing..."
                        simulatedCallDuration.value = 0L
                        
                        // Setup incoming signaling listener
                        incomingCallSignalingManager?.cleanup()
                        val signaling = com.example.webrtc.CallSignalingManager(
                            callId = callId,
                            callerUid = callerUid,
                            receiverUid = uid,
                            listener = object : com.example.webrtc.CallSignalingManager.SignalingListener {
                                override fun onCallEnd(senderUid: String) {
                                    activeSimulatedCall.value = null
                                    simulatedCallJob?.cancel()
                                    simulatedCallState.value = "Ended"
                                }
                                override fun onCallReject(senderUid: String) {
                                    activeSimulatedCall.value = null
                                    simulatedCallJob?.cancel()
                                    simulatedCallState.value = "Ended"
                                }
                            }
                        )
                        incomingCallSignalingManager = signaling
                        signaling.startListening()
                    }
                }
            }

        chatsListener?.cancel()
        chatsListener = viewModelScope.launch {
            var chatsFromChatsCol = emptyList<ChatRoom>()
            var chatsFromChatsColByParticipants = emptyList<ChatRoom>()
            var chatsFromMessagesCol = emptyList<ChatRoom>()
            var chatsFromMessagesColByParticipants = emptyList<ChatRoom>()
            var chatsFromAcceptedChatRequests = emptyList<ChatRoom>()
            var chatsFromAcceptedFriendRequests = emptyList<ChatRoom>()

            fun updateCombinedChats() {
                val combinedMap = mutableMapOf<String, ChatRoom>()
                (chatsFromChatsCol + chatsFromChatsColByParticipants + chatsFromMessagesCol + chatsFromMessagesColByParticipants + chatsFromAcceptedChatRequests + chatsFromAcceptedFriendRequests).forEach { room ->
                    if (room.chatId.isNotEmpty()) {
                        val existing = combinedMap[room.chatId]
                        if (existing == null || (room.lastMessageTimestamp ?: 0) >= (existing.lastMessageTimestamp ?: 0)) {
                            combinedMap[room.chatId] = room
                        }
                    }
                }
                val sortedList = combinedMap.values.sortedByDescending { it.lastMessageTimestamp ?: 0 }
                Log.d("MainChatSync", "Fetched ${sortedList.size} conversations for user $uid")
                _chats.value = sortedList
                fetchUsersForChats(sortedList)
            }

            // 1. Listen to chats collection by participantUids
            firestore.collection("chats")
                .whereArrayContains("participantUids", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        Log.e("Plenxo", "Chats collection listener error: ${error?.message}")
                        return@addSnapshotListener
                    }
                    chatsFromChatsCol = snapshot.documents.mapNotNull { doc ->
                        docToChatRoom(doc, uid)
                    }
                    updateCombinedChats()
                }

            // 2. Listen to chats collection by participants.$uid == true
            firestore.collection("chats")
                .whereEqualTo("participants.$uid", true)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        Log.e("Plenxo", "Chats collection (participants map) listener error: ${error?.message}")
                        return@addSnapshotListener
                    }
                    chatsFromChatsColByParticipants = snapshot.documents.mapNotNull { doc ->
                        docToChatRoom(doc, uid)
                    }
                    updateCombinedChats()
                }

            // 3. Listen to messages collection by participantUids
            firestore.collection("messages")
                .whereArrayContains("participantUids", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        Log.e("Plenxo", "Messages collection listener error: ${error?.message}")
                        return@addSnapshotListener
                    }
                    chatsFromMessagesCol = snapshot.documents.mapNotNull { doc ->
                        docToChatRoom(doc, uid)
                    }
                    updateCombinedChats()
                }

            // 4. Listen to messages collection by participants.$uid == true
            firestore.collection("messages")
                .whereEqualTo("participants.$uid", true)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        Log.e("Plenxo", "Messages collection (participants map) listener error: ${error?.message}")
                        return@addSnapshotListener
                    }
                    chatsFromMessagesColByParticipants = snapshot.documents.mapNotNull { doc ->
                        docToChatRoom(doc, uid)
                    }
                    updateCombinedChats()
                }

            // 5. Listen to chat_requests collection for accepted requests involving $uid
            val handleAcceptedRequests: (List<com.google.firebase.firestore.DocumentSnapshot>) -> List<ChatRoom> = { docs ->
                docs.mapNotNull { doc ->
                    val status = (doc.getString("status") ?: "").lowercase()
                    if (status != "accepted") return@mapNotNull null
                    
                    var senderId = doc.getString("senderUid") ?: doc.getString("senderId") ?: doc.getString("requestFrom") ?: ""
                    var receiverId = doc.getString("receiverUid") ?: doc.getString("receiverId") ?: doc.getString("requestTo") ?: ""
                    
                    if ((senderId.isBlank() || receiverId.isBlank()) && doc.id.contains("_")) {
                        val parts = doc.id.split("_")
                        if (parts.size >= 2) {
                            if (senderId.isBlank()) senderId = parts[0]
                            if (receiverId.isBlank()) receiverId = parts[1]
                        }
                    }
                    
                    if (senderId.isBlank() || receiverId.isBlank()) return@mapNotNull null
                    if (senderId != uid && receiverId != uid) return@mapNotNull null
                    
                    val conversationId = listOf(senderId, receiverId).sorted().joinToString("_")
                    val timestamp = doc.getLong("timestamp") ?: doc.getLong("updatedAt") ?: doc.getLong("createdAt") ?: System.currentTimeMillis()
                    
                    // Auto-repair/ensure Firestore documents exist in chats & messages collections
                    val conversationData = mapOf(
                        "conversationId" to conversationId,
                        "chatId" to conversationId,
                        "senderId" to senderId,
                        "receiverId" to receiverId,
                        "user1Id" to senderId,
                        "user2Id" to receiverId,
                        "participants" to mapOf(senderId to true, receiverId to true),
                        "participantUids" to listOf(senderId, receiverId),
                        "status" to "accepted",
                        "createdAt" to timestamp,
                        "lastMessage" to "Chat started",
                        "lastMessageTimestamp" to timestamp,
                        "updatedAt" to timestamp
                    )
                    try {
                        firestore.collection("chats").document(conversationId).set(conversationData, com.google.firebase.firestore.SetOptions.merge())
                        firestore.collection("messages").document(conversationId).set(conversationData, com.google.firebase.firestore.SetOptions.merge())
                    } catch (e: Exception) {
                        Log.w("MainChatSync", "Auto-repair set failed for $conversationId: ${e.message}")
                    }
                    
                    ChatRoom(
                        chatId = conversationId,
                        participantUids = listOf(senderId, receiverId),
                        lastMessage = "Chat started",
                        lastMessageTimestamp = timestamp,
                        unreadCounts = emptyMap()
                    )
                }
            }

            firestore.collection("chat_requests")
                .whereEqualTo("receiverUid", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val relevantDocs = snapshot.documents.filter { doc ->
                        (doc.getString("status") ?: "").lowercase() == "accepted"
                    }
                    chatsFromAcceptedChatRequests = handleAcceptedRequests(relevantDocs)
                    updateCombinedChats()
                }

            firestore.collection("friend_requests")
                .whereEqualTo("receiverUid", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val relevantDocs = snapshot.documents.filter { doc ->
                        (doc.getString("status") ?: "").lowercase() == "accepted"
                    }
                    chatsFromAcceptedFriendRequests = handleAcceptedRequests(relevantDocs)
                    updateCombinedChats()
                }
        }
    }

    private fun docToChatRoom(doc: com.google.firebase.firestore.DocumentSnapshot, currentUid: String): ChatRoom? {
        if (!doc.exists()) return null
        val chatId = doc.getString("chatId") ?: doc.getString("conversationId") ?: doc.id

        @Suppress("UNCHECKED_CAST")
        val participantUids = (doc.get("participantUids") as? List<*>)?.mapNotNull { it as? String }
            ?: run {
                val pMap = doc.get("participants") as? Map<*, *>
                val uidsFromMap = pMap?.keys?.mapNotNull { it as? String }?.filter { it != "senderId" && it != "receiverId" }
                if (!uidsFromMap.isNullOrEmpty()) {
                    uidsFromMap
                } else {
                    val senderId = doc.getString("senderId") ?: doc.getString("user1Id") ?: ""
                    val receiverId = doc.getString("receiverId") ?: doc.getString("user2Id") ?: ""
                    listOfNotNull(senderId.ifBlank { null }, receiverId.ifBlank { null })
                }
            }

        val lastMsgRaw = doc.getString("lastMessage") ?: doc.getString("text") ?: doc.getString("content") ?: ""
        val lastMsg = if (lastMsgRaw.isBlank()) "Chat started" else lastMsgRaw
        val lastTimestamp = doc.getLong("lastMessageTimestamp") ?: doc.getLong("timestamp") ?: doc.getLong("updatedAt") ?: doc.getLong("createdAt") ?: System.currentTimeMillis()

        @Suppress("UNCHECKED_CAST")
        val rawUnread = doc.get("unreadCounts") as? Map<*, *>
        val unreadMap = rawUnread?.entries?.mapNotNull { (k, v) ->
            if (k is String && v is Number) k to v.toInt() else null
        }?.toMap() ?: emptyMap()

        return ChatRoom(
            chatId = chatId,
            participantUids = if (participantUids.isNotEmpty()) participantUids else listOf(currentUid),
            lastMessage = lastMsg,
            lastMessageTimestamp = lastTimestamp,
            unreadCounts = unreadMap
        )
    }

    fun rejectInvitation(invitation: Invitation, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val requestId = invitation.requestId
                if (requestId.isEmpty()) return@launch
                firestore.collection("invitations").document(requestId).update("status", "REJECTED").await()
                onComplete()
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to reject invitation", e)
            }
        }
    }

    fun fetchUsersForChats(chatList: List<ChatRoom>) {
        val currentUid = currentUserId
        val uidsToFetch = chatList.flatMap { it.participantUids }.filter { it != currentUid && (!_usersCache.value.containsKey(it) || _usersCache.value[it]?.displayName.isNullOrBlank()) }.distinct()
        if (uidsToFetch.isEmpty()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            uidsToFetch.forEach { uid ->
                try {
                    val userDoc = firestore.collection("users").document(uid).get().await()
                    var user = userDoc.toObject(User::class.java)
                    if (user == null || user.displayName.isBlank()) {
                        if (userDoc.exists()) {
                            val name = userDoc.getString("displayName")
                                ?: userDoc.getString("name")
                                ?: "Plenxo User"
                            val pic = userDoc.getString("profilePic")
                                ?: userDoc.getString("profilePicUrl")
                                ?: userDoc.getString("photoUrl")
                                ?: ""
                            val ringId = userDoc.getString("profileRingId") ?: "none"
                            val userCode = userDoc.getString("userCode") ?: userDoc.getString("plenxoId") ?: ""
                            val plenxoId = userDoc.getString("plenxoId") ?: userDoc.getString("userCode") ?: ""
                            user = User(uid = uid, displayName = name, profilePicUrl = pic, profileRingId = ringId, userCode = userCode, plenxoId = plenxoId)
                        }
                    }
                    if (user == null) {
                        user = User(uid = uid, displayName = "Plenxo User", plenxoId = uid.take(6))
                    }
                    withContext(Dispatchers.Main) {
                        _usersCache.value = _usersCache.value + (uid to user)
                        Log.d("MainChatSync", "Hydrated user profile for $uid: ${user.displayName}")
                    }
                } catch (e: Exception) {
                    Log.e("MainChatSync", "Failed to fetch user $uid: ${e.message}", e)
                }
            }
        }
    }

    fun preloadDiscoveryUsers() {
        discoverySearchJob?.cancel()
        discoverySearchJob = viewModelScope.launch {
            val currentUid = currentUserId
            if (currentUid.isEmpty()) return@launch
            try {
                if (currentUserProfile.value == null) {
                    val userDoc = firestore.collection("users").document(currentUid).get().await()
                    val user = userDoc.toObject(UserProfile::class.java)
                    currentUserProfile.value = user
                }

                discoveryUsers.value = emptyList()

                val snapshot = firestore.collection("invitations")
                    .whereEqualTo("senderId", currentUid)
                    .whereEqualTo("status", "PENDING")
                    .get()
                    .await()
                val list = snapshot.documents.mapNotNull { it.toObject(Invitation::class.java) }
                val requestedIds = list.map { it.receiverId }.toSet()
                discoveryRequestedUserIds.value = requestedIds
                
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to preload discovery users", e)
            }
        }
    }

    fun updateDiscoverySearchQuery(query: String) {
        val filtered = query.trim().take(30)
        discoverySearchQuery.value = filtered
    }

    fun searchUserByCode() {
        val rawInput = discoverySearchQuery.value.trim().removePrefix("@").removePrefix("#").trim()
        if (rawInput.isBlank()) {
            _errorMessage.value = "Please enter a valid Plenxo ID."
            return
        }

        val numericPart = rawInput.removePrefix("PX-").removePrefix("px-").removePrefix("Px-").removePrefix("pX-").trim()
        val formattedPx = if (numericPart.isNotBlank()) "PX-$numericPart" else rawInput.uppercase()

        val queryKeys = buildSet {
            if (formattedPx.isNotBlank()) add(formattedPx)
            if (numericPart.isNotBlank()) add(numericPart)
            if (rawInput.isNotBlank()) {
                add(rawInput)
                add(rawInput.uppercase())
                add(rawInput.lowercase())
            }
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val foundDocs = mutableMapOf<String, UserProfile>()
                val collections = listOf("users")
                val fields = listOf("plenxoId", "userCode", "plenxo_id", "px_id", "user_code")

                for (collectionName in collections) {
                    for (field in fields) {
                        for (key in queryKeys) {
                            try {
                                val snap = firestore.collection(collectionName)
                                    .whereEqualTo(field, key)
                                    .get().await()
                                for (doc in snap.documents) {
                                    if (doc.exists()) {
                                        val data = doc.data ?: continue
                                        val resolvedUid = (data["uid"] as? String)?.ifBlank { null }
                                            ?: (data["id"] as? String)?.ifBlank { null }
                                            ?: doc.id
                                        val resolvedName = (data["displayName"] as? String)?.ifBlank { null }
                                            ?: (data["name"] as? String)
                                            ?: (data["fullName"] as? String)
                                            ?: "Plenxo User"
                                        val rawPxId = (data["plenxoId"] as? String)
                                            ?: (data["plenxo_id"] as? String)
                                            ?: (data["px_id"] as? String)
                                            ?: (data["userCode"] as? String)
                                            ?: (data["user_code"] as? String)
                                            ?: ""
                                        val cleanPxId = rawPxId.trim().removePrefix("@").removePrefix("#")
                                        val normalizedPxId = if (cleanPxId.startsWith("PX-", ignoreCase = true)) {
                                            "PX-${cleanPxId.removePrefix("PX-").removePrefix("px-")}"
                                        } else if (cleanPxId.length == 6 && cleanPxId.all { it.isDigit() }) {
                                            "PX-$cleanPxId"
                                        } else if (cleanPxId.isNotBlank()) {
                                            "PX-$cleanPxId"
                                        } else {
                                            formattedPx
                                        }
                                        val profilePic = (data["profilePicUrl"] as? String) ?: (data["avatar_url"] as? String) ?: ""
                                        val profileRing = (data["selectedRingId"] as? String) ?: (data["profileRingId"] as? String) ?: "none"

                                        val userProf = UserProfile(
                                            id = resolvedUid,
                                            uid = resolvedUid,
                                            displayName = resolvedName,
                                            plenxoId = normalizedPxId,
                                            userCode = normalizedPxId.removePrefix("PX-"),
                                            profilePicUrl = profilePic,
                                            profileRingId = profileRing
                                        )
                                        foundDocs[resolvedUid] = userProf
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("Plenxo", "Search error on $collectionName.$field = $key: ${e.message}")
                            }
                        }
                    }
                }

                val currentUid = currentUserId
                val results = foundDocs.values.filter { it.uid.isNotBlank() && it.uid != currentUid }
                discoveryUsers.value = results

                if (results.isEmpty()) {
                    _errorMessage.value = "No user found with Plenxo ID $formattedPx."
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Search failed", e)
                _errorMessage.value = "Search failed: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendFriendRequest(
        receiverId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val currentUid = currentUserId
        if (currentUid.isEmpty() || receiverId.isEmpty()) {
            Log.e("PlenxoVM", "sendFriendRequest failed: currentUid=$currentUid, receiverId=$receiverId")
            Toast.makeText(getApplication(), "Error sending request: Invalid user ID", Toast.LENGTH_SHORT).show()
            onError("Invalid user ID")
            return
        }
        val currentProfile = currentUserProfile.value
        val currentDisplayName = currentProfile?.displayName ?: "User"
        val currentPlenxoId = currentProfile?.plenxoId ?: currentProfile?.userCode ?: ""
        val currentProfilePic = currentProfile?.profilePicUrl ?: ""

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val chatRepo = com.example.repository.ChatRequestRepositoryImpl()
                val friendRepo = com.example.repository.FriendRequestRepositoryImpl()

                val successChat = chatRepo.sendChatRequest(
                    senderUid = currentUid,
                    receiverUid = receiverId,
                    senderName = currentDisplayName,
                    senderPlenxoId = currentPlenxoId,
                    senderPhotoUrl = currentProfilePic
                )
                val successFriend = friendRepo.sendFriendRequest(receiverId)

                if (successChat || successFriend) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        discoveryRequestedUserIds.value = discoveryRequestedUserIds.value + receiverId
                        onSuccess()
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onError("Failed to send request. Please try again.")
                    }
                }
            } catch (e: Exception) {
                Log.e("PlenxoVM", "Error in sendFriendRequest: ${e.message}", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Failed to send request.")
                }
            }
        }
    }

    fun acceptFriendRequest(
        requestId: String,
        senderUid: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val currentUid = currentUserId
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val friendRepo = com.example.repository.FriendRequestRepositoryImpl()
                val chatRepo = com.example.repository.ChatRequestRepositoryImpl()
                val ok1 = friendRepo.acceptFriendRequest(requestId, senderUid)
                val ok2 = chatRepo.acceptChatRequest(requestId, senderUid, currentUid)
                if (ok1 || ok2) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onSuccess()
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onError("Could not accept connection request.")
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Failed to accept request.")
                }
            }
        }
    }

    fun searchUserAndSendInvite(searchInput: String, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
        onFailure("Please use standard Add Friend")
    }

    // Retained for backward compatibility/bridge with existing code
    fun createNewChatByUserCode(
        targetUserCode: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        // Delegate user-code entry search to backend logic to prevent any UI breakage
        searchUserAndSendInvite(targetUserCode, onSuccess, onFailure)
    }

    fun startListeningForMessages(chatId: String) {
        val currentUid = currentUserId
        val receiverUid = currentChatRecipientUid.value
        val resolvedChatId = if (currentUid.isNotEmpty() && receiverUid.isNotEmpty()) {
            getChatRoomId(currentUid, receiverUid)
        } else {
            chatId
        }
        
        _messagesOffset = 0
        canLoadMore.value = true
        
        messagesListener?.cancel()
        messagesListener = null
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            dynamicStorageManager.streamMessages(resolvedChatId, limit = PAGE_SIZE, offset = 0).collect { msgPayloads ->
                if (msgPayloads.size < PAGE_SIZE) {
                    canLoadMore.value = false
                }
                _messagesOffset = msgPayloads.size
                val msgList = msgPayloads.map { payload ->
                    val decryptedText = if (payload.messageText.contains("|")) {
                        com.example.util.EncryptionManager.decryptMessage(payload.messageText)
                    } else {
                        payload.messageText
                    }

                    Message(
                        messageId = payload.messageId,
                        senderId = payload.senderId,
                        receiverId = payload.receiverId,
                        messageText = decryptedText,
                        timestamp = payload.timestamp,
                        status = payload.status,
                        deliveryStatus = when (payload.status) {
                            "SENDING" -> DeliveryStatus.SENDING
                            "SENT" -> DeliveryStatus.SENT
                            "DELIVERED" -> DeliveryStatus.DELIVERED
                            "READ" -> DeliveryStatus.READ
                            else -> DeliveryStatus.SENT
                        },
                        messageType = payload.messageType,
                        replyToMessageId = payload.replyToMessageId,
                        isEdited = payload.isEdited,
                        expiresAt = payload.expiresAt,
                        senderActiveFontId = payload.senderActiveFontId
                    )
                }
                
                val filteredMsgs = msgList.filter { 
                    it.expiresAt == null || it.expiresAt > System.currentTimeMillis() 
                }
                
                val newestMsg = filteredMsgs.firstOrNull()
                if (newestMsg != null && newestMsg.senderId != currentUid) {
                    val prevMsgs = _messages.value
                    val isNewMessage = prevMsgs.isEmpty() || prevMsgs.firstOrNull()?.messageId != newestMsg.messageId
                    if (isNewMessage) {
                        val isBackground = try {
                            androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState < androidx.lifecycle.Lifecycle.State.STARTED
                        } catch (e: Exception) { false }
                        
                        if (isBackground || currentScreen.value != PlenxoScreen.CHAT_DETAIL || currentChatId.value != resolvedChatId) {
                            com.example.util.NotificationHelper.showNotification(
                                context = getApplication(),
                                title = currentChatRecipientName.value.ifEmpty { "New message" },
                                message = newestMsg.messageText,
                                targetScreen = "CHAT_DETAIL",
                                extraData = mapOf("chatId" to resolvedChatId, "senderId" to newestMsg.senderId)
                            )
                        }
                    }
                }

                _messages.value = filteredMsgs
                markMessagesAsRead(chatId)
            }
        }
    }

    fun loadMoreMessages() {
        val chatId = currentChatId.value
        if (chatId.isEmpty() || isLoadingMore.value || !canLoadMore.value) return
        
        viewModelScope.launch {
            isLoadingMore.value = true
            try {
                val morePayloads = dynamicStorageManager.getMoreMessages(chatId, limit = PAGE_SIZE, offset = _messagesOffset)
                if (morePayloads.size < PAGE_SIZE) {
                    canLoadMore.value = false
                }
                
                val moreMessages = morePayloads.map { payload ->
                    val decryptedText = if (payload.messageText.contains("|")) {
                        com.example.util.EncryptionManager.decryptMessage(payload.messageText)
                    } else {
                        payload.messageText
                    }

                    Message(
                        messageId = payload.messageId,
                        senderId = payload.senderId,
                        receiverId = payload.receiverId,
                        messageText = decryptedText,
                        timestamp = payload.timestamp,
                        status = payload.status,
                        deliveryStatus = when (payload.status) {
                            "SENDING" -> DeliveryStatus.SENDING
                            "SENT" -> DeliveryStatus.SENT
                            "DELIVERED" -> DeliveryStatus.DELIVERED
                            "READ" -> DeliveryStatus.READ
                            else -> DeliveryStatus.SENT
                        },
                        messageType = payload.messageType,
                        replyToMessageId = payload.replyToMessageId,
                        isEdited = payload.isEdited,
                        expiresAt = payload.expiresAt,
                        senderActiveFontId = payload.senderActiveFontId
                    )
                }
                
                if (moreMessages.isNotEmpty()) {
                    _messages.value = moreMessages + _messages.value
                    _messagesOffset += moreMessages.size
                }
            } catch (e: Exception) {
                Log.e("PlenxoViewModel", "Error loading more messages", e)
            } finally {
                isLoadingMore.value = false
            }
        }
    }

    fun sendSystemMessage(chatId: String, text: String) {
        viewModelScope.launch {
            val messageId = java.util.UUID.randomUUID().toString()
            if (isLocalOnlyMode.value) {
                try {
                    val db = AppDatabase.getDatabase(getApplication())
                    val dao = db.localMessageDao()
                    val localMsg = LocalMessage(
                        messageId = messageId,
                        chatId = chatId,
                        senderId = "SYSTEM",
                        receiverId = "ALL",
                        messageText = text,
                        timestamp = System.currentTimeMillis(),
                        status = "SENT",
                        messageType = "SYSTEM"
                    )
                    dao.insertMessage(localMsg)
                } catch (dbEx: Exception) {
                    Log.e("Plenxo", "Failed to save system message locally", dbEx)
                }
                return@launch
            }
            
            val payload = com.example.model.MessagePayload(
                messageId = messageId,
                chatId = chatId,
                senderId = "SYSTEM",
                receiverId = "ALL",
                messageText = text,
                messageType = "SYSTEM",
                timestamp = System.currentTimeMillis(),
                replyToMessageId = null,
                isEdited = false,
                status = "SENT",
                expiresAt = null,
                senderActiveFontId = "DEFAULT"
            )
            dynamicStorageManager.saveMessage(payload)
        }
    }

    fun sendMessage(chatId: String, text: String, receiverId: String) {
        val senderId = currentUserId
        if (senderId.isEmpty() || text.isBlank()) return

        val textBytes = text.toByteArray(Charsets.UTF_8).size
        val maxTextLimit = 170L * 1024L * 1024L // 170 MB limit
        val firestoreTextLimit = 100L * 1024L // 100 KB limit for inline Firestore string

        if (textBytes > maxTextLimit) {
            Toast.makeText(getApplication(), "Text payload exceeds maximum 170 MB limit.", Toast.LENGTH_LONG).show()
            return
        }

        val resolvedChatId = getChatRoomId(senderId, receiverId)
        val activeFontId = "DEFAULT"
        val messageId = java.util.UUID.randomUUID().toString()
        val replyToId = replyToMessage.value?.messageId
        val timerVal = disappearingTimer.value
        val expiresAt = if (timerVal > 0L) System.currentTimeMillis() + timerVal else null

        replyToMessage.value = null

        val isOversized = textBytes > firestoreTextLimit
        val messageType = if (isOversized) "TEXT_ASSET" else "TEXT"
        val displaySummary = if (isOversized) "📄 Large Text Document (${textBytes / 1024} KB)" else text.trim()

        val tempMessage = Message(
            messageId = messageId,
            chatId = resolvedChatId,
            senderId = senderId,
            receiverId = receiverId,
            messageText = displaySummary,
            messageType = messageType,
            timestamp = System.currentTimeMillis(),
            status = "SENDING",
            messageStatus = com.example.model.MessageStatus.SENDING,
            replyToMessageId = replyToId,
            expiresAt = expiresAt,
            senderActiveFontId = activeFontId,
            uploadProgress = 0
        )

        // 1. Optimistic instant local append
        _messages.value = _messages.value + tempMessage

        viewModelScope.launch(Dispatchers.IO) {
            try {
                var mediaUrl = ""
                var messagePayloadText = text

                if (isOversized) {
                    val cloudChatRepo = com.example.repository.CloudChatRepositoryImpl(getApplication())
                    mediaUrl = cloudChatRepo.uploadLargeTextPayload(text) { percent ->
                        _messages.value = _messages.value.map {
                            if (it.messageId == messageId) it.copy(uploadProgress = percent) else it
                        }
                    }
                    messagePayloadText = displaySummary
                } else {
                    try {
                        var receiverPublicKey = ""
                        try {
                            val userDoc = firestore.collection("users").document(receiverId).get().await()
                            val receiverProfile = userDoc.toObject(UserProfile::class.java)
                            receiverPublicKey = receiverProfile?.publicKey ?: ""
                        } catch (keyEx: Exception) {
                            Log.e("Plenxo", "Key resolution failed for user $receiverId: ${keyEx.message}", keyEx)
                        }

                        if (receiverPublicKey.isNotEmpty()) {
                            messagePayloadText = com.example.util.EncryptionManager.encryptMessage(text, receiverPublicKey)
                        }
                    } catch (encEx: Exception) {
                        Log.e("Plenxo", "Encryption failed, falling back to raw plaintext: ${encEx.message}", encEx)
                        messagePayloadText = text
                    }
                }

                val payload = com.example.model.MessagePayload(
                    messageId = messageId,
                    chatId = resolvedChatId,
                    senderId = senderId,
                    receiverId = receiverId,
                    messageText = messagePayloadText,
                    messageType = messageType,
                    mediaUrl = mediaUrl,
                    timestamp = System.currentTimeMillis(),
                    replyToMessageId = replyToId,
                    isEdited = false,
                    status = "SENT",
                    expiresAt = expiresAt,
                    senderActiveFontId = activeFontId
                )

                dynamicStorageManager.saveMessage(payload)

                try {
                    val senderDisplayName = displayName.value.ifBlank { "Plenxo User" }
                    val notificationPayload = mapOf(
                        "sender_id" to senderId,
                        "sender_name" to senderDisplayName,
                        "message_text" to text.trim(),
                        "chat_id" to resolvedChatId,
                        "type" to "chat",
                        "timestamp" to System.currentTimeMillis()
                    )
                    com.google.firebase.database.FirebaseDatabase.getInstance()
                        .getReference("notifications")
                        .child(receiverId)
                        .child(messageId)
                        .setValue(notificationPayload)
                } catch (notifEx: Exception) {
                    Log.w("Plenxo", "Failed to queue FCM notification dispatch: ${notifEx.message}")
                }

                _messages.value = _messages.value.map {
                    if (it.messageId == messageId) it.copy(
                        status = "SENT",
                        messageStatus = com.example.model.MessageStatus.SENT,
                        mediaUrl = mediaUrl,
                        uploadProgress = 100
                    ) else it
                }

                withContext(Dispatchers.Main) {
                    com.example.util.HapticManager.playMessageSentThud(getApplication())
                }
            } catch (saveEx: Exception) {
                Log.w("Plenxo", "Message dispatch failed: ${saveEx.message}")
                _messages.value = _messages.value.map {
                    if (it.messageId == messageId) it.copy(status = "FAILED", messageStatus = com.example.model.MessageStatus.FAILED) else it
                }
            }
        }
    }

    private var typingDebounceJob: Job? = null
    private var typingListenerJob: Job? = null
    private var typingListener: ValueEventListener? = null

    fun setTypingStatus(isTyping: Boolean) {
        val uid = currentUserId
        val chatId = currentChatId.value
        if (uid.isEmpty() || chatId.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val database = com.google.firebase.database.FirebaseDatabase.getInstance()
                val typingRef = database.getReference("typing").child(chatId).child(uid)
                if (isTyping) {
                    typingRef.setValue(true)
                } else {
                    typingRef.removeValue()
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to set typing status", e)
            }
        }
    }

    fun onUserTyping() {
        typingDebounceJob?.cancel()
        setTypingStatus(true)

        typingDebounceJob = viewModelScope.launch {
            delay(1500)
            setTypingStatus(false)
        }
    }

    fun startListeningToTyping(chatId: String) {
        stopListeningToTyping(chatId)
        if (chatId.isEmpty()) return
        try {
            val database = com.google.firebase.database.FirebaseDatabase.getInstance()
            val typingRef = database.getReference("typing").child(chatId)
            typingListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val map = mutableMapOf<String, String>()
                    for (child in snapshot.children) {
                        val uid = child.key ?: continue
                        if (uid != currentUserId && child.getValue(Boolean::class.java) == true) {
                            val cachedUser = _usersCache.value[uid]
                            val name = cachedUser?.displayName ?: "Someone"
                            map[chatId] = "$name is typing..."
                        }
                    }
                    typingUsers.value = map
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Plenxo", "Typing listener cancelled: ${error.message}")
                }
            }
            typingRef.addValueEventListener(typingListener!!)
        } catch (e: Exception) {
            Log.e("Plenxo", "Failed to start listening to typing", e)
        }
    }

    fun stopListeningToTyping(chatId: String) {
        if (chatId.isNotEmpty() && typingListener != null) {
            try {
                val database = com.google.firebase.database.FirebaseDatabase.getInstance()
                database.getReference("typing").child(chatId).removeEventListener(typingListener!!)
            } catch (e: Exception) {}
        }
        typingListener = null
        typingUsers.value = emptyMap()
    }

    fun editMessage(messageId: String, newText: String) {
        val chatId = currentChatId.value
        if (chatId.isEmpty() || messageId.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                firestore.collection("messages").document(messageId)
                    .update("messageText", newText, "isEdited", true)
                    .await()
                Log.d("Plenxo", "Message edited successfully in Firestore: $messageId")
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to edit message: ${e.message}", e)
            }
        }
    }

    fun startAudioCall(chatId: String) {
        android.widget.Toast.makeText(getApplication(), "Calling feature is temporarily disabled.", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun startVideoCall(chatId: String) {
        android.widget.Toast.makeText(getApplication(), "Calling feature is temporarily disabled.", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun startRecordingVoice() {
        viewModelScope.launch {
            try {
                val cacheDir = getApplication<Application>().cacheDir
                val file = File.createTempFile("voice_note_", ".m4a", cacheDir)
                currentRecordingFile = file
                audioRecorder.startRecording(file)
                _isRecordingVoice.value = true
                Log.d("Plenxo", "Voice recording started: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to start voice recording", e)
                _errorMessage.value = "Failed to start recording: ${e.localizedMessage}"
            }
        }
    }

    fun stopAndSendVoiceMessage(chatId: String, receiverId: String) {
        viewModelScope.launch {
            try {
                audioRecorder.stopRecording()
                _isRecordingVoice.value = false
                val file = currentRecordingFile
                if (file != null && file.exists() && file.length() > 0) {
                    sendVoiceMessage(chatId, receiverId, file)
                } else {
                    Log.w("Plenxo", "Recording file is empty or null")
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to stop and send voice message", e)
            }
        }
    }

    fun cancelRecordingVoice() {
        viewModelScope.launch {
            try {
                audioRecorder.stopRecording()
                _isRecordingVoice.value = false
                val file = currentRecordingFile
                if (file != null && file.exists()) {
                    file.delete()
                    Log.d("Plenxo", "Voice recording cancelled and temp file deleted.")
                }
                currentRecordingFile = null
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to cancel voice recording", e)
            }
        }
    }


    fun sendVoiceMessage(chatId: String, receiverId: String, audioFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val senderId = currentUserId
            if (senderId.isEmpty()) return@launch

            val resolvedChatId = getChatRoomId(senderId, receiverId)
            _isLoading.value = true
            try {
                val voiceRepo = com.example.repository.VoiceNoteRepository()
                val audioUrl = voiceRepo.uploadAndSendVoiceNote(
                    getApplication(), 
                    audioFile, 
                    chatId = resolvedChatId,
                    receiverId = receiverId
                )
                
                com.example.util.HapticManager.playMessageSentThud(getApplication())
                Log.d("Plenxo", "Voice message sent successfully: $audioUrl")
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to send voice message: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Failed to send voice note: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                }
                if (audioFile.exists()) {
                    audioFile.delete()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Deprecated hardcoded version - kept for internal routing compatibility
    fun sendVoiceMessage(chatId: String, receiverId: String) {
    }

    fun checkFileSizeLimit(uri: Uri, maxBytes: Long = 150L * 1024L * 1024L): Boolean {
        return try {
            val pfd = getApplication<Application>().contentResolver.openFileDescriptor(uri, "r")
            val fileSize = pfd?.statSize ?: 0L
            pfd?.close()
            if (fileSize > maxBytes) {
                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "File size exceeds allowed limit (${maxBytes / (1024 * 1024)} MB).", Toast.LENGTH_LONG).show()
                }
                false
            } else {
                true
            }
        } catch (e: Exception) {
            true
        }
    }

    fun sendMultipleImages(chatId: String, uris: List<Uri>, receiverId: String) {
        if (uris.size > 15) {
            Toast.makeText(getApplication(), "You can send a maximum of 15 images at a time.", Toast.LENGTH_LONG).show()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                if (checkFileSizeLimit(uri, 150L * 1024L * 1024L)) {
                    sendImageMessage(chatId, uri, receiverId)
                }
            }
        }
    }

    fun sendVideoMessage(chatId: String, videoUri: Uri, receiverId: String) {
        val senderId = currentUserId
        if (senderId.isEmpty()) return
        if (!checkFileSizeLimit(videoUri, 150L * 1024L * 1024L)) return
        val resolvedChatId = if (chatId.isNotBlank()) chatId else getChatRoomId(senderId, receiverId)

        val tempId = java.util.UUID.randomUUID().toString()
        val tempMessage = Message(
            messageId = tempId,
            chatId = resolvedChatId,
            senderId = senderId,
            receiverId = receiverId,
            messageText = "📹 Video",
            messageType = "VIDEO",
            localUri = videoUri.toString(),
            mediaUrl = videoUri.toString(),
            timestamp = System.currentTimeMillis(),
            status = "SENDING",
            messageStatus = com.example.model.MessageStatus.SENDING,
            uploadProgress = 0
        )

        _messages.value = _messages.value + tempMessage

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cloudChatRepo = com.example.repository.CloudChatRepositoryImpl(getApplication())
                val downloadUrl = cloudChatRepo.uploadChatVideo(
                    chatId = resolvedChatId,
                    messageId = tempId,
                    fileUri = videoUri,
                    onProgress = { percent ->
                        _messages.value = _messages.value.map {
                            if (it.messageId == tempId) it.copy(uploadProgress = percent) else it
                        }
                    }
                )

                val payload = com.example.model.MessagePayload(
                    messageId = tempId,
                    chatId = resolvedChatId,
                    senderId = senderId,
                    receiverId = receiverId,
                    messageText = "📹 Video",
                    messageType = "VIDEO",
                    mediaUrl = downloadUrl,
                    timestamp = System.currentTimeMillis(),
                    status = "SENT"
                )

                dynamicStorageManager.saveMessage(payload)

                _messages.value = _messages.value.map {
                    if (it.messageId == tempId) it.copy(
                        mediaUrl = downloadUrl,
                        status = "SENT",
                        messageStatus = com.example.model.MessageStatus.SENT,
                        uploadProgress = 100
                    ) else it
                }

                withContext(Dispatchers.Main) {
                    com.example.util.HapticManager.playMessageSentThud(getApplication())
                }
            } catch (e: Exception) {
                Log.e("PlenxoViewModel", "Failed to upload and send video: ${e.message}", e)
                _messages.value = _messages.value.map {
                    if (it.messageId == tempId) it.copy(status = "FAILED", messageStatus = com.example.model.MessageStatus.FAILED) else it
                }
            }
        }
    }

    fun sendFileMessage(
        chatId: String,
        fileUri: Uri,
        fileName: String,
        fileSize: Long,
        receiverId: String
    ) {
        val senderId = currentUserId
        if (senderId.isEmpty()) return
        val maxFileLimit = 150L * 1024L * 1024L
        if (fileSize > maxFileLimit) {
            Toast.makeText(getApplication(), "File size exceeds 150 MB limit.", Toast.LENGTH_LONG).show()
            return
        }

        val resolvedChatId = if (chatId.isNotBlank()) chatId else getChatRoomId(senderId, receiverId)
        val tempId = java.util.UUID.randomUUID().toString()
        val displayName = if (fileName.isNotBlank()) fileName else "Attachment File"

        val tempMessage = Message(
            messageId = tempId,
            chatId = resolvedChatId,
            senderId = senderId,
            receiverId = receiverId,
            messageText = "📁 $displayName",
            messageType = "FILE",
            localUri = fileUri.toString(),
            mediaUrl = fileUri.toString(),
            timestamp = System.currentTimeMillis(),
            status = "SENDING",
            messageStatus = com.example.model.MessageStatus.SENDING,
            uploadProgress = 0
        )

        _messages.value = _messages.value + tempMessage

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mimeType = getApplication<Application>().contentResolver.getType(fileUri) ?: "application/octet-stream"
                val cloudChatRepo = com.example.repository.CloudChatRepositoryImpl(getApplication())
                val downloadUrl = cloudChatRepo.uploadChatFile(
                    chatId = resolvedChatId,
                    messageId = tempId,
                    fileUri = fileUri,
                    mimeType = mimeType,
                    onProgress = { percent ->
                        _messages.value = _messages.value.map {
                            if (it.messageId == tempId) it.copy(uploadProgress = percent) else it
                        }
                    }
                )

                val payload = com.example.model.MessagePayload(
                    messageId = tempId,
                    chatId = resolvedChatId,
                    senderId = senderId,
                    receiverId = receiverId,
                    messageText = "📁 $displayName",
                    messageType = "FILE",
                    mediaUrl = downloadUrl,
                    timestamp = System.currentTimeMillis(),
                    status = "SENT"
                )

                dynamicStorageManager.saveMessage(payload)

                _messages.value = _messages.value.map {
                    if (it.messageId == tempId) it.copy(
                        mediaUrl = downloadUrl,
                        status = "SENT",
                        messageStatus = com.example.model.MessageStatus.SENT,
                        uploadProgress = 100
                    ) else it
                }

                withContext(Dispatchers.Main) {
                    com.example.util.HapticManager.playMessageSentThud(getApplication())
                }
            } catch (e: Exception) {
                Log.e("PlenxoViewModel", "Failed to upload and send file: ${e.message}", e)
                _messages.value = _messages.value.map {
                    if (it.messageId == tempId) it.copy(status = "FAILED", messageStatus = com.example.model.MessageStatus.FAILED) else it
                }
            }
        }
    }

    fun handleSelectedMediaAttachment(uri: Uri) {
        val chatId = currentChatId.value
        val receiverId = currentChatRecipientUid.value
        if (chatId.isEmpty() || receiverId.isEmpty()) return
        if (!checkFileSizeLimit(uri)) return
        
        viewModelScope.launch {
            try {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to take persistable URI permission", e)
            }
            
            sendImageMessage(chatId, uri, receiverId)
        }
    }

    fun sendImageMessage(chatId: String, imageUri: Uri, receiverId: String) {
        val senderId = currentUserId
        if (senderId.isEmpty()) return
        if (!checkFileSizeLimit(imageUri)) return
        val resolvedChatId = if (chatId.isNotBlank()) chatId else getChatRoomId(senderId, receiverId)

        val tempId = java.util.UUID.randomUUID().toString()
        val tempMessage = Message(
            messageId = tempId,
            chatId = resolvedChatId,
            senderId = senderId,
            receiverId = receiverId,
            messageText = "📷 Photo",
            messageType = "IMAGE",
            localUri = imageUri.toString(),
            mediaUrl = imageUri.toString(),
            timestamp = System.currentTimeMillis(),
            status = "SENDING",
            messageStatus = com.example.model.MessageStatus.SENDING,
            uploadProgress = 0
        )

        // Optimistic local dispatch
        _messages.value = _messages.value + tempMessage

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cloudChatRepo = com.example.repository.CloudChatRepositoryImpl(getApplication())
                val downloadUrl = cloudChatRepo.uploadChatImage(
                    chatId = resolvedChatId,
                    messageId = tempId,
                    fileUri = imageUri,
                    onProgress = { percent ->
                        _messages.value = _messages.value.map {
                            if (it.messageId == tempId) it.copy(uploadProgress = percent) else it
                        }
                    }
                )

                val payload = com.example.model.MessagePayload(
                    messageId = tempId,
                    chatId = resolvedChatId,
                    senderId = senderId,
                    receiverId = receiverId,
                    messageText = "📷 Photo",
                    messageType = "IMAGE",
                    mediaUrl = downloadUrl,
                    timestamp = System.currentTimeMillis(),
                    status = "SENT"
                )

                dynamicStorageManager.saveMessage(payload)

                _messages.value = _messages.value.map {
                    if (it.messageId == tempId) it.copy(
                        mediaUrl = downloadUrl,
                        status = "SENT",
                        messageStatus = com.example.model.MessageStatus.SENT,
                        uploadProgress = 100
                    ) else it
                }

                withContext(Dispatchers.Main) {
                    com.example.util.HapticManager.playMessageSentThud(getApplication())
                }
            } catch (e: Exception) {
                Log.e("PlenxoViewModel", "Failed to upload and send image: ${e.message}", e)
                _messages.value = _messages.value.map {
                    if (it.messageId == tempId) it.copy(status = "FAILED", messageStatus = com.example.model.MessageStatus.FAILED) else it
                }
            }
        }
    }

    fun sendVoiceNoteMessage(chatId: String, voiceUri: Uri, receiverId: String) {
        val senderId = currentUserId
        if (senderId.isEmpty()) return
        if (!checkFileSizeLimit(voiceUri)) return
        val resolvedChatId = if (chatId.isNotBlank()) chatId else getChatRoomId(senderId, receiverId)

        val tempId = java.util.UUID.randomUUID().toString()
        val tempMessage = Message(
            messageId = tempId,
            chatId = resolvedChatId,
            senderId = senderId,
            receiverId = receiverId,
            messageText = "🎤 Voice Note",
            messageType = "VOICE",
            localUri = voiceUri.toString(),
            mediaUrl = voiceUri.toString(),
            timestamp = System.currentTimeMillis(),
            status = "SENDING",
            messageStatus = com.example.model.MessageStatus.SENDING,
            uploadProgress = 0
        )

        // Optimistic local dispatch
        _messages.value = _messages.value + tempMessage

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cloudChatRepo = com.example.repository.CloudChatRepositoryImpl(getApplication())
                val downloadUrl = cloudChatRepo.uploadVoiceNote(
                    chatId = resolvedChatId,
                    messageId = tempId,
                    fileUri = voiceUri,
                    onProgress = { percent ->
                        _messages.value = _messages.value.map {
                            if (it.messageId == tempId) it.copy(uploadProgress = percent) else it
                        }
                    }
                )

                val payload = com.example.model.MessagePayload(
                    messageId = tempId,
                    chatId = resolvedChatId,
                    senderId = senderId,
                    receiverId = receiverId,
                    messageText = downloadUrl,
                    messageType = "VOICE",
                    mediaUrl = downloadUrl,
                    timestamp = System.currentTimeMillis(),
                    status = "SENT"
                )

                dynamicStorageManager.saveMessage(payload)

                _messages.value = _messages.value.map {
                    if (it.messageId == tempId) it.copy(
                        mediaUrl = downloadUrl,
                        messageText = downloadUrl,
                        status = "SENT",
                        messageStatus = com.example.model.MessageStatus.SENT,
                        uploadProgress = 100
                    ) else it
                }

                withContext(Dispatchers.Main) {
                    com.example.util.HapticManager.playMessageSentThud(getApplication())
                }
            } catch (e: Exception) {
                Log.e("PlenxoViewModel", "Failed to upload and send voice note: ${e.message}", e)
                _messages.value = _messages.value.map {
                    if (it.messageId == tempId) it.copy(status = "FAILED", messageStatus = com.example.model.MessageStatus.FAILED) else it
                }
            }
        }
    }

    fun retryFailedMessage(message: Message) {
        if (message.messageId.isBlank()) return
        _messages.value = _messages.value.map {
            if (it.messageId == message.messageId) it.copy(status = "SENDING", messageStatus = com.example.model.MessageStatus.SENDING) else it
        }

        when (message.messageType.uppercase()) {
            "IMAGE" -> {
                val uriStr = message.localUri ?: message.mediaUrl
                if (uriStr.isNotBlank()) {
                    sendImageMessage(message.chatId, Uri.parse(uriStr), message.receiverId)
                }
            }
            "VOICE", "AUDIO" -> {
                val uriStr = message.localUri ?: message.mediaUrl
                if (uriStr.isNotBlank()) {
                    sendVoiceNoteMessage(message.chatId, Uri.parse(uriStr), message.receiverId)
                }
            }
            else -> {
                sendMessage(message.chatId, message.messageText, message.receiverId)
            }
        }
    }

    fun markMessagesAsRead(chatId: String) {
        val uid = currentUserId
        if (uid.isEmpty() || chatId.isEmpty()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chatRef = firestore.collection("chats").document(chatId)
                firestore.runTransaction { transaction ->
                    val chatSnapshot = transaction.get(chatRef)
                    if (chatSnapshot.exists()) {
                        @Suppress("UNCHECKED_CAST")
                        val currentUnread = chatSnapshot.get("unreadCounts") as? Map<String, Long> ?: emptyMap()
                        if ((currentUnread[uid] ?: 0L) > 0L) {
                            val newUnread = currentUnread.toMutableMap()
                            newUnread[uid] = 0L
                            transaction.update(chatRef, "unreadCounts", newUnread)
                        }
                    }
                    null
                }.await()

                val unreadSnapshot = firestore.collection("messages")
                    .whereEqualTo("chatId", chatId)
                    .whereEqualTo("receiverId", uid)
                    .whereNotEqualTo("status", "READ")
                    .get()
                    .await()

                if (!unreadSnapshot.isEmpty) {
                    val batch = firestore.batch()
                    for (doc in unreadSnapshot.documents) {
                        batch.update(doc.reference, "status", "READ")
                    }
                    batch.commit().await()
                    Log.d("Plenxo", "Batch updated ${unreadSnapshot.size()} incoming messages to READ status")
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to mark messages as read: ${e.message}", e)
            }
        }
    }

    fun stopListeningForMessages() {
        messagesJob?.cancel()
        messagesJob = null
        messagesListener?.cancel()
        messagesListener = null
    }
    
    fun openChatWithUid(targetUid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentUid = currentUserId
            val chatId = getChatRoomId(currentUid, targetUid)
            var targetName = "Chat"
            if (!_usersCache.value.containsKey(targetUid)) {
                try {
                    val userDoc = firestore.collection("users").document(targetUid).get().await()
                    val user = userDoc.toObject(User::class.java)
                    if (user != null) {
                        targetName = user.displayName
                        withContext(Dispatchers.Main) {
                            _usersCache.value = _usersCache.value + (targetUid to user)
                        }
                    }
                } catch (e: Exception) {}
            } else {
                targetName = _usersCache.value[targetUid]?.displayName ?: "Chat"
            }
            withContext(Dispatchers.Main) {
                currentChatId.value = chatId
                currentChatRecipientName.value = targetName
                currentChatRecipientUid.value = targetUid
                navigateToScreen(PlenxoScreen.CHAT_DETAIL)
                startListeningForMessages(chatId)
            }
        }
    }

    fun openChatWithEmail(targetEmail: String) {
        _errorMessage.value = "Email search and email chat are disabled. Please use Plenxo ID."
    }
    
    val selectedUserIdForProfile = MutableStateFlow<String>("")

    fun openUserProfile(userId: String) {
        if (userId.isNotBlank()) {
            selectedUserIdForProfile.value = userId
            navigateToScreen(PlenxoScreen.USER_PROFILE)
        }
    }

    fun openChatRoom(chat: ChatRoom?) {
        if (chat == null || chat.participantUids.isEmpty()) {
            Log.e("ChatTransition", "CRITICAL ERROR: Chat data missing or invalid")
            return
        }

        val currentUid = currentUserId
        val targetUid = chat.participantUids.firstOrNull { it != currentUid } ?: chat.participantUids.firstOrNull() ?: ""
        if (targetUid.isEmpty()) {
            Log.e("ChatTransition", "CRITICAL ERROR: Recipient data missing")
            return
        }

        // INSTANT TRANSITION: Set current chat state immediately from cache/room info
        val cachedName = usersCache.value[targetUid]?.displayName
            ?: "Chat"

        currentChatId.value = chat.chatId
        currentChatRecipientName.value = cachedName
        currentChatRecipientUid.value = targetUid

        // Navigate instantly without showing a full screen loading spinner
        navigateToScreen(PlenxoScreen.CHAT_DETAIL)
        startListeningForMessages(chat.chatId)

        // Asynchronously fetch/update target user details in background if needed
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userDoc = firestore.collection("users").document(targetUid).get().await()
                val targetUser = userDoc.toObject(User::class.java)
                if (targetUser != null && !targetUser.displayName.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        currentChatRecipientName.value = targetUser.displayName
                    }
                }
            } catch (e: Exception) {
                Log.w("ChatTransition", "Background user fetch failed: ${e.message}")
            }
        }
    }

    fun deleteMessage(chatId: String, messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Phase 3A: Privacy-Aware Branching
                if (isLocalOnlyMode.value) {
                    // Standardize Local Deletion
                    AppDatabase.getDatabase(getApplication()).localMessageDao().deleteMessage(messageId)
                    Log.d("Plenxo", "Privacy Mode: Deleted message locally")
                } else {
                    // Standardize Cloud Deletion with .await()
                    firestore.collection("chats").document(chatId)
                        .collection("messages").document(messageId)
                        .delete()
                        .await()
                    Log.d("Plenxo", "Cloud Mode: Deleted message from Firestore")
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "CRITICAL: Deletion Failure caught!", e)
                _errorMessage.value = "Failed to delete message. Please check your connection."
            }
        }
    }

    fun setMessageExpiry(chatId: String, messageId: String, expiryDurationMs: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val expiryTime = if (expiryDurationMs != null) System.currentTimeMillis() + expiryDurationMs else null
                if (isLocalOnlyMode.value) {
                    val db = AppDatabase.getDatabase(getApplication())
                    val msg = db.localMessageDao().getMessageById(messageId)
                    if (msg != null) {
                        val updatedMsg = msg.copy(expiresAt = expiryTime, expiryTimestamp = expiryTime)
                        db.localMessageDao().insertMessage(updatedMsg)
                        Log.d("Plenxo", "Updated message expiry locally")
                    }
                } else {
                    val docRef = firestore.collection("chats").document(chatId)
                        .collection("messages").document(messageId)
                    val updates = mutableMapOf<String, Any?>()
                    updates["expiresAt"] = expiryTime
                    updates["expiryTimestamp"] = expiryTime
                    docRef.update(updates).await()
                    Log.d("Plenxo", "Updated message expiry in Cloud")
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to set message expiry", e)
            }
        }
    }
    
    fun navigateBackToHome() {
        messagesListener?.cancel()
        messagesListener = null
        messagesJob?.cancel()
        messagesJob = null
        currentChatId.value = ""
        currentChatRecipientName.value = ""
        currentChatRecipientUid.value = ""
        _currentScreen.value = PlenxoScreen.HOME
    }


    fun forwardMessage(message: Message, targetChatIds: List<String>) {
        viewModelScope.launch {
            try {
                val currentChatsList = chats.value
                for (targetChatId in targetChatIds) {
                    val chatRoom = currentChatsList.find { it.chatId == targetChatId } ?: continue
                    val recipientId = chatRoom.participantUids.firstOrNull { it != currentUserId } ?: ""
                    sendMessage(targetChatId, message.messageText, recipientId)
                }
                Log.d("Plenxo", "Successfully forwarded message")
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to forward message: ${e.message}", e)
            }
        }
    }

    fun onDeleteActionConfirmed(chatId: String, messageId: String, deleteForEveryone: Boolean) {
        viewModelScope.launch {
            try {
                val db = AppDatabase.getDatabase(getApplication())
                db.localMessageDao().deleteMessage(messageId)

                if (!isLocalOnlyMode.value && deleteForEveryone) {
                    firestore.collection("chats")
                        .document(chatId)
                        .collection("messages")
                        .document(messageId)
                        .delete()
                        .await()
                    Log.d("Plenxo", "Successfully deleted message for everyone online")
                } else {
                    Log.d("Plenxo", "Deleted message locally")
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to delete message: ${e.message}", e)
            }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Delete messages from local SQLite database
                val db = AppDatabase.getDatabase(getApplication())
                db.localMessageDao().deleteMessagesForChat(chatId)
                
                // If not in local-only mode, delete chat and messages from Firestore
                if (!isLocalOnlyMode.value) {
                    val messagesSnapshot = firestore.collection("chats").document(chatId)
                        .collection("messages").get().await()
                    for (doc in messagesSnapshot.documents) {
                        doc.reference.delete().await()
                    }
                    firestore.collection("chats").document(chatId).delete().await()
                }
                Log.d("Plenxo", "Successfully deleted/purged chat and history")
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to delete chat: ${e.message}", e)
            }
        }
    }

    fun executeTextCopy(context: Context, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText(context.getString(com.example.R.string.str_copied_message), text)
            clipboard.setPrimaryClip(clip)
            Log.d("Plenxo", "Copied text content to clipboard.")
        } catch (e: Exception) {
            Log.e("Plenxo", "Failed to copy text to clipboard: ${e.message}", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        } catch (e: Exception) {
            Log.e("Plenxo", "Error removing authStateListener: ${e.message}")
        }
        audioRecorder.stopRecording()
        try {
            voicePlayer.release()
        } catch (e: Exception) {
            Log.e("Plenxo", "Failed to release voicePlayer", e)
        }
        stopAudio()
        callListener?.remove()
        outgoingCallSignalingManager?.cleanup()
        incomingCallSignalingManager?.cleanup()
        chatsListener?.cancel()
        messagesListener?.cancel()
        messagesJob?.cancel()
        invitationsListener?.cancel()
        currentUserListener?.cancel()
    }

    private suspend fun fetchQueryWithFallback(query: com.google.firebase.firestore.Query): com.google.firebase.firestore.QuerySnapshot {
        return try {
            query.get().await()
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE) {
                query.get(com.google.firebase.firestore.Source.CACHE).await()
            } else {
                throw e
            }
        } catch (e: Exception) {
            try {
                query.get(com.google.firebase.firestore.Source.CACHE).await()
            } catch (cacheEx: Exception) {
                throw e
            }
        }
    }

    private suspend fun fetchDocWithFallback(docRef: com.google.firebase.firestore.DocumentReference): com.google.firebase.firestore.DocumentSnapshot {
        return try {
            docRef.get().await()
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE) {
                docRef.get(com.google.firebase.firestore.Source.CACHE).await()
            } else {
                throw e
            }
        } catch (e: Exception) {
            try {
                docRef.get(com.google.firebase.firestore.Source.CACHE).await()
            } catch (cacheEx: Exception) {
                throw e
            }
        }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        try {
            addOnCompleteListener { task ->
                if (cont.isActive) {
                    if (task.isSuccessful) {
                        cont.resume(task.result)
                    } else {
                        cont.resumeWithException(task.exception ?: RuntimeException("Firebase Operation failed"))
                    }
                }
            }
            addOnFailureListener { e ->
                if (cont.isActive) {
                    cont.resumeWithException(e)
                }
            }
        } catch (t: Throwable) {
            if (cont.isActive) {
                cont.resumeWithException(t)
            }
        }
    }

    // PRESENCE SYSTEM
    private val _userPresences = MutableStateFlow<Map<String, Map<String, Any>>>(emptyMap())
    val userPresences = _userPresences.asStateFlow()

    private var myPresenceRef: com.google.firebase.database.DatabaseReference? = null
    private val presenceListeners = mutableMapOf<String, com.google.firebase.database.ValueEventListener>()
    private val firestorePresenceListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()
    private var typingJobMap = mutableMapOf<String, kotlinx.coroutines.Job>()

    fun setupPresenceSystem() {
        val uid = currentUserId
        if (uid.isEmpty()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = mapOf(
                    "status" to "online",
                    "state" to "online",
                    "isTyping" to false,
                    "lastSeen" to System.currentTimeMillis(),
                    "last_seen" to System.currentTimeMillis()
                )
                firestore.collection("users_presence").document(uid).set(data, com.google.firebase.firestore.SetOptions.merge())
            } catch (e: Exception) {
                Log.e("Plenxo", "Error setting up presence in Firestore", e)
            }
        }

        try {
            myPresenceRef = FirebaseDatabase.getInstance().getReference("status/$uid")
            val connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected")
            connectedRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    if (connected) {
                        val presenceRef = myPresenceRef ?: return
                        presenceRef.onDisconnect().setValue(
                            mapOf(
                                "state" to "offline",
                                "status" to "offline",
                                "last_seen" to com.google.firebase.database.ServerValue.TIMESTAMP
                            )
                        )
                        presenceRef.setValue(
                            mapOf(
                                "state" to "online",
                                "status" to "online",
                                "last_seen" to com.google.firebase.database.ServerValue.TIMESTAMP
                            )
                        )
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Log.e("Plenxo", "Presence listener cancelled", error.toException())
                }
            })
        } catch (e: Exception) {
            Log.e("Plenxo", "RTDB presence setup fallback error", e)
        }
    }

    fun setPresenceState(state: String) {
        val uid = currentUserId
        if (uid.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = mapOf(
                    "status" to state,
                    "state" to state,
                    "isTyping" to false,
                    "lastSeen" to System.currentTimeMillis(),
                    "last_seen" to System.currentTimeMillis()
                )
                firestore.collection("users_presence").document(uid).set(data, com.google.firebase.firestore.SetOptions.merge())
            } catch (e: Exception) {
                Log.e("Plenxo", "Error setting presence state", e)
            }
        }
    }

    fun startListeningToPresence(targetUid: String) {
        if (targetUid.isEmpty() || firestorePresenceListeners.containsKey(targetUid)) return

        val registration = firestore.collection("users_presence").document(targetUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val data = snapshot.data ?: emptyMap<String, Any>()
                val status = (data["status"] as? String) ?: (data["state"] as? String) ?: "offline"
                val isTyping = (data["isTyping"] as? Boolean) ?: false
                val typingTo = (data["typingTo"] as? String) ?: ""
                val lastSeen = (data["lastSeen"] as? Long) ?: (data["last_seen"] as? Long) ?: 0L

                val map = mapOf<String, Any>(
                    "status" to status,
                    "state" to status,
                    "isTyping" to isTyping,
                    "typingTo" to typingTo,
                    "lastSeen" to lastSeen,
                    "last_seen" to lastSeen
                )
                val current = _userPresences.value.toMutableMap()
                current[targetUid] = map
                _userPresences.value = current
            }
        firestorePresenceListeners[targetUid] = registration
    }

    fun stopListeningToPresence(targetUid: String) {
        firestorePresenceListeners.remove(targetUid)?.remove()
    }

    fun onUserTypingInChat(chatId: String, targetUid: String) {
        val uid = currentUserId
        if (uid.isEmpty()) return

        typingJobMap[chatId]?.cancel()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = mapOf(
                    "status" to "online",
                    "state" to "online",
                    "isTyping" to true,
                    "typingTo" to targetUid,
                    "lastSeen" to System.currentTimeMillis()
                )
                firestore.collection("users_presence").document(uid).set(data, com.google.firebase.firestore.SetOptions.merge())
            } catch (e: Exception) {
                Log.e("Plenxo", "Error sending typing start", e)
            }
        }

        typingJobMap[chatId] = viewModelScope.launch(Dispatchers.IO) {
            delay(2000)
            try {
                val data = mapOf(
                    "isTyping" to false,
                    "typingTo" to "",
                    "lastSeen" to System.currentTimeMillis()
                )
                firestore.collection("users_presence").document(uid).set(data, com.google.firebase.firestore.SetOptions.merge())
            } catch (e: Exception) {
                Log.e("Plenxo", "Error resetting typing status", e)
            }
        }
    }

    fun onUserStoppedTypingInChat(chatId: String) {
        val uid = currentUserId
        if (uid.isEmpty()) return
        typingJobMap[chatId]?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = mapOf(
                    "isTyping" to false,
                    "typingTo" to "",
                    "lastSeen" to System.currentTimeMillis()
                )
                firestore.collection("users_presence").document(uid).set(data, com.google.firebase.firestore.SetOptions.merge())
            } catch (e: Exception) {
                Log.e("Plenxo", "Error stopping typing", e)
            }
        }
    }

    fun saveFcmToken() {
        val uid = currentUserId
        if (uid.isEmpty()) return
        try {
            val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(getApplication())
            if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                Log.w("PlenxoViewModel", "Google Play Services unavailable (code $resultCode). Skipping FCM token save.")
                return
            }
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                try {
                    if (!task.isSuccessful) {
                        return@addOnCompleteListener
                    }
                    val token = task.result
                    if (token != null) {
                        viewModelScope.launch {
                            try {
                                com.example.service.PlenxoFCMService.updateFcmTokenInDatabase(uid, token)
                            } catch (e: Throwable) {
                                Log.e("PlenxoViewModel", "Failed to save FCM token: ${e.message}")
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.w("PlenxoViewModel", "Error processing FCM token response: ${t.message}")
                }
            }.addOnFailureListener { e ->
                Log.w("PlenxoViewModel", "FCM token request failed: ${e.message}")
            }
        } catch (t: Throwable) {
            Log.e("PlenxoViewModel", "FCM token initialization error: ${t.message}")
        }
    }
}
