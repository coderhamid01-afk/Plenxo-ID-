package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val changelog: String,
    val playStoreUrl: String,
    val apkPureUrl: String
)

object UpdateManager {
    private const val TAG = "UpdateManager"

    suspend fun fetchUpdateInfo(context: Context): UpdateInfo {
        val currentVersionCode = com.example.BuildConfig.VERSION_CODE
        val packageName = context.packageName

        var updateInfo = UpdateInfo(
            latestVersionCode = currentVersionCode,
            latestVersionName = com.example.BuildConfig.VERSION_NAME,
            changelog = "Bug fixes and performance improvements.",
            playStoreUrl = "https://play.google.com/store/apps/details?id=$packageName",
            apkPureUrl = "https://apkpure.com/p/$packageName"
        )

        try {
            val firestore = FirebaseFirestore.getInstance()
            val doc = firestore.collection("app_config").document("update").get().await()
            if (doc.exists()) {
                val code = (doc.getLong("latestVersionCode") ?: currentVersionCode.toLong()).toInt()
                val name = doc.getString("latestVersionName") ?: com.example.BuildConfig.VERSION_NAME
                val changes = doc.getString("changelog") ?: "Performance enhancements and bug fixes."
                val pUrl = doc.getString("playStoreUrl") ?: "https://play.google.com/store/apps/details?id=$packageName"
                val aUrl = doc.getString("apkPureUrl") ?: "https://apkpure.com/p/$packageName"

                updateInfo = UpdateInfo(
                    latestVersionCode = code,
                    latestVersionName = name,
                    changelog = changes,
                    playStoreUrl = pUrl,
                    apkPureUrl = aUrl
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch remote update config, using current version", e)
        }

        return updateInfo
    }

    fun openPlayStore(context: Context, playStoreUrl: String) {
        val packageName = context.packageName
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(playStoreUrl))
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(webIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to open Play Store", ex)
            }
        }
    }

    fun openApkPure(context: Context, apkPureUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkPureUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open APKPure", e)
        }
    }
}
