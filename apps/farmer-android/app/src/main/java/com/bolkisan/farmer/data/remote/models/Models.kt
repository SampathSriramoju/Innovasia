package com.bolkisan.farmer.data.remote.models

import com.google.gson.annotations.SerializedName

// AUTH
data class OTPRequestDto(
    @SerializedName("phone_number") val phoneNumber: String
)

data class OTPResponseDto(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String,
    @SerializedName("dev_otp") val devOtp: String?
)

data class OTPVerifyDto(
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("otp") val otp: String,
    @SerializedName("preferred_lang") val preferredLang: String = "te",
    @SerializedName("full_name") val fullName: String? = null
)

data class AuthTokenResponseDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("user") val user: UserDto
)

data class UserDto(
    @SerializedName("id") val id: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("role") val role: String,
    @SerializedName("preferred_lang") val preferredLang: String
)

// VOICE QUERY
data class VoiceQueryDto(
    @SerializedName("query_text") val queryText: String,
    @SerializedName("language") val language: String = "te",
    @SerializedName("crop_name") val cropName: String = "Cotton",
    @SerializedName("pest_or_disease") val pestOrDisease: String? = null,
    @SerializedName("chemical_mentioned") val chemicalMentioned: String? = null
)

data class VoiceAnswerDto(
    @SerializedName("status") val status: String, // 'answered' or 'escalated'
    @SerializedName("is_safe") val isSafe: Boolean,
    @SerializedName("response_text") val responseText: String,
    @SerializedName("ticket_number") val ticketNumber: String? = null,
    @SerializedName("ticket_id") val ticketId: String? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("citations") val citations: List<String>? = null,
    @SerializedName("warnings") val warnings: List<String>? = null,
    @SerializedName("tts_playback_url") val ttsPlaybackUrl: String? = null
)

// CROPS
data class CropDto(
    @SerializedName("id") val id: String,
    @SerializedName("scientific_name") val scientificName: String,
    @SerializedName("name_en") val nameEn: String,
    @SerializedName("name_te") val nameTe: String,
    @SerializedName("name_hi") val nameHi: String,
    @SerializedName("category") val category: String,
    @SerializedName("season") val season: String?
)

// TICKETS
data class TicketCreateDto(
    @SerializedName("crop_name") val cropName: String = "Maize",
    @SerializedName("state") val state: String = "Telangana",
    @SerializedName("district") val district: String = "Warangal",
    @SerializedName("mandal") val mandal: String = "Geesugonda",
    @SerializedName("village") val village: String = "Dharmaram",
    @SerializedName("escalation_reason") val escalationReason: String,
    @SerializedName("original_question_transcript") val originalQuestionTranscript: String,
    @SerializedName("priority") val priority: String = "high"
)

data class TicketEscalateResponseDto(
    @SerializedName("status") val status: String,
    @SerializedName("ticket_id") val ticketId: String,
    @SerializedName("ticket_number") val ticketNumber: String,
    @SerializedName("mandal") val mandal: String,
    @SerializedName("priority") val priority: String,
    @SerializedName("message") val message: String
)

data class TicketItemDto(
    @SerializedName("id") val id: String,
    @SerializedName("ticket_number") val ticketNumber: String,
    @SerializedName("status") val status: String,
    @SerializedName("priority") val priority: String,
    @SerializedName("original_question_transcript") val transcript: String,
    @SerializedName("escalation_reason") val reason: String,
    @SerializedName("resolution_prescription") val resolutionPrescription: String?,
    @SerializedName("officer_response") val officerResponse: String? = null,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("resolved_at") val resolvedAt: String?
)

// DISEASE DIAGNOSIS
data class DiseaseDiagnosisDto(
    @SerializedName("disease_name") val diseaseName: String,
    @SerializedName("crop_name") val cropName: String? = null,
    @SerializedName("confidence") val confidence: Double? = null,
    @SerializedName("severity") val severity: String? = null,
    @SerializedName("symptoms") val symptoms: String? = null,
    @SerializedName("treatment") val treatment: String? = null,
    @SerializedName("prevention") val prevention: String? = null,
    @SerializedName("citations") val citations: List<String>? = null,
    @SerializedName("is_safe") val isSafe: Boolean = true,
    @SerializedName("ticket_number") val ticketNumber: String? = null,
    @SerializedName("is_offline_sync_pending") val isOfflineSyncPending: Boolean = false
)
