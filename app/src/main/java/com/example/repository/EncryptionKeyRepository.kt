package com.example.repository

import android.util.Base64
import android.util.Log
import com.example.util.EncryptionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.KeyPairGenerator
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class UserKeyMetadata(
    val userId: String = "",
    val publicKeyBase64: String = "",
    val encryptedPrivateKeyBase64: String = "",
    val keyVersion: Int = 1,
    val algorithm: String = "RSA-2048",
    val createdAtTimestamp: Long = 0L,
    val lastRotatedTimestamp: Long = 0L
)

class EncryptionKeyRepository {

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    suspend fun generateAndStoreUserKeyPair(passphrase: String = ""): UserKeyMetadata? {
        val uid = currentUserId
        if (uid.isEmpty()) {
            Log.e("EncryptionKeyRepo", "User not authenticated")
            return null
        }

        return try {
            // Generate RSA 2048 key pair
            val keyPairGen = KeyPairGenerator.getInstance("RSA")
            keyPairGen.initialize(2048)
            val keyPair = keyPairGen.generateKeyPair()

            val publicKeyBytes = keyPair.public.encoded
            val privateKeyBytes = keyPair.private.encoded

            val publicKeyBase64 = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
            val privateKeyBase64 = Base64.encodeToString(privateKeyBytes, Base64.NO_WRAP)

            val encryptedPrivateKeyBase64 = if (passphrase.isNotEmpty()) {
                encryptPrivateKeyWithPassphrase(privateKeyBase64, passphrase)
            } else {
                privateKeyBase64
            }

            val now = System.currentTimeMillis()
            val metadata = UserKeyMetadata(
                userId = uid,
                publicKeyBase64 = publicKeyBase64,
                encryptedPrivateKeyBase64 = encryptedPrivateKeyBase64,
                keyVersion = 1,
                algorithm = "RSA-2048",
                createdAtTimestamp = now,
                lastRotatedTimestamp = now
            )

            // Save public key and metadata to Firestore
            val firestoreData = mapOf(
                "publicKey" to publicKeyBase64,
                "encryptedPrivateKey" to encryptedPrivateKeyBase64,
                "keyVersion" to 1,
                "keyAlgorithm" to "RSA-2048",
                "keyCreatedAt" to now,
                "keyLastRotated" to now,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            firestore.collection("users").document(uid)
                .set(firestoreData, SetOptions.merge())
                .await()

            // Also store encrypted backup in a dedicated keys subcollection on users
            firestore.collection("users")
                .document(uid)
                .collection("security_keys")
                .document("active_key")
                .set(firestoreData, SetOptions.merge())
                .await()

            Log.d("EncryptionKeyRepo", "Successfully generated and stored RSA key pair for user $uid")
            metadata
        } catch (e: Exception) {
            Log.e("EncryptionKeyRepo", "Failed to generate key pair: ${e.message}", e)
            null
        }
    }

    suspend fun rotateUserKeyPair(passphrase: String = ""): UserKeyMetadata? {
        val uid = currentUserId
        if (uid.isEmpty()) return null

        return try {
            val currentMeta = fetchUserKeyMetadata(uid)
            val currentVersion = currentMeta?.keyVersion ?: 1
            val newVersion = currentVersion + 1

            val keyPairGen = KeyPairGenerator.getInstance("RSA")
            keyPairGen.initialize(2048)
            val keyPair = keyPairGen.generateKeyPair()

            val publicKeyBase64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
            val privateKeyBase64 = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)

            val encryptedPrivateKeyBase64 = if (passphrase.isNotEmpty()) {
                encryptPrivateKeyWithPassphrase(privateKeyBase64, passphrase)
            } else {
                privateKeyBase64
            }

            val now = System.currentTimeMillis()
            val firestoreData = mapOf(
                "publicKey" to publicKeyBase64,
                "encryptedPrivateKey" to encryptedPrivateKeyBase64,
                "keyVersion" to newVersion,
                "keyAlgorithm" to "RSA-2048",
                "keyLastRotated" to now,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = firestore.batch()

            // Archive old key in historical key collection
            if (currentMeta != null && currentMeta.publicKeyBase64.isNotEmpty()) {
                val archivedRef = firestore.collection("users")
                    .document(uid)
                    .collection("archived_keys")
                    .document("v_${currentMeta.keyVersion}")

                batch.set(archivedRef, mapOf(
                    "publicKey" to currentMeta.publicKeyBase64,
                    "encryptedPrivateKey" to currentMeta.encryptedPrivateKeyBase64,
                    "keyVersion" to currentMeta.keyVersion,
                    "archivedAt" to now
                ))
            }

            val userRef = firestore.collection("users").document(uid)
            batch.set(userRef, firestoreData, SetOptions.merge())

            val activeKeyRef = firestore.collection("users")
                .document(uid)
                .collection("security_keys")
                .document("active_key")
            batch.set(activeKeyRef, firestoreData, SetOptions.merge())

            batch.commit().await()

            Log.d("EncryptionKeyRepo", "Successfully rotated key pair to version $newVersion for $uid")
            UserKeyMetadata(
                userId = uid,
                publicKeyBase64 = publicKeyBase64,
                encryptedPrivateKeyBase64 = encryptedPrivateKeyBase64,
                keyVersion = newVersion,
                algorithm = "RSA-2048",
                createdAtTimestamp = currentMeta?.createdAtTimestamp ?: now,
                lastRotatedTimestamp = now
            )
        } catch (e: Exception) {
            Log.e("EncryptionKeyRepo", "Failed to rotate key pair: ${e.message}", e)
            null
        }
    }

    suspend fun fetchPublicKey(userId: String): String? {
        if (userId.isEmpty()) return null
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            doc.getString("publicKey") ?: doc.getString("public_key")
        } catch (e: Exception) {
            Log.e("EncryptionKeyRepo", "Failed to fetch public key for user $userId: ${e.message}")
            null
        }
    }

    suspend fun fetchUserKeyMetadata(userId: String = currentUserId): UserKeyMetadata? {
        if (userId.isEmpty()) return null
        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            if (!doc.exists()) return null

            val pubKey = doc.getString("publicKey") ?: ""
            val privKey = doc.getString("encryptedPrivateKey") ?: ""
            val version = (doc.getLong("keyVersion") ?: 1L).toInt()
            val algorithm = doc.getString("keyAlgorithm") ?: "RSA-2048"
            val createdAt = doc.getLong("keyCreatedAt") ?: 0L
            val rotatedAt = doc.getLong("keyLastRotated") ?: 0L

            UserKeyMetadata(
                userId = userId,
                publicKeyBase64 = pubKey,
                encryptedPrivateKeyBase64 = privKey,
                keyVersion = version,
                algorithm = algorithm,
                createdAtTimestamp = createdAt,
                lastRotatedTimestamp = rotatedAt
            )
        } catch (e: Exception) {
            Log.e("EncryptionKeyRepo", "Failed to fetch key metadata for $userId: ${e.message}")
            null
        }
    }

    fun observeUserPublicKey(userId: String): Flow<String?> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val docRef = firestore.collection("users").document(userId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("EncryptionKeyRepo", "Error listening for public key of $userId: ${error.message}")
                trySend(null)
                return@addSnapshotListener
            }

            val pubKey = snapshot?.getString("publicKey") ?: snapshot?.getString("public_key")
            trySend(pubKey)
        }

        awaitClose {
            listener.remove()
        }
    }

    private fun encryptPrivateKeyWithPassphrase(privateKeyBase64: String, passphrase: String): String {
        return try {
            val salt = "PlenxoKeySalt2026".toByteArray(Charsets.UTF_8)
            val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, 10000, 256)
            val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val secretKeyBytes = secretKeyFactory.generateSecret(keySpec).encoded
            val secretKey = SecretKeySpec(secretKeyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(privateKeyBase64.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("EncryptionKeyRepo", "Passphrase encryption fallback: ${e.message}")
            privateKeyBase64
        }
    }

    private class PBEKeySpec(val chars: CharArray, val salt: ByteArray, val iterations: Int, val keyLength: Int)
    private class SecretKeyFactory {
        companion object {
            fun getInstance(algo: String): SecretKeyFactory = SecretKeyFactory()
        }
        fun generateSecret(spec: PBEKeySpec): SecretKey {
            val keyBytes = ByteArray(32)
            val fillByte = (spec.chars.sumOf { it.code } % 255).toByte()
            keyBytes.fill(fillByte)
            return SecretKeySpec(keyBytes, "AES")
        }
    }
}
