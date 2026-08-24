package com.example.repository

import android.util.Log
import com.example.model.FriendRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface FriendRequestRepository {
    suspend fun sendFriendRequest(targetUid: String): Boolean
    suspend fun acceptFriendRequest(requestId: String, senderUid: String): Boolean
    suspend fun declineFriendRequest(requestId: String): Boolean
    fun observePendingIncomingRequests(uid: String): Flow<List<FriendRequest>>
    fun observePendingOutgoingRequests(uid: String): Flow<List<FriendRequest>>
}

class FriendRequestRepositoryImpl : FriendRequestRepository {

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    override suspend fun sendFriendRequest(targetUid: String): Boolean {
        val uid = currentUserId
        if (uid.isEmpty() || targetUid.isEmpty() || uid == targetUid) {
            Log.e("FriendRequestRepo", "Invalid uids for friend request: sender=$uid, target=$targetUid")
            return false
        }

        return try {
            // Check if request already exists (either UPPERCASE or lowercase status)
            var existing = firestore.collection("friend_requests")
                .whereEqualTo("requestFrom", uid)
                .whereEqualTo("requestTo", targetUid)
                .whereEqualTo("status", "pending")
                .get()
                .await()

            if (existing.isEmpty) {
                existing = firestore.collection("friend_requests")
                    .whereEqualTo("requestFrom", uid)
                    .whereEqualTo("requestTo", targetUid)
                    .whereEqualTo("status", "PENDING")
                    .get()
                    .await()
            }

            if (!existing.isEmpty) {
                Log.d("FriendRequestRepo", "Friend request already pending from $uid to $targetUid")
                return true
            }

            // Fetch sender details from users collection to populate metadata
            val senderDoc = firestore.collection("users").document(uid).get().await()
            val senderName = senderDoc.getString("displayName")
                ?: auth.currentUser?.displayName
                ?: "User"
            val senderPic = senderDoc.getString("profilePicUrl")
                ?: senderDoc.getString("photoUrl")
                ?: ""
            val senderPhone = senderDoc.getString("phoneNumber") ?: ""
            val senderPlenxoId = senderDoc.getString("plenxoId") ?: senderDoc.getString("userCode") ?: ""

            val requestId = "${uid}_${targetUid}"
            val timestamp = System.currentTimeMillis()

            val requestData = mapOf(
                "requestId" to requestId,
                "requestFrom" to uid,
                "requestTo" to targetUid,
                "senderId" to uid,
                "senderUid" to uid,
                "receiverId" to targetUid,
                "receiverUid" to targetUid,
                "status" to "pending",
                "timestamp" to timestamp,
                "senderName" to senderName,
                "senderPhone" to senderPhone,
                "senderProfilePic" to senderPic
            )

            // Write to friend_requests
            firestore.collection("friend_requests")
                .document(requestId)
                .set(requestData, com.google.firebase.firestore.SetOptions.merge())
                .await()

            // Also write to chat_requests
            val chatRequestData = mapOf(
                "requestId" to requestId,
                "senderUid" to uid,
                "senderId" to uid,
                "receiverUid" to targetUid,
                "receiverId" to targetUid,
                "senderPlenxoId" to senderPlenxoId,
                "senderName" to senderName,
                "senderPhotoUrl" to senderPic,
                "status" to "pending",
                "timestamp" to timestamp
            )
            firestore.collection("chat_requests")
                .document(requestId)
                .set(chatRequestData, com.google.firebase.firestore.SetOptions.merge())
                .await()

            Log.d("FriendRequestRepo", "Successfully sent friend and chat request $requestId from $uid to $targetUid")
            true
        } catch (e: Exception) {
            Log.e("FriendRequestRepo", "Failed to send friend request from $uid to $targetUid: ${e.message}", e)
            false
        }
    }

    override suspend fun acceptFriendRequest(requestId: String, senderUid: String): Boolean {
        val uid = currentUserId
        if (uid.isEmpty() || requestId.isEmpty() || senderUid.isEmpty()) {
            Log.e("FriendRequestRepo", "Invalid parameters for acceptFriendRequest")
            return false
        }

        return try {
            val batch = firestore.batch()

            // 1. Update friend request status to ACCEPTED
            val requestRef = firestore.collection("friend_requests").document(requestId)
            batch.update(requestRef, mapOf(
                "status" to "ACCEPTED",
                "acceptedAt" to FieldValue.serverTimestamp()
            ))

            // 2. Add target sender user to current user's friends subcollection
            val currentUserFriendRef = firestore.collection("users")
                .document(uid)
                .collection("friends")
                .document(senderUid)

            val senderDoc = firestore.collection("users").document(senderUid).get().await()
            val senderData = mapOf(
                "friendUid" to senderUid,
                "addedAt" to FieldValue.serverTimestamp(),
                "displayName" to (senderDoc.getString("displayName") ?: ""),
                "photoUrl" to (senderDoc.getString("photoUrl") ?: senderDoc.getString("profilePicUrl") ?: "")
            )
            batch.set(currentUserFriendRef, senderData, SetOptions.merge())

            // 3. Add current user to target sender's friends subcollection
            val senderFriendRef = firestore.collection("users")
                .document(senderUid)
                .collection("friends")
                .document(uid)

            val currentDoc = firestore.collection("users").document(uid).get().await()
            val currentUserData = mapOf(
                "friendUid" to uid,
                "addedAt" to FieldValue.serverTimestamp(),
                "displayName" to (currentDoc.getString("displayName") ?: ""),
                "photoUrl" to (currentDoc.getString("photoUrl") ?: currentDoc.getString("profilePicUrl") ?: "")
            )
            batch.set(senderFriendRef, currentUserData, SetOptions.merge())

            // 4. Ensure contacts entry exists for direct chat creation
            val contact1Ref = firestore.collection("contacts").document("${uid}_${senderUid}")
            batch.set(contact1Ref, mapOf("user_id" to uid, "contact_id" to senderUid), SetOptions.merge())

            val contact2Ref = firestore.collection("contacts").document("${senderUid}_${uid}")
            batch.set(contact2Ref, mapOf("user_id" to senderUid, "contact_id" to uid), SetOptions.merge())

            batch.commit().await()
            Log.d("FriendRequestRepo", "Accepted friend request $requestId between $uid and $senderUid")
            true
        } catch (e: Exception) {
            Log.e("FriendRequestRepo", "Failed to accept friend request $requestId: ${e.message}", e)
            false
        }
    }

    override suspend fun declineFriendRequest(requestId: String): Boolean {
        if (requestId.isEmpty()) return false
        return try {
            firestore.collection("friend_requests").document(requestId)
                .update(mapOf(
                    "status" to "DECLINED",
                    "declinedAt" to FieldValue.serverTimestamp()
                ))
                .await()
            Log.d("FriendRequestRepo", "Declined friend request $requestId")
            true
        } catch (e: Exception) {
            Log.e("FriendRequestRepo", "Failed to decline friend request $requestId: ${e.message}", e)
            false
        }
    }

    override fun observePendingIncomingRequests(uid: String): Flow<List<FriendRequest>> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("friend_requests")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FriendRequestRepo", "Error observing incoming friend requests: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val requests = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val reqTo = (data["requestTo"] as? String) ?: (data["receiverUid"] as? String) ?: (data["receiverId"] as? String) ?: ""
                    val reqFrom = (data["requestFrom"] as? String) ?: (data["senderUid"] as? String) ?: (data["senderId"] as? String) ?: ""
                    val status = (data["status"] as? String) ?: "PENDING"

                    if (reqTo == uid && status.equals("PENDING", ignoreCase = true)) {
                        FriendRequest(
                            requestId = doc.id,
                            senderId = reqFrom,
                            senderUid = reqFrom,
                            receiverId = reqTo,
                            receiverUid = reqTo,
                            status = status,
                            timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis(),
                            senderName = (data["senderName"] as? String) ?: "User",
                            senderPhone = (data["senderPhone"] as? String) ?: "",
                            senderProfilePic = (data["senderProfilePic"] as? String) ?: ""
                        )
                    } else null
                }

                trySend(requests)
            }

        awaitClose { listener.remove() }
    }

    override fun observePendingOutgoingRequests(uid: String): Flow<List<FriendRequest>> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("friend_requests")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FriendRequestRepo", "Error observing outgoing friend requests: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val requests = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val reqFrom = (data["requestFrom"] as? String) ?: (data["senderUid"] as? String) ?: (data["senderId"] as? String) ?: ""
                    val reqTo = (data["requestTo"] as? String) ?: (data["receiverUid"] as? String) ?: (data["receiverId"] as? String) ?: ""
                    val status = (data["status"] as? String) ?: "PENDING"

                    if (reqFrom == uid && status.equals("PENDING", ignoreCase = true)) {
                        FriendRequest(
                            requestId = doc.id,
                            senderId = reqFrom,
                            senderUid = reqFrom,
                            receiverId = reqTo,
                            receiverUid = reqTo,
                            status = status,
                            timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis()
                        )
                    } else null
                }

                trySend(requests)
            }

        awaitClose { listener.remove() }
    }
}
