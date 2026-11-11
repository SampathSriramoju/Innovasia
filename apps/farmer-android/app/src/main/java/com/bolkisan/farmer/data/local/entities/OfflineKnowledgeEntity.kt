package com.bolkisan.farmer.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_knowledge")
data class OfflineKnowledgeEntity(
    @PrimaryKey
    val id: String,
    val collectionId: String, // KB01-KB23
    val crop: String,
    val category: String, // 'pesticide', 'disease', 'cultivation', 'fertilizer', 'weather'
    val title: String,
    val content: String,
    val sourceCitation: String,
    val language: String, // 'te', 'hi', 'en'
    val lastVerifiedDate: String
)

@Entity(tableName = "pending_sync_tickets")
data class PendingSyncTicketEntity(
    @PrimaryKey
    val localId: String,
    val queryText: String,
    val cropName: String,
    val audioPath: String? = null,
    val imagePath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val farmerId: String? = null,
    val conversationContext: String? = null,
    val language: String = "te",
    val syncStatus: String = if (isSynced) "SENT_TO_OFFICER" else "PENDING_SYNC",
    val backendTicketNumber: String? = null
)
