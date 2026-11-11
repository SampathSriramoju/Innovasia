package com.bolkisan.farmer.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bolkisan.farmer.data.remote.models.TicketMessageDto

@Entity(
    tableName = "ticket_messages",
    indices = [Index(value = ["ticketId"])]
)
data class TicketMessageEntity(
    @PrimaryKey
    val id: String,
    val ticketId: String,
    val senderId: String,
    val senderType: String, // "FARMER" or "OFFICER"
    val senderName: String,
    val msgType: String = "text", // "text", "image", "prescription"
    val content: String,
    val mediaUrl: String? = null,
    val createdAt: String,
    val localTimestamp: Long = System.currentTimeMillis()
) {
    fun toDto(): TicketMessageDto = TicketMessageDto(
        id = id,
        ticket_id = ticketId,
        sender_id = senderId,
        sender_type = senderType,
        sender_name = senderName,
        msg_type = msgType,
        content = content,
        media_url = mediaUrl,
        created_at = createdAt
    )

    companion object {
        fun fromDto(dto: TicketMessageDto): TicketMessageEntity = TicketMessageEntity(
            id = dto.id,
            ticketId = dto.ticket_id,
            senderId = dto.sender_id,
            senderType = dto.sender_type,
            senderName = dto.sender_name,
            msgType = dto.msg_type,
            content = dto.content,
            mediaUrl = dto.media_url,
            createdAt = dto.created_at
        )
    }
}
