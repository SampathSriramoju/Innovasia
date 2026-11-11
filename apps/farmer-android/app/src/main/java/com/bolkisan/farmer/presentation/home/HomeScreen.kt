package com.bolkisan.farmer.presentation.home

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bolkisan.farmer.R
import com.bolkisan.farmer.data.remote.ApiClient
import com.bolkisan.farmer.data.remote.BackendConnectionResult
import com.bolkisan.farmer.data.remote.ConnectionStatus
import com.bolkisan.farmer.data.remote.models.VoiceAnswerDto
import com.bolkisan.farmer.data.repository.BolKisanRepository
import com.bolkisan.farmer.data.repository.VoiceHistoryItem
import com.bolkisan.farmer.domain.voice.AssistantState
import com.bolkisan.farmer.domain.voice.VoiceAssistantManager
import com.bolkisan.farmer.presentation.common.appStrings
import com.bolkisan.farmer.presentation.common.localizedCropName
import com.bolkisan.farmer.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: BolKisanRepository,
    onNavigateToVoice: () -> Unit = {},
    onNavigateToDiseaseCamera: () -> Unit = {},
    onNavigateToOfflineLibrary: () -> Unit = {},
    onNavigateToOfficerTickets: () -> Unit = {},
    onNavigateToMarketPrices: () -> Unit = {},
    onNavigateToOfflineIVR: () -> Unit = {},
    onNavigateToSMSFallback: () -> Unit = {},
    onSwitchLanguage: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    val isOnline by repository.isOnline.collectAsState()
    val historyList by repository.historyFlow.collectAsState()
    val currentLang by repository.languageFlow.collectAsState()
    val strings = remember(currentLang) { appStrings(currentLang) }

    var typedQuery by remember { mutableStateOf("") }
    var isKeyboardOpen by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var activeSpeakingItemId by remember { mutableStateOf<String?>(null) }

    // Attached Image State for Disease Diagnosis Workflow
    var attachedPhotoFile by remember { mutableStateOf<File?>(null) }
    var attachedPhotoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showBackendSettingsSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    // Escalated tickets tracked by item ID -> ticket number
    var escalatedTickets by remember { mutableStateOf(mapOf<String, String>()) }
    var isEscalatingId by remember { mutableStateOf<String?>(null) }

    // Voice Assistant Manager (Speech-to-Text & Text-to-Speech Engine)
    var submitMessageAction by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val voiceManager = remember {
        VoiceAssistantManager(context) { recognizedSpeech ->
            if (recognizedSpeech.isNotBlank()) {
                submitMessageAction?.invoke(recognizedSpeech)
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

    // Clean spoken text helper
    fun sanitizeForSpeech(raw: String): String {
        return raw
            .replace(Regex("\\[.*?\\]"), "")
            .replace("*", "")
            .replace("#", "")
            .replace(Regex("(?m)^\\s*(?:ఆధారం|మూలం|स्रोत|Source|Citation)\\s*:.*$"), "")
            .trim()
    }

    // Submit question directly to unified conversational backend
    fun submitMessage(queryText: String) {
        val cleanQuery = queryText.trim()
        val photoFile = attachedPhotoFile
        if (cleanQuery.isBlank() && photoFile == null) return

        voiceManager.stopSpeaking()
        voiceManager.setProcessingState()
        typedQuery = ""
        attachedPhotoFile = null
        attachedPhotoBitmap = null
        keyboardController?.hide()
        isLoading = true

        coroutineScope.launch {
            val res = if (photoFile != null) {
                repository.askQuestionWithAttachment(
                    queryText = cleanQuery,
                    photoFile = photoFile
                )
            } else {
                repository.askQuestion(
                    queryText = cleanQuery
                )
            }
            isLoading = false
            if (res.isSuccess) {
                val ans = res.getOrNull()
                val spokenText = ans?.responseText?.let { sanitizeForSpeech(it) } ?: ""
                val latestId = repository.historyFlow.value.firstOrNull()?.id
                activeSpeakingItemId = latestId

                if (spokenText.isNotBlank() && !isMuted) {
                    voiceManager.speak(spokenText, currentLang)
                } else {
                    voiceManager.setReadyState()
                }

                // Scroll to newest message
                if (historyList.isNotEmpty()) {
                    listState.animateScrollToItem(0)
                }
            } else {
                voiceManager.setReadyState()
                Toast.makeText(context, strings.errorAnswer, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        submitMessageAction = { query -> submitMessage(query) }
    }

    // Secondary Speech Recognizer Intent Launcher as fallback
    val speechFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                submitMessage(spoken)
            }
        } else {
            voiceManager.setReadyState()
        }
    }

    // Microphone Permission Launcher
    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceManager.startListening(currentLang, isOnline)
        } else {
            Toast.makeText(context, "Microphone permission is required for voice assistant", Toast.LENGTH_LONG).show()
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

    // Camera Launcher for Photo Attachment
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            try {
                val file = File(context.cacheDir, "bolkisan_photo_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                attachedPhotoFile = file
                attachedPhotoBitmap = bitmap.asImageBitmap()
            } catch (e: Exception) {
                Toast.makeText(context, "Could not capture image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Gallery Launcher for Photo Attachment
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val file = File(context.cacheDir, "bolkisan_gallery_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(it)?.use { input ->
                    FileOutputStream(file).use { out ->
                        input.copyTo(out)
                    }
                }
                attachedPhotoFile = file
                val bm = BitmapFactory.decodeFile(file.absolutePath)
                attachedPhotoBitmap = bm?.asImageBitmap()
            } catch (e: Exception) {
                Toast.makeText(context, "Could not load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Starter Questions for multi-crop advisory
    val starterQuestions = remember(currentLang) {
        when (currentLang) {
            "te" -> listOf(
                "పత్తిలో గులాబీ రంగు పురుగు నివారణ ఏమిటి?",
                "పత్తిలో ఆకులు పసుపుగా మారుతున్నాయి, ఏం చేయాలి?",
                "వరిలో అగ్గి తెగులు లక్షణాలు, మందులు?",
                "మిరపలో తామర పురుగుల ఉధృతి ఎలా తగ్గించాలి?",
                "టమోటా ఆకు ముడత నివారణ మార్గాలు?",
                "సేంద్రీయ జీవామృతం తయారీ విధానం చెప్పండి"
            )
            "hi" -> listOf(
                "कपास में गुलाबी सुंडी का नियंत्रण कैसे करें?",
                "कपास में पत्तियां पीली पड़ रही हैं, क्या करें?",
                "धान में झुलसा रोग के लक्षण व दवा?",
                "मिर्च में थ्रिप्स कीट से बचाव के उपाय?",
                "टमाटर में पत्ती मरोड़ रोग की रोकथाम?",
                "जैविक जीवामृत बनाने की आसान विधि बताएं"
            )
            else -> listOf(
                "How to control pink bollworm in cotton?",
                "Leaves turning yellow in cotton, what to do?",
                "Remedies for blast disease in paddy/rice?",
                "How to manage thrips infestation in chilli?",
                "Prevention of leaf curl virus in tomato?",
                "Step-by-step preparation of organic Jeevamrit"
            )
        }
    }

    Scaffold(
        topBar = {
            // =========================================================================
            // TOP STATUS BAR:
            // Logo + "BOL KISAN" title
            // Clickable Online/Offline status badge (with 1-tap connection test)
            // 1-Tap Language Cycle Pill (Telugu / Hindi / English)
            // Settings Icon (Opens Settings & Diagnostics Sheet)
            // =========================================================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardWhite,
                shadowElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, SproutBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // App Branding & Connectivity Badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_bol_kisan_logo),
                            contentDescription = "Bol Kisan Logo",
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "BOL KISAN",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF14532D),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))

                        // Compact Online/Offline Status Indicator (Tap opens Backend LAN settings)
                        val modelStatus by com.bolkisan.farmer.domain.OfflineModelManager.modelStatus.collectAsState()
                        val isSlmReady = remember(modelStatus) {
                            val caps = com.bolkisan.farmer.domain.OfflineModelManager.getOfflineCapabilities(context)
                            (modelStatus == com.bolkisan.farmer.domain.OfflineModelStatus.READY || caps.modelStatus == com.bolkisan.farmer.domain.OfflineModelStatus.READY) &&
                                caps.totalRamGb >= 4 && caps.freeRamMb >= 800
                        }

                        val statusBg = if (isOnline) Color(0xFFDCFCE7) else if (isSlmReady) Color(0xFFF5F3FF) else Color(0xFFFFFBEB)
                        val statusBorder = if (isOnline) Color(0xFF86EFAC) else if (isSlmReady) Color(0xFFDDD6FE) else Color(0xFFFDE68A)
                        val statusDot = if (isOnline) Color(0xFF16A34A) else if (isSlmReady) Color(0xFF7C3AED) else Color(0xFFD97706)
                        val statusLabel = if (isOnline) "ONLINE (Cloud/LAN)" else if (isSlmReady) "OFFLINE SLM" else "OFFLINE KNOWLEDGE"
                        val statusTextColor = if (isOnline) Color(0xFF14532D) else if (isSlmReady) Color(0xFF5B21B6) else Color(0xFF92400E)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = statusBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, statusBorder),
                            modifier = Modifier.clickable { showBackendSettingsSheet = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(statusDot)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = statusLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusTextColor
                                )
                            }
                        }
                    }

                    // Action Icons: Language Cycle Pill & Settings Icon Button
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 1-Tap Language Cycle Pill
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MintCreamBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7EBD1)),
                            modifier = Modifier.clickable {
                                val nextLang = when (currentLang) {
                                    "te" -> "hi"
                                    "hi" -> "en"
                                    else -> "te"
                                }
                                repository.currentLanguage = nextLang
                                voiceManager.stopSpeaking()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = "Language",
                                    tint = ForestGreenPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when (currentLang) {
                                        "te" -> "తెలుగు"
                                        "hi" -> "हिन्दी"
                                        else -> "English"
                                    },
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF14532D)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Settings Icon Button (Opens Settings -> Offline AI & Storage, Officer Tickets, etc.)
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = ForestGreenPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MintCreamBg)
        ) {
            // =========================================================================
            // CONVERSATION STREAM (SCROLLABLE)
            // =========================================================================
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                reverseLayout = false
            ) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // If no conversations yet: Display Hero Greeting & Starter Voice Chips
                if (historyList.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .border(1.dp, SproutBorder, RoundedCornerShape(20.dp)),
                            colors = CardDefaults.cardColors(containerColor = CardWhite),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFDCFCE7)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.RecordVoiceOver,
                                            contentDescription = null,
                                            tint = ForestGreenPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = when (currentLang) {
                                                "te" -> "నమస్కారం! బోల్ కిసాన్ వాయిస్ అసిస్టెంట్"
                                                "hi" -> "नमस्ते! बोल किसान वॉयस असिस्टेंट"
                                                else -> "Bol Kisan Voice Assistant"
                                            },
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF14532D)
                                        )
                                        Text(
                                            text = when (currentLang) {
                                                "te" -> "రైతుల కోసం స్వదేశీ AI వాయిస్ సేవ"
                                                "hi" -> "भारतीय किसानों के लिए आवाज-प्रथम AI"
                                                else -> "Voice-first on-device AI for farmers"
                                            },
                                            fontSize = 11.5.sp,
                                            color = MutedSlate
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = when (currentLang) {
                                        "te" -> "ఏ పంట గురించైనా, తెగుళ్లు, ఎరువులు లేదా మార్కెట్ ధరల గురించి నేరుగా మైక్ నొక్కి మాట్లాడండి. ఆఫ్‌లైన్ లో కూడా Qwen3 మోడల్ ద్వారా సమాధానం లభిస్తుంది."
                                        "hi" -> "फसल, कीट, खाद या मंडी भाव के बारे में नीचे माइक दबाकर सीधे बोलें। ऑफ़लाइन में भी Qwen3 AI मॉडल द्वारा उत्तर मिलेगा।"
                                        else -> "Tap the microphone below to ask about crops, pests, fertilizers, or diseases. Runs fully offline using built-in Qwen3 SLM."
                                    },
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp,
                                    color = DarkEarthFg
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = strings.quickQuestions,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF14532D)
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Starter chips
                                starterQuestions.take(4).forEach { questionText ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFF1F8F4),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7EBD1)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable { submitMessage(questionText) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Mic,
                                                contentDescription = null,
                                                tint = ForestGreenPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = questionText,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF14532D)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Render history items (User questions and AI responses)
                items(historyList, key = { it.id }) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        // User Query Bubble
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                                color = ForestGreenPrimary,
                                modifier = Modifier.widthIn(max = 310.dp)
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
                                            text = "రైతు ప్రశ్న",
                                            fontSize = 10.sp,
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.query,
                                        fontSize = 13.5.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Bol Kisan AI Response Card
                        val isItemSpeaking = isSpeaking && activeSpeakingItemId == item.id
                        val itemOrigin = item.origin ?: "online"

                        val (badgeBg, badgeText, badgeFg) = when (itemOrigin) {
                            "online" -> Triple(Color(0xFFDCFCE7), "🟢 ONLINE (FastAPI Cloud/LAN)", Color(0xFF14532D))
                            "online_fallback" -> Triple(Color(0xFFE0F2FE), "🟢 ONLINE (ICAR Fallback)", Color(0xFF0369A1))
                            "offline_slm" -> Triple(Color(0xFFF5F3FF), "⚡ OFFLINE SLM (Qwen3-0.6B On-Device AI)", Color(0xFF5B21B6))
                            "offline_rule" -> Triple(Color(0xFFFFFBEB), "📦 OFFLINE KNOWLEDGE (Room SQLite ICAR)", Color(0xFF92400E))
                            "offline_queued" -> Triple(Color(0xFFFFFBEB), "⏳ OFFLINE QUEUED (Auto-Sync)", Color(0xFF92400E))
                            "needs_clarification" -> Triple(Color(0xFFFEE2E2), "❓ Clarification Needed", Color(0xFFB91C1C))
                            "escalated" -> Triple(Color(0xFFEDE9FE), "🧑‍🌾 Escalated to Officer", Color(0xFF6D28D9))
                            "tank_mix_intercept" -> Triple(Color(0xFFFEE2E2), "⚠️ Tank Mix Intercept", Color(0xFFB91C1C))
                            "offline_safety_intercept" -> Triple(Color(0xFFFEE2E2), "🛡️ Safety Intercept", Color(0xFFB91C1C))
                            "banned_chemical_intercept" -> Triple(Color(0xFFFEE2E2), "🛑 Banned Chemical", Color(0xFFB91C1C))
                            else -> Triple(Color(0xFFDCFCE7), "🟢 ONLINE (FastAPI Cloud/LAN)", Color(0xFF14532D))
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    1.dp,
                                    if (isItemSpeaking) ForestGreenPrimary else SproutBorder,
                                    RoundedCornerShape(16.dp)
                                ),
                            colors = CardDefaults.cardColors(containerColor = CardWhite),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                // Header: Crop Chip, Source Attribution Badge, Audio Wave
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (item.crop.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF14532D)
                                            ) {
                                                Text(
                                                    text = localizedCropName(item.crop, currentLang),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }

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

                                    // Animated audio wave when this item is speaking
                                    if (isItemSpeaking) {
                                        AnimatedAudioWave(isSpeaking = true, barColor = ForestGreenPrimary)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Advisory Text
                                Text(
                                    text = item.answer,
                                    fontSize = 13.5.sp,
                                    color = DarkEarthFg,
                                    lineHeight = 20.sp
                                )

                                // Citations / CIBRC Records
                                if (item.citations.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = item.citations.joinToString(" • "),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MutedSlate
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = Color(0xFFE5E7EB), thickness = 0.5.dp)
                                Spacer(modifier = Modifier.height(8.dp))

                                // Audio Controls Toolbar (Play/Pause, Replay, Stop, Mute, Escalate)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Play / Pause Button
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isItemSpeaking) Color(0xFFDCFCE7) else MintCreamBg,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clickable {
                                                    if (isItemSpeaking) {
                                                        voiceManager.stopSpeaking()
                                                    } else {
                                                        activeSpeakingItemId = item.id
                                                        voiceManager.speak(sanitizeForSpeech(item.answer), currentLang)
                                                    }
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    if (isItemSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = if (isItemSpeaking) "Pause" else "Play",
                                                    tint = ForestGreenPrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Replay Button
                                        Surface(
                                            shape = CircleShape,
                                            color = MintCreamBg,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clickable {
                                                    activeSpeakingItemId = item.id
                                                    voiceManager.speak(sanitizeForSpeech(item.answer), currentLang)
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Replay,
                                                    contentDescription = "Replay",
                                                    tint = ForestGreenPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Stop Button (if speaking)
                                        if (isItemSpeaking) {
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xFFFEE2E2),
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clickable { voiceManager.stopSpeaking() }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Stop,
                                                        contentDescription = "Stop",
                                                        tint = HazardRed,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }

                                        // Mute / Speaker Toggle
                                        IconButton(
                                            onClick = { voiceManager.toggleMute() },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                                tint = if (isMuted) HazardRed else ForestGreenPrimary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }

                                    // Escalate to Officer Button
                                    val isEscalating = isEscalatingId == item.id
                                    val ticketNum = escalatedTickets[item.id]

                                    if (ticketNum != null) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFDCFCE7)
                                        ) {
                                            Text(
                                                text = "Ticket: $ticketNum",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF14532D),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    } else {
                                        TextButton(
                                            onClick = {
                                                isEscalatingId = item.id
                                                coroutineScope.launch {
                                                    val res = repository.escalateToHumanOfficer(
                                                        questionText = item.query,
                                                        cropName = item.crop
                                                    )
                                                    isEscalatingId = null
                                                    if (res.isSuccess) {
                                                        val tkt = res.getOrNull()?.ticketNumber ?: "TKT-${System.currentTimeMillis().toString().takeLast(4)}"
                                                        escalatedTickets = escalatedTickets + (item.id to tkt)
                                                        Toast.makeText(context, "Escalated: $tkt", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Escalation failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            enabled = !isEscalating,
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            if (isEscalating) {
                                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                            } else {
                                                Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF0369A1))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("అధికారికి పంపండి", fontSize = 11.sp, color = Color(0xFF0369A1), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // Follow-up Chips
                                if (item.followUpSuggestions.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        item.followUpSuggestions.forEach { suggestion ->
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFFF1F8F4),
                                                border = androidx.compose.foundation.BorderStroke(0.5.dp, SproutBorder),
                                                modifier = Modifier.clickable { submitMessage(suggestion) }
                                            ) {
                                                Text(
                                                    text = suggestion,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF14532D),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Space for floating bottom voice dock
                item {
                    Spacer(modifier = Modifier.height(180.dp))
                }
            }

            // =========================================================================
            // FLOATING VOICE CONTROL DECK (BOTTOM-D悵CKED, VOICE-FIRST)
            // =========================================================================
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Transparent),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Live Speech Transcription Preview (Appears when listening or partial results exist)
                val isListening = assistantState == AssistantState.LISTENING
                if (isListening || liveTranscription.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1F2937).copy(alpha = 0.95f),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedAudioWave(isSpeaking = true, barColor = Color(0xFF4ADE80))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (liveTranscription.isNotBlank()) liveTranscription else when (currentLang) {
                                    "te" -> "మాట్లాడండి... మీ సందేహాన్ని వింటున్నాను"
                                    "hi" -> "बोलिए... आपकी बात सुन रहे हैं"
                                    else -> "Listening... Speak your crop query"
                                },
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 2. Assistant State Status Pill
                val (stateBg, stateFg, stateText) = when (assistantState) {
                    AssistantState.READY -> if (isOnline) {
                        Triple(Color(0xFFDCFCE7), Color(0xFF14532D), when (currentLang) {
                            "te" -> "సిద్ధంగా ఉంది • మాట్లాడటానికి మైక్ నొక్కండి"
                            "hi" -> "तैयार • बोलने के लिए माइक दबाएं"
                            else -> "Ready • Tap mic to speak"
                        })
                    } else {
                        Triple(Color(0xFFFEF3C7), Color(0xFF92400E), when (currentLang) {
                            "te" -> "ఆఫ్‌లైన్ SLM సిద్ధంగా ఉంది (ఇంటర్నెట్ అవసరం లేదు)"
                            "hi" -> "ऑफ़लाइन SLM तैयार (इंटरनेट रहित)"
                            else -> "Offline SLM Ready (Airplane mode active)"
                        })
                    }
                    AssistantState.LISTENING -> Triple(Color(0xFFFEE2E2), Color(0xFFB91C1C), when (currentLang) {
                        "te" -> "వింటున్నాను... మాట్లాడండి"
                        "hi" -> "सुन रहे हैं... बोलिए"
                        else -> "Listening... Speak now"
                    })
                    AssistantState.PROCESSING -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), when (currentLang) {
                        "te" -> "ఆలోచిస్తున్నాను & భద్రత పరిశీలిస్తున్నాను..."
                        "hi" -> "सोच रहे हैं और सुरक्षा जांच रहे हैं..."
                        else -> "Thinking & Verifying Safety..."
                    })
                    AssistantState.SPEAKING -> Triple(Color(0xFFE0F2FE), Color(0xFF0369A1), when (currentLang) {
                        "te" -> "సమాధానం చదువుతున్నాను... ఆపడానికి మైక్ నొక్కండి"
                        "hi" -> "सलाह पढ़ रहे हैं... रोकने के लिए माइक दबाएं"
                        else -> "Speaking response... Tap mic to stop"
                    })
                    AssistantState.ERROR -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), voiceError ?: "Speech error")
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = stateBg,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(stateFg)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stateText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = stateFg
                        )
                    }
                }

                // 3. Inline Text Input (Visible only when keyboard icon is toggled)
                AnimatedVisibility(visible = isKeyboardOpen) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardWhite,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SproutBorder),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = typedQuery,
                                onValueChange = { typedQuery = it },
                                placeholder = {
                                    Text(
                                        text = when (currentLang) {
                                            "te" -> "వ్యవసాయ సందేహాన్ని టైప్ చేయండి..."
                                            "hi" -> "कृषि समस्या टाइप करें..."
                                            else -> "Type agricultural question..."
                                        },
                                        fontSize = 12.sp,
                                        color = MutedSlate
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { submitMessage(typedQuery) }),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                )
                            )
                            IconButton(onClick = { submitMessage(typedQuery) }) {
                                Icon(Icons.Default.Send, contentDescription = "Send", tint = ForestGreenPrimary)
                            }
                        }
                    }
                }

                // 4. Central Voice Control Deck with Large Pulsing Mic
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = CardWhite,
                    shadowElevation = 12.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Attachment (+) Button
                        Surface(
                            shape = CircleShape,
                            color = MintCreamBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SproutBorder),
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { showAttachmentSheet = true }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Attach photo",
                                    tint = ForestGreenPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Center: Large Animated Central Microphone Button
                        LargeCentralMicButton(
                            assistantState = assistantState,
                            rmsLevel = rmsLevel,
                            onClick = { handleMicTapped() }
                        )

                        // Right: Keyboard / Text Chat Toggle Button
                        Surface(
                            shape = CircleShape,
                            color = if (isKeyboardOpen) ForestGreenPrimary else MintCreamBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SproutBorder),
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { isKeyboardOpen = !isKeyboardOpen }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Keyboard,
                                    contentDescription = "Toggle Keyboard",
                                    tint = if (isKeyboardOpen) Color.White else ForestGreenPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // BOTTOM SHEETS
    // =========================================================================

    // 1. Settings & Diagnostics Sheet
    if (showSettingsSheet) {
        SettingsAndDiagnosticsSheet(
            onNavigateToOfflineLibrary = {
                showSettingsSheet = false
                voiceManager.stopSpeaking()
                onNavigateToOfflineLibrary()
            },
            onNavigateToMarketPrices = {
                showSettingsSheet = false
                voiceManager.stopSpeaking()
                onNavigateToMarketPrices()
            },
            onNavigateToBackendSettings = {
                showSettingsSheet = false
                voiceManager.stopSpeaking()
                showBackendSettingsSheet = true
            },
            onNavigateToOfficerTickets = {
                showSettingsSheet = false
                voiceManager.stopSpeaking()
                onNavigateToOfficerTickets()
            },
            onNavigateToOfflineIVR = {
                showSettingsSheet = false
                voiceManager.stopSpeaking()
                onNavigateToOfflineIVR()
            },
            onNavigateToSMSFallback = {
                showSettingsSheet = false
                voiceManager.stopSpeaking()
                onNavigateToSMSFallback()
            },
            onSwitchLanguage = {
                showSettingsSheet = false
                voiceManager.stopSpeaking()
                onSwitchLanguage()
            },
            onDismiss = { showSettingsSheet = false }
        )
    }

    // 2. Developer Backend LAN / Wi-Fi Sheet
    if (showBackendSettingsSheet) {
        DeveloperBackendSettingsSheet(
            repository = repository,
            onDismiss = { showBackendSettingsSheet = false }
        )
    }

    // 3. Attachment Options Sheet (+ button)
    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            containerColor = CardWhite,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = strings.attachSheetTitle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF14532D)
                )
                Text(
                    text = "Add crop photo for disease diagnosis & advisory",
                    fontSize = 12.sp,
                    color = MutedSlate
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Option 1: Camera
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F8F4),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7EBD1)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showAttachmentSheet = false
                            takePhotoLauncher.launch(null)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8F5E9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = ForestGreenPrimary)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = strings.takePhotoSheet,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF14532D)
                            )
                            Text(
                                text = "Take leaf/crop photo with camera",
                                fontSize = 11.sp,
                                color = MutedSlate
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Option 2: Gallery
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F8F4),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7EBD1)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showAttachmentSheet = false
                            galleryLauncher.launch("image/*")
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8F5E9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = ForestGreenPrimary)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = strings.chooseGallerySheet,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF14532D)
                            )
                            Text(
                                text = "Select existing photo from storage",
                                fontSize = 11.sp,
                                color = MutedSlate
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

// =========================================================================
// LARGE CENTRAL MICROPHONE BUTTON (VOICE-FIRST DESIGN)
// =========================================================================
@Composable
fun LargeCentralMicButton(
    assistantState: AssistantState,
    rmsLevel: Float,
    onClick: () -> Unit
) {
    val isListening = assistantState == AssistantState.LISTENING
    val isProcessing = assistantState == AssistantState.PROCESSING
    val isSpeaking = assistantState == AssistantState.SPEAKING

    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.32f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse1"
    )
    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.50f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse2"
    )

    val buttonColor = when {
        isListening -> Color(0xFFDC2626) // Red when listening
        isProcessing -> Color(0xFFD97706) // Amber when thinking
        isSpeaking -> Color(0xFF0284C7) // Sky Blue when reading advisory
        else -> ForestGreenPrimary // Forest Green when ready
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(110.dp)
    ) {
        // Multi-layer glowing ripples when listening
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(105.dp)
                    .scale(pulseScale2 * (1f + rmsLevel * 0.2f))
                    .clip(CircleShape)
                    .background(buttonColor.copy(alpha = 0.18f))
            )
            Box(
                modifier = Modifier
                    .size(95.dp)
                    .scale(pulseScale1 * (1f + rmsLevel * 0.15f))
                    .clip(CircleShape)
                    .background(buttonColor.copy(alpha = 0.30f))
            )
        } else if (isSpeaking) {
            Box(
                modifier = Modifier
                    .size(95.dp)
                    .scale(pulseScale1)
                    .clip(CircleShape)
                    .background(buttonColor.copy(alpha = 0.22f))
            )
        }

        // Center Button Circle
        Surface(
            shape = CircleShape,
            color = buttonColor,
            modifier = Modifier
                .size(78.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = when {
                            isListening -> Icons.Default.Stop
                            isSpeaking -> Icons.Default.Stop
                            else -> Icons.Default.Mic
                        },
                        contentDescription = if (isListening) "Stop Listening" else "Speak to Bol Kisan",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }
    }
}

// =========================================================================
// ANIMATED AUDIO WAVEFORM VISUALIZER
// =========================================================================
@Composable
fun AnimatedAudioWave(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = ForestGreenPrimary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audioWave")
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

// =========================================================================
// SETTINGS & DIAGNOSTICS BOTTOM SHEET
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAndDiagnosticsSheet(
    onNavigateToOfflineLibrary: () -> Unit,
    onNavigateToMarketPrices: () -> Unit,
    onNavigateToBackendSettings: () -> Unit,
    onNavigateToOfficerTickets: () -> Unit,
    onNavigateToOfflineIVR: () -> Unit,
    onNavigateToSMSFallback: () -> Unit,
    onSwitchLanguage: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Settings & Diagnostics",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF14532D)
                    )
                    Text(
                        text = "Bol Kisan system configuration & tools",
                        fontSize = 12.sp,
                        color = MutedSlate
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MutedSlate)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Offline IVR / Voice Fallback
            SettingsMenuCard(
                title = "Offline IVR / Voice Fallback",
                description = "Access agricultural assistance through a guided voice menu, even without internet connectivity.",
                icon = Icons.Default.PhoneInTalk,
                badge = "On-Device",
                onClick = onNavigateToOfflineIVR
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. SMS Fallback
            SettingsMenuCard(
                title = "SMS Fallback",
                description = "Get agricultural assistance through text messages when mobile data is unavailable.",
                icon = Icons.AutoMirrored.Filled.Chat,
                badge = "Low Bandwidth / Demo",
                badgeBg = Color(0xFFFEF3C7),
                badgeFg = Color(0xFFB45309),
                onClick = onNavigateToSMSFallback
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Mandi Market Prices
            SettingsMenuCard(
                title = "Mandi Market Prices",
                description = "View APMC market prices, government MSP rates, and offline commodity price cache",
                icon = Icons.Default.Storefront,
                badge = "AGMARKNET",
                onClick = onNavigateToMarketPrices
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Offline AI & Storage Diagnostics (Moved from main screen)
            SettingsMenuCard(
                title = "Offline AI & Storage Diagnostics",
                description = "On-device Qwen3-0.6B SLM status, model extraction, LiteRT-LM runtime, and Room SQLite packages",
                icon = Icons.Default.CloudOff,
                badge = "On-Device",
                onClick = onNavigateToOfflineLibrary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 5. Backend LAN & Wi-Fi Network Settings
            SettingsMenuCard(
                title = "Backend LAN & Wi-Fi Settings",
                description = "Connect physical phone to laptop FastAPI server, test latency, configure custom endpoint",
                icon = Icons.Default.Dns,
                badge = "Local LAN",
                onClick = onNavigateToBackendSettings
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 6. Officer Escalation Tickets
            SettingsMenuCard(
                title = "Officer Escalation Tickets",
                description = "View questions escalated to human agricultural extension officers",
                icon = Icons.Default.ConfirmationNumber,
                badge = "Support",
                onClick = onNavigateToOfficerTickets
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 7. Change Language
            SettingsMenuCard(
                title = "Change Language (భాష / भाषा)",
                description = "Select primary language for speech recognition, advisory, and voice output",
                icon = Icons.Default.Language,
                badge = "te/hi/en",
                onClick = onSwitchLanguage
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
fun SettingsMenuCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String,
    badgeBg: Color = Color(0xFFDCFCE7),
    badgeFg: Color = ForestGreenPrimary,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF1F8F4),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7EBD1)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDCFCE7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14532D)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = badge,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = MutedSlate,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

// =========================================================================
// DEVELOPER BACKEND SETTINGS BOTTOM SHEET
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperBackendSettingsSheet(
    repository: BolKisanRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentUrl by remember { mutableStateOf(ApiClient.getBaseUrl(context)) }
    var inputUrl by remember { mutableStateOf(currentUrl) }
    var isChecking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<BackendConnectionResult?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var isLoggedIn by remember { mutableStateOf(repository.isLoggedIn()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Backend LAN & Wi-Fi Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF14532D)
                    )
                    Text(
                        text = "Connect to local Windows laptop or emulator",
                        fontSize = 12.sp,
                        color = MutedSlate
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MutedSlate)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Active Base URL
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFF1F8F4),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7EBD1)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "ACTIVE ENDPOINT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14532D),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentUrl,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1F2937)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Presets
            Text(
                text = "QUICK PRESETS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MutedSlate
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val devIp = ApiClient.getDevBackendIp()
                OutlinedButton(
                    onClick = {
                        inputUrl = "http://$devIp:8000/api/v1/"
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text("Laptop Wi-Fi\n($devIp)", fontSize = 11.sp, textAlign = TextAlign.Center)
                }

                OutlinedButton(
                    onClick = {
                        inputUrl = "http://10.0.2.2:8000/api/v1/"
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text("Emulator\n(10.0.2.2)", fontSize = 11.sp, textAlign = TextAlign.Center)
                }

                OutlinedButton(
                    onClick = {
                        inputUrl = ApiClient.getDefaultBaseUrl()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text("Default\nEndpoint", fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // URL input
            OutlinedTextField(
                value = inputUrl,
                onValueChange = { inputUrl = it },
                label = { Text("Base URL") },
                placeholder = { Text("http://192.168.0.150:8000/api/v1/") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Actions: Test Connection & Save
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        isChecking = true
                        checkResult = null
                        coroutineScope.launch {
                            val res = repository.checkBackendConnection(inputUrl)
                            checkResult = res
                            isChecking = false
                        }
                    },
                    enabled = !isChecking,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1))
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Testing...", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Connection", fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = {
                        val validated = ApiClient.validateBaseUrl(inputUrl)
                        if (validated.isValid) {
                            ApiClient.setBaseUrl(validated.sanitizedUrl, context)
                            currentUrl = validated.sanitizedUrl
                            inputUrl = validated.sanitizedUrl
                            Toast.makeText(context, "API Base URL updated and applied", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, validated.errorMessage ?: "Invalid URL", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save & Apply", fontSize = 12.sp)
                }
            }

            // Connection Check Result Card
            checkResult?.let { res: BackendConnectionResult ->
                Spacer(modifier = Modifier.height(14.dp))
                val isConnected = res.status == ConnectionStatus.CONNECTED
                val bg = if (isConnected) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                val border = if (isConnected) Color(0xFF86EFAC) else Color(0xFFFCA5A5)
                val fg = if (isConnected) Color(0xFF14532D) else Color(0xFF991B1B)

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = bg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isConnected) Color(0xFF16A34A) else Color(0xFFDC2626))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = res.status.name.replace("_", " "),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = fg
                                )
                            }
                            if (res.latencyMs > 0) {
                                Text(
                                    text = "${res.latencyMs} ms",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = fg
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = res.errorMessage ?: if (isConnected) "Connected successfully to backend." else "Unable to connect.",
                            fontSize = 11.5.sp,
                            color = fg
                        )

                        res.healthInfo?.let { health ->
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = border, thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Version: ${health.version ?: "1.0.0"} | DB: ${health.database?.status ?: "ok"} | AI: ${if (health.aiProvider?.isGeminiReady == true) "Ready" else "Key missing"}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = fg
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = SproutBorder)
            Spacer(modifier = Modifier.height(14.dp))

            // Demo Farmer Quick Login
            Text(
                text = "DEMO FARMER AUTHENTICATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MutedSlate
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "FastAPI voice queries require an authenticated session. Quick login as demo farmer (9876543210):",
                fontSize = 11.sp,
                color = MutedSlate
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isLoggedIn) "Status: Authenticated (JWT Active)" else "Status: Not Logged In",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isLoggedIn) Color(0xFF16A34A) else Color(0xFFDC2626)
                )

                if (isLoggedIn) {
                    TextButton(onClick = {
                        repository.logout()
                        isLoggedIn = false
                        Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Log Out", fontSize = 11.sp, color = Color(0xFFDC2626))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedButton(
                onClick = {
                    isLoggingIn = true
                    coroutineScope.launch {
                        val res = repository.quickLoginDemo()
                        isLoggingIn = false
                        if (res.isSuccess) {
                            isLoggedIn = true
                            Toast.makeText(context, "Logged in as Ramesh Patel (9876543210)", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Login failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = !isLoggingIn,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isLoggingIn) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ForestGreenPrimary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Authenticating...", fontSize = 12.sp)
                } else {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = ForestGreenPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("1-Tap Login as Demo Farmer", fontSize = 12.sp, color = ForestGreenPrimary, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
