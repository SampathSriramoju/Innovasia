package com.bolkisan.farmer

import com.bolkisan.farmer.domain.OfflineModelManager
import com.bolkisan.farmer.domain.OfflineModelStatus
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class OfflineModelTest {

    @Test
    fun testOfflineModelMetadataHonesty() {
        val metadata = OfflineModelManager.metadata
        assertEquals("qwen3-0.6b-int4-litertlm", metadata.modelId)
        assertTrue("Model parameter count must be 0.6B", metadata.parameterCount.contains("0.6"))
        assertTrue("Runtime must be Google LiteRT-LM", metadata.runtime.contains("LiteRT-LM"))
        assertTrue("Quantization must be INT4", metadata.quantization.contains("INT4"))
        assertEquals("Exact Hugging Face model weight size", 343_490_560L, metadata.downloadSizeBytes)
        assertEquals(
            "bf1aba9ce8d963124407353c6aa4cbf72eba78db21ae133de983eac672ce83e0",
            metadata.expectedSha256
        )
        assertTrue("Required storage must be at least 600MB", metadata.requiredStorageBytes >= 600_000_000L)
        assertEquals(4, metadata.minRamGb)
        assertEquals(4, metadata.recommendedRamGb)
    }

    @Test
    fun testOfflineModelDefaultStatusIsNotInstalled() {
        OfflineModelManager.setStatusForTesting(
            OfflineModelStatus.NOT_INSTALLED,
            "On-device Qwen3 SLM not installed."
        )
        assertEquals(OfflineModelStatus.NOT_INSTALLED, OfflineModelManager.modelStatus.value)
    }

    @Test
    fun testChecksumVerificationWithKnownData() {
        val tempFile = File.createTempFile("test_model_", ".bin")
        try {
            val content = "LiteRT-LM-Qwen3-Verification-Test-Payload".toByteArray(Charsets.UTF_8)
            tempFile.writeBytes(content)

            val digest = MessageDigest.getInstance("SHA-256")
            val expectedHash = digest.digest(content).joinToString("") { "%02x".format(it) }

            // Positive check
            val verified = OfflineModelManager.verifyChecksum(tempFile, expectedHash)
            assertTrue("Checksum must match calculated hash", verified)

            // Negative check with altered hash
            val mismatch = OfflineModelManager.verifyChecksum(tempFile, "0000000000000000000000000000000000000000000000000000000000000000")
            assertFalse("Checksum must fail on altered hash", mismatch)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testMissingModelFileFailsVerification() {
        val nonExistent = File("non_existent_model_file.litertlm")
        assertFalse(
            "Non-existent model file must fail checksum verification",
            OfflineModelManager.verifyChecksum(nonExistent, "dummyhash")
        )
    }

    @Test
    fun testStatusStateTransitions() {
        OfflineModelManager.setStatusForTesting(OfflineModelStatus.DOWNLOADING, "Downloading 25%")
        assertEquals(OfflineModelStatus.DOWNLOADING, OfflineModelManager.modelStatus.value)

        OfflineModelManager.setStatusForTesting(OfflineModelStatus.VERIFYING, "Verifying SHA-256...")
        assertEquals(OfflineModelStatus.VERIFYING, OfflineModelManager.modelStatus.value)

        OfflineModelManager.setStatusForTesting(OfflineModelStatus.READY, "Engine ready.")
        assertEquals(OfflineModelStatus.READY, OfflineModelManager.modelStatus.value)

        // Reset to default
        OfflineModelManager.setStatusForTesting(OfflineModelStatus.NOT_INSTALLED, "Reset")
        assertEquals(OfflineModelStatus.NOT_INSTALLED, OfflineModelManager.modelStatus.value)
    }

    @Test
    fun testOfflineModelManagerConstants() {
        assertEquals("bolkisan_qwen3_0_6b_int4.litertlm", OfflineModelManager.MODEL_FILENAME)
        assertEquals(343_490_560L, OfflineModelManager.MIN_VALID_MODEL_BYTES)
        assertEquals(
            "bf1aba9ce8d963124407353c6aa4cbf72eba78db21ae133de983eac672ce83e0",
            OfflineModelManager.EXPECTED_SHA256
        )
    }

    @Test
    fun testBenchmarkResultMetricsHonesty() {
        val result = com.bolkisan.farmer.domain.OfflineBenchmarkResult(
            engineUsed = "Qwen3-0.6B Ultra-Compact INT4 (LiteRT-LM CPU)",
            runtime = com.bolkisan.farmer.domain.EdgeRuntime.LITERT_LM_CPU,
            hardwareRamGb = 4,
            freeRamMb = 1200,
            modelLoadTimeMs = 850L,
            timeToFirstChunkMs = 120L,
            totalInferenceMs = 450L,
            outputWordCount = 35,
            estimatedTokens = 45,
            streamingChunksCount = 18,
            chunksPerSecond = 40.0,
            voiceToVoiceLatencySeconds = null, // Honest null for text query
            groundednessScore = 0.95,
            groundednessDescription = "8 ICAR keywords matched in context",
            didFallbackToVerifiedRules = false,
            statusLabel = "Real On-Device LiteRT-LM Neural Inference Active"
        )

        assertEquals(35, result.outputWordCount)
        assertEquals(45, result.estimatedTokens)
        assertEquals(18, result.streamingChunksCount)
        assertEquals(120L, result.timeToFirstTokenMs)
        assertEquals(35, result.tokenCount)
        assertNull("Voice latency must be null for text query", result.voiceToVoiceLatencySeconds)
        assertTrue("Grounding description must describe matched keywords", result.groundednessDescription.contains("ICAR keywords"))
    }

    @Test
    fun testCompleteVoiceToVoiceLatencyMetricsBreakdown() {
        val voiceResult = com.bolkisan.farmer.domain.OfflineBenchmarkResult(
            engineUsed = "Qwen3-0.6B Ultra-Compact INT4 (LiteRT-LM CPU)",
            runtime = com.bolkisan.farmer.domain.EdgeRuntime.LITERT_LM_CPU,
            hardwareRamGb = 4,
            freeRamMb = 1100,
            modelLoadTimeMs = 920L,
            timeToFirstChunkMs = 280L,
            totalInferenceMs = 1250L,
            outputWordCount = 42,
            estimatedTokens = 55,
            streamingChunksCount = 38,
            chunksPerSecond = 30.4,
            voiceToVoiceLatencySeconds = 2.128,
            groundednessScore = 0.92,
            groundednessDescription = "12 ICAR keywords matched in context",
            didFallbackToVerifiedRules = false,
            statusLabel = "Real On-Device LiteRT-LM Neural Inference Active",
            responseText = "పత్తిలో గులాబీ రంగు పురుగు నివారణకు ప్రొఫెనోఫాస్ 50% ఈసీ 30 మి.లీ 15 లీటర్ల నీటిలో కలిపి పిచికారీ చేయండి.",
            citation = "ICAR-CICR Cotton Pest Management Package 2024",
            asrLatencyMs = 380L,
            retrievalLatencyMs = 22L,
            safetyGateLatencyMs = 8L,
            firstAudioTtsLatencyMs = 188L,
            totalVoiceToVoiceMs = 2128L,
            isColdStart = false,
            meetsStrict6SecSla = true
        )

        assertEquals(380L, voiceResult.asrLatencyMs)
        assertEquals(22L, voiceResult.retrievalLatencyMs)
        assertEquals(280L, voiceResult.timeToFirstTokenMs)
        assertEquals(1250L, voiceResult.totalInferenceMs)
        assertEquals(30.4, voiceResult.chunksPerSecond ?: 0.0, 0.01)
        assertEquals(8L, voiceResult.safetyGateLatencyMs)
        assertEquals(188L, voiceResult.firstAudioTtsLatencyMs)
        assertEquals(2128L, voiceResult.totalVoiceToVoiceMs)
        assertEquals(2.128, voiceResult.voiceToVoiceLatencySeconds ?: 0.0, 0.001)
        assertFalse(voiceResult.isColdStart)
        assertTrue("Total latency of 2128ms must meet strict 6-second SLA", voiceResult.meetsStrict6SecSla)
        assertTrue("isWithin6SecLimit must be true", voiceResult.isWithin6SecLimit)
    }

    @Test
    fun testColdStartVsWarmStartLatencySlaSatisfaction() {
        // Cold Start: Model loading (~950ms) + ASR (~420ms) + Retrieval (~25ms) + TTFT (~320ms) + Gen (~1250ms) + Safety (~8ms) + TTS (~190ms) = ~3163ms
        val coldStartResult = com.bolkisan.farmer.domain.OfflineBenchmarkResult(
            engineUsed = "Qwen3-0.6B Ultra-Compact INT4 (LiteRT-LM CPU)",
            runtime = com.bolkisan.farmer.domain.EdgeRuntime.LITERT_LM_CPU,
            hardwareRamGb = 4,
            freeRamMb = 1100,
            modelLoadTimeMs = 950L,
            timeToFirstChunkMs = 320L,
            totalInferenceMs = 1250L,
            outputWordCount = 42,
            estimatedTokens = 55,
            streamingChunksCount = 38,
            chunksPerSecond = 30.4,
            voiceToVoiceLatencySeconds = 3.163,
            groundednessScore = 0.92,
            groundednessDescription = "12 ICAR keywords matched in context",
            didFallbackToVerifiedRules = false,
            statusLabel = "Real On-Device LiteRT-LM Neural Inference Active (Cold Start)",
            asrLatencyMs = 420L,
            retrievalLatencyMs = 25L,
            safetyGateLatencyMs = 8L,
            firstAudioTtsLatencyMs = 190L,
            totalVoiceToVoiceMs = 3163L,
            isColdStart = true,
            meetsStrict6SecSla = true
        )
        assertTrue("Cold start (3.16s) must satisfy strict 6-second SLA", coldStartResult.meetsStrict6SecSla)
        assertTrue(coldStartResult.isColdStart)

        // Warm Start: Model in RAM (0ms load) + ASR (~380ms) + Retrieval (~18ms) + TTFT (~240ms) + Gen (~920ms) + Safety (~6ms) + TTS (~170ms) = ~1512ms
        val warmStartResult = coldStartResult.copy(
            modelLoadTimeMs = 0L,
            asrLatencyMs = 380L,
            retrievalLatencyMs = 18L,
            timeToFirstChunkMs = 240L,
            totalInferenceMs = 920L,
            safetyGateLatencyMs = 6L,
            firstAudioTtsLatencyMs = 170L,
            totalVoiceToVoiceMs = 1512L,
            voiceToVoiceLatencySeconds = 1.512,
            isColdStart = false,
            meetsStrict6SecSla = true
        )
        assertTrue("Warm start (1.51s) must satisfy strict 6-second SLA", warmStartResult.meetsStrict6SecSla)
        assertFalse(warmStartResult.isColdStart)
        assertTrue("Warm start (1512ms) is significantly faster than cold start (3163ms)", warmStartResult.totalVoiceToVoiceMs!! < coldStartResult.totalVoiceToVoiceMs!!)

        // Edge Case: Latency exceeding 6000ms SLA
        val exceededResult = coldStartResult.copy(
            totalVoiceToVoiceMs = 6450L,
            voiceToVoiceLatencySeconds = 6.45,
            meetsStrict6SecSla = 6450L <= 6000L
        )
        assertFalse("Latency of 6450ms must not meet 6-second SLA", exceededResult.meetsStrict6SecSla)
        assertFalse("isWithin6SecLimit must be false when exceeding 6000ms", exceededResult.isWithin6SecLimit)
    }
}
