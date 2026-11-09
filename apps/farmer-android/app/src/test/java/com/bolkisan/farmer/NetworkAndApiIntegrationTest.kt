package com.bolkisan.farmer

import com.bolkisan.farmer.data.remote.ApiClient
import com.bolkisan.farmer.data.remote.models.BackendHealthDto
import com.bolkisan.farmer.data.remote.models.VoiceAnswerDto
import com.bolkisan.farmer.data.remote.models.VoiceQueryDto
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class NetworkAndApiIntegrationTest {

    private val gson = Gson()

    @Test
    fun testValidateBaseUrl_normalizesValidHttpUrl() {
        val input = "http://192.168.0.150:8000"
        val res = ApiClient.validateBaseUrl(input, isProduction = false)
        assertTrue(res.isValid)
        assertEquals("http://192.168.0.150:8000/api/v1/", res.sanitizedUrl)
    }

    @Test
    fun testValidateBaseUrl_normalizesTrailingSlashAndPath() {
        val input = "http://192.168.0.150:8000/api/v1"
        val res1 = ApiClient.validateBaseUrl(input, isProduction = false)
        assertTrue(res1.isValid)
        assertEquals("http://192.168.0.150:8000/api/v1/", res1.sanitizedUrl)

        val inputWithSlash = "http://10.0.2.2:8000/"
        val res2 = ApiClient.validateBaseUrl(inputWithSlash, isProduction = false)
        assertTrue(res2.isValid)
        assertEquals("http://10.0.2.2:8000/api/v1/", res2.sanitizedUrl)
    }

    @Test
    fun testValidateBaseUrl_normalizesValidHttpsUrl() {
        val input = "https://api.bolkisan.gov.in"
        val res = ApiClient.validateBaseUrl(input, isProduction = false)
        assertTrue(res.isValid)
        assertEquals("https://api.bolkisan.gov.in/api/v1/", res.sanitizedUrl)
    }

    @Test
    fun testValidateBaseUrl_rejectsInvalidUrls() {
        // Missing scheme
        val noScheme = ApiClient.validateBaseUrl("192.168.0.150:8000", isProduction = false)
        assertFalse(noScheme.isValid)

        // Unsupported protocol
        val ftp = ApiClient.validateBaseUrl("ftp://192.168.0.150:8000", isProduction = false)
        assertFalse(ftp.isValid)

        // Blank host
        val blankHost = ApiClient.validateBaseUrl("http://", isProduction = false)
        assertFalse(blankHost.isValid)

        // Empty string
        val emptyStr = ApiClient.validateBaseUrl("   ", isProduction = false)
        assertFalse(emptyStr.isValid)
    }

    @Test
    fun testProductionHttpsEnforcement() {
        // In production mode (isProduction = true), cleartext HTTP must be strictly rejected
        val devUrl = "http://192.168.0.150:8000/api/v1/"
        val rejectedInProd = ApiClient.validateBaseUrl(devUrl, isProduction = true)
        assertFalse("Production must reject cleartext HTTP URLs", rejectedInProd.isValid)

        val emulatorUrl = "http://10.0.2.2:8000/api/v1/"
        val rejectedEmulatorInProd = ApiClient.validateBaseUrl(emulatorUrl, isProduction = true)
        assertFalse("Production must reject emulator cleartext HTTP URLs", rejectedEmulatorInProd.isValid)

        val prodHttpsUrl = "https://api.bolkisan.gov.in/api/v1/"
        val acceptedInProd = ApiClient.validateBaseUrl(prodHttpsUrl, isProduction = true)
        assertTrue(acceptedInProd.isValid)
        assertEquals("https://api.bolkisan.gov.in/api/v1/", acceptedInProd.sanitizedUrl)
    }

    @Test
    fun testHealthDtoDeserialization_parsesFullBackendPayload() {
        val healthJson = """
        {
            "status": "healthy",
            "version": "1.2.0",
            "database": {
                "status": "connected",
                "dialect": "postgresql"
            },
            "ai_provider": {
                "gemini_configured": true,
                "model_name": "gemini-2.5-flash"
            }
        }
        """.trimIndent()

        val health = gson.fromJson(healthJson, BackendHealthDto::class.java)
        assertNotNull(health)
        assertEquals("healthy", health.status)
        assertEquals("1.2.0", health.version)
        assertNotNull(health.database)
        assertEquals("connected", health.database?.status)
        assertEquals("postgresql", health.database?.dialect)
        assertNotNull(health.aiProvider)
        assertEquals(true, health.aiProvider?.geminiConfigured)
        assertEquals("gemini-2.5-flash", health.aiProvider?.modelName)
    }

    @Test
    fun testHealthDtoDeserialization_handlesDegradedDatabasePayload() {
        val healthJson = """
        {
            "status": "degraded",
            "version": "1.2.0",
            "database": {
                "status": "disconnected",
                "error": "connection refused on 5432"
            },
            "ai_provider": {
                "gemini_configured": false
            }
        }
        """.trimIndent()

        val health = gson.fromJson(healthJson, BackendHealthDto::class.java)
        assertNotNull(health)
        assertEquals("degraded", health.status)
        assertEquals("disconnected", health.database?.status)
        assertEquals("connection refused on 5432", health.database?.error)
        assertEquals(false, health.aiProvider?.geminiConfigured)
    }

    @Test
    fun testVoiceQuerySerialization_withMultiTurnConversationHistory() {
        val history = listOf(
            mapOf("query" to "నా పత్తి పంటలో గులాబీ రంగు పురుగు ఉంది", "answer" to "ఎకరాకు 4 లింగాకర్షక బుట్టలు పెట్టండి.", "crop" to "Cotton"),
            mapOf("query" to "మందు ఏమి పిచికారీ చేయాలి?", "answer" to "ప్రొఫెనోఫాస్ 50% EC 400 మి.లీ పిచికారీ చేయండి.", "crop" to "Cotton")
        )

        val queryDto = VoiceQueryDto(
            queryText = "ఇది ఎంత మోతాదులో కలపాలి?",
            language = "te",
            cropName = "Cotton",
            conversationHistory = history
        )

        val json = gson.toJson(queryDto)
        assertTrue(json.contains("\"query_text\":\"ఇది ఎంత మోతాదులో కలపాలి?\""))
        assertTrue(json.contains("\"crop_name\":\"Cotton\""))
        assertTrue(json.contains("\"conversation_history\""))
        assertTrue(json.contains("ప్రొఫెనోఫాస్"))
    }

    @Test
    fun testVoiceAnswerDtoDeserialization_withOnlineFallbackAttribution() {
        val answerJson = """
        {
            "status": "answered",
            "is_safe": true,
            "response_text": "వరిలో అగ్గి తెగులు నివారణకు ట్రైసైక్లాజోల్ 75% డబ్ల్యూపీ 120 గ్రాములు ఎకరాకు పిచికారీ చేయండి.",
            "citations": ["ICAR-NRRI Rice Advisory 2024"],
            "is_fallback": true,
            "model_used": "icar_fallback",
            "provider": "icar_knowledge_base"
        }
        """.trimIndent()

        val answer = gson.fromJson(answerJson, VoiceAnswerDto::class.java)
        assertNotNull(answer)
        assertTrue(answer.isSafe)
        assertEquals(true, answer.isFallback)
        assertEquals("icar_fallback", answer.modelUsed)
        assertEquals("icar_knowledge_base", answer.provider)

        // Verify history origin attribution mapping logic
        val historyOrigin = when {
            answer.status == "needs_clarification" -> "needs_clarification"
            answer.status == "escalated" -> "escalated"
            answer.isFallback == true -> "online_fallback"
            else -> "online"
        }
        assertEquals("online_fallback", historyOrigin)
    }

    @Test
    fun testVoiceAnswerDtoDeserialization_withPrimaryGeminiAttribution() {
        val answerJson = """
        {
            "status": "answered",
            "is_safe": true,
            "response_text": "Cotton pink bollworm recommendation...",
            "citations": ["ICAR-CICR", "CIBRC"],
            "is_fallback": false,
            "model_used": "gemini-2.5-flash",
            "provider": "google_gemini"
        }
        """.trimIndent()

        val answer = gson.fromJson(answerJson, VoiceAnswerDto::class.java)
        assertNotNull(answer)
        assertEquals(false, answer.isFallback)
        assertEquals("gemini-2.5-flash", answer.modelUsed)

        val historyOrigin = when {
            answer.status == "needs_clarification" -> "needs_clarification"
            answer.status == "escalated" -> "escalated"
            answer.isFallback == true -> "online_fallback"
            else -> "online"
        }
        assertEquals("online", historyOrigin)
    }

    @Test
    fun testModeAttributionBadges() {
        fun getAttributionText(origin: String): String = when (origin) {
            "online" -> "🟢 ONLINE (Gemini + RAG)"
            "online_fallback" -> "🟢 ONLINE (ICAR Fallback)"
            "offline_slm" -> "⚡ OFFLINE SLM (Qwen3-0.6B)"
            "offline_rule" -> "📦 OFFLINE KNOWLEDGE FALLBACK (ICAR SQLite)"
            "needs_clarification" -> "❓ Clarification Needed"
            "escalated" -> "🧑‍🌾 Escalated to Officer"
            "tank_mix_intercept" -> "⚠️ Tank Mix Intercept"
            "offline_safety_intercept" -> "🛡️ Safety Intercept"
            else -> "🟢 ONLINE (Gemini + RAG)"
        }

        assertEquals("🟢 ONLINE (Gemini + RAG)", getAttributionText("online"))
        assertEquals("🟢 ONLINE (ICAR Fallback)", getAttributionText("online_fallback"))
        assertEquals("⚡ OFFLINE SLM (Qwen3-0.6B)", getAttributionText("offline_slm"))
        assertEquals("📦 OFFLINE KNOWLEDGE FALLBACK (ICAR SQLite)", getAttributionText("offline_rule"))
        assertEquals("❓ Clarification Needed", getAttributionText("needs_clarification"))
        assertEquals("🧑‍🌾 Escalated to Officer", getAttributionText("escalated"))
    }
}
