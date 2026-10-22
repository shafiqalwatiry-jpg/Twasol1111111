package com.tawasol.app.data.repository

import com.tawasol.app.core.supabase.SupabaseClientProvider
import com.tawasol.app.core.supabase.SupabaseConfig
import com.tawasol.app.domain.model.UserStatus
import com.tawasol.app.domain.repository.StatusRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
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
data class RemoteStatusDto(
    val id: String,
    val user_id: String,
    val content_text: String? = null,
    val media_url: String? = null,
    val status_type: String = "text",
    val created_at: String? = null,
    val expires_at: String? = null
)

class StatusRepositoryImpl(
    private val supabaseProvider: SupabaseClientProvider,
    private val currentUserIdProvider: () -> String?
) : StatusRepository {

    override fun getActiveStatuses(): Flow<List<UserStatus>> = flow {
        val currentUserId = currentUserIdProvider()
        try {
            val nowIso = Instant.now().toString()
            val remoteList = supabaseProvider.postgrest.from(SupabaseConfig.STATUSES_TABLE)
                .select {
                    filter {
                        gt("expires_at", nowIso)
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                    limit(50)
                }.decodeList<RemoteStatusDto>()

            val statuses = remoteList.map { dto ->
                // Fetch user profile for display name and avatar
                val profile = try {
                    supabaseProvider.postgrest.from(SupabaseConfig.PROFILES_TABLE)
                        .select {
                            filter { eq("id", dto.user_id) }
                        }.decodeSingleOrNull<ProfileDto>()
                } catch (e: Exception) {
                    null
                }

                UserStatus(
                    id = dto.id,
                    userId = dto.user_id,
                    userDisplayName = profile?.display_name ?: "مستخدم",
                    userAvatarUrl = profile?.avatar_url,
                    text = dto.content_text,
                    mediaUrl = dto.media_url,
                    statusType = dto.status_type,
                    createdAt = dto.created_at?.let { parseInstant(it) } ?: Instant.now(),
                    expiresAt = dto.expires_at?.let { parseInstant(it) } ?: Instant.now().plusSeconds(86400),
                    isSelf = dto.user_id == currentUserId
                )
            }
            emit(statuses)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun postTextStatus(text: String): Result<UserStatus> = withContext(Dispatchers.IO) {
        val currentUserId = currentUserIdProvider()
            ?: return@withContext Result.failure(Exception("يجب تسجيل الدخول لنشر حالة"))

        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            return@withContext Result.failure(Exception("لا يمكن نشر حالة فارغة"))
        }

        try {
            val newId = UUID.randomUUID().toString()
            val payload = buildJsonObject {
                put("id", newId)
                put("user_id", currentUserId)
                put("content_text", cleanText)
                put("status_type", "text")
            }

            supabaseProvider.postgrest.from(SupabaseConfig.STATUSES_TABLE).insert(payload)

            val status = UserStatus(
                id = newId,
                userId = currentUserId,
                text = cleanText,
                statusType = "text",
                isSelf = true
            )
            Result.success(status)
        } catch (e: Exception) {
            Result.failure(Exception("فشل نشر الحالة: ${e.localizedMessage ?: "تحقق من الاتصال"}"))
        }
    }

    override suspend fun postMediaStatus(imageBytes: ByteArray, caption: String?): Result<UserStatus> =
        withContext(Dispatchers.IO) {
            val currentUserId = currentUserIdProvider()
                ?: return@withContext Result.failure(Exception("يجب تسجيل الدخول لنشر حالة"))

            if (imageBytes.isEmpty()) {
                return@withContext Result.failure(Exception("الملف المختار فارغ"))
            }

            try {
                val newId = UUID.randomUUID().toString()
                val fileName = "${System.currentTimeMillis()}_status.jpg"
                val path = "$currentUserId/statuses/$fileName"

                val bucket = supabaseProvider.client.storage.from("chat-media")
                bucket.upload(path = path, data = imageBytes) {
                    upsert = true
                }
                val publicUrl = bucket.publicUrl(path)

                val payload = buildJsonObject {
                    put("id", newId)
                    put("user_id", currentUserId)
                    if (!caption.isNullOrBlank()) put("content_text", caption.trim())
                    put("media_url", publicUrl)
                    put("status_type", "image")
                }

                supabaseProvider.postgrest.from(SupabaseConfig.STATUSES_TABLE).insert(payload)

                val status = UserStatus(
                    id = newId,
                    userId = currentUserId,
                    text = caption?.trim(),
                    mediaUrl = publicUrl,
                    statusType = "image",
                    isSelf = true
                )
                Result.success(status)
            } catch (e: Exception) {
                Result.failure(Exception("فشل رفع الصورة ونشر الحالة: ${e.localizedMessage ?: "تحقق من الاتصال"}"))
            }
        }

    override suspend fun deleteStatus(statusId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUserId = currentUserIdProvider()
            ?: return@withContext Result.failure(Exception("يجب تسجيل الدخول"))

        try {
            supabaseProvider.postgrest.from(SupabaseConfig.STATUSES_TABLE)
                .delete {
                    filter {
                        eq("id", statusId)
                        eq("user_id", currentUserId)
                    }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markStatusViewed(statusId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUserId = currentUserIdProvider() ?: return@withContext Result.success(Unit)
        try {
            val payload = buildJsonObject {
                put("status_id", statusId)
                put("viewer_id", currentUserId)
            }
            supabaseProvider.postgrest.from("status_views").insert(payload)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit) // Ignore duplicate view inserts
        }
    }

    private fun parseInstant(isoString: String): Instant {
        return try {
            Instant.parse(isoString)
        } catch (e: Exception) {
            Instant.now()
        }
    }
}
