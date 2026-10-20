package com.tawasol.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val type: String, // "direct" or "group"
    val title: String,
    val avatarUrl: String? = null,
    val peerUserId: String? = null,
    val peerUsername: String? = null,
    val lastMessageText: String? = null,
    val lastMessageTimestamp: Instant? = null,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
