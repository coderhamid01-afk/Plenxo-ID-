package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.Message
import com.example.model.MessageStatus
import com.example.repository.CloudChatRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

private const val MAX_BYTE_SIZE_100_MB = 100 * 1024 * 1024L // 100 MB limit
private const val MAX_IMAGE_BATCH_COUNT = 15

/**
 * UNUSED / DEAD CODE NOTICE:
 * This file is currently unused/dead code and is not referenced from NavGraph.kt or PlenxoAppContent.kt.
 * The active implementation lives in ui/chat/ChatDetailScreen.kt driven directly by PlenxoViewModel.
 *
 * ChatViewModel managing:
 * 1. Text Message Validation (Max 100 MB byte size)
 * 2. Image Batch Validation (Max 15 images per batch)
 * 3. File Sharing Validation (Max 100 MB file size limit)
 * 4. Voice Note Dispatch & Validation (Max 100 MB limit)
 * 5. Optimistic local updates and background uploads
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CloudChatRepositoryImpl(application)

    private val _optimisticMessages = MutableStateFlow<List<Message>>(emptyList())
    val optimisticMessages: StateFlow<List<Message>> = _optimisticMessages.asStateFlow()

    private val _remoteMessages = MutableStateFlow<List<Message>>(emptyList())

    private val _toastEvent = MutableStateFlow<String?>(null)
    val toastEvent: StateFlow<String?> = _toastEvent.asStateFlow()

    fun clearToast() {
        _toastEvent.value = null
    }

    val messages: StateFlow<List<Message>> = combine(
        _remoteMessages,
        _optimisticMessages
    ) { remoteList, localList ->
        val remoteIds = remoteList.map { it.messageId }.toSet()
        val filteredLocal = localList.filter { it.messageId !in remoteIds }
        (remoteList + filteredLocal).sortedBy { it.timestamp ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun listenToChat(chatId: String) {
        if (chatId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.streamMessages(chatId).collect { payloads ->
                val mapped = payloads.map { payload ->
                    Message(
                        messageId = payload.messageId,
                        chatId = payload.chatId,
                        senderId = payload.senderId,
                        receiverId = payload.receiverId,
                        messageText = payload.messageText,
                        mediaUrl = payload.mediaUrl,
                        timestamp = payload.timestamp,
                        status = payload.status,
                        messageStatus = parseStatus(payload.status),
                        messageType = payload.messageType,
                        expiresAt = payload.expiresAt
                    )
                }
                _remoteMessages.value = mapped
            }
        }
    }

    /**
     * 1. TEXT MESSAGE SIZE VALIDATION (100 MB MAX):
     * Checks UTF-8 byte length <= 100 MB before dispatching.
     */
    fun sendTextMessage(chatId: String, senderId: String, receiverId: String, text: String) {
        if (text.isBlank() || chatId.isBlank() || senderId.isBlank()) return

        val textBytes = text.toByteArray(Charsets.UTF_8).size
        if (textBytes > MAX_BYTE_SIZE_100_MB) {
            Log.e("ChatViewModel", "Text payload exceeds 100 MB: $textBytes bytes")
            _toastEvent.value = "Text message exceeds maximum size of 100 MB"
            return
        }

        val tempId = UUID.randomUUID().toString()
        val tempMessage = Message(
            messageId = tempId,
            chatId = chatId,
            senderId = senderId,
            receiverId = receiverId,
            messageText = text.trim(),
            messageType = "TEXT",
            timestamp = System.currentTimeMillis(),
            status = "SENDING",
            messageStatus = MessageStatus.SENDING
        )

        addOptimisticMessage(tempMessage)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.saveFullMessage(tempMessage.copy(status = "SENT", messageStatus = MessageStatus.SENT))
                updateOptimisticMessageStatus(tempId, MessageStatus.SENT)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed background text message dispatch: ${e.message}", e)
                updateOptimisticMessageStatus(tempId, MessageStatus.FAILED)
            }
        }
    }

    /**
     * 2. IMAGE PICKER BATCH LIMITATION (MAX 15 PICS):
     * Validates image batch count <= 15 before uploading.
     */
    fun sendImageBatch(chatId: String, senderId: String, receiverId: String, imageUris: List<Uri>) {
        if (chatId.isBlank() || senderId.isBlank() || imageUris.isEmpty()) return

        if (imageUris.size > MAX_IMAGE_BATCH_COUNT) {
            Log.e("ChatViewModel", "Attempted to send ${imageUris.size} images (Max 15 allowed)")
            _toastEvent.value = "Maximum 15 images allowed per batch"
            return
        }

        imageUris.forEach { uri ->
            sendSingleImageMessage(chatId, senderId, receiverId, uri)
        }
    }

    fun sendSingleImageMessage(chatId: String, senderId: String, receiverId: String, imageUri: Uri) {
        if (chatId.isBlank() || senderId.isBlank()) return

        val tempId = UUID.randomUUID().toString()
        val tempMessage = Message(
            messageId = tempId,
            chatId = chatId,
            senderId = senderId,
            receiverId = receiverId,
            messageText = "📷 Photo",
            messageType = "IMAGE",
            localUri = imageUri.toString(),
            mediaUrl = imageUri.toString(),
            timestamp = System.currentTimeMillis(),
            status = "SENDING",
            messageStatus = MessageStatus.SENDING,
            uploadProgress = 0
        )

        addOptimisticMessage(tempMessage)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val downloadUrl = repository.uploadChatImage(
                    chatId = chatId,
                    messageId = tempId,
                    fileUri = imageUri,
                    onProgress = { progress ->
                        updateOptimisticMessageProgress(tempId, progress)
                    }
                )

                val finalMessage = tempMessage.copy(
                    mediaUrl = downloadUrl,
                    status = "SENT",
                    messageStatus = MessageStatus.SENT,
                    uploadProgress = 100
                )

                repository.saveFullMessage(finalMessage)
                replaceOptimisticMessage(tempId, finalMessage)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed background image upload: ${e.message}", e)
                updateOptimisticMessageStatus(tempId, MessageStatus.FAILED)
                withContext(Dispatchers.Main) {
                    _toastEvent.value = "Failed to upload image to Catbox. Please try again."
                }
            }
        }
    }

    /**
     * 3. FILE SHARING RESTRICTION (MAX 100 MB):
     * Validates file size before upload.
     */
    fun sendFileMessage(
        chatId: String,
        senderId: String,
        receiverId: String,
        fileUri: Uri,
        fileName: String,
        fileSize: Long
    ) {
        if (chatId.isBlank() || senderId.isBlank()) return

        if (fileSize > MAX_BYTE_SIZE_100_MB) {
            Log.e("ChatViewModel", "File size $fileSize exceeds 100 MB limit")
            _toastEvent.value = "File size exceeds 100 MB limit."
            return
        }

        val tempId = UUID.randomUUID().toString()
        val tempMessage = Message(
            messageId = tempId,
            chatId = chatId,
            senderId = senderId,
            receiverId = receiverId,
            messageText = "📁 $fileName",
            messageType = "FILE",
            localUri = fileUri.toString(),
            mediaUrl = fileUri.toString(),
            timestamp = System.currentTimeMillis(),
            status = "SENDING",
            messageStatus = MessageStatus.SENDING,
            uploadProgress = 0
        )

        addOptimisticMessage(tempMessage)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val downloadUrl = repository.uploadChatImage(
                    chatId = chatId,
                    messageId = tempId,
                    fileUri = fileUri,
                    onProgress = { progress ->
                        updateOptimisticMessageProgress(tempId, progress)
                    }
                )

                val finalMessage = tempMessage.copy(
                    mediaUrl = downloadUrl,
                    status = "SENT",
                    messageStatus = MessageStatus.SENT,
                    uploadProgress = 100
                )

                repository.saveFullMessage(finalMessage)
                replaceOptimisticMessage(tempId, finalMessage)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed background file upload: ${e.message}", e)
                updateOptimisticMessageStatus(tempId, MessageStatus.FAILED)
            }
        }
    }

    /**
     * 4. VOICE NOTE DISPATCH & VALIDATION (100 MB MAX):
     */
    fun sendVoiceNoteMessage(chatId: String, senderId: String, receiverId: String, voiceUri: Uri, fileSize: Long = 0L) {
        if (chatId.isBlank() || senderId.isBlank()) return

        if (fileSize > MAX_BYTE_SIZE_100_MB) {
            Log.e("ChatViewModel", "Voice note size $fileSize exceeds 100 MB limit")
            _toastEvent.value = "Voice note exceeds 100 MB limit"
            return
        }

        val tempId = UUID.randomUUID().toString()
        val tempMessage = Message(
            messageId = tempId,
            chatId = chatId,
            senderId = senderId,
            receiverId = receiverId,
            messageText = "🎤 Voice Note",
            messageType = "VOICE",
            localUri = voiceUri.toString(),
            mediaUrl = voiceUri.toString(),
            timestamp = System.currentTimeMillis(),
            status = "SENDING",
            messageStatus = MessageStatus.SENDING,
            uploadProgress = 0
        )

        addOptimisticMessage(tempMessage)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val downloadUrl = repository.uploadVoiceNote(
                    chatId = chatId,
                    messageId = tempId,
                    fileUri = voiceUri,
                    onProgress = { progress ->
                        updateOptimisticMessageProgress(tempId, progress)
                    }
                )

                val finalMessage = tempMessage.copy(
                    mediaUrl = downloadUrl,
                    status = "SENT",
                    messageStatus = MessageStatus.SENT,
                    uploadProgress = 100
                )

                repository.saveFullMessage(finalMessage)
                replaceOptimisticMessage(tempId, finalMessage)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed background voice note upload: ${e.message}", e)
                updateOptimisticMessageStatus(tempId, MessageStatus.FAILED)
            }
        }
    }

    /**
     * RETRY LOGIC FOR FAILED MESSAGES
     */
    fun retryFailedMessage(message: Message) {
        if (message.messageId.isBlank()) return
        updateOptimisticMessageStatus(message.messageId, MessageStatus.SENDING)

        when (message.messageType.uppercase()) {
            "IMAGE" -> {
                val uriStr = message.localUri ?: message.mediaUrl
                if (uriStr.isNotBlank()) {
                    sendSingleImageMessage(message.chatId, message.senderId, message.receiverId, Uri.parse(uriStr))
                }
            }
            "VOICE", "AUDIO" -> {
                val uriStr = message.localUri ?: message.mediaUrl
                if (uriStr.isNotBlank()) {
                    sendVoiceNoteMessage(message.chatId, message.senderId, message.receiverId, Uri.parse(uriStr))
                }
            }
            else -> {
                sendTextMessage(message.chatId, message.senderId, message.receiverId, message.messageText)
            }
        }
    }

    private fun addOptimisticMessage(message: Message) {
        _optimisticMessages.update { list -> list + message }
    }

    private fun updateOptimisticMessageStatus(id: String, status: MessageStatus) {
        _optimisticMessages.update { list ->
            list.map { if (it.messageId == id) it.copy(status = status.name, messageStatus = status) else it }
        }
    }

    private fun updateOptimisticMessageProgress(id: String, progress: Int) {
        _optimisticMessages.update { list ->
            list.map { if (it.messageId == id) it.copy(uploadProgress = progress) else it }
        }
    }

    private fun replaceOptimisticMessage(id: String, newMsg: Message) {
        _optimisticMessages.update { list ->
            list.map { if (it.messageId == id) newMsg else it }
        }
    }

    private fun parseStatus(statusStr: String): MessageStatus {
        return try {
            MessageStatus.valueOf(statusStr.uppercase())
        } catch (e: Exception) {
            MessageStatus.SENT
        }
    }
}
