package com.bolkisan.farmer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_messages")
data class SMSMessageEntity(
    @PrimaryKey val id: String,
    val senderNumber: String,
    val recipientNumber: String,
    val messageText: String,
    val isOutgoing: Boolean, // true = farmer sent, false = response
    val timestamp: Long = System.currentTimeMillis(),
    val deliveryStatus: String, // "SIMULATED_DELIVERED", "SAVED_LOCALLY", "SYNCED", "FAILED"
    val isOfflineCached: Boolean = false,
    val ticketNumber: String? = null,
    val language: String = "te",
    val charCount: Int = 0,
    val segments: Int = 1,
    val encoding: String = "GSM-7",
    val isSynced: Boolean = false
)
