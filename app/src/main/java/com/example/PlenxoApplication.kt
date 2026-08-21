package com.example

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.database.DatabaseCompactionWorker
import java.util.concurrent.TimeUnit
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.util.AppLockObserver

class PlenxoApplication : Application(), Application.ActivityLifecycleCallbacks {

    companion object {
        lateinit var instance: PlenxoApplication
            private set
    }

    class GlobalCrashHandler(private val context: android.content.Context) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            try {
                val logString = android.util.Log.getStackTraceString(throwable)
                val sharedPrefs = context.getSharedPreferences("app_crash_logs", android.content.Context.MODE_PRIVATE)
                sharedPrefs.edit()
                    .putString("last_crash_log", logString)
                    .putLong("last_crash_timestamp", System.currentTimeMillis())
                    .commit()

                android.util.Log.e("GlobalCrashHandler", "CRASH CAUGHT IN GLOBAL HANDLER: ", throwable)

                // Safe, clean restart
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("GLOBAL_CRASH_RESTART", true)
                }
                if (intent != null) {
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("GlobalCrashHandler", "Failed inside exception handler", e)
            } finally {
                android.os.Process.killProcess(android.os.Process.myPid())
                java.lang.System.exit(10)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            Thread.setDefaultUncaughtExceptionHandler(GlobalCrashHandler(this))
        } catch (t: Throwable) {
            android.util.Log.e("APP_INIT_ERROR", "Failed to register Global Crash Handler safely", t)
        }
        try {
            registerActivityLifecycleCallbacks(this)
        } catch (t: Throwable) {
            android.util.Log.e("APP_INIT_ERROR", "Failed to register activity lifecycle callbacks safely", t)
        }
        // Firebase Auth and Firestore are automatically initialized by Google Services plugin / FirebaseInitProvider
        try {
            val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(com.google.firebase.firestore.PersistentCacheSettings.newBuilder().build())
                .build()
            com.google.firebase.firestore.FirebaseFirestore.getInstance().firestoreSettings = settings
            android.util.Log.d("PlenxoInit", "Firestore offline persistence enabled successfully")
        } catch (t: Throwable) {
            android.util.Log.e("PlenxoInit", "Failed to enable Firestore offline persistence", t)
        }
        android.util.Log.d("PlenxoInit", "PlenxoApplication initialized successfully")
        try {
            com.example.service.AppNotificationService.createNotificationChannel(this)
        } catch (t: Throwable) {
            android.util.Log.e("APP_INIT_ERROR", "Failed to create notification channel safely", t)
        }
        try {
            scheduleDatabaseCompaction()
        } catch (t: Throwable) {
            android.util.Log.e("APP_INIT_ERROR", "Failed to schedule background workers safely", t)
        }
        try {
            registerAppLockObserver()
        } catch (t: Throwable) {
            android.util.Log.e("APP_INIT_ERROR", "Failed to register app lock observer safely", t)
        }
        try {
            val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(this)
            if (resultCode == com.google.android.gms.common.ConnectionResult.SUCCESS) {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    try {
                        if (task.isSuccessful) {
                            val token = task.result
                            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                            if (!uid.isNullOrEmpty() && !token.isNullOrEmpty()) {
                                com.example.service.PlenxoFCMService.updateFcmTokenInDatabase(uid, token)
                            }
                        }
                    } catch (t: Throwable) {
                        Log.w("PlenxoApplication", "Error processing FCM token: ${t.message}")
                    }
                }.addOnFailureListener { ex ->
                    Log.w("PlenxoApplication", "FCM token request failed: ${ex.message}")
                }
            } else {
                Log.w("PlenxoApplication", "Google Play Services unavailable on device (code $resultCode). FCM disabled.")
            }
        } catch (t: Throwable) {
            Log.e("PlenxoApplication", "Failed to fetch FCM token safely: ${t.message}")
        }
    }

    private fun registerAppLockObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLockObserver(this))
        ProcessLifecycleOwner.get().lifecycle.addObserver(com.example.util.PresenceObserver(this))
    }

    // ActivityLifecycleCallbacks implementation for screenshot restriction
    override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {
        applyScreenshotRestriction(activity)
    }

    override fun onActivityStarted(activity: android.app.Activity) {
        applyScreenshotRestriction(activity)
    }

    override fun onActivityResumed(activity: android.app.Activity) {}
    override fun onActivityPaused(activity: android.app.Activity) {}
    override fun onActivityStopped(activity: android.app.Activity) {}
    override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
    override fun onActivityDestroyed(activity: android.app.Activity) {}

    private fun applyScreenshotRestriction(activity: android.app.Activity) {
        val blockScreenshots = com.example.util.SessionManager.isScreenshotsBlocked(this)
        
        if (blockScreenshots) {
            activity.window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun scheduleDatabaseCompaction() {
        try {
            val compactionWorkRequest = PeriodicWorkRequestBuilder<DatabaseCompactionWorker>(
                7, TimeUnit.DAYS
            ).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "DatabaseCompactionWork",
                ExistingPeriodicWorkPolicy.KEEP,
                compactionWorkRequest
            )
        } catch (e: Exception) {
            android.util.Log.e("PlenxoApplication", "Failed to schedule DB compaction", e)
        }
    }
}
