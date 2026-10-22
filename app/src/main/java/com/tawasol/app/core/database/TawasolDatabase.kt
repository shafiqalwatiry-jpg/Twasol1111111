package com.tawasol.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tawasol.app.core.database.converter.DateConverters
import com.tawasol.app.core.database.dao.ConversationDao
import com.tawasol.app.core.database.dao.MessageDao
import com.tawasol.app.core.database.dao.PrivacySettingsDao
import com.tawasol.app.core.database.dao.UserDao
import com.tawasol.app.core.database.entity.ConversationEntity
import com.tawasol.app.core.database.entity.MessageEntity
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tawasol.app.core.database.entity.OutboxMessageEntity
import com.tawasol.app.core.database.entity.PrivacySettingsEntity
import com.tawasol.app.core.database.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        PrivacySettingsEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        OutboxMessageEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(DateConverters::class)
abstract class TawasolDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun privacySettingsDao(): PrivacySettingsDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

    companion object {
        private const val DATABASE_NAME = "tawasol.db"

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Safely add accountUserId column and indices without deleting existing messages or conversations
                db.execSQL("ALTER TABLE conversations ADD COLUMN accountUserId TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_accountUserId ON conversations(accountUserId)")
                db.execSQL("ALTER TABLE messages ADD COLUMN accountUserId TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_accountUserId ON messages(accountUserId)")
            }
        }

        @Volatile
        private var INSTANCE: TawasolDatabase? = null

        fun getInstance(context: Context): TawasolDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TawasolDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
