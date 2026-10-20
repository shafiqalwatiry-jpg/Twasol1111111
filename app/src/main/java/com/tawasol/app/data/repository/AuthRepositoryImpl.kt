package com.tawasol.app.data.repository

import com.tawasol.app.core.database.TawasolDatabase
import com.tawasol.app.core.database.entity.UserEntity
import com.tawasol.app.core.security.KeystoreManager
import com.tawasol.app.core.supabase.SupabaseClientProvider
import com.tawasol.app.core.supabase.SupabaseConfig
import com.tawasol.app.domain.model.User
import com.tawasol.app.domain.repository.AuthRepository
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

@Serializable
data class ProfileDto(
    val id: String,
    val username: String,
    val display_name: String,
    val avatar_url: String? = null,
    val bio: String? = null,
    val is_online: Boolean = false,
    val last_seen: String? = null,
    val created_at: String? = null
)

@Serializable
data class UserContactDto(
    val user_id: String,
    val phone_number: String? = null,
    val email: String? = null
)

class AuthRepositoryImpl(
    private val supabaseProvider: SupabaseClientProvider,
    private val database: TawasolDatabase,
    private val keystoreManager: KeystoreManager
) : AuthRepository {

    override val currentUserId: String?
        get() = keystoreManager.getCurrentUserId()

    override suspend fun login(loginIdentifier: String, password: String): Result<User> =
        withContext(Dispatchers.IO) {
            try {
                val cleanIdentifier = loginIdentifier.trim()
                val isEmail = cleanIdentifier.contains("@") && cleanIdentifier.contains(".")

                val authEmail = if (isEmail) {
                    // Try to resolve username from private contacts via secure RPC
                    val resolvedUsername = try {
                        supabaseProvider.postgrest.rpc(
                            "get_auth_username_by_contact_email",
                            buildJsonObject { put("search_email", cleanIdentifier) }
                        ).decodeSingleOrNull<String>()
                    } catch (e: Exception) {
                        null
                    }
                    if (!resolvedUsername.isNullOrBlank()) {
                        "$resolvedUsername@tawasol.local"
                    } else {
                        cleanIdentifier
                    }
                } else {
                    val cleanUsername = cleanIdentifier.removePrefix("@").lowercase()
                    "$cleanUsername@tawasol.local"
                }

                // Authenticate with Supabase Auth
                supabaseProvider.auth.signInWith(Email) {
                    this.email = authEmail
                    this.password = password
                }

                val currentSession = supabaseProvider.auth.currentSessionOrNull()
                val sessionUser = currentSession?.user
                    ?: return@withContext Result.failure(Exception("فشل التحقق من الجلسة بعد تسجيل الدخول"))

                val userId = sessionUser.id
                keystoreManager.saveCurrentUserId(userId)
                currentSession.accessToken.let { keystoreManager.saveAuthToken(it) }

                // Fetch user public profile
                val profile = try {
                    supabaseProvider.postgrest.from(SupabaseConfig.PROFILES_TABLE)
                        .select {
                            filter {
                                eq("id", userId)
                            }
                        }.decodeSingleOrNull<ProfileDto>()
                } catch (e: Exception) {
                    null
                }

                // Fetch private contact details (phone and real email) for self only
                val contact = try {
                    supabaseProvider.postgrest.from(SupabaseConfig.USER_CONTACTS_TABLE)
                        .select {
                            filter {
                                eq("user_id", userId)
                            }
                        }.decodeSingleOrNull<UserContactDto>()
                } catch (e: Exception) {
                    null
                }

                val user = User(
                    id = userId,
                    username = profile?.username ?: cleanIdentifier.removePrefix("@"),
                    displayName = profile?.display_name ?: cleanIdentifier.removePrefix("@"),
                    avatarUrl = profile?.avatar_url,
                    bio = profile?.bio,
                    phone = contact?.phone_number,
                    email = contact?.email ?: if (isEmail) cleanIdentifier else null,
                    isOnline = true
                )

                // Cache in local Room database
                database.userDao().insertUser(
                    UserEntity(
                        id = user.id,
                        username = user.username,
                        displayName = user.displayName,
                        avatarUrl = user.avatarUrl,
                        bio = user.bio,
                        phone = user.phone,
                        email = user.email,
                        isOnline = true,
                        lastSeen = Instant.now()
                    )
                )

                Result.success(user)
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("Invalid login credentials", ignoreCase = true) == true ->
                        "اسم المستخدم أو كلمة المرور غير صحيحة"
                    e.message?.contains("Network", ignoreCase = true) == true ||
                    e.message?.contains("ConnectException", ignoreCase = true) == true ->
                        "تعذر الاتصال بالخادم، يرجى التحقق من اتصال الإنترنت"
                    else -> "فشل تسجيل الدخول: ${e.localizedMessage ?: "حدث خطأ غير متوقع"}"
                }
                Result.failure(Exception(errorMsg))
            }
        }

    override suspend fun register(
        username: String,
        password: String,
        displayName: String,
        phone: String?,
        email: String?
    ): Result<User> = withContext(Dispatchers.IO) {
        try {
            val cleanUsername = username.trim().removePrefix("@").lowercase()

            // 1. Check username uniqueness in profiles table
            val existing = try {
                supabaseProvider.postgrest.from(SupabaseConfig.PROFILES_TABLE)
                    .select {
                        filter {
                            eq("username", cleanUsername)
                        }
                    }.decodeList<ProfileDto>()
            } catch (e: Exception) {
                emptyList()
            }

            if (existing.isNotEmpty()) {
                return@withContext Result.failure(Exception("اسم المستخدم @$cleanUsername محجوز بالفعل، اختر اسماً آخر"))
            }

            // 2. Auth identity in Supabase Auth (deterministic and private)
            val authEmail = "$cleanUsername@tawasol.local"

            supabaseProvider.auth.signUpWith(Email) {
                this.email = authEmail
                this.password = password
                this.data = buildJsonObject {
                    put("username", cleanUsername)
                    put("display_name", displayName.trim())
                }
            }

            val session = supabaseProvider.auth.currentSessionOrNull()
            val userId = session?.user?.id ?: session?.user?.id ?: java.util.UUID.randomUUID().toString()

            keystoreManager.saveCurrentUserId(userId)
            session?.accessToken?.let { keystoreManager.saveAuthToken(it) }

            // 3. Save public profile (without sensitive phone/email)
            val profileInsert = buildJsonObject {
                put("id", userId)
                put("username", cleanUsername)
                put("display_name", displayName.trim())
                put("is_online", true)
            }

            try {
                supabaseProvider.postgrest.from(SupabaseConfig.PROFILES_TABLE)
                    .insert(profileInsert)
            } catch (e: Exception) {
                // If trigger already inserted, ignore duplicate
            }

            // 4. Save private contacts in user_contacts protected by RLS
            if (!phone.isNullOrBlank() || !email.isNullOrBlank()) {
                val contactInsert = buildJsonObject {
                    put("user_id", userId)
                    if (!phone.isNullOrBlank()) put("phone_number", phone.trim())
                    if (!email.isNullOrBlank()) put("email", email.trim())
                }
                try {
                    supabaseProvider.postgrest.from(SupabaseConfig.USER_CONTACTS_TABLE)
                        .insert(contactInsert)
                } catch (e: Exception) {
                    // Ignore if trigger handled it
                }
            }

            val domainUser = User(
                id = userId,
                username = cleanUsername,
                displayName = displayName.trim(),
                avatarUrl = null,
                bio = null,
                phone = phone?.trim(),
                email = email?.trim(),
                isOnline = true
            )

            // Cache in local Room DB
            database.userDao().insertUser(
                UserEntity(
                    id = domainUser.id,
                    username = domainUser.username,
                    displayName = domainUser.displayName,
                    phone = domainUser.phone,
                    email = domainUser.email,
                    isOnline = true
                )
            )

            Result.success(domainUser)
        } catch (e: Exception) {
            val errorMsg = when {
                e.message?.contains("User already registered", ignoreCase = true) == true ->
                    "يوجد حساب مسجل بالفعل بهذه البيانات"
                e.message?.contains("Network", ignoreCase = true) == true ->
                    "تعذر الاتصال بالخادم، يرجى التحقق من اتصالك بالإنترنت"
                else -> "تعذر إكمال التسجيل: ${e.localizedMessage ?: "حدث خطأ غير متوقع"}"
            }
            Result.failure(Exception(errorMsg))
        }
    }

    override suspend fun getCurrentUser(): User? = withContext(Dispatchers.IO) {
        val currentId = keystoreManager.getCurrentUserId() ?: return@withContext null

        // 1. First load from Room local database for instant offline support
        val localUser = database.userDao().getUserByIdSync(currentId)

        // 2. Fetch fresh data from Supabase if online
        try {
            val profile = supabaseProvider.postgrest.from(SupabaseConfig.PROFILES_TABLE)
                .select {
                    filter {
                        eq("id", currentId)
                    }
                }.decodeSingleOrNull<ProfileDto>()

            if (profile != null) {
                val contact = try {
                    supabaseProvider.postgrest.from(SupabaseConfig.USER_CONTACTS_TABLE)
                        .select {
                            filter {
                                eq("user_id", currentId)
                            }
                        }.decodeSingleOrNull<UserContactDto>()
                } catch (e: Exception) {
                    null
                }

                val freshUser = User(
                    id = profile.id,
                    username = profile.username,
                    displayName = profile.display_name,
                    avatarUrl = profile.avatar_url,
                    bio = profile.bio,
                    phone = contact?.phone_number ?: localUser?.phone,
                    email = contact?.email ?: localUser?.email,
                    isOnline = profile.is_online
                )

                // Update cache
                database.userDao().insertUser(
                    UserEntity(
                        id = freshUser.id,
                        username = freshUser.username,
                        displayName = freshUser.displayName,
                        avatarUrl = freshUser.avatarUrl,
                        bio = freshUser.bio,
                        phone = freshUser.phone,
                        email = freshUser.email,
                        isOnline = freshUser.isOnline
                    )
                )

                return@withContext freshUser
            }
        } catch (e: Exception) {
            // Network failure: fallback to local database
        }

        localUser?.let {
            User(
                id = it.id,
                username = it.username,
                displayName = it.displayName,
                avatarUrl = it.avatarUrl,
                bio = it.bio,
                phone = it.phone,
                email = it.email,
                isOnline = it.isOnline
            )
        }
    }

    override suspend fun restoreSession(): Result<User?> = withContext(Dispatchers.IO) {
        try {
            val currentId = keystoreManager.getCurrentUserId()
            if (currentId.isNullOrBlank()) {
                return@withContext Result.success(null)
            }

            // Check Supabase session
            val session = supabaseProvider.auth.currentSessionOrNull()
            if (session == null) {
                // Check if local token exists
                val token = keystoreManager.getAuthToken()
                if (token.isNullOrBlank()) {
                    return@withContext Result.success(null)
                }
            }

            val user = getCurrentUser()
            Result.success(user)
        } catch (e: Exception) {
            // Even if network fails, allow offline session restore
            val localUser = getCurrentUser()
            Result.success(localUser)
        }
    }

    override suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabaseProvider.auth.signOut()
        } catch (e: Exception) {
            // Ignore network errors on logout
        } finally {
            keystoreManager.clearSession()
        }
        Result.success(Unit)
    }
}
