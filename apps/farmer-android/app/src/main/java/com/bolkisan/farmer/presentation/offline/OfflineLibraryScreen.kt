package com.bolkisan.farmer.presentation.offline

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolkisan.farmer.data.local.entities.OfflineKnowledgeEntity
import com.bolkisan.farmer.data.repository.BolKisanRepository
import com.bolkisan.farmer.domain.EdgeRuntime
import com.bolkisan.farmer.domain.OfflineInferenceEngine
import com.bolkisan.farmer.domain.OfflineModelManager
import com.bolkisan.farmer.domain.OfflineModelStatus
import com.bolkisan.farmer.presentation.common.NetworkStatusBar
import com.bolkisan.farmer.presentation.common.appStrings
import com.bolkisan.farmer.presentation.common.canonicalCropName
import com.bolkisan.farmer.presentation.common.cropFilterOptions
import com.bolkisan.farmer.presentation.common.localizedCropName
import com.bolkisan.farmer.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OfflineLibraryScreen(
    repository: BolKisanRepository,
    inferenceEngine: OfflineInferenceEngine,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentLang by repository.languageFlow.collectAsState()
    val strings = appStrings(currentLang)

    val isOnline by repository.isOnline.collectAsState()
    val knowledgeList by repository.getAllOfflineKnowledge().collectAsState(initial = emptyList())

    val modelStatus by OfflineModelManager.modelStatus.collectAsState()
    val downloadProgress by OfflineModelManager.downloadProgress.collectAsState()
    val statusMessage by OfflineModelManager.statusMessage.collectAsState()
    val modelMetadata = remember { OfflineModelManager.metadata }

    val capabilities = remember(modelStatus, knowledgeList.size) {
        OfflineModelManager.getOfflineCapabilities(context, hasRoomRecords = knowledgeList.isNotEmpty())
    }

    val (totalRamGb, freeRamMb) = remember { inferenceEngine.getDeviceRamInfo() }
    val isEngineInitialized = remember(modelStatus) { inferenceEngine.isEngineReady() }

    var isActionInProgress by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var selectedCropFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    // File picker launcher for genuine .litertlm offline bundle import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isActionInProgress = true
                val result = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            OfflineModelManager.importModelFromStream(
                                context = context,
                                inputStream = stream,
                                expectedSize = modelMetadata.downloadSizeBytes,
                                expectedSha = modelMetadata.expectedSha256
                            )
                        } ?: Result.failure(Exception("Unable to read selected file"))
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
                isActionInProgress = false
                if (result.isSuccess) {
                    Toast.makeText(context, "LiteRT-LM model imported successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Import failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val cropFilters = remember(currentLang) { cropFilterOptions(currentLang) }
    val filteredList = knowledgeList.filter { item ->
        val matchesCrop = selectedCropFilter == "All" ||
                item.crop.contains(selectedCropFilter, ignoreCase = true) ||
                canonicalCropName(item.crop).equals(canonicalCropName(selectedCropFilter), ignoreCase = true)
        val matchesQuery = searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.content.contains(searchQuery, ignoreCase = true) ||
                item.crop.contains(searchQuery, ignoreCase = true)
        val hasCurrentLang = knowledgeList.any { it.language == currentLang }
        val matchesLang = if (hasCurrentLang) item.language == currentLang else (item.language == "en")
        matchesCrop && matchesQuery && matchesLang
    }

    fun runTestInference() {
        isActionInProgress = true
        coroutineScope.launch {
            val sampleCrop = if (selectedCropFilter != "All") selectedCropFilter else "Cotton"
            val res = inferenceEngine.executeOfflineInference(
                query = "$sampleCrop pest and safe fertilizer dose",
                cropContext = sampleCrop,
                language = currentLang,
                preferredRuntime = EdgeRuntime.LITERT_LM_CPU
            )
            isActionInProgress = false
            testResult = """
                • Engine: ${res.engineUsed}
                • Status: ${res.statusLabel}
                • Hardware: ${res.hardwareRamGb}GB RAM (Free: ${res.freeRamMb}MB)
                • First Token: ${res.timeToFirstTokenMs}ms | Total Gen: ${res.totalInferenceMs}ms
                • Speed: ${String.format("%.1f", res.tokensPerSecond)} tokens/sec (${res.tokenCount} tokens)
                • Fallback: ${if (res.didFallbackToVerifiedRules) "Room SQLite Verified" else "Real Edge SLM"}
                • Groundedness: ${(res.groundednessScore * 100).toInt()}%
            """.trimIndent()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MintCreamBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Screen Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = DarkEarthFg)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = strings.offlineLibraryTitle,
                        style = Typography.titleLarge,
                        color = DarkEarthFg
                    )
                    Text(
                        text = strings.offlineLibrarySubtitle,
                        fontSize = 12.sp,
                        color = MutedSlate
                    )
                }
            }

            // Real-Time Network State Banner
            NetworkStatusBar(
                isOnline = isOnline,
                language = currentLang,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subsystem Health & SLM Dashboard Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, SproutBorder, RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = CardWhite)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Header Row with Status Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = ForestGreenPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strings.offlineSubsystemsTitle,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenPrimary
                            )
                        }

                        // Status Badge
                        val badgeBg = when (modelStatus) {
                            OfflineModelStatus.READY -> Color(0xFFDCFCE7)
                            OfflineModelStatus.DOWNLOADING, OfflineModelStatus.VERIFYING, OfflineModelStatus.INITIALIZING -> Color(0xFFFEF3C7)
                            OfflineModelStatus.UNSUPPORTED_DEVICE, OfflineModelStatus.INSUFFICIENT_MEMORY -> Color(0xFFFEE2E2)
                            else -> Color(0xFFF3F4F6)
                        }
                        val badgeFg = when (modelStatus) {
                            OfflineModelStatus.READY -> Color(0xFF166534)
                            OfflineModelStatus.DOWNLOADING, OfflineModelStatus.VERIFYING, OfflineModelStatus.INITIALIZING -> Color(0xFFB45309)
                            OfflineModelStatus.UNSUPPORTED_DEVICE, OfflineModelStatus.INSUFFICIENT_MEMORY -> HazardRed
                            else -> Color(0xFF4B5563)
                        }
                        val badgeText = when (modelStatus) {
                            OfflineModelStatus.READY -> strings.readyBadge
                            OfflineModelStatus.DOWNLOADING -> strings.downloadingBadge
                            OfflineModelStatus.VERIFYING -> strings.verifyingBadge
                            OfflineModelStatus.UNSUPPORTED_DEVICE -> strings.unsupportedBadge
                            OfflineModelStatus.NOT_INSTALLED -> strings.notInstalledBadge
                            else -> modelStatus.name
                        }

                        Surface(shape = RoundedCornerShape(8.dp), color = badgeBg) {
                            Text(
                                text = badgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeFg,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Subsystem Details Grid
                    val hasBundledAsset = remember { OfflineModelManager.isBundledModelAssetAvailable(context) }
                    SubsystemRow(
                        label = "APK Bundled Model",
                        value = if (hasBundledAsset) "Embedded in Release APK (${modelMetadata.downloadSizeBytes / (1024 * 1024)} MB)" else "External Download Required",
                        isHealthy = hasBundledAsset
                    )
                    SubsystemRow(
                        label = strings.slmEngineLabel,
                        value = "${modelMetadata.name} (${modelMetadata.parameterCount}, ${modelMetadata.quantization})",
                        isHealthy = modelStatus == OfflineModelStatus.READY
                    )
                    SubsystemRow(
                        label = strings.runtimeLabel,
                        value = "${modelMetadata.runtime} • ${if (isEngineInitialized) "Active (${inferenceEngine.getActiveRuntime()})" else "Standby"}",
                        isHealthy = isEngineInitialized
                    )
                    SubsystemRow(
                        label = strings.storageLabel,
                        value = if (capabilities.installedModelPath != null) {
                            "${capabilities.installedModelSizeBytes / (1024 * 1024)} MB / Internal Storage"
                        } else {
                            "0 MB (Requires ~${modelMetadata.downloadSizeBytes / (1024 * 1024)} MB)"
                        },
                        isHealthy = capabilities.installedModelPath != null
                    )
                    SubsystemRow(
                        label = strings.roomKnowledgeLabel,
                        value = "${knowledgeList.size} ICAR Verified Guides (Room SQLite FTS5)",
                        isHealthy = knowledgeList.isNotEmpty()
                    )
                    SubsystemRow(
                        label = strings.speechRecognitionLabel,
                        value = if (capabilities.isOfflineAsrAvailable) "On-Device SpeechRecognizer Active" else "System Speech Available",
                        isHealthy = capabilities.isOfflineAsrAvailable
                    )
                    SubsystemRow(
                        label = strings.ttsLabel,
                        value = if (capabilities.isOfflineTtsAvailable) "System Offline TTS Engine Active" else "Unavailable",
                        isHealthy = capabilities.isOfflineTtsAvailable
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.hardwareStatus(totalRamGb, freeRamMb),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (totalRamGb < 4) HazardRed else ForestGreenPrimary
                    )

                    // Download / Verification Progress Bar
                    if (modelStatus == OfflineModelStatus.DOWNLOADING || modelStatus == OfflineModelStatus.VERIFYING || modelStatus == OfflineModelStatus.INITIALIZING) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = statusMessage,
                                    fontSize = 11.sp,
                                    color = MutedSlate
                                )
                                Text(
                                    text = "${(downloadProgress * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreenPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = ForestGreenPrimary,
                                trackColor = SproutBorder
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action Buttons Row depending on status
                    when (modelStatus) {
                        OfflineModelStatus.READY -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { runTestInference() },
                                    enabled = !isActionInProgress,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
                                ) {
                                    Text(
                                        text = if (isActionInProgress) strings.benchmarkRunning else strings.testOfflineAiButton,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        OfflineModelManager.deleteModel(context)
                                        Toast.makeText(context, "Model deleted. Storage freed.", Toast.LENGTH_SHORT).show()
                                    },
                                    enabled = !isActionInProgress,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HazardRed)
                                ) {
                                    Text(text = strings.deleteModelButton, fontSize = 11.sp)
                                }
                            }
                        }
                        OfflineModelStatus.NOT_INSTALLED, OfflineModelStatus.FAILED -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (hasBundledAsset) {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                isActionInProgress = true
                                                val res = OfflineModelManager.provisionBundledModelOnFirstLaunch(context, force = true)
                                                isActionInProgress = false
                                                if (res) {
                                                    Toast.makeText(context, "Built-in model provisioned successfully!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Provisioning failed. Check storage.", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        enabled = !isActionInProgress && totalRamGb >= 4,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
                                    ) {
                                        Text(
                                            text = if (isActionInProgress) "Provisioning Built-in Model..." else "Extract & Provision Built-in Model",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    isActionInProgress = true
                                                    val res = OfflineModelManager.downloadModel(context)
                                                    isActionInProgress = false
                                                    if (res.isFailure) {
                                                        Toast.makeText(context, "Download failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            },
                                            enabled = !isActionInProgress && totalRamGb >= 4,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
                                        ) {
                                            Text(text = strings.downloadModelButton, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { importLauncher.launch("*/*") },
                                            enabled = !isActionInProgress,
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(text = strings.importModelButton, fontSize = 11.sp)
                                        }
                                    }
                                }
                                if (totalRamGb < 4) {
                                    Text(
                                        text = "Note: LiteRT-LM requires ≥ 4GB RAM. Room SQLite knowledge engine is active.",
                                        fontSize = 10.sp,
                                        color = MutedSlate
                                    )
                                }
                            }
                        }
                        else -> {
                            // Verification / Initialization / Downloading in progress
                            Text(
                                text = statusMessage,
                                fontSize = 11.sp,
                                color = MutedSlate
                            )
                        }
                    }

                    // Test Inference Metrics Box
                    testResult?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MintCreamBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = it,
                                fontSize = 11.sp,
                                color = DarkEarthFg,
                                modifier = Modifier.padding(10.dp),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Crop Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cropFilters.forEach { (cropKey, cropLabel) ->
                    val isSelected = selectedCropFilter == cropKey
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) ForestGreenPrimary else CardWhite,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) ForestGreenPrimary else SproutBorder
                        ),
                        modifier = Modifier.clickable { selectedCropFilter = cropKey }
                    ) {
                        Text(
                            text = cropLabel,
                            color = if (isSelected) Color.White else DarkEarthFg,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stored ICAR Guides Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.storedPackages(filteredList.size),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkEarthFg
                )
                Text(
                    text = strings.offlineAccess,
                    fontSize = 11.sp,
                    color = LeafGreenSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Dynamic Knowledge Cards List from Room
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (filteredList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings.loadingCropInfo,
                                color = MutedSlate,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    items(filteredList) { item ->
                        OfflinePackageCard(
                            crop = localizedCropName(item.crop, currentLang),
                            topic = item.title,
                            content = item.content,
                            citation = item.sourceCitation,
                            language = currentLang
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubsystemRow(
    label: String,
    value: String,
    isHealthy: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MutedSlate
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isHealthy) ForestGreenPrimary else DarkEarthFg
        )
    }
}

@Composable
fun OfflinePackageCard(crop: String, topic: String, content: String, citation: String, language: String) {
    val strings = appStrings(language)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SproutBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CardWhite)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = crop, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MintCreamBg
                ) {
                    Text(
                        text = strings.icarVerifiedBadge,
                        fontSize = 10.sp,
                        color = LeafGreenSecondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(text = topic, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkEarthFg, modifier = Modifier.padding(top = 4.dp))
            Text(text = content, fontSize = 12.sp, color = MutedSlate, modifier = Modifier.padding(top = 4.dp), lineHeight = 17.sp)
            Text(text = strings.citation(citation), fontSize = 10.sp, color = HarvestGoldAccent, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
