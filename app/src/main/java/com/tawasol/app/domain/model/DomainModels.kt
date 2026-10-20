package com.tawasol.app.domain.model

import java.time.Instant

data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val phone: String? = null, // Only visible to the user themselves or authorized admin
    val email: String? = null, // Only visible to the user themselves
    val isOnline: Boolean = false,
    val lastSeen: Instant? = null,
    val createdAt: Instant = Instant.now()
)

enum class VisibilityOption(val titleArabic: String) {
    EVERYONE("الجميع"),
    CONTACTS("جهات الاتصال"),
    NOBODY("لا أحد");

    companion object {
        fun fromString(value: String?): VisibilityOption {
            return when (value?.lowercase()) {
                "everyone" -> EVERYONE
                "contacts" -> CONTACTS
                "nobody" -> NOBODY
                else -> EVERYONE
            }
        }
    }
}

data class PrivacySettings(
    val userId: String,
    val lastSeenVisibility: VisibilityOption = VisibilityOption.EVERYONE,
    val onlineStatusVisibility: VisibilityOption = VisibilityOption.EVERYONE,
    val profilePhotoVisibility: VisibilityOption = VisibilityOption.EVERYONE,
    val bioVisibility: VisibilityOption = VisibilityOption.EVERYONE,
    val readReceiptsEnabled: Boolean = true
)

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    FILE
}

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val text: String?,
    val type: MessageType = MessageType.TEXT,
    val status: MessageStatus = MessageStatus.SENT,
    val mediaUrl: String? = null,
    val localFilePath: String? = null,
    val replyToMessageId: String? = null,
    val createdAt: Instant = Instant.now(),
    val isEncrypted: Boolean = true,
    val mediaDurationSeconds: Int? = null
)

data class Conversation(
    val id: String,
    val type: String, // "direct" or "group"
    val title: String,
    val avatarUrl: String? = null,
    val peerUserId: String? = null,
    val peerUsername: String? = null,
    val lastMessage: String? = null,
    val lastMessageTime: Instant? = null,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false
)

data class SafetyNumber(
    val formattedNumber: String,
    val peerUsername: String,
    val peerDisplayName: String,
    val isVerified: Boolean = false
)

