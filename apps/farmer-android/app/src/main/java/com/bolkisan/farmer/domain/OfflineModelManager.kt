package com.bolkisan.farmer.domain

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class OfflineModelStatus {
    NOT_INSTALLED,
    DOWNLOADING,
    EXTRACTING,
    VERIFYING,
    INITIALIZING,
    READY,
    FAILED,
    UNSUPPORTED_DEVICE,
    INSUFFICIENT_MEMORY
}

data class OfflineModelMetadata(
    val modelId: String = "qwen3-0.6b-int4-litertlm",
    val name: String = "Qwen3-0.6B Ultra-Compact INT4",
    val version: String = "3.0.0",
    val architecture: String = "Qwen3-Transformer",
    val runtime: String = "Google LiteRT-LM 0.19.1",
    val parameterCount: String = "0.6 Billion",
    val quantization: String = "Dynamic INT4 Block-32",
    val downloadSizeBytes: Long = 343_490_560L, // 327.58 MB exact HF weight
    val requiredStorageBytes: Long = 629_145_600L, // 600 MB required
    val minRamGb: Int = 4,
    val recommendedRamGb: Int = 4,
    val officialSourceUrl: String = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/bd47201734a34f1eba8ed7d28289225b60152e9e/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
    val expectedSha256: String = "bf1aba9ce8d963124407353c6aa4cbf72eba78db21ae133de983eac672ce83e0",
    val license: String = "Apache-2.0"
)

data class OfflineCapabilities(
    val isOfflineKnowledgeAvailable: Boolean,
    val modelStatus: OfflineModelStatus,
    val isOfflineAsrAvailable: Boolean,
    val isOfflineTtsAvailable: Boolean,
    val asrStatusDescription: String = "Speech Engine",
    val ttsStatusDescription: String = "TTS Engine",
    val isBundledAssetPresent: Boolean,
    val isModelExtractedAndVerified: Boolean,
    val isEngineInitialized: Boolean,
    val totalRamGb: Int,
    val freeRamMb: Long,
    val installedModelPath: String?,
    val installedModelSizeBytes: Long
)

object OfflineModelManager {

    private const val TAG = "OfflineModelManager"
    private const val MODEL_DIR = "models"
    const val MODEL_BIN_NAME = "bolkisan_qwen3_0_6b_int4.litertlm"
    const val MODEL_FILENAME = MODEL_BIN_NAME
    const val MIN_VALID_MODEL_BYTES = 343_490_560L
    const val EXPECTED_SHA256 = "bf1aba9ce8d963124407353c6aa4cbf72eba78db21ae133de983eac672ce83e0"
    private const val EXTRACTION_TEMP_NAME = "bolkisan_qwen3_0_6b_int4.litertlm.tmp"

    private const val PREFS_VERIFICATION = "bolkisan_model_verification_ledger"
    private const val KEY_RECORD_SHA = "verified_sha256"
    private const val KEY_RECORD_SIZE = "verified_size_bytes"
    private const val KEY_RECORD_MODIFIED = "verified_last_modified"
    private const val KEY_RECORD_MODEL_ID = "verified_model_id"

    val metadata = OfflineModelMetadata()

    private val _modelStatus = MutableStateFlow(OfflineModelStatus.NOT_INSTALLED)
    val modelStatus: StateFlow<OfflineModelStatus> = _modelStatus.asStateFlow()

    private val _extractionProgress = MutableStateFlow(0f)
    val extractionProgress: StateFlow<Float> = _extractionProgress.asStateFlow()
    val downloadProgress: StateFlow<Float> = _extractionProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow("On-device Qwen3 SLM standby. Verified Room SQLite Knowledge active.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    fun getModelFile(context: Context): File {
        val dir = File(context.filesDir, MODEL_DIR).apply { mkdirs() }
        return File(dir, MODEL_BIN_NAME)
    }

    private fun getTempFile(context: Context): File {
        val dir = File(context.filesDir, MODEL_DIR).apply { mkdirs() }
        return File(dir, EXTRACTION_TEMP_NAME)
    }

    fun markModelVerified(context: Context, file: File, sha256: String) {
        val prefs = context.getSharedPreferences(PREFS_VERIFICATION, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_RECORD_SHA, sha256)
            .putLong(KEY_RECORD_SIZE, file.length())
            .putLong(KEY_RECORD_MODIFIED, file.lastModified())
            .putString(KEY_RECORD_MODEL_ID, metadata.modelId)
            .apply()
        Log.i(TAG, "Recorded trustworthy verification record for ${file.name} (SHA: ${sha256.take(8)}...)")
    }

    fun clearModelVerificationRecord(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_VERIFICATION, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    /**
     * Checks if the built-in model asset exists in the APK assets.
     */
    fun isBundledModelAssetAvailable(context: Context): Boolean {
        return try {
            val assetList = context.assets.list("models") ?: emptyArray()
            MODEL_BIN_NAME in assetList || "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm" in assetList
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks whether a genuine model file exists on disk, meets exact size, and matches SHA-256.
     * Uses persistent verification ledger to avoid hashing 343MB on every farmer question (O(1)).
     * Only recalculates full byte-by-byte SHA-256 when forceRecheck is true or file modified.
     */
    fun isGenuineModelInstalled(context: Context, forceRecheckHash: Boolean = false): Boolean {
        val file = getModelFile(context)
        if (!file.exists() || file.length() != metadata.downloadSizeBytes) {
            return false
        }

        val prefs = context.getSharedPreferences(PREFS_VERIFICATION, Context.MODE_PRIVATE)
        val recordedSha = prefs.getString(KEY_RECORD_SHA, null)
        val recordedSize = prefs.getLong(KEY_RECORD_SIZE, -1L)
        val recordedTime = prefs.getLong(KEY_RECORD_MODIFIED, -1L)
        val recordedId = prefs.getString(KEY_RECORD_MODEL_ID, null)

        val hasValidRecord = !forceRecheckHash &&
                recordedSha.equals(metadata.expectedSha256, ignoreCase = true) &&
                recordedSize == file.length() &&
                recordedTime == file.lastModified() &&
                recordedId == metadata.modelId

        if (hasValidRecord) {
            return true
        }

        // Full cryptographic re-verification
        val isHashValid = verifyChecksum(file, metadata.expectedSha256)
        if (isHashValid) {
            markModelVerified(context, file, metadata.expectedSha256)
        }
        return isHashValid
    }

    /**
     * Verifies the cryptographic SHA-256 checksum of any model file.
     */
    fun verifyChecksum(file: File, expectedSha: String): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(128 * 1024)
            FileInputStream(file).use { fis ->
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            hash.equals(expectedSha, ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Checksum verification failed: ${e.message}", e)
            false
        }
    }

    /**
     * Hardware memory and storage suitability check based on real measurements.
     * Enforces minimum 3.4 GB total physical RAM (4GB-class hardware tier),
     * minimum 650 MB free RAM, lowMemory flag guard, and required storage.
     */
    fun checkHardwareSuitability(context: Context): Pair<Boolean, String> {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)

        val totalMemBytes = memInfo.totalMem
        val totalRamGb = Math.round(totalMemBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)).toInt().coerceAtLeast(1)
        val freeRamMb = memInfo.availMem / (1024 * 1024)
        val isLowMemory = memInfo.lowMemory

        // Device physical RAM tier check: true 4GB devices have totalMem around 3.5GB - 3.8GB
        if (totalMemBytes < 3_400_000_000L) {
            return Pair(
                false,
                "Device physical RAM: ${String.format("%.2f", totalMemBytes / (1024.0 * 1024.0 * 1024.0))}GB (< 3.4GB 4GB-tier required). Safe Room SQLite active."
            )
        }

        // Active memory pressure guard
        if (isLowMemory) {
            return Pair(
                false,
                "Device is currently under memory pressure (lowMemory=true, ${freeRamMb}MB free). Safe Room SQLite active to prevent OS kill."
            )
        }

        if (freeRamMb < 650) {
            return Pair(
                false,
                "Low available memory: ${freeRamMb}MB free (< 650MB required for LiteRT-LM runtime). Safe Room SQLite active."
            )
        }

        val stat = StatFs(context.filesDir.path)
        val freeStorageBytes = stat.availableBlocksLong * stat.blockSizeLong
        val freeStorageMb = freeStorageBytes / (1024 * 1024)

        if (freeStorageBytes < metadata.requiredStorageBytes) {
            return Pair(
                false,
                "Insufficient storage: ${freeStorageMb}MB free (${metadata.requiredStorageBytes / (1024 * 1024)}MB required)."
            )
        }

        return Pair(true, "Hardware measured suitable: ${totalRamGb}GB RAM, ${freeRamMb}MB free, ${freeStorageMb}MB storage.")
    }

    /**
     * First-launch model provisioning sequence:
     * 1. Check if model is already extracted and verified
     * 2. Validate hardware suitability (RAM & storage)
     * 3. Extract bundled model asset into temporary file
     * 4. Verify exact byte size and SHA-256 checksum
     * 5. Atomically commit model to app private storage
     * 6. Initialize LiteRT-LM runtime
     * 7. Run genuine inference smoke test
     * 8. Mark READY only after smoke test succeeds!
     */
    suspend fun provisionBundledModelOnFirstLaunch(context: Context, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val targetFile = getModelFile(context)

        // Step 1: Check if already provisioned & verified (skip if not forced)
        if (!force && targetFile.exists() && targetFile.length() == metadata.downloadSizeBytes && verifyChecksum(targetFile, metadata.expectedSha256)) {
            Log.i(TAG, "Model already provisioned and verified on disk. Initializing engine...")
            val ready = OfflineInferenceEngine.getInstance(context).initializeEngine()
            if (ready) {
                _modelStatus.value = OfflineModelStatus.READY
                _statusMessage.value = "${metadata.name} active on LiteRT-LM (${OfflineInferenceEngine.getInstance(context).getActiveRuntime()})."
                return@withContext true
            }
        }

        // Step 2: Hardware suitability check
        val (suitable, reason) = checkHardwareSuitability(context)
        if (!suitable) {
            _modelStatus.value = if (reason.contains("RAM")) OfflineModelStatus.UNSUPPORTED_DEVICE else OfflineModelStatus.INSUFFICIENT_MEMORY
            _statusMessage.value = reason
            return@withContext false
        }

        // Step 3: Locate bundled asset in APK
        val assetList = try { context.assets.list("models") ?: emptyArray() } catch (_: Exception) { emptyArray() }
        val assetName = listOf(MODEL_BIN_NAME, "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm")
            .firstOrNull { it in assetList }

        if (assetName == null) {
            Log.w(TAG, "Bundled model asset not found in APK package.")
            _modelStatus.value = OfflineModelStatus.NOT_INSTALLED
            _statusMessage.value = "Bundled model asset not found in APK package. Room SQLite knowledge active."
            return@withContext false
        }

        val tempFile = getTempFile(context)
        if (tempFile.exists()) tempFile.delete()

        try {
            _modelStatus.value = OfflineModelStatus.EXTRACTING
            _statusMessage.value = "Extracting built-in offline SLM from APK..."
            _extractionProgress.value = 0f

            val digest = MessageDigest.getInstance("SHA-256")
            var totalRead = 0L

            context.assets.open("models/$assetName").use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        totalRead += read
                        _extractionProgress.value = (totalRead.toFloat() / metadata.downloadSizeBytes).coerceIn(0f, 1f)
                    }
                    output.flush()
                }
            }

            // Step 4: Verification of size and SHA-256
            _modelStatus.value = OfflineModelStatus.VERIFYING
            _statusMessage.value = "Verifying cryptographic SHA-256 checksum..."

            val calcHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (totalRead != metadata.downloadSizeBytes || !calcHash.equals(metadata.expectedSha256, ignoreCase = true)) {
                Log.e(TAG, "Checksum mismatch! Expected: ${metadata.expectedSha256}, Got: $calcHash (Size: $totalRead)")
                tempFile.delete()
                _modelStatus.value = OfflineModelStatus.FAILED
                _statusMessage.value = "Model verification failed: Checksum or byte size mismatch."
                return@withContext false
            }

            // Step 5: Atomic file rename
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                Log.e(TAG, "Atomic rename of model file failed.")
                tempFile.delete()
                _modelStatus.value = OfflineModelStatus.FAILED
                _statusMessage.value = "Failed to commit verified model file to app storage."
                return@withContext false
            }
            markModelVerified(context, targetFile, metadata.expectedSha256)

            // Step 6: Engine initialization
            _modelStatus.value = OfflineModelStatus.INITIALIZING
            _statusMessage.value = "Initializing LiteRT-LM runtime..."

            val engineInit = OfflineInferenceEngine.getInstance(context).initializeEngine()
            if (!engineInit) {
                targetFile.delete()
                clearModelVerificationRecord(context)
                _modelStatus.value = OfflineModelStatus.FAILED
                _statusMessage.value = "LiteRT-LM runtime initialization failed on this device."
                return@withContext false
            }

            // Step 7: Genuine inference smoke test
            _statusMessage.value = "Executing on-device inference smoke test..."
            val smokeTest = OfflineInferenceEngine.getInstance(context).executeOfflineInference(
                query = "Bol Kisan model verification smoke test",
                retrievedContextText = "ICAR agricultural knowledge verification.",
                retrievedCitation = "ICAR-BolKisan",
                language = "en"
            )

            if (smokeTest.didFallbackToVerifiedRules || smokeTest.responseText.isBlank()) {
                Log.w(TAG, "Smoke test failed to produce real SLM output: ${smokeTest.statusLabel}")
                _modelStatus.value = OfflineModelStatus.FAILED
                _statusMessage.value = "Inference smoke test failed. Room SQLite fallback active."
                return@withContext false
            }

            _modelStatus.value = OfflineModelStatus.READY
            _statusMessage.value = "${metadata.name} verified and ready on ${smokeTest.engineUsed}."
            Log.i(TAG, "Built-in offline SLM fully provisioned, verified, and smoke-tested!")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Model provisioning error: ${e.message}", e)
            if (tempFile.exists()) tempFile.delete()
            _modelStatus.value = OfflineModelStatus.FAILED
            _statusMessage.value = "Provisioning error: ${e.message}"
            return@withContext false
        }
    }

    /**
     * Evaluates independent offline subsystem statuses for the UI dashboard.
     */
    fun getOfflineCapabilities(context: Context, hasRoomRecords: Boolean = true): OfflineCapabilities {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val totalRamGb = Math.round(memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)).toInt().coerceAtLeast(1)
        val freeRamMb = memInfo.availMem / (1024 * 1024)

        val modelFile = getModelFile(context)
        val hasGenuine = isGenuineModelInstalled(context)
        val isEngineReady = OfflineInferenceEngine.getInstance(context).isEngineReady()
        val isAssetBundled = isBundledModelAssetAvailable(context)

        val status = when {
            _modelStatus.value == OfflineModelStatus.FAILED -> OfflineModelStatus.FAILED
            _modelStatus.value == OfflineModelStatus.EXTRACTING -> OfflineModelStatus.EXTRACTING
            _modelStatus.value == OfflineModelStatus.VERIFYING -> OfflineModelStatus.VERIFYING
            _modelStatus.value == OfflineModelStatus.INITIALIZING -> OfflineModelStatus.INITIALIZING
            memInfo.totalMem < 3_400_000_000L -> OfflineModelStatus.UNSUPPORTED_DEVICE
            (freeRamMb < 650 || memInfo.lowMemory) && !hasGenuine -> OfflineModelStatus.INSUFFICIENT_MEMORY
            hasGenuine && isEngineReady -> OfflineModelStatus.READY
            hasGenuine -> OfflineModelStatus.INITIALIZING
            else -> OfflineModelStatus.NOT_INSTALLED
        }

        // Truthful ASR evaluation
        val speechAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        val onDeviceEnginePresent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            false
        }
        val asrDescription = when {
            onDeviceEnginePresent -> "On-Device Speech Engine Active (Supports offline voice)"
            speechAvailable -> "System Speech Recognition Available"
            else -> "Speech Recognizer Not Installed"
        }

        // Truthful TTS evaluation
        val ttsIntent = android.content.Intent(android.speech.tts.TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
        val resolveInfo = context.packageManager.queryIntentActivities(ttsIntent, 0)
        val ttsEngineInstalled = resolveInfo.isNotEmpty()
        val ttsDescription = when {
            ttsEngineInstalled -> "System TTS Engine Active (Multi-language voice output)"
            else -> "Text-to-Speech Engine Not Available"
        }

        return OfflineCapabilities(
            isOfflineKnowledgeAvailable = hasRoomRecords,
            modelStatus = status,
            isOfflineAsrAvailable = onDeviceEnginePresent || speechAvailable,
            isOfflineTtsAvailable = ttsEngineInstalled,
            asrStatusDescription = asrDescription,
            ttsStatusDescription = ttsDescription,
            isBundledAssetPresent = isAssetBundled,
            isModelExtractedAndVerified = hasGenuine,
            isEngineInitialized = isEngineReady,
            totalRamGb = totalRamGb,
            freeRamMb = freeRamMb,
            installedModelPath = if (hasGenuine) modelFile.absolutePath else null,
            installedModelSizeBytes = if (hasGenuine) modelFile.length() else 0L
        )
    }

    /**
     * Downloads model from official repository if not bundled.
     */
    suspend fun downloadModel(context: Context): Result<File> = withContext(Dispatchers.IO) {
        val targetFile = getModelFile(context)
        val tempFile = getTempFile(context)

        val (suitable, reason) = checkHardwareSuitability(context)
        if (!suitable) {
            _modelStatus.value = if (reason.contains("RAM")) OfflineModelStatus.UNSUPPORTED_DEVICE else OfflineModelStatus.INSUFFICIENT_MEMORY
            _statusMessage.value = reason
            return@withContext Result.failure(Exception(reason))
        }

        try {
            _modelStatus.value = OfflineModelStatus.DOWNLOADING
            _statusMessage.value = "Downloading ${metadata.name}..."
            _extractionProgress.value = 0f

            val url = java.net.URL(metadata.officialSourceUrl)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            conn.connect()

            val digest = MessageDigest.getInstance("SHA-256")
            var totalRead = 0L

            conn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        totalRead += read
                        _extractionProgress.value = (totalRead.toFloat() / metadata.downloadSizeBytes).coerceIn(0f, 1f)
                    }
                    output.flush()
                }
            }

            _modelStatus.value = OfflineModelStatus.VERIFYING
            _statusMessage.value = "Verifying downloaded SHA-256..."
            val calcHash = digest.digest().joinToString("") { "%02x".format(it) }

            if (totalRead != metadata.downloadSizeBytes || !calcHash.equals(metadata.expectedSha256, ignoreCase = true)) {
                tempFile.delete()
                _modelStatus.value = OfflineModelStatus.FAILED
                _statusMessage.value = "Download failed: Checksum or byte size mismatch."
                return@withContext Result.failure(Exception("Checksum verification failed"))
            }

            if (targetFile.exists()) targetFile.delete()
            if (tempFile.renameTo(targetFile)) {
                markModelVerified(context, targetFile, metadata.expectedSha256)
                _modelStatus.value = OfflineModelStatus.INITIALIZING
                _statusMessage.value = "Initializing LiteRT-LM runtime..."
                if (OfflineInferenceEngine.getInstance(context).initializeEngine()) {
                    _modelStatus.value = OfflineModelStatus.READY
                    _statusMessage.value = "${metadata.name} ready on ${OfflineInferenceEngine.getInstance(context).getActiveRuntime()}."
                    Result.success(targetFile)
                } else {
                    targetFile.delete()
                    clearModelVerificationRecord(context)
                    _modelStatus.value = OfflineModelStatus.FAILED
                    _statusMessage.value = "Initialization failed on device."
                    Result.failure(Exception("Engine init failed"))
                }
            } else {
                _modelStatus.value = OfflineModelStatus.FAILED
                _statusMessage.value = "Failed to commit downloaded file."
                Result.failure(Exception("Rename failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}", e)
            if (tempFile.exists()) tempFile.delete()
            _modelStatus.value = OfflineModelStatus.FAILED
            _statusMessage.value = "Download failed: ${e.message}"
            Result.failure(e)
        }
    }

    /**
     * Strictly verified developer sideload/import for testing.
     */
    suspend fun importModelFromStream(
        context: Context,
        inputStream: InputStream,
        expectedSize: Long = metadata.downloadSizeBytes,
        expectedSha: String = metadata.expectedSha256
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetFile = getModelFile(context)
        val tempFile = getTempFile(context)

        try {
            _modelStatus.value = OfflineModelStatus.EXTRACTING
            _statusMessage.value = "Importing offline model artifact..."

            val digest = MessageDigest.getInstance("SHA-256")
            var totalRead = 0L

            tempFile.outputStream().use { output ->
                val buffer = ByteArray(128 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    totalRead += read
                }
                output.flush()
            }

            _modelStatus.value = OfflineModelStatus.VERIFYING
            _statusMessage.value = "Verifying cryptographic checksum..."

            val calcHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (totalRead != expectedSize || !calcHash.equals(expectedSha, ignoreCase = true)) {
                tempFile.delete()
                _modelStatus.value = OfflineModelStatus.FAILED
                _statusMessage.value = "Import rejected: Size or SHA-256 checksum mismatch."
                return@withContext Result.failure(Exception("Checksum verification failed"))
            }

            if (targetFile.exists()) targetFile.delete()
            if (tempFile.renameTo(targetFile)) {
                markModelVerified(context, targetFile, metadata.expectedSha256)
                _modelStatus.value = OfflineModelStatus.INITIALIZING
                _statusMessage.value = "Initializing LiteRT-LM runtime..."

                if (OfflineInferenceEngine.getInstance(context).initializeEngine()) {
                    _modelStatus.value = OfflineModelStatus.READY
                    _statusMessage.value = "Import successful. Offline SLM ready."
                    Result.success(targetFile)
                } else {
                    targetFile.delete()
                    clearModelVerificationRecord(context)
                    _modelStatus.value = OfflineModelStatus.FAILED
                    _statusMessage.value = "Imported model could not initialize in LiteRT-LM."
                    Result.failure(Exception("LiteRT-LM initialization failed"))
                }
            } else {
                _modelStatus.value = OfflineModelStatus.FAILED
                _statusMessage.value = "Failed to commit imported model to app storage."
                Result.failure(Exception("File rename error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Import error: ${e.message}", e)
            if (tempFile.exists()) tempFile.delete()
            _modelStatus.value = OfflineModelStatus.FAILED
            _statusMessage.value = "Import failed: ${e.message}"
            Result.failure(e)
        }
    }

    /**
     * Deletes the extracted model to free storage.
     */
    fun deleteModel(context: Context): Boolean {
        OfflineInferenceEngine.getInstance(context).closeEngine()
        clearModelVerificationRecord(context)
        val file = getModelFile(context)
        val temp = getTempFile(context)
        if (temp.exists()) temp.delete()
        val deleted = if (file.exists()) file.delete() else true
        _modelStatus.value = OfflineModelStatus.NOT_INSTALLED
        _extractionProgress.value = 0f
        _statusMessage.value = "Extracted model deleted. Storage freed."
        Log.i(TAG, "Model uninstalled from app private storage.")
        return deleted
    }

    fun setStatusForTesting(status: OfflineModelStatus, message: String) {
        _modelStatus.value = status
        _statusMessage.value = message
    }
}
