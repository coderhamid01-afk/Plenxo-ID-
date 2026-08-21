```kotlin
// PlenxoViewModel.kt
@file:Suppress("DEPRECATION")
package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.example.network.supabase.*
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
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import android.util.Log
import android.net.Uri
import android.media.MediaPlayer
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.R
import com.example.network.CloudinaryStorageManager
import com.example.util.SessionManager
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

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import kotlinx.coroutines.tasks.await

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
    ANIMATIONS,
    CHAT_REQUESTS
}

sealed class AddUserState {
    object Idle : AddUserState()
    object Loading : AddUserState()
    data class Error(val message: String) : AddUserState()
    data class Success(val user: com.example.model.User) : AddUserState()
}

class PlenxoViewModel(application: Application) : AndroidViewModel(application) {

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    
    // Add User / New Chat Modal State
    val showAddUserSheet = MutableStateFlow(false)
    val addUserQuery = MutableStateFlow("")
    private val _addUserState = MutableStateFlow<AddUserState>(AddUserState.Idle)
    val addUserState: StateFlow<AddUserState> = _addUserState.asStateFlow()

    fun showAddUserModal() {
        addUserQuery.value = ""
        _addUserState.value = AddUserState.Idle
        showAddUserSheet.value = true
    }

    fun dismissAddUserModal() {
        showAddUserSheet.value = false
        _addUserState.value = AddUserState.Idle
        addUserQuery.value = ""
    }

    fun clearAddUserError() {
        if (_addUserState.value is AddUserState.Error) {
            _addUserState.value = AddUserState.Idle
        }
    }
    
    fun getChatRoomId(uid1: String, uid2: String): String {
        if (uid1.isEmpty() || uid2.isEmpty()) return ""
        return if (uid1 < uid2) {
            uid1 + "_" + uid2
        } else {
            uid2 + "_" + uid1
        }
    }
    
    private val localSettingsRepo by lazy { com.example.repository.LocalSettingsRepositoryImpl(application) }

    // Form inputs
    val email = MutableStateFlow("")
    val password = MutableStateFlow("")
    val confirmPassword = MutableStateFlow("")
    val isTermsAccepted = MutableStateFlow(false)
    val isPrivacyAccepted: MutableStateFlow<Boolean> get() = isTermsAccepted
    val phoneNumber = MutableStateFlow("")

    // CAPTCHA State Flows
    val captchaChallenge = MutableStateFlow<com.example.util.CaptchaManager.CaptchaChallenge?>(null)
    val captchaInput = MutableStateFlow("")
    val captchaVerificationToken = MutableStateFlow<String?>(null)
    val captchaErrorMessage = MutableStateFlow<String?>(null)
    val captchaIsLoading = MutableStateFlow(false)
    val captchaBlockRemaining = MutableStateFlow(0L)

    fun loadCaptchaChallenge() {
        viewModelScope.launch {
            captchaIsLoading.value = true
            captchaErrorMessage.value = null
            
            val blockTime = com.example.util.CaptchaManager.getBlockRemainingTime(getApplication())
            captchaBlockRemaining.value = blockTime
            if (blockTime > 0) {
                captchaChallenge.value = null
                captchaIsLoading.value = false
                return@launch
            }

            try {
                val challenge = com.example.util.CaptchaManager.fetchCaptchaChallenge(getApplication())
                captchaChallenge.value = challenge
            } catch (e: Exception) {
                android.util.Log.e("PlenxoViewModel", "Error fetching CAPTCHA", e)
                captchaErrorMessage.value = e.message ?: "Failed to load CAPTCHA."
                captchaChallenge.value = null
            } finally {
                captchaIsLoading.value = false
            }
        }
    }

    fun verifyCaptchaAnswer() {
        viewModelScope.launch {
            val challenge = captchaChallenge.value
            if (challenge == null) {
                captchaErrorMessage.value = "No active CAPTCHA challenge found."
                return@launch
            }

            captchaIsLoading.value = true
            captchaErrorMessage.value = null

            try {
                val token = com.example.util.CaptchaManager.verifyCaptchaChallenge(
                    getApplication(),
                    challenge.challengeId,
                    captchaInput.value
                )
                com.example.util.SessionManager.saveCaptchaVerified(getApplication(), true)
                captchaVerificationToken.value = token
                captchaInput.value = ""
                captchaErrorMessage.value = null
            } catch (e: Exception) {
                android.util.Log.e("PlenxoViewModel", "CAPTCHA Verification Failed", e)
                captchaErrorMessage.value = e.message ?: "Incorrect CAPTCHA."
                captchaVerificationToken.value = null
                loadCaptchaChallenge()
            } finally {
                captchaIsLoading.value = false
            }
        }
    }

    fun resetCaptcha() {
        captchaChallenge.value = null
        captchaInput.value = ""
        captchaVerificationToken.value = null
        captchaErrorMessage.value = null
        captchaIsLoading.value = false
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
    val readReceiptsEnabled = MutableStateFlow(true)
    val aboutText = MutableStateFlow("Hey there! I am using Plenxo Pro.")
    val pinnedChatIds = MutableStateFlow<Set<String>>(emptySet())
    val isLocalOnlyMode = MutableStateFlow(false)
    val darkModeEnabled = MutableStateFlow(true)
    val allowScreenshots = MutableStateFlow(false)
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
    val plenxoId = MutableStateFlow((100000..999999).random().toString())

    fun uploadProfilePicture(uri: Uri) {
        val currentUid = currentUserId
        if (currentUid.isEmpty()) return
        isProfilePicUploading.value = true
        viewModelScope.launch {
            try {
                val url = uploadToCatbox(uri)
                if (url != null) {
                    uploadedProfilePicUrl.value = url
                    galleryImageUriString.value = url
                    isProfilePicUploading.value = false
                    Log.d("Catbox", "Profile picture uploaded successfully: $url")
                } else {
                    isProfilePicUploading.value = false
                    _errorMessage.value = "Failed to upload image to Catbox."
                }
            } catch (e: Exception) {
                Log.e("Catbox", "Exception uploading profile picture", e)
                isProfilePicUploading.value = false
                _errorMessage.value = "Upload error: ${e.localizedMessage}"
            }
        }
    }

    private suspend fun uploadToCatbox(uri: Uri): String? {
        return try {
            com.example.network.CatboxStorageManager.uploadImage(getApplication(), uri)
        } catch (e: Exception) {
            Log.e("Catbox", "Upload exception", e)
            null
        }
    }

    fun saveProfileSetup() {
        var currentUid = currentUserId
        if (currentUid.isEmpty()) {
            val savedEmail = email.value.ifBlank { "user@plenxo.app" }
            currentUid = savedEmail.replace(".", "_")
            com.example.util.SessionManager.saveLoginState(getApplication(), currentUid, savedEmail)
        }
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val avatarUrl = uploadedProfilePicUrl.value ?: galleryImageUriString.value ?: ""
                val dName = displayName.value.ifBlank { "Plenxo User" }
                val pId = plenxoId.value.ifBlank { (100000..999999).random().toString() }

                val profileMap = mapOf(
                    "id" to currentUid,
                    "uid" to currentUid,
                    "displayName" to dName,
                    "display_name" to dName,
                    "userCode" to pId,
                    "plenxo_id" to pId,
                    "profilePicUrl" to avatarUrl,
                    "avatar_url" to avatarUrl,
                    "statusMessage" to "Hey there! I am using Plenxo.",
                    "is_profile_completed" to true,
                    "isProfileCompleted" to true
                )

                // Save directly to Firestore under users_data/{userId}
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users_data")
                    .document(currentUid)
                    .set(profileMap, com.google.firebase.firestore.SetOptions.merge())
                    .await()

                try {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(currentUid)
                        .set(profileMap, com.google.firebase.firestore.SetOptions.merge())
                        .await()
                } catch (e: Exception) {
                    Log.w("Plenxo", "Users collection set warning: ${e.message}")
                }

                _isLoading.value = false
                _currentScreen.value = PlenxoScreen.HOME
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to save profile setup", e)
                _errorMessage.value = "Failed to save profile: ${e.localizedMessage}"
                _isLoading.value = false
            }
        }
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
        val uid = com.example.network.supabase.SupabaseModule.currentUserId()
            .takeIf { it.isNotEmpty() }
            ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: ""
        if (uid.isEmpty()) return

        currentUserProfileListener = firestore.collection("users_data").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val data = snapshot.data ?: emptyMap()
                val resolvedName = (data["displayName"] as? String) ?: (data["username"] as? String) ?: ""
                val resolvedBio = (data["statusMessage"] as? String) ?: (data["bio"] as? String) ?: ""
                val resolvedPic = (data["profilePicUrl"] as? String) ?: (data["avatar_url"] as? String) ?: (data["photoUrl"] as? String) ?: ""
                val resolvedRing = (data["selectedRingId"] as? String) ?: (data["profileRingId"] as? String) ?: "none"
                val resolvedCode = (data["userCode"] as? String) ?: (data["user_code"] as? String) ?: ""

                val current = currentUserProfile.value
                currentUserProfile.value = (current ?: UserProfile(id = uid, uid = uid)).copy(
                    id = snapshot.id,
                    uid = uid,
                    displayName = resolvedName.ifEmpty { current?.displayName ?: "" },
                    statusMessage = resolvedBio.ifEmpty { current?.statusMessage ?: "" },
                    bio = resolvedBio.ifEmpty { current?.bio ?: "" },
                    profilePicUrl = resolvedPic.ifEmpty { current?.profilePicUrl ?: "" },
                    profileRingId = resolvedRing.ifEmpty { current?.profileRingId ?: "none" },
                    userCode = resolvedCode.ifEmpty { current?.userCode ?: "" }
                )
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
        viewModelScope.launch {
            val uid = currentUserId
            if (uid.isEmpty()) {
                _profileShareState.value = ProfileShareState.Error("You are not logged in.")
                return@launch
            }

            try {
                _profileShareState.value = ProfileShareState.Generating
                
                // Cryptographically secure unique token
                val token = java.util.UUID.randomUUID().toString().take(16)
                
                // Expiry calculation: Exactly 1 hour (3600 seconds) from now
                val ONE_HOUR_IN_MS = 3600 * 1000L
                val expiryTimeMs = System.currentTimeMillis() + ONE_HOUR_IN_MS
                
                val shareData = mapOf(
                    "token" to token,
                    "ownerUid" to uid,
                    "createdAt" to System.currentTimeMillis(),
                    "expiresAt" to expiryTimeMs
                )

                // Store in dedicated collection
                supabase.postgrest[COLLECTION_PROFILE_SHARES].insert(shareData)

                val deepLink = "plenxo://addfriend?token=$token"
                
                // Invoke native share sheet
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "Add me on Plenxo! Click this link to connect (expires in 1 hour): $deepLink")
                    type = "text/plain"
                }
                
                val chooser = Intent.createChooser(shareIntent, "Share Profile")
                // Ensure context is handled safely for non-activity contexts if needed
                if (context !is android.app.Activity) {
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                
                _profileShareState.value = ProfileShareState.Success(deepLink)
                
            } catch (e: Exception) {
                Log.e("Plenxo", "Elite Link Generation Failed", e)
                val easyMessage = if (e is java.net.UnknownHostException) "No internet. Try again later." else "Could not create link. Try again."
                _profileShareState.value = ProfileShareState.Error(easyMessage)
            }
        }
    }

    fun sendDeepLinkFriendRequest() {
        // Placeholder to fix build
    }

    /**
     * Handles and validates incoming deep link intents.
     */
    fun handleDeepLink(uri: Uri?) {
        // Strict scheme and host verification
        if (uri == null || uri.scheme != "plenxo" || uri.host != "addfriend") return
        
        val token = uri.getQueryParameter("token")
        if (token.isNullOrBlank()) {
            _deepLinkResolutionState.value = DeepLinkResolutionState.InvalidOrExpired("This link is broken.")
            return
        }
              viewModelScope.launch {
            try {
                _deepLinkResolutionState.value = DeepLinkResolutionState.Resolving
                
                // Query the share token
                val doc = supabase.postgrest[COLLECTION_PROFILE_SHARES].select {
                    filter {
                        eq("token", token)
                    }
                }.decodeSingleOrNull<Map<String, String>>()
                
                if (doc == null) {
                    _deepLinkResolutionState.value = DeepLinkResolutionState.InvalidOrExpired("This link is wrong or deleted.")
                    return@launch
                }

                // Check for expiry
                val expiresAt = doc["expiresAt"]?.toLongOrNull() ?: 0L
                if (System.currentTimeMillis() > expiresAt) {
                    _deepLinkResolutionState.value = DeepLinkResolutionState.InvalidOrExpired("This link has expired after 1 hour.")
                    // Cleanup expired document in background
                    supabase.postgrest[COLLECTION_PROFILE_SHARES].delete {
                        filter {
                            eq("token", token)
                        }
                    }
                    return@launch
                }

                val ownerUid = doc["ownerUid"]
                if (ownerUid == null) {
                    _deepLinkResolutionState.value = DeepLinkResolutionState.InvalidOrExpired("User no longer exists.")
                    return@launch
                }
                
                // Self-add prevention
                if (ownerUid == currentUserId) {
                    _deepLinkResolutionState.value = DeepLinkResolutionState.InvalidOrExpired("This is your own profile link!")
                    return@launch
                }

                // Resolve target user profile
                val profile = supabase.postgrest[COLLECTION_USERS].select {
                    filter {
                        eq("id", ownerUid)
                    }
                }.decodeSingleOrNull<UserProfile>()
                if (profile != null) {
                    _deepLinkResolutionState.value = DeepLinkResolutionState.ValidProfileFound(profile)
                } else {
                    _deepLinkResolutionState.value = DeepLinkResolutionState.InvalidOrExpired("Invalid profile data.")
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Deep Link Resolution Error", e)
                val easyMessage = if (e is java.net.UnknownHostException) "No internet. Cannot check link." else "Something went wrong. Try again."
                _deepLinkResolutionState.value = DeepLinkResolutionState.InvalidOrExpired(easyMessage)
            }
        }
    }

    fun clearDeepLinkResult() {
        _deepLinkResolutionState.value = DeepLinkResolutionState.Idle
        _profileShareState.value = ProfileShareState.Idle
    }

    // Screen navigation
    private val _currentScreen = MutableStateFlow(PlenxoScreen.SIGNUP)
    val currentScreen: StateFlow<PlenxoScreen> = _currentScreen
    private val screenHistory = mutableListOf<PlenxoScreen>()

    fun navigateToScreen(screen: PlenxoScreen, addToHistory: Boolean = true, clearHistory: Boolean = false) {
        try {
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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // OTP verification
    private val _generatedOtp = MutableStateFlow("")
    val generatedOtp: StateFlow<String> = _generatedOtp

    private val _isLoginOtpChallenge = MutableStateFlow(false)
    val isLoginOtpChallenge: StateFlow<Boolean> = _isLoginOtpChallenge

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
    val chats: StateFlow<List<ChatRoom>> = combine(_chats, _contactsSet) { chatList, contacts ->
        chatList.filter { chat ->
            val recipientUid = chat.participantUids.find { it != currentUserId } ?: ""
            recipientUid.isEmpty() || contacts.contains(recipientUid)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCall = MutableStateFlow<CallSession?>(null)
    private var callListener: ListenerRegistration? = null
    private var outgoingCallListener: ListenerRegistration? = null

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

    val currentChatRecipientName = MutableStateFlow("")
    val currentChatRecipientUid = MutableStateFlow("")
    val currentChatId = MutableStateFlow("")
    
    // Firestore listeners
    private var chatsListener: kotlinx.coroutines.Job? = null
    private var messagesListener: kotlinx.coroutines.Job? = null
    private var invitationsListener: kotlinx.coroutines.Job? = null
    private var friendRequestsListener: kotlinx.coroutines.Job? = null
    private var currentUserListener: kotlinx.coroutines.Job? = null

    private val supabase by lazy { com.example.network.supabase.SupabaseBridge.client }
    
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
        readReceiptsEnabled.value = SessionManager.getReadReceipts(context)
        aboutText.value = SessionManager.getAboutText(context)
        pinnedChatIds.value = SessionManager.getPinnedChats(context)
        isLocalOnlyMode.value = SessionManager.getLocalOnlyMode(context)
        darkModeEnabled.value = SessionManager.getDarkMode(context)
        allowScreenshots.value = SessionManager.isScreenshotsAllowed(context)
    }

    fun saveAllowScreenshots(enabled: Boolean) {
        allowScreenshots.value = enabled
        SessionManager.saveScreenshotsAllowed(getApplication(), enabled)
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

    fun blockUser(userId: String) {
        val updated = blockedUserIds.value.toMutableSet()
        updated.add(userId)
        blockedUserIds.value = updated
        SessionManager.saveBlockedUsers(getApplication(), updated)
        
        viewModelScope.launch {
            if (isLocalOnlyMode.value) {
                Log.d("Plenxo", "Privacy Mode: Blocked user sync disabled")
                return@launch
            }
            try {
                if (currentUserId.isNotEmpty()) {
                    supabase.postgrest["blocked_users"].insert(mapOf(
                        "blocker_id" to currentUserId,
                        "blocked_id" to userId
                    ))
                    Log.d("Plenxo", "Cloud Mode: User $userId blocked successfully")
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "CRITICAL: Silent Failure caught! Block sync failed for $userId", e)
                _errorMessage.value = "Failed to sync block status to cloud."
            }
        }
    }

    fun unblockUser(userId: String) {
        val updated = blockedUserIds.value.toMutableSet()
        updated.remove(userId)
        blockedUserIds.value = updated
        SessionManager.saveBlockedUsers(getApplication(), updated)
        
        viewModelScope.launch {
            if (isLocalOnlyMode.value) {
                Log.d("Plenxo", "Privacy Mode: Unblock user sync disabled")
                return@launch
            }
            try {
                if (currentUserId.isNotEmpty()) {
                    supabase.postgrest["blocked_users"].delete {
                        filter {
                            eq("blocker_id", currentUserId)
                            eq("blocked_id", userId)
                        }
                    }
                    Log.d("Plenxo", "Cloud Mode: User $userId unblocked successfully")
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "CRITICAL: Silent Failure caught! Unblock sync failed for $userId", e)
                _errorMessage.value = "Failed to sync unblock status to cloud."
            }
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
                    supabase.postgrest["users_data"].update({
                        set("lastSeenVisibility", visibility)
                    }) {
                        filter { eq("id", currentUserId) }
                    }
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
                    supabase.postgrest["users_data"].update({
                        set("profilePhotoVisibility", visibility)
                    }) {
                        filter { eq("id", currentUserId) }
                    }
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to update profile photo visibility on Firestore", e)
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
                    supabase.postgrest["users_data"].update({
                        set("readReceiptsEnabled", enabled)
                    }) {
                        filter { eq("id", currentUserId) }
                    }
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
                    supabase.postgrest["users_data"].update({
                        set("about", text)
                    }) {
                        filter { eq("id", currentUserId) }
                    }
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to update about text on Firestore", e)
            }
        }
    }

    fun checkAndRestoreSession() {
        try {
            val loginState = SessionManager.getLoginState(getApplication())
            if (loginState.isLoggedIn) {
                Log.d("Plenxo", "Persistent session found")
                email.value = loginState.email ?: ""
                _currentScreen.value = PlenxoScreen.PERMISSION_GATEWAY
                
                // Re-fetch user profile and listen for chats in the background
                viewModelScope.launch {
                    try {
                        val uid = com.example.network.supabase.SupabaseModule.currentUserId()
                        if (uid.isNotEmpty()) {
                            observeCurrentUserProfile()
                            val user = supabase.postgrest["users_data"].select {
                                filter {
                                    eq("id", uid)
                                }
                            }.decodeSingleOrNull<UserProfile>()
                            
                            if (user == null) {
                                Log.d("Plenxo", "User profile does not exist in Supabase. Redirecting to onboarding.")
                                _currentScreen.value = PlenxoScreen.WELCOME
                                return@launch
                            }
                            
                            displayName.value = user.displayName
                            userCode.value = user.userCode
                            phoneNumber.value = user.phoneNumber
                            
                            if (user.profilePicUrl.isNotEmpty()) {
                                if (user.profilePicUrl.startsWith("http")) {
                                    avatarType.value = "gallery"
                                    galleryImageUriString.value = user.profilePicUrl
                                } else if (user.profilePicUrl.contains(":")) {
                                    avatarType.value = "placeholder"
                                    val avatarList = maleAvatars + femaleAvatars
                                    val idx = avatarList.indexOfFirst { "${it.first}:${it.second}" == user.profilePicUrl }
                                    if (idx != -1) {
                                        selectedAvatarIndex.value = idx
                                    }
                                } else {
                                    avatarType.value = "emoji"
                                    selectedEmoji.value = user.profilePicUrl
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("Plenxo", "Failed to restore user profile from persistent session", e)
                    } finally {
                        startListeningForChats()
                    }
                }
            } else {
                Log.d("Plenxo", "No persistent session found, showing default screen")
            }
        } catch (e: SecurityException) {
            Log.e("Plenxo", "Security error during session restoration: ${e.message}")
            _currentScreen.value = PlenxoScreen.WELCOME
        } catch (e: Exception) {
            Log.e("Plenxo", "Unexpected error during session restoration", e)
            _currentScreen.value = PlenxoScreen.WELCOME
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
                _currentScreen.value = PlenxoScreen.HOME
            } else {
                _currentScreen.value = PlenxoScreen.LOGIN
            }
            Log.d("PlenxoViewModel", "Crash recovery executed successfully. Target screen: ${_currentScreen.value}")
        } catch (e: Exception) {
            Log.e("PlenxoViewModel", "Error in handleCrashRecovery", e)
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
                    .replace("io.github.jan.supabase.exceptions.RestException:", "")
                    .replace("io.github.jan.supabase.exceptions.", "")
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
                supabase.postgrest["users_data"].update({
                    set("publicKey", publicKey)
                }) {
                    filter { eq("id", uid) }
                }
            } catch (e: Exception) {
                Log.e("Security", "Failed to register E2EE key", e)
            }
        }
    }

    private suspend fun invokeSupabaseEdgeFunction(
        userEmail: String,
        subject: String,
        html: String,
        textBody: String,
        otpCode: String,
        type: String
    ) {
        try {
            com.example.network.email.BrevoEmailService.getInstance().sendOtpEmail(
                context = getApplication(),
                toEmail = userEmail,
                otpCode = otpCode
            )
        } catch (e: Exception) {
            Log.w("PlenxoAuthEmail", "Email dispatch note: ${e.message}")
        }
    }

    private fun dispatchSignupOtpEmail(userEmail: String, otpCode: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Log.d("PlenxoAuthEmail", "Dispatching Signup OTP email to $userEmail via Brevo REST API...")
                val success = com.example.network.email.BrevoEmailService.getInstance().sendOtpEmail(
                    context = getApplication(),
                    toEmail = userEmail,
                    otpCode = otpCode
                )
                Log.d("PlenxoAuthEmail", "Signup OTP email dispatch success: $success")
            } catch (e: Exception) {
                Log.e("PlenxoAuthEmail", "Failed to dispatch signup OTP email: ${e.localizedMessage}", e)
            }
        }
    }

    private fun dispatchLoginOtpEmail(userEmail: String, otpCode: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Log.d("PlenxoAuthEmail", "Dispatching Login OTP email to $userEmail via Brevo REST API...")
                val success = com.example.network.email.BrevoEmailService.getInstance().sendOtpEmail(
                    context = getApplication(),
                    toEmail = userEmail,
                    otpCode = otpCode
                )
                Log.d("PlenxoAuthEmail", "Login OTP email dispatch success: $success")
            } catch (e: Exception) {
                Log.e("PlenxoAuthEmail", "Failed to dispatch login OTP email: ${e.localizedMessage}", e)
            }
        }
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
                    val nowIso = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        java.time.Instant.now().toString()
                    } else {
                        java.util.Date().toString()
                    }
                    val brand = android.os.Build.BRAND
                    val model = android.os.Build.MODEL
                    val osVersion = android.os.Build.VERSION.RELEASE
                    val deviceInfo = "$brand $model (Android OS v$osVersion)"

                    val html = com.example.network.email.TemplateRenderer.renderTemplate(
                        context = getApplication(),
                        templateName = "login-alert.html",
                        replacements = mapOf(
                            "LOGIN_TIME" to nowIso,
                            "DEVICE_INFO" to deviceInfo
                        )
                    )

                    val req = com.example.network.email.SendEmailRequest(
                        to = userEmail,
                        subject = "New Sign-In Activity - Plenxo",
                        htmlBody = html
                    )

                    val res = com.example.network.email.BrevoEmailEngine.emailService.sendEmail(req.to, req.subject, req.htmlBody)
                    Log.d("SecurityService", "Login alert email dispatch result: $res")

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            getApplication(),
                            "Security notification sent to $userEmail (5th Login Alert)",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("SecurityService", "Failed to trigger login alert email: ${e.message}", e)
            }
        }
    }

    private fun auditSession() {
        val uid = currentUserId
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val sessionId = java.util.UUID.randomUUID().toString()
                val session = com.example.model.ActiveSession(
                    sessionId = sessionId,
                    deviceModel = android.os.Build.MODEL,
                    ipAddress = "127.0.0.1", // In a real app, fetch real IP
                    timestamp = System.currentTimeMillis()
                )
                // Map to a table active_sessions
                val sessionData = mapOf(
                    "sessionId" to session.sessionId,
                    "deviceModel" to session.deviceModel,
                    "ipAddress" to session.ipAddress,
                    "timestamp" to session.timestamp,
                    "user_id" to uid
                )
                supabase.postgrest["active_sessions"].insert(sessionData)
                
                // Save sessionId locally for self-auditing if needed
                com.example.util.SessionManager.saveSessionId(getApplication(), sessionId)
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
                supabase.postgrest["active_sessions"].delete {
                    filter {
                        eq("sessionId", sessionId)
                        eq("user_id", uid)
                    }
                }
            } catch (e: Exception) {
                Log.e("Security", "Failed to terminate session", e)
            }
        }
    }

    fun logout() {
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            Log.e("Plenxo", "Firebase signOut failed", e)
        }
        SessionManager.clearLoginState(getApplication())
        navigateToScreen(PlenxoScreen.LOGIN, clearHistory = true)
    }

    private fun startListeningToSessions() {
        val uid = currentUserId
        if (uid.isEmpty()) return
        sessionsListener?.cancel()
        sessionsListener = viewModelScope.launch {
            val channel = supabase.channel("active_sessions_$uid")
            val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "active_sessions"
                filter = "user_id=eq.$uid"
            }
            
            // Initial fetch
            try {
                val sessionList = supabase.postgrest["active_sessions"].select {
                    filter {
                        eq("user_id", uid)
                    }
                }.decodeList<com.example.model.ActiveSession>()
                activeSessions.value = sessionList
            } catch (e: Exception) {
                Log.e("Security", "Initial session fetch failed", e)
            }

            flow.collect {
                try {
                    val sessionList = supabase.postgrest["active_sessions"].select {
                        filter {
                            eq("user_id", uid)
                        }
                    }.decodeList<com.example.model.ActiveSession>()
                    activeSessions.value = sessionList
                    
                    // Check if current session still exists
                    val currentSessionId = com.example.util.SessionManager.getSessionId(getApplication())
                    if (currentSessionId != null && sessionList.none { it.sessionId == currentSessionId }) {
                        logout()
                    }
                } catch (e: Exception) {
                    Log.e("Security", "Session update fetch failed", e)
                }
            }
        }
    }

    fun navigateToSignup() {
        isPrivacyAccepted.value = false
        _currentScreen.value = PlenxoScreen.SIGNUP
        _errorMessage.value = null
    }

    fun navigateToLogin() {
        _currentScreen.value = PlenxoScreen.LOGIN
        _errorMessage.value = null
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
                val rawEmail = email.value.trim()
                val rawPassword = password.value

                if (!isTermsAccepted.value) {
                    _errorMessage.value = "Please accept the Terms & Conditions to proceed."
                    _isLoading.value = false
                    return@launch
                }
                if (rawEmail.isEmpty()) {
                    _errorMessage.value = "Please enter your email address."
                    _isLoading.value = false
                    return@launch
                }
                if (rawPassword.isEmpty()) {
                    _errorMessage.value = "Please enter your password."
                    _isLoading.value = false
                    return@launch
                }

                try {
                    val authResult = FirebaseAuth.getInstance().signInWithEmailAndPassword(rawEmail, rawPassword).await()
                    Log.d("Plenxo", "Successfully logged in with FirebaseAuth")
                    
                    val uid = authResult.user?.uid ?: FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    
                    // Track login count in SharedPreferences
                    val prefs = getApplication<android.app.Application>()
                        .getSharedPreferences("plenxo_login_stats", android.content.Context.MODE_PRIVATE)
                    val currentCount = prefs.getInt("login_count_$rawEmail", 0) + 1
                    prefs.edit().putInt("login_count_$rawEmail", currentCount).apply()

                    // Reset failed logins & record terms acceptance on success
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
                    } catch (e: Exception) {
                        Log.w("Plenxo", "Firestore terms update on login note: ${e.message}")
                    }

                    try {
                        supabase.postgrest["users_data"].update({
                            set("terms_accepted", true)
                            set("terms_accepted_at", isoTimestamp)
                        }) {
                            filter {
                                eq("id", uid)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("Plenxo", "Supabase terms update on login note: ${e.message}")
                    }
                    
                    SessionManager.saveLoginState(getApplication(), uid, rawEmail)
                    saveFcmToken()
                    try { triggerLoginAlertEmail(rawEmail) } catch (ignored: Exception) {}
                    try { auditSession() } catch (ignored: Exception) {}
                    try { registerE2EEKey() } catch (ignored: Exception) {}
                    
                    if (uid.isNotEmpty()) {
                        try {
                            val userDocDetailed = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
                            if (userDocDetailed.exists()) {
                                val user = userDocDetailed.toObject(User::class.java)
                                if (user != null) {
                                    displayName.value = user.displayName
                                    email.value = user.email
                                    userCode.value = user.userCode
                                    phoneNumber.value = user.phoneNumber
                                    selectedTheme.value = user.themePreference
                                    
                                    if (user.profilePicUrl.isNotEmpty()) {
                                        if (user.profilePicUrl.startsWith("http")) {
                                            avatarType.value = "gallery"
                                            galleryImageUriString.value = user.profilePicUrl
                                        } else if (user.profilePicUrl.contains(":")) {
                                            avatarType.value = "placeholder"
                                            val avatarList = maleAvatars + femaleAvatars
                                            val idx = avatarList.indexOfFirst { "${it.first}:${it.second}" == user.profilePicUrl }
                                            if (idx != -1) {
                                                selectedAvatarIndex.value = idx
                                            }
                                        } else {
                                            avatarType.value = "emoji"
                                            selectedEmoji.value = user.profilePicUrl
                                        }
                                    }
                                }
                            } else {
                                Log.d("Plenxo", "User profile missing on login. Redirecting to setup.")
                                _currentScreen.value = PlenxoScreen.WELCOME
                                return@launch
                            }
                        } catch (e: Exception) {
                            Log.e("Plenxo", "Failed to load user profile on login", e)
                        }
                    }
                    
                    try { startListeningForChats() } catch (ignored: Exception) {}
                    com.example.util.SessionManager.saveCaptchaVerified(getApplication(), false)
                    com.example.util.AppLockManager.setLocked(getApplication(), false)
                    _currentScreen.value = PlenxoScreen.PERMISSION_GATEWAY
                } catch (authEx: Exception) {
                    Log.e("SUPABASE_AUTH", "Login Exception Details: ", authEx)
                    val mapped = mapSupabaseError(authEx)
                    val rawMessage = authEx.localizedMessage ?: authEx.message ?: authEx.toString()
                    val displayError = when {
                        rawMessage.contains("invalid login credentials", ignoreCase = true) ||
                        rawMessage.contains("invalid_credentials", ignoreCase = true) ||
                        rawMessage.contains("invalid_grant", ignoreCase = true) -> {
                            "Incorrect email or password. Please try again."
                        }
                        rawMessage.contains("unknown error", ignoreCase = true) ||
                        rawMessage.contains("auth api error", ignoreCase = true) ||
                        mapped.contains("unknown error", ignoreCase = true) ||
                        mapped.contains("auth api error", ignoreCase = true) -> {
                            "Login failed. Please verify your email and password, or try registering if you don't have an account."
                        }
                        mapped.isNotBlank() -> mapped
                        else -> rawMessage
                    }
                    _errorMessage.value = displayError
                }
            } catch (e: Exception) {
                Log.e("SUPABASE_AUTH", "Unhandled Login Exception Details: ", e)
                val rawMessage = e.localizedMessage ?: e.message ?: e.toString()
                _errorMessage.value = if (rawMessage.isBlank()) "Error logging in" else rawMessage
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onSignUpClicked() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                clearError()
                val rawEmail = email.value.trim()
                val rawPassword = password.value
                val rawConfirmPassword = confirmPassword.value

                if (!isTermsAccepted.value) {
                    _errorMessage.value = "Please accept the Terms & Conditions to proceed."
                    _isLoading.value = false
                    return@launch
                }
                if (rawEmail.isEmpty() || !rawEmail.contains("@") || !rawEmail.contains(".")) {
                    _errorMessage.value = "Please enter a valid email address containing '@' and '.'"
                    _isLoading.value = false
                    return@launch
                }
                if (rawPassword.isEmpty()) {
                    _errorMessage.value = "Please enter your password."
                    _isLoading.value = false
                    return@launch
                }
                if (rawConfirmPassword.isEmpty()) {
                    _errorMessage.value = "Please enter your confirm password."
                    _isLoading.value = false
                    return@launch
                }
                if (rawPassword.length < 6) {
                    _errorMessage.value = "Password must be at least 6 characters long."
                    _isLoading.value = false
                    return@launch
                }
                if (rawPassword != rawConfirmPassword) {
                    _errorMessage.value = "Passwords do not match."
                    _isLoading.value = false
                    return@launch
                }

                try {
                    val authResult = FirebaseAuth.getInstance().createUserWithEmailAndPassword(rawEmail, rawPassword).await()
                    Log.d("SUPABASE_AUTH", "Signup successful.")
                    val uid = authResult.user?.uid ?: FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    
                    if (uid.isNotEmpty()) {
                        SessionManager.saveLoginState(getApplication(), uid, rawEmail)
                    }

                    password.value = ""
                    confirmPassword.value = ""
                    clearError()

                    // Generate OTP and dispatch verification email
                    val generatedCode = (100000..999999).random().toString()
                    _generatedOtp.value = generatedCode
                    _isLoginOtpChallenge.value = false
                    enteredOtp.value = ""

                    dispatchSignupOtpEmail(rawEmail, generatedCode)
                    startTimer()

                    _isLoading.value = false
                    _currentScreen.value = PlenxoScreen.OTP_VERIFICATION
                } catch (authEx: Exception) {
                    Log.e("SUPABASE_AUTH", "Signup Exception Details: ", authEx)
                    password.value = ""
                    confirmPassword.value = ""
                    val mapped = mapSupabaseError(authEx)
                    val rawMessage = authEx.localizedMessage ?: authEx.message ?: authEx.toString()
                    val displayError = when {
                        rawMessage.contains("already registered", ignoreCase = true) ||
                        rawMessage.contains("already exists", ignoreCase = true) ||
                        rawMessage.contains("User already registered", ignoreCase = true) ||
                        mapped.contains("already exists", ignoreCase = true) -> {
                            "An account with this email already exists. Please log in instead."
                        }
                        rawMessage.contains("Password should be", ignoreCase = true) ||
                        rawMessage.contains("weak", ignoreCase = true) -> {
                            "Password is too weak. Please choose a stronger password."
                        }
                        rawMessage.contains("invalid email", ignoreCase = true) -> {
                            "Invalid email address format."
                        }
                        rawMessage.contains("unknown error", ignoreCase = true) ||
                        rawMessage.contains("auth api error", ignoreCase = true) ||
                        mapped.contains("unknown error", ignoreCase = true) ||
                        mapped.contains("auth api error", ignoreCase = true) -> {
                            "An account with this email may already exist, or sign-up is temporarily unavailable. Please try logging in."
                        }
                        mapped.isNotBlank() -> mapped
                        else -> rawMessage
                    }
                    _errorMessage.value = displayError
                    _isLoading.value = false
                }

            } catch (e: Exception) {
                Log.e("SUPABASE_AUTH", "Signup Exception Details: ", e)
                val rawMessage = e.localizedMessage ?: e.message ?: e.toString()
                _errorMessage.value = if (rawMessage.isBlank()) "Error during sign up" else rawMessage
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Timer & Verification logic
    private fun startTimer() {
        timerJob?.cancel()
        _secondsRemaining.value = 60
        _isTimerRunning.value = true
        timerJob = viewModelScope.launch {
            while (_secondsRemaining.value > 0) {
                delay(1000)
                _secondsRemaining.value -= 1
            }
            _isTimerRunning.value = false
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
                val entered = enteredOtp.value.trim()
                if (entered.length != 6 || !entered.all { it.isDigit() }) {
                    _errorMessage.value = "Validation Error: Please enter a valid 6-digit verification code."
                    _isLoading.value = false
                    return@launch
                }

                // Check 60 second timer expiration
                if (_secondsRemaining.value <= 0) {
                    _errorMessage.value = "Verification Expired: The 60-second time limit has expired. Please request a new OTP code."
                    _isLoading.value = false
                    return@launch
                }

                val rawEmail = email.value.trim()
                var isVerified = false

                if (_generatedOtp.value.isNotEmpty() && entered == _generatedOtp.value) {
                    isVerified = true
                    Log.d("Plenxo", "OTP verified via generated code match")
                }

                if (isVerified) {
                    failedOtpAttempts = 0
                    clearError()
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

                    if (_isLoginOtpChallenge.value) {
                        // Complete 5th login challenge flow
                        SessionManager.saveLoginState(getApplication(), uid, rawEmail)
                        triggerLoginAlertEmail(rawEmail)
                        auditSession()
                        registerE2EEKey()
                        startListeningForChats()
                        com.example.util.SessionManager.saveCaptchaVerified(getApplication(), false)
                        com.example.util.AppLockManager.setLocked(getApplication(), false)
                        _currentScreen.value = PlenxoScreen.HOME
                    } else {
                        // Complete Signup OTP flow -> navigate to Profile Setup
                        if (uid.isNotEmpty()) {
                            val generatedCode = (100000..999999).random().toString()
                            val initialUser = User(
                                uid = uid,
                                email = rawEmail,
                                displayName = rawEmail.substringBefore("@"),
                                userCode = generatedCode,
                                lastLoginTimestamp = System.currentTimeMillis()
                            )
                            try {
                                firestore.collection("users").document(uid).set(initialUser).await()
                                Log.d("Plenxo", "Initial Firestore profile created on OTP verification")
                            } catch (e: Exception) {
                                Log.e("Plenxo", "Failed to create initial Firestore profile: ${e.message}", e)
                            }
                        }
                        SessionManager.saveLoginState(getApplication(), uid, rawEmail)
                        _currentScreen.value = PlenxoScreen.PROFILE_SETUP
                    }
                } else {
                    failedOtpAttempts++
                    if (failedOtpAttempts >= 3) {
                        _isOtpButtonFrozen.value = true
                        _errorMessage.value = "Security Block: Too many failed OTP attempts. Verification is frozen for 60 seconds."
                        viewModelScope.launch {
                            delay(60_000)
                            _isOtpButtonFrozen.value = false
                            failedOtpAttempts = 0
                        }
                    } else {
                        _errorMessage.value = "Invalid Verification Code. Please check your email and try again."
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
    fun navigateToAvatarSetup() {
        _currentScreen.value = PlenxoScreen.AVATAR_SETUP
    }

    fun navigateToFinalDetails() {
        val randomCode = (100000..999999).random().toString()
        userCode.value = randomCode
        _currentScreen.value = PlenxoScreen.FINAL_DETAILS
    }

    fun onFinishSetupClicked() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val name = displayName.value.trim()
                if (name.isEmpty()) {
                    _errorMessage.value = "Validation Error: Display Name cannot be empty."
                    _isLoading.value = false
                    return@launch
                }

                // --- AUTH STATE GUARD (Vector 1 Fix) ---
                val currentUid = com.example.network.supabase.SupabaseModule.currentUserId()
                if (currentUid.isEmpty()) {
                    Log.e("Plenxo", "onFinishSetupClicked: Supabase user is not authenticated. Cannot save profile.")
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
                } catch (dbEx: Exception) {
                    Log.e("Plenxo", "RTDB write failed (non-fatal): ${dbEx.message}", dbEx)
                }

                // --- FIRESTORE WRITE: NO FALLBACK TIMEOUT (Vector 6 Fix) ---
                val firestoreUser = User(
                    uid = currentUid,
                    email = email.value.trim(),
                    displayName = name,
                    profilePicUrl = avatarValue,
                    themePreference = selectedTheme.value,
                    userCode = userCode.value,
                    dob = dob,
                    phoneNumber = phoneNumber.value.trim(),
                    totalAccumulatedSeconds = totalAccumulatedSeconds.value,
                    selectedRingId = selectedRingId.value
                )

                try {
                    firestore.collection("users").document(currentUid).set(firestoreUser).await()
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
                _currentScreen.value = PlenxoScreen.PERMISSION_GATEWAY

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
        
        _currentScreen.value = PlenxoScreen.SIGNUP
    }

    // Helper to resend OTP
    fun resendOtp() {
        val rawEmail = email.value.trim()
        if (rawEmail.isEmpty()) {
            _errorMessage.value = "Email address is required to resend verification code."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val newCode = (100000..999999).random().toString()
                _generatedOtp.value = newCode
                enteredOtp.value = ""
                clearError()

                if (_isLoginOtpChallenge.value) {
                    dispatchLoginOtpEmail(rawEmail, newCode)
                } else {
                    dispatchSignupOtpEmail(rawEmail, newCode)
                }

                startTimer()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        getApplication(),
                        "A new 6-digit verification code has been sent to $rawEmail",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to resend OTP code: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateProfile(newDisplayName: String, newAbout: String) {
        displayName.value = newDisplayName
        updateAboutText(newAbout)
        val avatarUrl = uploadedProfilePicUrl.value
        viewModelScope.launch {
            try {
                if (currentUserId.isNotEmpty()) {
                    val updates = mutableMapOf<String, Any>("displayName" to newDisplayName, "about" to newAbout)
                    if (!avatarUrl.isNullOrEmpty()) {
                        updates["profilePicUrl"] = avatarUrl
                        updates["avatar_url"] = avatarUrl
                    }
                    firestore.collection("users_data").document(currentUserId)
                        .set(updates, com.google.firebase.firestore.SetOptions.merge())
                    
                    try {
                        firestore.collection("users").document(currentUserId)
                            .update(mapOf("displayName" to newDisplayName, "about" to newAbout))
                    } catch (e: Exception) {}
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Cloud profile sync failed: ${e.message}")
            }
        }
    }

    fun fetchPendingInvitations() {
        val uid = currentUserId
        if (uid.isEmpty()) return
        invitationsListener?.cancel()
        invitationsListener = viewModelScope.launch {
            val channel = supabase.channel("invitations_$uid")
            val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "invitations"
                filter = "receiverId=eq.$uid"
            }
            
            // Initial fetch
            try {
                val invitationList = supabase.postgrest["invitations"].select {
                    filter {
                        eq("receiverId", uid)
                        eq("status", "PENDING")
                    }
                }.decodeList<Invitation>()
                _pendingInvitations.value = invitationList
                Log.d("Plenxo", "Fetched pending invitations: ${invitationList.size}")
            } catch (e: Exception) {
                Log.e("Plenxo", "Initial invitations fetch failed", e)
            }

            flow.collect {
                try {
                    val invitationList = supabase.postgrest["invitations"].select {
                        filter {
                            eq("receiverId", uid)
                            eq("status", "PENDING")
                        }
                    }.decodeList<Invitation>()
                    _pendingInvitations.value = invitationList
                } catch (e: Exception) {
                    Log.e("Plenxo", "Invitations update fetch failed", e)
                }
            }
        }
    }

    fun acceptInvitation(invitation: Invitation, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val requestId = invitation.requestId
                if (requestId.isEmpty()) return@launch
                
                // 1. Update status to ACCEPTED
                supabase.postgrest["invitations"].update({
                    set("status", "ACCEPTED")
                }) {
                    filter { eq("requestId", requestId) }
                }
                
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
                supabase.postgrest["chats"].upsert(chatRoom)
                
                onComplete()
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to accept invitation", e)
            }
        }
    }

    fun fetchPendingFriendRequests() {
        val uid = currentUserId
        if (uid.isEmpty()) return
        friendRequestsListener?.cancel()
        friendRequestsListener = viewModelScope.launch {
            val channel = supabase.channel("friend_requests_$uid")
            val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "friend_requests"
                filter = "receiverUid=eq.$uid"
            }
            
            // Initial fetch
            try {
                val requestList = supabase.postgrest["friend_requests"].select {
                    filter {
                        eq("receiverUid", uid)
                        eq("status", "pending")
                    }
                }.decodeList<FriendRequest>()
                _pendingFriendRequests.value = requestList
                Log.d("Plenxo", "Fetched pending friend requests: ${requestList.size}")

                // Preload sender profiles into usersCache
                requestList.forEach { req ->
                    val senderUid = req.senderUid.ifEmpty { req.senderId }
                    if (senderUid.isNotEmpty() && !_usersCache.value.containsKey(senderUid)) {
                        try {
                            val u = supabase.postgrest["users_data"].select {
                                filter { eq("id", senderUid) }
                            }.decodeSingleOrNull<User>()
                            if (u != null) {
                                val updatedCache = _usersCache.value.toMutableMap()
                                updatedCache[senderUid] = u
                                _usersCache.value = updatedCache
                            }
                        } catch (ex: Exception) {
                            Log.e("Plenxo", "Failed to preload friend request sender: $senderUid", ex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Initial friend requests fetch failed", e)
            }

            flow.collect {
                try {
                    val requestList = supabase.postgrest["friend_requests"].select {
                        filter {
                            eq("receiverUid", uid)
                            eq("status", "pending")
                        }
                    }.decodeList<FriendRequest>()
                    _pendingFriendRequests.value = requestList
                } catch (e: Exception) {
                    Log.e("Plenxo", "Friend requests update fetch failed", e)
                }
            }
        }
    }

    fun acceptFriendRequest(request: FriendRequest, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val requestId = request.requestId
                val senderUid = request.senderUid.ifEmpty { request.senderId }
                val receiverUid = currentUserId
                if (requestId.isEmpty() || senderUid.isEmpty()) return@launch

                // 1. Update the request document status to "accepted"
                supabase.postgrest["friend_requests"].update({
                    set("status", "accepted")
                }) {
                    filter {
                        eq("requestId", requestId)
                        eq("receiverUid", receiverUid)
                    }
                }

                // 2. Add each other to `contacts` collection
                supabase.postgrest["contacts"].insert(
                    mapOf("id" to "${senderUid}_${receiverUid}", "user_id" to senderUid, "contact_id" to receiverUid)
                )
                supabase.postgrest["contacts"].insert(
                    mapOf("id" to "${receiverUid}_${senderUid}", "user_id" to receiverUid, "contact_id" to senderUid)
                )

                // 3. Create (or retrieve) the 1-on-1 Chat Room in `chats` collection
                val chatId = getChatRoomId(senderUid, receiverUid)
                val chat = ChatRoom(
                    chatId = chatId,
                    participantUids = listOf(senderUid, receiverUid),
                    lastMessage = "You are now connected! Start chatting.",
                    lastMessageTimestamp = System.currentTimeMillis(),
                    unreadCounts = mapOf(senderUid to 0, receiverUid to 0)
                )
                supabase.postgrest["chats"].upsert(chat)

                // 4. Ensure sender's profile is in usersCache before navigation
                if (!_usersCache.value.containsKey(senderUid)) {
                    try {
                        val senderProfile = supabase.postgrest["users_data"].select {
                            filter { eq("id", senderUid) }
                        }.decodeSingleOrNull<com.example.model.User>()
                        if (senderProfile != null) {
                            _usersCache.value = _usersCache.value + (senderUid to senderProfile)
                        }
                    } catch (ex: Exception) {
                        Log.w("Plenxo", "Could not preload sender profile: ${ex.message}")
                    }
                }

                // 5. INSTANTLY navigate to the Chatting Screen
                val cachedName = _usersCache.value[senderUid]?.displayName
                    ?: request.senderName.ifEmpty { "Chat" }

                currentChatId.value = chatId
                currentChatRecipientName.value = cachedName
                currentChatRecipientUid.value = senderUid

                navigateToScreen(PlenxoScreen.CHAT_DETAIL)
                startListeningForMessages(chatId)

                onComplete()
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to accept friend request", e)
                _errorMessage.value = "Failed to accept request: ${e.localizedMessage}"
            }
        }
    }

    fun rejectFriendRequest(request: FriendRequest, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val requestId = request.requestId
                val receiverUid = currentUserId
                if (requestId.isEmpty()) return@launch

                // Update status to "rejected" (soft-delete, for audit trail)
                supabase.postgrest["friend_requests"].update({
                    set("status", "rejected")
                }) {
                    filter {
                        eq("requestId", requestId)
                        eq("receiverUid", receiverUid)
                    }
                }

                // Immediately refresh pending requests list
                fetchPendingFriendRequests()

                onComplete()
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to reject friend request", e)
            }
        }
    }

    private fun startListeningToCurrentUserProfile() {
        val uid = currentUserId
        if (uid.isEmpty()) return
        currentUserListener?.cancel()
        currentUserListener = viewModelScope.launch {
            val channel = supabase.channel("user_profile_$uid")
            val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "users_data"
                filter = "id=eq.$uid"
            }
            
            // Initial fetch
            try {
                val user = supabase.postgrest["users_data"].select {
                    filter { eq("id", uid) }
                }.decodeSingleOrNull<User>()
                
                if (user != null) {
                    displayName.value = user.displayName
                    email.value = user.email
                    userCode.value = user.userCode
                    phoneNumber.value = user.phoneNumber
                    selectedTheme.value = user.themePreference
                    selectedRingId.value = user.selectedRingId
                    profileRingId.value = user.profileRingId
                    com.example.util.SessionManager.saveProfileRingId(getApplication(), user.profileRingId)
                    totalAccumulatedSeconds.value = user.totalAccumulatedSeconds
                    
                    val current = currentUserProfile.value
                    currentUserProfile.value = (current ?: UserProfile(id = uid, uid = uid)).copy(
                        displayName = user.displayName,
                        email = user.email,
                        userCode = user.userCode,
                        phoneNumber = user.phoneNumber,
                        profileRingId = user.profileRingId,
                        selectedRingId = user.selectedRingId,
                        profilePicUrl = user.profilePicUrl
                    )

                    if (user.profilePicUrl.isNotEmpty()) {
                        if (user.profilePicUrl.startsWith("http")) {
                            avatarType.value = "gallery"
                            galleryImageUriString.value = user.profilePicUrl
                        } else if (user.profilePicUrl.contains(":")) {
                            avatarType.value = "placeholder"
                            val avatarList = maleAvatars + femaleAvatars
                            val idx = avatarList.indexOfFirst { "${it.first}:${it.second}" == user.profilePicUrl }
                            if (idx != -1) {
                                selectedAvatarIndex.value = idx
                            }
                        } else {
                            avatarType.value = "emoji"
                            selectedEmoji.value = user.profilePicUrl
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Initial profile fetch failed", e)
            }

            flow.collect {
                // Fetch updated profile
                try {
                    val user = supabase.postgrest["users_data"].select {
                        filter { eq("id", uid) }
                    }.decodeSingleOrNull<User>()
                    if (user != null) {
                        displayName.value = user.displayName
                        email.value = user.email
                        userCode.value = user.userCode
                        phoneNumber.value = user.phoneNumber
                        selectedTheme.value = user.themePreference
                        selectedRingId.value = user.selectedRingId
                        profileRingId.value = user.profileRingId
                        com.example.util.SessionManager.saveProfileRingId(getApplication(), user.profileRingId)
                        totalAccumulatedSeconds.value = user.totalAccumulatedSeconds

                        val current = currentUserProfile.value
                        currentUserProfile.value = (current ?: UserProfile(id = uid, uid = uid)).copy(
                            displayName = user.displayName,
                            email = user.email,
                            userCode = user.userCode,
                            phoneNumber = user.phoneNumber,
                            profileRingId = user.profileRingId,
                            selectedRingId = user.selectedRingId,
                            profilePicUrl = user.profilePicUrl
                        )
                    }
                } catch (e: Exception) {
                    Log.e("Plenxo", "Profile update fetch failed", e)
                }
            }
        }
    }

    private fun startListeningToContacts() {
        val uid = currentUserId
        if (uid.isEmpty()) return
        contactsListener?.cancel()
        contactsListener = viewModelScope.launch {
            val channel = supabase.channel("contacts_$uid")
            val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "contacts"
                filter = "user_id=eq.$uid"
            }
            
            // Initial fetch
            try {
                val list = supabase.postgrest["contacts"].select {
                    filter { eq("user_id", uid) }
                }.decodeList<Map<String, String>>()
                val contactIds = list.mapNotNull { it["contact_id"] }.toSet()
                _contactsSet.value = contactIds
            } catch (e: Exception) {
                Log.e("Plenxo", "Initial contacts fetch failed", e)
            }

            flow.collect {
                try {
                    val list = supabase.postgrest["contacts"].select {
                        filter { eq("user_id", uid) }
                    }.decodeList<Map<String, String>>()
                    val contactIds = list.mapNotNull { it["contact_id"] }.toSet()
                    _contactsSet.value = contactIds
                } catch (e: Exception) {
                    Log.e("Plenxo", "Contacts update fetch failed", e)
                }
            }
        }
    }

    fun startListeningForChats() {
        val uid = currentUserId
        if (uid.isEmpty()) return
        
        startListeningToCurrentUserProfile()
        fetchPendingInvitations()
        fetchPendingFriendRequests()
        startListeningToContacts()
        
        chatsListener?.cancel()
        chatsListener = viewModelScope.launch {
            val channel = supabase.channel("chats_$uid")
            val flow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "chats"
                // Assuming postgres array containment filter
                // Or we can fetch all and filter client side if filter is complex
            }
            
            // Initial fetch
            try {
                // In Supabase Postgrest, we use 'cs' (contains) for array column
                val chatList = supabase.postgrest["chats"].select {
                    filter {
                        // CS filter for participantUids array
                        // IO Github Jan library might use different syntax
                    }
                }.decodeList<ChatRoom>().filter { it.participantUids.contains(uid) }
                
                _chats.value = chatList.sortedByDescending { it.lastMessageTimestamp ?: 0 }
                fetchUsersForChats(chatList)
            } catch (e: Exception) {
                Log.e("Plenxo", "Initial chats fetch failed", e)
            }

            flow.collect {
                try {
                    val chatList = supabase.postgrest["chats"].select().decodeList<ChatRoom>().filter { it.participantUids.contains(uid) }
                    _chats.value = chatList.sortedByDescending { it.lastMessageTimestamp ?: 0 }
                    fetchUsersForChats(chatList)
                } catch (e: Exception) {
                    Log.e("Plenxo", "Chats update fetch failed", e)
                }
            }
        }
    }

    fun rejectInvitation(invitation: Invitation, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val requestId = invitation.requestId
                if (requestId.isEmpty()) return@launch
                supabase.postgrest["invitations"].update({
                    set("status", "REJECTED")
                }) {
                    filter { eq("requestId", requestId) }
                }
                onComplete()
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to reject invitation", e)
            }
        }
    }

    fun fetchUsersForChats(chatList: List<ChatRoom>) {
        val currentUid = currentUserId
        val uidsToFetch = chatList.flatMap { it.participantUids }.filter { it != currentUid && !_usersCache.value.containsKey(it) }.distinct()
        if (uidsToFetch.isEmpty()) return
        
        viewModelScope.launch {
            uidsToFetch.forEach { uid ->
                try {
                    val user = supabase.postgrest["users_data"].select {
                        filter { eq("id", uid) }
                    }.decodeSingleOrNull<User>()
                    if (user != null) {
                        _usersCache.value = _usersCache.value + (uid to user)
                    }
                } catch (e: Exception) {
                    Log.e("Plenxo", "Failed to fetch user $uid", e)
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
                // Fetch current user profile if not cached in flow
                if (currentUserProfile.value == null) {
                    val user = supabase.postgrest["users_data"].select {
                        filter { eq("id", currentUid) }
                    }.decodeSingleOrNull<UserProfile>()
                    currentUserProfile.value = user
                }

                // Do not query all users for suggestions
                discoveryUsers.value = emptyList()

                // Get requested user ids
                val list = supabase.postgrest["invitations"].select {
                    filter {
                        eq("senderId", currentUid)
                        eq("status", "PENDING")
                    }
                }.decodeList<Invitation>()
                
                val requestedIds = list.map { it.receiverId }.toSet()
                discoveryRequestedUserIds.value = requestedIds
                
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to preload discovery users", e)
            }
        }
    }

    fun updateDiscoverySearchQuery(query: String) {
        val filtered = query.filter { it.isDigit() }.take(15)
        discoverySearchQuery.value = filtered
        discoverySearchJob?.cancel()
    }

    fun searchUserByCode() {
        val queryInput = discoverySearchQuery.value
        if (queryInput.isBlank()) {
            _errorMessage.value = "Please enter a valid phone number."
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val results = supabase.postgrest["users_data"].select {
                    filter {
                        eq("phoneNumber", queryInput)
                    }
                }.decodeList<UserProfile>()
                    .filter { it.uid != currentUserId }
                discoveryUsers.value = results
                
                if (results.isEmpty()) {
                    _errorMessage.value = "No user found with phone number $queryInput."
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Search failed", e)
                _errorMessage.value = "Search failed: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendFriendRequest(receiverId: String) {
        val currentUid = currentUserId
        if (currentUid.isEmpty()) return

        val currentUserDoc = currentUserProfile.value
        val currentDisplayName = currentUserDoc?.displayName ?: "User"
        val currentPhoneNumber = currentUserDoc?.phoneNumber ?: ""
        val currentProfilePic = currentUserDoc?.profilePicUrl ?: ""

        val requestId = java.util.UUID.randomUUID().toString()

        // SINGLE COLLECTION: friend_requests (drop the invitations dual-write)
        val requestData = mapOf(
            "requestId" to requestId,
            "id" to requestId,           // ← REQUIRED for TableBridge to set doc ID correctly
            "senderId" to currentUid,
            "senderUid" to currentUid,
            "receiverId" to receiverId,
            "receiverUid" to receiverId,
            "status" to "pending",
            "timestamp" to System.currentTimeMillis(),
            "senderName" to currentDisplayName,
            "senderPhone" to currentPhoneNumber,
            "senderProfilePic" to currentProfilePic
        )

        viewModelScope.launch {
            try {
                supabase.postgrest["friend_requests"].insert(requestData)
                discoveryRequestedUserIds.value = discoveryRequestedUserIds.value + receiverId
                val newRequest = FriendRequest(
                    requestId = requestId,
                    senderId = currentUid,
                    senderUid = currentUid,
                    receiverId = receiverId,
                    receiverUid = receiverId,
                    status = "pending",
                    timestamp = requestData["timestamp"] as Long,
                    senderName = currentDisplayName,
                    senderPhone = currentPhoneNumber,
                    senderProfilePic = currentProfilePic
                )
                _outgoingPendingRequests.value = _outgoingPendingRequests.value + newRequest
                Log.d("Plenxo", "Friend request $requestId sent to $receiverId")
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to send friend request", e)
                _errorMessage.value = "Failed to send friend request: ${e.localizedMessage}"
            }
        }
    }

    fun searchUserAndSendInvite(
        searchInput: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val currentUid = currentUserId
        if (currentUid.isEmpty()) {
            onFailure("Authentication Error.")
            return
        }

        val queryCode = searchInput.trim()
        if (queryCode.length != 6 || !queryCode.all { it.isDigit() }) {
            onFailure("Please enter a valid 6-digit User Code.")
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val targetUser = supabase.postgrest["users_data"].select {
                    filter { eq("userCode", queryCode) }
                }.decodeSingleOrNull<User>()

                if (targetUser == null) {
                    onFailure("User not found.")
                    _isLoading.value = false
                    return@launch
                }

                val targetUid = targetUser.uid
                if (targetUid == currentUid) {
                    onFailure("You cannot add yourself.")
                    _isLoading.value = false
                    return@launch
                }

                // Check for duplicate invitations
                val existingOutbound = supabase.postgrest["invitations"].select {
                    filter {
                        eq("senderId", currentUid)
                        eq("receiverId", targetUid)
                        eq("status", "PENDING")
                    }
                }.decodeList<Invitation>()

                if (existingOutbound.isNotEmpty()) {
                    onFailure("Friend request already sent.")
                    _isLoading.value = false
                    return@launch
                }

                val inviteId = java.util.UUID.randomUUID().toString()
                
                val currentUserObj = supabase.postgrest["users_data"].select {
                    filter { eq("id", currentUid) }
                }.decodeSingleOrNull<User>()
                
                val currentUserCode = currentUserObj?.userCode ?: "000000"
                val currentDisplayName = currentUserObj?.displayName ?: "User"

                val invitationMap = mapOf(
                    "invitationId" to inviteId,
                    "requestId" to inviteId,
                    "senderUid" to currentUid,
                    "senderId" to currentUid,
                    "senderUserCode" to currentUserCode,
                    "senderName" to currentDisplayName,
                    "receiverUid" to targetUid,
                    "receiverId" to targetUid,
                    "status" to "PENDING",
                    "timestamp" to System.currentTimeMillis()
                )

                supabase.postgrest["invitations"].insert(invitationMap)
                onSuccess("Friend request sent to ${targetUser.displayName}!")
            } catch (e: Exception) {
                Log.e("Plenxo", "Search and invite failed", e)
                onFailure("An error occurred: ${e.localizedMessage}")
            } finally {
                _isLoading.value = false
            }
        }
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
        viewModelScope.launch {
            val senderId = currentUserId
            if (senderId.isEmpty() || text.isBlank()) return@launch

            val resolvedChatId = getChatRoomId(senderId, receiverId)
            val activeFontId = "DEFAULT"
            
            val messageId = java.util.UUID.randomUUID().toString()
            val replyToId = replyToMessage.value?.messageId
            val timerVal = disappearingTimer.value
            val expiresAt = if (timerVal > 0L) System.currentTimeMillis() + timerVal else null
            
            if (isLocalOnlyMode.value) {
                try {
                    val db = AppDatabase.getDatabase(getApplication())
                    val dao = db.localMessageDao()
                    val localMsg = LocalMessage(
                        messageId = messageId,
                        chatId = resolvedChatId,
                        senderId = senderId,
                        receiverId = receiverId,
                        messageText = text,
                        timestamp = System.currentTimeMillis(),
                        status = "SENT",
                        messageType = "TEXT",
                        replyToMessageId = replyToId,
                        expiresAt = expiresAt,
                        senderActiveFontId = activeFontId
                    )
                    dao.insertMessage(localMsg)
                    replyToMessage.value = null
                    com.example.util.HapticManager.playMessageSentThud(getApplication())
                } catch (dbEx: Exception) {
                    Log.e("Plenxo", "Failed to save message to local Room database in Local-Only Mode", dbEx)
                }
                return@launch
            }
            
            // 1. Immediately insert to Room database locally as SENDING to make the UI feel instantaneous (offline-first)
            try {
                val db = AppDatabase.getDatabase(getApplication())
                val dao = db.localMessageDao()
                val localMsg = LocalMessage(
                    messageId = messageId,
                    chatId = resolvedChatId,
                    senderId = senderId,
                    receiverId = receiverId,
                    messageText = text,
                    timestamp = System.currentTimeMillis(),
                    status = "SENDING",
                    messageType = "TEXT",
                    replyToMessageId = replyToId,
                    expiresAt = expiresAt,
                        senderActiveFontId = activeFontId
                    )
                dao.insertMessage(localMsg)
            } catch (dbEx: Exception) {
                Log.e("Plenxo", "Failed to save message to local Room database before sending", dbEx)
            }
            
            // Clear reply reference
            replyToMessage.value = null

            // 2. Try sending to Supabase
            try {
                // E2EE Encryption
                val receiverProfile = supabase.postgrest["users_data"].select {
                    filter { eq("id", receiverId) }
                }.decodeSingleOrNull<UserProfile>()
                
                val receiverPublicKey = receiverProfile?.publicKey ?: ""
                val encryptedText = if (receiverPublicKey.isNotEmpty()) {
                    com.example.util.EncryptionManager.encryptMessage(text, receiverPublicKey)
                } else {
                    text
                }

                val payload = com.example.model.MessagePayload(
                    messageId = messageId,
                    chatId = resolvedChatId,
                    senderId = senderId,
                    receiverId = receiverId,
                    messageText = encryptedText,
                    messageType = "TEXT",
                    timestamp = System.currentTimeMillis(),
                    replyToMessageId = replyToId,
                    isEdited = false,
                    status = "SENT",
                    expiresAt = expiresAt,
                    senderActiveFontId = activeFontId
                )

                dynamicStorageManager.saveMessage(payload)

                // Update Room status to SENT on successful write
                try {
                    val db = AppDatabase.getDatabase(getApplication())
                    db.localMessageDao().updateMessageStatus(messageId, "SENT")
                    com.example.util.HapticManager.playMessageSentThud(getApplication())
                } catch (dbEx: Exception) {
                    Log.e("Plenxo", "Failed to update status in local database", dbEx)
                }

            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to send message online - scheduling offline worker", e)
                // Schedule WorkManager worker to retry synchronization when network is back
                try {
                    val constraints = androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()

                    val syncWorkRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.database.OfflineMessageWorker>()
                        .setConstraints(constraints)
                        .build()

                    androidx.work.WorkManager.getInstance(getApplication()).enqueue(syncWorkRequest)
                } catch (wmEx: Exception) {
                    Log.e("Plenxo", "Failed to schedule offline WorkManager sync", wmEx)
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

        viewModelScope.launch {
            try {
                val channel = supabase.channel("typing_$chatId")
                channel.broadcast("typing", buildJsonObject {
                    put("userId", uid)
                    put("isTyping", isTyping)
                })
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

        typingListenerJob = viewModelScope.launch {
            val channel = supabase.channel("typing_$chatId")
            val flow = channel.broadcastFlow<Map<String, Any>>("typing")
            
            flow.collect { data ->
                val userId = data["userId"] as? String ?: return@collect
                val isTyping = data["isTyping"] as? Boolean ?: false
                
                val map = typingUsers.value.toMutableMap()
                if (isTyping && userId != currentUserId) {
                    val cachedUser = _usersCache.value[userId]
                    val name = cachedUser?.displayName ?: "Someone"
                    map[chatId] = "$name is typing..."
                } else {
                    map.remove(chatId)
                }
                typingUsers.value = map
            }
        }
    }

    fun stopListeningToTyping(chatId: String) {
        typingListenerJob?.cancel()
        typingListenerJob = null
        typingUsers.value = emptyMap()
    }

    fun editMessage(messageId: String, newText: String) {
        val chatId = currentChatId.value
        if (chatId.isEmpty() || messageId.isEmpty()) return

        viewModelScope.launch {
            try {
                val localMsg = AppDatabase.getDatabase(getApplication()).localMessageDao().getMessageById(messageId)
                val originalText = localMsg?.messageText ?: ""
                
                val history = mutableListOf<String>()
                localMsg?.let {
                    val cleanJson = it.originalContentHistoryJson.removePrefix("[").removeSuffix("]")
                    if (cleanJson.isNotEmpty()) {
                        history.addAll(cleanJson.split(",").map { item -> item.trim().removeSurrounding("\"") })
                    }
                    if (originalText.isNotEmpty() && !history.contains(originalText)) {
                        history.add(originalText)
                    }
                }
                
                val historyJson = "[" + history.joinToString(",") { "\"$it\"" } + "]"

                localMsg?.let {
                    val updatedLocal = it.copy(
                        messageText = newText,
                        isEdited = true,
                        originalContentHistoryJson = historyJson
                    )
                    AppDatabase.getDatabase(getApplication()).localMessageDao().updateMessage(updatedLocal)
                }

                supabase.postgrest["messages"].update({
                    set("messageText", newText)
                    set("isEdited", true)
                    set("originalContentHistory", history)
                }) {
                    filter {
                        eq("messageId", messageId)
                    }
                }

                Log.d("Plenxo", "Message edited successfully online and offline: $messageId")
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to edit message: ${e.message}", e)
            }
        }
    }

    fun startAudioCall(chatId: String) {
        val chat = _chats.value.find { it.chatId == chatId } ?: return
        val receiverUid = chat.participantUids.find { it != currentUserId } ?: return
        val receiverProfile = _usersCache.value[receiverUid]
        
        val context = getApplication<Application>()
        val intent = android.content.Intent(context, com.example.ui.CallActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("ROOM_ID", chatId)
            putExtra("CALL_TYPE", "audio")
            putExtra("IS_CALLER", true)
            putExtra("RECEIVER_UID", receiverUid)
            putExtra("RECEIVER_NAME", receiverProfile?.displayName ?: "Plenxo User")
            putExtra("RECEIVER_PIC", receiverProfile?.profilePicUrl ?: "")
        }
        context.startActivity(intent)
    }

    fun startVideoCall(chatId: String) {
        val chat = _chats.value.find { it.chatId == chatId } ?: return
        val receiverUid = chat.participantUids.find { it != currentUserId } ?: return
        val receiverProfile = _usersCache.value[receiverUid]
        
        val context = getApplication<Application>()
        val intent = android.content.Intent(context, com.example.ui.CallActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("ROOM_ID", chatId)
            putExtra("CALL_TYPE", "video")
            putExtra("IS_CALLER", true)
            putExtra("RECEIVER_UID", receiverUid)
            putExtra("RECEIVER_NAME", receiverProfile?.displayName ?: "Plenxo User")
            putExtra("RECEIVER_PIC", receiverProfile?.profilePicUrl ?: "")
        }
        context.startActivity(intent)
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

    private suspend fun uploadAudioToCloudinary(chatId: String, file: File): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("Cloudinary", "Initiating Cloudinary audio upload: ${file.name}")
                val secureUrl = CloudinaryStorageManager.uploadAudio(getApplication(), file, folder = "chat_audio/$chatId")
                Log.d("Cloudinary", "Cloudinary audio upload succeeded: $secureUrl")
                return@withContext secureUrl
            } catch (e: Exception) {
                Log.e("Cloudinary", "Error in uploadAudioToCloudinary: ${e.localizedMessage}", e)
                throw e
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
                // 1. Upload to Catbox and Insert to Supabase using VoiceNoteRepository
                val voiceRepo = com.example.repository.VoiceNoteRepository()
                val audioUrl = voiceRepo.uploadAndSendVoiceNote(
                    getApplication(), 
                    audioFile, 
                    chatId = currentChatId.value,
                    receiverId = currentChatRecipientUid.value
                )
                
                // 2. Prepare Message
                val msgRef = firestore.collection("chats").document(resolvedChatId).collection("messages").document()
                val messageId = msgRef.id
                val message = Message(
                    messageId = messageId,
                    senderId = senderId,
                    receiverId = receiverId,
                    messageText = audioUrl,
                    messageType = "VOICE",
                    status = "SENT",
                    expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
                )

                // 3. Save to DB (Local vs Firestore)
                if (isLocalOnlyMode.value) {
                    val localMsg = LocalMessage(
                        messageId = messageId,
                        chatId = resolvedChatId,
                        senderId = senderId,
                        receiverId = receiverId,
                        messageText = audioUrl,
                        timestamp = System.currentTimeMillis(),
                        messageType = "VOICE",
                        expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
                    )
                    AppDatabase.getDatabase(getApplication()).localMessageDao().insertMessage(localMsg)
                } else {
                    // Await the write operation to catch errors (Fixes silent failures)
                    msgRef.set(message).await() 
                    firestore.collection("chats").document(resolvedChatId).update(
                        "lastMessage", "🎤 Voice Note",
                        "lastMessageTimestamp", FieldValue.serverTimestamp()
                    ).await()
                }

                // 4. Save to Realtime Database messages node as requested by the user
                try {
                    val rtdbRef = com.google.firebase.database.FirebaseDatabase.getInstance()
                        .getReference("messages")
                        .child(resolvedChatId)
                        .child(messageId)
                    
                    rtdbRef.setValue(
                        mapOf(
                            "messageId" to messageId,
                            "senderId" to senderId,
                            "receiverId" to receiverId,
                            "messageText" to audioUrl,
                            "messageType" to "audio",
                            "timestamp" to System.currentTimeMillis()
                        )
                    ).await()
                    Log.d("Plenxo", "Saved audio message to Realtime Database messages/$resolvedChatId/$messageId")
                } catch (dbEx: Exception) {
                    Log.e("Plenxo", "Failed to save audio message to Realtime Database: ${dbEx.message}", dbEx)
                }
                
                Log.d("Plenxo", "Voice message sent successfully: $audioUrl")
                
                // 5. Cleanup local file
                if (audioFile.exists()) {
                    audioFile.delete()
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to send voice message", e)
                _errorMessage.value = "Failed to send voice message: ${e.localizedMessage}"
                // Delete file on failure to prevent storage bloat
                if (audioFile.exists()) {
                    audioFile.delete()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Deprecated hardcoded version - removed or kept for internal routing if needed
    fun sendVoiceMessage(chatId: String, receiverId: String) {
        // This is now replaced by stopAndSendVoiceMessage logic
        // but we keep the signature if other parts of the UI call it (though they shouldn't)
    }

    fun handleSelectedMediaAttachment(uri: Uri) {
        val chatId = currentChatId.value
        val receiverId = currentChatRecipientUid.value
        if (chatId.isEmpty() || receiverId.isEmpty()) return
        
        viewModelScope.launch {
            try {
                // Request persistent read URI authorization permissions to prevent access token expiration during background tasks
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to take persistable URI permission", e)
            }
            
            // Extract raw filename metadata and structural dimension details using ContentResolver
            try {
                val cursor = getApplication<Application>().contentResolver.query(uri, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        val fileName = if (nameIndex != -1) c.getString(nameIndex) else "unknown"
                        val fileSize = if (sizeIndex != -1) c.getLong(sizeIndex) else 0L
                        Log.d("Plenxo", "Photo Picker Selected File: $fileName, Size: $fileSize")
                    }
                }
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to query content resolver for photo details", e)
            }
            
            // Pass the verified valid document URI reference directly into active media upload pipeline
            sendImageMessage(chatId, uri, receiverId)
        }
    }

    fun sendImageMessage(chatId: String, imageUri: Uri, receiverId: String) {
        viewModelScope.launch {
            val senderId = currentUserId
            if (senderId.isEmpty()) return@launch
            val resolvedChatId = getChatRoomId(senderId, receiverId)
            val activeFontId = "DEFAULT"
            
            if (isLocalOnlyMode.value) {
                try {
                    val db = AppDatabase.getDatabase(getApplication())
                    val dao = db.localMessageDao()
                    val messageId = "local_img_" + System.currentTimeMillis()
                    val localMsg = LocalMessage(
                        messageId = messageId,
                        chatId = resolvedChatId,
                        senderId = senderId,
                        receiverId = receiverId,
                        messageText = imageUri.toString(),
                        timestamp = System.currentTimeMillis(),
                        status = "SENT",
                        messageType = "IMAGE"
                    )
                    dao.insertMessage(localMsg)
                } catch (dbEx: Exception) {
                    Log.e("Plenxo", "Failed to save image message locally", dbEx)
                }
                return@launch
            }
            
            val chatRef = firestore.collection("chats").document(resolvedChatId)
            val messageRef = chatRef.collection("messages").document()
            val tempMessageId = messageRef.id
            
            // Create a temporary message with STATUS "SENDING" to show progress spinner
            val tempMessage = Message(
                messageId = tempMessageId,
                senderId = senderId,
                receiverId = receiverId,
                messageText = "Uploading image...",
                status = "SENDING",
                messageType = "IMAGE"
            )
            
            try {
                val msgMap = mapOf(
                    "messageId" to tempMessage.messageId,
                    "senderId" to tempMessage.senderId,
                    "receiverId" to tempMessage.receiverId,
                    "messageText" to tempMessage.messageText,
                    "status" to tempMessage.status,
                    "messageType" to tempMessage.messageType,
                    "timestamp" to FieldValue.serverTimestamp()
                )
                messageRef.set(msgMap).await()
                
                // Upload to Cloudinary
                val secureUrl = CloudinaryStorageManager.uploadImage(getApplication(), imageUri, folder = "chats")
                
                // Update firestore document inside a transaction
                firestore.runTransaction { transaction ->
                    transaction.update(messageRef, mapOf(
                        "messageText" to secureUrl,
                        "status" to "SENT"
                    ))
                    
                    val chatSnapshot = transaction.get(chatRef)
                    if (chatSnapshot.exists()) {
                        @Suppress("UNCHECKED_CAST")
                        val currentUnread = chatSnapshot.get("unreadCounts") as? Map<String, Long> ?: emptyMap()
                        val newUnread = currentUnread.toMutableMap()
                        val currentCount = newUnread[receiverId] ?: 0L
                        newUnread[receiverId] = currentCount + 1

                        transaction.update(chatRef, mapOf(
                            "lastMessage" to "[Image]",
                            "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                            "unreadCounts" to newUnread
                        ))
                    } else {
                        val newUnread = mapOf(receiverId to 1L)
                        val chatRoom = mapOf(
                            "chatId" to resolvedChatId,
                            "participantUids" to listOf(senderId, receiverId),
                            "lastMessage" to "[Image]",
                            "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                            "unreadCounts" to newUnread
                        )
                        transaction.set(chatRef, chatRoom)
                    }
                    null
                }.await()
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to upload and send image", e)
                try {
                    messageRef.update(mapOf(
                        "messageText" to "Failed to upload image.",
                        "status" to "ERROR"
                    )).await()
                } catch (ex: Exception) {
                    Log.e("Plenxo", "Failed to update message error status", ex)
                }
            }
        }
    }

    fun markMessagesAsRead(chatId: String) {
        val uid = currentUserId
        if (uid.isEmpty()) return
        firestore.runTransaction { transaction ->
            val chatRef = firestore.collection("chats").document(chatId)
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
        }
    }
    
    fun findAndStartChat(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            _addUserState.value = AddUserState.Error("Please enter an Email, Username, or Plenxo ID.")
            return
        }

        viewModelScope.launch {
            _addUserState.value = AddUserState.Loading
            try {
                val currentUid = currentUserId
                val currentProfile = currentUserProfile.value

                val currentEmail = currentProfile?.email ?: email.value
                val currentDisplayName = currentProfile?.displayName ?: ""
                val currentCode = currentProfile?.userCode ?: ""

                // Edge Case: User enters their own ID/Email/Name
                if ((currentUid.isNotEmpty() && query.equals(currentUid, ignoreCase = true)) ||
                    (currentEmail.isNotEmpty() && query.equals(currentEmail, ignoreCase = true)) ||
                    (currentDisplayName.isNotEmpty() && query.equals(currentDisplayName, ignoreCase = true)) ||
                    (currentCode.isNotEmpty() && query.equals(currentCode, ignoreCase = true))
                ) {
                    _addUserState.value = AddUserState.Error("You cannot start a chat with yourself.")
                    return@launch
                }

                var foundUser: com.example.model.User? = null

                // 1. Search Supabase users_data by email, userCode, or uid
                try {
                    val supabaseResults = supabase.postgrest["users_data"].select {
                        filter {
                            or {
                                eq("email", query)
                                eq("userCode", query)
                                eq("uid", query)
                            }
                        }
                    }.decodeList<com.example.model.User>()

                    foundUser = supabaseResults.firstOrNull { u ->
                        u.email.equals(query, ignoreCase = true) ||
                        u.userCode.equals(query, ignoreCase = true) ||
                        u.uid.equals(query, ignoreCase = true) ||
                        u.displayName.equals(query, ignoreCase = true)
                    }
                } catch (e: Exception) {
                    Log.w("Plenxo", "Supabase query error during findAndStartChat: ${e.message}")
                }

                // 2. Fallback search in Firestore users
                if (foundUser == null) {
                    try {
                        var snap = firestore.collection("users").whereEqualTo("email", query).get().await()
                        if (snap.isEmpty) {
                            snap = firestore.collection("users").whereEqualTo("userCode", query).get().await()
                        }
                        if (snap.isEmpty) {
                            snap = firestore.collection("users").whereEqualTo("displayName", query).get().await()
                        }
                        if (snap.isEmpty) {
                            val docSnap = firestore.collection("users").document(query).get().await()
                            if (docSnap.exists()) {
                                foundUser = docSnap.toObject(com.example.model.User::class.java)
                            }
                        } else {
                            foundUser = snap.documents.firstOrNull()?.toObject(com.example.model.User::class.java)
                        }
                    } catch (e: Exception) {
                        Log.w("Plenxo", "Firestore query error during findAndStartChat: ${e.message}")
                    }
                }

                // Edge Case: User not found
                if (foundUser == null) {
                    _addUserState.value = AddUserState.Error("User not found. Please check the ID/Email.")
                    return@launch
                }

                val targetUid = foundUser.uid
                if (targetUid.isEmpty()) {
                    _addUserState.value = AddUserState.Error("User not found. Please check the ID/Email.")
                    return@launch
                }

                if (targetUid == currentUid) {
                    _addUserState.value = AddUserState.Error("You cannot start a chat with yourself.")
                    return@launch
                }

                // Update users cache
                val updatedCache = _usersCache.value.toMutableMap()
                updatedCache[targetUid] = foundUser
                _usersCache.value = updatedCache

                val chatId = getChatRoomId(currentUid, targetUid)
                val chatRoom = ChatRoom(
                    chatId = chatId,
                    participantUids = listOf(currentUid, targetUid),
                    lastMessage = "",
                    lastMessageTimestamp = System.currentTimeMillis()
                )

                _addUserState.value = AddUserState.Success(foundUser)

                // Dismiss dialog and navigate immediately to Individual Chat Screen
                dismissAddUserModal()
                openChatRoom(chatRoom)

            } catch (e: Exception) {
                Log.e("Plenxo", "findAndStartChat error: ${e.message}", e)
                _addUserState.value = AddUserState.Error(e.localizedMessage ?: "A network error occurred. Please try again.")
            }
        }
    }

    fun openChatWithEmail(targetEmail: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val usersSnapshot = firestore.collection("users")
                    .whereEqualTo("email", targetEmail.trim())
                    .get().await()
                
                if (usersSnapshot.isEmpty) {
                    _errorMessage.value = "User not found with email: $targetEmail"
                    return@launch
                }
                
                val targetUser = usersSnapshot.documents.first().toObject(User::class.java)
                if (targetUser == null) {
                    _errorMessage.value = "Failed to parse user data."
                    return@launch
                }
                
                val currentUid = currentUserId
                val targetUid = targetUser.uid
                val chatId = getChatRoomId(currentUid, targetUid)
                
                currentChatId.value = chatId
                currentChatRecipientName.value = targetUser.displayName
                currentChatRecipientUid.value = targetUid
                
                navigateToScreen(PlenxoScreen.CHAT_DETAIL)
                startListeningForMessages(chatId)
                
            } catch (e: Exception) {
                Log.e("Plenxo", "Failed to open chat", e)
                _errorMessage.value = "Error starting chat: ${e.message}"
            } finally {
                _isLoading.value = false
            }
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
        audioRecorder.stopRecording()
        try {
            voicePlayer.release()
        } catch (e: Exception) {
            Log.e("Plenxo", "Failed to release voicePlayer", e)
        }
        stopAudio()
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
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cont.resume(task.result)
            } else {
                cont.resumeWithException(task.exception ?: RuntimeException("Firebase Operation failed"))
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
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                return@addOnCompleteListener
            }
            val token = task.result
            viewModelScope.launch {
                try {
                    supabase.postgrest["users_data"].update(
                        {
                            set("fcmToken", token)
                        }
                    ) {
                        filter { eq("id", uid) }
                    }
                } catch (e: Exception) {
                    Log.e("PlenxoViewModel", "Failed to save FCM token", e)
                }
            }
        }
    }
}

```

```kotlin
// UserDiscoveryScreen.kt
package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.UserProfile
import com.example.ui.theme.PlenxoColors
import com.example.ui.components.ProfileRingBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDiscoveryScreen(
    currentUser: UserProfile?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    users: List<UserProfile>,
    requestedUserIds: Set<String>,
    onAddFriend: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117)) // Deep Slate
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Phone Lookup input
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(id = R.string.str_find_contact_by_phone_number),
                color = Color(0xFF8B949E),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(stringResource(id = R.string.str_enter_phone_number),
                            color = Color(0xFF8B949E),
                            fontSize = 15.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Phone Icon",
                            tint = Color(0xFF8B949E)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF010409),
                        unfocusedContainerColor = Color(0xFF010409),
                        focusedBorderColor = Color(0xFF58A6FF),
                        unfocusedBorderColor = Color(0xFF30363D),
                        focusedTextColor = Color(0xFFF0F6FC),
                        unfocusedTextColor = Color(0xFFF0F6FC)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("user_search_input")
                )

                Button(
                    onClick = onSearchClick,
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF58A6FF))
                ) {
                    Text(stringResource(R.string.str_search_add), color = Color(0xFF0D1117), fontWeight = FontWeight.Bold)
                }
            }
        }

        // Results Layout
        if (searchQuery.isNotEmpty() && users.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(id = R.string.str_no_users_found),
                    color = Color(0xFF8B949E),
                    fontSize = 16.sp
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (users.isNotEmpty()) {
                    Text(stringResource(id = R.string.str_search_results),
                        color = Color(0xFF8B949E),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("users_search_results_list"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(users) { user ->
                        val isRequested = requestedUserIds.contains(user.uid)
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("user_discovery_row_${user.uid}"),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Cloudinary Profile Pic with Coil AsyncImage
                                ProfileRingBox(ringId = user.profileRingId, ringPadding = 3.dp, borderWidth = 4.dp) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0D1117)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (user.profilePicUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                    .data(user.profilePicUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                placeholder = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_gallery),
                                                error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image),
                                                contentDescription = "User Avatar",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = "Placeholder",
                                                tint = Color(0xFF8B949E),
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = user.displayName,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF0F6FC)
                                    )
                                    Text(
                                        text = "Phone: ${user.phoneNumber}",
                                        fontSize = 14.sp,
                                        color = Color(0xFF8B949E)
                                    )
                                }

                                Button(
                                    onClick = { onAddFriend(user.uid) },
                                    enabled = !isRequested,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("add_friend_button_${user.uid}"),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF58A6FF),
                                        contentColor = Color(0xFF0D1117),
                                        disabledContainerColor = Color(0xFF30363D),
                                        disabledContentColor = Color.Gray
                                    )
                                ) {
                                    Text(
                                        text = if (isRequested) "Requested" else "Add",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
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

```

```kotlin
// ChatsListScreen.kt
package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.components.bounceCombinedClickable

import android.widget.Toast
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChatRoom
import com.example.model.FriendRequest
import com.example.ui.components.ProfileImageWithRing
import androidx.compose.foundation.BorderStroke
import com.example.viewmodel.PlenxoViewModel
import com.example.viewmodel.PlenxoScreen
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(viewModel: PlenxoViewModel, primaryColor: Color) {
    val chats by viewModel.chats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val usersCache by viewModel.usersCache.collectAsState()
    val currentUserProfile by viewModel.currentUserProfile.collectAsState()
    val galleryImageUriString by viewModel.galleryImageUriString.collectAsState()
    val currentUserId = viewModel.currentUserId

    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
        
        var pendingChatToOpen by remember { mutableStateOf<ChatRoom?>(null) }
        val unlockLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                pendingChatToOpen?.let { viewModel.openChatRoom(it) }
            }
            pendingChatToOpen = null
        }

    val context = LocalContext.current
    var chatToDelete by remember { mutableStateOf<ChatRoom?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDeletePasswordDialog by remember { mutableStateOf(false) }
    var deletePasswordText by remember { mutableStateOf("") }
    var deletePasswordError by remember { mutableStateOf<String?>(null) }

    val localChat1 = chatToDelete
    if (showDeletePasswordDialog && localChat1 != null) {
        val targetChat = localChat1
        val contextLocal = androidx.compose.ui.platform.LocalContext.current
        val correctPin = remember(targetChat.chatId) {
            com.example.repository.SecurityRepository(contextLocal).getChatLock(targetChat.chatId)
        }
        AlertDialog(
            onDismissRequest = {
                showDeletePasswordDialog = false
                deletePasswordText = ""
                deletePasswordError = null
            },
            title = { Text(stringResource(R.string.str_verify_chat_pin), fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column {
                    Text(stringResource(id = R.string.str_this_chat_is_locked_sensitive),
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = deletePasswordText,
                        onValueChange = {
                            deletePasswordText = it
                            deletePasswordError = null
                        },
                        placeholder = { Text(stringResource(R.string.str_enter_chat_pin), color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        isError = deletePasswordError != null,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (deletePasswordError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = deletePasswordError ?: "", color = Color.Red, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (deletePasswordText == correctPin) {
                            viewModel.deleteChat(targetChat.chatId)
                            showDeletePasswordDialog = false
                            chatToDelete = null
                            deletePasswordText = ""
                            deletePasswordError = null
                            android.widget.Toast.makeText(contextLocal, "Chat and history purged successfully", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            deletePasswordError = "Incorrect PIN. Deletion denied."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text(stringResource(R.string.str_verify_purge))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeletePasswordDialog = false
                    deletePasswordText = ""
                    deletePasswordError = null
                }) {
                    Text(stringResource(R.string.cancel), color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E2332)
        )
    }

    val localChat2 = chatToDelete
    if (showDeleteConfirmDialog && localChat2 != null) {
        val targetChat = localChat2
        val contextLocal = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
            },
            title = { Text(stringResource(R.string.str_delete_chat), fontWeight = FontWeight.Bold, color = Color.Red) },
            text = {
                Text(stringResource(id = R.string.str_are_you_sure_you_want),
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteChat(targetChat.chatId)
                        showDeleteConfirmDialog = false
                        chatToDelete = null
                        android.widget.Toast.makeText(contextLocal, "Chat deleted permanently", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text(stringResource(R.string.str_delete_permanently))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    chatToDelete = null
                }) {
                    Text(stringResource(R.string.cancel), color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E2332)
        )
    }
val pinnedChatIds by viewModel.pinnedChatIds.collectAsState()
    
    val sortedChats = remember(chats, pinnedChatIds) {
        chats.sortedWith(compareByDescending<ChatRoom> { pinnedChatIds.contains(it.chatId) }
            .thenByDescending { it.lastMessageTimestamp })
    }

    val filteredChats = remember(sortedChats, searchQuery, usersCache) {
        if (searchQuery.isBlank()) {
            sortedChats
        } else {
            sortedChats.filter { chat ->
                val recipientUid = chat.participantUids.firstOrNull { it != currentUserId } ?: ""
                val recipientUser = usersCache[recipientUid]
                val nameMatch = recipientUser?.displayName?.contains(searchQuery, ignoreCase = true) == true
                val emailMatch = recipientUser?.email?.contains(searchQuery, ignoreCase = true) == true
                val userCodeMatch = recipientUser?.userCode?.contains(searchQuery, ignoreCase = true) == true
                nameMatch || emailMatch || userCodeMatch
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    val contextLocal = androidx.compose.ui.platform.LocalContext.current
                    val localRingId = com.example.util.SessionManager.getProfileRingId(contextLocal)
                    val userRingId = if (localRingId != "none") localRingId else (currentUserProfile?.profileRingId ?: "none")
                    IconButton(
                        onClick = { viewModel.navigateToScreen(PlenxoScreen.SETTINGS_PROFILE) },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        val displayAvatarUrl = currentUserProfile?.profilePicUrl?.takeIf { it.isNotEmpty() }
                            ?: galleryImageUriString?.takeIf { it.isNotEmpty() }
                        com.example.ui.components.ProfileRingBox(ringId = userRingId, ringPadding = 1.dp, borderWidth = 2.dp) {
                            if (!displayAvatarUrl.isNullOrEmpty() && (displayAvatarUrl.startsWith("http") || displayAvatarUrl.startsWith("content://") || displayAvatarUrl.startsWith("file://"))) {
                                coil.compose.AsyncImage(
                                    model = coil.request.ImageRequest.Builder(contextLocal)
                                        .data(displayAvatarUrl)
                                        .crossfade(true)
                                        .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                                        .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                                        .build(),
                                    placeholder = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_gallery),
                                    error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image),
                                    contentDescription = "Profile Picture",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(primaryColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = currentUserProfile?.displayName?.takeIf { it.isNotBlank() }?.take(1)?.uppercase() ?: "U",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { 
                            context.startActivity(android.content.Intent(context, com.example.ui.AddContactActivity::class.java))
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Contact", tint = primaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF9F9F9)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            val pendingFriendRequests by viewModel.pendingFriendRequests.collectAsState()
            val outgoingPendingRequests by viewModel.outgoingPendingRequests.collectAsState()

            Column(modifier = Modifier.fillMaxSize()) {
                if (pendingFriendRequests.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.dp, Color.LightGray.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Pending Friend Requests (${pendingFriendRequests.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = primaryColor,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        pendingFriendRequests.forEach { request ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val senderRingId = usersCache[request.senderUid]?.profileRingId
                                    com.example.ui.components.ProfileRingBox(ringId = senderRingId, ringPadding = 2.dp, borderWidth = 3.dp) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(Color.LightGray.copy(alpha = 0.3f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                        if (request.senderProfilePic.isNotEmpty()) {
                                            coil.compose.AsyncImage(
                                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                    .data(request.senderProfilePic)
                                                    .crossfade(true)
                                        .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                                        .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                                                    .build(),
                                                placeholder = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_gallery),
                                                error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image),
                                                contentDescription = "Sender profile picture",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = "Placeholder",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = request.senderName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.Black
                                        )
                                        Text(
                                            text = request.senderPhone,
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.acceptFriendRequest(request) {
                                                Toast.makeText(context, "Added!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text(stringResource(R.string.str_add), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.rejectFriendRequest(request) {
                                                Toast.makeText(context, "Request cancelled", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text(stringResource(R.string.cancel), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                                    }
                                }
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (isLoading && chats.isEmpty()) {
                        // Shimmer Effect Loading State
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            repeat(5) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                        .padding(vertical = 8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.LightGray.copy(alpha = 0.3f))
                                )
                            }
                        }
                    } else if (filteredChats.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Empty State",
                                tint = Color.LightGray,
                                modifier = Modifier.size(100.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                if (searchQuery.isNotEmpty()) "No matching conversations found." else "No conversations yet. Start a new chat below!",
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        val lockedChatIds by viewModel.lockedChatIds.collectAsState()
                        val userPresences by viewModel.userPresences.collectAsState()
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(filteredChats, key = { _, chat -> chat.chatId }) { index, chat ->
                                var isVisible by remember { mutableStateOf(false) }
                                LaunchedEffect(chat.chatId) {
                                    kotlinx.coroutines.delay(index * 30L)
                                    isVisible = true
                                }
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isVisible,
                                    enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300)) + 
                                            androidx.compose.animation.slideInVertically(initialOffsetY = { 50 }, animationSpec = androidx.compose.animation.core.tween(300))
                                ) {
                                    Column {
                                    val recipientUid = chat.participantUids.firstOrNull { it != currentUserId } ?: "Unknown"
                                    val recipientUser = usersCache[recipientUid]
                                    val displayName = recipientUser?.displayName ?: "User"
                                    val unreadCount = chat.unreadCounts[currentUserId] ?: 0
                                    val isPinned = pinnedChatIds.contains(chat.chatId)
                                    val isLocked = lockedChatIds.contains(chat.chatId)
                                    
                                    val presenceMap = userPresences[recipientUid] ?: emptyMap()
                                    val presenceState = presenceMap["state"] as? String ?: "offline"
                                    LaunchedEffect(recipientUid) {
                                        if (recipientUid.isNotEmpty()) {
                                            viewModel.startListeningToPresence(recipientUid)
                                        }
                                    }

                                ChatListItem(
                                    chat = chat,
                                    recipientName = displayName,
                                    profilePicUrl = recipientUser?.profilePicUrl ?: "",
                                    profileRingId = recipientUser?.profileRingId ?: "none",
                                    unreadCount = unreadCount,
                                    primaryColor = primaryColor,
                                    isPinned = isPinned,
                                    isLocked = isLocked,
                                    presenceState = presenceState,
                                    onDeleteChat = {
                                        chatToDelete = chat
                                        if (isLocked) {
                                            showDeletePasswordDialog = true
                                        } else {
                                            showDeleteConfirmDialog = true
                                        }
                                    },
                                    onPinToggle = { viewModel.toggleChatPin(chat.chatId) },
                                    onLockToggle = {
                                        if (isLocked) {
                                            viewModel.toggleChatLock(chat.chatId)
                                            com.example.repository.SecurityRepository(context).setChatLock(chat.chatId, null)
                                            com.example.repository.SecurityRepository(context).setChatLockType(chat.chatId, null)
                                        } else {
                                            val intent = android.content.Intent(context, com.example.ui.AppLockSetupActivity::class.java).apply {
                                                putExtra("chatId", chat.chatId)
                                            }
                                            context.startActivity(intent)
                                            viewModel.toggleChatLock(chat.chatId)
                                        }
                                    },
                                    onClick = {
                                        if (isLocked) {
                                            pendingChatToOpen = chat
                                            val intent = android.content.Intent(context, com.example.ui.UnlockActivity::class.java).apply {
                                                putExtra("chatId", chat.chatId)
                                            }
                                            unlockLauncher.launch(intent)
                                        } else {
                                            viewModel.openChatRoom(chat)
                                        }
                                    }

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListItem(
    chat: ChatRoom,
    recipientName: String,
    profilePicUrl: String,
    profileRingId: String,
    unreadCount: Int,
    primaryColor: Color,
    isPinned: Boolean,
    isLocked: Boolean,
    presenceState: String = "offline",
    onPinToggle: () -> Unit,
    onLockToggle: () -> Unit,
    onDeleteChat: () -> Unit,
    onClick: () -> Unit
) {
    val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val timeString = chat.lastMessageTimestamp?.let { formatter.format(java.util.Date(it)) } ?: ""
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isPinned) primaryColor.copy(alpha = 0.04f) else Color.Transparent)
                .bounceCombinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                com.example.ui.components.ProfileRingBox(ringId = profileRingId, ringPadding = 2.dp, borderWidth = 3.dp) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (profilePicUrl.isNotEmpty()) {
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                    .data(profilePicUrl)
                                    .crossfade(true)
                                        .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                                        .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                                    .build(),
                                placeholder = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_gallery),
                                error = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_report_image),
                                contentDescription = "Recipient Profile Picture",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Text(recipientName.take(1).uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (presenceState == "online") Color(0xFF4CAF50) else Color(0xFF9E9E9E))
                        .border(2.dp, Color.White, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = recipientName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.Black)
                    if (isPinned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(imageVector = Icons.Default.PushPin, contentDescription = "Pinned", tint = primaryColor, modifier = Modifier.size(14.dp))
                    }
                    if (isLocked) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Locked", tint = Color.Gray, modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    text = chat.lastMessage,
                    fontSize = 14.sp,
                    color = if (unreadCount > 0) Color.Black else Color.Gray,
                    fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(horizontalAlignment = Alignment.End) {
                Text(text = timeString, fontSize = 12.sp, color = if (unreadCount > 0) primaryColor else Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(primaryColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = unreadCount.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (isPinned) "Unpin Chat" else "Pin Chat to Top") },
                onClick = {
                    onPinToggle()
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text(if (isLocked) "Unlock Chat (Disable App Lock)" else "Lock Chat") },
                onClick = {
                    onLockToggle()
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.str_delete_chat_1), color = Color.Red) },
                onClick = {
                    onDeleteChat()
                    showMenu = false
                }
            )
        }
    }
}

```

```kotlin
// PlenxoAppContent.kt
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.example.viewmodel.PlenxoScreen
import com.example.viewmodel.PlenxoViewModel
import com.example.viewmodel.NormalSettingsViewModel
import com.example.viewmodel.ProfileSettingsViewModel
import com.example.ui.NormalSettingsScreen
import com.example.ui.ProfileSettingsScreen
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

    // CAPTCHA Reset Control
    LaunchedEffect(currentScreen) {
        viewModel.resetCaptcha()
    }

    // Global Back Handler
    androidx.activity.compose.BackHandler(enabled = currentScreen != PlenxoScreen.HOME && currentScreen != PlenxoScreen.LOGIN) {
        if (!viewModel.navigateBack()) {
            viewModel.navigateToScreen(PlenxoScreen.HOME, addToHistory = false, clearHistory = true)
        }
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
                    (slideInHorizontally(initialOffsetX = { it }, animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)) + fadeIn(animationSpec = androidx.compose.animation.core.tween(200))) togetherWith
                    (slideOutHorizontally(targetOffsetX = { -it }, animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)) + fadeOut(animationSpec = androidx.compose.animation.core.tween(200)))
                } else if (initialState == PlenxoScreen.SIGNUP && targetState == PlenxoScreen.LOGIN) {
                    (slideInHorizontally(initialOffsetX = { -it }, animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)) + fadeIn(animationSpec = androidx.compose.animation.core.tween(200))) togetherWith
                    (slideOutHorizontally(targetOffsetX = { it }, animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)) + fadeOut(animationSpec = androidx.compose.animation.core.tween(200)))
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
            PlenxoScreen.PROFILE_SETUP, PlenxoScreen.AVATAR_SETUP -> {
                ProfileSetupScreen(viewModel = viewModel, primaryColor = primaryColor)
            }
            PlenxoScreen.FINAL_DETAILS -> {
                FinalDetailsScreen(viewModel = viewModel, primaryColor = primaryColor)
            }
            PlenxoScreen.PERMISSION_GATEWAY -> {
                val context = LocalContext.current
                PermissionGatewayScreen(
                    viewModel = viewModel, 
                    primaryColor = primaryColor, 
                    permissionManager = permissionManager,
                    onNavigateHome = {
                        android.util.Log.e("DEBUG_NAV", "Navigating to HOME from Gateway")
                        viewModel.navigateToScreen(PlenxoScreen.HOME)
                    }
                )
            }
            PlenxoScreen.HOME -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF131824))
                ) {
                    SocialDashboardScreen(viewModel = viewModel)
                }
            }
            PlenxoScreen.CHAT_DETAIL -> {
                ChatDetailScreen(viewModel = viewModel, primaryColor = primaryColor, permissionManager = permissionManager)
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
            PlenxoScreen.ANIMATIONS -> {
                AnimationSettingsScreen(viewModel = viewModel, primaryColor = primaryColor)
            }
            PlenxoScreen.CHAT_REQUESTS -> {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    RequestsScreen(
                        viewModel = viewModel,
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
                val searchQuery by viewModel.discoverySearchQuery.collectAsState()
                val users by viewModel.discoveryUsers.collectAsState()
                val requestedIds by viewModel.discoveryRequestedUserIds.collectAsState()
                val currentUser by viewModel.currentUserProfile.collectAsState()

                Scaffold(
                    topBar = {
                        @OptIn(ExperimentalMaterial3Api::class)
                        TopAppBar(
                            title = { Text(stringResource(R.string.str_discover_users), color = PlenxoColors.TextPrimary, fontWeight = FontWeight.Bold) },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = PlenxoColors.Surface),
                            navigationIcon = {
                                IconButton(onClick = { viewModel.navigateBack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PlenxoColors.Primary)
                                }
                            }
                        )
                    },
                    containerColor = PlenxoColors.Background
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        UserDiscoveryScreen(
                            currentUser = currentUser,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { viewModel.updateDiscoverySearchQuery(it) },
                            onSearchClick = { viewModel.searchUserByCode() },
                            users = users,
                            requestedUserIds = requestedIds,
                            onAddFriend = { uid -> viewModel.sendFriendRequest(uid) }
                        )
                    }
                }
            }
            PlenxoScreen.ACTIVE_SESSIONS -> {
                ActiveSessionsScreen(viewModel = viewModel)
            }
            PlenxoScreen.APP_LOCK_SETUP -> {
                // This is handled by an activity but we need a branch for exhaustiveness
                // or we can just navigate to login if it ever reaches here
            }
        }
        }

        // Full Screen Advanced Loading Indicator Overlay
        if (isLoading) {
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    viewModel: PlenxoViewModel, 
    primaryColor: Color
) {
    val email by viewModel.email.collectAsState()
    val password by viewModel.password.collectAsState()
    val confirmPassword by viewModel.confirmPassword.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(errorMessage) {
        val msg = errorMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    AuthWrapper(title = "Create Account") {
        if (errorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = PlenxoSpacing.Medium)
                    .background(Color(0x22FF4D4F), shape = RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFFF4D4F), shape = RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error icon",
                    tint = Color(0xFFFF4D4F),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = errorMessage ?: "",
                    color = Color(0xFFFF4D4F),
                    style = PlenxoTypography.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        PlenxoTextField(
            value = email,
            onValueChange = { 
                viewModel.email.value = it
                if (errorMessage != null) viewModel.clearError()
            },
            label = "Email Address",
            isDark = true,
            isError = errorMessage?.let { it.contains("email", ignoreCase = true) || it.contains("account with this email", ignoreCase = true) } == true,
            trailingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = androidx.compose.ui.graphics.Color.LightGray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = androidx.compose.ui.text.input.ImeAction.Next, autoCorrectEnabled = false),
            modifier = Modifier.testTag("signup_email_input")
        )
        Spacer(modifier = Modifier.height(PlenxoSpacing.Medium))
        PlenxoTextField(
            value = password,
            onValueChange = { 
                viewModel.password.value = it
                if (errorMessage != null) viewModel.clearError()
            },
            label = "Password",
            isDark = true,
            isError = errorMessage?.let { it.contains("password", ignoreCase = true) && !it.contains("confirm", ignoreCase = true) } == true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(icon, contentDescription = null, tint = androidx.compose.ui.graphics.Color.LightGray)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = androidx.compose.ui.text.input.ImeAction.Next, autoCorrectEnabled = false),
            modifier = Modifier.testTag("signup_password_input")
        )
        Spacer(modifier = Modifier.height(PlenxoSpacing.Medium))
        PlenxoTextField(
            value = confirmPassword,
            onValueChange = { 
                viewModel.confirmPassword.value = it
                if (errorMessage != null) viewModel.clearError()
            },
            label = "Confirm Password",
            isDark = true,
            isError = errorMessage?.let { it.contains("confirm", ignoreCase = true) || it.contains("match", ignoreCase = true) } == true,
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val icon = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                    Icon(icon, contentDescription = null, tint = androidx.compose.ui.graphics.Color.LightGray)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = androidx.compose.ui.text.input.ImeAction.Done, autoCorrectEnabled = false),
            modifier = Modifier.testTag("signup_confirm_password_input")
        )
        Spacer(modifier = Modifier.height(PlenxoSpacing.Medium))

        TermsAndPrivacyCheckboxRow(viewModel = viewModel, errorMessage = errorMessage)

        Spacer(modifier = Modifier.height(PlenxoSpacing.Large))

        PlenxoButton(
            text = "Sign Up",
            onClick = { 
                viewModel.onSignUpClicked() 
            },
            enabled = true,
            modifier = Modifier.testTag("signup_button")
        )
        Spacer(modifier = Modifier.height(PlenxoSpacing.Medium))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.str_already_have_an_account), style = PlenxoTypography.Body)
            Text(stringResource(id = R.string.str_login),
                style = PlenxoTypography.Body.copy(color = PlenxoColors.Primary, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .clickable { viewModel.navigateToLogin() }
                    .padding(4.dp)
                    .testTag("navigate_to_login")
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: PlenxoViewModel, 
    primaryColor: Color
) {
    val email by viewModel.email.collectAsState()
    val password by viewModel.password.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var passwordVisible by remember { mutableStateOf(false) }

    AuthWrapper(title = "Welcome Back") {
        if (errorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = PlenxoSpacing.Medium)
                    .background(Color(0x22FF4D4F), shape = RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFFF4D4F), shape = RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error icon",
                    tint = Color(0xFFFF4D4F),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = errorMessage ?: "",
                    color = Color(0xFFFF4D4F),
                    style = PlenxoTypography.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        PlenxoTextField(
            value = email,
            onValueChange = { 
                viewModel.email.value = it
                if (errorMessage != null) viewModel.clearError()
            },
            label = "Email Address",
            isDark = true,
            isError = errorMessage?.let { it.contains("email", ignoreCase = true) || it.contains("credentials", ignoreCase = true) } == true,
            trailingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = androidx.compose.ui.graphics.Color.LightGray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = androidx.compose.ui.text.input.ImeAction.Next, autoCorrectEnabled = false),
            modifier = Modifier.testTag("login_email_input")
        )
        Spacer(modifier = Modifier.height(PlenxoSpacing.Medium))
        PlenxoTextField(
            value = password,
            onValueChange = { 
                viewModel.password.value = it
                if (errorMessage != null) viewModel.clearError()
            },
            label = "Password",
            isDark = true,
            isError = errorMessage?.let { it.contains("password", ignoreCase = true) || it.contains("credentials", ignoreCase = true) } == true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(icon, contentDescription = null, tint = androidx.compose.ui.graphics.Color.LightGray)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = androidx.compose.ui.text.input.ImeAction.Done, autoCorrectEnabled = false),
            modifier = Modifier.testTag("login_password_input")
        )
        Spacer(modifier = Modifier.height(PlenxoSpacing.Medium))

        TermsAndPrivacyCheckboxRow(viewModel = viewModel, errorMessage = errorMessage)

        Spacer(modifier = Modifier.height(PlenxoSpacing.Large))

        PlenxoButton(
            text = "Login",
            onClick = { 
                viewModel.onLoginClicked() 
            },
            enabled = true,
            modifier = Modifier.testTag("login_button")
        )
        Spacer(modifier = Modifier.height(PlenxoSpacing.Medium))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.str_don_t_have_an_account), style = PlenxoTypography.Body)
            Text(stringResource(id = R.string.str_sign_up),
                style = PlenxoTypography.Body.copy(color = PlenxoColors.Primary, fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .clickable { viewModel.navigateToSignup() }
                    .padding(4.dp)
                    .testTag("navigate_to_signup")
            )
        }
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

@Composable
fun ProfileSetupScreen(viewModel: PlenxoViewModel, primaryColor: Color) {
    val galleryImageUriStr by viewModel.galleryImageUriString.collectAsState()
    val isProfilePicUploading by viewModel.isProfilePicUploading.collectAsState()
    val displayName by viewModel.displayName.collectAsState()
    val plenxoId by viewModel.plenxoId.collectAsState()
    val context = LocalContext.current

    var showGalleryPermissionExpl by remember { mutableStateOf(false) }
    var showCameraPermissionExpl by remember { mutableStateOf(false) }

    val storagePermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val cameraPermission = Manifest.permission.CAMERA

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadProfilePicture(it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: android.graphics.Bitmap? ->
        bitmap?.let {
            val path = android.provider.MediaStore.Images.Media.insertImage(
                context.contentResolver, it, "ProfilePic_${System.currentTimeMillis()}", null
            )
            val uri = Uri.parse(path)
            viewModel.uploadProfilePicture(uri)
        }
    }

    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            galleryLauncher.launch("image/*")
        } else {
            Toast.makeText(context, "Gallery permission is required to upload photos.", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission is required to take photos.", Toast.LENGTH_SHORT).show()
        }
    }

    // Dynamic Permission Explanation Dialogs
    if (showGalleryPermissionExpl) {
        AlertDialog(
            onDismissRequest = { showGalleryPermissionExpl = false },
            title = { Text(stringResource(R.string.str_photo_access_required), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFF0F6FC)) },
            text = { Text(stringResource(R.string.str_we_need_access_to_your), fontSize = 15.sp, color = Color(0xFF8B949E)) },
            confirmButton = {
                Button(
                    onClick = {
                        showGalleryPermissionExpl = false
                        galleryPermissionLauncher.launch(storagePermission)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text(stringResource(R.string.str_ok), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGalleryPermissionExpl = false }) {
                    Text(stringResource(R.string.cancel), color = Color(0xFF8B949E))
                }
            },
            containerColor = Color(0xFF161B22),
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showCameraPermissionExpl) {
        AlertDialog(
            onDismissRequest = { showCameraPermissionExpl = false },
            title = { Text(stringResource(R.string.str_camera_access_required), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFF0F6FC)) },
            text = { Text(stringResource(R.string.str_we_need_access_to_your_1), fontSize = 15.sp, color = Color(0xFF8B949E)) },
            confirmButton = {
                Button(
                    onClick = {
                        showCameraPermissionExpl = false
                        cameraPermissionLauncher.launch(cameraPermission)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text(stringResource(R.string.str_ok), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCameraPermissionExpl = false }) {
                    Text(stringResource(R.string.cancel), color = Color(0xFF8B949E))
                }
            },
            containerColor = Color(0xFF161B22),
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(stringResource(id = R.string.str_create_your_identity),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Text(stringResource(id = R.string.str_choose_a_professional_photo_to),
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            textAlign = TextAlign.Center
        )

        // Profile Picture with Gradient Border
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.White)
                .drawBehind {
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(primaryColor, Color(0xFF00C6FF))
                        ),
                        style = Stroke(width = 8f)
                    )
                }
                .testTag("profile_pic_container"),
            contentAlignment = Alignment.Center
        ) {
            if (galleryImageUriStr != null) {
                AsyncImage(
                    model = galleryImageUriStr,
                    contentDescription = "Profile Picture",
                    placeholder = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_report_image),
                    error = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_report_image),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Avatar Placeholder",
                    modifier = Modifier.size(80.dp),
                    tint = Color.LightGray
                )
            }

            if (isProfilePicUploading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons for Photo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val hasStoragePerm = ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED
                    if (hasStoragePerm) {
                        galleryLauncher.launch("image/*")
                    } else {
                        showGalleryPermissionExpl = true
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, primaryColor)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.str_gallery), color = primaryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = {
                    val hasCameraPerm = ContextCompat.checkSelfPermission(context, cameraPermission) == PackageManager.PERMISSION_GRANTED
                    if (hasCameraPerm) {
                        cameraLauncher.launch(null)
                    } else {
                        showCameraPermissionExpl = true
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.str_camera), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Display Name Input Field
        OutlinedTextField(
            value = displayName,
            onValueChange = { viewModel.displayName.value = it },
            label = { Text("Display Name") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("display_name_input"),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 6-Digit Plenxo ID Read-only Badge/Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("plenxo_id_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F2F5))
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Your Plenxo ID",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Auto-generated unique 6-digit ID",
                        fontSize = 11.sp,
                        color = Color.DarkGray
                    )
                }
                Text(
                    text = plenxoId,
                    fontSize = 20.sp,
                    color = primaryColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Navigation Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.saveProfileSetup() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("profile_setup_continue"),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
            ) {
                Text(stringResource(R.string.str_continue), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// TASK 4: PROFILE DATA COLLECTION & DB INJECTION
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinalDetailsScreen(viewModel: PlenxoViewModel, primaryColor: Color) {
    val displayName by viewModel.displayName.collectAsState()
    val userCode by viewModel.userCode.collectAsState()
    val bDay by viewModel.birthDay.collectAsState()
    val bMonth by viewModel.birthMonth.collectAsState()
    val bYear by viewModel.birthYear.collectAsState()
    val phoneNumberVal by viewModel.phoneNumber.collectAsState()

    val daysList = (1..31).map { it.toString() }
    val monthsList = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val yearsList = (1960..2026).map { it.toString() }.reversed()

    var showDayDropdown by remember { mutableStateOf(false) }
    var showMonthDropdown by remember { mutableStateOf(false) }
    var showYearDropdown by remember { mutableStateOf(false) }

    val isButtonEnabled = displayName.trim().isNotEmpty() && phoneNumberVal.trim().isNotEmpty()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0D1117)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(stringResource(id = R.string.str_final_profile_details),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF0F6FC)
            )

            Text(stringResource(id = R.string.str_your_unique_6_digit_code),
                fontSize = 14.sp,
                color = Color(0xFF8B949E),
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                textAlign = TextAlign.Center
            )

            // Permanent 6-Digit User Code Display
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                color = Color(0xFF161B22),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF30363D))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(id = R.string.str_your_permanent_code),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B949E),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = userCode,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF58A6FF), // Sleek accent
                        letterSpacing = 4.sp
                    )
                }
            }

            // Display Name input field
            OutlinedTextField(
                value = displayName,
                onValueChange = { viewModel.displayName.value = it },
                label = { Text(stringResource(R.string.str_display_name), color = Color(0xFF8B949E)) },
                placeholder = { Text(stringResource(R.string.str_how_others_see_you), color = Color(0xFF8B949E)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "Face Logo",
                        tint = Color(0xFF8B949E)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("display_name_input"),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF161B22),
                    unfocusedContainerColor = Color(0xFF161B22),
                    focusedTextColor = Color(0xFFF0F6FC),
                    unfocusedTextColor = Color(0xFFF0F6FC),
                    focusedBorderColor = Color(0xFF58A6FF),
                    unfocusedBorderColor = Color.Transparent,
                    focusedLabelColor = Color(0xFF58A6FF),
                    unfocusedLabelColor = Color(0xFF8B949E),
                    focusedPlaceholderColor = Color(0xFF8B949E),
                    unfocusedPlaceholderColor = Color(0xFF8B949E)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Phone Number input field
            OutlinedTextField(
                value = phoneNumberVal,
                onValueChange = { viewModel.phoneNumber.value = it },
                label = { Text(stringResource(R.string.str_phone_number), color = Color(0xFF8B949E)) },
                placeholder = { Text(stringResource(R.string.str_enter_your_phone_number), color = Color(0xFF8B949E)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Phone Icon",
                        tint = Color(0xFF8B949E)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("phone_number_input"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF161B22),
                    unfocusedContainerColor = Color(0xFF161B22),
                    focusedTextColor = Color(0xFFF0F6FC),
                    unfocusedTextColor = Color(0xFFF0F6FC),
                    focusedBorderColor = Color(0xFF58A6FF),
                    unfocusedBorderColor = Color.Transparent,
                    focusedLabelColor = Color(0xFF58A6FF),
                    unfocusedLabelColor = Color(0xFF8B949E),
                    focusedPlaceholderColor = Color(0xFF8B949E),
                    unfocusedPlaceholderColor = Color(0xFF8B949E)
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Birthday pickers header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Cake,
                    contentDescription = "Cake Icon",
                    tint = Color(0xFF58A6FF),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(id = R.string.str_birthday_date_of_birth_dob),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFFF0F6FC)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Triple dropdown picker selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Day selector
                Box(modifier = Modifier.weight(1f)) {
                    ExposedDropdownMenuBox(
                        expanded = showDayDropdown,
                        onExpandedChange = { showDayDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = bDay,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "dropdown arrow",
                                    tint = Color(0xFF8B949E)
                                )
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("day_picker_trigger"),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF161B22),
                                unfocusedContainerColor = Color(0xFF161B22),
                                focusedTextColor = Color(0xFFF0F6FC),
                                unfocusedTextColor = Color(0xFFF0F6FC),
                                focusedBorderColor = Color(0xFF58A6FF),
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                        DropdownMenu(
                            expanded = showDayDropdown,
                            onDismissRequest = { showDayDropdown = false },
                            modifier = Modifier.background(Color(0xFF161B22))
                        ) {
                            daysList.forEach { d ->
                                DropdownMenuItem(
                                    text = { Text(d, color = Color(0xFFF0F6FC)) },
                                    onClick = {
                                        viewModel.birthDay.value = d
                                        showDayDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Month selector
                Box(modifier = Modifier.weight(1.5f)) {
                    ExposedDropdownMenuBox(
                        expanded = showMonthDropdown,
                        onExpandedChange = { showMonthDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = bMonth,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "dropdown arrow",
                                    tint = Color(0xFF8B949E)
                                )
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("month_picker_trigger"),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF161B22),
                                unfocusedContainerColor = Color(0xFF161B22),
                                focusedTextColor = Color(0xFFF0F6FC),
                                unfocusedTextColor = Color(0xFFF0F6FC),
                                focusedBorderColor = Color(0xFF58A6FF),
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                        DropdownMenu(
                            expanded = showMonthDropdown,
                            onDismissRequest = { showMonthDropdown = false },
                            modifier = Modifier.background(Color(0xFF161B22))
                        ) {
                            monthsList.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m, color = Color(0xFFF0F6FC)) },
                                    onClick = {
                                        viewModel.birthMonth.value = m
                                        showMonthDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Year selector
                Box(modifier = Modifier.weight(1.2f)) {
                    ExposedDropdownMenuBox(
                        expanded = showYearDropdown,
                        onExpandedChange = { showYearDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = bYear,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "dropdown arrow",
                                    tint = Color(0xFF8B949E)
                                )
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("year_picker_trigger"),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF161B22),
                                unfocusedContainerColor = Color(0xFF161B22),
                                focusedTextColor = Color(0xFFF0F6FC),
                                unfocusedTextColor = Color(0xFFF0F6FC),
                                focusedBorderColor = Color(0xFF58A6FF),
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                        DropdownMenu(
                            expanded = showYearDropdown,
                            onDismissRequest = { showYearDropdown = false },
                            modifier = Modifier.background(Color(0xFF161B22))
                        ) {
                            yearsList.forEach { y ->
                                DropdownMenuItem(
                                    text = { Text(y, color = Color(0xFFF0F6FC)) },
                                    onClick = {
                                        viewModel.birthYear.value = y
                                        showYearDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Action Finish Setup Button (Task 4)
            Button(
                onClick = { viewModel.onFinishSetupClicked() },
                enabled = isButtonEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("finish_setup_button"),
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF58A6FF),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF58A6FF).copy(alpha = 0.5f),
                    disabledContentColor = Color.White.copy(alpha = 0.6f)
                )
            ) {
                Text(stringResource(id = R.string.str_finish_setup),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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

```

