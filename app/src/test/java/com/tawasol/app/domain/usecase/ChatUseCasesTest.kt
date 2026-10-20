package com.tawasol.app.domain.usecase

import com.tawasol.app.domain.model.Conversation
import com.tawasol.app.domain.model.Message
import com.tawasol.app.domain.model.MessageStatus
import com.tawasol.app.domain.model.MessageType
import com.tawasol.app.domain.model.SafetyNumber
import com.tawasol.app.domain.model.User
import com.tawasol.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class FakeChatRepository : ChatRepository {
    val conversations = mutableMapOf<String, Conversation>()
    val messages = mutableMapOf<String, MutableList<Message>>()
    val directConvoPairs = mutableMapOf<String, String>() // peerUserId -> conversationId
    var markAsReadCallCount = 0

    override fun getConversations(): Flow<List<Conversation>> {
        return flowOf(conversations.values.sortedByDescending { it.lastMessageTime })
    }

    override fun getConversation(conversationId: String): Flow<Conversation?> {
        return flowOf(conversations[conversationId])
    }

    override fun getMessages(conversationId: String, limit: Int): Flow<List<Message>> {
        val list = messages[conversationId] ?: emptyList()
        val paged = list.sortedBy { it.createdAt }.takeLast(limit)
        return flowOf(paged)
    }

    override suspend fun getOrCreateDirectConversation(otherUserId: String): Result<String> {
        if (otherUserId == "self_id") {
            return Result.failure(IllegalArgumentException("لا يمكن بدء محادثة مع نفسك"))
        }
        val existingId = directConvoPairs[otherUserId]
        if (existingId != null) {
            return Result.success(existingId)
        }
        val newId = "convo_${directConvoPairs.size + 1}"
        directConvoPairs[otherUserId] = newId
        conversations[newId] = Conversation(
            id = newId,
            type = "direct",
            title = "المستخدم $otherUserId",
            peerUserId = otherUserId
        )
        return Result.success(newId)
    }

    override suspend fun sendMessage(
        conversationId: String,
        text: String?,
        replyToId: String?
    ): Result<Message> {
        val msgList = messages.getOrPut(conversationId) { mutableListOf() }
        val newMsg = Message(
            id = "msg_${msgList.size + 1}",
            conversationId = conversationId,
            senderId = "self_id",
            text = text,
            type = MessageType.TEXT,
            status = MessageStatus.SENT,
            replyToMessageId = replyToId,
            createdAt = Instant.now()
        )
        msgList.add(newMsg)

        val convo = conversations[conversationId]
        if (convo != null) {
            conversations[conversationId] = convo.copy(
                lastMessage = text,
                lastMessageTime = newMsg.createdAt
            )
        }
        return Result.success(newMsg)
    }

    override suspend fun markConversationAsRead(conversationId: String): Result<Unit> {
        markAsReadCallCount++
        val convo = conversations[conversationId]
        if (convo != null) {
            conversations[conversationId] = convo.copy(unreadCount = 0)
        }
        return Result.success(Unit)
    }

    override suspend fun fetchOlderMessages(
        conversationId: String,
        beforeTimestamp: Instant,
        limit: Int
    ): Result<Int> {
        val list = messages[conversationId] ?: return Result.success(0)
        val older = list.filter { it.createdAt.isBefore(beforeTimestamp) }.take(limit)
        return Result.success(older.size)
    }

    override fun startRealtimeMessagesSubscription(conversationId: String): Flow<Message> {
        return MutableSharedFlow()
    }

    override suspend fun syncConversations(): Result<Unit> = Result.success(Unit)
    override suspend fun syncOutbox(): Result<Unit> = Result.success(Unit)

    override suspend fun createGroup(title: String, description: String?, memberUserIds: List<String>): Result<String> =
        Result.success("group_fake_id")

    override suspend fun addGroupMember(conversationId: String, userId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun removeGroupMember(conversationId: String, userId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun getGroupMembers(conversationId: String): Result<List<User>> =
        Result.success(emptyList())

    override suspend fun sendMediaMessage(
        conversationId: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        messageType: MessageType,
        caption: String?,
        replyToId: String?,
        durationSeconds: Int?
    ): Result<Message> {
        val msgList = messages.getOrPut(conversationId) { mutableListOf() }
        val newMsg = Message(
            id = "msg_${msgList.size + 1}",
            conversationId = conversationId,
            senderId = "self_id",
            text = caption,
            type = messageType,
            mediaUrl = "https://fake.storage/$fileName",
            mediaDurationSeconds = durationSeconds,
            status = MessageStatus.SENT,
            replyToMessageId = replyToId,
            createdAt = Instant.now()
        )
        msgList.add(newMsg)
        return Result.success(newMsg)
    }

    override suspend fun ensureE2EESetup(): Result<Unit> = Result.success(Unit)

    override suspend fun getSafetyNumber(peerUserId: String): Result<SafetyNumber> =
        Result.success(
            SafetyNumber(
                formattedNumber = "12345 67890 12345 67890 12345 67890 12345 67890 12345 67890 12345 67890",
                peerUsername = "peer_user",
                peerDisplayName = "المستخدم الآخر",
                isVerified = true
            )
        )
}

class ChatUseCasesTest {

    private lateinit var fakeChatRepository: FakeChatRepository
    private lateinit var sendMessageUseCase: SendMessageUseCase
    private lateinit var getOrCreateConversationUseCase: GetOrCreateConversationUseCase
    private lateinit var markConversationAsReadUseCase: MarkConversationAsReadUseCase
    private lateinit var getMessagesUseCase: GetMessagesUseCase
    private lateinit var sendMediaMessageUseCase: SendMediaMessageUseCase

    @Before
    fun setUp() {
        fakeChatRepository = FakeChatRepository()
        sendMessageUseCase = SendMessageUseCase(fakeChatRepository)
        getOrCreateConversationUseCase = GetOrCreateConversationUseCase(fakeChatRepository)
        markConversationAsReadUseCase = MarkConversationAsReadUseCase(fakeChatRepository)
        getMessagesUseCase = GetMessagesUseCase(fakeChatRepository)
        sendMediaMessageUseCase = SendMediaMessageUseCase(fakeChatRepository)
    }

    @Test
    fun `empty or blank message is rejected`() = runBlocking {
        val emptyResult = sendMessageUseCase("convo_1", "")
        assertTrue(emptyResult.isFailure)
        assertTrue(emptyResult.exceptionOrNull()?.message?.contains("فارغة") == true)

        val blankResult = sendMessageUseCase("convo_1", "    ")
        assertTrue(blankResult.isFailure)
    }

    @Test
    fun `oversized message exceeding 5000 chars is rejected`() = runBlocking {
        val hugeText = "أ".repeat(5001)
        val result = sendMessageUseCase("convo_1", hugeText)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("طويلة") == true)
    }

    @Test
    fun `valid message is trimmed and sent successfully`() = runBlocking {
        fakeChatRepository.conversations["convo_1"] = Conversation(id = "convo_1", type = "direct", title = "أحمد")
        val result = sendMessageUseCase("convo_1", "   مرحباً بك في تواصل   ")
        assertTrue(result.isSuccess)
        val message = result.getOrNull()!!
        assertEquals("مرحباً بك في تواصل", message.text)
        assertEquals(MessageStatus.SENT, message.status)
        assertEquals(MessageType.TEXT, message.type)
    }

    @Test
    fun `create or get direct conversation prevents duplicate conversations`() = runBlocking {
        val otherUserId = "user_456"
        val firstResult = getOrCreateConversationUseCase(otherUserId)
        assertTrue(firstResult.isSuccess)
        val firstConvoId = firstResult.getOrNull()!!

        // Calling again for the same otherUserId must return identical conversation ID
        val secondResult = getOrCreateConversationUseCase(otherUserId)
        assertTrue(secondResult.isSuccess)
        val secondConvoId = secondResult.getOrNull()!!

        assertEquals(firstConvoId, secondConvoId)
        assertEquals(1, fakeChatRepository.directConvoPairs.size)
    }

    @Test
    fun `cannot start conversation with invalid or self id`() = runBlocking {
        val blankResult = getOrCreateConversationUseCase("   ")
        assertTrue(blankResult.isFailure)

        val selfResult = getOrCreateConversationUseCase("self_id")
        assertTrue(selfResult.isFailure)
        assertTrue(selfResult.exceptionOrNull()?.message?.contains("نفسك") == true)
    }

    @Test
    fun `mark conversation as read resets unread count`() = runBlocking {
        val convoId = "convo_test"
        fakeChatRepository.conversations[convoId] = Conversation(
            id = convoId,
            type = "direct",
            title = "محمد",
            unreadCount = 5
        )

        assertEquals(5, fakeChatRepository.conversations[convoId]?.unreadCount)
        val result = markConversationAsReadUseCase(convoId)
        assertTrue(result.isSuccess)
        assertEquals(0, fakeChatRepository.conversations[convoId]?.unreadCount)
        assertEquals(1, fakeChatRepository.markAsReadCallCount)
    }

    @Test
    fun `messages are ordered chronologically and respect pagination limit`() = runBlocking {
        val convoId = "convo_pagination"
        fakeChatRepository.conversations[convoId] = Conversation(id = convoId, type = "direct", title = "سارة")

        // Send 10 messages
        for (i in 1..10) {
            sendMessageUseCase(convoId, "رسالة $i")
        }

        // Request latest 5 messages
        var retrieved: List<Message> = emptyList()
        getMessagesUseCase(convoId, limit = 5).collect { retrieved = it }

        assertEquals(5, retrieved.size)
        // Verify chronological order (older first, newest last)
        for (i in 0 until retrieved.size - 1) {
            assertFalse(retrieved[i].createdAt.isAfter(retrieved[i + 1].createdAt))
        }
    }

    @Test
    fun `sendMediaMessage successfully sends audio voice note with duration`() = runBlocking {
        val convoId = "convo_audio"
        val audioBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val result = sendMediaMessageUseCase(
            conversationId = convoId,
            fileBytes = audioBytes,
            fileName = "voice_note.m4a",
            mimeType = "audio/m4a",
            messageType = MessageType.AUDIO,
            durationSeconds = 25
        )

        assertTrue(result.isSuccess)
        val msg = result.getOrNull()!!
        assertEquals(MessageType.AUDIO, msg.type)
        assertEquals(25, msg.mediaDurationSeconds)
        assertEquals(MessageStatus.SENT, msg.status)
        assertTrue(msg.mediaUrl?.contains("voice_note.m4a") == true)
    }

    @Test
    fun `sendMediaMessage successfully sends image with caption`() = runBlocking {
        val convoId = "convo_image"
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val result = sendMediaMessageUseCase(
            conversationId = convoId,
            fileBytes = imageBytes,
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            messageType = MessageType.IMAGE,
            caption = "صورة توضيحية من تطبيق تواصل"
        )

        assertTrue(result.isSuccess)
        val msg = result.getOrNull()!!
        assertEquals(MessageType.IMAGE, msg.type)
        assertEquals("صورة توضيحية من تطبيق تواصل", msg.text)
        assertEquals(MessageStatus.SENT, msg.status)
        assertTrue(msg.mediaUrl?.contains("photo.jpg") == true)
    }

    @Test
    fun `sendMediaMessage fails when file bytes are empty`() = runBlocking {
        val result = sendMediaMessageUseCase(
            conversationId = "convo_empty",
            fileBytes = byteArrayOf(),
            fileName = "empty.jpg",
            mimeType = "image/jpeg",
            messageType = MessageType.IMAGE
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("فارغ") == true)
    }
}
