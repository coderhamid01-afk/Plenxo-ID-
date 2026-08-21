package com.example.util

import android.app.Activity
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log

class ScreenshotDetector(
    private val activity: Activity,
    private val onScreenshotDetected: () -> Unit
) {

    private var screenCaptureCallback: Any? = null
    private var contentObserver: ContentObserver? = null

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            screenCaptureCallback = Activity.ScreenCaptureCallback {
                onScreenshotDetected()
            }
            activity.registerScreenCaptureCallback(
                activity.mainExecutor,
                screenCaptureCallback as Activity.ScreenCaptureCallback
            )
            Log.d("ScreenshotDetector", "Started API 34+ ScreenCaptureCallback")
        } else {
            contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    if (uri != null && uri.toString().contains(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())) {
                        Log.d("ScreenshotDetector", "Detected screenshot via ContentObserver")
                        onScreenshotDetected()
                    }
                }
            }
            contentObserver?.let { observer ->
                activity.contentResolver.registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true,
                    observer
                )
            }
            Log.d("ScreenshotDetector", "Started ContentObserver fallback")
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            screenCaptureCallback?.let {
                activity.unregisterScreenCaptureCallback(it as Activity.ScreenCaptureCallback)
            }
        } else {
            contentObserver?.let {
                activity.contentResolver.unregisterContentObserver(it)
            }
        }
    }
}
