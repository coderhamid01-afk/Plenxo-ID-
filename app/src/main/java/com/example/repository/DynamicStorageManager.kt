package com.example.repository

import android.net.Uri
import com.example.model.MessagePayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class DynamicStorageManager(
    private val localRepo: LocalChatRepositoryImpl,
    private val cloudRepo: CloudChatRepositoryImpl,
    private val localStorageOnlyFlow: Flow<Boolean>
) : ChatDataRepository {

    override suspend fun saveMessage(message: MessagePayload) {
        val isLocal = localStorageOnlyFlow.first()
        if (isLocal) {
            localRepo.saveMessage(message)
        } else {
            try {
                cloudRepo.saveMessage(message)
            } catch (e: Exception) {
                android.util.Log.w("DynamicStorageManager", "Cloud save note: ${e.message}")
            }
            localRepo.saveMessage(message)
        }
    }

    override suspend fun uploadMediaAsset(fileUri: Uri): String {
        val isLocal = localStorageOnlyFlow.first()
        return if (isLocal) {
            localRepo.uploadMediaAsset(fileUri)
        } else {
            cloudRepo.uploadMediaAsset(fileUri)
        }
    }

    override fun streamMessages(chatId: String, limit: Int, offset: Int): Flow<List<MessagePayload>> {
        return localStorageOnlyFlow.flatMapLatest { isLocal ->
            if (isLocal) {
                localRepo.streamMessages(chatId, limit, offset)
            } else {
                cloudRepo.streamMessages(chatId, limit, offset)
            }
        }
    }

    override suspend fun getMoreMessages(chatId: String, limit: Int, offset: Int): List<MessagePayload> {
        val isLocal = localStorageOnlyFlow.first()
        return if (isLocal) {
            localRepo.getMoreMessages(chatId, limit, offset)
        } else {
            cloudRepo.getMoreMessages(chatId, limit, offset)
        }
    }
}
