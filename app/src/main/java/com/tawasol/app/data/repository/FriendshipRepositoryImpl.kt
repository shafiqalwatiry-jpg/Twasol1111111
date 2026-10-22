package com.tawasol.app.data.repository

import com.tawasol.app.core.supabase.SupabaseClientProvider
import com.tawasol.app.core.supabase.SupabaseConfig
import com.tawasol.app.domain.model.FriendRequest
import com.tawasol.app.domain.model.FriendshipStatus
import com.tawasol.app.domain.model.User
import com.tawasol.app.domain.repository.FriendshipRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

@Serializable
data class RemoteFriendRequestDto(
    val id: String,
    val sender_id: String,
    val receiver_id: String,
    val status: String = "pending",
    val created_at: String? = null,
    val updated_at: String? = null
)

class FriendshipRepositoryImpl(
    private val supabaseProvider: SupabaseClientProvider,
    private val currentUserIdProvider: () -> String?
) : FriendshipRepository {

    override suspend fun sendFriendRequest(targetUserId: String): Result<FriendRequest> =
        withContext(Dispatchers.IO) {
            val currentUserId = currentUserIdProvider()
                ?: return@withContext Result.failure(IllegalStateException("يجب تسجيل الدخول لإرسال طلب صداقة"))

            if (currentUserId == targetUserId) {
                return@withContext Result.failure(IllegalArgumentException("لا يمكن إرسال طلب صداقة لنفسك"))
            }

            try {
                val newId = UUID.randomUUID().toString()
                val payload = buildJsonObject {
                    put("id", newId)
                    put("sender_id", currentUserId)
                    put("receiver_id", targetUserId)
                    put("status", "pending")
                }

                supabaseProvider.postgrest.from("friend_requests").insert(payload)

                val request = FriendRequest(
                    id = newId,
                    senderId = currentUserId,
                    receiverId = targetUserId,
                    status = "pending",
                    createdAt = Instant.now()
                )
                Result.success(request)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر إرسال طلب الصداقة: ${e.localizedMessage ?: "تحقق من الاتصال"}"))
            }
        }

    override suspend fun acceptFriendRequest(requestId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val currentUserId = currentUserIdProvider()
                ?: return@withContext Result.failure(IllegalStateException("يجب تسجيل الدخول"))

            try {
                supabaseProvider.postgrest.from("friend_requests")
                    .update({
                        set("status", "accepted")
                        set("updated_at", Instant.now().toString())
                    }) {
                        filter {
                            eq("id", requestId)
                            eq("receiver_id", currentUserId)
                        }
                    }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر قبول الطلب: ${e.localizedMessage}"))
            }
        }

    override suspend fun rejectFriendRequest(requestId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val currentUserId = currentUserIdProvider()
                ?: return@withContext Result.failure(IllegalStateException("يجب تسجيل الدخول"))

            try {
                supabaseProvider.postgrest.from("friend_requests")
                    .update({
                        set("status", "rejected")
                        set("updated_at", Instant.now().toString())
                    }) {
                        filter {
                            eq("id", requestId)
                            eq("receiver_id", currentUserId)
                        }
                    }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر رفض الطلب: ${e.localizedMessage}"))
            }
        }

    override suspend fun cancelFriendRequest(requestId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val currentUserId = currentUserIdProvider()
                ?: return@withContext Result.failure(IllegalStateException("يجب تسجيل الدخول"))

            try {
                supabaseProvider.postgrest.from("friend_requests")
                    .delete {
                        filter {
                            eq("id", requestId)
                            eq("sender_id", currentUserId)
                        }
                    }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر إلغاء الطلب: ${e.localizedMessage}"))
            }
        }

    override suspend fun removeFriend(friendUserId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val currentUserId = currentUserIdProvider()
                ?: return@withContext Result.failure(IllegalStateException("يجب تسجيل الدخول"))

            try {
                supabaseProvider.postgrest.from("friend_requests")
                    .delete {
                        filter {
                            or {
                                and {
                                    eq("sender_id", currentUserId)
                                    eq("receiver_id", friendUserId)
                                }
                                and {
                                    eq("sender_id", friendUserId)
                                    eq("receiver_id", currentUserId)
                                }
                            }
                        }
                    }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر إزالة الصديق: ${e.localizedMessage}"))
            }
        }

    override suspend fun getFriendshipStatus(otherUserId: String): Result<FriendshipStatus> =
        withContext(Dispatchers.IO) {
            val currentUserId = currentUserIdProvider()
                ?: return@withContext Result.success(FriendshipStatus.NONE)

            if (currentUserId == otherUserId) {
                return@withContext Result.success(FriendshipStatus.NONE)
            }

            try {
                val requests = supabaseProvider.postgrest.from("friend_requests")
                    .select {
                        filter {
                            or {
                                and {
                                    eq("sender_id", currentUserId)
                                    eq("receiver_id", otherUserId)
                                }
                                and {
                                    eq("sender_id", otherUserId)
                                    eq("receiver_id", currentUserId)
                                }
                            }
                        }
                        order("created_at", Order.DESCENDING)
                        limit(1)
                    }.decodeList<RemoteFriendRequestDto>()

                val active = requests.firstOrNull() ?: return@withContext Result.success(FriendshipStatus.NONE)

                val status = when (active.status) {
                    "accepted" -> FriendshipStatus.FRIENDS
                    "pending" -> if (active.sender_id == currentUserId) FriendshipStatus.REQUEST_SENT else FriendshipStatus.REQUEST_RECEIVED
                    else -> FriendshipStatus.NONE
                }
                Result.success(status)
            } catch (e: Exception) {
                Result.success(FriendshipStatus.NONE)
            }
        }

    override fun getPendingIncomingRequests(): Flow<List<FriendRequest>> = flow {
        val currentUserId = currentUserIdProvider() ?: run {
            emit(emptyList())
            return@flow
        }

        try {
            val rawList = supabaseProvider.postgrest.from("friend_requests")
                .select {
                    filter {
                        eq("receiver_id", currentUserId)
                        eq("status", "pending")
                    }
                    order("created_at", Order.DESCENDING)
                }.decodeList<RemoteFriendRequestDto>()

            val requests = rawList.map { dto ->
                val senderProfile = try {
                    supabaseProvider.postgrest.from(SupabaseConfig.PROFILES_TABLE)
                        .select {
                            filter { eq("id", dto.sender_id) }
                        }.decodeSingleOrNull<ProfileDto>()
                } catch (e: Exception) {
                    null
                }

                FriendRequest(
                    id = dto.id,
                    senderId = dto.sender_id,
                    senderUsername = senderProfile?.username ?: "مستخدم",
                    senderDisplayName = senderProfile?.display_name ?: "مستخدم تواصل",
                    senderAvatarUrl = senderProfile?.avatar_url,
                    receiverId = dto.receiver_id,
                    status = dto.status,
                    createdAt = dto.created_at?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
                )
            }
            emit(requests)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getPendingOutgoingRequests(): Flow<List<FriendRequest>> = flow {
        val currentUserId = currentUserIdProvider() ?: run {
            emit(emptyList())
            return@flow
        }

        try {
            val rawList = supabaseProvider.postgrest.from("friend_requests")
                .select {
                    filter {
                        eq("sender_id", currentUserId)
                        eq("status", "pending")
                    }
                    order("created_at", Order.DESCENDING)
                }.decodeList<RemoteFriendRequestDto>()

            val requests = rawList.map { dto ->
                val receiverProfile = try {
                    supabaseProvider.postgrest.from(SupabaseConfig.PROFILES_TABLE)
                        .select {
                            filter { eq("id", dto.receiver_id) }
                        }.decodeSingleOrNull<ProfileDto>()
                } catch (e: Exception) {
                    null
                }

                FriendRequest(
                    id = dto.id,
                    senderId = dto.sender_id,
                    receiverId = dto.receiver_id,
                    receiverUsername = receiverProfile?.username ?: "مستخدم",
                    receiverDisplayName = receiverProfile?.display_name ?: "مستخدم تواصل",
                    receiverAvatarUrl = receiverProfile?.avatar_url,
                    status = dto.status,
                    createdAt = dto.created_at?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
                )
            }
            emit(requests)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getFriends(): Flow<List<User>> = flow {
        val currentUserId = currentUserIdProvider() ?: run {
            emit(emptyList())
            return@flow
        }

        try {
            val rawList = supabaseProvider.postgrest.from("friend_requests")
                .select {
                    filter {
                        eq("status", "accepted")
                        or {
                            eq("sender_id", currentUserId)
                            eq("receiver_id", currentUserId)
                        }
                    }
                }.decodeList<RemoteFriendRequestDto>()

            val friendUserIds = rawList.map {
                if (it.sender_id == currentUserId) it.receiver_id else it.sender_id
            }.distinct()

            val friends = friendUserIds.mapNotNull { friendId ->
                try {
                    val p = supabaseProvider.postgrest.from(SupabaseConfig.PROFILES_TABLE)
                        .select {
                            filter { eq("id", friendId) }
                        }.decodeSingleOrNull<ProfileDto>()

                    if (p != null) {
                        User(
                            id = p.id,
                            username = p.username,
                            displayName = p.display_name,
                            avatarUrl = p.avatar_url,
                            bio = p.bio,
                            isOnline = p.is_online
                        )
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
            emit(friends)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)
}
