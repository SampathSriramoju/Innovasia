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
    @SerializedName("crop_name") val cropName: String? = null,
    @SerializedName("pest_or_disease") val pestOrDisease: String? = null,
    @SerializedName("chemical_mentioned") val chemicalMentioned: String? = null,
    @SerializedName("conversation_history") val conversationHistory: List<Map<String, String>>? = null
)

data class VoiceAnswerDto(
    @SerializedName("status") val status: String, // 'answered', 'escalated', 'needs_clarification', 'answered_offline_fallback'
    @SerializedName("is_safe") val isSafe: Boolean,
    @SerializedName("response_text") val responseText: String,
    @SerializedName("ticket_number") val ticketNumber: String? = null,
    @SerializedName("ticket_id") val ticketId: String? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("crop_identified") val cropIdentified: String? = null,
    @SerializedName("crop_source") val cropSource: String? = null,
    @SerializedName("is_ambiguous") val isAmbiguous: Boolean? = null,
    @SerializedName("clarification_prompt") val clarificationPrompt: String? = null,
    @SerializedName("citations") val citations: List<String>? = null,
    @SerializedName("warnings") val warnings: List<String>? = null,
    @SerializedName("follow_up_suggestions") val followUpSuggestions: List<String>? = null,
    @SerializedName("is_fallback") val isFallback: Boolean? = null,
    @SerializedName("model_used") val modelUsed: String? = null,
    @SerializedName("provider") val provider: String? = null,
    @SerializedName("tts_playback_url") val ttsPlaybackUrl: String? = null,
    @SerializedName("asr_latency_ms") val asrLatencyMs: Long? = null,
    @SerializedName("retrieval_latency_ms") val retrievalLatencyMs: Long? = null,
    @SerializedName("inference_latency_ms") val inferenceLatencyMs: Long? = null,
    @SerializedName("ttft_ms") val ttftMs: Long? = null,
    @SerializedName("tokens_per_sec") val tokensPerSec: Double? = null,
    @SerializedName("safety_gate_latency_ms") val safetyGateLatencyMs: Long? = null,
    @SerializedName("tts_first_audio_ms") val ttsFirstAudioLatencyMs: Long? = null,
    @SerializedName("total_v2v_latency_ms") val totalVoiceToVoiceLatencyMs: Long? = null,
    @SerializedName("is_cold_start") val isColdStart: Boolean? = null,
    @SerializedName("meets_6sec_sla") val meets6SecSla: Boolean? = null
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
    @SerializedName("crop_name") val cropName: String? = null,
    @SerializedName("crop") val crop: String? = null,
    @SerializedName("original_question_transcript") val transcript: String,
    @SerializedName("escalation_reason") val reason: String,
    @SerializedName("resolution_prescription") val resolutionPrescription: String?,
    @SerializedName("officer_response") val officerResponse: String? = null,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("resolved_at") val resolvedAt: String?
) {
    val displayCrop: String
        get() = cropName ?: crop ?: "General"
}

data class TicketMessageDto(
    @SerializedName("id") val id: String,
    @SerializedName("ticket_id") val ticket_id: String,
    @SerializedName("sender_id") val sender_id: String,
    @SerializedName("sender_type") val sender_type: String, // "FARMER" or "OFFICER"
    @SerializedName("sender_name") val sender_name: String,
    @SerializedName("msg_type") val msg_type: String = "text", // "text", "image", "prescription"
    @SerializedName("content") val content: String,
    @SerializedName("media_url") val media_url: String? = null,
    @SerializedName("created_at") val created_at: String
) {
    val ticketId: String get() = ticket_id
    val senderId: String get() = sender_id
    val senderType: String get() = sender_type
    val senderName: String get() = sender_name
    val msgType: String get() = msg_type
    val mediaUrl: String? get() = media_url
    val createdAt: String get() = created_at
}

data class SendTicketMessageRequest(
    @SerializedName("content") val content: String,
    @SerializedName("msg_type") val msg_type: String = "text",
    @SerializedName("media_url") val media_url: String? = null
)

// DISEASE DIAGNOSIS
data class DiseaseDiagnosisDto(
    @SerializedName("status") val status: String? = null,
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
    @SerializedName("is_offline_sync_pending") val isOfflineSyncPending: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("farmer_explanation") val farmerExplanation: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("image_sha256") val imageSha256: String? = null,
    @SerializedName("image_dimensions") val imageDimensions: String? = null
)

// OFFLINE SYNC
data class OfflineSyncItemDto(
    @SerializedName("client_uuid") val clientUuid: String,
    @SerializedName("crop_name") val cropName: String?,
    @SerializedName("question_text") val questionText: String,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("language") val language: String? = "te",
    @SerializedName("created_at_epoch") val createdAtEpoch: Long? = null
)

data class OfflineSyncRequestDto(
    @SerializedName("pending_tickets") val pendingTickets: List<OfflineSyncItemDto>
)

data class OfflineSyncResponseDto(
    @SerializedName("status") val status: String,
    @SerializedName("synced_count") val syncedCount: Int,
    @SerializedName("synced_client_uuids") val syncedClientUuids: List<String>? = null,
    @SerializedName("tickets") val tickets: List<Map<String, Any>>? = null
)

// CONVERSATIONS
data class ConversationCreateDto(
    @SerializedName("title") val title: String,
    @SerializedName("crop") val crop: String = "General",
    @SerializedName("language") val language: String = "te"
)

data class ConversationSummaryDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("crop") val crop: String,
    @SerializedName("language") val language: String,
    @SerializedName("message_count") val messageCount: Int,
    @SerializedName("escalation_status") val escalationStatus: String,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class ConversationMessageDto(
    @SerializedName("id") val id: String,
    @SerializedName("sender_type") val senderType: String, // FARMER, AI, OFFICER
    @SerializedName("original_query") val originalQuery: String?,
    @SerializedName("transcript") val transcript: String?,
    @SerializedName("response") val response: String,
    @SerializedName("source_citations") val sourceCitations: List<String>? = null,
    @SerializedName("language") val language: String,
    @SerializedName("is_safe") val isSafe: Boolean = true,
    @SerializedName("is_escalated") val isEscalated: Boolean = false,
    @SerializedName("ticket_id") val ticketId: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null
)

data class ConversationDetailDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("crop") val crop: String,
    @SerializedName("language") val language: String,
    @SerializedName("message_count") val messageCount: Int,
    @SerializedName("escalation_status") val escalationStatus: String,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("messages") val messages: List<ConversationMessageDto>
)

data class MessagePostDto(
    @SerializedName("query_text") val queryText: String,
    @SerializedName("transcript") val transcript: String? = null,
    @SerializedName("language") val language: String? = "te",
    @SerializedName("crop") val crop: String? = "General",
    @SerializedName("pest_or_disease") val pestOrDisease: String? = null,
    @SerializedName("chemical_mentioned") val chemicalMentioned: String? = null,
    @SerializedName("force_escalation") val forceEscalation: Boolean = false
)

data class MessagePostResponseDto(
    @SerializedName("status") val status: String,
    @SerializedName("is_safe") val isSafe: Boolean,
    @SerializedName("is_escalated") val isEscalated: Boolean,
    @SerializedName("response_text") val responseText: String,
    @SerializedName("ticket_number") val ticketNumber: String? = null,
    @SerializedName("ticket_id") val ticketId: String? = null,
    @SerializedName("citations") val citations: List<String>? = null
)

// SYSTEM HEALTH
data class BackendHealthDto(
    @SerializedName("status") val status: String,
    @SerializedName("service") val service: String? = null,
    @SerializedName("version") val version: String? = null,
    @SerializedName("environment") val environment: String? = null,
    @SerializedName("database") val database: DatabaseHealthDto? = null,
    @SerializedName("ai_provider") val aiProvider: AiProviderHealthDto? = null,
    @SerializedName("safety_gate") val safetyGate: Map<String, Any>? = null
)

data class DatabaseHealthDto(
    @SerializedName("status") val status: String,
    @SerializedName("engine") val engine: String? = null,
    @SerializedName("dialect") val dialect: String? = null,
    @SerializedName("error") val error: String? = null
)

data class AiProviderHealthDto(
    @SerializedName("provider") val provider: String? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("model_name") val modelName: String? = null,
    @SerializedName("is_configured") val isConfigured: Boolean = false,
    @SerializedName("gemini_configured") val geminiConfigured: Boolean = false,
    @SerializedName("client_ready") val clientReady: Boolean = false,
    @SerializedName("status") val status: String? = null
) {
    val isGeminiReady: Boolean
        get() = geminiConfigured || isConfigured || clientReady
}
