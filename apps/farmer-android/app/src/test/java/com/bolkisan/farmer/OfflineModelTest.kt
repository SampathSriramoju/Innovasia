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
        assertEquals("qwen3-0.6b-litertlm", metadata.modelId)
        assertTrue("Model parameter count must be 0.6B", metadata.parameterCount.contains("0.6"))
        assertTrue("Runtime must be Google LiteRT-LM", metadata.runtime.contains("LiteRT-LM"))
        assertTrue("Quantization must be Dynamic INT8", metadata.quantization.contains("INT8"))
        assertEquals("Exact Hugging Face model weight size", 613_722_264L, metadata.downloadSizeBytes)
        assertEquals(
            "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108",
            metadata.expectedSha256
        )
        assertTrue("Required storage must be at least 1GB", metadata.requiredStorageBytes >= 1_000_000_000L)
        assertEquals(4, metadata.minRamGb)
        assertEquals(6, metadata.recommendedRamGb)
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
}
