package com.bolkisan.farmer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bolkisan.farmer.data.local.entities.SMSMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SMSMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: SMSMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<SMSMessageEntity>)

    @Query("SELECT * FROM sms_messages WHERE senderNumber = :phone OR recipientNumber = :phone ORDER BY timestamp ASC")
    fun getMessagesForPhone(phone: String): Flow<List<SMSMessageEntity>>

    @Query("SELECT * FROM sms_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<SMSMessageEntity>>

    @Query("SELECT * FROM sms_messages ORDER BY timestamp ASC")
    suspend fun getAllMessagesSync(): List<SMSMessageEntity>

    @Query("SELECT * FROM sms_messages WHERE isSynced = 0 AND isOutgoing = 1 ORDER BY timestamp ASC")
    suspend fun getUnsyncedOutgoingMessages(): List<SMSMessageEntity>

    @Query("UPDATE sms_messages SET isSynced = 1, deliveryStatus = 'SYNCED' WHERE id = :id")
    suspend fun markAsSynced(id: String)

    @Query("UPDATE sms_messages SET deliveryStatus = :status WHERE id = :id")
    suspend fun updateDeliveryStatus(id: String, status: String)

    @Query("UPDATE sms_messages SET ticketNumber = :ticketNumber WHERE id = :id")
    suspend fun updateTicketNumber(id: String, ticketNumber: String)

    @Query("DELETE FROM sms_messages WHERE senderNumber = :phone OR recipientNumber = :phone")
    suspend fun clearHistoryForPhone(phone: String)

    @Query("DELETE FROM sms_messages")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM sms_messages")
    suspend fun getMessageCount(): Int
}
