package com.bolkisan.farmer

import com.bolkisan.farmer.domain.OfflineModelCandidate
import com.bolkisan.farmer.domain.PreinstalledModelInfo
import org.junit.Assert.*
import org.junit.Test

class OfflineModelTest {

    @Test
    fun testPreinstalledModelInfoDefaults() {
        val modelInfo = PreinstalledModelInfo(
            modelId = "agriparam-q4-edge",
            name = "AgriParam-Q4 Edge SLM",
            version = "1.0.4",
            architecture = "Transformer-Decoder-Quantized-INT4",
            runtime = "LiteRT-LM / GGUF Edge Runtime",
            parameters = "2.1 Billion",
            quantization = "INT4_AWQ",
            fileSizeBytes = 262144L,
            isPreinstalled = true,
            isReady = true,
            localFilePath = "assets://models/agriparam_q4_edge.bin"
        )

        assertTrue(modelInfo.isPreinstalled)
        assertTrue(modelInfo.isReady)
        assertEquals("agriparam-q4-edge", modelInfo.modelId)
        assertEquals("INT4_AWQ", modelInfo.quantization)
        assertTrue(modelInfo.fileSizeBytes > 0)
    }

    @Test
    fun testOfflineModelCandidates() {
        val candidates = OfflineModelCandidate.values()
        assertTrue(candidates.contains(OfflineModelCandidate.AGRIPARAM_Q4))
        assertTrue(candidates.contains(OfflineModelCandidate.GEMMA_4_E2B_Q4))
    }
}
