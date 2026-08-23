package com.example.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class SignUpResult {
    data class Success(val userId: String, val email: String) : SignUpResult()
    data class Error(val exception: Exception) : SignUpResult()
}

sealed class SignInResult {
    data class SuccessDirect(val userId: String, val email: String) : SignInResult()
    data class RequiresVerification(val email: String) : SignInResult()
    data class Error(val exception: Exception) : SignInResult()
}

sealed class VerificationResult {
    data class Success(val userId: String, val email: String) : VerificationResult()
    data class Error(val exception: Exception) : VerificationResult()
}

interface AuthRepository {
    suspend fun signUpWithEmail(email: String, password: String, displayName: String = ""): SignUpResult
    suspend fun loginWithEmail(email: String, password: String): SignInResult
    suspend fun getLoginCount(email: String): Int
    suspend fun incrementLoginCount(email: String, currentCount: Int, userId: String)
    suspend fun resetLoginCount(email: String, userId: String)
}

class AuthRepositoryImpl : AuthRepository {

    private val firebaseAuth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    override suspend fun signUpWithEmail(email: String, password: String, displayName: String): SignUpResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cleanPassword = password.trim()
        val cleanDisplayName = displayName.trim()

        val gmailRegex = Regex("^[a-zA-Z0-9._%+-]+@gmail\\.com$", RegexOption.IGNORE_CASE)
        if (!gmailRegex.matches(cleanEmail)) {
            return@withContext SignUpResult.Error(IllegalArgumentException("Registration is restricted exclusively to valid @gmail.com email addresses."))
        }

        return@withContext try {
            Log.d("AuthRepositoryImpl", "Creating Firebase Auth user for: $cleanEmail")

            val authResult = firebaseAuth.createUserWithEmailAndPassword(cleanEmail, cleanPassword).await()
            val firebaseUser = authResult.user
                ?: return@withContext SignUpResult.Error(Exception("Firebase Auth returned null user after sign up."))

            val userId = firebaseUser.uid
            Log.d("AuthRepositoryImpl", "Firebase SignUp success. UserId: $userId")

            // Single authoritative Plenxo ID resolution and profile initialization
            try {
                val generatedPlenxoId = com.example.model.resolveOrCreatePlenxoId(userId, firestore)
                val numericCode = generatedPlenxoId.removePrefix("PX-")

                val userData = mapOf(
                    "uid" to userId,
                    "id" to userId,
                    "userId" to userId,
                    "email" to cleanEmail,
                    "displayName" to cleanDisplayName.ifBlank { cleanEmail.substringBefore("@") },
                    "bio" to "Hey there! I am using Plenxo.",
                    "statusMessage" to "Hey there! I am using Plenxo.",
                    "profilePicUrl" to "",
                    "plenxoId" to generatedPlenxoId,
                    "userCode" to numericCode,
                    "user_code" to numericCode,
                    "px_id" to generatedPlenxoId,
                    "px_code" to numericCode,
                    "login_count" to 1,
                    "createdAt" to System.currentTimeMillis()
                )

                firestore.collection("users").document(userId).set(userData, com.google.firebase.firestore.SetOptions.merge()).await()
                firestore.collection("users_data").document(userId).set(userData, com.google.firebase.firestore.SetOptions.merge()).await()
                Log.d("PlenxoProfileSync", "User Profile persisted successfully with plenxoId: $generatedPlenxoId")
            } catch (dbEx: Exception) {
                Log.e("AuthRepositoryImpl", "Failed/timed out inserting initial Firestore user document: ${dbEx.message}", dbEx)
            }

            SignUpResult.Success(userId, cleanEmail)
        } catch (e: Exception) {
            Log.e("AuthRepositoryImpl", "Firebase SignUp failure: ${e.localizedMessage}", e)
            SignUpResult.Error(e)
        }
    }

    override suspend fun loginWithEmail(email: String, password: String): SignInResult = withContext(Dispatchers.IO) {
        val input = email.trim()
        val cleanPassword = password.trim()

        return@withContext try {
            val resolvedEmail: String = if (input.contains("@")) {
                val gmailRegex = Regex("^[a-zA-Z0-9._%+-]+@gmail\\.com$", RegexOption.IGNORE_CASE)
                if (!gmailRegex.matches(input)) {
                    return@withContext SignInResult.Error(IllegalArgumentException("Registration is restricted exclusively to valid @gmail.com email addresses."))
                }
                input
            } else {
                // Dual login: Provided a Plenxo ID -> query Firestore 'users', 'users_data', & Realtime DB
                var targetPlenxoId = input
                if (!targetPlenxoId.uppercase().startsWith("PX-")) {
                    targetPlenxoId = "PX-$targetPlenxoId"
                }
                val rawNumericCode = input.removePrefix("PX-").removePrefix("px-").trim()

                var foundEmailStr: String? = null

                // 1. Query 'users' collection
                val queriesToTry = listOf(
                    firestore.collection("users").whereEqualTo("plenxoId", targetPlenxoId.uppercase()),
                    firestore.collection("users").whereEqualTo("plenxo_id", targetPlenxoId.uppercase()),
                    firestore.collection("users").whereEqualTo("plenxoId", targetPlenxoId),
                    firestore.collection("users").whereEqualTo("plenxo_id", targetPlenxoId),
                    firestore.collection("users").whereEqualTo("userCode", rawNumericCode),
                    firestore.collection("users").whereEqualTo("user_code", rawNumericCode),
                    firestore.collection("users_data").whereEqualTo("plenxoId", targetPlenxoId.uppercase()),
                    firestore.collection("users_data").whereEqualTo("plenxo_id", targetPlenxoId.uppercase()),
                    firestore.collection("users_data").whereEqualTo("userCode", rawNumericCode)
                )

                for (query in queriesToTry) {
                    val snap = query.limit(1).get().await()
                    if (!snap.isEmpty) {
                        val emailField = snap.documents[0].getString("email")
                        if (!emailField.isNullOrBlank()) {
                            foundEmailStr = emailField
                            break
                        }
                    }
                }

                // 2. Realtime Database fallback
                if (foundEmailStr.isNullOrBlank()) {
                    try {
                        val rdbRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users")
                        val rdbSnap = rdbRef.get().await()
                        if (rdbSnap.exists()) {
                            for (child in rdbSnap.children) {
                                val pId = child.child("plenxo_id").value as? String
                                    ?: child.child("plenxoId").value as? String
                                    ?: child.child("userCode").value as? String
                                    ?: child.child("user_code").value as? String
                                if (pId != null && (pId.equals(targetPlenxoId, ignoreCase = true) || pId.equals(rawNumericCode, ignoreCase = true) || pId.equals(input, ignoreCase = true))) {
                                    val em = child.child("email").value as? String
                                    if (!em.isNullOrBlank()) {
                                        foundEmailStr = em
                                        break
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("AuthRepositoryImpl", "RDB fallback query error: ${e.message}")
                    }
                }

                if (foundEmailStr.isNullOrBlank()) {
                    return@withContext SignInResult.Error(IllegalArgumentException("No account found matching this Plenxo ID."))
                }
                foundEmailStr
            }

            Log.d("AuthRepositoryImpl", "Logging in via Firebase Auth for: $resolvedEmail")

            val authResult = firebaseAuth.signInWithEmailAndPassword(resolvedEmail, cleanPassword).await()
            val firebaseUser = authResult.user
                ?: return@withContext SignInResult.Error(Exception("Firebase Auth returned null user after login."))

            val userId = firebaseUser.uid
            Log.d("AuthRepositoryImpl", "Firebase login success for $resolvedEmail (userId: $userId)")

            // Check Before Write Logic: Verify if user profile document exists in Firestore
            try {
                val userDocRef = firestore.collection("users").document(userId)
                val userDataDocRef = firestore.collection("users_data").document(userId)
                var userSnap = try { userDocRef.get().await() } catch (e: Exception) { null }
                if (userSnap == null || !userSnap.exists()) {
                    userSnap = try { userDataDocRef.get().await() } catch (e: Exception) { null }
                }

                if (userSnap != null && userSnap.exists()) {
                    // STEP B: IF IT EXISTS (Returning User): DO NOT write or overwrite anything in Firestore.
                    val existingPxId = userSnap.getString("plenxoId")
                        ?: userSnap.getString("plenxo_id")
                        ?: userSnap.getString("px_id")
                        ?: userSnap.getString("userCode")
                        ?: ""
                    val existingName = userSnap.getString("displayName")
                        ?: userSnap.getString("name")
                        ?: ""
                    val existingBio = userSnap.getString("bio")
                        ?: userSnap.getString("statusMessage")
                        ?: ""
                    val existingPic = userSnap.getString("profilePicUrl")
                        ?: userSnap.getString("avatar_url")
                        ?: userSnap.getString("photoUrl")
                        ?: ""
                    val existingAge = userSnap.get("age")?.toString()
                        ?: userSnap.getString("dateOfBirth")
                        ?: ""

                    Log.d("AuthRepositoryImpl", "Returning user $userId exists. Preserving Plenxo ID: $existingPxId, Name: $existingName")
                    try {
                        val appCtx = com.example.PlenxoApplication.instance
                        com.example.util.SessionManager.saveUserProfileLocally(
                            appCtx,
                            plenxoId = existingPxId,
                            displayName = existingName,
                            bio = existingBio,
                            profilePicUrl = existingPic,
                            age = existingAge
                        )
                    } catch (e: Exception) {
                        Log.w("AuthRepositoryImpl", "Failed to save profile locally: ${e.message}")
                    }
                } else {
                    // IF IT DOES NOT EXIST (New Sign-Up / First Time User without document):
                    // ONLY THEN generate a new Plenxo ID and create a new document in Firestore.
                    val generatedPlenxoId = com.example.model.resolveOrCreatePlenxoId(userId, firestore)
                    val numericCode = generatedPlenxoId.removePrefix("PX-")
                    val initialData = mapOf(
                        "uid" to userId,
                        "id" to userId,
                        "email" to resolvedEmail,
                        "plenxoId" to generatedPlenxoId,
                        "plenxo_id" to generatedPlenxoId,
                        "userCode" to numericCode,
                        "user_code" to numericCode,
                        "displayName" to resolvedEmail.substringBefore("@"),
                        "bio" to "",
                        "profilePicUrl" to "",
                        "isProfileSetupCompleted" to false,
                        "createdAt" to System.currentTimeMillis()
                    )
                    userDocRef.set(initialData, com.google.firebase.firestore.SetOptions.merge()).await()
                    userDataDocRef.set(initialData, com.google.firebase.firestore.SetOptions.merge()).await()
                }
            } catch (e: Exception) {
                Log.e("AuthRepositoryImpl", "Check before write error on login: ${e.message}", e)
            }

            // Update login count asynchronously
            try {
                val count = getLoginCount(resolvedEmail)
                incrementLoginCount(resolvedEmail, count, userId)
            } catch (dbEx: Exception) {
                Log.w("AuthRepositoryImpl", "Failed to update login counter: ${dbEx.message}")
            }

            SignInResult.SuccessDirect(userId, resolvedEmail)
        } catch (e: Exception) {
            Log.e("AuthRepositoryImpl", "Firebase login failure: ${e.localizedMessage}", e)
            SignInResult.Error(e)
        }
    }

    override suspend fun getLoginCount(email: String): Int = withContext(Dispatchers.IO) {
        return@withContext try {
            val querySnapshot = firestore.collection("users_data")
                .whereEqualTo("email", email.trim())
                .limit(1)
                .get()
                .await()

            if (!querySnapshot.isEmpty) {
                val doc = querySnapshot.documents[0]
                (doc.getLong("login_count") ?: 0L).toInt()
            } else {
                0
            }
        } catch (e: Exception) {
            Log.w("AuthRepositoryImpl", "Could not query users_data login count: ${e.message}. Assuming 0.")
            0
        }
    }

    override suspend fun incrementLoginCount(email: String, currentCount: Int, userId: String) {
        try {
            val updates = mapOf(
                "login_count" to currentCount + 1,
                "email" to email.trim()
            )
            firestore.collection("users_data").document(userId)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                .await()
            Log.d("AuthRepositoryImpl", "Firestore: Incremented login_count to ${currentCount + 1}")
        } catch (e: Exception) {
            Log.e("AuthRepositoryImpl", "Failed to increment login_count: ${e.message}", e)
        }
    }

    override suspend fun resetLoginCount(email: String, userId: String) {
        try {
            val updates = mapOf(
                "login_count" to 0,
                "email" to email.trim()
            )
            firestore.collection("users_data").document(userId)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                .await()
            Log.d("AuthRepositoryImpl", "Firestore: Reset login_count to 0 for user $userId")
        } catch (e: Exception) {
            Log.e("AuthRepositoryImpl", "Failed to reset login_count: ${e.message}", e)
        }
    }
}
