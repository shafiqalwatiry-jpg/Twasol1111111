package com.tawasol.app.domain.usecase

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LoginUseCaseTest {

    private lateinit var fakeAuthRepository: FakeAuthRepository
    private lateinit var loginUseCase: LoginUseCase

    @Before
    fun setUp() {
        fakeAuthRepository = FakeAuthRepository()
        loginUseCase = LoginUseCase(fakeAuthRepository)
    }

    @Test
    fun `empty identifier fails login`() = runBlocking {
        val result = loginUseCase("   ", "password123")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("يرجى إدخال") == true)
    }

    @Test
    fun `short password fails login`() = runBlocking {
        val result = loginUseCase("user_1", "123")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("6 أحرف") == true)
    }

    @Test
    fun `valid credentials succeed with username or email`() = runBlocking {
        val resultUsername = loginUseCase("@ahmed_test", "password123")
        assertTrue(resultUsername.isSuccess)
        assertEquals("@ahmed_test", resultUsername.getOrNull()?.username)

        val resultEmail = loginUseCase("ahmed@test.com", "password123")
        assertTrue(resultEmail.isSuccess)
    }
}
