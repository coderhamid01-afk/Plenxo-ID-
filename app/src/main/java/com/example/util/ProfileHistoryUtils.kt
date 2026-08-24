package com.example.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

object ProfileHistoryUtils {

    suspend fun saveProfileWithHistory(
        uid: String,
        newName: String,
        newBio: String,
        newProfileUrl: String,
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
        auth: FirebaseAuth = FirebaseAuth.getInstance()
    ) {
        if (uid.isBlank()) return

        val currentTimestamp = System.currentTimeMillis()
        val userDocRef = firestore.collection("users").document(uid)

        // 1. Fetch current snapshot to read existing name, bio, profileUrl
        val snapshot = try {
            userDocRef.get().await()
        } catch (e: Exception) {
            null
        }

        val oldName = snapshot?.getString("displayName")
            ?: snapshot?.getString("name")
            ?: ""
        val oldBio = snapshot?.getString("bio")
            ?: snapshot?.getString("about")
            ?: snapshot?.getString("statusMessage")
            ?: ""
        val oldProfileUrl = snapshot?.getString("profilePicUrl")
            ?: snapshot?.getString("avatar_url")
            ?: snapshot?.getString("photoUrl")
            ?: snapshot?.getString("profileUrl")
            ?: snapshot?.getString("profilePic")
            ?: ""
        val userEmail = auth.currentUser?.email
            ?: snapshot?.getString("email")
            ?: ""

        val updates = mutableMapOf<String, Any>(
            "updatedAt" to currentTimestamp
        )

        if (userEmail.isNotBlank()) {
            updates["email"] = userEmail
        }

        // Name update and history
        if (newName.isNotBlank()) {
            updates["displayName"] = newName
            updates["name"] = newName
            updates["current_name"] = newName

            if (oldName.isNotBlank() && oldName != newName) {
                updates["previous_name"] = oldName
                updates["last_previous_name"] = oldName
                updates["previous_names"] = FieldValue.arrayUnion(oldName)

                val nameHistoryItem = mapOf(
                    "previous_name" to oldName,
                    "current_name" to newName,
                    "timestamp" to currentTimestamp
                )
                updates["name_history"] = FieldValue.arrayUnion(nameHistoryItem)

                // Subcollection for queryable history
                try {
                    userDocRef.collection("name_history").add(nameHistoryItem).await()
                } catch (e: Exception) {
                    Log.w("ProfileHistory", "Error writing name_history subcollection: ${e.message}")
                }
            }
        }

        // Bio update and history
        if (newBio.isNotBlank() || oldBio.isNotBlank()) {
            updates["bio"] = newBio
            updates["about"] = newBio
            updates["statusMessage"] = newBio
            updates["current_bio"] = newBio

            if (oldBio.isNotBlank() && oldBio != newBio) {
                updates["previous_bio"] = oldBio
                updates["last_previous_bio"] = oldBio
                updates["previous_bios"] = FieldValue.arrayUnion(oldBio)

                val bioHistoryItem = mapOf(
                    "previous_bio" to oldBio,
                    "current_bio" to newBio,
                    "timestamp" to currentTimestamp
                )
                updates["bio_history"] = FieldValue.arrayUnion(bioHistoryItem)

                try {
                    userDocRef.collection("bio_history").add(bioHistoryItem).await()
                } catch (e: Exception) {
                    Log.w("ProfileHistory", "Error writing bio_history subcollection: ${e.message}")
                }
            }
        }

        // Profile Pic / Logo update and history
        if (newProfileUrl.isNotBlank()) {
            updates["profilePicUrl"] = newProfileUrl
            updates["avatar_url"] = newProfileUrl
            updates["photoUrl"] = newProfileUrl
            updates["profileUrl"] = newProfileUrl
            updates["profilePic"] = newProfileUrl
            updates["current_profile_pic_url"] = newProfileUrl

            if (oldProfileUrl.isNotBlank() && oldProfileUrl != newProfileUrl) {
                updates["previous_profile_pic_url"] = oldProfileUrl
                updates["last_previous_profile_pic"] = oldProfileUrl
                updates["previous_profile_pics"] = FieldValue.arrayUnion(oldProfileUrl)

                val avatarHistoryItem = mapOf(
                    "previous_avatar_url" to oldProfileUrl,
                    "current_avatar_url" to newProfileUrl,
                    "timestamp" to currentTimestamp
                )
                updates["avatar_history"] = FieldValue.arrayUnion(avatarHistoryItem)

                try {
                    userDocRef.collection("avatar_history").add(avatarHistoryItem).await()
                } catch (e: Exception) {
                    Log.w("ProfileHistory", "Error writing avatar_history subcollection: ${e.message}")
                }
            }
        }

        // Write to Firestore users
        try {
            userDocRef.set(updates, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("ProfileHistory", "Error updating users doc: ${e.message}")
        }

        // Write to Realtime Database
        try {
            val rdbRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users").child(uid)
            val rdbMap = hashMapOf<String, Any>(
                "uid" to uid,
                "displayName" to newName,
                "name" to newName,
                "current_name" to newName,
                "bio" to newBio,
                "current_bio" to newBio,
                "profile_pic_url" to newProfileUrl,
                "current_profile_pic_url" to newProfileUrl,
                "updatedAt" to currentTimestamp
            )

            if (userEmail.isNotBlank()) {
                rdbMap["email"] = userEmail
            }

            if (oldName.isNotBlank() && oldName != newName) {
                rdbMap["previous_name"] = oldName
            }
            if (oldBio.isNotBlank() && oldBio != newBio) {
                rdbMap["previous_bio"] = oldBio
            }
            if (oldProfileUrl.isNotBlank() && oldProfileUrl != newProfileUrl) {
                rdbMap["previous_profile_pic_url"] = oldProfileUrl
            }

            rdbRef.updateChildren(rdbMap).await()
        } catch (e: Exception) {
            Log.w("ProfileHistory", "Error updating Realtime Database: ${e.message}")
        }
    }
}
