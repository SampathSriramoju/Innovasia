package com.bolkisan.farmer

import com.bolkisan.farmer.data.remote.models.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class DataModelsTest {

    private val gson = Gson()

    @Test
    fun testVoiceQueryDtoSerialization() {
        val request = VoiceQueryDto(
            queryText = "మిరపలో ఆకుముడత నివారణ ఏమిటి?",
            language = "te",
            cropName = "Chilli",
            pestOrDisease = "Thrips",
            chemicalMentioned = null
        )
        val json = gson.toJson(request)
        assertTrue(json.contains("query_text"))
        assertTrue(json.contains("Chilli"))
        assertTrue(json.contains("\"language\":\"te\""))
    }

    @Test
    fun testVoiceAnswerDtoDeserialization() {
        val json = """
        {
            "status": "answered",
            "is_safe": true,
            "response_text": "మిరపలో తామర పురుగుల నివారణకు పెగాసస్ 50% డబ్ల్యూపీ 25 గ్రాములు 15 లీటర్ల నీటిలో కలిపి పిచికారీ చేయండి.",
            "ticket_number": null,
            "citations": ["PJTSAU Chilli Package of Practices 2024", "CIBRC Approved List 2024"],
            "warnings": ["Use protective gear", "Wait 7 days before harvest"]
        }
        """.trimIndent()

        val response = gson.fromJson(json, VoiceAnswerDto::class.java)
        assertNotNull(response)
        assertTrue(response.isSafe)
        assertEquals("answered", response.status)
        assertEquals(2, response.citations?.size)
        assertTrue(response.responseText.contains("పెగాసస్"))
    }

    @Test
    fun testDiseaseDiagnosisDtoParsing() {
        val json = """
        {
            "disease_name": "Alternaria Leaf Spot",
            "crop_name": "Cotton",
            "confidence": 0.94,
            "severity": "MODERATE",
            "treatment": "Spray Mancozeb 75% WP @ 30g per 15L knapsack pump.",
            "is_safe": true,
            "citations": ["ICAR-CICR Cotton Protection Advisory 2024"]
        }
        """.trimIndent()

        val response = gson.fromJson(json, DiseaseDiagnosisDto::class.java)
        assertEquals("Alternaria Leaf Spot", response.diseaseName)
        assertEquals("Cotton", response.cropName)
        assertEquals(0.94, response.confidence ?: 0.0, 0.001)
        assertTrue(response.isSafe)
        assertEquals("MODERATE", response.severity)
    }

    @Test
    fun testTicketItemDtoModel() {
        val ticket = TicketItemDto(
            id = "tkt-001",
            ticketNumber = "TKT-2026-0001",
            status = "OPEN",
            priority = "CRITICAL",
            transcript = "Suspected severe Pink Bollworm flare-up in field 2",
            reason = "Pest flare-up exceeds statutory threshold",
            resolutionPrescription = null,
            createdAt = "2026-11-08T10:00:00Z",
            resolvedAt = null
        )

        assertEquals("CRITICAL", ticket.priority)
        assertEquals("OPEN", ticket.status)
        assertEquals("TKT-2026-0001", ticket.ticketNumber)
    }

    @Test
    fun testCropDtoModel() {
        val crop = CropDto(
            id = "crop-cotton",
            scientificName = "Gossypium hirsutum",
            nameEn = "Cotton",
            nameTe = "ప్రత్తి",
            nameHi = "कपास",
            category = "FIBER",
            season = "Kharif"
        )

        assertEquals("Gossypium hirsutum", crop.scientificName)
        assertEquals("ప్రత్తి", crop.nameTe)
        assertEquals("कपास", crop.nameHi)
    }
}
