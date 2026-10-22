package com.tawasol.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tawasol.app.core.database.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, lastMessageTimestamp DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE accountUserId = :accountUserId OR accountUserId = '' ORDER BY isPinned DESC, lastMessageTimestamp DESC")
    fun getConversationsForAccount(accountUserId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversationById(conversationId: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :conversationId AND (accountUserId = :accountUserId OR accountUserId = '')")
    suspend fun getConversationByIdAndAccount(conversationId: String, accountUserId: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    fun getConversationFlow(conversationId: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :conversationId AND (accountUserId = :accountUserId OR accountUserId = '')")
    fun getConversationFlowForAccount(conversationId: String, accountUserId: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE peerUserId = :peerUserId LIMIT 1")
    suspend fun getConversationByPeerUserId(peerUserId: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE peerUserId = :peerUserId AND (accountUserId = :accountUserId OR accountUserId = '') LIMIT 1")
    suspend fun getConversationByPeerUserIdAndAccount(peerUserId: String, accountUserId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<ConversationEntity>)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET lastMessageText = :text, lastMessageTimestamp = :timestamp, updatedAt = :timestamp WHERE id = :conversationId")
    suspend fun updateLastMessage(conversationId: String, text: String?, timestamp: java.time.Instant)

    @Query("UPDATE conversations SET unreadCount = unreadCount + 1 WHERE id = :conversationId")
    suspend fun incrementUnreadCount(conversationId: String)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :conversationId")
    suspend fun markAsRead(conversationId: String)

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("DELETE FROM conversations WHERE accountUserId = :accountUserId")
    suspend fun deleteConversationsForAccount(accountUserId: String)
}
