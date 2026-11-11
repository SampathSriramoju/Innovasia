package com.bolkisan.farmer.data.remote.models

import com.google.gson.annotations.SerializedName

/**
 * Bol Kisan SMS Integration Models
 * Supports 3GPP TS 23.040 / ETSI GSM 03.38 compliant SMS payloads,
 * telecom segment accounting, and carrier simulation responses.
 */

data class SMSIncomingRequestDto(
    @SerializedName("sender_number")
    val sender_number: String,

    @SerializedName("message_text")
    val message_text: String,

    @SerializedName("language")
    val language: String? = null,

    @SerializedName("message_id")
    val message_id: String? = null,

    @SerializedName("idempotency_key")
    val idempotency_key: String? = null,

    @SerializedName("simulate_failure")
    val simulate_failure: Boolean? = null
) {
    val senderNumber: String get() = sender_number
    val messageText: String get() = message_text
    val messageId: String? get() = message_id
    val idempotencyKey: String? get() = idempotency_key
    val simulateFailure: Boolean? get() = simulate_failure
}

data class SMSReplyDetailDto(
    @SerializedName("message_text")
    val message_text: String = "",

    @SerializedName("is_safe")
    val is_safe: Boolean = true,

    @SerializedName("ticket_number")
    val ticket_number: String? = null,

    @SerializedName("language")
    val language: String? = null,

    @SerializedName("char_count")
    val char_count: Int = 0,

    @SerializedName("segments")
    val segments: Int = 1,

    @SerializedName("encoding")
    val encoding: String = "GSM-7",

    @SerializedName("disclaimer")
    val disclaimer: String? = null,

    @SerializedName("is_simulated")
    val is_simulated: Boolean = true
) {
    val messageText: String get() = message_text
    val isSafe: Boolean get() = is_safe
    val ticketNumber: String? get() = ticket_number
    val charCount: Int get() = char_count
    val isSimulated: Boolean get() = is_simulated
}

data class SMSIncomingResponseDto(
    @SerializedName("success")
    val success: Boolean = true,

    @SerializedName("reply_text")
    val reply_text: String? = null,

    @SerializedName("reply")
    val reply: SMSReplyDetailDto? = null,

    @SerializedName("outbound_message")
    val outbound_message: SMSReplyDetailDto? = null,

    @SerializedName("inbound_message")
    val inbound_message: Map<String, Any>? = null,

    @SerializedName("safety_alert")
    val safety_alert: Boolean = false,

    @SerializedName("ticket_escalated")
    val ticket_escalated: Boolean = false,

    @SerializedName("ticket_id")
    val ticket_id: String? = null,

    @SerializedName("is_demo")
    val is_demo: Boolean = true,

    @SerializedName("is_simulated")
    val is_simulated: Boolean = true,

    @SerializedName("error")
    val error: String? = null
) {
    val replyText: String? get() = reply_text
    val outboundMessage: SMSReplyDetailDto? get() = outbound_message
    val inboundMessage: Map<String, Any>? get() = inbound_message
    val safetyAlert: Boolean get() = safety_alert
    val ticketEscalated: Boolean get() = ticket_escalated
    val ticketId: String? get() = ticket_id
    val isDemo: Boolean get() = is_demo
    val isSimulated: Boolean get() = is_simulated
}

data class SMSSegmentCalcResponseDto(
    @SerializedName("char_count")
    val char_count: Int = 0,

    @SerializedName("encoding")
    val encoding: String = "GSM-7",

    @SerializedName("is_unicode")
    val is_unicode: Boolean = false,

    @SerializedName("units")
    val units: Int = 0,

    @SerializedName("segments")
    val segments: Int = 0,

    @SerializedName("max_single")
    val max_single: Int = 160,

    @SerializedName("max_multipart")
    val max_multipart: Int = 153,

    @SerializedName("explanation")
    val explanation: String = ""
) {
    val charCount: Int get() = char_count
    val isUnicode: Boolean get() = is_unicode
    val maxSingle: Int get() = max_single
    val maxMultipart: Int get() = max_multipart
}

data class SMSHistoryResponseDto(
    @SerializedName("phone_number")
    val phone_number: String = "",

    @SerializedName("count")
    val count: Int = 0,

    @SerializedName("messages")
    val messages: List<Map<String, Any>> = emptyList(),

    @SerializedName("is_demo")
    val is_demo: Boolean = true,

    @SerializedName("is_simulated")
    val is_simulated: Boolean = true
) {
    val phoneNumber: String get() = phone_number
    val isDemo: Boolean get() = is_demo
    val isSimulated: Boolean get() = is_simulated
}
