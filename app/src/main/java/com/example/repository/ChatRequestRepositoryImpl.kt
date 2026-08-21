package com.example.repository

import android.util.Log
import com.example.model.ChatRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface ChatRequestRepository {
    suspend fun sendChatRequest(
        senderUid: String,
        receiverUid: String,
        senderName: String = "",
        senderPlenxoId: String = "",
        senderPhotoUrl: String = "",
        receiverPlenxoId: String = ""
    ): Boolean

    fun observeIncomingRequests(receiverUid: String): Flow<List<ChatRequest>>
    fun observeSentRequests(senderUid: String): Flow<List<ChatRequest>>
    fun observeAllRequestsForUser(uid: String): Flow<List<ChatRequest>>
    fun observeContactStatuses(uid: String): Flow<Map<String, String>>
    
    suspend fun acceptChatRequest(requestId: String, senderUid: String, receiverUid: String): Boolean
    suspend fun rejectChatRequest(requestId: String, senderUid: String = "", receiverUid: String = ""): Boolean
}

class ChatRequestRepositoryImpl : ChatRequestRepository {
    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    override suspend fun sendChatRequest(
        senderUid: String,
        receiverUid: String,
        senderName: String,
        senderPlenxoId: String,
        senderPhotoUrl: String,
        receiverPlenxoId: String
    ): Boolean {
        if (senderUid.isBlank() || receiverUid.isBlank()) return false

        val requestId = "${senderUid}_${receiverUid}"
        val now = System.currentTimeMillis()

        val requestData = mapOf(
            "requestId" to requestId,
            "senderUid" to senderUid,
            "senderId" to senderUid,
            "senderPlenxoId" to senderPlenxoId,
            "senderName" to senderName,
            "senderPhotoUrl" to senderPhotoUrl,
            "receiverUid" to receiverUid,
            "receiverId" to receiverUid,
            "requestFrom" to senderUid,
            "requestTo" to receiverUid,
            "status" to "pending",
            "timestamp" to now
        )

        return try {
            firestore.collection("chat_requests")
                .document(requestId)
                .set(requestData, SetOptions.merge())
                .await()

            firestore.collection("friend_requests")
                .document(requestId)
                .set(requestData, SetOptions.merge())
                .await()

            Log.d("ChatRequestRepo", "Successfully wrote chat_requests and friend_requests for $requestId")
            true
        } catch (e: Exception) {
            Log.e("ChatRequestRepo", "Failed to send chat request from $senderUid to $receiverUid: ${e.message}", e)
            false
        }
    }

    override fun observeIncomingRequests(receiverUid: String): Flow<List<ChatRequest>> = callbackFlow {
        if (receiverUid.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("chat_requests")
            .whereEqualTo("receiverUid", receiverUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatRequestRepo", "Error observing incoming requests: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val requests = snapshot.documents.mapNotNull { doc ->
                        try {
                            val id = doc.getString("requestId") ?: doc.id
                            val senderId = doc.getString("senderUid") ?: ""
                            val rId = doc.getString("receiverUid") ?: ""
                            val status = (doc.getString("status") ?: "PENDING").uppercase()
                            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            val sName = doc.getString("senderName") ?: ""
                            val sPlenxoId = doc.getString("senderPlenxoId") ?: ""
                            val sProfilePic = doc.getString("senderPhotoUrl") ?: ""
                            val rPlenxoId = doc.getString("receiverPlenxoId") ?: ""
                            val rName = doc.getString("receiverName") ?: ""
                            val rProfilePic = doc.getString("receiverPhotoUrl") ?: ""

                            ChatRequest(
                                requestId = id,
                                senderUid = senderId,
                                receiverUid = rId,
                                status = status,
                                timestamp = timestamp,
                                senderName = sName,
                                senderPlenxoId = sPlenxoId,
                                senderPhotoUrl = sProfilePic,
                                receiverPlenxoId = rPlenxoId,
                                receiverName = rName,
                                receiverPhotoUrl = rProfilePic
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    trySend(requests)
                } else {
                    trySend(emptyList())
                }
            }

        awaitClose { listener.remove() }
    }

    override fun observeSentRequests(senderUid: String): Flow<List<ChatRequest>> = callbackFlow {
        if (senderUid.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("chat_requests")
            .whereEqualTo("senderUid", senderUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatRequestRepo", "Error observing sent requests: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val requests = snapshot.documents.mapNotNull { doc ->
                        try {
                            val id = doc.getString("requestId") ?: doc.id
                            val sId = doc.getString("senderUid") ?: ""
                            val rId = doc.getString("receiverUid") ?: ""
                            val status = (doc.getString("status") ?: "PENDING").uppercase()
                            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            val sName = doc.getString("senderName") ?: ""
                            val sPlenxoId = doc.getString("senderPlenxoId") ?: ""
                            val sProfilePic = doc.getString("senderPhotoUrl") ?: ""
                            val rPlenxoId = doc.getString("receiverPlenxoId") ?: ""
                            val rName = doc.getString("receiverName") ?: ""
                            val rProfilePic = doc.getString("receiverPhotoUrl") ?: ""

                            ChatRequest(
                                requestId = id,
                                senderUid = sId,
                                receiverUid = rId,
                                status = status,
                                timestamp = timestamp,
                                senderName = sName,
                                senderPlenxoId = sPlenxoId,
                                senderPhotoUrl = sProfilePic,
                                receiverPlenxoId = rPlenxoId,
                                receiverName = rName,
                                receiverPhotoUrl = rProfilePic
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    trySend(requests)
                } else {
                    trySend(emptyList())
                }
            }
        awaitClose { listener.remove() }
    }

    override fun observeAllRequestsForUser(uid: String): Flow<List<ChatRequest>> = callbackFlow {
        if (uid.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("chat_requests")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatRequestRepo", "Error observing all requests: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val requests = snapshot.documents.mapNotNull { doc ->
                        try {
                            val id = doc.getString("requestId") ?: doc.id
                            val sId = doc.getString("senderUid") ?: ""
                            val rId = doc.getString("receiverUid") ?: ""
                            val status = (doc.getString("status") ?: "PENDING").uppercase()
                            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            val sName = doc.getString("senderName") ?: ""
                            val sPlenxoId = doc.getString("senderPlenxoId") ?: ""
                            val sProfilePic = doc.getString("senderPhotoUrl") ?: ""
                            val rPlenxoId = doc.getString("receiverPlenxoId") ?: ""

                            if (sId == uid || rId == uid) {
                                ChatRequest(
                                    requestId = id,
                                    senderUid = sId,
                                    receiverUid = rId,
                                    status = status,
                                    timestamp = timestamp,
                                    senderName = sName,
                                    senderPlenxoId = sPlenxoId,
                                    senderPhotoUrl = sProfilePic,
                                    receiverPlenxoId = rPlenxoId
                                )
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                    trySend(requests)
                } else {
                    trySend(emptyList())
                }
            }
        awaitClose { listener.remove() }
    }

    override fun observeContactStatuses(uid: String): Flow<Map<String, String>> = callbackFlow {
        if (uid.isBlank()) {
            trySend(emptyMap())
            close()
            return@callbackFlow
        }

        // To determine contact statuses cleanly, we will observe chat_requests for both incoming and outgoing
        val listener = firestore.collection("chat_requests")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatRequestRepo", "Error observing contact statuses: ${error.message}")
                    trySend(emptyMap())
                    return@addSnapshotListener
                }
                
                val statusMap = mutableMapOf<String, String>()
                snapshot?.documents?.forEach { doc ->
                    val sId = doc.getString("senderUid") ?: ""
                    val rId = doc.getString("receiverUid") ?: ""
                    val status = (doc.getString("status") ?: "PENDING").uppercase()
                    
                    if (sId == uid) {
                        statusMap[rId] = status
                    } else if (rId == uid) {
                        statusMap[sId] = status
                    }
                }
                trySend(statusMap)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun acceptChatRequest(
        requestId: String,
        senderUid: String,
        receiverUid: String
    ): Boolean {
        if (requestId.isBlank() || senderUid.isBlank() || receiverUid.isBlank()) {
            Log.e("MainChatSync", "acceptChatRequest failed: blank input parameters (requestId=$requestId, sender=$senderUid, receiver=$receiverUid)")
            return false
        }

        val conversationId = listOf(senderUid, receiverUid).sorted().joinToString("_")
        val timestamp = System.currentTimeMillis()

        Log.d("MainChatSync", "acceptChatRequest executing for requestId=$requestId, conversationId=$conversationId")

        // Step 1: Update chat_requests document status to "accepted"
        try {
            val reqRef = firestore.collection("chat_requests").document(requestId)
            reqRef.set(mapOf("status" to "accepted", "updatedAt" to timestamp), SetOptions.merge()).await()
            Log.d("MainChatSync", "Step 1: chat_requests/$requestId status set to accepted")
        } catch (e: Exception) {
            Log.e("MainChatSync", "Step 1 (chat_requests) error: ${e.message}", e)
        }

        // Step 1b: Update friend_requests document status to "accepted"
        try {
            firestore.collection("friend_requests").document(requestId)
                .set(mapOf("status" to "accepted", "updatedAt" to timestamp), SetOptions.merge()).await()
            Log.d("MainChatSync", "Step 1b: friend_requests/$requestId status set to accepted")
        } catch (e: Exception) {
            Log.w("MainChatSync", "Step 1b (friend_requests) skipped/error: ${e.message}")
        }

        // Step 2: Create conversation document in messages/{conversationId} & chats/{conversationId}
        val participantsMap = mapOf(
            senderUid to true,
            receiverUid to true
        )
        val participantUidsList = listOf(senderUid, receiverUid)

        val conversationData = mapOf(
            "conversationId" to conversationId,
            "chatId" to conversationId,
            "senderId" to senderUid,
            "receiverId" to receiverUid,
            "user1Id" to senderUid,
            "user2Id" to receiverUid,
            "participants" to participantsMap,
            "participantUids" to participantUidsList,
            "status" to "accepted",
            "createdAt" to timestamp,
            "lastMessage" to "Chat started",
            "lastMessageTimestamp" to timestamp,
            "updatedAt" to timestamp
        )

        var step2Success = false
        try {
            firestore.collection("messages").document(conversationId)
                .set(conversationData, SetOptions.merge())
                .await()
            step2Success = true
            Log.d("MainChatSync", "Step 2: messages/$conversationId written successfully")
        } catch (e: Exception) {
            Log.e("MainChatSync", "Step 2 (messages) error: ${e.message}", e)
        }

        // Step 3: Create conversation document in chats/{conversationId}
        var step3Success = false
        try {
            firestore.collection("chats").document(conversationId)
                .set(conversationData, SetOptions.merge())
                .await()
            step3Success = true
            Log.d("MainChatSync", "Step 3: chats/$conversationId written successfully")
        } catch (e: Exception) {
            Log.e("MainChatSync", "Step 3 (chats) error: ${e.message}", e)
        }

        // Step 4: Write contact documents to `contacts` collection for both directions
        try {
            val contactDoc1 = "${senderUid}_${receiverUid}"
            val contactDoc2 = "${receiverUid}_${senderUid}"
            firestore.collection("contacts").document(contactDoc1)
                .set(mapOf("user_id" to senderUid, "contact_id" to receiverUid, "timestamp" to timestamp), SetOptions.merge())
                .await()
            firestore.collection("contacts").document(contactDoc2)
                .set(mapOf("user_id" to receiverUid, "contact_id" to senderUid, "timestamp" to timestamp), SetOptions.merge())
                .await()
            Log.d("MainChatSync", "Step 4: contacts entries created for $senderUid <-> $receiverUid")
        } catch (e: Exception) {
            Log.w("MainChatSync", "Step 4 (contacts) error: ${e.message}", e)
        }

        return step2Success || step3Success
    }

    override suspend fun rejectChatRequest(requestId: String, senderUid: String, receiverUid: String): Boolean {
        if (requestId.isBlank()) return false
        return try {
            firestore.collection("chat_requests").document(requestId).delete().await()
            Log.d("ChatRequestRepo", "Chat request deleted/rejected atomically: $requestId")
            true
        } catch (e: Exception) {
            Log.e("ChatRequestRepo", "Failed to delete/reject chat request: ${e.message}", e)
            false
        }
    }
}
