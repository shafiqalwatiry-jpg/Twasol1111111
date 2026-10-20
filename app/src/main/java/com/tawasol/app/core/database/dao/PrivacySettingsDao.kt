package com.tawasol.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tawasol.app.core.database.entity.PrivacySettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrivacySettingsDao {
    @Query("SELECT * FROM privacy_settings WHERE userId = :userId")
    fun getPrivacySettings(userId: String): Flow<PrivacySettingsEntity?>

    @Query("SELECT * FROM privacy_settings WHERE userId = :userId")
    suspend fun getPrivacySettingsSync(userId: String): PrivacySettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: PrivacySettingsEntity)

    @Query("DELETE FROM privacy_settings WHERE userId = :userId")
    suspend fun deleteSettings(userId: String)
}
