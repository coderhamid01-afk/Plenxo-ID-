package com.example.repository

import android.net.Uri
import com.example.model.MessagePayload
import kotlinx.coroutines.flow.Flow

interface ChatDataRepository {
    suspend fun saveMessage(message: MessagePayload)
    suspend fun uploadMediaAsset(fileUri: Uri): String
    fun streamMessages(chatId: String, limit: Int = 50, offset: Int = 0): Flow<List<MessagePayload>>
    suspend fun getMoreMessages(chatId: String, limit: Int = 50, offset: Int): List<MessagePayload>
}
