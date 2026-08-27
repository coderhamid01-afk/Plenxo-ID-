package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AuthState
import com.example.network.CatboxStorageManager
import com.example.util.DateUtils
import com.example.util.Language
import com.example.util.plenxoLanguages
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class ProfileSetupUiState {
    object Idle : ProfileSetupUiState()
    data class Loading(val message: String = "Saving profile...") : ProfileSetupUiState()
    data class Success(val plenxoId: String) : ProfileSetupUiState()
    data class Error(val message: String) : ProfileSetupUiState()
}

class ProfileSetupViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow<ProfileSetupUiState>(ProfileSetupUiState.Idle)
    val uiState: StateFlow<ProfileSetupUiState> = _uiState.asStateFlow()

    val profilePictureUri = MutableStateFlow<Uri?>(null)
    val displayName = MutableStateFlow("")
    val bio = MutableStateFlow("")
    val dobMillis = MutableStateFlow<Long?>(null)
    val selectedGender = MutableStateFlow("Prefer not to say")
    val selectedLanguage = MutableStateFlow(
        plenxoLanguages.find { it.code == "en" } ?: Language("English", "en")
    )
    val favouriteColorHex = MutableStateFlow("#58A6FF")
    val selectedTheme = MutableStateFlow("System Default")

    init {
        // Pre-fill name from FirebaseAuth user if available
        val currentUser = auth.currentUser
        if (currentUser != null) {
            if (!currentUser.displayName.isNullOrBlank()) {
                displayName.value = currentUser.displayName ?: ""
            }
            if (currentUser.photoUrl != null && profilePictureUri.value == null) {
                profilePictureUri.value = currentUser.photoUrl
            }

            val uid = currentUser.uid
            viewModelScope.launch {
                try {
                    val readResult = com.example.model.fetchUserDocumentSafely(uid, firestore)
                    if (readResult.readConfirmed && readResult.snapshot != null && readResult.snapshot.exists()) {
                        val docSnap = readResult.snapshot
                        val name = docSnap.getString("displayName") ?: docSnap.getString("name") ?: ""
                        val b = docSnap.getString("bio") ?: docSnap.getString("statusMessage") ?: ""
                        val pic = docSnap.getString("profilePicUrl") ?: docSnap.getString("avatar_url") ?: docSnap.getString("photoUrl") ?: ""
                        val dob = docSnap.getLong("dobTimestamp")

                        if (name.isNotBlank() && displayName.value.isBlank()) displayName.value = name
                        if (b.isNotBlank() && bio.value.isBlank()) bio.value = b
                        if (dob != null && dob > 0L) dobMillis.value = dob
                        if (pic.isNotBlank() && profilePictureUri.value == null) {
                            try { profilePictureUri.value = Uri.parse(pic) } catch (_: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    Log.w("ProfileSetupVM", "Pre-fetch profile warning: ${e.message}")
                }
            }
        }
    }

    fun setProfilePicture(uri: Uri?) {
        profilePictureUri.value = uri
    }

    fun setDisplayName(name: String) {
        displayName.value = name
    }

    fun setBio(newBio: String) {
        if (newBio.length <= 100) {
            bio.value = newBio
        }
    }

    fun setDob(millis: Long?) {
        dobMillis.value = millis
    }

    fun setGender(gender: String) {
        selectedGender.value = gender
    }

    fun setLanguage(context: Context, language: Language) {
        selectedLanguage.value = language
        com.example.util.LocaleHelper.setLocale(context, language.code)
    }

    fun setFavouriteColor(colorHex: String) {
        favouriteColorHex.value = colorHex
    }

    fun setTheme(theme: String) {
        selectedTheme.value = theme
    }

    fun resetState() {
        _uiState.value = ProfileSetupUiState.Idle
    }

    fun saveProfile(context: Context, mainViewModel: PlenxoViewModel? = null) {
        if (mainViewModel != null && (mainViewModel.authState.value == AuthState.UNAUTHENTICATED || mainViewModel.authState.value == AuthState.VERIFYING_OTP)) {
            _uiState.value = ProfileSetupUiState.Error("OTP verification required before setting up profile. Please verify your email code.")
            return
        }

        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            _uiState.value = ProfileSetupUiState.Error("User not authenticated. Please log in.")
            return
        }

        if (displayName.value.trim().isBlank()) {
            _uiState.value = ProfileSetupUiState.Error("Please enter your display name.")
            return
        }

        viewModelScope.launch {
            _uiState.value = ProfileSetupUiState.Loading("Uploading profile photo...")

            var catboxUrl = ""
            val imageUri = profilePictureUri.value

            if (imageUri != null && imageUri.scheme != null && imageUri.scheme!!.startsWith("http")) {
                // Already an HTTP URL
                catboxUrl = imageUri.toString()
            } else if (imageUri != null) {
                try {
                    kotlinx.coroutines.withTimeoutOrNull(10000L) {
                        catboxUrl = com.example.network.CatboxUploader.uploadImage(context, imageUri)
                    }
                    Log.d("ProfileSetupVM", "Profile pic uploaded to Catbox: $catboxUrl")
                } catch (e: Exception) {
                    Log.e("ProfileSetupVM", "Catbox upload error: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Image upload took too long or failed. Proceeding with default avatar.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            _uiState.value = ProfileSetupUiState.Loading("Saving profile details...")

            try {
                val saveSuccess = kotlinx.coroutines.withTimeoutOrNull(12000L) {
                    val readResult = com.example.model.fetchUserDocumentSafely(uid, firestore)
                    val existingSnap = readResult.snapshot
                    val existingPlenxoId = com.example.model.resolveOrCreatePlenxoId(uid, firestore)
                    val numericCode = existingPlenxoId.removePrefix("PX-")

                    val userEmail = auth.currentUser?.email ?: ""
                    val formattedDob = DateUtils.formatDateForFirestore(dobMillis.value)
                    val dob = dobMillis.value
                    val calculatedAgeInt = if (dob != null && dob > 0L) {
                        val dobCal = java.util.Calendar.getInstance().apply { timeInMillis = dob }
                        val today = java.util.Calendar.getInstance()
                        var age = today.get(java.util.Calendar.YEAR) - dobCal.get(java.util.Calendar.YEAR)
                        if (today.get(java.util.Calendar.DAY_OF_YEAR) < dobCal.get(java.util.Calendar.DAY_OF_YEAR)) {
                            age--
                        }
                        if (age < 0) 0 else age
                    } else 0

                    val existingPicUrl = existingSnap?.getString("profilePicUrl") 
                        ?: existingSnap?.getString("avatar_url") 
                        ?: existingSnap?.getString("photoUrl") 
                        ?: ""
                    val finalPicUrl = catboxUrl.ifBlank { existingPicUrl }
                    val existingBio = existingSnap?.getString("bio") ?: existingSnap?.getString("statusMessage") ?: ""
                    val finalBio = if (bio.value.isNotBlank()) bio.value.trim() else existingBio

                    val userData = hashMapOf<String, Any>(
                        "uid" to uid,
                        "id" to uid,
                        "email" to userEmail,
                        "displayName" to displayName.value.trim(),
                        "name" to displayName.value.trim(),
                        "current_name" to displayName.value.trim(),
                        "bio" to finalBio,
                        "current_bio" to finalBio,
                        "statusMessage" to finalBio,
                        "dateOfBirth" to formattedDob,
                        "dobTimestamp" to (dobMillis.value ?: 0L),
                        "age" to calculatedAgeInt,
                        "gender" to selectedGender.value,
                        "language" to selectedLanguage.value.name,
                        "languageCode" to selectedLanguage.value.code,
                        "favouriteColour" to favouriteColorHex.value,
                        "theme" to selectedTheme.value,
                        "profilePic" to finalPicUrl,
                        "profilePicUrl" to finalPicUrl,
                        "photoUrl" to finalPicUrl,
                        "current_profile_pic_url" to finalPicUrl,
                        "plenxoId" to existingPlenxoId,
                        "plenxo_id" to existingPlenxoId,
                        "userCode" to numericCode,
                        "user_code" to numericCode,
                        "px_id" to existingPlenxoId,
                        "px_code" to numericCode,
                        "isProfileCompleted" to true,
                        "isProfileSetupCompleted" to true,
                        "isProfileSetup" to true,
                        "profileSetupCompleted" to true,
                        "is_profile_completed" to true,
                        "profileRing" to (existingSnap?.getString("profileRing") ?: "none"),
                        "profileRingId" to (existingSnap?.getString("profileRingId") ?: "none"),
                        "selectedRingId" to (existingSnap?.getString("selectedRingId") ?: "none"),
                        "createdAt" to (existingSnap?.getLong("createdAt") ?: System.currentTimeMillis()),
                        "updatedAt" to System.currentTimeMillis()
                    )

                    firestore.collection("users").document(uid).set(userData, SetOptions.merge()).await()

                    try {
                        val rdbRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users").child(uid)
                        val rdbMap = hashMapOf<String, Any>(
                            "uid" to uid,
                            "email" to userEmail,
                            "displayName" to displayName.value.trim(),
                            "name" to displayName.value.trim(),
                            "plenxo_id" to existingPlenxoId,
                            "plenxoId" to existingPlenxoId,
                            "user_code" to numericCode,
                            "userCode" to numericCode,
                            "profile_pic_url" to catboxUrl,
                            "profilePicUrl" to catboxUrl,
                            "bio" to bio.value.trim(),
                            "created_at" to System.currentTimeMillis(),
                            "createdAt" to System.currentTimeMillis(),
                            "is_profile_completed" to true
                        )
                        rdbRef.updateChildren(rdbMap).await()
                    } catch (rdbEx: Exception) {
                        Log.w("ProfileSetupVM", "Failed Realtime DB update: ${rdbEx.message}")
                    }

                    Log.d("PlenxoProfileSync", "User Profile persisted successfully with plenxoId: $existingPlenxoId")

                    // Mark session as logged in ONLY AFTER database writes are 100% confirmed
                    com.example.util.SessionManager.saveLoginState(context, uid, userEmail)

                    // Save selected language in AppCompatDelegate for per-app language setting
                    try {
                        val localeList = LocaleListCompat.forLanguageTags(selectedLanguage.value.code)
                        AppCompatDelegate.setApplicationLocales(localeList)
                    } catch (e: Exception) {
                        Log.e("ProfileSetupVM", "Failed to apply locale: ${e.message}")
                    }

                    if (mainViewModel != null) {
                        mainViewModel.setAuthState(AuthState.AUTHENTICATED)
                        mainViewModel.displayName.value = displayName.value.trim()
                        mainViewModel.plenxoId.value = existingPlenxoId
                        mainViewModel.userCode.value = numericCode
                        if (catboxUrl.isNotBlank()) {
                            mainViewModel.avatarType.value = "gallery"
                            mainViewModel.galleryImageUriString.value = catboxUrl
                        }
                    }

                    _uiState.value = ProfileSetupUiState.Success(existingPlenxoId)
                    true
                }

                if (saveSuccess == null) {
                    _uiState.value = ProfileSetupUiState.Error("Profile save timed out. Please check your network connection and try again.")
                }
            } catch (e: Exception) {
                Log.e("ProfileSetupVM", "Firestore save error or timeout: ${e.message}", e)
                _uiState.value = ProfileSetupUiState.Error("Failed to save profile: ${e.localizedMessage ?: e.message}")
            }
        }
    }
}
