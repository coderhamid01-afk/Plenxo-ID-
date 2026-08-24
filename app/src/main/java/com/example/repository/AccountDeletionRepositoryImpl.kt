package com.example.repository

import android.content.Context
import android.util.Log
import com.example.util.SessionManager
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class AccountDeletionRepositoryImpl : AccountDeletionRepository {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val fcm: FirebaseMessaging get() = FirebaseMessaging.getInstance()

    override suspend fun reauthenticateUser(password: String): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("User is not authenticated."))
        val email = user.email
            ?: return Result.failure(IllegalStateException("Account email could not be retrieved."))

        if (password.isBlank()) {
            return Result.failure(IllegalArgumentException("Password cannot be empty."))
        }

        return try {
            val credential = EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(credential).await()
            Log.d("AccountDeletionRepo", "User successfully re-authenticated with provided password.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AccountDeletionRepo", "Re-authentication failed: ${e.message}", e)
            Result.failure(Exception("Incorrect password. Please verify and try again."))
        }
    }

    override suspend fun executeFullAccountDeletion(context: Context): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("User is not authenticated."))
        val uid = user.uid

        if (uid.isBlank()) {
            return Result.failure(IllegalStateException("Invalid user ID."))
        }

        Log.d("AccountDeletionRepo", "Executing 6-Step account deletion pipeline for UID: $uid")

        return try {
            // STEP 2: FCM Token Cleanup
            try {
                fcm.unsubscribeFromTopic("all_users").await()
            } catch (ex: Throwable) {
                Log.w("AccountDeletionRepo", "FCM unsubscribe warning: ${ex.message}")
            }

            try {
                val fcmClearMap = mapOf("fcmToken" to FieldValue.delete())
                firestore.collection("users").document(uid).update(fcmClearMap).await()
            } catch (ex: Throwable) {
                Log.w("AccountDeletionRepo", "FCM token field clear warning: ${ex.message}")
            }

            try {
                fcm.deleteToken().await()
                Log.d("AccountDeletionRepo", "FCM token deleted from device")
            } catch (ex: Throwable) {
                Log.w("AccountDeletionRepo", "FCM deleteToken warning: ${ex.message}")
            }

            // STEP 3: Media Assets Note (Media hosted on Catbox.moe)
            Log.d("AccountDeletionRepo", "Catbox.moe media links cleared with profile documents.")

            // STEP 4: Firestore Records Erase
            try {
                val subcollections = listOf("friends", "settings", "keys", "blocked_list", "security_keys", "archived_keys")
                for (sub in subcollections) {
                    try {
                        val docs = firestore.collection("users").document(uid).collection(sub).get().await()
                        if (!docs.isEmpty) {
                            val batch = firestore.batch()
                            for (doc in docs.documents) {
                                batch.delete(doc.reference)
                            }
                            batch.commit().await()
                        }
                    } catch (subEx: Exception) {
                        Log.w("AccountDeletionRepo", "Subcollection $sub deletion warning: ${subEx.message}")
                    }
                }

                // Delete primary user documents
                val mainBatch = firestore.batch()
                mainBatch.delete(firestore.collection("users").document(uid))
                mainBatch.delete(firestore.collection("status").document(uid))
                mainBatch.commit().await()

                // Delete active friend request documents
                val reqsFrom = firestore.collection("friend_requests").whereEqualTo("requestFrom", uid).get().await()
                val reqsTo = firestore.collection("friend_requests").whereEqualTo("requestTo", uid).get().await()
                val reqsSender = firestore.collection("friend_requests").whereEqualTo("senderUid", uid).get().await()
                val reqsReceiver = firestore.collection("friend_requests").whereEqualTo("receiverUid", uid).get().await()

                val allRequestDocs = (reqsFrom.documents + reqsTo.documents + reqsSender.documents + reqsReceiver.documents).distinctBy { it.id }
                if (allRequestDocs.isNotEmpty()) {
                    val reqBatch = firestore.batch()
                    for (doc in allRequestDocs) {
                        reqBatch.delete(doc.reference)
                    }
                    reqBatch.commit().await()
                }

                Log.d("AccountDeletionRepo", "Firestore documents successfully erased for $uid")
            } catch (ex: Exception) {
                Log.e("AccountDeletionRepo", "Firestore deletion error: ${ex.message}", ex)
                return Result.failure(Exception("Failed to clean up Firestore database records: ${ex.localizedMessage}"))
            }

            // STEP 5: Firebase Auth User Deletion
            try {
                user.delete().await()
                Log.d("AccountDeletionRepo", "Firebase Auth user deleted permanently")
            } catch (ex: Exception) {
                Log.e("AccountDeletionRepo", "Firebase Auth user delete failed: ${ex.message}", ex)
                return Result.failure(Exception("Failed to delete authentication account: ${ex.localizedMessage}"))
            }

            // STEP 6: Session Clear
            try {
                auth.signOut()
                SessionManager.clearLoginState(context)
                Log.d("AccountDeletionRepo", "Session state cleared successfully")
            } catch (ex: Exception) {
                Log.w("AccountDeletionRepo", "Sign out warning: ${ex.message}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AccountDeletionRepo", "Unhandled exception during account deletion: ${e.message}", e)
            Result.failure(Exception("Account deletion failed: ${e.localizedMessage}"))
        }
    }
}
