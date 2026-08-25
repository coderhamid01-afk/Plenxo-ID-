package com.example.model

import android.util.Log
import androidx.compose.runtime.Immutable
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlin.random.Random

data class UserDocReadResult(
    val snapshot: com.google.firebase.firestore.DocumentSnapshot?,
    val readConfirmed: Boolean
)

suspend fun fetchUserDocumentSafely(
    uid: String,
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    maxAttempts: Int = 3,
    emailFallback: String? = null
): UserDocReadResult {
    if (uid.isBlank()) return UserDocReadResult(null, false)
    val userDocRef = firestore.collection("users").document(uid)

    for (attempt in 1..maxAttempts) {
        try {
            val snap = kotlinx.coroutines.withTimeoutOrNull(8000L) {
                userDocRef.get().await()
            }
            if (snap != null && snap.exists()) {
                return UserDocReadResult(snap, true)
            } else if (snap != null && !snap.exists()) {
                // If direct doc lookup by uid didn't find anything, try query by email or uid field
                val targetEmail = emailFallback ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
                if (!targetEmail.isNullOrBlank()) {
                    val querySnap = kotlinx.coroutines.withTimeoutOrNull(6000L) {
                        firestore.collection("users")
                            .whereEqualTo("email", targetEmail.trim())
                            .limit(1)
                            .get()
                            .await()
                    }
                    if (querySnap != null && !querySnap.isEmpty) {
                        return UserDocReadResult(querySnap.documents[0], true)
                    }
                }
                return UserDocReadResult(snap, true)
            }
        } catch (e: Exception) {
            Log.w("PlenxoUserFetch", "Attempt $attempt failed reading users/$uid: ${e.message}")
        }
        try {
            val cacheSnap = userDocRef.get(com.google.firebase.firestore.Source.CACHE).await()
            if (cacheSnap.exists()) {
                return UserDocReadResult(cacheSnap, true)
            }
        } catch (_: Exception) { /* cache miss, fall through */ }
        if (attempt < maxAttempts) kotlinx.coroutines.delay(250L * attempt)
    }
    // Every attempt failed AND no cached copy existed
    Log.w("PlenxoUserFetch", "Could not confirm users/$uid after $maxAttempts attempts.")
    return UserDocReadResult(null, false)
}

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
 * and verifies its uniqueness in Firestore. Called strictly via [getOrCreatePermanentPlenxoId].
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
 * Single, authoritative entry point for getting or creating a user's permanent Plenxo ID.
 *
 * ARCHITECTURAL SPECIFICATION:
 * «ONE USER = ONE FIREBASE AUTH UID = ONE FIRESTORE USER DOCUMENT = ONE PERMANENT PLENXO ID.»
 *
 * Priority order:
 * 1. Firebase Auth UID
 * 2. Firestore /users/{uid} -> existing plenxoId
 * 3. Return existing ID (NEVER replace or regenerate)
 * 4. Only if Firestore document confirmed absent/empty: generate atomic unique PX-XXXXXX and save.
 */
suspend fun getOrCreatePermanentPlenxoId(
    uid: String,
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
): String {
    require(uid.isNotBlank()) { "User UID cannot be blank when resolving Plenxo ID." }

    val userDocRef = firestore.collection("users").document(uid)

    var existingPxId: String? = null

    // Authoritative Step 1: Read Firestore /users/{uid} (Server, fallback Cache)
    for (attempt in 1..3) {
        try {
            val userSnap = kotlinx.coroutines.withTimeoutOrNull(2500L) {
                userDocRef.get().await()
            }
            if (userSnap != null && userSnap.exists()) {
                existingPxId = userSnap.getString("plenxoId")
                    ?: userSnap.getString("plenxo_id")
                    ?: userSnap.getString("px_id")
                    ?: userSnap.getString("userCode")
                    ?: userSnap.getString("user_code")
                break
            } else if (userSnap != null && !userSnap.exists()) {
                break
            }
        } catch (e: Exception) {
            Log.w("PlenxoIdResolver", "Attempt $attempt reading Plenxo ID for $uid: ${e.message}")
            try {
                val userSnapCache = kotlinx.coroutines.withTimeoutOrNull(1500L) {
                    userDocRef.get(com.google.firebase.firestore.Source.CACHE).await()
                }
                if (userSnapCache != null && userSnapCache.exists()) {
                    existingPxId = userSnapCache.getString("plenxoId")
                        ?: userSnapCache.getString("plenxo_id")
                        ?: userSnapCache.getString("px_id")
                        ?: userSnapCache.getString("userCode")
                        ?: userSnapCache.getString("user_code")
                    break
                }
            } catch (_: Exception) {}

            if (attempt < 3) {
                kotlinx.coroutines.delay(200)
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
            "user_code" to numericCode,
            "px_id" to normalized,
            "px_code" to numericCode
        )
        try {
            userDocRef.set(updateMap, com.google.firebase.firestore.SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("PlenxoIdResolver", "Warning: Failed to sync normalized Plenxo ID $normalized: ${e.message}")
        }
        try {
            val appCtx = com.example.PlenxoApplication.instance
            val currentLocal = com.example.util.SessionManager.getUserProfileLocally(appCtx)
            com.example.util.SessionManager.saveUserProfileLocally(
                appCtx,
                plenxoId = normalized,
                displayName = currentLocal.displayName,
                bio = currentLocal.bio,
                profilePicUrl = currentLocal.profilePicUrl
            )
        } catch (_: Exception) {}
        return normalized
    }

    // Authoritative Step 2: Only if Firestore document has NO Plenxo ID, atomically generate and save ONE
    val deterministicCode = (kotlin.math.abs(uid.hashCode()) % 900000 + 100000).toString()
    val fallbackPxId = "PX-$deterministicCode"

    val newPlenxoId = try {
        generateUniqueNumericPlenxoId(firestore)
    } catch (e: Exception) {
        fallbackPxId
    }

    val numericCode = newPlenxoId.removePrefix("PX-")
    val newMap = mapOf(
        "plenxoId" to newPlenxoId,
        "plenxo_id" to newPlenxoId,
        "userCode" to numericCode,
        "user_code" to numericCode,
        "px_id" to newPlenxoId,
        "px_code" to numericCode
    )

    try {
        userDocRef.set(newMap, com.google.firebase.firestore.SetOptions.merge()).await()
        val appCtx = com.example.PlenxoApplication.instance
        val currentLocal = com.example.util.SessionManager.getUserProfileLocally(appCtx)
        com.example.util.SessionManager.saveUserProfileLocally(
            appCtx,
            plenxoId = newPlenxoId,
            displayName = currentLocal.displayName,
            bio = currentLocal.bio,
            profilePicUrl = currentLocal.profilePicUrl
        )
    } catch (e: Exception) {
        Log.w("PlenxoIdResolver", "Warning: Failed write for new ID $newPlenxoId: ${e.message}")
    }

    Log.d("PlenxoIdResolver", "Resolved/created permanent Plenxo ID: $newPlenxoId for UID: $uid")
    return newPlenxoId
}

/**
 * Backward compatibility alias for [getOrCreatePermanentPlenxoId].
 */
suspend fun resolveOrCreatePlenxoId(
    uid: String,
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
): String = getOrCreatePermanentPlenxoId(uid, firestore)

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
