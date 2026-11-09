package com.bolkisan.farmer

import com.bolkisan.farmer.domain.OfflineSafetyGate
import org.junit.Assert.*
import org.junit.Test

/**
 * Regression Test Suite against Production OfflineSafetyGate Logic.
 * Directly exercises production domain classes, never test-only helper duplicates.
 */
class OfflineSafetyRulesTest {

    @Test
    fun testProductionKnapsackDoseCalculation() {
        // CIBRC Label: Emamectin Benzoate 5% SG: 88g per acre in 200L water.
        val emamectinPump = OfflineSafetyGate.calculateKnapsackDose(88.0, 200.0, 15.0)
        assertEquals("Emamectin 88g/200L -> 6.6g per 15L knapsack", 6.6, emamectinPump, 0.01)

        // CIBRC Label: Flubendiamide 39.35% SC (Fame): 50 ml per acre in 200L water.
        val flubendiamidePump = OfflineSafetyGate.calculateKnapsackDose(50.0, 200.0, 15.0)
        assertEquals("Flubendiamide 50ml/200L -> 3.75ml per 15L knapsack", 3.75, flubendiamidePump, 0.01)

        // CIBRC Label: Chlorantraniliprole 18.5% SC (Coragen): 60 ml per acre in 200L water.
        val coragenPump = OfflineSafetyGate.calculateKnapsackDose(60.0, 200.0, 15.0)
        assertEquals("Coragen 60ml/200L -> 4.5ml per 15L knapsack", 4.5, coragenPump, 0.01)

        // CIBRC Label: Mancozeb 75% WP: 400g per acre in 200L water.
        val mancozebPump = OfflineSafetyGate.calculateKnapsackDose(400.0, 200.0, 15.0)
        assertEquals("Mancozeb 400g/200L -> 30g per 15L knapsack", 30.0, mancozebPump, 0.01)
    }

    @Test
    fun testProductionBannedChemicalDetectionList() {
        val testQueries = listOf(
            "Can I use Endosulfan 35 EC for cotton bollworm?" to "Endosulfan",
            "Spray Thiodan on my crop" to "Endosulfan",
            "What is the dose of Methyl Parathion for rice?" to "Methyl Parathion",
            "How much Metacid to apply?" to "Methyl Parathion",
            "Can I apply Phorate 10g in soil?" to "Phorate",
            "Phosphamidon spray dose for aphids" to "Phosphamidon",
            "DDT powder application for chickpea" to "DDT",
            "Aldicarb or Temik for root nematodes" to "Aldicarb",
            "Dichlorvos or DDVP spray for storage pests" to "Dichlorvos",
            "Diazinon spray for sugarcane" to "Diazinon"
        )

        for ((query, expectedName) in testQueries) {
            val (isBanned, rule) = OfflineSafetyGate.isBannedChemical(query)
            assertTrue("Banned chemical $expectedName must be detected in: '$query'", isBanned)
            assertNotNull("Rule must not be null for $expectedName", rule)
            assertEquals(expectedName, rule?.name)
            assertTrue("Banned chemical must be marked as total ban", rule?.isTotalBan == true)
        }
    }

    @Test
    fun testVegetableMonocrotophosBanRule() {
        // Monocrotophos on vegetables is strictly prohibited by CIBRC
        val (chilliBanned, chilliRule) = OfflineSafetyGate.isBannedChemical("monocrotophos spray", "chilli")
        assertTrue("Monocrotophos must be banned on chilli", chilliBanned)
        assertEquals("Monocrotophos", chilliRule?.name)

        val (tomatoBanned, _) = OfflineSafetyGate.isBannedChemical("nuvacron dose for tomato", "tomato")
        assertTrue("Nuvacron (Monocrotophos) must be banned on tomato", tomatoBanned)

        val (brinjalBanned, _) = OfflineSafetyGate.isBannedChemical("monocil on brinjal", "brinjal")
        assertTrue("Monocil (Monocrotophos) must be banned on brinjal", brinjalBanned)

        val (okraBanned, _) = OfflineSafetyGate.isBannedChemical("మోనోక్రోటోఫాస్ బెండకాయ", "బెండకాయ")
        assertTrue("Monocrotophos must be banned on okra (Telugu)", okraBanned)

        // Monocrotophos on Cotton is permitted under strict CIBRC label
        val (cottonBanned, _) = OfflineSafetyGate.isBannedChemical("monocrotophos for cotton aphid", "cotton")
        assertFalse("Monocrotophos is not banned on cotton (fiber crop)", cottonBanned)
    }

    @Test
    fun testUnapprovedTankMixtureDetection() {
        assertTrue(
            "Tank mix with 'mix' and 'and' must be detected",
            OfflineSafetyGate.detectTankMixture("Can I mix Coragen and Bavistin in the same tank?")
        )
        assertTrue(
            "Tank mix with '+' sign must be detected",
            OfflineSafetyGate.detectTankMixture("Spray Rogor + Neem oil on chilli")
        )
        assertTrue(
            "Telugu tank mix phrase must be detected",
            OfflineSafetyGate.detectTankMixture("రెండు మందులు కలిపి పిచికారీ చేయవచ్చా?")
        )
        assertTrue(
            "Hindi tank mix phrase must be detected",
            OfflineSafetyGate.detectTankMixture("दवाइयों का मिश्रण मिलाकर छिड़काव करें")
        )
    }

    @Test
    fun testValidateResponseInterceptsBannedChemical() {
        val result = OfflineSafetyGate.validateResponse(
            query = "Can I spray Endosulfan for cotton bollworm?",
            responseText = "Yes, spray Endosulfan 35 EC @ 2ml/L.",
            retrievedContext = "Standard ICAR IPM practices.",
            crop = "Cotton",
            language = "en"
        )

        assertFalse("Banned chemical response must be marked unsafe", result.isSafe)
        assertTrue("Needs escalation must be true", result.needsEscalation)
        assertEquals("CRITICAL", result.severity)
        assertEquals("Endosulfan", result.detectedBannedChemical)
        assertTrue("Sanitized response must contain statutory warning", result.sanitizedResponse.contains("prohibited"))
    }

    @Test
    fun testValidateResponseInterceptsUnapprovedTankMixture() {
        val result = OfflineSafetyGate.validateResponse(
            query = "How to control aphids and powdery mildew?",
            responseText = "You should mix Rogor and Bavistin in the tank for combined control.",
            retrievedContext = "ICAR Package of Practices.",
            crop = "Chilli",
            language = "en"
        )

        assertFalse("Tank mix response must be marked unsafe", result.isSafe)
        assertTrue("Needs escalation must be true", result.needsEscalation)
        assertEquals("HIGH", result.severity)
        assertTrue("Tank mix detected flag must be true", result.isTankMixDetected)
    }

    @Test
    fun testValidateResponseRejectsRogueDosageWithoutContext() {
        val result = OfflineSafetyGate.validateResponse(
            query = "What chemical to spray for leaf spot?",
            responseText = "Spray Hexaconazole 5% EC at 2.5 ml/L.",
            retrievedContext = "", // Zero verified context
            crop = "Cotton",
            language = "en"
        )

        assertFalse("Rogue dosage without verified context must be marked unsafe", result.isSafe)
        assertTrue("Needs escalation must be true", result.needsEscalation)
        assertTrue("Warning must indicate rogue dosage suppression", result.warnings.any { it.contains("Rogue") })
    }

    @Test
    fun testValidateResponseCorrectsRogueNumbersAgainstVerifiedContext() {
        val verifiedContext = "Spray Tricyclazole 75% WP @ 0.6g/L at disease onset."
        val result = OfflineSafetyGate.validateResponse(
            query = "What is the dosage of Tricyclazole?",
            responseText = "Apply Tricyclazole 75% WP @ 5.0 g/L for quick control.", // Rogue 5.0 g/L
            retrievedContext = verifiedContext,
            crop = "Rice",
            language = "en"
        )

        assertTrue("Should return verified safe advisory substitution", result.isSafe)
        assertFalse("Needs escalation should be false since verified context was substituted", result.needsEscalation)
        assertTrue("Sanitized response must contain the verified ICAR context", result.sanitizedResponse.contains(verifiedContext))
        assertTrue("Warnings must note the substitution", result.warnings.any { it.contains("substituted") })
    }

    @Test
    fun testValidateResponseCleanAgronomicAdvicePasses() {
        val context = "Alternate Wetting and Drying (AWD): Drain standing water completely for 3-4 days upon detecting BPH."
        val result = OfflineSafetyGate.validateResponse(
            query = "How to manage BPH in paddy without chemicals?",
            responseText = "For BPH management, follow Alternate Wetting and Drying by draining standing water for 3-4 days.",
            retrievedContext = context,
            crop = "Rice",
            language = "en"
        )

        assertTrue("Clean agronomic advice must pass safety gate", result.isSafe)
        assertFalse("No escalation needed", result.needsEscalation)
        assertEquals("LOW", result.severity)
        assertTrue("Dosage verified flag must be true", result.isDosageVerified)
    }

    @Test
    fun testKnapsackDoseRefusesUnverifiedWaterVolume() {
        // Without verified spray volume per acre, calculateKnapsackDose must return 0.0 (never assume universal 200L)
        val unverifiedPump = OfflineSafetyGate.calculateKnapsackDose(88.0)
        assertEquals("Unverified water volume must return 0.0", 0.0, unverifiedPump, 0.001)

        // formatKnapsackDoseText without water volume must refuse naive conversion and provide calibration guidance
        val guidanceText = OfflineSafetyGate.formatKnapsackDoseText(80.0, "ml", language = "en")
        assertTrue("Must state verified per-acre rate", guidanceText.contains("80 ml/acre"))
        assertTrue("Must include KVK calibration guidance", guidanceText.contains("Knapsack spray volume depends on crop canopy"))
    }

    @Test
    fun testSpecificCropWaterVolumesAndDilutions() {
        // Bispyribac-sodium (Nominee Gold) in rice: 120 L/acre (300 L/ha)
        val nomineePump = OfflineSafetyGate.calculateKnapsackDose(80.0, 120.0, 15.0)
        assertEquals("Nominee Gold in 120L water: 80g/120L -> 10.0g per 15L pump", 10.0, nomineePump, 0.01)

        // Halosulfuron-methyl (Sempra) in sugarcane: 150 L/acre (375 L/ha)
        val sempraPump = OfflineSafetyGate.calculateKnapsackDose(36.0, 150.0, 15.0)
        assertEquals("Sempra in 150L water: 36g/150L -> 3.6g per 15L pump", 3.6, sempraPump, 0.01)

        // Clodinafop-propargyl (Topik) in wheat: 150 L/acre (375 L/ha)
        val topikPump = OfflineSafetyGate.calculateKnapsackDose(160.0, 150.0, 15.0)
        assertEquals("Topik in 150L water: 160g/150L -> 16.0g per 15L pump", 16.0, topikPump, 0.01)

        // Dilution per litre: Mancozeb 2.5 g/L -> 37.5 g per 15L pump
        val mancozebDilutionPump = OfflineSafetyGate.calculateKnapsackDoseFromDilution(2.5, 15.0)
        assertEquals("Mancozeb 2.5g/L dilution -> 37.5g per 15L pump", 37.5, mancozebDilutionPump, 0.01)
    }

    @Test
    fun testContradictoryDosageQueryIntercepted() {
        // Query asking for dangerous excessive dosage of Coragen (500 ml/acre vs label 60 ml/acre)
        val check = OfflineSafetyGate.evaluateQueryDosageSafety("Can I spray 500 ml/acre of Coragen?", "Cotton")
        assertNotNull("Excessive query dosage must be intercepted", check)
        assertFalse("Must be marked unsafe", check!!.isSafe)
        assertTrue("Must require escalation", check.needsEscalation)
        assertEquals("HIGH", check.severity)

        // Proposed dosage contradictory to verified context
        val context = "Coragen 18.5% SC @ 60 ml/acre in 200 L water"
        val compatCheck = OfflineSafetyGate.validateDosageCompatibility("Can I apply 250 ml/acre of Coragen?", context)
        assertNotNull("Contradictory dosage against context must be intercepted", compatCheck)
        assertFalse("Must be marked unsafe", compatCheck!!.isSafe)
        assertTrue("Must require escalation", compatCheck.needsEscalation)
    }
}
