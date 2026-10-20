package com.tawasol.app.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "privacy_settings")
data class PrivacySettingsEntity(
    @PrimaryKey
    val userId: String,
    val lastSeenVisibility: String = "everyone",
    val onlineStatusVisibility: String = "everyone",
    val profilePhotoVisibility: String = "everyone",
    val bioVisibility: String = "everyone",
    val readReceiptsEnabled: Boolean = true,
    val updatedAt: Instant = Instant.now()
)
