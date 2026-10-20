package com.tawasol.app.domain.usecase

import com.tawasol.app.domain.model.PrivacySettings
import com.tawasol.app.domain.model.User
import com.tawasol.app.domain.model.VisibilityOption
import com.tawasol.app.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeUserRepository : UserRepository {
    var lastSearchedQuery: String? = null

    override fun getUser(userId: String): Flow<User?> = flowOf(null)
    override suspend fun getUserProfile(userId: String): Result<User> {
        return Result.success(User(id = userId, username = "test", displayName = "Test"))
    }

    override suspend fun searchUsers(query: String): Result<List<User>> {
        lastSearchedQuery = query
        return Result.success(
            listOf(
                User(id = "1", username = query, displayName = "Result $query", phone = null, email = null)
            )
        )
    }

    override suspend fun updateProfile(userId: String, displayName: String, bio: String?): Result<User> {
        return Result.success(User(id = userId, username = "test", displayName = displayName, bio = bio))
    }

    override suspend fun uploadAvatar(userId: String, imageBytes: ByteArray, mimeType: String): Result<String> {
        return Result.success("https://storage.supabase.co/avatars/test.jpg")
    }

    override fun getPrivacySettings(userId: String): Flow<PrivacySettings?> = flowOf(PrivacySettings(userId = userId))
    override suspend fun updatePrivacySettings(settings: PrivacySettings): Result<Unit> = Result.success(Unit)
}

class SearchUsersUseCaseTest {

    private lateinit var fakeUserRepository: FakeUserRepository
    private lateinit var searchUsersUseCase: SearchUsersUseCase

    @Before
    fun setUp() {
        fakeUserRepository = FakeUserRepository()
        searchUsersUseCase = SearchUsersUseCase(fakeUserRepository)
    }

    @Test
    fun `empty or blank search query returns empty list without calling repository`() = runBlocking {
        val result = searchUsersUseCase("   ")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
        assertEquals(null, fakeUserRepository.lastSearchedQuery)
    }

    @Test
    fun `at symbol prefix is stripped when searching`() = runBlocking {
        val result = searchUsersUseCase("@tawasol_admin")
        assertTrue(result.isSuccess)
        assertEquals("tawasol_admin", fakeUserRepository.lastSearchedQuery)
        val users = result.getOrNull()!!
        assertEquals(1, users.size)
        // Ensure sensitive fields are strictly null in search
        assertEquals(null, users.first().phone)
        assertEquals(null, users.first().email)
    }
}

class PrivacySettingsLogicTest {

    @Test
    fun `visibility option parsing matches expected enums`() {
        assertEquals(VisibilityOption.EVERYONE, VisibilityOption.fromString("everyone"))
        assertEquals(VisibilityOption.CONTACTS, VisibilityOption.fromString("contacts"))
        assertEquals(VisibilityOption.NOBODY, VisibilityOption.fromString("nobody"))
        assertEquals(VisibilityOption.EVERYONE, VisibilityOption.fromString("unknown_value"))
        assertEquals(VisibilityOption.EVERYONE, VisibilityOption.fromString(null))
    }

    @Test
    fun `default privacy settings have public defaults and read receipts enabled`() {
        val defaultSettings = PrivacySettings(userId = "user_1")
        assertEquals(VisibilityOption.EVERYONE, defaultSettings.lastSeenVisibility)
        assertEquals(VisibilityOption.EVERYONE, defaultSettings.onlineStatusVisibility)
        assertEquals(VisibilityOption.EVERYONE, defaultSettings.profilePhotoVisibility)
        assertEquals(VisibilityOption.EVERYONE, defaultSettings.bioVisibility)
        assertTrue(defaultSettings.readReceiptsEnabled)
    }
}
