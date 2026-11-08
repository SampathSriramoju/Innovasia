package com.bolkisan.farmer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bolkisan.farmer.data.local.entities.OfflineKnowledgeEntity
import com.bolkisan.farmer.data.local.entities.PendingSyncTicketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineKnowledgeDao {

    @Query("SELECT COUNT(*) FROM offline_knowledge")
    suspend fun getKnowledgeCount(): Int

    @Query("SELECT * FROM offline_knowledge ORDER BY crop ASC, title ASC")
    fun getAllKnowledge(): Flow<List<OfflineKnowledgeEntity>>

    @Query("SELECT * FROM offline_knowledge WHERE language = :lang ORDER BY crop ASC, title ASC")
    fun getAllKnowledgeByLang(lang: String): Flow<List<OfflineKnowledgeEntity>>

    @Query("SELECT * FROM offline_knowledge WHERE crop = :crop AND language = :lang")
    fun getKnowledgeByCropAndLang(crop: String, lang: String): Flow<List<OfflineKnowledgeEntity>>

    @Query("SELECT * FROM offline_knowledge WHERE (crop LIKE '%' || :crop || '%' OR :crop LIKE '%' || crop || '%') AND (language = :lang OR :lang = '')")
    suspend fun getKnowledgeByCropSync(crop: String, lang: String): List<OfflineKnowledgeEntity>

    @Query("SELECT * FROM offline_knowledge WHERE category = :category AND language = :lang")
    fun getKnowledgeByCategory(category: String, lang: String): Flow<List<OfflineKnowledgeEntity>>

    @Query("SELECT * FROM offline_knowledge WHERE (content LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR crop LIKE '%' || :query || '%') AND (language = :lang OR :lang = '') LIMIT 10")
    suspend fun searchLocalKnowledge(query: String, lang: String): List<OfflineKnowledgeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnowledge(items: List<OfflineKnowledgeEntity>)

    // Outbox Sync Queue
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueuePendingSyncTicket(ticket: PendingSyncTicketEntity)

    @Query("SELECT * FROM pending_sync_tickets WHERE isSynced = 0 ORDER BY timestamp DESC")
    suspend fun getUnsyncedTickets(): List<PendingSyncTicketEntity>

    @Query("SELECT * FROM pending_sync_tickets ORDER BY timestamp DESC")
    fun getAllPendingTickets(): Flow<List<PendingSyncTicketEntity>>

    @Query("UPDATE pending_sync_tickets SET isSynced = 1 WHERE localId = :localId")
    suspend fun markTicketSynced(localId: String)
}
