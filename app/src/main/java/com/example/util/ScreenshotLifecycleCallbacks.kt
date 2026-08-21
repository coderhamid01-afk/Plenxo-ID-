package com.example.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import android.content.Context

class ScreenshotLifecycleCallbacks(private val context: Context) : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        applyScreenshotRestriction(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        applyScreenshotRestriction(activity)
    }

    private fun applyScreenshotRestriction(activity: Activity) {
        val blockScreenshots = SessionManager.isScreenshotsBlocked(context)
        
        if (blockScreenshots) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
