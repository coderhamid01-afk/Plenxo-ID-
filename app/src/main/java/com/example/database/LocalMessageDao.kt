package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.LocalMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalMessageDao {
    @Query("SELECT * FROM local_messages WHERE chatId = :chatId ORDER BY isPinned DESC, timestamp ASC LIMIT :limit OFFSET :offset")
    fun getMessagesForChatPaginated(chatId: String, limit: Int, offset: Int): Flow<List<LocalMessage>>

    @Query("SELECT * FROM local_messages WHERE chatId = :chatId ORDER BY isPinned DESC, timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesForChatStatic(chatId: String, limit: Int, offset: Int): List<LocalMessage>

    @Query("SELECT * FROM local_messages WHERE status = 'SENDING' ORDER BY timestamp ASC")
    suspend fun getPendingSendingMessages(): List<LocalMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: LocalMessage)

    @Update
    suspend fun updateMessage(message: LocalMessage)

    @Query("UPDATE local_messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("DELETE FROM local_messages WHERE messageId = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM local_messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: String)

    @Query("DELETE FROM local_messages WHERE expiresAt IS NOT NULL AND expiresAt <= :currentTime")
    suspend fun deleteExpiredMessages(currentTime: Long): Int

    @Query("UPDATE local_messages SET status = 'READ' WHERE chatId = :chatId AND receiverId = :receiverId")
    suspend fun markChatMessagesAsRead(chatId: String, receiverId: String)

    @Query("SELECT * FROM local_messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): LocalMessage?
}
