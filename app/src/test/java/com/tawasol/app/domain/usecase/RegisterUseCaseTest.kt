package com.tawasol.app.domain.usecase

import com.tawasol.app.domain.model.User
import com.tawasol.app.domain.repository.AuthRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeAuthRepository : AuthRepository {
    override val currentUserId: String? = null
    var lastRegisteredUsername: String? = null
    var lastRegisteredEmail: String? = null
    var lastRegisteredPhone: String? = null

    override suspend fun login(loginIdentifier: String, password: String): Result<User> {
        return Result.success(User(id = "1", username = loginIdentifier, displayName = loginIdentifier))
    }

    override suspend fun register(
        username: String,
        password: String,
        displayName: String,
        phone: String?,
        email: String?
    ): Result<User> {
        lastRegisteredUsername = username
        lastRegisteredPhone = phone
        lastRegisteredEmail = email
        return Result.success(
            User(
                id = "user_123",
                username = username,
                displayName = displayName,
                phone = phone,
                email = email
            )
        )
    }

    override suspend fun getCurrentUser(): User? = null
    override suspend fun restoreSession(): Result<User?> = Result.success(null)
    override suspend fun logout(): Result<Unit> = Result.success(Unit)
}

class RegisterUseCaseTest {

    private lateinit var fakeAuthRepository: FakeAuthRepository
    private lateinit var registerUseCase: RegisterUseCase

    @Before
    fun setUp() {
        fakeAuthRepository = FakeAuthRepository()
        registerUseCase = RegisterUseCase(fakeAuthRepository)
    }

    @Test
    fun `valid registration succeeds and cleans username prefix and case`() = runBlocking {
        val result = registerUseCase(
            username = "@Ahmed_2026",
            password = "StrongPassword123",
            displayName = "أحمد",
            phone = "+966500000000",
            email = "ahmed@example.com"
        )

        assertTrue(result.isSuccess)
        assertEquals("ahmed_2026", fakeAuthRepository.lastRegisteredUsername)
        assertEquals("+966500000000", fakeAuthRepository.lastRegisteredPhone)
        assertEquals("ahmed@example.com", fakeAuthRepository.lastRegisteredEmail)
    }

    @Test
    fun `short username fails validation`() = runBlocking {
        val result = registerUseCase(
            username = "ab",
            password = "StrongPassword123",
            displayName = "أحمد"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("3 إلى 30") == true)
    }

    @Test
    fun `reserved username is rejected`() = runBlocking {
        val reservedNames = listOf("admin", "support", "root", "tawasol", "bot")
        for (name in reservedNames) {
            val result = registerUseCase(
                username = name,
                password = "StrongPassword123",
                displayName = "مسؤول"
            )
            assertTrue("Expected failure for reserved name: $name", result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("محجوز") == true)
        }
    }

    @Test
    fun `username with forbidden special characters is rejected`() = runBlocking {
        val invalidNames = listOf("ahmed-ali", "user@name", "user.name", "user#1")
        for (name in invalidNames) {
            val result = registerUseCase(
                username = name,
                password = "StrongPassword123",
                displayName = "مستخدم"
            )
            assertTrue("Expected failure for invalid characters: $name", result.isFailure)
        }
    }

    @Test
    fun `weak password under 8 characters is rejected`() = runBlocking {
        val result = registerUseCase(
            username = "valid_user",
            password = "Pass1",
            displayName = "مستخدم"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("8 أحرف") == true)
    }

    @Test
    fun `password without digits is rejected`() = runBlocking {
        val result = registerUseCase(
            username = "valid_user",
            password = "PasswordOnlyLetters",
            displayName = "مستخدم"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("مزيج") == true)
    }

    @Test
    fun `invalid email format is rejected`() = runBlocking {
        val result = registerUseCase(
            username = "valid_user",
            password = "Password123",
            displayName = "مستخدم",
            email = "invalid-email-without-at"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("صيغة البريد") == true)
    }

    @Test
    fun `empty phone or email is treated as null`() = runBlocking {
        val result = registerUseCase(
            username = "clean_user",
            password = "Password123",
            displayName = "مستخدم",
            phone = "   ",
            email = ""
        )

        assertTrue(result.isSuccess)
        assertEquals(null, fakeAuthRepository.lastRegisteredPhone)
        assertEquals(null, fakeAuthRepository.lastRegisteredEmail)
    }
}
