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
     * Derives a 256-bit AES symmetric key from an ECDH shared secret using HKDF-SHA-256 (RFC 5869).
     */
    private fun deriveKeyHkdf(sharedSecret: ByteArray, info: ByteArray): SecretKeySpec {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        val salt = ByteArray(32)
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(sharedSecret)

        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info)
        mac.update(1.toByte())
        val okm = mac.doFinal()
        val keyBytes = okm.copyOf(32)
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Backward-compatible SHA-256 derivation for v1 payloads.
     */
    private fun deriveKeySha256(sharedSecret: ByteArray): SecretKeySpec {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val derivedKeyBytes = sha256.digest(sharedSecret)
        return SecretKeySpec(derivedKeyBytes, "AES")
    }

    private fun computeEcdhSecret(myPrivateKey: PrivateKey, peerPublicKey: PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(myPrivateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        return keyAgreement.generateSecret()
    }

    /**
     * Encrypts plaintext message into an authenticated E2EE payload.
     * Output format: enc:v2:<base64_iv>:<base64_ciphertext>:<sender_pub_base64>:<recipient_pub_base64>
     *
     * Including both public keys allows both the recipient and the sender (when reloading from server)
     * to compute the exact same ECDH shared secret and decrypt the message without plaintext ever reaching the server.
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

            val sharedSecret = computeEcdhSecret(myKeyPair.private, peerPublicKey)
            val sharedKey = deriveKeyHkdf(sharedSecret, "Tawasol-E2EE-v2".toByteArray(Charsets.UTF_8))

            // 12-byte random IV for AES-256-GCM (fresh unique nonce per message)
            val iv = ByteArray(12)
            secureRandom.nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, sharedKey, gcmSpec)

            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val ivBase64 = encodeBase64(iv)
            val cipherBase64 = encodeBase64(cipherBytes)
            val senderPubBase64 = encodeBase64(myKeyPair.public.encoded)
            val recipientPubBase64 = peerPublicKeyBase64.trim()

            Result.success("$PREFIX_V2$ivBase64:$cipherBase64:$senderPubBase64:$recipientPubBase64")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Decrypts an incoming or stored message.
     * Supports both v2 (HKDF-SHA-256 + dual-key header for sender & recipient) and
     * v1 (SHA-256 backward compatibility).
     *
     * Never crashes or leaks raw ciphertext: returns safe readable fallback if corrupted.
     */
    fun decryptMessage(
        myUserId: String,
        rawText: String?,
        fallbackPeerPublicKeyBase64: String? = null
    ): String {
        if (rawText == null) return ""
        if (!rawText.startsWith(PREFIX_V2) && !rawText.startsWith(PREFIX_V1)) {
            return rawText
        }

        return try {
            val myKeyPair = getOrCreateUserKeyPair(myUserId)
            val myPubBase64 = encodeBase64(myKeyPair.public.encoded)

            if (rawText.startsWith(PREFIX_V2)) {
                val payload = rawText.removePrefix(PREFIX_V2)
                val parts = payload.split(":")
                if (parts.size < 4) return "🔒 [رسالة مشفرة غير صالحة]"

                val ivBase64 = parts[0]
                val cipherBase64 = parts[1]
                val senderPubBase64 = parts[2]
                val recipientPubBase64 = parts[3]

                // Determine peer's public key based on whether we are sender or recipient
                val peerPubBase64 = if (myPubBase64 == senderPubBase64) {
                    recipientPubBase64
                } else {
                    senderPubBase64
                }

                val peerPubKeyBytes = decodeBase64(peerPubBase64)
                val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPubKeyBytes))

                val sharedSecret = computeEcdhSecret(myKeyPair.private, peerPublicKey)
                val sharedKey = deriveKeyHkdf(sharedSecret, "Tawasol-E2EE-v2".toByteArray(Charsets.UTF_8))

                val iv = decodeBase64(ivBase64)
                val cipherBytes = decodeBase64(cipherBase64)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val gcmSpec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, sharedKey, gcmSpec)

                val decryptedBytes = cipher.doFinal(cipherBytes)
                String(decryptedBytes, Charsets.UTF_8)
            } else {
                // v1 backward compatibility
                val payload = rawText.removePrefix(PREFIX_V1)
                val parts = payload.split(":")
                if (parts.size < 3) return "🔒 [رسالة مشفرة غير صالحة]"

                val ivBase64 = parts[0]
                val cipherBase64 = parts[1]
                val senderPubBase64 = parts[2]

                val peerPubBase64 = if (myPubBase64 == senderPubBase64) {
                    fallbackPeerPublicKeyBase64 ?: return "🔒 [رسالة مشفرة غير قابلة للقراءة]"
                } else {
                    senderPubBase64
                }

                val peerPubKeyBytes = decodeBase64(peerPubBase64)
                val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerPubKeyBytes))

                val sharedSecret = computeEcdhSecret(myKeyPair.private, peerPublicKey)
                val sharedKey = deriveKeySha256(sharedSecret)

                val iv = decodeBase64(ivBase64)
                val cipherBytes = decodeBase64(cipherBase64)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val gcmSpec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, sharedKey, gcmSpec)

                val decryptedBytes = cipher.doFinal(cipherBytes)
                String(decryptedBytes, Charsets.UTF_8)
            }
        } catch (_: Exception) {
            "🔒 [رسالة مشفرة غير قابلة للقراءة]"
        }
    }

    fun isEncrypted(rawText: String?): Boolean = Companion.isEncrypted(rawText)

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
        const val PREFIX_V2 = "enc:v2:"

        fun isEncrypted(rawText: String?): Boolean {
            return rawText?.startsWith(PREFIX_V2) == true || rawText?.startsWith(PREFIX_V1) == true
        }
    }
}
