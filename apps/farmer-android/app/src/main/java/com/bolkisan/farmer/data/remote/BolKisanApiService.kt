package com.bolkisan.farmer.data.remote

import com.bolkisan.farmer.data.remote.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface BolKisanApiService {

    @GET("health")
    suspend fun getHealth(): Response<BackendHealthDto>

    @POST("auth/request-otp")
    suspend fun requestOtp(@Body request: OTPRequestDto): Response<OTPResponseDto>

    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body request: OTPVerifyDto): Response<AuthTokenResponseDto>

    @GET("crops/")
    suspend fun getCrops(): Response<List<CropDto>>

    @POST("voice/query")
    suspend fun submitVoiceQuery(
        @Header("Authorization") token: String,
        @Body query: VoiceQueryDto
    ): Response<VoiceAnswerDto>

    @GET("tickets/")
    suspend fun getFarmerTickets(
        @Header("Authorization") token: String
    ): Response<List<TicketItemDto>>

    @POST("tickets/escalate")
    suspend fun escalateTicket(
        @Header("Authorization") token: String,
        @Body ticket: TicketCreateDto
    ): Response<TicketEscalateResponseDto>

    @POST("tickets/offline-sync")
    suspend fun syncOfflineTickets(
        @Header("Authorization") token: String,
        @Body request: OfflineSyncRequestDto
    ): Response<OfflineSyncResponseDto>

    @GET("tickets/{ticket_id}/messages")
    suspend fun getTicketMessages(
        @Path("ticket_id") ticketId: String,
        @Header("Authorization") token: String? = null
    ): Response<List<TicketMessageDto>>

    @POST("tickets/{ticket_id}/messages")
    suspend fun sendTicketMessage(
        @Path("ticket_id") ticketId: String,
        @Body body: SendTicketMessageRequest,
        @Header("Authorization") token: String? = null
    ): Response<TicketMessageDto>

    @POST("tickets/{ticket_id}/status")
    suspend fun updateTicketStatus(
        @Path("ticket_id") ticketId: String,
        @Body body: Map<String, String>,
        @Header("Authorization") token: String? = null
    ): Response<Map<String, Any>>

    @Multipart
    @POST("disease/diagnose")
    suspend fun diagnoseDisease(
        @Header("Authorization") token: String?,
        @Part file: MultipartBody.Part,
        @Part("crop_name") cropName: RequestBody,
        @Part("plant_part") plantPart: RequestBody? = null,
        @Part("farmer_notes") farmerNotes: RequestBody? = null,
        @Part("language") language: RequestBody? = null
    ): Response<DiseaseDiagnosisDto>

    // CONVERSATIONS API
    @GET("conversations/")
    suspend fun getConversations(
        @Header("Authorization") token: String
    ): Response<List<ConversationSummaryDto>>

    @POST("conversations/")
    suspend fun createConversation(
        @Header("Authorization") token: String,
        @Body request: ConversationCreateDto
    ): Response<ConversationSummaryDto>

    @GET("conversations/{conversation_id}")
    suspend fun getConversationDetail(
        @Header("Authorization") token: String,
        @Path("conversation_id") conversationId: String
    ): Response<ConversationDetailDto>

    @POST("conversations/{conversation_id}/messages")
    suspend fun postConversationMessage(
        @Header("Authorization") token: String,
        @Path("conversation_id") conversationId: String,
        @Body message: MessagePostDto
    ): Response<MessagePostResponseDto>

    // MANDI MARKET PRICES API
    @GET("market-prices")
    suspend fun getMarketPrices(
        @Query("commodity") commodity: String? = null,
        @Query("district") district: String? = null,
        @Query("state") state: String? = null,
        @Query("market") market: String? = null,
        @Query("limit") limit: Int = 100
    ): Response<MandiPricesResponseDto>

    @GET("market-prices/sync-status")
    suspend fun getMarketPricesSyncStatus(): Response<MandiSyncStatusDto>

    // SMS INTEGRATION API
    @POST("sms/incoming")
    suspend fun sendSMSIncoming(
        @Body request: SMSIncomingRequestDto
    ): Response<SMSIncomingResponseDto>

    @GET("sms/history")
    suspend fun getSMSHistory(
        @Query("phone_number") phoneNumber: String
    ): Response<SMSHistoryResponseDto>

    @GET("sms/segment-calc")
    suspend fun calculateSMSSegments(
        @Query("text") text: String
    ): Response<SMSSegmentCalcResponseDto>
}

