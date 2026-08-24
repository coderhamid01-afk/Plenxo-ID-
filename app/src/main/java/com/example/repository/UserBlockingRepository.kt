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

class UserBlockingRepository {

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    suspend fun blockUser(targetUserId: String): Boolean {
        val uid = currentUserId
        if (uid.isEmpty() || targetUserId.isEmpty() || uid == targetUserId) {
            Log.e("UserBlockingRepo", "Invalid parameters for blocking user")
            return false
        }

        return try {
            val batch = firestore.batch()

            val currentUserRef = firestore.collection("users").document(uid)
            batch.set(
                currentUserRef,
                mapOf(
                    "blockedUsers" to FieldValue.arrayUnion(targetUserId),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )

            val blockedEntryRef = firestore.collection("users")
                .document(uid)
                .collection("blocked_list")
                .document(targetUserId)

            batch.set(
                blockedEntryRef,
                mapOf(
                    "targetUserId" to targetUserId,
                    "blockedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )

            batch.commit().await()
            Log.d("UserBlockingRepo", "Successfully blocked user $targetUserId for $uid")
            true
        } catch (e: Exception) {
            Log.e("UserBlockingRepo", "Failed to block user $targetUserId: ${e.message}", e)
            false
        }
    }

    suspend fun unblockUser(targetUserId: String): Boolean {
        val uid = currentUserId
        if (uid.isEmpty() || targetUserId.isEmpty()) {
            return false
        }

        return try {
            val batch = firestore.batch()

            val currentUserRef = firestore.collection("users").document(uid)
            batch.set(
                currentUserRef,
                mapOf(
                    "blockedUsers" to FieldValue.arrayRemove(targetUserId),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )

            val blockedEntryRef = firestore.collection("users")
                .document(uid)
                .collection("blocked_list")
                .document(targetUserId)

            batch.delete(blockedEntryRef)

            batch.commit().await()
            Log.d("UserBlockingRepo", "Successfully unblocked user $targetUserId for $uid")
            true
        } catch (e: Exception) {
            Log.e("UserBlockingRepo", "Failed to unblock user $targetUserId: ${e.message}", e)
            false
        }
    }

    fun getBlockedUsersFlow(): Flow<Set<String>> = callbackFlow {
        val uid = currentUserId
        if (uid.isEmpty()) {
            trySend(emptySet())
            close()
            return@callbackFlow
        }

        val docRef = firestore.collection("users").document(uid)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("UserBlockingRepo", "Error observing blocked users list: ${error.message}")
                trySend(emptySet())
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists()) {
                trySend(emptySet())
                return@addSnapshotListener
            }

            @Suppress("UNCHECKED_CAST")
            val blockedList = (snapshot.get("blockedUsers") as? List<String>)?.toSet() ?: emptySet()
            trySend(blockedList)
        }

        awaitClose {
            listener.remove()
        }
    }

    suspend fun isUserBlocked(targetUserId: String): Boolean {
        val uid = currentUserId
        if (uid.isEmpty() || targetUserId.isEmpty()) return false

        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            @Suppress("UNCHECKED_CAST")
            val blockedList = (doc.get("blockedUsers") as? List<String>) ?: emptyList()
            blockedList.contains(targetUserId)
        } catch (e: Exception) {
            Log.e("UserBlockingRepo", "Failed to check if user $targetUserId is blocked: ${e.message}")
            false
        }
    }

    suspend fun canInteractWithUser(targetUserId: String): Boolean {
        val uid = currentUserId
        if (uid.isEmpty() || targetUserId.isEmpty()) return false

        val IBlockedTarget = isUserBlocked(targetUserId)
        if (IBlockedTarget) return false

        return try {
            val targetDoc = firestore.collection("users").document(targetUserId).get().await()
            @Suppress("UNCHECKED_CAST")
            val targetBlockedList = (targetDoc.get("blockedUsers") as? List<String>) ?: emptyList()
            !targetBlockedList.contains(uid)
        } catch (e: Exception) {
            Log.e("UserBlockingRepo", "Failed to check interaction permission with $targetUserId: ${e.message}")
            true
        }
    }
}
