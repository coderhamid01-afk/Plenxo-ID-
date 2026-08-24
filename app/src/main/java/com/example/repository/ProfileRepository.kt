@file:Suppress("DEPRECATION")
package com.example.repository

import android.util.Log
import com.example.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ProfileRepository {

    private val firebaseAuth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    fun getUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        val currentUid = firebaseAuth.currentUser?.uid ?: ""
        val targetId = if (userId.isBlank()) currentUid else userId

        if (targetId.isEmpty()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        val docRef = firestore.collection("users").document(targetId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(null)
                return@addSnapshotListener
            }

            val data = snapshot.data ?: emptyMap()
            val profile = UserProfile(
                id = snapshot.id,
                email = (data["email"] as? String) ?: "",
                displayName = (data["displayName"] as? String) ?: "",
                bio = (data["statusMessage"] as? String) ?: (data["bio"] as? String) ?: "",
                profilePicUrl = (data["profilePicUrl"] as? String) ?: (data["avatar_url"] as? String) ?: "",
                selectedRingId = (data["selectedRingId"] as? String) ?: ""
            )
            trySend(profile)
        }

        awaitClose {
            listener.remove()
        }
    }

    suspend fun updateProfile(userId: String, updates: Map<String, Any>) {
        val currentUid = firebaseAuth.currentUser?.uid ?: ""
        if (currentUid.isEmpty()) throw Exception("Not authenticated in Firebase Auth")
        val targetId = if (userId.isBlank()) currentUid else userId

        try {
            firestore.collection("users").document(targetId)
                .set(updates, SetOptions.merge())
                .await()
            Log.d("ProfileRepository", "Profile updated in Firestore for $targetId")
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Failed to update profile: ${e.message}", e)
            throw e
        }
    }
}
