package com.example.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

object PresenceManager {
    private const val TAG = "PresenceManager"

    fun setOnline(scope: kotlinx.coroutines.CoroutineScope) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                FirebaseFirestore.getInstance().collection("presence").document(uid).set(
                    mapOf(
                        "user_id" to uid,
                        "state" to "online",
                        "last_seen" to System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "User $uid is now online via Firestore Presence")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set status to online", e)
            }
        }
    }

    fun setOffline(scope: kotlinx.coroutines.CoroutineScope) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                FirebaseFirestore.getInstance().collection("presence").document(uid).set(
                    mapOf(
                        "user_id" to uid,
                        "state" to "offline",
                        "last_seen" to System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "User $uid is now offline via Firestore Presence")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set status to offline", e)
            }
        }
    }
}
