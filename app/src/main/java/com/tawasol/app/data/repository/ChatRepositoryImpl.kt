package com.tawasol.app.data.repository

import com.tawasol.app.core.database.TawasolDatabase
import com.tawasol.app.core.database.entity.ConversationEntity
import com.tawasol.app.core.database.entity.MessageEntity
import com.tawasol.app.core.database.entity.OutboxMessageEntity
import com.tawasol.app.core.network.NetworkMonitor
import com.tawasol.app.core.security.E2EEManager
import com.tawasol.app.core.security.KeystoreManager
import com.tawasol.app.core.supabase.SupabaseClientProvider
import com.tawasol.app.core.supabase.SupabaseConfig
import com.tawasol.app.domain.model.Conversation
import com.tawasol.app.domain.model.Message
import com.tawasol.app.domain.model.MessageStatus
import com.tawasol.app.domain.model.MessageType
import com.tawasol.app.domain.model.SafetyNumber
import com.tawasol.app.domain.model.User
import com.tawasol.app.domain.repository.ChatRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

@Serializable
data class RemoteMessageDto(
    val id: String,
    val conversation_id: String,
    val sender_id: String,
    val text: String?,
    val message_type: String = "text",
    val status: String = "sent",
    val reply_to_message_id: String? = null,
    val media_url: String? = null,
    val created_at: String
)

@Serializable
data class RemoteConversationMemberDto(
    val conversation_id: String,
    val user_id: String,
    val role: String = "member",
    val last_read_at: String? = null
)

@Serializable
data class RemoteConversationDto(
    val id: String,
    val type: String = "direct",
    val title: String? = null,
    val avatar_url: String? = null,
    val last_message_text: String? = null,
    val last_message_at: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class RemoteUserE2eeKeyDto(
    val user_id: String,
    val public_key: String,
    val key_algorithm: String = "ECDH-P256",
    val created_at: String? = null,
    val updated_at: String? = null
)

class ChatRepositoryImpl(
    private val database: TawasolDatabase,
    private val supabaseProvider: SupabaseClientProvider,
    private val networkMonitor: NetworkMonitor,
    private val e2eeManager: E2EEManager,
    private val keystoreManager: KeystoreManager,
    private val currentUserIdProvider: () -> String?
) : ChatRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun getConversations(): Flow<List<Conversation>> {
        return database.conversationDao().getAllConversations().map { entities ->
            entities.map { entity ->
                Conversation(
                    id = entity.id,
                    type = entity.type,
                    title = entity.title,
                    avatarUrl = entity.avatarUrl,
                    peerUserId = entity.peerUserId,
                    peerUsername = entity.peerUsername,
                    lastMessage = entity.lastMessageText,
                    lastMessageTime = entity.lastMessageTimestamp,
                    unreadCount = entity.unreadCount,
                    isPinned = entity.isPinned,
                    isMuted = entity.isMuted
                )
            }
        }
    }

    override fun getConversation(conversationId: String): Flow<Conversation?> {
        return database.conversationDao().getConversationFlow(conversationId).map { entity ->
            entity?.let {
                Conversation(
                    id = it.id,
                    type = it.type,
                    title = it.title,
                    avatarUrl = it.avatarUrl,
                    peerUserId = it.peerUserId,
                    peerUsername = it.peerUsername,
                    lastMessage = it.lastMessageText,
                    lastMessageTime = it.lastMessageTimestamp,
                    unreadCount = it.unreadCount,
                    isPinned = it.isPinned,
                    isMuted = it.isMuted
                )
            }
        }
    }

    override fun getMessages(conversationId: String, limit: Int): Flow<List<Message>> {
        return database.messageDao().getMessagesPaged(conversationId, limit).map { entities ->
            entities.map { entity ->
                Message(
                    id = entity.id,
                    conversationId = entity.conversationId,
                    senderId = entity.senderId,
                    text = entity.text,
                    type = try { MessageType.valueOf(entity.messageType.uppercase()) } catch (e: Exception) { MessageType.TEXT },
                    status = try { MessageStatus.valueOf(entity.status.uppercase()) } catch (e: Exception) { MessageStatus.SENT },
                    mediaUrl = entity.mediaUrl,
                    localFilePath = entity.localFilePath,
                    replyToMessageId = entity.replyToMessageId,
                    createdAt = entity.createdAt,
                    mediaDurationSeconds = entity.durationSeconds
                )
            }
        }
    }

    override suspend fun getOrCreateDirectConversation(otherUserId: String): Result<String> = withContext(Dispatchers.IO) {
        val currentUserId = currentUserIdProvider()
            ?: return@withContext Result.failure(Exception("يجب تسجيل الدخول لبدء محادثة"))

        if (currentUserId == otherUserId) {
            return@withContext Result.failure(Exception("لا يمكن بدء محادثة مع نفسك"))
        }

        // 1. Check local database first
        val localConvo = database.conversationDao().getConversationByPeerUserId(otherUserId)
        if (localConvo != null) {
            return@withContext Result.success(localConvo.id)
        }

        // 2. Fetch peer profile details for name and avatar
        val peerProfile = try {
            supabaseProvider.postgrest.from(SupabaseConfig.PROFILES_TABLE)
                .select {
                    filter { eq("id", otherUserId) }
                }.decodeSingleOrNull<ProfileDto>()
        } catch (e: Exception) {
            database.userDao().getUserByIdSync(otherUserId)?.let {
                ProfileDto(
                    id = it.id,
                    username = it.username,
                    display_name = it.displayName,
                    avatar_url = it.avatarUrl,
                    bio = it.bio,
                    is_online = it.isOnline
                )
            }
        }

        val peerName = peerProfile?.display_name ?: "مستخدم"
        val peerUsername = peerProfile?.username ?: "user"
        val peerAvatar = peerProfile?.avatar_url

        // Pre-fetch peer E2EE public key in background
        launch {
            getOrFetchPeerPublicKey(otherUserId)
        }

        // 3. Call atomic RPC on Supabase
        try {
            val response = supabaseProvider.postgrest.rpc(
                "get_or_create_direct_conversation",
                buildJsonObject {
                    put("target_user_id", otherUserId)
                }
            ).decodeSingleOrNull<String>()

            val convoId = response ?: UUID.randomUUID().toString()

            // Save in Room
            val entity = ConversationEntity(
                id = convoId,
                type = "direct",
                title = peerName,
                avatarUrl = peerAvatar,
                peerUserId = otherUserId,
                peerUsername = peerUsername,
                lastMessageText = null,
                lastMessageTimestamp = Instant.now(),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            database.conversationDao().insertConversation(entity)

            Result.success(convoId)
        } catch (e: Exception) {
            // Offline fallback: generate local conversation ID
            val fallbackId = UUID.randomUUID().toString()
            val entity = ConversationEntity(
                id = fallbackId,
                type = "direct",
                title = peerName,
                avatarUrl = peerAvatar,
                peerUserId = otherUserId,
                peerUsername = peerUsername,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            database.conversationDao().insertConversation(entity)
            Result.success(fallbackId)
        }
    }

    override suspend fun sendMessage(
        conversationId: String,
        text: String?,
        replyToId: String?
    ): Result<Message> = withContext(Dispatchers.IO) {
        val currentUserId = currentUserIdProvider()
            ?: return@withContext Result.failure(Exception("يجب تسجيل الدخول لإرسال الرسالة"))

        val cleanText = text?.trim()
        if (cleanText.isNullOrEmpty()) {
            return@withContext Result.failure(Exception("لا يمكن إرسال رسالة فارغة"))
        }

        val localId = UUID.randomUUID().toString()
        val now = Instant.now()

        // 1. Immediately save into Room Local DB with status SENDING and plaintext for instant UI
        val localMessage = MessageEntity(
            id = localId,
            conversationId = conversationId,
            senderId = currentUserId,
            text = cleanText,
            messageType = "text",
            status = "sending",
            replyToMessageId = replyToId,
            createdAt = now
        )
        database.messageDao().insertMessage(localMessage)

        // Update local conversation preview
        database.conversationDao().updateLastMessage(conversationId, cleanText, now)

        // 2. Perform End-to-End Encryption if this is a 1-to-1 direct chat
        val convo = database.conversationDao().getConversationById(conversationId)
        val peerId = convo?.peerUserId
        val outboundText: String = if (convo?.type == "direct" && !peerId.isNullOrBlank()) {
            val peerPubKey = getOrFetchPeerPublicKey(peerId)
            if (!peerPubKey.isNullOrBlank()) {
                e2eeManager.encryptMessage(currentUserId, peerPubKey, cleanText).getOrDefault(cleanText)
            } else {
                cleanText
            }
        } else {
            cleanText
        }

        // 3. Check network connectivity
        val isOnline = try { networkMonitor.isOnline.first() } catch (e: Exception) { false }

        if (!isOnline) {
            // Queue encrypted payload into Outbox for automatic synchronization
            database.messageDao().insertOutboxMessage(
                OutboxMessageEntity(
                    localId = localId,
                    conversationId = conversationId,
                    text = outboundText,
                    messageType = "text",
                    replyToMessageId = replyToId
                )
            )
            return@withContext Result.success(
                Message(
                    id = localId,
                    conversationId = conversationId,
                    senderId = currentUserId,
                    text = cleanText,
                    status = MessageStatus.SENDING,
                    createdAt = now
                )
            )
        }

        // 4. Send encrypted payload to Supabase Postgrest backend
        try {
            val payload = buildJsonObject {
                put("id", localId)
                put("conversation_id", conversationId)
                put("sender_id", currentUserId)
                put("text", outboundText)
                put("message_type", "text")
                put("status", "sent")
                if (replyToId != null) put("reply_to_message_id", replyToId)
            }

            supabaseProvider.postgrest.from(SupabaseConfig.MESSAGES_TABLE)
                .insert(payload)

            // Update status in local DB to SENT
            database.messageDao().updateMessageStatus(localId, "sent")

            Result.success(
                Message(
                    id = localId,
                    conversationId = conversationId,
                    senderId = currentUserId,
                    text = cleanText,
                    status = MessageStatus.SENT,
                    createdAt = now
                )
            )
        } catch (e: Exception) {
            // Update status to failed and queue in outbox
            database.messageDao().insertOutboxMessage(
                OutboxMessageEntity(
                    localId = localId,
                    conversationId = conversationId,
                    text = outboundText,
                    messageType = "text",
                    lastError = e.message
                )
            )
            database.messageDao().updateMessageStatus(localId, "failed")
            Result.failure(Exception("تعذر إرسال الرسالة: ${e.localizedMessage ?: "تحقق من الاتصال"}"))
        }
    }

    override suspend fun sendMediaMessage(
        conversationId: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        messageType: MessageType,
        caption: String?,
        replyToId: String?,
        durationSeconds: Int?
    ): Result<Message> = withContext(Dispatchers.IO) {
        val currentUserId = currentUserIdProvider()
            ?: return@withContext Result.failure(Exception("يجب تسجيل الدخول لإرسال الملفات"))

        val localId = UUID.randomUUID().toString()
        val now = Instant.now()
        val typeStr = when (messageType) {
            MessageType.IMAGE -> "image"
            MessageType.AUDIO -> "audio"
            else -> "file"
        }

        val localMessage = MessageEntity(
            id = localId,
            conversationId = conversationId,
            senderId = currentUserId,
            text = caption,
            messageType = typeStr,
            status = "sending",
            replyToMessageId = replyToId,
            fileSize = fileBytes.size.toLong(),
            durationSeconds = durationSeconds,
            createdAt = now
        )
        database.messageDao().insertMessage(localMessage)
        val defaultLast = when (typeStr) {
            "image" -> "صورة"
            "audio" -> "رسالة صوتية"
            else -> fileName
        }
        database.conversationDao().updateLastMessage(conversationId, caption ?: defaultLast, now)

        // 2. Perform End-to-End Encryption on caption if direct chat
        val convo = database.conversationDao().getConversationById(conversationId)
        val peerId = convo?.peerUserId
        val outboundCaption: String = if (!caption.isNullOrBlank() && convo?.type == "direct" && !peerId.isNullOrBlank()) {
            val peerPubKey = getOrFetchPeerPublicKey(peerId)
            if (!peerPubKey.isNullOrBlank()) {
                e2eeManager.encryptMessage(currentUserId, peerPubKey, caption).getOrDefault(caption)
            } else {
                caption
            }
        } else {
            caption ?: ""
        }

        val isOnline = try { networkMonitor.isOnline.first() } catch (e: Exception) { false }
        if (!isOnline) {
            database.messageDao().insertOutboxMessage(
                OutboxMessageEntity(
                    localId = localId,
                    conversationId = conversationId,
                    text = outboundCaption,
                    messageType = typeStr,
                    replyToMessageId = replyToId
                )
            )
            return@withContext Result.success(
                Message(
                    id = localId,
                    conversationId = conversationId,
                    senderId = currentUserId,
                    text = caption,
                    type = messageType,
                    status = MessageStatus.SENDING,
                    createdAt = now
                )
            )
        }

        try {
            val storagePath = "$currentUserId/$conversationId/${System.currentTimeMillis()}_$fileName"
            val bucket = supabaseProvider.client.storage.from("chat-media")
            bucket.upload(path = storagePath, data = fileBytes) {
                upsert = true
            }
            val publicUrl = bucket.publicUrl(storagePath)

            val payload = buildJsonObject {
                put("id", localId)
                put("conversation_id", conversationId)
                put("sender_id", currentUserId)
                put("text", outboundCaption)
                put("message_type", typeStr)
                put("status", "sent")
                put("media_url", publicUrl)
                put("file_size", fileBytes.size.toLong())
                if (durationSeconds != null) put("duration_seconds", durationSeconds)
                if (replyToId != null) put("reply_to_message_id", replyToId)
            }

            supabaseProvider.postgrest.from(SupabaseConfig.MESSAGES_TABLE).insert(payload)
            database.messageDao().updateMessageStatus(localId, "sent")

            Result.success(
                Message(
                    id = localId,
                    conversationId = conversationId,
                    senderId = currentUserId,
                    text = caption,
                    type = messageType,
                    status = MessageStatus.SENT,
                    mediaUrl = publicUrl,
                    createdAt = now
                )
            )
        } catch (e: Exception) {
            database.messageDao().updateMessageStatus(localId, "failed")
            Result.failure(Exception("فشل إرسال الملف: ${e.localizedMessage ?: "تحقق من الاتصال"}"))
        }
    }

    override suspend fun markConversationAsRead(conversationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUserId = currentUserIdProvider() ?: return@withContext Result.success(Unit)

        // Update local Room database
        database.conversationDao().markAsRead(conversationId)

        // Update Supabase if online
        try {
            val payload = buildJsonObject {
                put("last_read_at", Instant.now().toString())
            }
            supabaseProvider.postgrest.from("conversation_members").update(payload) {
                filter {
                    eq("conversation_id", conversationId)
                    eq("user_id", currentUserId)
                }
            }
        } catch (e: Exception) {
            // Ignore offline errors for read receipts
        }

        Result.success(Unit)
    }

    override suspend fun fetchOlderMessages(
        conversationId: String,
        beforeTimestamp: Instant,
        limit: Int
    ): Result<Int> = withContext(Dispatchers.IO) {
        val currentUserId = currentUserIdProvider()
        try {
            val remoteMessages = supabaseProvider.postgrest.from(SupabaseConfig.MESSAGES_TABLE)
                .select {
                    filter {
                        eq("conversation_id", conversationId)
                        lt("created_at", beforeTimestamp.toString())
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                    limit(limit.toLong())
                }.decodeList<RemoteMessageDto>()

            if (remoteMessages.isEmpty()) {
                return@withContext Result.success(0)
            }

            val entities = remoteMessages.map { dto ->
                val rawText = dto.text
                val decryptedText = if (currentUserId != null && rawText?.startsWith(E2EEManager.PREFIX_V1) == true) {
                    e2eeManager.decryptMessage(currentUserId, rawText)
                } else {
                    rawText
                }

                MessageEntity(
                    id = dto.id,
                    conversationId = dto.conversation_id,
                    senderId = dto.sender_id,
                    text = decryptedText,
                    messageType = dto.message_type,
                    status = "sent",
                    replyToMessageId = dto.reply_to_message_id,
                    createdAt = parseInstantOrNow(dto.created_at)
                )
            }

            database.messageDao().insertMessages(entities)
            Result.success(entities.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun startRealtimeMessagesSubscription(conversationId: String): Flow<Message> = callbackFlow {
        val channel = supabaseProvider.realtime.channel("chat:$conversationId")

        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = SupabaseConfig.MESSAGES_TABLE
            filter("conversation_id", FilterOperator.EQ, conversationId)
        }

        val job = launch(Dispatchers.IO) {
            try {
                channel.subscribe()
                changeFlow.collect { action ->
                    if (action is PostgresAction.Insert) {
                        try {
                            val record = action.record
                            val dto = json.decodeFromJsonElement<RemoteMessageDto>(record)
                            val now = parseInstantOrNow(dto.created_at)
                            val currentUserId = currentUserIdProvider()

                            // Decrypt E2EE message if applicable
                            val rawText = dto.text
                            val decryptedText = if (currentUserId != null && rawText?.startsWith(E2EEManager.PREFIX_V1) == true) {
                                e2eeManager.decryptMessage(currentUserId, rawText)
                            } else {
                                rawText
                            }

                            // Save into Room (idempotent REPLACE prevents duplicates)
                            val entity = MessageEntity(
                                id = dto.id,
                                conversationId = dto.conversation_id,
                                senderId = dto.sender_id,
                                text = decryptedText,
                                messageType = dto.message_type,
                                status = "sent",
                                replyToMessageId = dto.reply_to_message_id,
                                createdAt = now
                            )
                            database.messageDao().insertMessage(entity)

                            // Update conversation preview
                            database.conversationDao().updateLastMessage(
                                conversationId = dto.conversation_id,
                                text = decryptedText,
                                timestamp = now
                            )

                            // If message is from other user, increment unread count
                            if (dto.sender_id != currentUserId) {
                                database.conversationDao().incrementUnreadCount(dto.conversation_id)
                            }

                            val domainMessage = Message(
                                id = dto.id,
                                conversationId = dto.conversation_id,
                                senderId = dto.sender_id,
                                text = decryptedText,
                                type = try { MessageType.valueOf(dto.message_type.uppercase()) } catch (e: Exception) { MessageType.TEXT },
                                status = MessageStatus.SENT,
                                replyToMessageId = dto.reply_to_message_id,
                                createdAt = now
                            )
                            trySend(domainMessage)
                        } catch (e: Exception) {
                            // Ignore malformed realtime payload
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore realtime connectivity error
            }
        }

        awaitClose {
            job.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    channel.unsubscribe()
                } catch (e: Exception) {
                    // Ignore unsubscribe errors
                }
            }
        }
    }

    override suspend fun syncConversations(): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUserId = currentUserIdProvider() ?: return@withContext Result.success(Unit)

        try {
            // 1. Fetch user's conversation memberships
            val memberships = supabaseProvider.postgrest.from("conversation_members")
                .select {
                    filter { eq("user_id", currentUserId) }
                }.decodeList<RemoteConversationMemberDto>()

            if (memberships.isEmpty()) {
                return@withContext Result.success(Unit)
            }

            for (membership in memberships) {
                val convoId = membership.conversation_id

                // Fetch conversation details
                val convo = try {
                    supabaseProvider.postgrest.from(SupabaseConfig.CONVERSATIONS_TABLE)
                        .select {
                            filter { eq("id", convoId) }
                        }.decodeSingleOrNull<RemoteConversationDto>()
                } catch (e: Exception) {
                    null
                } ?: continue

                // Find other participant for direct conversation
                val otherMember = try {
                    supabaseProvider.postgrest.from("conversation_members")
                        .select {
                            filter {
                                eq("conversation_id", convoId)
                                neq("user_id", currentUserId)
                            }
                        }.decodeSingleOrNull<RemoteConversationMemberDto>()
                } catch (e: Exception) {
                    null
                }

                val peerProfile = if (otherMember != null) {
                    try {
                        supabaseProvider.postgrest.from(SupabaseConfig.PROFILES_TABLE)
                            .select {
                                filter { eq("id", otherMember.user_id) }
                            }.decodeSingleOrNull<ProfileDto>()
                    } catch (e: Exception) {
                        null
                    }
                } else null

                val title = peerProfile?.display_name ?: convo.title ?: "محادثة"
                val avatarUrl = peerProfile?.avatar_url ?: convo.avatar_url
                val peerUsername = peerProfile?.username

                val rawLastMsg = convo.last_message_text
                val decryptedLastMsg = if (rawLastMsg?.startsWith(E2EEManager.PREFIX_V1) == true) {
                    e2eeManager.decryptMessage(currentUserId, rawLastMsg)
                } else {
                    rawLastMsg
                }

                val entity = ConversationEntity(
                    id = convoId,
                    type = convo.type,
                    title = title,
                    avatarUrl = avatarUrl,
                    peerUserId = otherMember?.user_id,
                    peerUsername = peerUsername,
                    lastMessageText = decryptedLastMsg,
                    lastMessageTimestamp = convo.last_message_at?.let { parseInstantOrNow(it) },
                    createdAt = convo.created_at?.let { parseInstantOrNow(it) } ?: Instant.now(),
                    updatedAt = convo.updated_at?.let { parseInstantOrNow(it) } ?: Instant.now()
                )
                database.conversationDao().insertConversation(entity)

                // Fetch latest batch of messages for this conversation (50 messages)
                try {
                    val messages = supabaseProvider.postgrest.from(SupabaseConfig.MESSAGES_TABLE)
                        .select {
                            filter { eq("conversation_id", convoId) }
                            order(column = "created_at", order = Order.DESCENDING)
                            limit(50)
                        }.decodeList<RemoteMessageDto>()

                    if (messages.isNotEmpty()) {
                        val messageEntities = messages.map { dto ->
                            val rawText = dto.text
                            val decryptedText = if (rawText?.startsWith(E2EEManager.PREFIX_V1) == true) {
                                e2eeManager.decryptMessage(currentUserId, rawText)
                            } else {
                                rawText
                            }
                            MessageEntity(
                                id = dto.id,
                                conversationId = dto.conversation_id,
                                senderId = dto.sender_id,
                                text = decryptedText,
                                messageType = dto.message_type,
                                status = "sent",
                                replyToMessageId = dto.reply_to_message_id,
                                createdAt = parseInstantOrNow(dto.created_at)
                            )
                        }
                        database.messageDao().insertMessages(messageEntities)
                    }
                } catch (e: Exception) {
                    // Ignore message fetch failure during convo sync
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createGroup(title: String, description: String?, memberUserIds: List<String>): Result<String> = withContext(Dispatchers.IO) {
        val currentUserId = currentUserIdProvider()
            ?: return@withContext Result.failure(Exception("يجب تسجيل الدخول لإنشاء مجموعة"))

        if (title.isBlank()) {
            return@withContext Result.failure(Exception("اسم المجموعة لا يمكن أن يكون فارغاً"))
        }

        try {
            val uuidList = memberUserIds.map { kotlinx.serialization.json.JsonPrimitive(it) }
            val response = supabaseProvider.postgrest.rpc(
                "create_group_conversation",
                buildJsonObject {
                    put("group_title", title.trim())
                    put("group_description", description?.trim() ?: "")
                    put("member_ids", kotlinx.serialization.json.JsonArray(uuidList))
                }
            ).decodeSingle<String>()

            syncConversations()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(Exception("فشل إنشاء المجموعة: ${e.localizedMessage ?: "خطأ غير معروف"}"))
        }
    }

    override suspend fun addGroupMember(conversationId: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabaseProvider.postgrest.rpc(
                "add_group_member",
                buildJsonObject {
                    put("target_conversation_id", conversationId)
                    put("target_user_id", userId)
                }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("فشل إضافة العضو: ${e.localizedMessage ?: ""}"))
        }
    }

    override suspend fun removeGroupMember(conversationId: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabaseProvider.postgrest.rpc(
                "remove_group_member",
                buildJsonObject {
                    put("target_conversation_id", conversationId)
                    put("target_user_id", userId)
                }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("فشل إزالة العضو: ${e.localizedMessage ?: ""}"))
        }
    }

    override suspend fun getGroupMembers(conversationId: String): Result<List<User>> = withContext<Result<List<User>>>(Dispatchers.IO) {
        try {
            val members = supabaseProvider.postgrest.from("conversation_members")
                .select {
                    filter { eq("conversation_id", conversationId) }
                }.decodeList<RemoteConversationMemberDto>()

            val userIds = members.map { it.user_id }
            if (userIds.isEmpty()) {
                return@withContext Result.success(emptyList<User>())
            }

            val profiles = supabaseProvider.postgrest.from(SupabaseConfig.PROFILES_TABLE)
                .select {
                    filter { isIn("id", userIds) }
                }.decodeList<ProfileDto>()

            val resultUsers: List<User> = profiles.map {
                User(
                    id = it.id,
                    username = it.username,
                    displayName = it.display_name,
                    avatarUrl = it.avatar_url,
                    bio = it.bio,
                    isOnline = it.is_online
                )
            }
            Result.success<List<User>>(resultUsers)
        } catch (e: Exception) {
            Result.failure<List<User>>(e)
        }
    }

    override suspend fun syncOutbox(): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUserId = currentUserIdProvider() ?: return@withContext Result.success(Unit)
        val pending = database.messageDao().getAllPendingOutboxMessages()

        for (item in pending) {
            try {
                val payload = buildJsonObject {
                    put("id", item.localId)
                    put("conversation_id", item.conversationId)
                    put("sender_id", currentUserId)
                    put("text", item.text)
                    put("message_type", item.messageType)
                    put("status", "sent")
                    if (item.replyToMessageId != null) put("reply_to_message_id", item.replyToMessageId)
                }
                supabaseProvider.postgrest.from(SupabaseConfig.MESSAGES_TABLE).insert(payload)
                database.messageDao().deleteOutboxMessage(item.localId)
                database.messageDao().updateMessageStatus(item.localId, "sent")
            } catch (e: Exception) {
                // Keep in outbox, will retry on next sync cycle
            }
        }
        Result.success(Unit)
    }

    override suspend fun ensureE2EESetup(): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUserId = currentUserIdProvider()
            ?: return@withContext Result.failure(Exception("غير مسجل الدخول"))

        try {
            val pubKeyBase64 = e2eeManager.getUserPublicKeyBase64(currentUserId)
            val payload = buildJsonObject {
                put("user_id", currentUserId)
                put("public_key", pubKeyBase64)
                put("key_algorithm", "ECDH-P256")
            }
            supabaseProvider.postgrest.from("user_e2ee_keys").upsert(payload)
            Result.success(Unit)
        } catch (e: Exception) {
            // Local KeyStore is hardware-backed, proceed smoothly even if offline
            Result.success(Unit)
        }
    }

    override suspend fun getSafetyNumber(peerUserId: String): Result<SafetyNumber> = withContext(Dispatchers.IO) {
        val currentUserId = currentUserIdProvider()
            ?: return@withContext Result.failure(Exception("يجب تسجيل الدخول لعرض أرقام الأمان"))

        try {
            val peerPubKey = getOrFetchPeerPublicKey(peerUserId)
                ?: return@withContext Result.failure(Exception("لم يتم العثور على مفتاح التشفير العام للطرف الآخر"))

            val peerProfile = try {
                database.userDao().getUserByIdSync(peerUserId)
            } catch (_: Exception) {
                null
            }

            val safetyNumberStr = e2eeManager.generateSafetyNumber(currentUserId, peerPubKey)
            val isVerified = keystoreManager.isSafetyNumberVerified(peerUserId)

            Result.success(
                SafetyNumber(
                    formattedNumber = safetyNumberStr,
                    peerUsername = peerProfile?.username ?: "user",
                    peerDisplayName = peerProfile?.displayName ?: "مستخدم تواصل",
                    isVerified = isVerified
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getOrFetchPeerPublicKey(peerUserId: String): String? {
        val cached = keystoreManager.getPeerPublicKey(peerUserId)
        if (!cached.isNullOrBlank()) return cached

        return try {
            val remote = supabaseProvider.postgrest.from("user_e2ee_keys")
                .select {
                    filter { eq("user_id", peerUserId) }
                }.decodeSingleOrNull<RemoteUserE2eeKeyDto>()

            if (remote != null) {
                keystoreManager.savePeerPublicKey(peerUserId, remote.public_key)
                remote.public_key
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseInstantOrNow(isoString: String?): Instant {
        if (isoString.isNullOrBlank()) return Instant.now()
        return try {
            Instant.parse(isoString)
        } catch (e: Exception) {
            Instant.now()
        }
    }
}
