package com.example.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ProfilePhotoPrivacyRepository {

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    suspend fun saveProfilePhotoVisibility(visibility: String): Boolean {
        val uid = currentUserId
        if (uid.isEmpty()) {
            Log.e("ProfilePhotoPrivacyRepo", "Cannot save profile photo visibility: user not authenticated")
            return false
        }

        return try {
            val normalizedVisibility = visibility.uppercase()
            val updates = mapOf(
                "profilePhotoVisibility" to normalizedVisibility,
                "profilePicVisibility" to normalizedVisibility,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("users").document(uid)
                .set(updates, SetOptions.merge())
                .await()

            Log.d("ProfilePhotoPrivacyRepo", "Profile photo visibility updated to: $normalizedVisibility for user $uid")
            true
        } catch (e: Exception) {
            Log.e("ProfilePhotoPrivacyRepo", "Failed to save profile photo visibility: ${e.message}", e)
            false
        }
    }

    fun getProfilePhotoVisibilityFlow(): Flow<String> = callbackFlow {
        val uid = currentUserId
        if (uid.isEmpty()) {
            trySend("EVERYONE")
            close()
            return@callbackFlow
        }

        val docRef = firestore.collection("users").document(uid)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("ProfilePhotoPrivacyRepo", "Error listening for profile photo visibility: ${error.message}")
                trySend("EVERYONE")
                return@addSnapshotListener
            }

            val visibility = snapshot?.getString("profilePhotoVisibility")
                ?: snapshot?.getString("profilePicVisibility")
                ?: "EVERYONE"

            trySend(visibility)
        }

        awaitClose {
            listener.remove()
        }
    }

    fun observeUserProfilePhoto(targetUserId: String): Flow<String?> = callbackFlow {
        if (targetUserId.isEmpty()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val docRef = firestore.collection("users").document(targetUserId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("ProfilePhotoPrivacyRepo", "Error observing target user profile photo: ${error.message}")
                trySend(null)
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists()) {
                trySend(null)
                return@addSnapshotListener
            }

            val visibility = (snapshot.getString("profilePhotoVisibility")
                ?: snapshot.getString("profilePicVisibility")
                ?: "EVERYONE").uppercase()

            val rawPhotoUrl = snapshot.getString("profilePicUrl")
                ?: snapshot.getString("avatar_url")
                ?: snapshot.getString("photoUrl")
                ?: snapshot.getString("profileUrl")
                ?: ""

            val canView = when (visibility) {
                "EVERYONE" -> true
                "NOBODY" -> false
                "CONTACTS", "MY_CONTACTS" -> true
                else -> true
            }

            if (!canView || rawPhotoUrl.isEmpty()) {
                trySend(null)
            } else {
                trySend(rawPhotoUrl)
            }
        }

        awaitClose {
            listener.remove()
        }
    }

    suspend fun fetchProfilePhotoVisibility(userId: String): String {
        if (userId.isEmpty()) return "EVERYONE"
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            doc.getString("profilePhotoVisibility")
                ?: doc.getString("profilePicVisibility")
                ?: "EVERYONE"
        } catch (e: Exception) {
            Log.e("ProfilePhotoPrivacyRepo", "Failed to fetch profile photo visibility: ${e.message}")
            "EVERYONE"
        }
    }
}
