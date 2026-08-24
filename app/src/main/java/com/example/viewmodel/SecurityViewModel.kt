package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ActiveSession
import com.example.model.UserProfile
import com.example.util.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SecurityViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _sessions = MutableStateFlow<List<ActiveSession>>(emptyList())
    val sessions = _sessions.asStateFlow()

    private val _blockedUsers = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val blockedUsers = _blockedUsers.asStateFlow()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    fun changePassword(
        currentPass: String,
        newPass: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onFailure("User not authenticated.")
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                user.updatePassword(newPass).await()
                _isLoading.value = false
                onSuccess()
            } catch (e: Exception) {
                Log.e("SecurityVM", "Failed to update password", e)
                _isLoading.value = false
                onFailure(e.localizedMessage ?: "Password update failed.")
            }
        }
    }

    fun fetchActiveSessions() {
        val uid = currentUserId
        if (uid.isEmpty()) return

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("active_sessions")
                    .whereEqualTo("userId", uid)
                    .get().await()
                val sessionList = snapshot.documents.mapNotNull { it.toObject(ActiveSession::class.java) }
                _sessions.value = sessionList
            } catch (e: Exception) {
                Log.e("SecurityVM", "Error fetching active sessions", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun terminateSession(sessionId: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val uid = currentUserId
        if (uid.isEmpty()) return

        _isLoading.value = true
        viewModelScope.launch {
            try {
                firestore.collection("active_sessions").document(sessionId).delete().await()
                fetchActiveSessions()
                onSuccess()
            } catch (e: Exception) {
                Log.e("SecurityVM", "Failed to terminate session: $sessionId", e)
                onFailure(e.localizedMessage ?: "Failed to terminate session.")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteAccount(
        currentPass: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            val loginState = SessionManager.getLoginState(getApplication())
            if (loginState.isLoggedIn) {
                _isLoading.value = true
                viewModelScope.launch {
                    try {
                        auth.signInAnonymously().await()
                        deleteAccountInternal(auth.currentUser, onSuccess, onFailure)
                    } catch (e: Exception) {
                        _isLoading.value = false
                        onFailure("User session expired. Please re-login.")
                    }
                }
            } else {
                onFailure("User not authenticated.")
            }
            return
        }
        deleteAccountInternal(user, onSuccess, onFailure)
    }

    private fun deleteAccountInternal(
        user: com.google.firebase.auth.FirebaseUser?,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = user?.uid ?: ""
        if (uid.isEmpty()) {
            _isLoading.value = false
            onFailure("User not authenticated.")
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                try {
                    firestore.collection("users").document(uid).delete().await()
                    firestore.collection("status").document(uid).delete().await()
                } catch (ex: Exception) {
                    Log.e("SecurityVM", "Failed to wipe Firestore node", ex)
                }

                try {
                    user?.delete()?.await()
                } catch (ex: Exception) {
                    Log.e("SecurityVM", "Firebase Auth delete user failed", ex)
                    auth.signOut()
                }

                SessionManager.clearLoginState(getApplication())
                onSuccess()
            } catch (e: Exception) {
                Log.e("SecurityVM", "Account deletion process failed", e)
                onFailure(e.localizedMessage ?: "Failed to delete account completely.")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updatePhotoVisibility(visibility: String) {
        val uid = currentUserId
        if (uid.isEmpty()) return

        SessionManager.savePhotoVis(getApplication(), visibility)
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .update("profilePhotoVisibility", visibility)
            } catch (e: Exception) {
                Log.e("SecurityVM", "Failed to sync photo visibility", e)
            }
        }
    }

    fun updateAboutVisibility(visibility: String) {
        val uid = currentUserId
        if (uid.isEmpty()) return

        getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit().putString("about_visibility", visibility).apply()

        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .update("aboutVisibility", visibility)
            } catch (e: Exception) {
                Log.e("SecurityVM", "Failed to sync about visibility", e)
            }
        }
    }

    fun updateDisappearingMessages(durationMs: Long) {
        val uid = currentUserId
        if (uid.isEmpty()) return

        SessionManager.saveDisappearingTimer(getApplication(), durationMs)
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .update("disappearingTimer", durationMs)
            } catch (e: Exception) {
                Log.e("SecurityVM", "Failed to sync disappearing messages timer", e)
            }
        }
    }

    fun fetchBlockedUsers() {
        val uid = currentUserId
        if (uid.isEmpty()) return

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("blocked_users")
                    .whereEqualTo("userId", uid)
                    .get().await()

                val blockedUids = snapshot.documents.mapNotNull { it.getString("blockedUserId") }
                val resolvedList = mutableListOf<Pair<String, String>>()
                for (bUid in blockedUids) {
                    if (bUid.isEmpty()) continue
                    try {
                        val doc = firestore.collection("users").document(bUid).get().await()
                        val user = doc.toObject(UserProfile::class.java)
                        val displayName = user?.displayName ?: "User ($bUid)"
                        resolvedList.add(bUid to displayName)
                    } catch (ex: Exception) {
                        resolvedList.add(bUid to bUid)
                    }
                }

                _blockedUsers.value = resolvedList
            } catch (e: Exception) {
                Log.e("SecurityVM", "Failed to fetch blocked users", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun blockUser(targetUid: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val uid = currentUserId
        if (uid.isEmpty() || targetUid.isEmpty()) {
            onFailure("Invalid user parameters.")
            return
        }

        if (uid == targetUid) {
            onFailure("You cannot block yourself.")
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val targetDoc = firestore.collection("users").document(targetUid).get().await()
                if (!targetDoc.exists()) {
                    onFailure("User ID does not exist.")
                    _isLoading.value = false
                    return@launch
                }

                firestore.collection("blocked_users").add(mapOf(
                    "userId" to uid,
                    "blockedUserId" to targetUid
                )).await()

                fetchBlockedUsers()
                onSuccess()
            } catch (e: Exception) {
                Log.e("SecurityVM", "Failed to block user", e)
                onFailure(e.localizedMessage ?: "Failed to block user.")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun unblockUser(targetUid: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val uid = currentUserId
        if (uid.isEmpty()) return

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("blocked_users")
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("blockedUserId", targetUid)
                    .get().await()

                for (doc in snapshot.documents) {
                    doc.reference.delete().await()
                }

                fetchBlockedUsers()
                onSuccess()
            } catch (e: Exception) {
                Log.e("SecurityVM", "Failed to unblock user", e)
                onFailure(e.localizedMessage ?: "Failed to unblock user.")
            } finally {
                _isLoading.value = false
            }
        }
    }
}
