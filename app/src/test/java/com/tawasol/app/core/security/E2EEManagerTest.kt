package com.tawasol.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class FakeSecureKeyStorage : SecureKeyStorage {
    private val storage = mutableMapOf<String, Any>()

    override fun saveUserPrivateKey(userId: String, privateKeyBase64: String) {
        storage["priv_$userId"] = privateKeyBase64
    }

    override fun getUserPrivateKey(userId: String): String? {
        return storage["priv_$userId"] as? String
    }

    override fun saveUserPublicKey(userId: String, publicKeyBase64: String) {
        storage["pub_$userId"] = publicKeyBase64
    }

    override fun getUserPublicKey(userId: String): String? {
        return storage["pub_$userId"] as? String
    }

    override fun savePeerPublicKey(peerUserId: String, publicKeyBase64: String) {
        storage["peer_$peerUserId"] = publicKeyBase64
    }

    override fun getPeerPublicKey(peerUserId: String): String? {
        return storage["peer_$peerUserId"] as? String
    }

    override fun saveSafetyNumberVerified(peerUserId: String, isVerified: Boolean) {
        storage["verified_$peerUserId"] = isVerified
    }

    override fun isSafetyNumberVerified(peerUserId: String): Boolean {
        return (storage["verified_$peerUserId"] as? Boolean) ?: false
    }
}

class E2EEManagerTest {

    private lateinit var aliceStorage: FakeSecureKeyStorage
    private lateinit var bobStorage: FakeSecureKeyStorage
    private lateinit var eveStorage: FakeSecureKeyStorage

    private lateinit var aliceE2EE: E2EEManager
    private lateinit var bobE2EE: E2EEManager
    private lateinit var eveE2EE: E2EEManager

    private val aliceId = "alice_uuid_101"
    private val bobId = "bob_uuid_202"
    private val eveId = "eve_uuid_303"

    @Before
    fun setUp() {
        aliceStorage = FakeSecureKeyStorage()
        bobStorage = FakeSecureKeyStorage()
        eveStorage = FakeSecureKeyStorage()

        aliceE2EE = E2EEManager(aliceStorage)
        bobE2EE = E2EEManager(bobStorage)
        eveE2EE = E2EEManager(eveStorage)
    }

    @Test
    fun `generate keypair and store in storage`() {
        val keyPair = aliceE2EE.getOrCreateUserKeyPair(aliceId)
        assertNotNull(keyPair.public)
        assertNotNull(keyPair.private)
        assertEquals("EC", keyPair.public.algorithm)

        // Verify keys were saved in storage
        assertNotNull(aliceStorage.getUserPrivateKey(aliceId))
        assertNotNull(aliceStorage.getUserPublicKey(aliceId))

        // Second call should return the exact same key pair
        val secondKeyPair = aliceE2EE.getOrCreateUserKeyPair(aliceId)
        assertEquals(keyPair.public, secondKeyPair.public)
    }

    @Test
    fun `encrypt produces v2 format and both Bob and Alice can decrypt`() {
        val bobPubKey = bobE2EE.getUserPublicKeyBase64(bobId)
        val originalMessage = "السلام عليكم ورحمة الله، هذه رسالة مشفرة تماماً في تواصل! 🔒"

        // Alice encrypts for Bob
        val encryptResult = aliceE2EE.encryptMessage(
            myUserId = aliceId,
            peerPublicKeyBase64 = bobPubKey,
            plainText = originalMessage
        )

        assertTrue(encryptResult.isSuccess)
        val cipherPayload = encryptResult.getOrNull()!!
        assertTrue(cipherPayload.startsWith(E2EEManager.PREFIX_V2))
        assertNotEquals(originalMessage, cipherPayload)

        // 1. Bob (recipient) decrypts the message
        val bobDecrypted = bobE2EE.decryptMessage(
            myUserId = bobId,
            rawText = cipherPayload
        )
        assertEquals(originalMessage, bobDecrypted)

        // 2. Alice (sender) can also decrypt her own message when reloading from server!
        val aliceDecrypted = aliceE2EE.decryptMessage(
            myUserId = aliceId,
            rawText = cipherPayload
        )
        assertEquals(originalMessage, aliceDecrypted)
    }

    @Test
    fun `eve cannot decrypt message intended for Bob`() {
        val bobPubKey = bobE2EE.getUserPublicKeyBase64(bobId)
        val secretMessage = "بيانات سرية للغاية لا يمكن لأحد غير بوب قراءتها"

        val encryptResult = aliceE2EE.encryptMessage(
            myUserId = aliceId,
            peerPublicKeyBase64 = bobPubKey,
            plainText = secretMessage
        )
        val cipherPayload = encryptResult.getOrNull()!!

        // Eve tries to decrypt with her own private key
        val eveAttempt = eveE2EE.decryptMessage(
            myUserId = eveId,
            rawText = cipherPayload
        )

        assertTrue(eveAttempt.contains("غير قابلة للقراءة"))
        assertNotEquals(secretMessage, eveAttempt)
    }

    @Test
    fun `backward compatibility with legacy v1 payloads`() {
        // Construct a genuine v1 payload: enc:v1:<iv>:<cipher>:<senderPub>
        val aliceKeyPair = aliceE2EE.getOrCreateUserKeyPair(aliceId)
        val bobPubKeyBytes = java.util.Base64.getDecoder().decode(bobE2EE.getUserPublicKeyBase64(bobId))
        val kf = java.security.KeyFactory.getInstance("EC")
        val bobPublicKey = kf.generatePublic(X509EncodedKeySpec(bobPubKeyBytes))

        // ECDH + SHA-256 (v1 logic)
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(aliceKeyPair.private)
        ka.doPhase(bobPublicKey, true)
        val secret = ka.generateSecret()
        val derived = MessageDigest.getInstance("SHA-256").digest(secret)
        val aesKey = SecretKeySpec(derived, "AES")

        val iv = ByteArray(12) { 7 }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        val plainText = "رسالة قديمة بتشفير الإصدار الأول v1"
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val b64Iv = java.util.Base64.getEncoder().encodeToString(iv)
        val b64Cipher = java.util.Base64.getEncoder().encodeToString(cipherBytes)
        val b64AlicePub = aliceE2EE.getUserPublicKeyBase64(aliceId)

        val v1Payload = "enc:v1:$b64Iv:$b64Cipher:$b64AlicePub"

        // Bob decrypts the v1 payload seamlessly
        val decrypted = bobE2EE.decryptMessage(bobId, v1Payload)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun `tampered ciphertext returns safe fallback string and never crashes`() {
        val bobPubKey = bobE2EE.getUserPublicKeyBase64(bobId)
        val msg = "اختبار رسالة متلاعب بها"
        val payload = aliceE2EE.encryptMessage(aliceId, bobPubKey, msg).getOrNull()!!

        // Corrupt the ciphertext part
        val parts = payload.split(":")
        val corruptedPayload = "enc:v2:${parts[2]}:TAMPERED_CIPHER_TEXT_123:${parts[4]}:${parts[5]}"

        val result = bobE2EE.decryptMessage(bobId, corruptedPayload)
        assertTrue(result.contains("غير قابلة للقراءة"))
    }

    @Test
    fun `safety number is commutative and produces 12 groups of 5 digits`() {
        val alicePubKey = aliceE2EE.getUserPublicKeyBase64(aliceId)
        val bobPubKey = bobE2EE.getUserPublicKeyBase64(bobId)

        // Alice computes safety number with Bob's public key
        val aliceSafetyNumber = aliceE2EE.generateSafetyNumber(aliceId, bobPubKey)

        // Bob computes safety number with Alice's public key
        val bobSafetyNumber = bobE2EE.generateSafetyNumber(bobId, alicePubKey)

        // Both must be EXACTLY identical
        assertEquals(aliceSafetyNumber, bobSafetyNumber)

        // Check format: 12 blocks of 5 digits separated by spaces
        val blocks = aliceSafetyNumber.split(" ")
        assertEquals(12, blocks.size)
        for (block in blocks) {
            assertEquals(5, block.length)
            assertTrue(block.all { it.isDigit() })
        }
    }

    @Test
    fun `decrypting raw unencrypted plaintext returns text unchanged`() {
        val legacyText = "رسالة قديمة غير مشفرة"
        val result = bobE2EE.decryptMessage(bobId, legacyText)
        assertEquals(legacyText, result)
    }
}
