package com.example.repository

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.database.AppDatabase
import com.example.database.ChatWallpaperDao
import com.example.database.ChatWallpaperEntity
import com.example.database.ConversationWallpaperMappingEntity
import com.example.database.WallpaperDownloadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File

class ChatWallpaperRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val dao: ChatWallpaperDao = database.chatWallpaperDao()

    val allWallpapers: Flow<List<ChatWallpaperEntity>> = dao.getAllWallpapers()

    fun getWallpapersByCategory(category: String): Flow<List<ChatWallpaperEntity>> {
        return dao.getWallpapersByCategory(category)
    }

    fun getWallpaperMappingForConversation(conversationId: String): Flow<ConversationWallpaperMappingEntity?> {
        return dao.getWallpaperMappingForConversation(conversationId)
    }

    suspend fun getWallpaperMappingForConversationDirect(conversationId: String): ConversationWallpaperMappingEntity? {
        return dao.getWallpaperMappingForConversationDirect(conversationId)
    }

    suspend fun insertWallpaperMapping(mapping: ConversationWallpaperMappingEntity) {
        dao.insertWallpaperMapping(mapping)
    }

    suspend fun deleteWallpaperMapping(conversationId: String) {
        dao.deleteWallpaperMapping(conversationId)
    }

    suspend fun getWallpaperById(id: String): ChatWallpaperEntity? {
        return dao.getWallpaperById(id)
    }

    fun downloadWallpaper(wallpaperId: String, cloudUrl: String) {
        val data = Data.Builder()
            .putString("WALLPAPER_ID_INPUT", wallpaperId)
            .putString("CLOUD_URL_INPUT", cloudUrl)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<WallpaperDownloadWorker>()
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueue(downloadRequest)
    }

    fun getLocalWallpaperFile(wallpaperId: String): File {
        return File(File(context.filesDir, "wallpapers"), "${wallpaperId}.4k")
    }

    suspend fun seedWallpapersIfNeeded() {
        val current = dao.getAllWallpapers().first()
        if (current.isEmpty()) {
            val list = listOf(
                // SAD
                ChatWallpaperEntity("sad_1", "SAD", "https://images.unsplash.com/photo-1518199266791-5375a83190b7?q=80&w=1200", "https://images.unsplash.com/photo-1518199266791-5375a83190b7?q=80&w=300", fileSizeInBytes = 250000),
                ChatWallpaperEntity("sad_2", "SAD", "https://images.unsplash.com/photo-1485206412400-93e0b3a188aa?q=80&w=1200", "https://images.unsplash.com/photo-1485206412400-93e0b3a188aa?q=80&w=300", fileSizeInBytes = 280000),
                ChatWallpaperEntity("sad_3", "SAD", "https://images.unsplash.com/photo-1516339901601-2e1d62dc0c45?q=80&w=1200", "https://images.unsplash.com/photo-1516339901601-2e1d62dc0c45?q=80&w=300", fileSizeInBytes = 310000),
                ChatWallpaperEntity("sad_4", "SAD", "https://images.unsplash.com/photo-1494548162494-384bba4ab999?q=80&w=1200", "https://images.unsplash.com/photo-1494548162494-384bba4ab999?q=80&w=300", fileSizeInBytes = 240000),
                ChatWallpaperEntity("sad_5", "SAD", "https://images.unsplash.com/photo-1514897575457-c4db96724744?q=80&w=1200", "https://images.unsplash.com/photo-1514897575457-c4db96724744?q=80&w=300", fileSizeInBytes = 260000),

                // ROMANTIC
                ChatWallpaperEntity("romantic_1", "ROMANTIC", "https://images.unsplash.com/photo-1518599904199-0ca897819ddb?q=80&w=1200", "https://images.unsplash.com/photo-1518599904199-0ca897819ddb?q=80&w=300", fileSizeInBytes = 230000),
                ChatWallpaperEntity("romantic_2", "ROMANTIC", "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?q=80&w=1200", "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?q=80&w=300", fileSizeInBytes = 270000),
                ChatWallpaperEntity("romantic_3", "ROMANTIC", "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?q=80&w=1200", "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?q=80&w=300", fileSizeInBytes = 290000),
                ChatWallpaperEntity("romantic_4", "ROMANTIC", "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?q=80&w=1200", "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?q=80&w=300", fileSizeInBytes = 220000),
                ChatWallpaperEntity("romantic_5", "ROMANTIC", "https://images.unsplash.com/photo-1518156677180-95a2893f3e9f?q=80&w=1200", "https://images.unsplash.com/photo-1518156677180-95a2893f3e9f?q=80&w=300", fileSizeInBytes = 250000),

                // AESTHETIC
                ChatWallpaperEntity("aesthetic_1", "AESTHETIC", "https://images.unsplash.com/photo-1507608869274-d3177c8bb4c7?q=80&w=1200", "https://images.unsplash.com/photo-1507608869274-d3177c8bb4c7?q=80&w=300", fileSizeInBytes = 320000),
                ChatWallpaperEntity("aesthetic_2", "AESTHETIC", "https://images.unsplash.com/photo-1508739773434-c26b3d09e071?q=80&w=1200", "https://images.unsplash.com/photo-1508739773434-c26b3d09e071?q=80&w=300", fileSizeInBytes = 300000),
                ChatWallpaperEntity("aesthetic_3", "AESTHETIC", "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?q=80&w=1200", "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?q=80&w=300", fileSizeInBytes = 290000),
                ChatWallpaperEntity("aesthetic_4", "AESTHETIC", "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=1200", "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=300", fileSizeInBytes = 350000),
                ChatWallpaperEntity("aesthetic_5", "AESTHETIC", "https://images.unsplash.com/photo-1528459801416-a9e53bbf4e17?q=80&w=1200", "https://images.unsplash.com/photo-1528459801416-a9e53bbf4e17?q=80&w=300", fileSizeInBytes = 280000),

                // MINIMALIST
                ChatWallpaperEntity("minimalist_1", "MINIMALIST", "https://images.unsplash.com/photo-1541701494587-cb58502866ab?q=80&w=1200", "https://images.unsplash.com/photo-1541701494587-cb58502866ab?q=80&w=300", fileSizeInBytes = 210000),
                ChatWallpaperEntity("minimalist_2", "MINIMALIST", "https://images.unsplash.com/photo-1618005198143-e5283b519a7f?q=80&w=1200", "https://images.unsplash.com/photo-1618005198143-e5283b519a7f?q=80&w=300", fileSizeInBytes = 240000),
                ChatWallpaperEntity("minimalist_3", "MINIMALIST", "https://images.unsplash.com/photo-1475924156734-496f6cac6ec1?q=80&w=1200", "https://images.unsplash.com/photo-1475924156734-496f6cac6ec1?q=80&w=300", fileSizeInBytes = 260000),
                ChatWallpaperEntity("minimalist_4", "MINIMALIST", "https://images.unsplash.com/photo-1506318137071-a8e063b4bec0?q=80&w=1200", "https://images.unsplash.com/photo-1506318137071-a8e063b4bec0?q=80&w=300", fileSizeInBytes = 200000),
                ChatWallpaperEntity("minimalist_5", "MINIMALIST", "https://images.unsplash.com/photo-1533158326339-7f3cf2404354?q=80&w=1200", "https://images.unsplash.com/photo-1533158326339-7f3cf2404354?q=80&w=300", fileSizeInBytes = 230000)
            )
            dao.insertWallpapers(list)
        }
    }
}
