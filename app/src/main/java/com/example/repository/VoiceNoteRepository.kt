package com.example.repository

import android.content.Context
import android.util.Log
import com.example.network.CatboxApiService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class VoiceNoteRepository {

    private val catboxService = CatboxApiService.create()

    private val firebaseAuth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    suspend fun uploadAndSendVoiceNote(context: Context, audioFile: File, chatId: String, receiverId: String): String = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            throw Exception("Audio recording file is empty or does not exist.")
        }

        Log.d("VoiceNoteRepository", "Uploading voice note of size: ${audioFile.length()} bytes to Catbox...")

        // 1. Upload to Catbox
        val directUrl = com.example.network.CatboxStorageManager.uploadVoiceNote(audioFile)

        if (directUrl.isBlank()) {
            throw Exception("Voice note upload succeeded, but returned an empty URL response.")
        }

        Log.d("VoiceNoteRepository", "Voice note upload successful! Direct URL: $directUrl")

        // 2. Insert message document into Firestore top-level messages collection
        try {
            val currentUid = firebaseAuth.currentUser?.uid ?: ""
            if (currentUid.isEmpty()) {
                throw Exception("Failed to sync data: Not authenticated.")
            }

            Log.d("VoiceNoteRepository", "Inserting voice note message for user: $currentUid into Firestore")

            val messageId = java.util.UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val messageData = mapOf(
                "messageId" to messageId,
                "chatId" to chatId,
                "senderId" to currentUid,
                "receiverId" to receiverId,
                "messageText" to directUrl,
                "messageType" to "VOICE",
                "mediaUrl" to directUrl,
                "timestamp" to now,
                "status" to "SENT"
            )

            firestore.collection("messages").document(messageId).set(messageData).await()
            Log.d("VoiceNoteRepository", "Firestore messages document inserted successfully.")

            val chatRoomUpdate = mapOf(
                "chatId" to chatId,
                "lastMessage" to "🎤 Voice Note",
                "lastMessageTimestamp" to now
            )
            firestore.collection("chats").document(chatId)
                .set(chatRoomUpdate, com.google.firebase.firestore.SetOptions.merge())
                .await()

        } catch (dbEx: Exception) {
            Log.e("VoiceNoteRepository", "Firestore database insertion failed: ${dbEx.message}", dbEx)
            throw Exception("Failed to sync data: ${dbEx.message}")
        } finally {
            if (audioFile.exists()) {
                audioFile.delete()
                Log.d("VoiceNoteRepository", "Temporary audio cache file deleted successfully.")
            }
        }

        return@withContext directUrl
    }
}
