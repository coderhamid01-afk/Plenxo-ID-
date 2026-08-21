package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ChatRequest
import com.example.repository.ChatRequestRepository
import com.example.repository.ChatRequestRepositoryImpl
import com.example.repository.UserRepository
import com.example.repository.UserRepositoryImpl
import com.example.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatRequestViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: ChatRequestRepository = ChatRequestRepositoryImpl()
    private val userRepository: UserRepository = UserRepositoryImpl()

    private val currentUid: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val _incomingRequests = MutableStateFlow<List<ChatRequest>>(emptyList())
    val incomingRequests: StateFlow<List<ChatRequest>> = _incomingRequests.asStateFlow()

    private val _sentRequests = MutableStateFlow<List<ChatRequest>>(emptyList())
    val sentRequests: StateFlow<List<ChatRequest>> = _sentRequests.asStateFlow()

    private val _allRequests = MutableStateFlow<List<ChatRequest>>(emptyList())
    val allRequests: StateFlow<List<ChatRequest>> = _allRequests.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _contactStatuses = MutableStateFlow<Map<String, String>>(emptyMap())
    val contactStatuses: StateFlow<Map<String, String>> = _contactStatuses.asStateFlow()

    private var previousIncomingRequestIds = setOf<String>()

    init {
        val uid = currentUid
        if (uid.isNotEmpty()) {
            observeIncomingRequests(uid)
            observeSentRequests(uid)
            observeAllRequests(uid)
            observeContactStatuses(uid)
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    private fun observeContactStatuses(uid: String) {
        viewModelScope.launch {
            repository.observeContactStatuses(uid).collect { statuses ->
                _contactStatuses.value = statuses
            }
        }
    }

    private fun observeIncomingRequests(uid: String) {
        viewModelScope.launch {
            repository.observeIncomingRequests(uid).collect { requests ->
                val validRequests = requests.filter { 
                    val st = it.status.lowercase()
                    st == "pending" || st == "accepted" || st.isEmpty()
                }
                _incomingRequests.value = validRequests

                // Fire notification if new incoming pending chat request arrives
                val pendingRequests = validRequests.filter { it.status.lowercase() == "pending" || it.status.isEmpty() }
                val currentIds = pendingRequests.map { it.id }.toSet()
                if (previousIncomingRequestIds.isNotEmpty()) {
                    val newRequests = pendingRequests.filter { it.id !in previousIncomingRequestIds }
                    for (req in newRequests) {
                        val senderName = req.senderName.ifEmpty { "Plenxo User" }
                        NotificationHelper.showNotification(
                            context = getApplication(),
                            title = "New Chat Request",
                            message = "New Chat Request from $senderName",
                            targetScreen = "CHAT_REQUESTS",
                            extraData = mapOf("type" to "chat_request", "senderId" to req.senderId)
                        )
                    }
                }
                previousIncomingRequestIds = currentIds
            }
        }
    }

    private fun observeSentRequests(uid: String) {
        viewModelScope.launch {
            repository.observeSentRequests(uid).collect { requests ->
                _sentRequests.value = requests
            }
        }
    }

    private fun observeAllRequests(uid: String) {
        viewModelScope.launch {
            repository.observeAllRequestsForUser(uid).collect { requests ->
                _allRequests.value = requests
            }
        }
    }

    /**
     * Dispatch chat request to target user.
     */
    fun sendChatRequest(targetUser: Map<String, Any>) {
        val senderUid = currentUid
        val receiverUid = (targetUser["uid"] as? String) ?: (targetUser["id"] as? String) ?: ""
        val targetPlenxoId = (targetUser["plenxoId"] as? String) ?: ""

        if (senderUid.isBlank() || receiverUid.isBlank()) {
            _toastMessage.value = "Invalid user to send request"
            return
        }

        if (senderUid == receiverUid) {
            _toastMessage.value = "You cannot send a request to yourself"
            return
        }

        viewModelScope.launch {
            val currentUserData = userRepository.getUserData(senderUid)
            val senderName = (currentUserData?.get("displayName") as? String) ?: "User"
            val senderPlenxoId = (currentUserData?.get("plenxoId") as? String) ?: ""
            val senderProfilePic = (currentUserData?.get("profilePicUrl") as? String) ?: ""

            val success = repository.sendChatRequest(
                senderUid = senderUid,
                receiverUid = receiverUid,
                senderName = senderName,
                senderPlenxoId = senderPlenxoId,
                senderPhotoUrl = senderProfilePic,
                receiverPlenxoId = targetPlenxoId
            )

            if (success) {
                _toastMessage.value = "Chat request sent to $targetPlenxoId!"
            } else {
                _toastMessage.value = "Failed to send chat request. Please try again."
            }
        }
    }

    /**
     * Accept incoming chat request.
     */
    fun acceptRequest(request: ChatRequest) {
        _incomingRequests.value = _incomingRequests.value.map {
            if (it.id == request.id) it.copy(status = "ACCEPTED") else it
        }
        viewModelScope.launch {
            try {
                val success = repository.acceptChatRequest(
                    requestId = request.id,
                    senderUid = request.senderId,
                    receiverUid = request.receiverId
                )

                if (success) {
                    _toastMessage.value = "Request Accepted!"
                } else {
                    _toastMessage.value = "Failed to accept request."
                }
            } catch (e: Exception) {
                Log.e("ChatRequestViewModel", "Error in acceptRequest: ${e.message}", e)
                _toastMessage.value = "Failed to accept request: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Reject incoming chat request.
     */
    fun rejectRequest(request: ChatRequest) {
        _incomingRequests.value = _incomingRequests.value.filter { it.id != request.id }
        viewModelScope.launch {
            val success = repository.rejectChatRequest(
                requestId = request.id,
                senderUid = request.senderId,
                receiverUid = request.receiverId
            )

            if (success) {
                _toastMessage.value = "Chat request rejected"
            } else {
                _toastMessage.value = "Failed to reject request."
            }
        }
    }
}

