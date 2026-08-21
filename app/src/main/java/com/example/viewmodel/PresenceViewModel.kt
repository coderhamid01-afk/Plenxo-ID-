package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class UserPresence(
    val status: String = "offline", // "online" | "offline"
    val isTyping: Boolean = false,
    val typingTo: String = "",
    val lastSeen: Long = 0L
) {
    val isOnline: Boolean get() = status == "online"
}

class PresenceViewModel(application: Application) : AndroidViewModel(application), DefaultLifecycleObserver {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private val _userPresences = MutableStateFlow<Map<String, UserPresence>>(emptyMap())
    val userPresences: StateFlow<Map<String, UserPresence>> = _userPresences.asStateFlow()

    private var typingTimerJob: Job? = null
    private val presenceListeners = mutableMapOf<String, ListenerRegistration>()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        updatePresence(status = "online")
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        updatePresence(status = "online")
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        updatePresence(status = "offline")
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        updatePresence(status = "offline")
    }

    fun updatePresence(status: String) {
        val uid = auth.currentUser?.uid ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lastSeenTime = System.currentTimeMillis()
                val data = mapOf(
                    "status" to status,
                    "isTyping" to if (status == "offline") false else (_userPresences.value[uid]?.isTyping ?: false),
                    "lastSeen" to lastSeenTime
                )

                firestore.collection("users_presence").document(uid).set(data, SetOptions.merge())

                val currentMap = _userPresences.value.toMutableMap()
                val existing = currentMap[uid] ?: UserPresence()
                currentMap[uid] = existing.copy(
                    status = status,
                    lastSeen = lastSeenTime,
                    isTyping = if (status == "offline") false else existing.isTyping
                )
                _userPresences.value = currentMap
            } catch (e: Exception) {
                Log.e("PresenceViewModel", "Failed to track presence state", e)
            }
        }
    }

    fun onUserTyping(targetUid: String) {
        val uid = auth.currentUser?.uid ?: return

        typingTimerJob?.cancel()

        viewModelScope.launch(Dispatchers.IO) {
            sendTypingBroadcast(uid, targetUid, isTyping = true)
        }

        // Reset isTyping = false after 2 seconds of inactivity
        typingTimerJob = viewModelScope.launch(Dispatchers.IO) {
            delay(2000)
            sendTypingBroadcast(uid, targetUid, isTyping = false)
        }
    }

    fun onUserStoppedTyping(targetUid: String) {
        val uid = auth.currentUser?.uid ?: return
        typingTimerJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            sendTypingBroadcast(uid, targetUid, isTyping = false)
        }
    }

    private suspend fun sendTypingBroadcast(senderUid: String, targetUid: String, isTyping: Boolean) {
        try {
            val data = mapOf(
                "status" to "online",
                "isTyping" to isTyping,
                "typingTo" to if (isTyping) targetUid else "",
                "lastSeen" to System.currentTimeMillis()
            )
            firestore.collection("users_presence").document(senderUid).set(data, SetOptions.merge())
        } catch (e: Exception) {
            Log.e("PresenceViewModel", "Failed to broadcast typing state", e)
        }
    }

    fun startListeningToUserPresence(targetUid: String) {
        if (targetUid.isEmpty() || presenceListeners.containsKey(targetUid)) return

        val listener = firestore.collection("users_presence").document(targetUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val status = snapshot.getString("status") ?: "offline"
                val isTyping = snapshot.getBoolean("isTyping") ?: false
                val typingTo = snapshot.getString("typingTo") ?: ""
                val lastSeen = snapshot.getLong("lastSeen") ?: 0L

                val currentMap = _userPresences.value.toMutableMap()
                currentMap[targetUid] = UserPresence(
                    status = status,
                    isTyping = isTyping,
                    typingTo = typingTo,
                    lastSeen = lastSeen
                )
                _userPresences.value = currentMap
            }

        presenceListeners[targetUid] = listener
    }

    fun stopListeningToUserPresence(targetUid: String) {
        presenceListeners.remove(targetUid)?.remove()
    }

    override fun onCleared() {
        super.onCleared()
        presenceListeners.values.forEach { it.remove() }
        presenceListeners.clear()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
    }
}
