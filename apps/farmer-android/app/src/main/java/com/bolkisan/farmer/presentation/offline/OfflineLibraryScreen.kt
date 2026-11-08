package com.bolkisan.farmer.presentation.offline

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolkisan.farmer.data.local.entities.OfflineKnowledgeEntity
import com.bolkisan.farmer.data.repository.BolKisanRepository
import com.bolkisan.farmer.domain.EdgeRuntime
import com.bolkisan.farmer.domain.OfflineInferenceEngine
import com.bolkisan.farmer.domain.OfflineModelCandidate
import com.bolkisan.farmer.presentation.common.NetworkStatusBar
import com.bolkisan.farmer.presentation.common.appStrings
import com.bolkisan.farmer.presentation.common.cropFilterOptions
import com.bolkisan.farmer.presentation.common.localizedCropName
import com.bolkisan.farmer.theme.*
import kotlinx.coroutines.launch

@Composable
fun OfflineLibraryScreen(
    repository: BolKisanRepository,
    inferenceEngine: OfflineInferenceEngine,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val currentLang by repository.languageFlow.collectAsState()
    val strings = appStrings(currentLang)
    var benchmarkResult by remember { mutableStateOf<String?>(null) }
    var isBenchmarking by remember { mutableStateOf(false) }

    val isOnline by repository.isOnline.collectAsState()
    val knowledgeList by repository.getAllOfflineKnowledge().collectAsState(initial = emptyList())

    var selectedCropFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    val (totalRamGb, freeRamMb) = remember { inferenceEngine.getDeviceRamInfo() }
    val preinstalledModel = remember { inferenceEngine.getPreinstalledModelInfo() }

    val cropFilters = remember(currentLang) { cropFilterOptions(currentLang) }

    val filteredList = knowledgeList.filter { item ->
        val matchesCrop = selectedCropFilter == "All" || item.crop.contains(selectedCropFilter, ignoreCase = true)
        val matchesQuery = searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.content.contains(searchQuery, ignoreCase = true) ||
                item.crop.contains(searchQuery, ignoreCase = true)
        val matchesLang = item.language == currentLang || item.language == "en"
        matchesCrop && matchesQuery && matchesLang
    }

    fun runBenchmark() {
        isBenchmarking = true
        coroutineScope.launch {
            val res = inferenceEngine.executeOfflineInference(
                query = "Cotton pink bollworm chemical dose",
                preferredCandidate = OfflineModelCandidate.AGRIPARAM_Q4,
                runtime = EdgeRuntime.LITERT_LM
            )
            isBenchmarking = false
            benchmarkResult = """
                • ${strings.benchmarkDeviceRam}: ${res.hardwareRamGb}GB (Free: ${res.freeRamMb}MB)
                • ${strings.benchmarkModelEvaluated}: ${res.model} via ${res.runtime}
                • ${strings.benchmarkFirstToken}: ${res.timeToFirstTokenMs}ms
                • ${strings.benchmarkTotalInference}: ${res.totalInferenceMs}ms
                • ${strings.benchmarkVoiceLatency}: ${String.format("%.2f", res.voiceToVoiceLatencySeconds)}s (${strings.benchmarkTarget} < 6.0s)
                • ${strings.benchmarkFallback}: ${if (res.didFallbackToVerifiedRules) strings.benchmarkFallbackYes else strings.benchmarkFallbackNo}
                • ${strings.benchmarkGroundedness}: ${res.groundednessScore * 100}%
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
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
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

            Spacer(modifier = Modifier.height(8.dp))

            // Preinstalled Offline Model & Edge AI Assessment Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, SproutBorder, RoundedCornerShape(18.dp)),
                colors = CardDefaults.cardColors(containerColor = CardWhite)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.preinstalledModel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreenPrimary
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFDCFCE7)
                        ) {
                            Text(
                                text = strings.preinstalledBadge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.modelLabel(preinstalledModel.name, preinstalledModel.parameters),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkEarthFg
                    )
                    Text(
                        text = strings.architectureLabel(preinstalledModel.architecture, preinstalledModel.quantization),
                        fontSize = 11.sp,
                        color = MutedSlate
                    )
                    Text(
                        text = strings.hardwareStatus(totalRamGb, freeRamMb),
                        fontSize = 11.sp,
                        color = if (totalRamGb <= 3) HazardRed else ForestGreenPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { runBenchmark() },
                        enabled = !isBenchmarking,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
                    ) {
                        Text(
                            text = if (isBenchmarking) strings.benchmarkRunning else strings.benchmarkButton,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    benchmarkResult?.let {
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

            Spacer(modifier = Modifier.height(12.dp))

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
