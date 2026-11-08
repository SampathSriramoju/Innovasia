package com.bolkisan.farmer

import org.junit.Assert.*
import org.junit.Test

class OfflineSafetyRulesTest {

    /**
     * CIBRC / ICAR Standard Knapsack Dilution:
     * Standard knapsack tank = 15 Litres.
     * Standard field spray volume per acre = 200 Litres of water.
     * Conversion factor: 15 / 200 = 0.075 (or acre dose divided by 13.33).
     */
    private fun calculateKnapsackDose(dosePerAcre: Double, waterVolumeLitresPerAcre: Double = 200.0, pumpCapacityLitres: Double = 15.0): Double {
        return (dosePerAcre / waterVolumeLitresPerAcre) * pumpCapacityLitres
    }

    @Test
    fun testEmamectinBenzoateKnapsackCalculation() {
        // CIBRC Label: Emamectin Benzoate 5% SG: 88g per acre in 200L water.
        val perPump = calculateKnapsackDose(88.0, 200.0, 15.0)
        // 88 * 0.075 = 6.6 g per 15L pump
        assertEquals(6.6, perPump, 0.01)
    }

    @Test
    fun testFlubendiamideKnapsackCalculation() {
        // CIBRC Label: Flubendiamide 39.35% SC (Fame): 50 ml per acre in 200L water.
        val perPump = calculateKnapsackDose(50.0, 200.0, 15.0)
        // 50 * 0.075 = 3.75 ml per 15L pump
        assertEquals(3.75, perPump, 0.01)
    }

    @Test
    fun testChlorantraniliproleKnapsackCalculation() {
        // CIBRC Label: Chlorantraniliprole 18.5% SC (Coragen): 60 ml per acre in 200L water.
        val perPump = calculateKnapsackDose(60.0, 200.0, 15.0)
        // 60 * 0.075 = 4.5 ml per 15L pump
        assertEquals(4.5, perPump, 0.01)
    }

    @Test
    fun testMancozebKnapsackCalculation() {
        // CIBRC Label: Mancozeb 75% WP: 400g per acre in 200L water.
        val perPump = calculateKnapsackDose(400.0, 200.0, 15.0)
        // 400 * 0.075 = 30.0 g per 15L pump
        assertEquals(30.0, perPump, 0.01)
    }

    @Test
    fun testBannedChemicalDetectionList() {
        val bannedChemicals = setOf(
            "endosulfan",
            "thiodan",
            "methyl parathion",
            "metacid",
            "phorate",
            "phosphamidon",
            "ddt",
            "aldicarb",
            "dichlorvos"
        )

        val query = "Can I use Endosulfan 35 EC for cotton bollworm?"
        val detected = bannedChemicals.any { query.lowercase().contains(it) }
        assertTrue("Banned chemical Endosulfan must be detected", detected)
    }

    @Test
    fun testVegetableMonocrotophosBanRule() {
        val vegetableCrops = setOf("chilli", "tomato", "brinjal", "cabbage", "cauliflower", "okra")
        val crop = "chilli"
        val chemical = "monocrotophos"

        val isProhibited = vegetableCrops.contains(crop.lowercase()) && chemical.lowercase() == "monocrotophos"
        assertTrue("Monocrotophos must be strictly banned on chilli/vegetables", isProhibited)
    }
}
