package com.bolkisan.farmer

import com.bolkisan.farmer.domain.OfflineInferenceEngine
import com.bolkisan.farmer.domain.OfflineKnowledgeFormatter
import org.junit.Assert.*
import org.junit.Test

class OfflineKnowledgeFormatterTest {

    @Test
    fun testLatinBinomialReplacementInTelugu() {
        val rawInput = "Crop & Pest: Cotton - Pectinophora gossypiella attack on bolls. Also Bemisia tabaci vector."
        val formatted = OfflineKnowledgeFormatter.replaceLatinNames(rawInput, "te")

        assertFalse("Must not contain raw Latin 'Pectinophora gossypiella'", formatted.contains("Pectinophora gossypiella", ignoreCase = true))
        assertFalse("Must not contain raw Latin 'Bemisia tabaci'", formatted.contains("Bemisia tabaci", ignoreCase = true))
        assertTrue("Must contain Telugu common name 'గులాబీ రంగు పురుగు'", formatted.contains("గులాబీ రంగు పురుగు"))
        assertTrue("Must contain Telugu common name 'తెల్లదోమ'", formatted.contains("తెల్లదోమ"))
    }

    @Test
    fun testLatinBinomialReplacementInHindi() {
        val rawInput = "चने में Helicoverpa armigera और Spodoptera litura का प्रकोप।"
        val formatted = OfflineKnowledgeFormatter.replaceLatinNames(rawInput, "hi")

        assertFalse("Must not contain Latin 'Helicoverpa armigera'", formatted.contains("Helicoverpa armigera", ignoreCase = true))
        assertFalse("Must not contain Latin 'Spodoptera litura'", formatted.contains("Spodoptera litura", ignoreCase = true))
        assertTrue("Must contain Hindi common name 'फली छेदक'", formatted.contains("फली छेदक"))
        assertTrue("Must contain Hindi common name 'तंबाकू की इल्ली'", formatted.contains("तंबाकू की इल्ली"))
    }

    @Test
    fun testAcademicJargonAndCitationPreservation() {
        val rawInput = "Crop & Practices: Chickpea - ETL: 1 larva/meter row. vegetative stage (35 DAS). Pre-Harvest Interval (PHI): 14 days. ICAR-IIPR Package 2024."
        val cleaned = OfflineKnowledgeFormatter.cleanAcademicJargon(rawInput, "te")

        assertFalse("Must strip 'Crop & Practices:'", cleaned.contains("Crop & Practices:"))
        assertTrue("Must PRESERVE authentic agricultural citation 'ICAR-IIPR Package 2024'", cleaned.contains("ICAR-IIPR Package 2024"))
        assertFalse("Must not leave bare 'ETL:'", cleaned.contains("ETL: 1"))
        assertFalse("Must not leave bare '35 DAS'", cleaned.contains("35 DAS"))
        assertTrue("Must replace DAS with 'విత్తిన రోజుల తర్వాత'", cleaned.contains("విత్తిన రోజుల తర్వాత"))
    }

    @Test
    fun testCitationBoxFormattingAcrossLanguages() {
        val teBox = OfflineKnowledgeFormatter.buildCitationBox("ICAR-CICR", "పత్తి గులాబీ రంగు పురుగు నివారణ 2024", "te")
        assertTrue("Must contain Telugu citation header", teBox.contains("📚 ప్రామాణిక సమాచార మూలం:"))
        assertTrue("Must contain institution", teBox.contains("ICAR-CICR"))

        val hiBox = OfflineKnowledgeFormatter.buildCitationBox("भाकृअनुप-IIPR", "चना फली छेदक प्रबंधन 2024", "hi")
        assertTrue("Must contain Hindi citation header", hiBox.contains("📚 प्रामाणिक कृषि संदर्भ:"))
        assertTrue("Must contain institution", hiBox.contains("भाकृअनुप-IIPR"))

        val enBox = OfflineKnowledgeFormatter.buildCitationBox("ICAR-CRIDA & CIBRC", "Cotton Pest Label 2024", "en")
        assertTrue("Must contain English citation header", enBox.contains("📚 Source Citation:"))
        assertTrue("Must contain institution", enBox.contains("ICAR-CRIDA & CIBRC"))
    }

    @Test
    fun testEnsureCitationBoxRepositioning() {
        val originalText = "రైతుకు ముఖ్య సూచన: జాగ్రత్తలు పాటించండి.\n\n📚 ప్రామాణిక సమాచార మూలం: ICAR-CICR - పత్తి సస్యరక్షణ 2024"
        val textWithExtra = "$originalText\n\n🎒 15 లీటర్ల పంపుకు సూచన: మోతాదు జాగ్రత్తగా కలపండి."
        val repositioned = OfflineKnowledgeFormatter.ensureCitationBox(textWithExtra, "Cotton", "te")

        assertTrue("Citation box must be relocated to the very end", repositioned.trim().endsWith("📚 ప్రామాణిక సమాచార మూలం: ICAR-CICR - పత్తి సస్యరక్షణ 2024"))
        assertTrue("Knapsack note must remain above citation box", repositioned.indexOf("15 లీటర్ల పంపుకు సూచన") < repositioned.indexOf("📚 ప్రామాణిక సమాచార మూలం"))
    }

    @Test
    fun testFullCardFormattingTeluguProducesFarmerFriendlySections() {
        val rawCard = """
            Crop & Practices: శనగ (Cicer arietinum) - శనగ పచ్చ పురుగు (Helicoverpa armigera) సస్యరక్షణ.
            పురుగు లక్షణాలు & నష్ట పరిమితి: లద్దె పురుగులు లేత ఆకులను తిని, కాయలకు గుండ్రటి రంధ్రాలు చేసి లోపలి గింజలను తింటాయి. ETL పరిమితి: వరుస మీటరుకు 1 పురుగు.
            సమగ్ర సస్యరక్షణ (IPM): 1) ఎకరాకు 20 పక్షి స్థావరాలు (T కర్రలు) నాటాలి. 2) ఎకరాకు 5 లింగాకర్షక బుట్టలు పెట్టాలి. 3) పూత ప్రారంభంలో 5% వేప గింజల కషాయం పిచికారీ చేయాలి.
            CIBRC ఆమోదిత రసాయన నివారణ: ఎమామెక్టిన్ బెంజోయేట్ 5% SG (ప్రోక్లైమ్) ఎకరాకు 88 గ్రా (15 లీటర్ల చేతి పంపుకు 6.6 గ్రా; 200 లీటర్ల నీటిలో), వేచి ఉండే కాలం (PHI): 14 రోజులు.
            రక్షణ చర్యలు: చేతి తొడుగులు మరియు మాస్క్ ధరించాలి.
        """.trimIndent()

        val formatted = OfflineKnowledgeFormatter.format(rawCard, "శనగ", "te")

        assertFalse("Must not contain Latin binomials", formatted.contains("Cicer arietinum"))
        assertFalse("Must not contain Latin 'Helicoverpa armigera'", formatted.contains("Helicoverpa armigera"))
        assertTrue("Must have Problem/Crop header", formatted.contains("సమస్య / పంట:"))
        assertTrue("Must have Key Symptoms header", formatted.contains("గమనించాల్సిన ముఖ్య లక్షణాలు:"))
        assertTrue("Must have Recommended Actions header", formatted.contains("సిఫార్సు చేసిన నివారణ చర్యలు:"))
        assertTrue("Must highlight 15L pump dosage", formatted.contains("15 లీటర్ల చేతి పంపుకు"))
        assertTrue("Must have Farmer Advice header", formatted.contains("రైతుకు ముఖ్య సూచన & రక్షణ చర్యలు:"))
        assertTrue("Must end with dedicated Telugu citation box", formatted.contains("📚 ప్రామాణిక సమాచార మూలం:"))
        assertTrue("Must cite CIBRC approved pesticide label claim", formatted.contains("CIBRC") && formatted.contains("CIR-11425/2014"))
    }

    @Test
    fun testFullCardFormattingEnglishProducesFarmerFriendlySections() {
        val rawCard = """
            Crop & Practices: Chickpea (Cicer arietinum) - Gram Pod Borer (Helicoverpa armigera) IPM.
            Symptoms & ETL: Larvae bore circular holes into green pods consuming developing seeds. ETL: 1 larva/meter row.
            IPM: 1) Install 20 T-shaped bird perches/acre. 2) Erect 5 pheromone traps/acre. 3) Spray 5% NSKE at flower initiation.
            CIBRC Approved Chemical Control: Emamectin Benzoate 5% SG @ 88 g/acre (approx 6.6 g per 15L backpack pump in 200L water), Pre-Harvest Interval (PHI): 14 days.
            Safety & PPE: Wear chemical-resistant gloves and respirator mask.
        """.trimIndent()

        val formatted = OfflineKnowledgeFormatter.format(rawCard, "Chickpea", "en")

        assertFalse("Must not contain Latin binomial 'Cicer arietinum'", formatted.contains("Cicer arietinum"))
        assertFalse("Must not contain Latin binomial 'Helicoverpa armigera'", formatted.contains("Helicoverpa armigera"))
        assertTrue("Must contain Problem/Crop header", formatted.contains("Problem / Crop:"))
        assertTrue("Must contain What to Observe header", formatted.contains("What to Observe"))
        assertTrue("Must contain Recommended Actions header", formatted.contains("Recommended Actions:"))
        assertTrue("Must contain 15L knapsack pump advice", formatted.contains("15L"))
        assertTrue("Must contain Farmer Advisory & Safety header", formatted.contains("Farmer Advisory & Safety:"))
        assertTrue("Must end with dedicated English citation box", formatted.contains("📚 Source Citation:"))
        assertTrue("Must cite CIBRC registration number", formatted.contains("CIR-11425/2014"))
    }

    @Test
    fun testCibrcApprovedPesticideLabelDetection() {
        val text = "Spray Proclaim 5% SG or Coragen 18.5% SC per knapsack pump."
        val detected = OfflineKnowledgeFormatter.findCibrcPesticides(text)
        assertTrue("Must detect at least 2 pesticides", detected.size >= 2)
        val regNumbers = detected.map { it.regNumber }
        assertTrue("Must contain Emamectin Proclaim reg number", regNumbers.contains("CIR-11425/2014"))
        assertTrue("Must contain Chlorantraniliprole Coragen reg number", regNumbers.contains("CIR-25114/2017"))
    }

    @Test
    fun testResolveCitationsForCardIncludesInstitutionAndCibrcLabel() {
        val entity = com.bolkisan.farmer.data.local.entities.OfflineKnowledgeEntity(
            id = "test-chk",
            collectionId = "KB05",
            crop = "Bengal Gram",
            category = "pest_management",
            title = "Chickpea: Pod Borer IPM & Controls",
            content = "Apply Emamectin Benzoate 5% SG (Proclaim) @ 88 g/acre.",
            sourceCitation = "ICAR-IIPR Kanpur Chickpea Production Technology 2024",
            language = "en",
            lastVerifiedDate = "2026-11-09"
        )
        val citations = OfflineKnowledgeFormatter.resolveCitationsForCard(entity, entity.content, "en")
        assertTrue("Must contain primary institution citation", citations.any { it.contains("ICAR-IIPR") })
        assertTrue("Must contain CIBRC approved product label claim", citations.any { it.contains("CIR-11425/2014") && it.contains("Proclaim") })
    }

    @Test
    fun testPromptEngineeringEnforcesDirectFarmerGuidelines() {
        val query = "ఆకులు పసుపు రంగులోకి మారుతున్నాయి, ఏం చేయాలి?"
        val context = "Cotton sucking pests: Whitefly, Aphids, and Jassids cause yellowing."
        val prompt = OfflineInferenceEngine.buildGroundedPrompt(query, context, "te")

        assertTrue("Prompt must enforce strictly 3-5 bullet points", prompt.contains("3 నుండి 5 చిన్న, ఆచరణాత్మక అంశాలలో"))
        assertTrue("Prompt must ban academic Latin scientific names", prompt.contains("లాటిన్"))
        assertTrue("Prompt must instruct pump dosage per 15L", prompt.contains("15 లీటర్ల"))
        assertTrue("Prompt must instruct differential possibilities for vague symptoms", prompt.contains("అస్పష్టమైన లక్షణాలు"))
        assertTrue("Prompt must instruct 1-2 diagnostic questions", prompt.contains("పరిశీలన ప్రశ్నలు"))
        assertTrue("Prompt must include farmer question", prompt.contains(query))
        assertTrue("Prompt must include grounding context", prompt.contains(context))
    }

    @Test
    fun testCleanModelResponseStripsThinkAndCodeBlocks() {
        val rawWithThink = """
            <think>
            The farmer is asking about cotton yellow leaves.
            I should recommend checking for sucking pests or nitrogen deficiency.
            </think>
            1. ఆకుల అడుగున తెల్లదోమ లేదా పచ్చదోమ ఉందో లేదో గమనించండి.
            2. 15 లీటర్ల పంపుకు 5% వేప నూనె 50 మి.లీ కలిపి పిచికారీ చేయండి.
        """.trimIndent()

        val cleaned = OfflineInferenceEngine.cleanModelResponse(rawWithThink)

        assertFalse("Cleaned response must not contain <think> tag", cleaned.contains("<think>"))
        assertFalse("Cleaned response must not contain inner thinking", cleaned.contains("The farmer is asking"))
        assertTrue("Cleaned response must retain farmer advisory", cleaned.contains("15 లీటర్ల పంపుకు"))
    }
}
