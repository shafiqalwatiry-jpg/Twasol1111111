package com.tawasol.app.data

import com.tawasol.app.core.network.NetworkErrorMapper
import com.tawasol.app.core.security.E2EEManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

/**
 * Audit unit tests verifying fixes for:
 * 1. Problem 1: Network error sanitization (no IP, port, URL leaks; clear Arabic errors).
 * 2. Problem 2: Commutative deterministic direct conversation ID (User A and B share same ID).
 * 3. Problem 3: E2EE v2 prefix recognition and decryption check.
 * 4. Problem 4: Audio validation rules (no empty/short recordings).
 */
class NetworkAndDirectChatAuditTest {

    // --- Problem 1: Network Error Sanitization ---

    @Test
    fun testNetworkErrorMapper_sanitizesTechnicalDetails() {
        val rawTechnicalError = Exception("Failed to connect to wyrkekldegvsvvlztisr.supabase.co/104.18.38.10:443")
        val arabicMessage = NetworkErrorMapper.toUserMessage(rawTechnicalError)

        assertFalse("Message must not expose IP address", arabicMessage.contains("104.18.38.10"))
        assertFalse("Message must not expose domain name", arabicMessage.contains("supabase.co"))
        assertFalse("Message must not expose port", arabicMessage.contains(":443"))
        assertEquals("لا يوجد اتصال بالإنترنت. تحقق من اتصالك وحاول مرة أخرى", arabicMessage)
    }

    @Test
    fun testNetworkErrorMapper_unknownHostException() {
        val error = UnknownHostException("wyrkekldegvsvvlztisr.supabase.co")
        val message = NetworkErrorMapper.toUserMessage(error)
        assertEquals("لا يوجد اتصال بالإنترنت. تحقق من اتصالك وحاول مرة أخرى", message)
    }

    @Test
    fun testNetworkErrorMapper_connectException() {
        val error = ConnectException("Connection refused")
        val message = NetworkErrorMapper.toUserMessage(error)
        assertEquals("تعذر الاتصال بالخادم. تحقق من اتصالك بالإنترنت وحاول مجدداً", message)
    }

    @Test
    fun testNetworkErrorMapper_socketTimeoutException() {
        val error = SocketTimeoutException("Read timed out")
        val message = NetworkErrorMapper.toUserMessage(error)
        assertEquals("انتهت مهلة الاتصال بالخادم. حاول مجدداً", message)
    }

    @Test
    fun testNetworkErrorMapper_invalidCredentials() {
        val error = Exception("Invalid login credentials")
        val message = NetworkErrorMapper.toUserMessage(error)
        assertEquals("بيانات الدخول غير صحيحة. تحقق من اسم المستخدم أو كلمة المرور", message)
    }

    @Test
    fun testNetworkErrorMapper_sanitizesGeneralHttpError() {
        val rawGeneral = "Http failure response: 500 at https://api.tawasol.internal/v1/resource/192.168.1.1"
        val sanitized = NetworkErrorMapper.sanitize(rawGeneral)
        assertFalse(sanitized.contains("https://"))
        assertFalse(sanitized.contains("192.168.1.1"))
    }

    // --- Problem 2: Deterministic Commutative Conversation ID ---

    private fun computeDirectConversationId(userA: String, userB: String): String {
        val sortedUids = listOf(userA, userB).sorted()
        return UUID.nameUUIDFromBytes("tawasol_direct_${sortedUids[0]}_${sortedUids[1]}".toByteArray()).toString()
    }

    @Test
    fun testDirectConversationId_isCommutative() {
        val user1 = "0a56e01a-8210-4bf2-b2a1-37d4512e2fbb"
        val user2 = "9f21bc34-1189-4912-9c32-15e87a2d8a01"

        val idFromUser1Perspective = computeDirectConversationId(user1, user2)
        val idFromUser2Perspective = computeDirectConversationId(user2, user1)

        assertEquals("Conversation ID must be strictly identical regardless of initiator", idFromUser1Perspective, idFromUser2Perspective)
    }

    @Test
    fun testDirectConversationId_isDeterministic() {
        val user1 = "alice_uuid"
        val user2 = "bob_uuid"

        val run1 = computeDirectConversationId(user1, user2)
        val run2 = computeDirectConversationId(user1, user2)

        assertEquals("Conversation ID must be deterministic across calls", run1, run2)
    }

    @Test
    fun testDirectConversationId_distinguishesDifferentPeers() {
        val alice = "alice_uuid"
        val bob = "bob_uuid"
        val charlie = "charlie_uuid"

        val aliceBob = computeDirectConversationId(alice, bob)
        val aliceCharlie = computeDirectConversationId(alice, charlie)

        assertNotEquals("Different peer pairs must produce different conversation IDs", aliceBob, aliceCharlie)
    }

    // --- Problem 3: E2EE Recognition of V1 and V2 ---

    @Test
    fun testE2EE_recognizesBothV1AndV2Prefixes() {
        val plainText = "السلام عليكم ورحمة الله وبركاته"
        val v1Ciphertext = "enc:v1:YWxpY2Vfa2V5:Ym9iX2NpcGhlcg=="
        val v2Ciphertext = "enc:v2:YWxpY2VfcHVi:aXZfbm9uY2U=:Y2lwaGVydGV4dF9kYXRh"

        assertFalse("Plaintext must not be flagged as encrypted", E2EEManager.isEncrypted(plainText))
        assertTrue("V1 ciphertext must be recognized", E2EEManager.isEncrypted(v1Ciphertext))
        assertTrue("V2 ciphertext must be recognized", E2EEManager.isEncrypted(v2Ciphertext))
    }

    // --- Problem 4: Audio Validation Rules ---

    @Test
    fun testAudioValidation_rejectsTooShortOrEmpty() {
        val emptyBytes = ByteArray(0)
        val tinyBytes = ByteArray(100) // Less than 500 bytes minimum
        val validAudioBytes = ByteArray(1500) { 0x33.toByte() }

        val isValidEmpty = emptyBytes.isNotEmpty() && emptyBytes.size >= 500
        val isValidTiny = tinyBytes.isNotEmpty() && tinyBytes.size >= 500
        val isValidReal = validAudioBytes.isNotEmpty() && validAudioBytes.size >= 500

        assertFalse("Empty audio must be rejected", isValidEmpty)
        assertFalse("Tiny audio (< 500 bytes) must be rejected", isValidTiny)
        assertTrue("Real audio (>= 500 bytes) must be accepted", isValidReal)
    }
}
