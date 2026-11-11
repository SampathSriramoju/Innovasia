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

    @Query("""
        SELECT * FROM offline_knowledge 
        WHERE :crop IS NOT NULL 
          AND TRIM(:crop) != '' 
          AND LOWER(TRIM(:crop)) NOT IN ('general', 'all', 'agri', 'farming', 'none', 'unknown', 'null', 'unspecified')
          AND (crop LIKE '%' || :crop || '%' OR :crop LIKE '%' || crop || '%') 
          AND (language = :lang OR :lang = '')
    """)
    suspend fun getKnowledgeByCropSync(crop: String, lang: String): List<OfflineKnowledgeEntity>

    @Query("SELECT * FROM offline_knowledge WHERE category = :category AND language = :lang")
    fun getKnowledgeByCategory(category: String, lang: String): Flow<List<OfflineKnowledgeEntity>>

    @Query("""
        SELECT * FROM offline_knowledge 
        WHERE :query IS NOT NULL 
          AND TRIM(:query) != '' 
          AND (content LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR crop LIKE '%' || :query || '%') 
          AND (language = :lang OR :lang = '') 
        LIMIT 10
    """)
    suspend fun searchLocalKnowledge(query: String, lang: String): List<OfflineKnowledgeEntity>

    @Query("SELECT * FROM offline_knowledge WHERE (crop = 'Organic' OR category = 'cultivation') AND (language = :lang OR :lang = '') LIMIT 5")
    suspend fun getGeneralFarmingCards(lang: String): List<OfflineKnowledgeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnowledge(items: List<OfflineKnowledgeEntity>)

    // Outbox Sync Queue
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueuePendingSyncTicket(ticket: PendingSyncTicketEntity)

    @Query("SELECT * FROM pending_sync_tickets WHERE isSynced = 0 ORDER BY timestamp DESC")
    suspend fun getUnsyncedTickets(): List<PendingSyncTicketEntity>

    @Query("SELECT COUNT(*) FROM pending_sync_tickets WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_sync_tickets WHERE isSynced = 0")
    suspend fun getUnsyncedCountSync(): Int

    @Query("SELECT * FROM pending_sync_tickets ORDER BY timestamp DESC")
    fun getAllPendingTickets(): Flow<List<PendingSyncTicketEntity>>

    @Query("SELECT * FROM pending_sync_tickets ORDER BY timestamp DESC")
    suspend fun getAllPendingTicketsSync(): List<PendingSyncTicketEntity>

    @Query("SELECT * FROM pending_sync_tickets WHERE localId = :localId LIMIT 1")
    suspend fun getPendingTicketById(localId: String): PendingSyncTicketEntity?

    @Query("UPDATE pending_sync_tickets SET isSynced = 1, syncStatus = :status, backendTicketNumber = :ticketNum WHERE localId = :localId")
    suspend fun markTicketSyncedWithDetails(localId: String, status: String = "SENT_TO_OFFICER", ticketNum: String? = null)

    @Query("UPDATE pending_sync_tickets SET syncStatus = :status WHERE localId = :localId")
    suspend fun updateSyncStatus(localId: String, status: String)

    @Query("UPDATE pending_sync_tickets SET isSynced = 1, syncStatus = 'SENT_TO_OFFICER' WHERE localId = :localId")
    suspend fun markTicketSynced(localId: String)
}
