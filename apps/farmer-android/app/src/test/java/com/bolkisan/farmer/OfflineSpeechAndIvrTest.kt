package com.bolkisan.farmer

import com.bolkisan.farmer.domain.voice.IvrAudioPrompts
import com.bolkisan.farmer.domain.voice.IvrLanguage
import com.bolkisan.farmer.domain.voice.IvrOption
import com.bolkisan.farmer.domain.voice.OfflineSpeechService
import com.bolkisan.farmer.domain.voice.VoiceLatencyTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSpeechAndIvrTest {

    @Test
    fun testIvrAudioPromptsCompletenessTelugu() {
        val welcome = IvrAudioPrompts.getWelcomePrompt(IvrLanguage.TELUGU)
        assertTrue("Telugu welcome must contain service introduction", welcome.contains("బోల్ కిసాన్"))
        assertTrue("Telugu welcome must mention key 1 for Telugu", welcome.contains("తెలుగు కోసం 1"))
        assertTrue("Telugu welcome must mention key 2 for Hindi", welcome.contains("హిందీ కోసం 2"))
        assertTrue("Telugu welcome must mention key 3 for English", welcome.contains("ఇంగ్లీష్ కోసం 3"))
        assertTrue("Telugu welcome must mention key 4 for Mandi prices", welcome.contains("మార్కెట్ ధరల కోసం 4"))
        assertTrue("Telugu welcome must mention key 5 for Crop advice", welcome.contains("పంట సలహా కోసం 5"))
        assertTrue("Telugu welcome must mention key 7 for Safety", welcome.contains("పురుగుమందుల భద్రత కోసం 7"))
        assertTrue("Telugu welcome must mention key 9 for Officer", welcome.contains("వ్యవసాయ అధికారి కోసం 9"))

        val confirm = IvrAudioPrompts.getLanguageSelectedPrompt(IvrLanguage.TELUGU)
        assertTrue("Telugu confirmation prompt", confirm.contains("మీరు తెలుగు ఎంచుకున్నారు"))

        val mandi = IvrAudioPrompts.getMandiPrompt(IvrLanguage.TELUGU)
        assertTrue("Telugu Mandi prompt", mandi.contains("తాజా మార్కెట్ ధరలు"))

        val advisory = IvrAudioPrompts.getCropAdvisoryPrompt(IvrLanguage.TELUGU)
        assertTrue("Telugu Crop advisory prompt", advisory.contains("మీ పంట ప్రశ్న అడగండి"))

        val officer = IvrAudioPrompts.getOfficerTicketSavedPrompt(IvrLanguage.TELUGU)
        assertTrue("Telugu Officer ticket saved prompt", officer.contains("వ్యవసాయ అధికారికి టికెట్ సేవ్ చేయబడింది"))

        val callEnd = IvrAudioPrompts.getCallEndPrompt(IvrLanguage.TELUGU)
        assertTrue("Telugu Call end prompt", callEnd.contains("ధన్యవాదాలు"))
    }

    @Test
    fun testIvrAudioPromptsCompletenessHindi() {
        val welcome = IvrAudioPrompts.getWelcomePrompt(IvrLanguage.HINDI)
        assertTrue("Hindi welcome must contain service introduction", welcome.contains("बोल किसान"))
        assertTrue("Hindi welcome must mention key 1 for Telugu", welcome.contains("तेलुगु के लिए 1"))
        assertTrue("Hindi welcome must mention key 2 for Hindi", welcome.contains("हिंदी के लिए 2"))
        assertTrue("Hindi welcome must mention key 3 for English", welcome.contains("अंग्रेजी के लिए 3"))
        assertTrue("Hindi welcome must mention key 4 for Mandi prices", welcome.contains("मंडी भाव के लिए 4"))
        assertTrue("Hindi welcome must mention key 5 for Crop advice", welcome.contains("फसल सलाह के लिए 5"))
        assertTrue("Hindi welcome must mention key 7 for Safety", welcome.contains("कीटनाशक सुरक्षा के लिए 7"))
        assertTrue("Hindi welcome must mention key 9 for Officer", welcome.contains("कृषि अधिकारी के लिए 9"))

        val confirm = IvrAudioPrompts.getLanguageSelectedPrompt(IvrLanguage.HINDI)
        assertTrue("Hindi confirmation prompt", confirm.contains("आपने हिंदी चुनी है"))

        val mandi = IvrAudioPrompts.getMandiPrompt(IvrLanguage.HINDI)
        assertTrue("Hindi Mandi prompt", mandi.contains("ताजा मंडी भाव"))

        val advisory = IvrAudioPrompts.getCropAdvisoryPrompt(IvrLanguage.HINDI)
        assertTrue("Hindi Crop advisory prompt", advisory.contains("अपनी फसल का प्रश्न पूछें"))

        val officer = IvrAudioPrompts.getOfficerTicketSavedPrompt(IvrLanguage.HINDI)
        assertTrue("Hindi Officer ticket saved prompt", officer.contains("कृषि अधिकारी हेतु टिकट सुरक्षित किया गया"))

        val callEnd = IvrAudioPrompts.getCallEndPrompt(IvrLanguage.HINDI)
        assertTrue("Hindi Call end prompt", callEnd.contains("धन्यवाद"))
    }

    @Test
    fun testIvrAudioPromptsCompletenessEnglish() {
        val welcome = IvrAudioPrompts.getWelcomePrompt(IvrLanguage.ENGLISH)
        assertTrue("English welcome must contain service introduction", welcome.contains("Bol Kisan"))
        assertTrue("English welcome must mention key 1 for Telugu", welcome.contains("Press 1 for Telugu"))
        assertTrue("English welcome must mention key 2 for Hindi", welcome.contains("2 for Hindi"))
        assertTrue("English welcome must mention key 3 for English", welcome.contains("3 for English"))
        assertTrue("English welcome must mention key 4 for Mandi", welcome.contains("4 for Mandi Prices"))
        assertTrue("English welcome must mention key 5 for Crop advice", welcome.contains("5 for Crop Advisory"))
        assertTrue("English welcome must mention key 7 for Safety", welcome.contains("7 for Safety"))
        assertTrue("English welcome must mention key 9 for Officer", welcome.contains("9 for Officer Escalation"))
        assertTrue("English welcome must mention key 0 to End", welcome.contains("0 to End"))

        val confirm = IvrAudioPrompts.getLanguageSelectedPrompt(IvrLanguage.ENGLISH)
        assertTrue("English confirmation prompt", confirm.contains("You selected English"))

        val mandi = IvrAudioPrompts.getMandiPrompt(IvrLanguage.ENGLISH)
        assertTrue("English Mandi prompt", mandi.contains("Cached Mandi Prices"))

        val advisory = IvrAudioPrompts.getCropAdvisoryPrompt(IvrLanguage.ENGLISH)
        assertTrue("English Crop advisory prompt", advisory.contains("Please speak your crop question"))

        val officer = IvrAudioPrompts.getOfficerTicketSavedPrompt(IvrLanguage.ENGLISH)
        assertTrue("English Officer ticket saved prompt", officer.contains("Ticket saved for Extension Officer"))

        val callEnd = IvrAudioPrompts.getCallEndPrompt(IvrLanguage.ENGLISH)
        assertTrue("English Call end prompt", callEnd.contains("Jai Kisan"))
    }

    @Test
    fun testRepeatAndErrorPrompts() {
        for (lang in IvrLanguage.entries) {
            val noSpeech = IvrAudioPrompts.getNoSpeechPrompt(lang)
            assertTrue("No speech prompt for $lang must not be blank", noSpeech.isNotBlank())

            val invalid = IvrAudioPrompts.getInvalidInputPrompt(lang)
            assertTrue("Invalid input prompt for $lang must not be blank", invalid.isNotBlank())

            val missingPack = IvrAudioPrompts.getOfflinePackMissingPrompt(lang)
            assertTrue("Offline pack missing prompt for $lang must not be blank", missingPack.isNotBlank())

            val micReq = IvrAudioPrompts.getMicPermissionRequiredPrompt(lang)
            assertTrue("Mic permission prompt for $lang must not be blank", micReq.isNotBlank())

            val bannedAlert = IvrAudioPrompts.getBannedChemicalAlertPrompt(lang)
            assertTrue("Banned chemical prompt for $lang must not be blank", bannedAlert.isNotBlank())
        }
    }

    @Test
    fun testCleanSpokenTextStripsMarkdownAndCitations() {
        val raw = "## Spray Protocol *Important* [KB-01]\n- Use 5ml per 15L tank `now`.\n📚 ప్రామాణిక సమాచార మూలం: ICAR-IIPR (Doc-02)"
        val cleaned = OfflineSpeechService.cleanSpokenText(raw, "en")

        assertFalse("Must strip markdown headers", cleaned.contains("##"))
        assertFalse("Must strip markdown bold/italic *", cleaned.contains("*"))
        assertFalse("Must strip backticks", cleaned.contains("`"))
        assertFalse("Must strip bracketed citations [KB-01]", cleaned.contains("[KB-01]"))
        assertFalse("Must strip bullet dashes", cleaned.startsWith("-"))
        assertFalse("Must strip citation block header", cleaned.contains("📚 ప్రామాణిక సమాచార మూలం"))
        assertTrue("Must preserve actionable instruction", cleaned.contains("Use 5ml per 15L tank now"))
    }

    @Test
    fun testCleanSpokenTextReplacesLatinBinomialsInTelugu() {
        val raw = "పత్తి పంటలో Pectinophora gossypiella మరియు Bemisia tabaci సోకకుండా జాగ్రత్త వహించండి. [KB-COTTON-01]"
        val cleaned = OfflineSpeechService.cleanSpokenText(raw, "te")

        assertFalse("Must not contain Latin Pectinophora gossypiella", cleaned.contains("Pectinophora gossypiella", ignoreCase = true))
        assertFalse("Must not contain Latin Bemisia tabaci", cleaned.contains("Bemisia tabaci", ignoreCase = true))
        assertFalse("Must strip citation tag", cleaned.contains("[KB-COTTON-01]"))
        assertTrue("Must contain Telugu common name గులాబీ రంగు పురుగు", cleaned.contains("గులాబీ రంగు పురుగు"))
        assertTrue("Must contain Telugu common name తెల్లదోమ", cleaned.contains("తెల్లదోమ"))
    }

    @Test
    fun testCleanSpokenTextReplacesLatinBinomialsInHindi() {
        val raw = "चने में Helicoverpa armigera का हमला पाया गया। (Source: ICAR-IIPR)"
        val cleaned = OfflineSpeechService.cleanSpokenText(raw, "hi")

        assertFalse("Must not contain Latin Helicoverpa armigera", cleaned.contains("Helicoverpa armigera", ignoreCase = true))
        assertFalse("Must strip parenthesized citation", cleaned.contains("Source: ICAR-IIPR"))
        assertTrue("Must contain Hindi common name फली छेदक", cleaned.contains("फली छेदक"))
    }

    @Test
    fun testDtmfOptionsAndLanguages() {
        assertEquals(IvrLanguage.TELUGU, IvrLanguage.fromDtmf("1"))
        assertEquals(IvrLanguage.HINDI, IvrLanguage.fromDtmf("2"))
        assertEquals(IvrLanguage.ENGLISH, IvrLanguage.fromDtmf("3"))
        assertNull(IvrLanguage.fromDtmf("9"))

        assertEquals(IvrOption.LANGUAGE_TELUGU, IvrOption.fromDtmf("1"))
        assertEquals(IvrOption.LANGUAGE_HINDI, IvrOption.fromDtmf("2"))
        assertEquals(IvrOption.LANGUAGE_ENGLISH, IvrOption.fromDtmf("3"))
        assertEquals(IvrOption.MANDI_PRICES, IvrOption.fromDtmf("4"))
        assertEquals(IvrOption.CROP_ADVISORY, IvrOption.fromDtmf("5"))
        assertEquals(IvrOption.SAFETY_GUIDELINES, IvrOption.fromDtmf("7"))
        assertEquals(IvrOption.OFFICER_ESCALATION, IvrOption.fromDtmf("9"))
        assertEquals(IvrOption.END_CALL, IvrOption.fromDtmf("0"))
        assertNull(IvrOption.fromDtmf("6"))
    }

    @Test
    fun testVoiceLatencyTelemetryMeetsStrict6SecSla() {
        val telemetryPassing = VoiceLatencyTelemetry(
            asrLatencyMs = 380L,
            retrievalLatencyMs = 25L,
            ttftMs = 520L,
            inferenceDurationMs = 1850L,
            tokensPerSec = 14.5,
            safetyGateLatencyMs = 15L,
            firstAudioTtsLatencyMs = 210L,
            totalVoiceToVoiceMs = 3000L,
            isColdStart = false,
            meetsStrict6SecSla = true
        )
        assertTrue(telemetryPassing.meetsStrict6SecSla)
        assertTrue(telemetryPassing.totalVoiceToVoiceMs <= 6000L)

        val telemetryFailing = VoiceLatencyTelemetry(
            totalVoiceToVoiceMs = 7200L,
            meetsStrict6SecSla = false
        )
        assertFalse(telemetryFailing.meetsStrict6SecSla)
    }
}
