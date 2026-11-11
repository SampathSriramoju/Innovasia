package com.bolkisan.farmer.data.local

import androidx.room.*
import com.bolkisan.farmer.data.local.entities.TicketMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TicketMessageDao {

    @Query("SELECT * FROM ticket_messages WHERE ticketId = :ticketId ORDER BY createdAt ASC, localTimestamp ASC")
    fun getMessagesForTicketFlow(ticketId: String): Flow<List<TicketMessageEntity>>

    @Query("SELECT * FROM ticket_messages WHERE ticketId = :ticketId ORDER BY createdAt ASC, localTimestamp ASC")
    suspend fun getMessagesForTicket(ticketId: String): List<TicketMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: TicketMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<TicketMessageEntity>)

    @Query("DELETE FROM ticket_messages WHERE ticketId = :ticketId")
    suspend fun deleteMessagesForTicket(ticketId: String)
}
