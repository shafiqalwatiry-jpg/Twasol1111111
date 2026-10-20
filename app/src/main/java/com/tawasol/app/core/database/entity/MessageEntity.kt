package com.tawasol.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["createdAt"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String?,
    val messageType: String = "text", // text, image, video, audio, file
    val status: String = "sent", // sending, sent, delivered, read, failed
    val replyToMessageId: String? = null,
    val mediaUrl: String? = null,
    val localFilePath: String? = null,
    val fileSize: Long? = null,
    val durationSeconds: Int? = null,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
