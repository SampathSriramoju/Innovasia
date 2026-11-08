package com.bolkisan.farmer.domain

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class PreinstalledModelInfo(
    val modelId: String,
    val name: String,
    val version: String,
    val architecture: String,
    val runtime: String,
    val parameters: String,
    val quantization: String,
    val fileSizeBytes: Long,
    val isPreinstalled: Boolean,
    val isReady: Boolean,
    val localFilePath: String
)

object OfflineModelManager {

    private const val TAG = "OfflineModelManager"
    private const val ASSET_DIR = "models"
    private const val MANIFEST_FILE = "model_manifest.json"
    private const val MODEL_BIN_FILE = "agriparam_q4_edge.bin"

    fun getPreinstalledModelInfo(context: Context): PreinstalledModelInfo {
        var modelId = "agriparam-q4-edge"
        var name = "AgriParam-Q4 Edge SLM"
        var version = "1.0.4"
        var architecture = "Transformer-Decoder-Quantized-INT4"
        var runtime = "LiteRT-LM / GGUF Edge Runtime"
        var parameters = "2.1 Billion"
        var quantization = "INT4_AWQ"

        try {
            val manifestStream = context.assets.open("$ASSET_DIR/$MANIFEST_FILE")
            val manifestContent = manifestStream.bufferedReader().use { it.readText() }
            val json = JSONObject(manifestContent)

            modelId = json.optString("model_id", modelId)
            name = json.optString("name", name)
            version = json.optString("version", version)
            architecture = json.optString("architecture", architecture)
            runtime = json.optString("runtime", runtime)
            parameters = json.optString("parameters", parameters)
            quantization = json.optString("quantization", quantization)
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse model manifest: ${e.message}")
        }

        val targetDir = File(context.filesDir, "models")
        val targetFile = File(targetDir, MODEL_BIN_FILE)

        var assetSize = 0L
        try {
            val afd = context.assets.openFd("$ASSET_DIR/$MODEL_BIN_FILE")
            assetSize = afd.length
            afd.close()
        } catch (e: Exception) {
            try {
                val stream = context.assets.open("$ASSET_DIR/$MODEL_BIN_FILE")
                assetSize = stream.available().toLong()
                stream.close()
            } catch (ignored: Exception) {}
        }

        val isReady = targetFile.exists() && targetFile.length() > 0 || assetSize > 0

        return PreinstalledModelInfo(
            modelId = modelId,
            name = name,
            version = version,
            architecture = architecture,
            runtime = runtime,
            parameters = parameters,
            quantization = quantization,
            fileSizeBytes = if (targetFile.exists()) targetFile.length() else assetSize,
            isPreinstalled = true,
            isReady = isReady,
            localFilePath = if (targetFile.exists()) targetFile.absolutePath else "assets://$ASSET_DIR/$MODEL_BIN_FILE"
        )
    }

    /**
     * Extracts model from APK assets to internal storage if not already present.
     */
    fun ensureModelExtracted(context: Context): File {
        val targetDir = File(context.filesDir, "models").apply { mkdirs() }
        val targetFile = File(targetDir, MODEL_BIN_FILE)

        if (!targetFile.exists() || targetFile.length() == 0L) {
            try {
                Log.i(TAG, "Extracting pre-installed offline model asset to ${targetFile.absolutePath}...")
                context.assets.open("$ASSET_DIR/$MODEL_BIN_FILE").use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Model extraction completed successfully: ${targetFile.length()} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract model: ${e.message}", e)
            }
        }
        return targetFile
    }

    fun verifyModelIntegrity(context: Context): Boolean {
        return try {
            val stream = context.assets.open("$ASSET_DIR/$MODEL_BIN_FILE")
            val magic = ByteArray(11)
            val read = stream.read(magic)
            stream.close()
            read == 11 && String(magic) == "AGRI_SLM_V1"
        } catch (e: Exception) {
            Log.e(TAG, "Model integrity check failed: ${e.message}")
            false
        }
    }
}
