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

class LastSeenPrivacyRepository {

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    suspend fun saveLastSeenVisibility(visibility: String): Boolean {
        val uid = currentUserId
        if (uid.isEmpty()) {
            Log.e("LastSeenPrivacyRepo", "Cannot save last seen visibility: user not authenticated")
            return false
        }

        return try {
            val normalizedVisibility = visibility.uppercase()
            val updates = mapOf(
                "lastSeenVisibility" to normalizedVisibility,
                "lastSeenVisibilitySetting" to normalizedVisibility,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("users").document(uid)
                .set(updates, SetOptions.merge())
                .await()

            Log.d("LastSeenPrivacyRepo", "Last seen visibility updated to: $normalizedVisibility for user $uid")
            true
        } catch (e: Exception) {
            Log.e("LastSeenPrivacyRepo", "Failed to save last seen visibility: ${e.message}", e)
            false
        }
    }

    fun getLastSeenVisibilityFlow(): Flow<String> = callbackFlow {
        val uid = currentUserId
        if (uid.isEmpty()) {
            trySend("EVERYONE")
            close()
            return@callbackFlow
        }

        val docRef = firestore.collection("users").document(uid)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("LastSeenPrivacyRepo", "Error listening for last seen visibility: ${error.message}")
                trySend("EVERYONE")
                return@addSnapshotListener
            }

            val visibility = snapshot?.getString("lastSeenVisibility")
                ?: snapshot?.getString("lastSeenVisibilitySetting")
                ?: "EVERYONE"

            trySend(visibility)
        }

        awaitClose {
            listener.remove()
        }
    }

    suspend fun updateLastSeenTimestamp(): Boolean {
        val uid = currentUserId
        if (uid.isEmpty()) return false

        return try {
            val now = System.currentTimeMillis()
            val updates = mapOf(
                "lastSeen" to now,
                "last_seen" to now,
                "lastActiveTimestamp" to FieldValue.serverTimestamp()
            )

            firestore.collection("users").document(uid)
                .set(updates, SetOptions.merge())
                .await()

            true
        } catch (e: Exception) {
            Log.e("LastSeenPrivacyRepo", "Failed to update last seen timestamp: ${e.message}", e)
            false
        }
    }

    fun observeUserLastSeen(targetUserId: String): Flow<Long?> = callbackFlow {
        if (targetUserId.isEmpty()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val docRef = firestore.collection("users").document(targetUserId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("LastSeenPrivacyRepo", "Error observing target user last seen: ${error.message}")
                trySend(null)
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists()) {
                trySend(null)
                return@addSnapshotListener
            }

            val visibility = (snapshot.getString("lastSeenVisibility")
                ?: snapshot.getString("lastSeenVisibilitySetting")
                ?: "EVERYONE").uppercase()

            val canView = when (visibility) {
                "EVERYONE" -> true
                "NOBODY" -> false
                "CONTACTS", "MY_CONTACTS" -> true // Defaults to contacts visibility perm
                else -> true
            }

            if (!canView) {
                trySend(null)
                return@addSnapshotListener
            }

            val lastSeenVal = snapshot.get("lastSeen") ?: snapshot.get("last_seen")
            val timestamp = when (lastSeenVal) {
                is Number -> lastSeenVal.toLong()
                is String -> lastSeenVal.toLongOrNull()
                else -> null
            }

            trySend(timestamp)
        }

        awaitClose {
            listener.remove()
        }
    }

    suspend fun fetchLastSeenVisibility(userId: String): String {
        if (userId.isEmpty()) return "EVERYONE"
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            doc.getString("lastSeenVisibility")
                ?: doc.getString("lastSeenVisibilitySetting")
                ?: "EVERYONE"
        } catch (e: Exception) {
            Log.e("LastSeenPrivacyRepo", "Failed to fetch last seen visibility: ${e.message}")
            "EVERYONE"
        }
    }
}
