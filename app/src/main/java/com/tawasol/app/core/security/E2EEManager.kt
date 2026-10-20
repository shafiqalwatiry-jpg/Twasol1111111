package com.tawasol.app.core.security

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-End Encryption (E2EE) Manager for Tawasol.
 *
 * Implements Elliptic Curve Diffie-Hellman (ECDH) on the standard NIST P-256 curve (secp256r1)
 * and AES-256-GCM symmetric payload encryption with authenticated integrity.
 */
class E2EEManager(private val keystoreManager: SecureKeyStorage) {

    private val secureRandom = SecureRandom()
    private val keyFactory = KeyFactory.getInstance("EC")

    private fun encodeBase64(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun decodeBase64(str: String): ByteArray {
        return Base64.getDecoder().decode(str.trim())
    }

    /**
     * Retrieves or generates the user's permanent local E2EE EC KeyPair.
     */
    @Synchronized
    fun getOrCreateUserKeyPair(userId: String): KeyPair {
        val privBase64 = keystoreManager.getUserPrivateKey(userId)
        val pubBase64 = keystoreManager.getUserPublicKey(userId)

        if (privBase64 != null && pubBase64 != null) {
            try {
                val privBytes = decodeBase64(privBase64)
                val pubBytes = decodeBase64(pubBase64)
                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))
                return KeyPair(publicKey, privateKey)
            } catch (_: Exception) {
                // If keys are corrupted, generate fresh ones
            }
        }

        // Generate new EC KeyPair on standard NIST P-256 (secp256r1)
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = kpg.generateKeyPair()

        val newPrivBase64 = encodeBase64(keyPair.private.encoded)
        val newPubBase64 = encodeBase64(keyPair.public.encoded)

        keystoreManager.saveUserPrivateKey(userId, newPrivBase64)
        keystoreManager.saveUserPublicKey(userId, newPubBase64)

        return keyPair
    }

    /**
     * Returns the user's public key encoded in Base64 for publishing to Supabase.
     */
    fun getUserPublicKeyBase64(userId: String): String {
        val keyPair = getOrCreateUserKeyPair(userId)
        return encodeBase64(keyPair.public.encoded)
    }

    /**
     * Derives a 256-bit AES symmetric key from an ECDH shared secret between our private key
     * and the peer's public key.
     */
    private fun deriveSharedKey(myPrivateKey: PrivateKey, peerPublicKey: PublicKey): SecretKeySpec {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(myPrivateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        // Hash shared secret with SHA-256 to produce uniform 256-bit AES key
        val sha256 = MessageDigest.getInstance("SHA-256")
        val derivedKeyBytes = sha256.digest(sharedSecret)
        return SecretKeySpec(derivedKeyBytes, "AES")
    }

    /**
     * Encrypts plaintext message into an E2EE payload.
     * Output format: enc:v1:<base64_iv>:<base64_ciphertext>:<sender_public_key_base64>
     */
    fun encryptMessage(
        myUserId: String,
        peerPublicKeyBase64: String,
        plainText: String
    ): Result<String> {
        return try {
            val myKeyPair = getOrCreateUserKeyPair(myUserId)
            val peerPubKeyBytes = decodeBase64(peerPublicKeyBase64)
            val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPubKeyBytes))

            val sharedKey = deriveSharedKey(myKeyPair.private, peerPublicKey)

            // 12-byte random IV for AES-GCM
            val iv = ByteArray(12)
            secureRandom.nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, sharedKey, gcmSpec)

            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val ivBase64 = encodeBase64(iv)
            val cipherBase64 = encodeBase64(cipherBytes)
            val myPubBase64 = encodeBase64(myKeyPair.public.encoded)

            Result.success("$PREFIX_V1$ivBase64:$cipherBase64:$myPubBase64")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Decrypts an incoming message. If the message starts with enc:v1:, decrypts it using
     * our private key and the sender's public key. Otherwise returns the raw text (backward compatibility).
     */
    fun decryptMessage(
        myUserId: String,
        rawText: String?
    ): String {
        if (rawText == null || !rawText.startsWith(PREFIX_V1)) {
            return rawText ?: ""
        }

        return try {
            val payload = rawText.removePrefix(PREFIX_V1)
            val parts = payload.split(":")
            if (parts.size < 3) return rawText

            val ivBase64 = parts[0]
            val cipherBase64 = parts[1]
            val senderPubBase64 = parts[2]

            val myKeyPair = getOrCreateUserKeyPair(myUserId)
            val senderPubKeyBytes = decodeBase64(senderPubBase64)
            val senderPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(senderPubKeyBytes))

            val sharedKey = deriveSharedKey(myKeyPair.private, senderPublicKey)

            val iv = decodeBase64(ivBase64)
            val cipherBytes = decodeBase64(cipherBase64)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, sharedKey, gcmSpec)

            val decryptedBytes = cipher.doFinal(cipherBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            // In case decryption fails, return a safe indicator
            "🔒 [رسالة مشفرة غير قابلة للقراءة]"
        }
    }

    /**
     * Generates a standard 60-digit Safety Number (رقم الأمان) between two users.
     * Formats 12 blocks of 5 digits: "12345 67890 12345 67890 ..."
     */
    fun generateSafetyNumber(
        myUserId: String,
        peerPublicKeyBase64: String
    ): String {
        val myPubKeyBase64 = getUserPublicKeyBase64(myUserId)

        // Sort both public keys lexicographically to guarantee identical safety number on both devices
        val sortedKeys = if (myPubKeyBase64 < peerPublicKeyBase64) {
            myPubKeyBase64 + peerPublicKeyBase64
        } else {
            peerPublicKeyBase64 + myPubKeyBase64
        }

        val sha512 = MessageDigest.getInstance("SHA-512")
        val hash = sha512.digest(sortedKeys.toByteArray(Charsets.UTF_8))

        // Convert 64 bytes of SHA-512 into 60 decimal digits (12 groups of 5)
        val digits = StringBuilder()
        for (i in 0 until 12) {
            val byte1 = (hash[i * 4].toInt() and 0xFF)
            val byte2 = (hash[i * 4 + 1].toInt() and 0xFF)
            val byte3 = (hash[i * 4 + 2].toInt() and 0xFF)
            val byte4 = (hash[i * 4 + 3].toInt() and 0xFF)

            val num = ((byte1 shl 24) or (byte2 shl 16) or (byte3 shl 8) or byte4).toLong() and 0xFFFFFFFFL
            val chunk = String.format("%05d", (num % 100000L))
            if (digits.isNotEmpty()) digits.append(" ")
            digits.append(chunk)
        }

        return digits.toString()
    }

    companion object {
        const val PREFIX_V1 = "enc:v1:"
    }
}
