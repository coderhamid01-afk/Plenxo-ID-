package com.example.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class PermissionManager(private val activity: ComponentActivity) {

    private var onPermissionsResult: ((Map<String, Boolean>) -> Unit)? = null

    private val multiplePermissionsLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            onPermissionsResult?.invoke(results)
        }

    fun requestPermissions(permissions: List<String>, onResult: (Map<String, Boolean>) -> Unit) {
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            onResult(permissions.associateWith { true })
        } else {
            onPermissionsResult = onResult
            try {
                if (activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) {
                    multiplePermissionsLauncher.launch(permissions.toTypedArray())
                } else {
                    onResult(permissions.associateWith { false })
                }
            } catch (e: Exception) {
                android.util.Log.e("PermissionManager", "Failed to launch permissions request", e)
                onResult(permissions.associateWith { false })
            }
        }
    }

    fun requestSinglePermission(permission: String, onResult: (Boolean) -> Unit) {
        requestPermissions(listOf(permission)) { results ->
            onResult(results[permission] ?: false)
        }
    }

    fun requestNotificationPermission(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestSinglePermission(Manifest.permission.POST_NOTIFICATIONS, onResult)
        } else {
            onResult(true)
        }
    }

    fun requestMicrophonePermission(onResult: (Boolean) -> Unit) {
        requestSinglePermission(Manifest.permission.RECORD_AUDIO, onResult)
    }

    fun requestCameraPermission(onResult: (Boolean) -> Unit) {
        requestSinglePermission(Manifest.permission.CAMERA, onResult)
    }

    fun requestMediaPermission(onResult: (Boolean) -> Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        requestSinglePermission(permission, onResult)
    }

    fun openSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", activity.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("PermissionManager", "Failed to launch application settings", e)
        }
    }
}
