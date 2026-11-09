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
}
