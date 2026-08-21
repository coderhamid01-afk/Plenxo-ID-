package com.example.util

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

import androidx.lifecycle.lifecycleScope

class PresenceObserver(private val context: Context) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        PresenceManager.setOnline(owner.lifecycleScope)
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        PresenceManager.setOnline(owner.lifecycleScope)
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        PresenceManager.setOffline(owner.lifecycleScope)
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        PresenceManager.setOffline(owner.lifecycleScope)
    }

    private fun updatePresenceState(owner: LifecycleOwner, state: String) {
        // Kept for backward compatibility if any callers existed, but delegating to PresenceManager
        if (state == "online") {
            PresenceManager.setOnline(owner.lifecycleScope)
        } else {
            PresenceManager.setOffline(owner.lifecycleScope)
        }
    }
}
