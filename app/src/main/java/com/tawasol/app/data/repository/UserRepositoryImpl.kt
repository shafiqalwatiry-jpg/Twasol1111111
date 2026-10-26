package com.tawasol.app.data.repository

import com.tawasol.app.core.database.TawasolDatabase
import com.tawasol.app.core.database.entity.PrivacySettingsEntity
import com.tawasol.app.core.database.entity.UserEntity
import com.tawasol.app.core.security.KeystoreManager
import com.tawasol.app.core.supabase.SupabaseClientProvider
import com.tawasol.app.core.supabase.SupabaseConfig
import com.tawasol.app.domain.model.PrivacySettings
import com.tawasol.app.domain.model.User
import com.tawasol.app.domain.model.VisibilityOption
import com.tawasol.app.domain.repository.UserRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

@Serializable
data class PrivacySettingsDto(
    val user_id: String,
    val last_seen_visibility: String = "everyone",
    val status_visibility: String = "everyone",
    val avatar_visibility: String = "everyone",
    val allow_messages_from: String = "everyone",
    val read_receipts_enabled: Boolean = true
)

class UserRepositoryImpl(
    private val database: TawasolDatabase,
    private val supabaseProvider: SupabaseClientProvider,
    private val keystoreManager: KeystoreManager
) : UserRepository {

    override fun getUser(userId: String): Flow<User?> {
        return database.userDao().getUserById(userId).map { entity ->
            entity?.let {
                User(
                    id = it.id,
                    username = it.username,
                    displayName = it.displayName,
                    avatarUrl = it.avatarUrl,
                    bio = it.bio,
                    phone = it.phone,
                    email = it.email,
                    isOnline = it.isOnline,
                    lastSeen = it.lastSeen,
                    createdAt = it.createdAt
                )
            }
        }
    }

    override suspend fun getUserProfile(userId: String): Result<User> = withContext(Dispatchers.IO) {
        val currentUserId = keystoreManager.getCurrentUserId()
        val isSelf = currentUserId == userId

        try {
            val profile = supabaseProvider.postgrest.from(SupabaseConfig.PROFILES_TABLE)
                .select {
                    filter {
                        eq("id", userId)
                    }
                }.decodeSingleOrNull<ProfileDto>()
                ?: return@withContext Result.failure(Exception("المستخدم غير موجود"))

            val contact = if (isSelf) {
                try {
                    supabaseProvider.postgrest.from(SupabaseConfig.USER_CONTACTS_TABLE)
                        .select {
                            filter {
                                eq("user_id", userId)
                            }
                        }.decodeSingleOrNull<UserContactDto>()
                } catch (e: Exception) {
                    null
                }
            } else null

            val user = User(
                id = profile.id,
                username = profile.username,
                displayName = profile.display_name,
                avatarUrl = profile.avatar_url,
                bio = profile.bio,
                phone = contact?.phone_number,
                email = contact?.email,
                isOnline = profile.is_online
            )

            // Cache in Room DB
            database.userDao().insertUser(
                UserEntity(
                    id = user.id,
                    username = user.username,
                    displayName = user.displayName,
                    avatarUrl = user.avatarUrl,
                    bio = user.bio,
                    phone = user.phone,
                    email = user.email,
                    isOnline = user.isOnline
                )
            )

            Result.success(user)
        } catch (e: Exception) {
            // Offline fallback from local DB
            val localUser = database.userDao().getUserByIdSync(userId)
            if (localUser != null) {
                Result.success(
                    User(
                        id = localUser.id,
                        username = localUser.username,
                        displayName = localUser.displayName,
                        avatarUrl = localUser.avatarUrl,
                        bio = localUser.bio,
                        phone = if (isSelf) localUser.phone else null,
                        email = if (isSelf) localUser.email else null,
                        isOnline = localUser.isOnline
                    )
                )
            } else {
                Result.failure(Exception("تعذر استرجاع بيانات الملف الشخصي: ${e.localizedMessage ?: "تأكد من الاتصال"}"))
            }
        }
    }

    override suspend fun searchUsers(query: String): Result<List<User>> = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = query.trim().removePrefix("@").lowercase()

            val results = supabaseProvider.postgrest.from(SupabaseConfig.PROFILES_TABLE)
                .select {
                    filter {
                        or {
                            ilike("username", "%$cleanQuery%")
                            ilike("display_name", "%$cleanQuery%")
                        }
                    }
                    limit(30)
                }.decodeList<ProfileDto>()

            val users = results.map { dto ->
                // Note: Phone and email are NEVER included in search results to preserve privacy!
                User(
                    id = dto.id,
                    username = dto.username,
                    displayName = dto.display_name,
                    avatarUrl = dto.avatar_url,
                    bio = dto.bio,
                    phone = null,
                    email = null,
                    isOnline = dto.is_online
                )
            }

            // Cache retrieved public profiles in Room (preserve logged-in user's private data)
            if (users.isNotEmpty()) {
                val currentUserId = keystoreManager.getCurrentUserId()
                val otherUsers = users.filter { it.id != currentUserId }
                if (otherUsers.isNotEmpty()) {
                    database.userDao().insertUsers(
                        otherUsers.map {
                            UserEntity(
                                id = it.id,
                                username = it.username,
                                displayName = it.displayName,
                                avatarUrl = it.avatarUrl,
                                bio = it.bio,
                                isOnline = it.isOnline
                            )
                        }
                    )
                }
            }

            Result.success(users)
        } catch (e: Exception) {
            // Offline search in local database
            try {
                val localMatches = database.userDao().searchLocalUsers(query)
                val users = localMatches.map {
                    User(
                        id = it.id,
                        username = it.username,
                        displayName = it.displayName,
                        avatarUrl = it.avatarUrl,
                        bio = it.bio,
                        phone = null,
                        email = null,
                        isOnline = it.isOnline
                    )
                }
                Result.success(users)
            } catch (localEx: Exception) {
                Result.failure(Exception("تعذر إتمام البحث: ${e.localizedMessage ?: "حدث خطأ"}"))
            }
        }
    }

    override suspend fun updateProfile(
        userId: String,
        displayName: String,
        bio: String?
    ): Result<User> = withContext(Dispatchers.IO) {
        try {
            val payload = buildJsonObject {
                put("display_name", displayName.trim())
                if (bio != null) put("bio", bio.trim()) else put("bio", "")
            }

            supabaseProvider.postgrest.from(SupabaseConfig.PROFILES_TABLE)
                .update(payload) {
                    filter {
                        eq("id", userId)
                    }
                }

            // Update in Room DB
            database.userDao().updateProfile(
                userId = userId,
                displayName = displayName.trim(),
                bio = bio?.trim(),
                avatarUrl = null,
                updatedAt = Instant.now()
            )

            val updated = getUserProfile(userId).getOrNull()
                ?: User(id = userId, username = "", displayName = displayName, bio = bio)

            Result.success(updated)
        } catch (e: Exception) {
            // Update locally even if offline, will sync later
            database.userDao().updateProfile(
                userId = userId,
                displayName = displayName.trim(),
                bio = bio?.trim(),
                avatarUrl = null,
                updatedAt = Instant.now()
            )
            Result.failure(Exception("فشل حفظ التعديلات في السيرفر، تم الحفظ محلياً: ${e.localizedMessage}"))
        }
    }

    override suspend fun uploadAvatar(
        userId: String,
        imageBytes: ByteArray,
        mimeType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fileName = "$userId/avatar_${System.currentTimeMillis()}.jpg"

            val bucket = supabaseProvider.client.storage.from(SupabaseConfig.AVATARS_BUCKET)
            bucket.upload(
                path = fileName,
                data = imageBytes
            ) {
                upsert = true
            }

            val publicUrl = bucket.publicUrl(fileName)

            // Update profile avatar_url
            val payload = buildJsonObject {
                put("avatar_url", publicUrl)
            }
            supabaseProvider.postgrest.from(SupabaseConfig.PROFILES_TABLE)
                .update(payload) {
                    filter {
                        eq("id", userId)
                    }
                }

            // Update local DB
            val existing = database.userDao().getUserByIdSync(userId)
            if (existing != null) {
                database.userDao().insertUser(existing.copy(avatarUrl = publicUrl, updatedAt = Instant.now()))
            }

            Result.success(publicUrl)
        } catch (e: Exception) {
            Result.failure(Exception("تعذر رفع الصورة الشخصية: ${e.localizedMessage ?: "تحقق من اتصال الإنترنت وحجم الصورة"}"))
        }
    }

    override fun getPrivacySettings(userId: String): Flow<PrivacySettings?> {
        return database.privacySettingsDao().getPrivacySettings(userId).map { entity ->
            if (entity != null) {
                PrivacySettings(
                    userId = entity.userId,
                    lastSeenVisibility = VisibilityOption.fromString(entity.lastSeenVisibility),
                    onlineStatusVisibility = VisibilityOption.fromString(entity.onlineStatusVisibility),
                    profilePhotoVisibility = VisibilityOption.fromString(entity.profilePhotoVisibility),
                    bioVisibility = VisibilityOption.fromString(entity.bioVisibility),
                    readReceiptsEnabled = entity.readReceiptsEnabled
                )
            } else {
                PrivacySettings(userId = userId)
            }
        }
    }

    override suspend fun updatePrivacySettings(settings: PrivacySettings): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Save in Room DB
            database.privacySettingsDao().insertOrUpdate(
                PrivacySettingsEntity(
                    userId = settings.userId,
                    lastSeenVisibility = settings.lastSeenVisibility.name.lowercase(),
                    onlineStatusVisibility = settings.onlineStatusVisibility.name.lowercase(),
                    profilePhotoVisibility = settings.profilePhotoVisibility.name.lowercase(),
                    bioVisibility = settings.bioVisibility.name.lowercase(),
                    readReceiptsEnabled = settings.readReceiptsEnabled,
                    updatedAt = Instant.now()
                )
            )

            // Save in Supabase
            val payload = buildJsonObject {
                put("user_id", settings.userId)
                put("last_seen_visibility", settings.lastSeenVisibility.name.lowercase())
                put("status_visibility", settings.onlineStatusVisibility.name.lowercase())
                put("avatar_visibility", settings.profilePhotoVisibility.name.lowercase())
                put("read_receipts_enabled", settings.readReceiptsEnabled)
            }

            supabaseProvider.postgrest.from(SupabaseConfig.PRIVACY_SETTINGS_TABLE)
                .upsert(payload)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("تعذر حفظ إعدادات الخصوصية في الخادم: ${e.localizedMessage ?: "حدث خطأ"}"))
        }
    }

    override suspend fun updatePresence(isOnline: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUserId = keystoreManager.getCurrentUserId()
            ?: return@withContext Result.failure(IllegalStateException("المستخدم غير مسجل"))

        val now = Instant.now()
        // Update local Room cache first
        try {
            database.userDao().updateUserStatus(currentUserId, isOnline, now)
        } catch (_: Exception) {}

        try {
            val payload = buildJsonObject {
                put("is_online", isOnline)
                put("last_seen", now.toString())
            }
            supabaseProvider.postgrest.from(SupabaseConfig.PROFILES_TABLE)
                .update(payload) {
                    filter { eq("id", currentUserId) }
                }
            Result.success(Unit)
        } catch (e: Exception) {
            // Non-fatal if offline
            Result.success(Unit)
        }
    }
}

