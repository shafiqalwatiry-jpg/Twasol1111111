package com.tawasol.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tawasol.app.core.database.entity.MessageEntity
import com.tawasol.app.core.database.entity.OutboxMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM (SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt DESC LIMIT :limit) ORDER BY createdAt ASC")
    fun getMessagesPaged(conversationId: String, limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND (createdAt < :beforeTimestamp OR (createdAt = :beforeTimestamp AND id < :beforeId)) ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun getOlderMessages(conversationId: String, beforeTimestamp: java.time.Instant, beforeId: String, limit: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND senderId != :currentUserId AND status != 'read'")
    suspend fun getUnreadCount(conversationId: String, currentUserId: String): Int

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    // Outbox Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutboxMessage(outboxMessage: OutboxMessageEntity)

    @Query("SELECT * FROM outbox_messages ORDER BY createdAt ASC")
    suspend fun getAllPendingOutboxMessages(): List<OutboxMessageEntity>

    @Query("DELETE FROM outbox_messages WHERE localId = :localId")
    suspend fun deleteOutboxMessage(localId: String)
}
