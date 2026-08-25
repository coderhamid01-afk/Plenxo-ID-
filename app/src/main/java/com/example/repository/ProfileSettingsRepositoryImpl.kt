@file:Suppress("DEPRECATION")
package com.example.repository

import android.util.Log
import com.example.model.UserProfileDomainModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody

class ProfileSettingsRepositoryImpl : ProfileSettingsRepository {

    private val firebaseAuth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private val moshi = com.squareup.moshi.Moshi.Builder()
        .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    override fun getProfileFlow(userId: String): Flow<UserProfileDomainModel?> = callbackFlow {
        val fbUid = firebaseAuth.currentUser?.uid ?: ""
        val sessionToken = try { com.example.util.SessionManager.getLoginState(com.example.PlenxoApplication.instance).token ?: "" } catch (e: Exception) { "" }
        val sessionEmail = try { com.example.util.SessionManager.getLoginState(com.example.PlenxoApplication.instance).email?.replace(".", "_") ?: "" } catch (e: Exception) { "" }
        val fallbackUid = fbUid.ifEmpty { sessionToken.ifEmpty { sessionEmail } }
        val targetId = if (userId.isNotBlank()) userId else fallbackUid

        if (targetId.isEmpty()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        val docRef = firestore.collection("users").document(targetId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                val currentEmail = firebaseAuth.currentUser?.email
                if (!currentEmail.isNullOrBlank()) {
                    firestore.collection("users").whereEqualTo("email", currentEmail).limit(1).get()
                        .addOnSuccessListener { querySnap ->
                            if (!querySnap.isEmpty) {
                                val d = querySnap.documents[0]
                                val data = d.data ?: emptyMap()
                                val resolvedDisplayName = (data["displayName"] as? String)
                                    ?: (data["display_name"] as? String)
                                    ?: (data["name"] as? String)
                                    ?: (data["current_name"] as? String)
                                    ?: (data["fullName"] as? String)
                                    ?: ""
                                val resolvedBio = (data["bio"] as? String)
                                    ?: (data["statusMessage"] as? String)
                                    ?: (data["current_bio"] as? String)
                                    ?: (data["status_message"] as? String)
                                    ?: (data["about"] as? String)
                                    ?: (data["status"] as? String)
                                    ?: ""
                                val resolvedPicUrl = (data["profilePicUrl"] as? String)
                                    ?: (data["profilePic"] as? String)
                                    ?: (data["avatarUrl"] as? String)
                                    ?: (data["avatar_url"] as? String)
                                    ?: (data["photoUrl"] as? String)
                                    ?: (data["profileUrl"] as? String)
                                    ?: (data["current_profile_pic_url"] as? String)
                                    ?: ""
                                val resolvedEmail = (data["email"] as? String)?.takeIf { it.isNotBlank() } ?: currentEmail
                                val resolvedPlenxoId = (data["plenxoId"] as? String)
                                    ?: (data["plenxo_id"] as? String)
                                    ?: (data["px_id"] as? String)
                                    ?: (data["userCode"] as? String)
                                    ?: (data["user_code"] as? String)
                                    ?: ""
                                val profile = UserProfileDomainModel(
                                    id = d.id,
                                    userId = targetId,
                                    displayName = resolvedDisplayName,
                                    name = resolvedDisplayName,
                                    email = resolvedEmail,
                                    statusMessage = resolvedBio,
                                    bio = resolvedBio,
                                    profilePicUrl = resolvedPicUrl,
                                    profileUrl = resolvedPicUrl,
                                    userCode = resolvedPlenxoId,
                                    plenxoId = resolvedPlenxoId,
                                    selectedRingId = (data["selectedRingId"] as? String) ?: "",
                                    profileRingId = (data["profileRingId"] as? String) ?: ""
                                )
                                trySend(profile)
                            } else {
                                trySend(null)
                            }
                        }
                        .addOnFailureListener {
                            trySend(null)
                        }
                } else {
                    trySend(null)
                }
                return@addSnapshotListener
            }

            val data = snapshot.data ?: emptyMap()
            val resolvedDisplayName = (data["displayName"] as? String)
                ?: (data["display_name"] as? String)
                ?: (data["name"] as? String)
                ?: (data["current_name"] as? String)
                ?: (data["fullName"] as? String)
                ?: ""
            val resolvedBio = (data["bio"] as? String)
                ?: (data["statusMessage"] as? String)
                ?: (data["current_bio"] as? String)
                ?: (data["status_message"] as? String)
                ?: (data["about"] as? String)
                ?: (data["status"] as? String)
                ?: ""
            val resolvedPicUrl = (data["profilePicUrl"] as? String)
                ?: (data["profilePic"] as? String)
                ?: (data["avatarUrl"] as? String)
                ?: (data["avatar_url"] as? String)
                ?: (data["photoUrl"] as? String)
                ?: (data["profileUrl"] as? String)
                ?: (data["current_profile_pic_url"] as? String)
                ?: ""
            val resolvedEmail = (data["email"] as? String)?.takeIf { it.isNotBlank() }
                ?: firebaseAuth.currentUser?.email
                ?: ""
            val resolvedPlenxoId = (data["plenxoId"] as? String)
                ?: (data["plenxo_id"] as? String)
                ?: (data["px_id"] as? String)
                ?: (data["userCode"] as? String)
                ?: (data["user_code"] as? String)
                ?: ""

            val profile = UserProfileDomainModel(
                id = snapshot.id,
                userId = targetId,
                displayName = resolvedDisplayName,
                name = resolvedDisplayName,
                email = resolvedEmail,
                statusMessage = resolvedBio,
                bio = resolvedBio,
                profilePicUrl = resolvedPicUrl,
                profileUrl = resolvedPicUrl,
                userCode = resolvedPlenxoId,
                plenxoId = resolvedPlenxoId,
                selectedRingId = (data["selectedRingId"] as? String) ?: "",
                profileRingId = (data["profileRingId"] as? String) ?: ""
            )
            trySend(profile)
        }

        awaitClose {
            listener.remove()
        }
    }

    override suspend fun updateProfile(
        userId: String,
        name: String,
        bio: String,
        profileUrl: String
    ) {
        val currentUid = firebaseAuth.currentUser?.uid ?: ""
        if (currentUid.isEmpty()) throw Exception("Not authenticated in Firebase Auth")
        val targetId = if (userId.isBlank()) currentUid else userId

        try {
            com.example.util.ProfileHistoryUtils.saveProfileWithHistory(
                uid = targetId,
                newName = name,
                newBio = bio,
                newProfileUrl = profileUrl,
                firestore = firestore,
                auth = firebaseAuth
            )

            val updates = mutableMapOf<String, Any>()
            if (name.isNotEmpty()) {
                updates["displayName"] = name
            }
            if (bio.isNotEmpty()) {
                updates["bio"] = bio
                updates["statusMessage"] = bio
            }
            if (profileUrl.isNotEmpty()) {
                updates["profilePicUrl"] = profileUrl
                updates["photoUrl"] = profileUrl
            }
            updates["updatedAt"] = System.currentTimeMillis()
            firebaseAuth.currentUser?.email?.let { updates["email"] = it }

            firestore.collection("users").document(targetId)
                .set(updates, SetOptions.merge())
                .await()

            val profileUpdateBuilder = com.google.firebase.auth.UserProfileChangeRequest.Builder()
            if (name.isNotEmpty()) profileUpdateBuilder.setDisplayName(name)
            if (profileUrl.isNotEmpty() && (profileUrl.startsWith("http://") || profileUrl.startsWith("https://"))) {
                profileUpdateBuilder.setPhotoUri(android.net.Uri.parse(profileUrl))
            }
            firebaseAuth.currentUser?.updateProfile(profileUpdateBuilder.build())?.await()

            Log.d("ProfileSettingsRepo", "Profile updated successfully in Firestore users for $targetId")
        } catch (e: Exception) {
            Log.e("ProfileSettingsRepo", "Failed to update profile in Firestore: ${e.message}")
            throw e
        }
    }

    override suspend fun updateRing(userId: String, ringId: String) {
        val currentUid = firebaseAuth.currentUser?.uid ?: ""
        if (currentUid.isEmpty()) throw Exception("Not authenticated in Firebase Auth")
        val targetId = if (userId.isBlank()) currentUid else userId

        try {
            val updates = mapOf("selectedRingId" to ringId)
            firestore.collection("users").document(targetId)
                .set(updates, SetOptions.merge())
                .await()
            Log.d("ProfileSettingsRepo", "Selected ring updated in Firestore for $targetId")
        } catch (e: Exception) {
            Log.e("ProfileSettingsRepo", "Failed to update ring in Firestore: ${e.message}")
            throw e
        }
    }

    override suspend fun updateProfileRing(userId: String, ringId: String) {
        val currentUid = firebaseAuth.currentUser?.uid ?: ""
        if (currentUid.isEmpty()) throw Exception("Not authenticated in Firebase Auth")
        val targetId = if (userId.isBlank()) currentUid else userId

        try {
            val updates = mapOf(
                "profileRing" to ringId,
                "profileRingId" to ringId,
                "selectedRingId" to ringId,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection("users").document(targetId)
                .set(updates, SetOptions.merge())
                .await()
            Log.d("ProfileSettingsRepo", "Profile ring updated in Firestore users for $targetId")
        } catch (e: Exception) {
            Log.e("ProfileSettingsRepo", "Failed to update profile ring in Firestore: ${e.message}")
            throw e
        }
    }

    override suspend fun uploadProfileImage(
        context: android.content.Context,
        uri: android.net.Uri
    ): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
        Log.d("ProfileSettingsRepo", "Starting Catbox.moe profile picture upload...")
        val uploadedUrl = com.example.network.CatboxStorageManager.uploadImage(context, uri)
        Log.d("ProfileSettingsRepo", "Catbox upload completed successfully. CDN URL: $uploadedUrl")

        // Synchronize with Firestore 'users' collection
        val currentUid = firebaseAuth.currentUser?.uid ?: ""
        if (currentUid.isNotEmpty()) {
            Log.d("ProfileSettingsRepo", "Syncing profilePicUrl to Firestore 'users' collection for userId: $currentUid")
            firestore.collection("users").document(currentUid)
                .set(mapOf("profilePicUrl" to uploadedUrl, "avatar_url" to uploadedUrl, "photoUrl" to uploadedUrl), SetOptions.merge())
                .await()

            val req = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setPhotoUri(android.net.Uri.parse(uploadedUrl))
                .build()
            firebaseAuth.currentUser?.updateProfile(req)?.await()
            Log.d("ProfileSettingsRepo", "Firestore users document and FirebaseAuth updated successfully with new Catbox CDN URL.")
        } else {
            Log.w("ProfileSettingsRepo", "No active user session detected. Skipping database sync.")
        }

        return@withContext uploadedUrl
    }
}
