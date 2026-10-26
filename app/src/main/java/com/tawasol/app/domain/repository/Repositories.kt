package com.tawasol.app.domain.repository

import com.tawasol.app.domain.model.Conversation
import com.tawasol.app.domain.model.Message
import com.tawasol.app.domain.model.MessageType
import com.tawasol.app.domain.model.PrivacySettings
import com.tawasol.app.domain.model.SafetyNumber
import com.tawasol.app.domain.model.User
import com.tawasol.app.domain.model.UserStatus
import com.tawasol.app.domain.model.CallSession
import com.tawasol.app.domain.model.CallType
import com.tawasol.app.domain.model.FriendRequest
import com.tawasol.app.domain.model.FriendshipStatus
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(loginIdentifier: String, password: String): Result<User>
    suspend fun register(
        username: String,
        password: String,
        displayName: String,
        phone: String? = null,
        email: String? = null
    ): Result<User>
    suspend fun getCurrentUser(): User?
    suspend fun restoreSession(): Result<User?>
    suspend fun logout(): Result<Unit>
    val currentUserId: String?
}

interface UserRepository {
    fun getUser(userId: String): Flow<User?>
    suspend fun getUserProfile(userId: String): Result<User>
    suspend fun searchUsers(query: String): Result<List<User>>
    suspend fun updateProfile(userId: String, displayName: String, bio: String?): Result<User>
    suspend fun uploadAvatar(userId: String, imageBytes: ByteArray, mimeType: String): Result<String>
    fun getPrivacySettings(userId: String): Flow<PrivacySettings?>
    suspend fun updatePrivacySettings(settings: PrivacySettings): Result<Unit>
    suspend fun updatePresence(isOnline: Boolean): Result<Unit>
}

interface ChatRepository {
    fun getConversations(): Flow<List<Conversation>>
    fun getMessages(conversationId: String, limit: Int = 50): Flow<List<Message>>
    fun getConversation(conversationId: String): Flow<Conversation?>
    suspend fun getOrCreateDirectConversation(otherUserId: String): Result<String>
    suspend fun createGroup(title: String, description: String?, memberUserIds: List<String>): Result<String>
    suspend fun addGroupMember(conversationId: String, userId: String): Result<Unit>
    suspend fun removeGroupMember(conversationId: String, userId: String): Result<Unit>
    suspend fun getGroupMembers(conversationId: String): Result<List<User>>
    suspend fun sendMessage(
        conversationId: String,
        text: String?,
        replyToId: String? = null
    ): Result<Message>
    suspend fun sendMediaMessage(
        conversationId: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        messageType: MessageType,
        caption: String? = null,
        replyToId: String? = null,
        durationSeconds: Int? = null
    ): Result<Message>
    suspend fun markConversationAsRead(conversationId: String): Result<Unit>
    suspend fun fetchOlderMessages(
        conversationId: String,
        beforeTimestamp: java.time.Instant,
        limit: Int = 30
    ): Result<Int>
    fun startRealtimeMessagesSubscription(conversationId: String): Flow<Message>
    suspend fun syncConversations(): Result<Unit>
    suspend fun syncOutbox(): Result<Unit>
    suspend fun ensureE2EESetup(): Result<Unit>
    suspend fun getSafetyNumber(peerUserId: String): Result<SafetyNumber>
}

interface StatusRepository {
    fun getActiveStatuses(): Flow<List<UserStatus>>
    suspend fun postTextStatus(text: String): Result<UserStatus>
    suspend fun postMediaStatus(imageBytes: ByteArray, caption: String?): Result<UserStatus>
    suspend fun deleteStatus(statusId: String): Result<Unit>
    suspend fun markStatusViewed(statusId: String): Result<Unit>
}

interface FriendshipRepository {
    suspend fun sendFriendRequest(targetUserId: String): Result<FriendRequest>
    suspend fun acceptFriendRequest(requestId: String): Result<Unit>
    suspend fun acceptFriendRequestFromUser(senderUserId: String): Result<Unit>
    suspend fun rejectFriendRequest(requestId: String): Result<Unit>
    suspend fun cancelFriendRequest(requestId: String): Result<Unit>
    suspend fun removeFriend(friendUserId: String): Result<Unit>
    suspend fun getFriendshipStatus(otherUserId: String): Result<FriendshipStatus>
    fun getPendingIncomingRequests(): Flow<List<FriendRequest>>
    fun getPendingOutgoingRequests(): Flow<List<FriendRequest>>
    fun getFriends(): Flow<List<User>>
}

interface CallRepository {
    suspend fun startCall(receiverId: String, conversationId: String?, type: CallType): Result<CallSession>
    suspend fun acceptCall(callId: String, sdpAnswerJson: String? = null): Result<Unit>
    suspend fun rejectCall(callId: String): Result<Unit>
    suspend fun endCall(callId: String): Result<Unit>
    suspend fun updateSdpOffer(callId: String, sdpOfferJson: String): Result<Unit>
    suspend fun updateSdpAnswer(callId: String, sdpAnswerJson: String): Result<Unit>
    suspend fun addIceCandidate(callId: String, candidateJson: String, isCaller: Boolean): Result<Unit>
    fun getActiveCallFlow(callId: String): Flow<CallSession?>
    fun getIncomingCallsFlow(): Flow<CallSession?>
    fun getCallHistory(): Flow<List<CallSession>>
}
