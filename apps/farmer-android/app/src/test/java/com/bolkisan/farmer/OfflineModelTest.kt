package com.bolkisan.farmer

import com.bolkisan.farmer.domain.OfflineModelManager
import com.bolkisan.farmer.domain.OfflineModelStatus
import org.junit.Assert.*
import org.junit.Test

class OfflineModelTest {

    @Test
    fun testOfflineModelMetadataHonesty() {
        val metadata = OfflineModelManager.metadata
        assertEquals("gemma-2b-it-mobile-q4", metadata.modelId)
        assertTrue("Model parameter count must be 2.1B", metadata.parameterCount.contains("2.1"))
        assertTrue("Download size must be realistic (~1.45GB)", metadata.downloadSizeBytes > 1_000_000_000L)
        assertTrue("Required storage must be at least 2GB", metadata.requiredStorageBytes >= 2_000_000_000L)
        assertEquals(4, metadata.minRamGb)
    }

    @Test
    fun testOfflineModelDefaultStatusIsNotInstalled() {
        assertEquals(OfflineModelStatus.NOT_INSTALLED, OfflineModelManager.modelStatus.value)
    }
}
