package com.tawasol.app.domain.usecase

import com.tawasol.app.domain.model.Conversation
import com.tawasol.app.domain.model.Message
import com.tawasol.app.domain.model.MessageType
import com.tawasol.app.domain.model.PrivacySettings
import com.tawasol.app.domain.model.SafetyNumber
import com.tawasol.app.domain.model.User
import com.tawasol.app.domain.repository.AuthRepository
import com.tawasol.app.domain.repository.ChatRepository
import com.tawasol.app.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow

class LoginUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(loginIdentifier: String, password: String): Result<User> {
        val cleanIdentifier = loginIdentifier.trim()
        if (cleanIdentifier.isEmpty()) {
            return Result.failure(IllegalArgumentException("يرجى إدخال اسم المستخدم أو البريد الإلكتروني"))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("كلمة المرور يجب ألا تقل عن 6 أحرف"))
        }
        return authRepository.login(cleanIdentifier, password)
    }
}

class RegisterUseCase(private val authRepository: AuthRepository) {
    private val reservedUsernames = setOf(
        "admin", "administrator", "support", "tawasol", "help",
        "root", "system", "moderator", "official", "security", "bot"
    )
    private val usernameRegex = "^[a-zA-Z0-9_]{3,30}$".toRegex()

    suspend operator fun invoke(
        username: String,
        password: String,
        displayName: String,
        phone: String? = null,
        email: String? = null
    ): Result<User> {
        val cleanUsername = username.trim().removePrefix("@").lowercase()

        if (cleanUsername.length < 3 || cleanUsername.length > 30) {
            return Result.failure(IllegalArgumentException("اسم المستخدم يجب أن يكون بين 3 إلى 30 حرفاً"))
        }

        if (!cleanUsername.matches(usernameRegex)) {
            return Result.failure(IllegalArgumentException("اسم المستخدم يجب أن يحتوي فقط على أحرف إنجليزية وأرقام والشرطة السفلية (_)"))
        }

        if (reservedUsernames.contains(cleanUsername)) {
            return Result.failure(IllegalArgumentException("اسم المستخدم محجوز لنظام تواصل، يرجى اختيار اسم آخر"))
        }

        if (password.length < 8) {
            return Result.failure(IllegalArgumentException("كلمة المرور يجب أن تكون 8 أحرف على الأقل لحماية حسابك"))
        }

        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        if (!hasLetter || !hasDigit) {
            return Result.failure(IllegalArgumentException("كلمة المرور يجب أن تحتوي على مزيج من الحروف والأرقام"))
        }

        if (displayName.trim().isEmpty()) {
            return Result.failure(IllegalArgumentException("يرجى إدخال الاسم الظاهر"))
        }

        val cleanPhone = phone?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanPhone != null && cleanPhone.length < 7) {
            return Result.failure(IllegalArgumentException("يرجى التأكد من صحة رقم الهاتف المدخل"))
        }

        val cleanEmail = email?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanEmail != null && (!cleanEmail.contains("@") || !cleanEmail.contains("."))) {
            return Result.failure(IllegalArgumentException("صيغة البريد الإلكتروني غير صحيحة"))
        }

        return authRepository.register(
            username = cleanUsername,
            password = password,
            displayName = displayName.trim(),
            phone = cleanPhone,
            email = cleanEmail
        )
    }
}

class SearchUsersUseCase(private val userRepository: UserRepository) {
    suspend operator fun invoke(query: String): Result<List<User>> {
        val cleanQuery = query.trim().removePrefix("@")
        if (cleanQuery.isEmpty()) {
            return Result.success(emptyList<User>())
        }
        return userRepository.searchUsers(cleanQuery)
    }
}

class UpdateProfileUseCase(private val userRepository: UserRepository) {
    suspend operator fun invoke(userId: String, displayName: String, bio: String?): Result<User> {
        if (displayName.trim().isEmpty()) {
            return Result.failure(IllegalArgumentException("الاسم الظاهر لا يمكن أن يكون فارغاً"))
        }
        return userRepository.updateProfile(userId, displayName.trim(), bio?.trim())
    }
}

class UploadAvatarUseCase(private val userRepository: UserRepository) {
    suspend operator fun invoke(userId: String, imageBytes: ByteArray, mimeType: String): Result<String> {
        if (imageBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("الملف المختار فارغ"))
        }
        return userRepository.uploadAvatar(userId, imageBytes, mimeType)
    }
}

class GetPrivacySettingsUseCase(private val userRepository: UserRepository) {
    operator fun invoke(userId: String): Flow<PrivacySettings?> {
        return userRepository.getPrivacySettings(userId)
    }
}

class UpdatePrivacySettingsUseCase(private val userRepository: UserRepository) {
    suspend operator fun invoke(settings: PrivacySettings): Result<Unit> {
        return userRepository.updatePrivacySettings(settings)
    }
}

class GetConversationsUseCase(private val chatRepository: ChatRepository) {
    operator fun invoke(): Flow<List<Conversation>> {
        return chatRepository.getConversations()
    }
}

class SendMessageUseCase(private val chatRepository: ChatRepository) {
    suspend operator fun invoke(conversationId: String, text: String): Result<Message> {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            return Result.failure(IllegalArgumentException("لا يمكن إرسال رسالة فارغة"))
        }
        if (cleanText.length > 5000) {
            return Result.failure(IllegalArgumentException("الرسالة طويلة جداً، الحد الأقصى 5000 حرف"))
        }
        return chatRepository.sendMessage(conversationId, cleanText)
    }
}

class SendMediaMessageUseCase(private val chatRepository: ChatRepository) {
    suspend operator fun invoke(
        conversationId: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        messageType: MessageType,
        caption: String? = null,
        durationSeconds: Int? = null
    ): Result<Message> {
        if (fileBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("الملف المختار فارغ"))
        }
        if (fileBytes.size > 25 * 1024 * 1024) {
            return Result.failure(IllegalArgumentException("حجم الملف يتجاوز الحد الأقصى المسموح (25 ميجابايت)"))
        }
        return chatRepository.sendMediaMessage(conversationId, fileBytes, fileName, mimeType, messageType, caption, durationSeconds = durationSeconds)
    }
}

class GetMessagesUseCase(private val chatRepository: ChatRepository) {
    operator fun invoke(conversationId: String, limit: Int = 50): Flow<List<Message>> {
        return chatRepository.getMessages(conversationId, limit)
    }
}

class GetConversationUseCase(private val chatRepository: ChatRepository) {
    operator fun invoke(conversationId: String): Flow<Conversation?> {
        return chatRepository.getConversation(conversationId)
    }
}

class GetOrCreateConversationUseCase(private val chatRepository: ChatRepository) {
    suspend operator fun invoke(otherUserId: String): Result<String> {
        if (otherUserId.isBlank()) {
            return Result.failure(IllegalArgumentException("معرف المستخدم غير صحيح"))
        }
        return chatRepository.getOrCreateDirectConversation(otherUserId.trim())
    }
}

class MarkConversationAsReadUseCase(private val chatRepository: ChatRepository) {
    suspend operator fun invoke(conversationId: String): Result<Unit> {
        return chatRepository.markConversationAsRead(conversationId)
    }
}

class CreateGroupUseCase(private val chatRepository: ChatRepository) {
    suspend operator fun invoke(title: String, description: String?, memberUserIds: List<String>): Result<String> {
        if (title.trim().isEmpty()) {
            return Result.failure(IllegalArgumentException("اسم المجموعة لا يمكن أن يكون فارغاً"))
        }
        return chatRepository.createGroup(title.trim(), description?.trim(), memberUserIds)
    }
}

class AddGroupMemberUseCase(private val chatRepository: ChatRepository) {
    suspend operator fun invoke(conversationId: String, userId: String): Result<Unit> {
        if (conversationId.isBlank() || userId.isBlank()) {
            return Result.failure(IllegalArgumentException("بيانات غير صحيحة"))
        }
        return chatRepository.addGroupMember(conversationId, userId)
    }
}

class RemoveGroupMemberUseCase(private val chatRepository: ChatRepository) {
    suspend operator fun invoke(conversationId: String, userId: String): Result<Unit> {
        if (conversationId.isBlank() || userId.isBlank()) {
            return Result.failure(IllegalArgumentException("بيانات غير صحيحة"))
        }
        return chatRepository.removeGroupMember(conversationId, userId)
    }
}

class GetGroupMembersUseCase(private val chatRepository: ChatRepository) {
    suspend operator fun invoke(conversationId: String): Result<List<User>> {
        if (conversationId.isBlank()) {
            return Result.failure(IllegalArgumentException("معرف المجموعة غير صحيح"))
        }
        return chatRepository.getGroupMembers(conversationId)
    }
}

class EnsureE2EESetupUseCase(private val chatRepository: ChatRepository) {
    suspend operator fun invoke(): Result<Unit> {
        return chatRepository.ensureE2EESetup()
    }
}

class GetSafetyNumberUseCase(private val chatRepository: ChatRepository) {
    suspend operator fun invoke(peerUserId: String): Result<SafetyNumber> {
        if (peerUserId.isBlank()) {
            return Result.failure(IllegalArgumentException("معرف المستخدم غير صحيح"))
        }
        return chatRepository.getSafetyNumber(peerUserId)
    }
}

