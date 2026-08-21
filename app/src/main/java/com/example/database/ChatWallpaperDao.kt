package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatWallpaperDao {
    @Query("SELECT * FROM chat_wallpapers")
    fun getAllWallpapers(): Flow<List<ChatWallpaperEntity>>

    @Query("SELECT * FROM chat_wallpapers WHERE category = :category")
    fun getWallpapersByCategory(category: String): Flow<List<ChatWallpaperEntity>>

    @Query("SELECT * FROM chat_wallpapers WHERE wallpaperId = :wallpaperId LIMIT 1")
    suspend fun getWallpaperById(wallpaperId: String): ChatWallpaperEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallpaper(wallpaper: ChatWallpaperEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallpapers(wallpapers: List<ChatWallpaperEntity>)

    @Query("SELECT * FROM conversation_wallpaper_mappings WHERE conversationId = :conversationId LIMIT 1")
    fun getWallpaperMappingForConversation(conversationId: String): Flow<ConversationWallpaperMappingEntity?>

    @Query("SELECT * FROM conversation_wallpaper_mappings WHERE conversationId = :conversationId LIMIT 1")
    suspend fun getWallpaperMappingForConversationDirect(conversationId: String): ConversationWallpaperMappingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallpaperMapping(mapping: ConversationWallpaperMappingEntity)

    @Query("DELETE FROM conversation_wallpaper_mappings WHERE conversationId = :conversationId")
    suspend fun deleteWallpaperMapping(conversationId: String)
}
