package com.example.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

object EncryptionManager {

    private const val KEY_ALIAS = "plenxo_e2ee_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_SIZE = 128

    init {
        generateKeyPairIfNeeded()
    }

    private fun generateKeyPairIfNeeded() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
            kpg.initialize(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .build()
            )
            kpg.generateKeyPair()
        }
    }

    fun getPublicKeyBase64(): String {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    fun encryptMessage(message: String, receiverPublicKeyBase64: String): String {
        // 1. Generate random AES key
        val aesKey = generateAesKey()
        
        // 2. Encrypt message with AES-GCM
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(TAG_SIZE, iv))
        val ciphertext = cipher.doFinal(message.toByteArray())
        
        // 3. Encrypt AES key with receiver's RSA public key
        val receiverPublicKey = decodePublicKey(receiverPublicKeyBase64)
        val rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION)
        rsaCipher.init(Cipher.ENCRYPT_MODE, receiverPublicKey)
        val encryptedAesKey = rsaCipher.doFinal(aesKey.encoded)
        
        // 4. Package: Base64(EncryptedAesKey) | Base64(IV) | Base64(Ciphertext)
        return "${Base64.encodeToString(encryptedAesKey, Base64.NO_WRAP)}|${Base64.encodeToString(iv, Base64.NO_WRAP)}|${Base64.encodeToString(ciphertext, Base64.NO_WRAP)}"
    }

    fun decryptMessage(encryptedPackage: String): String {
        try {
            val parts = encryptedPackage.split("|")
            if (parts.size != 3) return encryptedPackage // Not an encrypted package
            
            val encryptedAesKey = Base64.decode(parts[0], Base64.NO_WRAP)
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
            
            // 1. Decrypt AES key with my RSA private key
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
            val rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION)
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey)
            val aesKeyBytes = rsaCipher.doFinal(encryptedAesKey)
            val aesKey = SecretKeySpec(aesKeyBytes, "AES")
            
            // 2. Decrypt message with AES-GCM
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(TAG_SIZE, iv))
            return String(cipher.doFinal(ciphertext))
        } catch (e: Exception) {
            return "[Decryption Failed]"
        }
    }

    private fun generateAesKey(): SecretKey {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(256)
        return kg.generateKey()
    }

    private fun decodePublicKey(base64: String): PublicKey {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        val spec = X509EncodedKeySpec(bytes)
        val kf = KeyFactory.getInstance("RSA")
        return kf.generatePublic(spec)
    }
}
