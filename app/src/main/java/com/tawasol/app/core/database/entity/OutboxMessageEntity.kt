package com.tawasol.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Outbox queue for offline-first message synchronization.
 * Messages created when offline are stored here and retried automatically.
 */
@Entity(tableName = "outbox_messages")
data class OutboxMessageEntity(
    @PrimaryKey
    val localId: String,
    val conversationId: String,
    val text: String?,
    val messageType: String,
    val localFilePath: String? = null,
    val replyToMessageId: String? = null,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Instant = Instant.now()
)
