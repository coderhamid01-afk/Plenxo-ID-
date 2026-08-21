package com.example.util

import android.content.Context
import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.ui.UnlockActivity

class AppLockObserver(private val context: Context) : DefaultLifecycleObserver {

    companion object {
        var isLockScreenShowing: Boolean = false
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        if (isLockScreenShowing) return
        
        if (AppLockManager.isLocked(context)) {
            isLockScreenShowing = true
            val intent = Intent(context, UnlockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        if (AppLockManager.isAppLockEnabled(context)) {
            AppLockManager.setLocked(context, true)
        }
    }
}
