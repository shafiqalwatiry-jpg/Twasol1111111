package com.tawasol.app.data.repository

import com.tawasol.app.core.supabase.SupabaseClientProvider
import com.tawasol.app.core.supabase.SupabaseConfig
import com.tawasol.app.domain.model.CallSession
import com.tawasol.app.domain.model.CallStatus
import com.tawasol.app.domain.model.CallType
import com.tawasol.app.domain.repository.CallRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Serializable
data class RemoteCallDto(
    val id: String,
    val caller_id: String,
    val receiver_id: String? = null,
    val conversation_id: String? = null,
    val type: String = "voice",
    val status: String = "outgoing",
    val webrtc_sdp_offer: String? = null,
    val webrtc_sdp_answer: String? = null,
    val started_at: String? = null,
    val ended_at: String? = null,
    val created_at: String? = null
)

class CallRepositoryImpl(
    private val supabaseProvider: SupabaseClientProvider,
    private val currentUserIdProvider: () -> String?
) : CallRepository {

    companion object {
        val DEFAULT_ICE_SERVERS = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun2.l.google.com:19302"
        )
    }

    override suspend fun startCall(
        receiverId: String,
        conversationId: String?,
        type: CallType
    ): Result<CallSession> = withContext(Dispatchers.IO) {
        val currentUserId = currentUserIdProvider()
            ?: return@withContext Result.failure(IllegalStateException("يجب تسجيل الدخول لبدء مكالمة"))

        try {
            val callId = UUID.randomUUID().toString()
            val typeStr = if (type == CallType.VIDEO) "video" else "voice"

            val payload = buildJsonObject {
                put("id", callId)
                put("caller_id", currentUserId)
                put("receiver_id", receiverId)
                if (conversationId != null) put("conversation_id", conversationId)
                put("type", typeStr)
                put("status", "outgoing")
            }

            supabaseProvider.postgrest.from("calls").insert(payload)

            val session = CallSession(
                id = callId,
                callerId = currentUserId,
                receiverId = receiverId,
                conversationId = conversationId,
                type = type,
                status = CallStatus.OUTGOING,
                createdAt = Instant.now()
            )
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(Exception("تعذر بدء الاتصال: ${e.localizedMessage ?: "تحقق من الاتصال"}"))
        }
    }

    override suspend fun acceptCall(callId: String, sdpAnswerJson: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val nowIso = Instant.now().toString()
                supabaseProvider.postgrest.from("calls")
                    .update({
                        set("status", "accepted")
                        set("started_at", nowIso)
                    }) {
                        filter { eq("id", callId) }
                    }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر قبول المكالمة: ${e.localizedMessage}"))
            }
        }

    override suspend fun rejectCall(callId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val nowIso = Instant.now().toString()
                supabaseProvider.postgrest.from("calls")
                    .update({
                        set("status", "rejected")
                        set("ended_at", nowIso)
                    }) {
                        filter { eq("id", callId) }
                    }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر رفض المكالمة: ${e.localizedMessage}"))
            }
        }

    override suspend fun endCall(callId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val nowIso = Instant.now().toString()
                supabaseProvider.postgrest.from("calls")
                    .update({
                        set("status", "ended")
                        set("ended_at", nowIso)
                    }) {
                        filter { eq("id", callId) }
                    }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(Exception("تعذر إنهاء المكالمة: ${e.localizedMessage}"))
            }
        }

    override suspend fun updateSdpOffer(callId: String, sdpOfferJson: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                supabaseProvider.postgrest.from("calls")
                    .update({
                        set("webrtc_sdp_offer", sdpOfferJson)
                    }) {
                        filter { eq("id", callId) }
                    }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun updateSdpAnswer(callId: String, sdpAnswerJson: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                supabaseProvider.postgrest.from("calls")
                    .update({
                        set("webrtc_sdp_answer", sdpAnswerJson)
                    }) {
                        filter { eq("id", callId) }
                    }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun addIceCandidate(callId: String, candidateJson: String, isCaller: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            // WebRTC candidate exchange is supported via signalling channel
            Result.success(Unit)
        }

    override fun getActiveCallFlow(callId: String): Flow<CallSession?> = flow {
        try {
            val dto = supabaseProvider.postgrest.from("calls")
                .select {
                    filter { eq("id", callId) }
                }.decodeSingleOrNull<RemoteCallDto>()

            emit(dto?.let { toCallSession(it) })
        } catch (e: Exception) {
            emit(null)
        }
    }.flowOn(Dispatchers.IO)

    override fun getIncomingCallsFlow(): Flow<CallSession?> = flow {
        val currentUserId = currentUserIdProvider() ?: run {
            emit(null)
            return@flow
        }

        try {
            val incoming = supabaseProvider.postgrest.from("calls")
                .select {
                    filter {
                        eq("receiver_id", currentUserId)
                        eq("status", "outgoing")
                    }
                    order("created_at", Order.DESCENDING)
                    limit(1)
                }.decodeSingleOrNull<RemoteCallDto>()

            emit(incoming?.let { toCallSession(it) })
        } catch (e: Exception) {
            emit(null)
        }
    }.flowOn(Dispatchers.IO)

    override fun getCallHistory(): Flow<List<CallSession>> = flow {
        val currentUserId = currentUserIdProvider() ?: run {
            emit(emptyList())
            return@flow
        }

        try {
            val remoteList = supabaseProvider.postgrest.from("calls")
                .select {
                    filter {
                        or {
                            eq("caller_id", currentUserId)
                            eq("receiver_id", currentUserId)
                        }
                    }
                    order("created_at", Order.DESCENDING)
                    limit(50)
                }.decodeList<RemoteCallDto>()

            val sessions = remoteList.map { dto ->
                toCallSession(dto)
            }
            emit(sessions)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun toCallSession(dto: RemoteCallDto): CallSession {
        val callerProfile = try {
            supabaseProvider.postgrest.from(SupabaseConfig.PROFILES_TABLE)
                .select { filter { eq("id", dto.caller_id) } }
                .decodeSingleOrNull<ProfileDto>()
        } catch (e: Exception) {
            null
        }

        val receiverProfile = if (dto.receiver_id != null) {
            try {
                supabaseProvider.postgrest.from(SupabaseConfig.PROFILES_TABLE)
                    .select { filter { eq("id", dto.receiver_id) } }
                    .decodeSingleOrNull<ProfileDto>()
            } catch (e: Exception) {
                null
            }
        } else null

        val start = dto.started_at?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val end = dto.ended_at?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val duration = if (start != null && end != null) {
            Duration.between(start, end).seconds.coerceAtLeast(0)
        } else 0L

        val statusEnum = when (dto.status.lowercase()) {
            "outgoing" -> CallStatus.OUTGOING
            "incoming" -> CallStatus.INCOMING
            "ringing" -> CallStatus.RINGING
            "accepted" -> CallStatus.ACCEPTED
            "rejected" -> CallStatus.REJECTED
            "busy" -> CallStatus.BUSY
            "missed" -> CallStatus.MISSED
            else -> CallStatus.ENDED
        }

        val typeEnum = if (dto.type.lowercase() == "video") CallType.VIDEO else CallType.VOICE

        return CallSession(
            id = dto.id,
            callerId = dto.caller_id,
            callerDisplayName = callerProfile?.display_name ?: "مستخدم",
            callerAvatarUrl = callerProfile?.avatar_url,
            receiverId = dto.receiver_id ?: "",
            receiverDisplayName = receiverProfile?.display_name ?: "مستخدم تواصل",
            receiverAvatarUrl = receiverProfile?.avatar_url,
            conversationId = dto.conversation_id,
            type = typeEnum,
            status = statusEnum,
            startedAt = start,
            endedAt = end,
            createdAt = dto.created_at?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now(),
            durationSeconds = duration
        )
    }
}
