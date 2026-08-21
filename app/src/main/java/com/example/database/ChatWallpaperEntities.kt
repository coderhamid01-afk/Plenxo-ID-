package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_wallpapers")
data class ChatWallpaperEntity(
    @PrimaryKey val wallpaperId: String,
    val category: String, // SAD, ROMANTIC, AESTHETIC, MINIMALIST
    val cloudUrl: String, // Secure public CDN endpoint reference (Cloudinary or structured remote location)
    val thumbnailCloudUrl: String, // Downscaled asset URL for lightning-fast gallery previews
    val localFilePath: String? = null, // Populated only when the asset is physically downloaded on the disk storage
    val isDownloaded: Boolean = false,
    val fileSizeInBytes: Long
)

@Entity(tableName = "conversation_wallpaper_mappings")
data class ConversationWallpaperMappingEntity(
    @PrimaryKey val conversationId: String, // Unique Chat Reference Thread ID
    val activeWallpaperId: String?, // References chat_wallpapers.Id. If null, the global default app theme backdrop handles rendering
    val backgroundOpacity: Float = 1.0f // Slider multiplier scaling value ranging continuously between 0.1f and 1.0f
)
