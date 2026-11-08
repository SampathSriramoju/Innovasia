package com.bolkisan.farmer.presentation.voice

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolkisan.farmer.data.remote.models.VoiceAnswerDto
import com.bolkisan.farmer.data.repository.BolKisanRepository
import com.bolkisan.farmer.presentation.common.NetworkStatusBar
import com.bolkisan.farmer.presentation.common.appStrings
import com.bolkisan.farmer.presentation.common.localizedCropName
import com.bolkisan.farmer.presentation.common.voiceQuickPrompts
import com.bolkisan.farmer.theme.*
import kotlinx.coroutines.launch

@Composable
fun VoiceAssistantScreen(
    repository: BolKisanRepository,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val isOnline by repository.isOnline.collectAsState()
    val currentLang by repository.languageFlow.collectAsState()
    val strings = appStrings(currentLang)
    val quickPrompts = remember(currentLang) { voiceQuickPrompts(currentLang) }
    val cropDisplay = localizedCropName(repository.selectedCrop, currentLang)
    var isRecording by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf("") }
    var answerState by remember { mutableStateOf<VoiceAnswerDto?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Pulsing animation for microphone
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    fun submitQuery(query: String) {
        recognizedText = query
        isLoading = true
        coroutineScope.launch {
            val res = repository.askQuestion(query)
            isLoading = false
            if (res.isSuccess) {
                answerState = res.getOrNull()
            } else {
                answerState = VoiceAnswerDto(
                    status = "error",
                    isSafe = false,
                    responseText = "${strings.voiceServerError}\n[Error: ${res.exceptionOrNull()?.message}]",
                    citations = listOf("Local Network Fallback")
                )
            }
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Nav
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
                        text = strings.voiceTitle,
                        style = Typography.titleLarge,
                        color = DarkEarthFg
                    )
                    Text(
                        text = strings.voiceSubtitle(cropDisplay),
                        fontSize = 12.sp,
                        color = MutedSlate
                    )
                }
            }

            // Real-Time Network State Banner
            NetworkStatusBar(
                isOnline = isOnline,
                language = currentLang,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Sample Quick Voice Chips (language follows the selected farmer language)
            Text(
                text = strings.quickQuestions,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkEarthFg,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                quickPrompts.forEach { prompt ->
                    QuickPromptChip(
                        text = prompt.text,
                        badge = prompt.badge,
                        isWarning = prompt.isWarning,
                        onClick = { submitQuery(prompt.text) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Central Push-To-Talk Button (80dp diameter with haptic pulse)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .scale(pulseScale)
                            .background(LeafGreenSecondary.copy(alpha = 0.35f), CircleShape)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = if (isRecording) HazardRed else ForestGreenPrimary,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .clickable {
                            isRecording = !isRecording
                            if (!isRecording) {
                                submitQuery(quickPrompts.firstOrNull()?.text ?: strings.voiceTitle)
                            }
                        },
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Push to Talk",
                            tint = CardWhite,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Text(
                text = if (isRecording) strings.voiceListening else strings.voiceTapToSpeak,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isRecording) HazardRed else ForestGreenPrimary,
                modifier = Modifier.padding(top = 12.dp)
            )

            if (isLoading) {
                Spacer(modifier = Modifier.height(20.dp))
                CircularProgressIndicator(color = ForestGreenPrimary)
                Text(
                    text = strings.checkingSafety,
                    fontSize = 12.sp,
                    color = MutedSlate,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Results Card
            answerState?.let { ans ->
                Spacer(modifier = Modifier.height(24.dp))

                if (!ans.isSafe || ans.status == "escalated") {
                    // Safety Escalation Card (Red)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, HazardRed, RoundedCornerShape(20.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = "Safety Alert", tint = HazardRed)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = strings.safetyAlertEscalated,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = HazardRed
                                )
                            }

                            ans.ticketNumber?.let {
                                Text(
                                    text = strings.ticketNumber(it),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkEarthFg,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            Text(
                                text = ans.responseText,
                                fontSize = 14.sp,
                                color = DarkEarthFg,
                                modifier = Modifier.padding(top = 8.dp),
                                lineHeight = 20.sp
                            )

                            ans.reason?.let {
                                Text(
                                    text = strings.reason(it),
                                    fontSize = 12.sp,
                                    color = HazardRed,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Verified Answer Card (Green)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, SproutBorder, RoundedCornerShape(20.dp)),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Verified", tint = ForestGreenPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = strings.verifiedInfo,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = ForestGreenPrimary
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = MintCreamBg,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.VolumeUp, contentDescription = "Play Audio", tint = ForestGreenPrimary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }

                            val cleanedDisplay = ans.responseText
                                .replace(Regex("\\[.*?\\]"), "")
                                .replace(Regex("(?m)^\\s*(?:ఆధారం|మూలం|स्रोत|Source|Citation)\\s*:.*$"), "")
                                .trim()

                            Text(
                                text = cleanedDisplay,
                                fontSize = 14.sp,
                                color = DarkEarthFg,
                                modifier = Modifier.padding(top = 10.dp),
                                lineHeight = 21.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickPromptChip(
    text: String,
    badge: String,
    isWarning: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isWarning) Color(0xFFFEF2F2) else CardWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isWarning) Color(0xFFFCA5A5) else SproutBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isWarning) HazardRed else DarkEarthFg,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isWarning) HazardRed else ForestGreenPrimary
            ) {
                Text(
                    text = badge,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CardWhite,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
