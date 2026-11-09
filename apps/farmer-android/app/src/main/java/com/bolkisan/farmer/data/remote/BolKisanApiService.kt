package com.bolkisan.farmer.data.remote

import com.bolkisan.farmer.data.remote.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface BolKisanApiService {

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
}
