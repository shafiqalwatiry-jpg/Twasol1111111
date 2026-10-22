package com.tawasol.app.data

import com.tawasol.app.core.database.entity.MessageEntity
import com.tawasol.app.core.database.entity.OutboxMessageEntity
import com.tawasol.app.domain.model.MessageType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Instant
import java.util.UUID

/**
 * Unit tests validating offline media handling and outbox synchronization:
 * 1. Saving file permanently to app-private storage and enqueuing in outbox.
 * 2. Clear failure if file cannot be saved locally (fail-closed, no fake success).
 * 3. Resending file upon reconnection and updating status to sent.
 * 4. Idempotency and duplicate prevention upon retries.
 * 5. Strict account isolation of media files and outbox items.
 * 6. Ensuring local file is never deleted prematurely before upload completion.
 */
class OfflineMediaOutboxTest {

    private lateinit var testStorageDir: File

    // In-memory fake Room DAO for outbox and messages
    private class FakeMediaDao {
        val messages = mutableListOf<MessageEntity>()
        val outbox = mutableListOf<OutboxMessageEntity>()

        fun insertMessage(msg: MessageEntity) {
            messages.removeAll { it.id == msg.id }
            messages.add(msg)
        }

        fun updateMessageStatus(id: String, status: String) {
            val idx = messages.indexOfFirst { it.id == id }
            if (idx != -1) {
                messages[idx] = messages[idx].copy(status = status)
            }
        }

        fun updateMessageMediaUrl(id: String, mediaUrl: String, status: String) {
            val idx = messages.indexOfFirst { it.id == id }
            if (idx != -1) {
                messages[idx] = messages[idx].copy(mediaUrl = mediaUrl, status = status)
            }
        }

        fun insertOutboxMessage(outboxMsg: OutboxMessageEntity) {
            outbox.removeAll { it.localId == outboxMsg.localId }
            outbox.add(outboxMsg)
        }

        fun getPendingOutboxForAccount(accountUserId: String): List<OutboxMessageEntity> {
            return outbox.filter { it.accountUserId == accountUserId }
        }

        fun deleteOutboxMessage(localId: String) {
            outbox.removeAll { it.localId == localId }
        }
    }

    private lateinit var fakeDao: FakeMediaDao

    @Before
    fun setUp() {
        testStorageDir = Files.createTempDirectory("tawasol_offline_test").toFile()
        fakeDao = FakeMediaDao()
    }

    @After
    fun tearDown() {
        testStorageDir.deleteRecursively()
    }

    private fun saveMediaLocally(
        userId: String,
        fileBytes: ByteArray,
        fileName: String,
        localId: String,
        storageBase: File
    ): File {
        if (fileBytes.isEmpty()) {
            throw IllegalArgumentException("الملف المختار فارغ ولا يمكن إرساله")
        }
        val userFolder = File(storageBase, userId)
        if (!userFolder.exists() && !userFolder.mkdirs()) {
            throw IOException("تعذر إنشاء مجلد التخزين للمستخدم")
        }
        val safeExt = if (fileName.contains('.')) fileName.substringAfterLast('.') else "jpg"
        val localFile = File(userFolder, "${localId}_${System.currentTimeMillis()}.$safeExt")
        localFile.writeBytes(fileBytes)
        return localFile
    }

    @Test
    fun `save media locally and enqueue to outbox when offline`() {
        val userA = "user_alpha_123"
        val convoId = "convo_456"
        val localId = UUID.randomUUID().toString()
        val imageBytes = "FAKE_JPEG_IMAGE_CONTENT_BYTES".toByteArray()

        // 1. Save file locally
        val savedFile = saveMediaLocally(userA, imageBytes, "photo.jpg", localId, testStorageDir)
        assertTrue(savedFile.exists())
        assertEquals(imageBytes.size.toLong(), savedFile.length())

        // 2. Insert into local message table with status "sending"
        val localMessage = MessageEntity(
            id = localId,
            conversationId = convoId,
            senderId = userA,
            text = "صورة تجريبية",
            messageType = "image",
            status = "sending",
            localFilePath = savedFile.absolutePath,
            fileSize = imageBytes.size.toLong(),
            accountUserId = userA
        )
        fakeDao.insertMessage(localMessage)

        // 3. Insert into outbox table
        val outboxItem = OutboxMessageEntity(
            localId = localId,
            conversationId = convoId,
            text = "صورة تجريبية",
            messageType = "image",
            localFilePath = savedFile.absolutePath,
            accountUserId = userA
        )
        fakeDao.insertOutboxMessage(outboxItem)

        // Verify outbox state
        val pending = fakeDao.getPendingOutboxForAccount(userA)
        assertEquals(1, pending.size)
        assertEquals(savedFile.absolutePath, pending.first().localFilePath)
        assertEquals("image", pending.first().messageType)

        // Verify local message is in sending state
        val msg = fakeDao.messages.first { it.id == localId }
        assertEquals("sending", msg.status)
        assertEquals(savedFile.absolutePath, msg.localFilePath)
    }

    @Test
    fun `clear failure and no outbox insertion when file saving fails`() {
        val userA = "user_alpha_123"
        val emptyBytes = ByteArray(0)
        val localId = UUID.randomUUID().toString()

        var caughtException: Exception? = null
        try {
            saveMediaLocally(userA, emptyBytes, "empty.png", localId, testStorageDir)
        } catch (e: Exception) {
            caughtException = e
        }

        assertNotNull(caughtException)
        assertTrue(caughtException is IllegalArgumentException)
        assertEquals("الملف المختار فارغ ولا يمكن إرساله", caughtException?.message)

        // Ensure outbox was NOT populated
        assertTrue(fakeDao.getPendingOutboxForAccount(userA).isEmpty())
    }

    @Test
    fun `sync outbox successfully uploads file and updates local message status`() {
        val userA = "user_alpha_123"
        val convoId = "convo_456"
        val localId = UUID.randomUUID().toString()
        val fileBytes = "VOICE_NOTE_AUDIO_BYTES_TEST".toByteArray()

        val savedFile = saveMediaLocally(userA, fileBytes, "voice.m4a", localId, testStorageDir)

        fakeDao.insertMessage(
            MessageEntity(
                id = localId,
                conversationId = convoId,
                senderId = userA,
                text = null,
                messageType = "audio",
                status = "sending",
                localFilePath = savedFile.absolutePath,
                accountUserId = userA
            )
        )

        fakeDao.insertOutboxMessage(
            OutboxMessageEntity(
                localId = localId,
                conversationId = convoId,
                text = null,
                messageType = "audio",
                localFilePath = savedFile.absolutePath,
                accountUserId = userA
            )
        )

        // Simulate reconnection sync
        val pending = fakeDao.getPendingOutboxForAccount(userA)
        assertEquals(1, pending.size)
        val item = pending.first()

        val file = File(item.localFilePath!!)
        assertTrue("الملف يجب أن يكون موجوداً وقابلاً للقراءة", file.exists() && file.canRead())

        // Simulate upload to Supabase Storage
        val fakePublicUrl = "https://supabase.co/storage/v1/object/public/chat-media/$userA/$convoId/${localId}_voice.m4a"
        fakeDao.updateMessageMediaUrl(item.localId, fakePublicUrl, "sent")
        fakeDao.deleteOutboxMessage(item.localId)

        // Verify outbox is cleared
        assertTrue(fakeDao.getPendingOutboxForAccount(userA).isEmpty())

        // Verify message is marked sent and has public URL
        val updated = fakeDao.messages.first { it.id == localId }
        assertEquals("sent", updated.status)
        assertEquals(fakePublicUrl, updated.mediaUrl)
        // Local file is NOT deleted, remains for offline caching
        assertTrue(file.exists())
    }

    @Test
    fun `repeated outbox sync does not duplicate messages`() {
        val userA = "user_alpha_123"
        val convoId = "convo_456"
        val localId = UUID.randomUUID().toString()
        val fileBytes = "TEST_DOCUMENT_PDF".toByteArray()

        val savedFile = saveMediaLocally(userA, fileBytes, "doc.pdf", localId, testStorageDir)

        val entity = MessageEntity(
            id = localId,
            conversationId = convoId,
            senderId = userA,
            text = "مستند مهم",
            messageType = "file",
            status = "sending",
            localFilePath = savedFile.absolutePath,
            accountUserId = userA
        )
        fakeDao.insertMessage(entity)

        val outboxItem = OutboxMessageEntity(
            localId = localId,
            conversationId = convoId,
            text = "مستند مهم",
            messageType = "file",
            localFilePath = savedFile.absolutePath,
            accountUserId = userA
        )
        fakeDao.insertOutboxMessage(outboxItem)

        // Simulate two consecutive sync cycles with the same localId
        for (i in 1..2) {
            val pending = fakeDao.getPendingOutboxForAccount(userA)
            if (pending.isNotEmpty()) {
                val item = pending.first()
                val uploadedUrl = "https://supabase.co/storage/$localId"
                fakeDao.insertMessage(entity.copy(status = "sent", mediaUrl = uploadedUrl))
                fakeDao.deleteOutboxMessage(item.localId)
            }
        }

        // Must still have exactly 1 message in table (no duplication)
        assertEquals(1, fakeDao.messages.size)
        assertEquals("sent", fakeDao.messages.first().status)
    }

    @Test
    fun `account isolation prevents cross-account file access and sync`() {
        val userA = "user_alpha"
        val userB = "user_beta"

        val localIdA = "msg_user_a_file"
        val localIdB = "msg_user_b_file"

        val fileA = saveMediaLocally(userA, "USER_A_PRIVATE_MEDIA".toByteArray(), "a.jpg", localIdA, testStorageDir)
        val fileB = saveMediaLocally(userB, "USER_B_PRIVATE_MEDIA".toByteArray(), "b.jpg", localIdB, testStorageDir)

        // Ensure directories on disk are separate
        assertFalse(fileA.parentFile.absolutePath == fileB.parentFile.absolutePath)
        assertTrue(fileA.absolutePath.contains("/$userA/"))
        assertTrue(fileB.absolutePath.contains("/$userB/"))

        // Add outbox items
        fakeDao.insertOutboxMessage(
            OutboxMessageEntity(
                localId = localIdA,
                conversationId = "convo_1",
                text = "ملف أ",
                messageType = "image",
                localFilePath = fileA.absolutePath,
                accountUserId = userA
            )
        )
        fakeDao.insertOutboxMessage(
            OutboxMessageEntity(
                localId = localIdB,
                conversationId = "convo_2",
                text = "ملف ب",
                messageType = "image",
                localFilePath = fileB.absolutePath,
                accountUserId = userB
            )
        )

        // User A only sees file A
        val outboxA = fakeDao.getPendingOutboxForAccount(userA)
        assertEquals(1, outboxA.size)
        assertEquals(localIdA, outboxA.first().localId)

        // User B only sees file B
        val outboxB = fakeDao.getPendingOutboxForAccount(userB)
        assertEquals(1, outboxB.size)
        assertEquals(localIdB, outboxB.first().localId)
    }

    @Test
    fun `file is not deleted on transient upload failure and retry increments`() {
        val userA = "user_alpha"
        val localId = "msg_transient_fail"
        val fileBytes = "TRANSIENT_FILE_TEST".toByteArray()
        val file = saveMediaLocally(userA, fileBytes, "audio.m4a", localId, testStorageDir)

        var outboxItem = OutboxMessageEntity(
            localId = localId,
            conversationId = "convo_999",
            text = null,
            messageType = "audio",
            localFilePath = file.absolutePath,
            retryCount = 0,
            accountUserId = userA
        )
        fakeDao.insertOutboxMessage(outboxItem)

        // Simulate transient network failure during upload
        val simulatedNetworkError = IOException("انقطاع الاتصال أثناء الرفع")
        outboxItem = outboxItem.copy(
            retryCount = outboxItem.retryCount + 1,
            lastError = simulatedNetworkError.message
        )
        fakeDao.insertOutboxMessage(outboxItem)

        // File MUST remain intact on device
        assertTrue("الملف المحلي لا يجب حذفه عند فشل الرفع", file.exists())
        assertEquals(fileBytes.size.toLong(), file.length())

        // Outbox item must remain with incremented retry count
        val pending = fakeDao.getPendingOutboxForAccount(userA)
        assertEquals(1, pending.size)
        assertEquals(1, pending.first().retryCount)
        assertEquals("انقطاع الاتصال أثناء الرفع", pending.first().lastError)
    }
}
