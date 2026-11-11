package com.bolkisan.farmer.presentation.voice

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bolkisan.farmer.data.remote.models.VoiceAnswerDto
import com.bolkisan.farmer.data.repository.BolKisanRepository
import com.bolkisan.farmer.domain.voice.AssistantState
import com.bolkisan.farmer.domain.voice.VoiceAssistantManager
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isOnline by repository.isOnline.collectAsState()
    val currentLang by repository.languageFlow.collectAsState()
    val strings = remember(currentLang) { appStrings(currentLang) }
    val quickPrompts = remember(currentLang) { voiceQuickPrompts(currentLang) }
    val cropDisplay = localizedCropName(repository.selectedCrop, currentLang)

    var recognizedText by remember { mutableStateOf("") }
    var answerState by remember { mutableStateOf<VoiceAnswerDto?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Initialize VoiceAssistantManager with speech recognition callback
    var submitQueryAction by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val voiceManager = remember {
        VoiceAssistantManager(context) { query ->
            if (query.isNotBlank()) {
                submitQueryAction?.invoke(query)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceManager.release()
        }
    }

    val assistantState by voiceManager.assistantState.collectAsState()
    val liveTranscription by voiceManager.liveTranscription.collectAsState()
    val rmsLevel by voiceManager.rmsLevel.collectAsState()
    val isSpeaking by voiceManager.isSpeaking.collectAsState()
    val isMuted by voiceManager.isMuted.collectAsState()
    val voiceError by voiceManager.errorMessage.collectAsState()

    // Pulsing animation for microphone
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (assistantState == AssistantState.LISTENING) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    fun submitQuery(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return

        recognizedText = cleanQuery
        voiceManager.stopSpeaking()
        voiceManager.setProcessingState()
        isLoading = true

        coroutineScope.launch {
            val asrLatency = voiceManager.getLastAsrLatencyMs()
            val res = repository.askQuestion(cleanQuery, asrLatencyMs = asrLatency)
            isLoading = false
            if (res.isSuccess) {
                val ans = res.getOrNull()
                answerState = ans
                voiceManager.setReadyState()

                if (ans != null) {
                    voiceManager.recordPipelineLatencies(
                        retrievalMs = ans.retrievalLatencyMs ?: 0L,
                        ttftMs = ans.ttftMs ?: 0L,
                        inferenceMs = ans.inferenceLatencyMs ?: 0L,
                        tokensPerSec = ans.tokensPerSec ?: 0.0,
                        safetyMs = ans.safetyGateLatencyMs ?: 0L,
                        isColdStart = ans.isColdStart ?: false
                    )
                }

                // CRITICAL: Call voiceManager.speak ONLY on ans.responseText (the AI answer), NEVER on the farmer's question!
                val spokenText = ans?.responseText
                if (!spokenText.isNullOrBlank() && !isMuted) {
                    voiceManager.speak(spokenText, currentLang)
                }
            } else {
                voiceManager.setReadyState()
                val errorReason = res.exceptionOrNull()?.message ?: "Unknown error"
                answerState = VoiceAnswerDto(
                    status = "error",
                    isSafe = false,
                    responseText = "${strings.voiceServerError}\n[Error: $errorReason]",
                    citations = listOf("Local Network Fallback")
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        submitQueryAction = { query -> submitQuery(query) }
    }

    // Microphone Permission Launcher
    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceManager.startListening(currentLang, isOnline)
        } else {
            Toast.makeText(context, "Microphone permission is required for voice assistant", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleMicTapped() {
        if (assistantState == AssistantState.LISTENING) {
            voiceManager.stopListening()
        } else if (assistantState == AssistantState.SPEAKING) {
            voiceManager.stopSpeaking()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                voiceManager.startListening(currentLang, isOnline)
            } else {
                recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
                IconButton(onClick = {
                    voiceManager.stopSpeaking()
                    voiceManager.stopListening()
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = DarkEarthFg)
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

            // Real-Time Network State Banner (differentiating Online, Offline SLM, Offline Knowledge)
            NetworkStatusBar(
                isOnline = isOnline,
                language = currentLang,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            // Voice error banner (handles missing offline speech pack gracefully)
            if (voiceError != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFFBEB),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = voiceError ?: "",
                            fontSize = 12.sp,
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.Medium,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

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

            Spacer(modifier = Modifier.height(24.dp))

            // Central Push-To-Talk Button (80dp diameter with haptic pulse)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                if (assistantState == AssistantState.LISTENING) {
                    val dynamicScale = (pulseScale + (rmsLevel * 0.25f)).coerceIn(1f, 1.45f)
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .scale(dynamicScale)
                            .background(LeafGreenSecondary.copy(alpha = 0.35f), CircleShape)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = if (assistantState == AssistantState.LISTENING) HazardRed else ForestGreenPrimary,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .clickable { handleMicTapped() },
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (assistantState == AssistantState.LISTENING) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Push to Talk",
                            tint = CardWhite,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Text(
                text = when {
                    assistantState == AssistantState.LISTENING -> strings.voiceListening
                    isSpeaking -> strings.heroSpeaking
                    isLoading -> strings.thinkingIndicator
                    else -> strings.voiceTapToSpeak
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (assistantState == AssistantState.LISTENING) HazardRed else ForestGreenPrimary,
                modifier = Modifier.padding(top = 10.dp)
            )

            // Live speech transcription during listening
            if (assistantState == AssistantState.LISTENING && liveTranscription.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F8F4),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7EBD1)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = ForestGreenPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = liveTranscription,
                            fontSize = 13.sp,
                            color = DarkEarthFg,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

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
                Spacer(modifier = Modifier.height(20.dp))

                // Farmer Recognized Question preview
                if (recognizedText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ForestGreenPrimary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when (currentLang) {
                                        "te" -> "రైతు ప్రశ్న"
                                        "hi" -> "किसान का प्रश्न"
                                        else -> "Farmer Question"
                                    },
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = recognizedText,
                                fontSize = 13.5.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

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
                    val status = ans.status ?: "online"
                    val (badgeBg, badgeText, badgeFg) = when {
                        status == "answered_offline_slm" -> Triple(Color(0xFFF5F3FF), "⚡ OFFLINE SLM (Qwen3-0.6B On-Device AI)", Color(0xFF5B21B6))
                        status == "answered_offline_fallback" -> Triple(Color(0xFFFFFBEB), "📦 OFFLINE KNOWLEDGE (Room SQLite ICAR)", Color(0xFF92400E))
                        status == "offline_queued" -> Triple(Color(0xFFFFFBEB), "⏳ OFFLINE QUEUED (Auto-Sync)", Color(0xFF92400E))
                        isOnline -> Triple(Color(0xFFDCFCE7), "🟢 ONLINE (FastAPI Cloud/LAN)", Color(0xFF14532D))
                        else -> Triple(Color(0xFFFFFBEB), "📦 OFFLINE KNOWLEDGE (Room SQLite ICAR)", Color(0xFF92400E))
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, SproutBorder, RoundedCornerShape(20.dp)),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            // Header with Status Badge and Audio Wave
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Verified", tint = ForestGreenPrimary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = badgeBg
                                    ) {
                                        Text(
                                            text = badgeText,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeFg,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (isSpeaking) {
                                    VoiceAnimatedAudioWave(isSpeaking = true, barColor = ForestGreenPrimary)
                                }
                            }

                            val cleanedDisplay = ans.responseText.trim()

                            Text(
                                text = cleanedDisplay,
                                fontSize = 14.sp,
                                color = DarkEarthFg,
                                modifier = Modifier.padding(top = 10.dp),
                                lineHeight = 21.sp
                            )

                            if (!ans.citations.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = ans.citations.joinToString(" • "),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MutedSlate
                                )
                            }

                            if (ans.totalVoiceToVoiceLatencyMs != null || ans.inferenceLatencyMs != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFF3F4F6)
                                ) {
                                    val v2vText = ans.totalVoiceToVoiceLatencyMs?.let { "${it}ms" } ?: "${ans.inferenceLatencyMs ?: 0}ms"
                                    val slaPass = (ans.totalVoiceToVoiceLatencyMs ?: ans.inferenceLatencyMs ?: 0L) <= 6000L
                                    Text(
                                        text = "⚡ Voice-to-Voice: $v2vText • SLA (<6s): ${if (slaPass) "PASSED" else "EXCEEDED"}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (slaPass) ForestGreenPrimary else HazardRed,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFFE5E7EB), thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            // Working Audio Controls: Play/Pause, Replay, Stop, Mute
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Play / Pause Button
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSpeaking) Color(0xFFDCFCE7) else MintCreamBg,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                if (isSpeaking) {
                                                    voiceManager.stopSpeaking()
                                                } else {
                                                    voiceManager.speak(ans.responseText, currentLang)
                                                }
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                if (isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isSpeaking) "Pause" else "Play",
                                                tint = ForestGreenPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    // Replay Button
                                    Surface(
                                        shape = CircleShape,
                                        color = MintCreamBg,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                voiceManager.speak(ans.responseText, currentLang)
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Replay,
                                                contentDescription = "Replay",
                                                tint = ForestGreenPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    // Stop Button
                                    if (isSpeaking) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFFFEE2E2),
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .clickable { voiceManager.stopSpeaking() }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Stop,
                                                    contentDescription = "Stop",
                                                    tint = HazardRed,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }
                                }

                                // Mute / Unmute Button
                                IconButton(
                                    onClick = { voiceManager.toggleMute() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = if (isMuted) "Unmute" else "Mute",
                                        tint = if (isMuted) HazardRed else ForestGreenPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceAnimatedAudioWave(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = ForestGreenPrimary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audioWaveVoice")
    val heights = (0..4).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = if (isSpeaking) 1.0f else 0.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(300 + index * 80, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$index"
        )
    }

    Row(
        modifier = modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEach { anim ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(anim.value)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(barColor)
            )
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
