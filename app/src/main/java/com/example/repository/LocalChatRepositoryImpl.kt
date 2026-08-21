package com.example.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.database.AppDatabase
import com.example.model.LocalMessage
import com.example.model.MessagePayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class LocalChatRepositoryImpl(private val context: Context) : ChatDataRepository {
    private val database = AppDatabase.getDatabase(context)
    private val localMessageDao = database.localMessageDao()

    override suspend fun saveMessage(message: MessagePayload) {
        val localMsg = LocalMessage(
            messageId = message.messageId,
            chatId = message.chatId,
            senderId = message.senderId,
            receiverId = message.receiverId,
            messageText = message.messageText,
            timestamp = message.timestamp,
            status = message.status,
            messageType = message.messageType,
            replyToMessageId = message.replyToMessageId,
            isEdited = message.isEdited,
            expiresAt = message.expiresAt,
            originalContentHistoryJson = "[]",
            senderActiveFontId = message.senderActiveFontId
        )
        localMessageDao.insertMessage(localMsg)
    }

    override suspend fun uploadMediaAsset(fileUri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(fileUri) ?: return@withContext fileUri.toString()
            val localFile = File(context.filesDir, "media_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(localFile)

            val bytes = inputStream.readBytes()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            } else {
                outputStream.write(bytes)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            "file://${localFile.absolutePath}"
        } catch (e: Exception) {
            android.util.Log.e("LocalChatRepo", "Failed to copy media asset locally", e)
            fileUri.toString()
        }
    }

    override fun streamMessages(chatId: String, limit: Int, offset: Int): Flow<List<MessagePayload>> {
        return localMessageDao.getMessagesForChatPaginated(chatId, limit, offset).map { localMessages ->
            localMessages.map { localMsg ->
                MessagePayload(
                    messageId = localMsg.messageId,
                    chatId = localMsg.chatId,
                    senderId = localMsg.senderId,
                    receiverId = localMsg.receiverId,
                    messageText = localMsg.messageText,
                    timestamp = localMsg.timestamp,
                    status = localMsg.status,
                    messageType = localMsg.messageType,
                    replyToMessageId = localMsg.replyToMessageId,
                    isEdited = localMsg.isEdited,
                    expiresAt = localMsg.expiresAt,
                    senderActiveFontId = localMsg.senderActiveFontId
                )
            }
        }
    }

    override suspend fun getMoreMessages(chatId: String, limit: Int, offset: Int): List<MessagePayload> {
        return localMessageDao.getMessagesForChatStatic(chatId, limit, offset).map { localMsg ->
            MessagePayload(
                messageId = localMsg.messageId,
                chatId = localMsg.chatId,
                senderId = localMsg.senderId,
                receiverId = localMsg.receiverId,
                messageText = localMsg.messageText,
                timestamp = localMsg.timestamp,
                status = localMsg.status,
                messageType = localMsg.messageType,
                replyToMessageId = localMsg.replyToMessageId,
                isEdited = localMsg.isEdited,
                expiresAt = localMsg.expiresAt,
                senderActiveFontId = localMsg.senderActiveFontId
            )
        }
    }
}
