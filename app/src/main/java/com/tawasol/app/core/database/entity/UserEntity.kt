package com.tawasol.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val phone: String? = null, // Only stored for the logged-in user
    val email: String? = null, // Only stored for the logged-in user
    val isOnline: Boolean = false,
    val lastSeen: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
