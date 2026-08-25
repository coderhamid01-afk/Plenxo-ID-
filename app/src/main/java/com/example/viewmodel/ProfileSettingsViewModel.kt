package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.withContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.usecase.FetchUserProfileUseCase
import com.example.domain.usecase.SaveUserProfileUseCase
import com.example.model.UserProfileDomainModel
import com.example.repository.ProfileSettingsRepositoryImpl
import com.example.repository.AccountDeletionRepositoryImpl
import com.example.repository.AccountDeletionUiState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface ProfileUiState {
    object Loading : ProfileUiState
    data class Success(val profile: UserProfileDomainModel) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

sealed interface UpdateUiState {
    object Idle : UpdateUiState
    object Loading : UpdateUiState
    data class Uploading(val progress: Int) : UpdateUiState
    object Success : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class ProfileSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProfileSettingsRepositoryImpl()
    private val fetchUserProfileUseCase = FetchUserProfileUseCase(repository)
    private val saveUserProfileUseCase = SaveUserProfileUseCase(repository)
    private val accountDeletionRepo = AccountDeletionRepositoryImpl()

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Int>(0)
    val uploadProgress: StateFlow<Int> = _uploadProgress.asStateFlow()

    private val _accountDeletionState = MutableStateFlow<AccountDeletionUiState>(AccountDeletionUiState.Idle)
    val accountDeletionState: StateFlow<AccountDeletionUiState> = _accountDeletionState.asStateFlow()

    private val _deletionPasswordError = MutableStateFlow<String?>(null)
    val deletionPasswordError: StateFlow<String?> = _deletionPasswordError.asStateFlow()

    private val _isReauthenticating = MutableStateFlow(false)
    val isReauthenticating: StateFlow<Boolean> = _isReauthenticating.asStateFlow()

    private val auth get() = FirebaseAuth.getInstance()
    private val firestore get() = FirebaseFirestore.getInstance()

    val currentUid: String
        get() {
            val fbUid = auth.currentUser?.uid
            if (!fbUid.isNullOrEmpty()) return fbUid
            val savedToken = com.example.util.SessionManager.getLoginState(getApplication()).token
            if (!savedToken.isNullOrEmpty()) return savedToken
            val savedEmail = com.example.util.SessionManager.getLoginState(getApplication()).email
            if (!savedEmail.isNullOrEmpty()) return savedEmail.replace(".", "_")
            return ""
        }

    val profileUiState: StateFlow<ProfileUiState> = flow {
        val resolvedUid = currentUid
        if (resolvedUid.isEmpty()) {
            emit(ProfileUiState.Error("User not authenticated"))
        } else {
            val userEmail = auth.currentUser?.email 
                ?: com.example.util.SessionManager.getLoginState(getApplication()).email 
                ?: ""
            fetchUserProfileUseCase(resolvedUid)
                .map { profile ->
                    if (profile != null) {
                        ProfileUiState.Success(profile)
                    } else {
                        val local = com.example.util.SessionManager.getUserProfileLocally(getApplication())
                        val resolvedName = local.displayName.ifBlank {
                            auth.currentUser?.displayName ?: if (userEmail.contains("@")) userEmail.substringBefore("@") else "User"
                        }
                        val resolvedBio = local.bio.ifBlank { "" }
                        val resolvedPic = local.profilePicUrl.ifBlank { auth.currentUser?.photoUrl?.toString() ?: "" }
                        val resolvedPxId = local.plenxoId.ifBlank { "" }
                        ProfileUiState.Success(
                            UserProfileDomainModel(
                                userId = resolvedUid,
                                email = userEmail,
                                name = resolvedName,
                                displayName = resolvedName,
                                bio = resolvedBio,
                                statusMessage = resolvedBio,
                                profileUrl = resolvedPic,
                                profilePicUrl = resolvedPic,
                                plenxoId = resolvedPxId,
                                userCode = resolvedPxId
                            )
                        )
                    }
                }
                .catch { emit(ProfileUiState.Error(it.message ?: "Unknown Error")) }
                .collect { emit(it) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState.Loading
    )

    /**
     * Saves user profile updates to Firestore and Firebase Auth.
     */
    fun saveProfile(name: String, bio: String, profileUrl: String) {
        val resolvedUid = currentUid
        if (resolvedUid.isEmpty()) {
            _updateState.value = UpdateUiState.Error("User not authenticated")
            return
        }

        if (name.isBlank()) {
            _updateState.value = UpdateUiState.Error("Display Name cannot be empty")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = UpdateUiState.Loading
            _uploadProgress.value = 0

            try {
                val timedOutResult = kotlinx.coroutines.withTimeoutOrNull(15000L) {
                    var finalProfileUrl = profileUrl

                    val isLocalUri = profileUrl.startsWith("content://") ||
                            profileUrl.startsWith("file://") ||
                            (profileUrl.isNotEmpty() && !profileUrl.startsWith("http://") && !profileUrl.startsWith("https://"))

                    if (isLocalUri) {
                        Log.d("ProfileSettingsVM", "Uploading local profile picture to Catbox for user: $resolvedUid")
                        val localUri = Uri.parse(profileUrl)
                        _updateState.value = UpdateUiState.Uploading(50)
                        try {
                            val downloadUrl = com.example.network.CatboxUploader.uploadImage(getApplication<Application>(), localUri)
                            finalProfileUrl = downloadUrl
                            _updateState.value = UpdateUiState.Uploading(100)
                            Log.d("ProfileSettingsVM", "Catbox upload complete. Download URL: $finalProfileUrl")
                        } catch (e: Exception) {
                            Log.e("ProfileSettingsVM", "Catbox upload error: ${e.message}", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(getApplication<Application>(), "Failed to upload image to Catbox. Please try again.", Toast.LENGTH_LONG).show()
                                _updateState.value = UpdateUiState.Error("Failed to upload image to Catbox. Please try again.")
                            }
                            return@withTimeoutOrNull false
                        }
                    }

                    com.example.util.ProfileHistoryUtils.saveProfileWithHistory(
                        uid = resolvedUid,
                        newName = name,
                        newBio = bio,
                        newProfileUrl = finalProfileUrl,
                        firestore = firestore,
                        auth = auth
                    )

                    val profileData = mapOf(
                        "profilePicUrl" to finalProfileUrl,
                        "avatar_url" to finalProfileUrl,
                        "photoUrl" to finalProfileUrl,
                        "profileUrl" to finalProfileUrl,
                        "displayName" to name,
                        "name" to name,
                        "bio" to bio,
                        "statusMessage" to bio,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )

                    firestore.collection("users").document(resolvedUid)
                        .set(profileData, SetOptions.merge())
                        .await()

                    try {
                        val authProfileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .apply {
                                if (finalProfileUrl.isNotEmpty() && finalProfileUrl.startsWith("http")) {
                                    setPhotoUri(Uri.parse(finalProfileUrl))
                                }
                            }
                            .build()
                        auth.currentUser?.updateProfile(authProfileUpdates)?.await()
                    } catch (authEx: Exception) {
                        Log.w("ProfileSettingsVM", "Firebase Auth profile update warning: ${authEx.message}")
                    }

                    withContext(Dispatchers.Main) {
                        _uploadProgress.value = 100
                        _updateState.value = UpdateUiState.Success
                    }
                    true
                }

                if (timedOutResult == null) {
                    Log.e("ProfileSettingsVM", "saveProfile operation timed out after 15s")
                    withContext(Dispatchers.Main) {
                        _uploadProgress.value = 0
                        _updateState.value = UpdateUiState.Error("Request timed out. Please check your connection and try again.")
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileSettingsVM", "Failed to save profile to Firebase: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _uploadProgress.value = 0
                    _updateState.value = UpdateUiState.Error(e.localizedMessage ?: e.message ?: "Failed to save profile to Firebase Storage and Firestore")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _uploadProgress.value = 0
                    if (_updateState.value is UpdateUiState.Loading || _updateState.value is UpdateUiState.Uploading) {
                        _updateState.value = UpdateUiState.Idle
                    }
                }
            }
        }
    }

    fun resetUpdateState() {
        _updateState.value = UpdateUiState.Idle
        _uploadProgress.value = 0
    }

    fun updateRing(ringId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.updateRing(currentUserId, ringId)
            } catch (e: Exception) {
                Log.e("ProfileSettingsVM", "Failed to update ring: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _updateState.value = UpdateUiState.Error(e.message ?: "Failed to update ring")
                }
            }
        }
    }

    fun updateProfileRing(ringId: String, onResult: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.updateProfileRing(currentUserId, ringId)
                com.example.util.SessionManager.saveProfileRingId(getApplication(), ringId)
                withContext(Dispatchers.Main) {
                    onResult(true)
                }
            } catch (e: Exception) {
                Log.e("ProfileSettingsVM", "Failed to update profile ring: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _updateState.value = UpdateUiState.Error(e.message ?: "Failed to update profile ring")
                    onResult(false)
                }
            }
        }
    }

    // ==========================================
    // 2-STEP ACCOUNT DELETION WORKFLOW METHODS
    // ==========================================

    fun initiateAccountDeletion() {
        _deletionPasswordError.value = null
        _accountDeletionState.value = AccountDeletionUiState.ShowPasswordDialog
    }

    fun verifyPasswordAndProceed(password: String) {
        if (password.isBlank()) {
            _deletionPasswordError.value = "Password cannot be empty"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isReauthenticating.value = true
            _deletionPasswordError.value = null

            val result = accountDeletionRepo.reauthenticateUser(password)
            _isReauthenticating.value = false

            if (result.isSuccess) {
                Log.d("ProfileSettingsVM", "Re-authentication successful, moving to Warning Dialog")
                _accountDeletionState.value = AccountDeletionUiState.ShowWarningDialog
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Incorrect password. Please verify and try again."
                Log.e("ProfileSettingsVM", "Re-authentication failed: $errorMsg")
                _deletionPasswordError.value = errorMsg
                _accountDeletionState.value = AccountDeletionUiState.ShowPasswordDialog
            }
        }
    }

    fun executeAccountDeletion() {
        viewModelScope.launch(Dispatchers.IO) {
            _accountDeletionState.value = AccountDeletionUiState.Deleting
            Log.d("ProfileSettingsVM", "Executing full 6-Step account deletion pipeline...")

            val result = accountDeletionRepo.executeFullAccountDeletion(getApplication())
            if (result.isSuccess) {
                Log.d("ProfileSettingsVM", "Account deletion pipeline succeeded")
                _accountDeletionState.value = AccountDeletionUiState.Success
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to delete account. Please try again."
                Log.e("ProfileSettingsVM", "Account deletion pipeline failed: $errorMsg")
                _accountDeletionState.value = AccountDeletionUiState.Error(errorMsg)
            }
        }
    }

    fun dismissAccountDeletion() {
        _deletionPasswordError.value = null
        _isReauthenticating.value = false
        _accountDeletionState.value = AccountDeletionUiState.Idle
    }
}
