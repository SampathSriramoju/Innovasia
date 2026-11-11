package com.bolkisan.farmer

import com.bolkisan.farmer.data.remote.BolKisanApiService
import com.bolkisan.farmer.data.remote.models.*
import com.bolkisan.farmer.domain.sms.SmsSegmentCalculator
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.POST

class SmsIntegrationTest {

    private val gson = Gson()

    // =========================================================================
    // 1. TELECOM SMS SEGMENT CALCULATOR TESTS (3GPP TS 23.040 / ETSI GSM 03.38)
    // =========================================================================

    @Test
    fun testEmptyMessageCalculation() {
        val details = SmsSegmentCalculator.calculateDetails("")
        assertEquals(0, details.charCount)
        assertEquals(0, details.units)
        assertEquals(0, details.segments)
        assertEquals("GSM-7", details.encoding)
        assertFalse(details.isUnicode)
        assertEquals(160, details.maxSingle)
        assertEquals(153, details.maxMultipart)
        assertTrue(details.explanation.contains("Empty message"))

        val nullDetails = SmsSegmentCalculator.calculateDetails(null)
        assertEquals(0, nullDetails.segments)
        assertEquals(0, nullDetails.units)
    }

    @Test
    fun testGsm7BasicSingleSegment() {
        val text160 = "A".repeat(160)
        val details = SmsSegmentCalculator.calculateDetails(text160)
        assertEquals(160, details.charCount)
        assertEquals(160, details.units)
        assertEquals(1, details.segments)
        assertEquals("GSM-7", details.encoding)
        assertFalse(details.isUnicode)
        assertEquals(160, details.maxSingle)
        assertEquals(153, details.maxMultipart)
        assertTrue(details.explanation.contains("GSM-7 encoding: 160 septets -> 1 segment(s)"))
    }

    @Test
    fun testGsm7BasicMultiSegmentBoundary() {
        val text161 = "B".repeat(161)
        val details = SmsSegmentCalculator.calculateDetails(text161)
        assertEquals(161, details.charCount)
        assertEquals(161, details.units)
        // Concatenated boundary: 161 / 153 = ceil(1.052) = 2 segments
        assertEquals(2, details.segments)
        assertEquals("GSM-7", details.encoding)
        assertFalse(details.isUnicode)
    }

    @Test
    fun testGsm7Crucial320CharsCalculatesTo3SegmentsNot2() {
        // Crucial telecom specification rule:
        // 320 chars in GSM-7 requires UDH for multi-part (153 septets per segment).
        // 320 / 153 = 2.0915... -> ceil = 3 segments (NOT 2 segments!)
        val text320 = "C".repeat(320)
        val details = SmsSegmentCalculator.calculateDetails(text320)
        assertEquals(320, details.charCount)
        assertEquals(320, details.units)
        assertEquals(3, details.segments)
        assertEquals("GSM-7", details.encoding)
        assertFalse(details.isUnicode)
        assertEquals(160, details.maxSingle)
        assertEquals(153, details.maxMultipart)
    }

    @Test
    fun testGsm7ExtendedCharactersCostTwoSeptetsEach() {
        // Extended characters: ^{}\[~]|€ take 2 septets each
        val extendedChars = "^{}\\[~]|€"
        assertEquals(9, extendedChars.length)

        // 9 extended chars = 18 septets
        val details = SmsSegmentCalculator.calculateDetails(extendedChars)
        assertEquals(9, details.charCount)
        assertEquals(18, details.units)
        assertEquals(1, details.segments)
        assertEquals("GSM-7", details.encoding)
        assertFalse(details.isUnicode)

        // 81 extended chars (e.g. '€' repeated 81 times) = 162 septets -> > 160 -> 2 segments
        val euros81 = "€".repeat(81)
        val euroDetails = SmsSegmentCalculator.calculateDetails(euros81)
        assertEquals(81, euroDetails.charCount)
        assertEquals(162, euroDetails.units)
        assertEquals(2, euroDetails.segments)
    }

    @Test
    fun testUnicodeTeluguAndHindiSingleSegment() {
        // 70 Telugu characters = single segment (<= 70)
        val telugu70 = "వరి".repeat(23) + "వ" // 23 * 3 + 1 = 70 chars
        assertEquals(70, telugu70.length)

        val details = SmsSegmentCalculator.calculateDetails(telugu70)
        assertEquals(70, details.charCount)
        assertEquals(70, details.units)
        assertEquals(1, details.segments)
        assertEquals("UCS-2 (Unicode)", details.encoding)
        assertTrue(details.isUnicode)
        assertEquals(70, details.maxSingle)
        assertEquals(67, details.maxMultipart)
        assertTrue(details.explanation.contains("UCS-2 Unicode encoding: 70 code units -> 1 segment(s)"))
    }

    @Test
    fun testUnicodeMultiSegmentBoundary() {
        // 71 Hindi/Telugu characters = 2 segments (71 / 67 = 2)
        val hindi71 = "क".repeat(71)
        val details = SmsSegmentCalculator.calculateDetails(hindi71)
        assertEquals(71, details.charCount)
        assertEquals(71, details.units)
        assertEquals(2, details.segments)
        assertEquals("UCS-2 (Unicode)", details.encoding)
        assertTrue(details.isUnicode)
    }

    @Test
    fun testUnicodeCrucial320CharsCalculatesTo5SegmentsNot2() {
        // Crucial telecom specification rule:
        // 320 Telugu/Hindi chars in UCS-2 multi-part (67 code units per segment).
        // 320 / 67 = 4.776... -> ceil = 5 segments (NOT 2 segments!)
        val telugu320 = "రైతు".repeat(80) // 80 * 4 = 320 chars
        assertEquals(320, telugu320.length)

        val details = SmsSegmentCalculator.calculateDetails(telugu320)
        assertEquals(320, details.charCount)
        assertEquals(320, details.units)
        assertEquals(5, details.segments)
        assertEquals("UCS-2 (Unicode)", details.encoding)
        assertTrue(details.isUnicode)
        assertEquals(70, details.maxSingle)
        assertEquals(67, details.maxMultipart)
    }

    @Test
    fun testMixedTextFallbackToUnicode() {
        // English message with one Telugu word
        val mixed = "Hello farmer: వరి లో తెగులు నివారణ"
        val details = SmsSegmentCalculator.calculateDetails(mixed)
        assertEquals(mixed.length, details.charCount)
        assertEquals("UCS-2 (Unicode)", details.encoding)
        assertTrue(details.isUnicode)
        assertFalse(SmsSegmentCalculator.isGsm7Compatible(mixed))
    }

    @Test
    fun testGsm7CharsetCompatibilityHelper() {
        assertTrue(SmsSegmentCalculator.isGsm7Compatible("Hello World 123!"))
        assertTrue(SmsSegmentCalculator.isGsm7Compatible("Special: @£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ÄÖÑÜ§¿äöñüà^{}\\[~]|€"))
        assertFalse(SmsSegmentCalculator.isGsm7Compatible("నమస్కారం"))
        assertFalse(SmsSegmentCalculator.isGsm7Compatible("🌾 Cotton Pest Alert"))
    }

    // =========================================================================
    // 2. DATA TRANSFER OBJECT (DTO) SERIALIZATION & DESERIALIZATION TESTS
    // =========================================================================

    @Test
    fun testSMSIncomingRequestDtoSerialization() {
        val request = SMSIncomingRequestDto(
            sender_number = "+919876543210",
            message_text = "వరిలో అగ్గితెగులు మందు ఏమిటి?",
            language = "te",
            message_id = "msg-12345",
            idempotency_key = "idemp-abc-99",
            simulate_failure = false
        )

        // Verify getter helpers
        assertEquals("+919876543210", request.senderNumber)
        assertEquals("వరిలో అగ్గితెగులు మందు ఏమిటి?", request.messageText)
        assertEquals("msg-12345", request.messageId)
        assertEquals("idemp-abc-99", request.idempotencyKey)
        assertEquals(false, request.simulateFailure)

        val json = gson.toJson(request)
        assertTrue(json.contains("\"sender_number\":\"+919876543210\""))
        assertTrue(json.contains("\"message_text\":\"వరిలో అగ్గితెగులు మందు ఏమిటి?\""))
        assertTrue(json.contains("\"language\":\"te\""))
        assertTrue(json.contains("\"message_id\":\"msg-12345\""))
        assertTrue(json.contains("\"idempotency_key\":\"idemp-abc-99\""))
        assertTrue(json.contains("\"simulate_failure\":false"))
    }

    @Test
    fun testSMSIncomingResponseDtoDeserialization() {
        val json = """
        {
            "success": true,
            "reply_text": "[SIMULATED SMS] వరిలో అగ్గితెగులు నివారణకు ట్రైసైక్లాజోల్ 75% WP ఎకరాకు 120 గ్రాములు పిచికారీ చేయండి.",
            "reply": {
                "message_text": "[SIMULATED SMS] వరిలో అగ్గితెగులు నివారణకు ట్రైసైక్లాజోల్ 75% WP ఎకరాకు 120 గ్రాములు పిచికారీ చేయండి.",
                "is_safe": true,
                "ticket_number": null,
                "language": "te",
                "char_count": 98,
                "segments": 2,
                "encoding": "UCS-2 (Unicode)",
                "disclaimer": "SIMULATED SMS",
                "is_simulated": true
            },
            "outbound_message": {
                "message_text": "[SIMULATED SMS] వరిలో అగ్గితెగులు నివారణకు ట్రైసైక్లాజోల్ 75% WP ఎకరాకు 120 గ్రాములు పిచికారీ చేయండి.",
                "is_safe": true,
                "ticket_number": null,
                "language": "te",
                "char_count": 98,
                "segments": 2,
                "encoding": "UCS-2 (Unicode)",
                "disclaimer": "SIMULATED SMS",
                "is_simulated": true
            },
            "inbound_message": {
                "sender_number": "+919876543210",
                "message_text": "వరిలో అగ్గితెగులు మందు ఏమిటి?"
            },
            "safety_alert": false,
            "ticket_escalated": false,
            "ticket_id": null,
            "is_demo": true,
            "is_simulated": true,
            "error": null
        }
        """.trimIndent()

        val response = gson.fromJson(json, SMSIncomingResponseDto::class.java)
        assertNotNull(response)
        assertTrue(response.success)
        assertTrue(response.is_demo)
        assertTrue(response.is_simulated)
        assertFalse(response.safetyAlert)
        assertFalse(response.ticketEscalated)
        assertNull(response.ticketId)
        assertNull(response.error)
        assertTrue(response.replyText!!.contains("ట్రైసైక్లాజోల్"))

        val reply = response.reply
        assertNotNull(reply)
        assertTrue(reply!!.isSafe)
        assertEquals("UCS-2 (Unicode)", reply.encoding)
        assertEquals(2, reply.segments)
        assertEquals(98, reply.charCount)
        assertTrue(reply.isSimulated)

        val inbound = response.inboundMessage
        assertNotNull(inbound)
        assertEquals("+919876543210", inbound!!["sender_number"])
    }

    @Test
    fun testSMSSegmentCalcResponseDtoDeserialization() {
        val json = """
        {
            "char_count": 320,
            "encoding": "GSM-7",
            "is_unicode": false,
            "units": 320,
            "segments": 3,
            "max_single": 160,
            "max_multipart": 153,
            "explanation": "GSM-7 encoding: 320 septets -> 3 segment(s) (160 single / 153 multi-part)"
        }
        """.trimIndent()

        val dto = gson.fromJson(json, SMSSegmentCalcResponseDto::class.java)
        assertEquals(320, dto.charCount)
        assertEquals("GSM-7", dto.encoding)
        assertFalse(dto.isUnicode)
        assertEquals(320, dto.units)
        assertEquals(3, dto.segments)
        assertEquals(160, dto.maxSingle)
        assertEquals(153, dto.maxMultipart)
        assertTrue(dto.explanation.contains("3 segment(s)"))
    }

    @Test
    fun testSMSHistoryResponseDtoDeserialization() {
        val json = """
        {
            "phone_number": "+919876543210",
            "count": 2,
            "messages": [
                {
                    "direction": "inbound",
                    "text": "వరిలో అగ్గితెగులు మందు ఏమిటి?",
                    "timestamp": "2026-11-11T07:15:00Z"
                },
                {
                    "direction": "outbound",
                    "text": "[SIMULATED SMS] ట్రైసైక్లాజోల్ 75% WP వాడండి.",
                    "timestamp": "2026-11-11T07:15:02Z"
                }
            ],
            "is_demo": true,
            "is_simulated": true
        }
        """.trimIndent()

        val history = gson.fromJson(json, SMSHistoryResponseDto::class.java)
        assertEquals("+919876543210", history.phoneNumber)
        assertEquals(2, history.count)
        assertEquals(2, history.messages.size)
        assertTrue(history.isDemo)
        assertTrue(history.isSimulated)
        assertEquals("inbound", history.messages[0]["direction"])
        assertEquals("outbound", history.messages[1]["direction"])
    }

    // =========================================================================
    // 3. RETROFIT API SERVICE METHOD DECLARATION TEST
    // =========================================================================

    @Test
    fun testApiServiceSmsMethodsPresentWithCorrectAnnotations() {
        val methods = BolKisanApiService::class.java.declaredMethods

        val sendSmsMethod = methods.find { it.name == "sendSMSIncoming" }
        assertNotNull("sendSMSIncoming must be declared in BolKisanApiService", sendSmsMethod)
        val postAnnotation = sendSmsMethod!!.getAnnotation(POST::class.java)
        assertNotNull("sendSMSIncoming must have @POST annotation", postAnnotation)
        assertTrue("sendSMSIncoming route must contain sms/incoming", postAnnotation!!.value.endsWith("sms/incoming"))

        val getHistoryMethod = methods.find { it.name == "getSMSHistory" }
        assertNotNull("getSMSHistory must be declared in BolKisanApiService", getHistoryMethod)
        val getHistoryAnnotation = getHistoryMethod!!.getAnnotation(GET::class.java)
        assertNotNull("getSMSHistory must have @GET annotation", getHistoryAnnotation)
        assertTrue("getSMSHistory route must contain sms/history", getHistoryAnnotation!!.value.endsWith("sms/history"))

        val calcSegmentsMethod = methods.find { it.name == "calculateSMSSegments" }
        assertNotNull("calculateSMSSegments must be declared in BolKisanApiService", calcSegmentsMethod)
        val calcAnnotation = calcSegmentsMethod!!.getAnnotation(GET::class.java)
        assertNotNull("calculateSMSSegments must have @GET annotation", calcAnnotation)
        assertTrue("calculateSMSSegments route must contain sms/segment-calc", calcAnnotation!!.value.endsWith("sms/segment-calc"))
    }
}
