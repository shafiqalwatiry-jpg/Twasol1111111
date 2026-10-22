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

data class UserStatus(
    val id: String,
    val userId: String,
    val userDisplayName: String = "",
    val userAvatarUrl: String? = null,
    val text: String? = null,
    val mediaUrl: String? = null,
    val statusType: String = "text", // "text", "image", "video"
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant = Instant.now().plusSeconds(86400),
    val viewCount: Int = 0,
    val isSelf: Boolean = false,
    val backgroundColor: String? = null,
    val isViewedByMe: Boolean = false
) {
    val content: String? get() = text
    val mediaType: String get() = statusType
    val viewsCount: Int get() = viewCount
}

data class FriendRequest(
    val id: String,
    val senderId: String,
    val senderUsername: String = "",
    val senderDisplayName: String = "",
    val senderAvatarUrl: String? = null,
    val receiverId: String,
    val receiverUsername: String = "",
    val receiverDisplayName: String = "",
    val receiverAvatarUrl: String? = null,
    val status: String = "pending", // "pending", "accepted", "rejected", "cancelled"
    val createdAt: Instant = Instant.now()
)

enum class FriendshipStatus {
    NONE,
    REQUEST_SENT,
    REQUEST_RECEIVED,
    FRIENDS,
    BLOCKED
}

enum class CallType {
    VOICE,
    VIDEO
}

enum class CallStatus {
    OUTGOING,
    INCOMING,
    RINGING,
    ACCEPTED,
    REJECTED,
    BUSY,
    MISSED,
    ENDED
}

data class CallSession(
    val id: String,
    val callerId: String,
    val callerDisplayName: String = "",
    val callerAvatarUrl: String? = null,
    val receiverId: String,
    val receiverDisplayName: String = "",
    val receiverAvatarUrl: String? = null,
    val conversationId: String? = null,
    val type: CallType = CallType.VOICE,
    val status: CallStatus = CallStatus.OUTGOING,
    val sdpOffer: String? = null,
    val sdpAnswer: String? = null,
    val startedAt: Instant? = null,
    val endedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val durationSeconds: Long = 0L
)

