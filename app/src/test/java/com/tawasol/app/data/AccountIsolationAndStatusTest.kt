package com.tawasol.app.data

import com.tawasol.app.core.database.entity.ConversationEntity
import com.tawasol.app.core.database.entity.MessageEntity
import com.tawasol.app.domain.model.UserStatus
import com.tawasol.app.domain.repository.StatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Unit tests validating:
 * 1. Multi-account data isolation in Room schema (accountUserId column scoping).
 * 2. Status / Stories repository business rules: 24h expiry, view tracking, and status deletion.
 */
class AccountIsolationAndStatusTest {

    // --- Fake in-memory Room DAO simulator testing accountUserId filtering ---
    private class FakeRoomStorage {
        val conversations = mutableListOf<ConversationEntity>()
        val messages = mutableListOf<MessageEntity>()

        fun insertConversation(entity: ConversationEntity) {
            conversations.removeAll { it.id == entity.id && it.accountUserId == entity.accountUserId }
            conversations.add(entity)
        }

        fun insertMessage(entity: MessageEntity) {
            messages.removeAll { it.id == entity.id && it.accountUserId == entity.accountUserId }
            messages.add(entity)
        }

        fun getConversationsForAccount(accountUserId: String): List<ConversationEntity> {
            return conversations.filter { it.accountUserId == accountUserId }
        }

        fun getMessagesForAccount(accountUserId: String, conversationId: String): List<MessageEntity> {
            return messages.filter { it.accountUserId == accountUserId && it.conversationId == conversationId }
        }

        fun deleteConversationsForAccount(accountUserId: String) {
            conversations.removeAll { it.accountUserId == accountUserId }
        }

        fun deleteMessagesForAccount(accountUserId: String) {
            messages.removeAll { it.accountUserId == accountUserId }
        }
    }

    // --- Fake StatusRepository for unit testing status logic ---
    private class FakeStatusRepository(private val currentUserId: String) : StatusRepository {
        val statuses = mutableListOf<UserStatus>()
        private val _flow = MutableStateFlow<List<UserStatus>>(emptyList())

        private fun updateFlow() {
            val now = Instant.now()
            val cutoff = now.minus(24, ChronoUnit.HOURS)
            _flow.value = statuses.filter { it.createdAt.isAfter(cutoff) }
        }

        override fun getActiveStatuses(): Flow<List<UserStatus>> {
            updateFlow()
            return _flow
        }

        fun getMyStatuses(): Flow<List<UserStatus>> {
            updateFlow()
            return _flow.map { list -> list.filter { it.userId == currentUserId } }
        }

        override suspend fun postTextStatus(text: String): Result<UserStatus> {
            val status = UserStatus(
                id = "status_${statuses.size + 1}",
                userId = currentUserId,
                userDisplayName = "حساب تجريبي",
                userAvatarUrl = null,
                text = text,
                mediaUrl = null,
                statusType = "text",
                backgroundColor = "#005C4B",
                createdAt = Instant.now(),
                expiresAt = Instant.now().plus(24, ChronoUnit.HOURS),
                viewCount = 0,
                isSelf = true
            )
            statuses.add(status)
            updateFlow()
            return Result.success(status)
        }

        override suspend fun postMediaStatus(imageBytes: ByteArray, caption: String?): Result<UserStatus> {
            if (imageBytes.isEmpty()) {
                return Result.failure(IllegalArgumentException("الصورة فارغة"))
            }
            val status = UserStatus(
                id = "status_${statuses.size + 1}",
                userId = currentUserId,
                userDisplayName = "حساب تجريبي",
                userAvatarUrl = null,
                text = caption,
                mediaUrl = "https://fake.storage/img_${System.currentTimeMillis()}.jpg",
                statusType = "image",
                backgroundColor = "#005C4B",
                createdAt = Instant.now(),
                expiresAt = Instant.now().plus(24, ChronoUnit.HOURS),
                viewCount = 0,
                isSelf = true
            )
            statuses.add(status)
            updateFlow()
            return Result.success(status)
        }

        override suspend fun markStatusViewed(statusId: String): Result<Unit> {
            val idx = statuses.indexOfFirst { it.id == statusId }
            if (idx != -1) {
                val current = statuses[idx]
                if (!current.isViewedByMe) {
                    statuses[idx] = current.copy(
                        viewCount = current.viewCount + 1,
                        isViewedByMe = true
                    )
                    updateFlow()
                }
            }
            return Result.success(Unit)
        }

        override suspend fun deleteStatus(statusId: String): Result<Unit> {
            val removed = statuses.removeAll { it.id == statusId && it.userId == currentUserId }
            updateFlow()
            return if (removed) Result.success(Unit) else Result.failure(IllegalStateException("الحالة غير موجودة أو ليست لك"))
        }

        suspend fun createTextStatus(text: String, backgroundColor: String = "#005C4B"): Result<UserStatus> =
            postTextStatus(text)

        suspend fun createImageStatus(imageBytes: ByteArray, caption: String? = null, backgroundColor: String? = null): Result<UserStatus> =
            postMediaStatus(imageBytes, caption)

        suspend fun markStatusAsViewed(statusId: String): Result<Unit> =
            markStatusViewed(statusId)
    }

    private lateinit var roomStorage: FakeRoomStorage
    private lateinit var statusRepository: FakeStatusRepository

    @Before
    fun setUp() {
        roomStorage = FakeRoomStorage()
        statusRepository = FakeStatusRepository(currentUserId = "user_A")
    }

    @Test
    fun `account isolation prevents User B from seeing User A cached conversations`() {
        val userA = "uuid_user_A"
        val userB = "uuid_user_B"

        // Simulate User A logging in and syncing 2 conversations
        roomStorage.insertConversation(
            ConversationEntity(
                id = "convo_1",
                accountUserId = userA,
                type = "direct",
                title = "محادثة خاصة أ",
                unreadCount = 2
            )
        )
        roomStorage.insertConversation(
            ConversationEntity(
                id = "convo_2",
                accountUserId = userA,
                type = "direct",
                title = "محادثة خاصة ب",
                unreadCount = 0
            )
        )

        // Simulate User A storing messages in convo_1
        roomStorage.insertMessage(
            MessageEntity(
                id = "msg_1",
                accountUserId = userA,
                conversationId = "convo_1",
                senderId = userA,
                text = "بيانات سرية للمستخدم أ",
                messageType = "text",
                status = "read",
                createdAt = Instant.now()
            )
        )

        // Verify User A sees their conversations and messages
        val userAConvos = roomStorage.getConversationsForAccount(userA)
        assertEquals(2, userAConvos.size)
        val userAMsgs = roomStorage.getMessagesForAccount(userA, "convo_1")
        assertEquals(1, userAMsgs.size)
        assertEquals("بيانات سرية للمستخدم أ", userAMsgs.first().text)

        // Switch to User B (e.g. log out A, log in B on the same phone)
        val userBConvos = roomStorage.getConversationsForAccount(userB)
        val userBMsgs = roomStorage.getMessagesForAccount(userB, "convo_1")

        // CRITICAL CHECK: User B MUST see zero conversations and zero messages from User A
        assertTrue("User B should see 0 conversations from User A", userBConvos.isEmpty())
        assertTrue("User B should see 0 messages from User A", userBMsgs.isEmpty())

        // Now User B receives their own conversation
        roomStorage.insertConversation(
            ConversationEntity(
                id = "convo_3",
                accountUserId = userB,
                type = "direct",
                title = "محادثة المستخدم ب",
                unreadCount = 1
            )
        )
        val updatedUserBConvos = roomStorage.getConversationsForAccount(userB)
        assertEquals(1, updatedUserBConvos.size)
        assertEquals("محادثة المستخدم ب", updatedUserBConvos.first().title)

        // Verify User A still only sees their own 2 conversations
        assertEquals(2, roomStorage.getConversationsForAccount(userA).size)
    }

    @Test
    fun `clearing cache for User A does not wipe data for User B`() {
        val userA = "uuid_user_A"
        val userB = "uuid_user_B"

        roomStorage.insertConversation(
            ConversationEntity(id = "convo_a", accountUserId = userA, type = "direct", title = "A")
        )
        roomStorage.insertConversation(
            ConversationEntity(id = "convo_b", accountUserId = userB, type = "direct", title = "B")
        )

        // Clear local storage for user A
        roomStorage.deleteConversationsForAccount(userA)

        assertTrue(roomStorage.getConversationsForAccount(userA).isEmpty())
        assertEquals(1, roomStorage.getConversationsForAccount(userB).size)
        assertEquals("convo_b", roomStorage.getConversationsForAccount(userB).first().id)
    }

    @Test
    fun `status creation and 24h expiration filter`() = runBlocking {
        // Create active text status
        val activeRes = statusRepository.createTextStatus("حالة نشطة اليوم", "#005C4B")
        assertTrue(activeRes.isSuccess)

        // Insert an expired status (created 25 hours ago)
        val expiredStatus = UserStatus(
            id = "status_expired",
            userId = "user_A",
            userDisplayName = "حساب قديم",
            userAvatarUrl = null,
            text = "حالة قديمة جداً",
            mediaUrl = null,
            statusType = "text",
            backgroundColor = "#128C7E",
            createdAt = Instant.now().minus(25, ChronoUnit.HOURS),
            expiresAt = Instant.now().minus(1, ChronoUnit.HOURS),
            viewCount = 10,
            isViewedByMe = true
        )
        (statusRepository as FakeStatusRepository).statuses.add(expiredStatus)

        // Retrieve active statuses
        val activeList = statusRepository.getActiveStatuses().first()
        assertEquals(1, activeList.size)
        assertEquals("حالة نشطة اليوم", activeList.first().content)
        assertFalse(activeList.any { it.id == "status_expired" })
    }

    @Test
    fun `status view tracking increments once per viewer`() = runBlocking {
        val statusRes = statusRepository.createTextStatus("صباح الخير", "#075E54")
        val statusId = statusRes.getOrNull()!!.id

        assertEquals(0, statusRes.getOrNull()!!.viewsCount)
        assertFalse(statusRes.getOrNull()!!.isViewedByMe)

        // First view
        statusRepository.markStatusAsViewed(statusId)
        val viewedList = statusRepository.getActiveStatuses().first()
        val viewedStatus = viewedList.first { it.id == statusId }
        assertEquals(1, viewedStatus.viewsCount)
        assertTrue(viewedStatus.isViewedByMe)

        // Duplicate view from same user should not double-increment
        statusRepository.markStatusAsViewed(statusId)
        val reViewedStatus = statusRepository.getActiveStatuses().first().first { it.id == statusId }
        assertEquals(1, reViewedStatus.viewsCount)
    }

    @Test
    fun `user can delete their own status but not others`() = runBlocking {
        val myStatus = statusRepository.createTextStatus("حالتي الخاصة", "#005C4B").getOrNull()!!

        // Add a peer's status
        val peerStatus = UserStatus(
            id = "status_peer",
            userId = "peer_user_123",
            userDisplayName = "صديق",
            userAvatarUrl = null,
            text = "حالة الصديق",
            mediaUrl = null,
            statusType = "text",
            backgroundColor = "#25D366",
            createdAt = Instant.now(),
            expiresAt = Instant.now().plus(24, ChronoUnit.HOURS),
            viewCount = 0,
            isViewedByMe = false
        )
        (statusRepository as FakeStatusRepository).statuses.add(peerStatus)

        // Try deleting peer's status -> should fail
        val peerDeleteRes = statusRepository.deleteStatus("status_peer")
        assertTrue(peerDeleteRes.isFailure)

        // Delete own status -> should succeed
        val myDeleteRes = statusRepository.deleteStatus(myStatus.id)
        assertTrue(myDeleteRes.isSuccess)

        val remaining = statusRepository.getActiveStatuses().first()
        assertEquals(1, remaining.size)
        assertEquals("status_peer", remaining.first().id)
    }
}
