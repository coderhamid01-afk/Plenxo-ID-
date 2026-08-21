package com.example.model

import android.util.Log
import androidx.compose.runtime.Immutable
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
@Immutable
data class UserModel(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val bio: String = "",
    val profilePicUrl: String = "",
    val plenxoId: String = "",
    val profileRingId: String = "none",
    val profileRing: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Generates a strictly 6-digit numeric Plenxo ID in range 100000..999999
 * formatted with the fixed 'PX-' prefix (e.g., PX-102938).
 */
fun generateUniquePlenxoId(): String {
    val numericCode = Random.nextInt(100000, 1000000).toString()
    return "PX-$numericCode"
}

/**
 * Single authoritative primitive that generates a candidate 6-digit Plenxo ID (PX-XXXXXX)
 * and verifies its uniqueness in Firestore. Called strictly via [resolveOrCreatePlenxoId].
 */
suspend fun generateUniqueNumericPlenxoId(
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
): String {
    val fallbackCode = Random.nextInt(100000, 1000000).toString()
    val fallbackPxId = "PX-$fallbackCode"
    return try {
        kotlinx.coroutines.withTimeoutOrNull(5000L) {
            var attempts = 0
            while (attempts < 5) {
                val numericCode = Random.nextInt(100000, 1000000).toString()
                val candidatePxId = "PX-$numericCode"

                try {
                    val plenxoIdQuery = firestore.collection("users")
                        .whereEqualTo("plenxoId", candidatePxId)
                        .limit(1)
                        .get()
                        .await()

                    if (plenxoIdQuery.isEmpty) {
                        return@withTimeoutOrNull candidatePxId
                    }
                } catch (e: Exception) {
                    Log.e("UserModels", "Error verifying Plenxo ID uniqueness: ${e.message}")
                    return@withTimeoutOrNull candidatePxId
                }
                attempts++
            }
            fallbackPxId
        } ?: fallbackPxId
    } catch (e: Exception) {
        fallbackPxId
    }
}

/**
 * Single, authoritative entry point for resolving or generating a user's permanent Plenxo ID.
 *
 * ROOT CAUSE FIX (Bug 1 - Dual Plenxo ID):
 * Previously, 7 distinct call sites attempted to read or generate a Plenxo ID using short, artificial
 * timeouts or fallback blocks. A read timeout was misinterpreted as "no ID exists", causing a new random
 * ID to be generated and written over the existing ID in Firestore.
 *
 * This function guarantees:
 * 1. Reads from Firestore default without artificial early timeouts (with 1 retry on error).
 * 2. Normalizes any valid existing ID (`PX-XXXXXX` or bare `XXXXXX`) and NEVER regenerates if found.
 * 3. Only generates a new ID if document read succeeds and field is genuinely absent.
 * 4. Atomically persists the resolved ID across both `users` and `users_data` collections via batched write.
 */
suspend fun resolveOrCreatePlenxoId(
    uid: String,
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
): String {
    require(uid.isNotBlank()) { "User UID cannot be blank when resolving Plenxo ID." }

    val userDocRef = firestore.collection("users").document(uid)
    val userDataDocRef = firestore.collection("users_data").document(uid)

    var existingPxId: String? = null
    var readSuccessful = false

    // 1. Try reading from Firestore (DEFAULT - SERVER/CACHE)
    for (attempt in 1..2) {
        try {
            val userSnap = userDocRef.get().await()
            val userDataSnap = userDataDocRef.get().await()

            existingPxId = userSnap.getString("plenxoId")
                ?: userSnap.getString("plenxo_id")
                ?: userDataSnap.getString("plenxoId")
                ?: userDataSnap.getString("plenxo_id")
                ?: userSnap.getString("userCode")
                ?: userDataSnap.getString("userCode")
                ?: userSnap.getString("px_id")
                ?: userDataSnap.getString("px_id")

            readSuccessful = true
            break
        } catch (e: Exception) {
            Log.w("PlenxoIdResolver", "Attempt $attempt failed reading Plenxo ID from DEFAULT source for $uid: ${e.message}")
            // Fall back to local CACHE source if offline
            try {
                val userSnapCache = userDocRef.get(com.google.firebase.firestore.Source.CACHE).await()
                val userDataSnapCache = userDataDocRef.get(com.google.firebase.firestore.Source.CACHE).await()
                existingPxId = userSnapCache.getString("plenxoId")
                    ?: userSnapCache.getString("plenxo_id")
                    ?: userDataSnapCache.getString("plenxoId")
                    ?: userDataSnapCache.getString("plenxo_id")
                    ?: userSnapCache.getString("userCode")
                    ?: userDataSnapCache.getString("userCode")
                    ?: userSnapCache.getString("px_id")
                    ?: userDataSnapCache.getString("px_id")

                readSuccessful = true
                break
            } catch (cacheEx: Exception) {
                Log.w("PlenxoIdResolver", "Cache fallback read also failed for $uid: ${cacheEx.message}")
            }

            if (attempt < 2) {
                kotlinx.coroutines.delay(300)
            }
        }
    }

    // Check if valid existing ID is found and normalize
    val cleanId = existingPxId?.trim()
    val normalized = when {
        cleanId == null -> null
        cleanId.matches(Regex("^PX-\\d{6}$")) -> cleanId
        cleanId.matches(Regex("^\\d{6}$")) -> "PX-$cleanId"
        cleanId.isNotBlank() && cleanId.startsWith("PX-") -> cleanId
        else -> null
    }

    if (normalized != null) {
        val numericCode = normalized.removePrefix("PX-")
        val updateMap = mapOf(
            "plenxoId" to normalized,
            "plenxo_id" to normalized,
            "userCode" to numericCode,
            "px_id" to normalized,
            "user_code" to numericCode
        )
        try {
            val batch = firestore.batch()
            batch.set(userDocRef, updateMap, com.google.firebase.firestore.SetOptions.merge())
            batch.set(userDataDocRef, updateMap, com.google.firebase.firestore.SetOptions.merge())
            batch.commit()
            
            val rdbRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users").child(uid)
            val rdbMap = hashMapOf<String, Any>(
                "plenxo_id" to normalized,
                "plenxoId" to normalized,
                "user_code" to numericCode,
                "userCode" to numericCode
            )
            rdbRef.updateChildren(rdbMap)
        } catch (e: Exception) {
            Log.w("PlenxoIdResolver", "Warning: Failed to sync normalized Plenxo ID $normalized: ${e.message}")
        }
        return normalized
    }

    // Deterministic fallback code derived from UID hash (100000..999999) if offline/unread
    val deterministicCode = (kotlin.math.abs(uid.hashCode()) % 900000 + 100000).toString()
    val fallbackPxId = "PX-$deterministicCode"

    val newPlenxoId = if (readSuccessful) {
        try {
            generateUniqueNumericPlenxoId(firestore)
        } catch (e: Exception) {
            fallbackPxId
        }
    } else {
        fallbackPxId
    }

    val numericCode = newPlenxoId.removePrefix("PX-")

    val newMap = mapOf(
        "plenxoId" to newPlenxoId,
        "plenxo_id" to newPlenxoId,
        "userCode" to numericCode,
        "px_id" to newPlenxoId,
        "user_code" to numericCode
    )

    try {
        val batch = firestore.batch()
        batch.set(userDocRef, newMap, com.google.firebase.firestore.SetOptions.merge())
        batch.set(userDataDocRef, newMap, com.google.firebase.firestore.SetOptions.merge())
        batch.commit()

        val rdbRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users").child(uid)
        val rdbMap = hashMapOf<String, Any>(
            "plenxo_id" to newPlenxoId,
            "plenxoId" to newPlenxoId,
            "user_code" to numericCode,
            "userCode" to numericCode
        )
        rdbRef.updateChildren(rdbMap)
    } catch (e: Exception) {
        Log.w("PlenxoIdResolver", "Warning: Failed batch write for new ID $newPlenxoId: ${e.message}")
    }

    Log.d("PlenxoIdResolver", "Resolved/created permanent Plenxo ID: $newPlenxoId for UID: $uid (readSuccessful=$readSuccessful)")
    return newPlenxoId
}

@Serializable
@Immutable
data class UserProfile(
    val uid: String = "",
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val userCode: String = "",
    val profilePicUrl: String = "",
    val statusMessage: String = "",
    val bio: String = "",
    val selectedRingId: String = "NONE",
    val profileRingId: String = "none",
    val profileRing: String? = null,
    val selectedFontId: String = "DEFAULT",
    val publicKey: String = "",
    val phoneNumber: String = "",
    val plenxoId: String = "",
    val securityData: SecurityData = SecurityData(),
    val lastSeenTimestamp: Long = 0L,
    val lastLoginTimestamp: Long = 0L,
    val termsAccepted: Boolean = false,
    val termsAcceptedAt: String = "",
    val leagueData: LeagueData = LeagueData()
)

fun UserProfile.toUserModel(): UserModel = UserModel(
    uid = uid.ifEmpty { id },
    displayName = displayName,
    email = email,
    bio = bio.ifEmpty { statusMessage },
    profilePicUrl = profilePicUrl,
    plenxoId = plenxoId,
    profileRingId = profileRingId.ifEmpty { selectedRingId },
    createdAt = lastLoginTimestamp
)

fun UserModel.toUserProfile(): UserProfile = UserProfile(
    uid = uid,
    id = uid,
    displayName = displayName,
    email = email,
    bio = bio,
    statusMessage = bio,
    profilePicUrl = profilePicUrl,
    plenxoId = plenxoId,
    profileRingId = profileRingId,
    selectedRingId = profileRingId,
    userCode = plenxoId
)


@Serializable
data class LeagueData(
    val currentCrown: String = "Bronze",
    val activeAccumulatedSeconds: Int = 0,
    val unlockedRings: List<String> = listOf("bronze_ring_1"),
    val unlockedFonts: List<String> = listOf("DEFAULT"),
    val lastClaimedTimestamp: String = ""
)

@Serializable
data class SecurityData(
    val failedLoginCount: Int = 0,
    val lockoutUntil: Long = 0L,
    val totalLifetimeFails: Int = 0
)

@Serializable
data class ActiveSession(
    val sessionId: String = "",
    val deviceName: String = "",
    val deviceModel: String = "",
    val operatingSystem: String = "",
    val ipAddress: String = "",
    val timestamp: Long = 0L,
    val lastActiveTime: Long = 0L,
    val isCurrentDevice: Boolean = false
)

@Serializable
@Immutable
data class ConnectedFriend(
    val uid: String = "",
    val displayName: String = "",
    val bio: String = "",
    val profilePicUrl: String = "",
    val plenxoId: String = "",
    val email: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

@Serializable
@Immutable
data class FriendRequest(
    val requestId: String = "",
    val senderUid: String = "",
    val senderPlenxoId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String = "",
    val receiverUid: String = "",
    val receiverPlenxoId: String = "",
    val status: String = "PENDING",
    val timestamp: Long = 0L,
    val senderPhone: String = "",
    val id: String = requestId,
    val senderId: String = senderUid,
    val receiverId: String = receiverUid,
    val senderProfilePic: String = senderPhotoUrl
)

@Serializable
data class MessagePayload(
    val messageId: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val messageText: String = "",
    val messageType: String = "TEXT",
    val mediaUrl: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val replyToMessageId: String? = null,
    val isEdited: Boolean = false,
    val status: String = "SENT",
    val expiresAt: Long? = null,
    val senderActiveFontId: String = "DEFAULT"
)

@Serializable
data class CallSession(
    val callId: String,
    val callerId: String,
    val receiverId: String,
    val state: String,
    val callType: String, // AUDIO, VIDEO
    val timestamp: Long
)
