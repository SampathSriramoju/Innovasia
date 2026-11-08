package com.bolkisan.farmer.presentation.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolkisan.farmer.data.remote.models.VoiceAnswerDto
import com.bolkisan.farmer.data.repository.BolKisanRepository
import com.bolkisan.farmer.data.repository.VoiceHistoryItem
import com.bolkisan.farmer.presentation.common.appStrings
import com.bolkisan.farmer.presentation.common.cropOptions
import com.bolkisan.farmer.presentation.common.homeSampleQuestions
import com.bolkisan.farmer.presentation.common.localizedCropName
import com.bolkisan.farmer.presentation.common.mandiRows
import com.bolkisan.farmer.presentation.common.offlineInstantQuestions
import com.bolkisan.farmer.presentation.common.pesticideRecommendation
import com.bolkisan.farmer.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

enum class VoiceMicState {
    IDLE,
    LISTENING,
    PROCESSING,
    PREVIEW_TRANSCRIPT
}

@Composable
fun HomeScreen(
    repository: BolKisanRepository,
    onNavigateToVoice: () -> Unit,
    onNavigateToDiseaseCamera: () -> Unit,
    onNavigateToOfflineLibrary: () -> Unit,
    onNavigateToOfficerTickets: () -> Unit,
    onSwitchLanguage: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val isOnline by repository.isOnline.collectAsState()
    val historyList by repository.historyFlow.collectAsState()

    val currentLang by repository.languageFlow.collectAsState()
    val strings = remember(currentLang) { appStrings(currentLang) }

    var selectedCrop by remember { mutableStateOf(repository.selectedCrop) }

    var isListening by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isEscalating by remember { mutableStateOf(false) }
    var escalatedTicketNotice by remember { mutableStateOf<String?>(null) }
    var activeAnswer by remember { mutableStateOf<VoiceAnswerDto?>(null) }
    var lastQueryText by remember { mutableStateOf("") }
    var typedQuery by remember { mutableStateOf("") }

    // Enhanced Voice UX States
    var micState by remember { mutableStateOf(VoiceMicState.IDLE) }
    var recognizedTranscript by remember { mutableStateOf("") }
    var editableTranscript by remember { mutableStateOf("") }
    var detectedChemicalForConfirmation by remember { mutableStateOf<String?>(null) }
    var showChemicalConfirmDialog by remember { mutableStateOf(false) }

    // Dialogs for working features
    var showCropPicker by remember { mutableStateOf(false) }
    var showMandiDialog by remember { mutableStateOf(false) }
    var showWeatherDialog by remember { mutableStateOf(false) }
    var showPesticideDialog by remember { mutableStateOf(false) }
    var showOfflineVoiceFallbackDialog by remember { mutableStateOf(false) }

    // Initialize TextToSpeech engine
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Initialized
            }
        }
        ttsEngine = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    fun speakText(text: String) {
        ttsEngine?.let { tts ->
            tts.stop()
            val locale = when (currentLang) {
                "te" -> Locale("te", "IN")
                "hi" -> Locale("hi", "IN")
                else -> Locale.US
            }
            val available = tts.isLanguageAvailable(locale)
            if (available >= TextToSpeech.LANG_AVAILABLE) {
                tts.language = locale
            } else {
                tts.language = Locale.US
            }
            val cleanSpeech = text
                .replace(Regex("\\[.*?\\]"), "")
                .replace("*", "")
                .replace("#", "")
            tts.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, "bolkisan_tts")
            isSpeaking = true
        }
    }

    fun stopSpeaking() {
        ttsEngine?.stop()
        isSpeaking = false
    }

    // Submit question directly to knowledge base (Online RAG or Offline ICAR)
    fun submitQuery(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return

        stopSpeaking()
        lastQueryText = cleanQuery
        typedQuery = ""
        keyboardController?.hide()
        isLoading = true
        micState = VoiceMicState.PROCESSING

        coroutineScope.launch {
            val res = repository.askQuestion(cleanQuery)
            isLoading = false
            micState = VoiceMicState.IDLE
            if (res.isSuccess) {
                val ans = res.getOrNull()
                val cleanedText = ans?.responseText
                    ?.replace(Regex("\\[.*?\\]"), "")
                    ?.replace(Regex("(?m)^\\s*(?:ఆధారం|మూలం|स्रोत|Source|Citation)\\s*:.*$"), "")
                    ?.replace(Regex("\\n{3,}"), "\n\n")
                    ?.trim() ?: ""
                val cleanedAns = ans?.copy(responseText = cleanedText)
                activeAnswer = cleanedAns
                if (cleanedText.isNotBlank()) {
                    speakText(cleanedText)
                }
            } else {
                activeAnswer = VoiceAnswerDto(
                    status = "error",
                    isSafe = false,
                    responseText = strings.errorAnswer,
                    citations = listOf("System Notification")
                )
            }
        }
    }

    // Speech Recognizer Intent Launcher with Preview & Safety Intercept
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                recognizedTranscript = spoken
                editableTranscript = spoken

                // Detect agrochemical names for mandatory safety confirmation
                val chemKeywords = listOf(
                    "coragen", "కోరాజెన్", "कोराजन",
                    "curacron", "కురాక్రాన్", "क्यूराक्रॉन",
                    "proclaim", "ప్రోక్లైమ్",
                    "fame", "ఫేమ్",
                    "profenophos", "ప్రొఫెనోఫాస్",
                    "chlorantraniliprole", "క్లోరాంట్రానిలిప్రోల్",
                    "emamectin", "ఇమామెక్టిన్",
                    "tricyclazole", "ట్రైసైక్లాజోల్",
                    "monocrotophos", "మోనోక్రోటోఫాస్"
                )
                val matched = chemKeywords.firstOrNull { spoken.lowercase().contains(it) }
                if (matched != null) {
                    detectedChemicalForConfirmation = when (matched.lowercase()) {
                        "coragen", "కోరాజెన్", "कोराजन" -> "Coragen (Chlorantraniliprole 18.5% SC)"
                        "curacron", "కురాక్రాన్", "क्यूराक्रॉन" -> "Curacron (Profenophos 50% EC)"
                        "proclaim", "ప్రోక్లైమ్" -> "Proclaim (Emamectin Benzoate 5% SG)"
                        "fame", "ఫేమ్" -> "Fame (Flubendiamide 39.35% SC)"
                        "monocrotophos", "మోనోక్రోటోఫాస్" -> "Monocrotophos (BANNED ON VEGETABLES - CIBRC)"
                        "tricyclazole", "ట్రైసైక్లాజోల్" -> "Beam (Tricyclazole 75% WP)"
                        else -> matched.replaceFirstChar { it.uppercase() }
                    }
                    showChemicalConfirmDialog = true
                    micState = VoiceMicState.PREVIEW_TRANSCRIPT
                } else {
                    micState = VoiceMicState.PREVIEW_TRANSCRIPT
                }
            } else {
                micState = VoiceMicState.IDLE
            }
        } else {
            micState = VoiceMicState.IDLE
        }
    }

    fun startListening() {
        val hasOnDeviceASR = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            false
        }

        // If offline and device does NOT support on-device offline recognition:
        if (!isOnline && !hasOnDeviceASR) {
            isListening = false
            micState = VoiceMicState.IDLE
            showOfflineVoiceFallbackDialog = true
            return
        }

        try {
            isListening = true
            micState = VoiceMicState.LISTENING
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                if (!isOnline) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
                val langCode = when (currentLang) {
                    "te" -> "te-IN"
                    "hi" -> "hi-IN"
                    else -> "en-IN"
                }
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
                putExtra(RecognizerIntent.EXTRA_PROMPT, when (currentLang) {
                    "te" -> if (isOnline) "మాట్లాడండి... ఏ పంట గురించైనా అడగండి" else "ఆఫ్‌లైన్ మైక్... మాట్లాడండి"
                    "hi" -> if (isOnline) "बोलिए... किसी भी फसल के बारे में पूछें" else "ऑफ़लाइन माइक... बोलिए"
                    else -> if (isOnline) "Speak now... ask about any crop or pest" else "Offline mic... speak now"
                })
            }
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            isListening = false
            micState = VoiceMicState.IDLE
            showOfflineVoiceFallbackDialog = true
        }
    }

    // Animated pulsing scale for the hero microphone
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening || isLoading) 1.22f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = if (isListening || isLoading) 0.65f else 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // Crop definitions (localized names follow the selected language)
    val crops = remember(currentLang) { cropOptions(currentLang) }

    val currentCropDisplay = localizedCropName(selectedCrop, currentLang)

    // Suggestion chips for 1-tap testing (localized)
    val sampleQuestions = remember(selectedCrop, currentLang) {
        homeSampleQuestions(selectedCrop, currentLang)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MintCreamBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // =========================================================================
            // 1. TOP HEADER: BOL KISAN, Online Status Badge & 1-Tap Language Toggle
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "BOL KISAN",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF14532D),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Compact Network Status Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isOnline) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isOnline) Color(0xFF86EFAC) else Color(0xFFFDE68A)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isOnline) Color(0xFF16A34A) else Color(0xFFD97706))
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isOnline) strings.onlineAiBadge else strings.offlineIcarBadge,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOnline) Color(0xFF14532D) else Color(0xFF92400E)
                                )
                            }
                        }
                    }
                    Text(
                        text = strings.appTagline,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF335C3D)
                    )
                }

                // Language Pill (Tap to cycle: Telugu -> Hindi -> English)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CardWhite,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFC7EBD1)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.clickable {
                        val nextLang = when (currentLang) {
                            "te" -> "hi"
                            "hi" -> "en"
                            else -> "te"
                        }
                        repository.currentLanguage = nextLang
                        stopSpeaking()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = "Language",
                            tint = ForestGreenPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = when (currentLang) {
                                "te" -> "తెలుగు"
                                "hi" -> "हिन्दी"
                                else -> "English"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF14532D)
                        )
                    }
                }
            }

            // =========================================================================
            // 2. COMPACT FEATURES BAR (Clean horizontal scrollable action chips)
            // Replaces the giant cluttered 4-card grid while keeping all features 100% working!
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Feature 1: Selected Crop Selector
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF9A5B13),
                    modifier = Modifier.clickable { showCropPicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🌾 $currentCropDisplay",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = CardWhite
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Change Crop",
                            tint = CardWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Feature: Disease Camera
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E3A8A),
                    modifier = Modifier.clickable { onNavigateToDiseaseCamera() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = CardWhite, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = strings.diseaseCameraChip,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CardWhite
                        )
                    }
                }

                // Feature 2: Pesticide Safety (CIBRC)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFE7CD)),
                    modifier = Modifier.clickable { showPesticideDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = strings.pesticidesChip,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF14532D)
                        )
                    }
                }

                // Feature 3: Mandi Prices
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFE7CD)),
                    modifier = Modifier.clickable { showMandiDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = strings.mandiChip,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF14532D)
                        )
                    }
                }

                // Feature 4: Weather Advisory
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFE7CD)),
                    modifier = Modifier.clickable { showWeatherDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WbSunny, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = strings.weatherChip,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF14532D)
                        )
                    }
                }

                // Feature 5: Officer Tickets
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFE7CD)),
                    modifier = Modifier.clickable { onNavigateToOfficerTickets() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = strings.ticketsChip,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF14532D)
                        )
                    }
                }

                // Feature 6: Offline Library
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFE7CD)),
                    modifier = Modifier.clickable { onNavigateToOfflineLibrary() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = strings.offlineLibraryChip,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF14532D)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // =========================================================================
            // 3. HERO VOICE ASSISTANT (Dedicated, Simple, Interactive Centerpiece)
            // =========================================================================
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardWhite,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, SproutBorder),
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Status Header
                    Text(
                        text = if (isLoading) {
                            strings.heroSearching
                        } else if (isListening) {
                            strings.heroListening
                        } else if (isSpeaking) {
                            strings.heroSpeaking
                        } else {
                            strings.heroTapToSpeak
                        },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF14532D),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = strings.heroHint(currentCropDisplay),
                        fontSize = 12.sp,
                        color = MutedSlate,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Inline Offline Voice Notice Banner (shown when offline without on-device language pack)
                    if (!isOnline) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEF3C7),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = strings.offlineBanner,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF92400E)
                                )
                            }
                        }
                    }

                    // Transcript Preview & Edit Card (Phase 4 UX Enhancement)
                    if (micState == VoiceMicState.PREVIEW_TRANSCRIPT) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF0FDF4),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF86EFAC)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = strings.transcriptHeader,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF14532D)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = editableTranscript,
                                    onValueChange = { editableTranscript = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ForestGreenPrimary,
                                        unfocusedBorderColor = Color(0xFF86EFAC)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = DarkEarthFg)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            micState = VoiceMicState.IDLE
                                            editableTranscript = ""
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = strings.cancel,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            val q = editableTranscript.trim()
                                            if (q.isNotBlank()) {
                                                micState = VoiceMicState.PROCESSING
                                                submitQuery(q)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = strings.askButton,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Big Animated Voice Orb / Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(116.dp)
                    ) {
                        // Outer Pulsing Ripple Ring
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(
                                    (if (isListening) HazardRed else if (isSpeaking) LeafGreenSecondary else ForestGreenPrimary)
                                        .copy(alpha = pulseAlpha)
                                )
                        )

                        // Secondary Mid Ring
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(
                                    (if (isListening) HazardRed else Color(0xFF86EFAC))
                                        .copy(alpha = 0.35f)
                                )
                        )

                        // Main Touch Target Microphone
                        Surface(
                            shape = CircleShape,
                            color = if (isListening) HazardRed else if (isSpeaking) Color(0xFF16A34A) else ForestGreenPrimary,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (isSpeaking) {
                                        stopSpeaking()
                                    } else if (isListening) {
                                        isListening = false
                                    } else {
                                        startListening()
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        color = CardWhite,
                                        modifier = Modifier.size(36.dp),
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    Icon(
                                        if (isSpeaking) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = "Voice Assistant Microphone",
                                        tint = CardWhite,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isSpeaking) {
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(
                            onClick = { stopSpeaking() },
                            colors = ButtonDefaults.textButtonColors(contentColor = HazardRed)
                        ) {
                            Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(strings.stopAudio, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Quick Suggestion Chips (1-tap testing for farmers)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sampleQuestions.forEach { sampleQ ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF1F8F4),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7EBD1)),
                                modifier = Modifier.clickable { submitQuery(sampleQ) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = HarvestGoldAccent, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = sampleQ,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF14532D)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Text Query Fallback Input Bar (Farmer/tester can also type question)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = typedQuery,
                            onValueChange = { typedQuery = it },
                            placeholder = {
                                Text(
                                    text = strings.typedQuestionHint,
                                    fontSize = 12.5.sp,
                                    color = MutedSlate
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { submitQuery(typedQuery) }),
                            modifier = Modifier.weight(1f)
                        )

                        if (typedQuery.isNotBlank()) {
                            IconButton(
                                onClick = { submitQuery(typedQuery) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Send Question",
                                    tint = ForestGreenPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 4. ACTIVE KNOWLEDGE BASE ANSWER CARD (Front & Center when answering)
            // =========================================================================
            if (activeAnswer != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .border(
                            2.dp,
                            if (activeAnswer?.isSafe == false) HazardRed else LeafGreenSecondary,
                            RoundedCornerShape(20.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ForestGreenPrimary
                                ) {
                                    Text(
                                        text = localizedCropName(selectedCrop, currentLang),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CardWhite,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = lastQueryText,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkEarthFg,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // TTS Audio Play/Stop Button
                            Surface(
                                shape = CircleShape,
                                color = if (isSpeaking) LeafGreenSecondary else MintCreamBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, ForestGreenPrimary),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable {
                                        if (isSpeaking) stopSpeaking() else activeAnswer?.responseText?.let { speakText(it) }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                                        contentDescription = "Speak Answer",
                                        tint = if (isSpeaking) CardWhite else ForestGreenPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Safety Alert if not verified safe
                        if (activeAnswer?.isSafe == false) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEE2E2),
                                border = androidx.compose.foundation.BorderStroke(1.dp, HazardRed),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = "Warning",
                                        tint = HazardRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = strings.bannedWarning,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HazardRed
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Answer Content from Knowledge Base
                        Text(
                            text = activeAnswer?.responseText ?: "",
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp,
                            color = DarkEarthFg
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Escalation Card / Button
                        if (escalatedTicketNotice != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MintCreamBg,
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, LeafGreenSecondary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Success",
                                            tint = LeafGreenSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = strings.escalatedSuccess,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ForestGreenPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = strings.escalatedNote(escalatedTicketNotice ?: ""),
                                        fontSize = 11.5.sp,
                                        color = DarkEarthFg
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isEscalating = true
                                        val res = repository.escalateToHumanOfficer(lastQueryText)
                                        isEscalating = false
                                        if (res.isSuccess) {
                                            escalatedTicketNotice = res.getOrNull()?.ticketNumber ?: "BK-2026-X1"
                                        } else {
                                            Toast.makeText(context, strings.escalationFailed, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEscalating) "..." else strings.escalateButton,
                                    fontSize = 12.sp,
                                    color = CardWhite
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 5. CONVERSATION HISTORY SECTION (Prominent, Always Visible)
            // =========================================================================
            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = "History",
                        tint = ForestGreenPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = strings.historyHeader(historyList.size),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkEarthFg
                    )
                }

                if (historyList.isNotEmpty()) {
                    Text(
                        text = strings.clear,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = HazardRed,
                        modifier = Modifier
                            .clickable { repository.clearHistory() }
                            .padding(4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (historyList.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CardWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SproutBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            contentDescription = "No history",
                            tint = Color(0xFFA7D7B5),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = strings.historyEmpty,
                            fontSize = 12.5.sp,
                            color = MutedSlate,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    historyList.forEach { item ->
                        HistoryCard(
                            item = item,
                            language = currentLang,
                            onListen = { speakText(item.answer) },
                            onSelect = {
                                lastQueryText = item.query
                                activeAnswer = VoiceAnswerDto(
                                    status = "history",
                                    isSafe = item.isSafe,
                                    responseText = item.answer,
                                    citations = item.citations
                                )
                                speakText(item.answer)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // =============================================================================
    // MODAL DIALOGS FOR WORKING FEATURES (All functional with 1-tap!)
    // =============================================================================

    // 1. Crop Picker Dialog
    if (showCropPicker) {
        AlertDialog(
            onDismissRequest = { showCropPicker = false },
            title = {
                Text(
                    text = strings.selectCrop,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF14532D)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    crops.forEach { (cropKey, label) ->
                        val isSelected = selectedCrop == cropKey
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF1E7E34) else Color(0xFFF1F8F4),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF1E7E34) else Color(0xFFC7EBD1)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCrop = cropKey
                                    repository.selectedCrop = cropKey
                                    showCropPicker = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$label ($cropKey)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) CardWhite else Color(0xFF14532D)
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = CardWhite)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCropPicker = false }) {
                    Text(strings.ok, fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
                }
            }
        )
    }

    // 2. Mandi Market Prices Dialog
    if (showMandiDialog) {
        AlertDialog(
            onDismissRequest = { showMandiDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = ForestGreenPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.mandiTitle(currentCropDisplay),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14532D),
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = strings.mandiLiveReport,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF14532D)
                    )

                    val prices = remember(selectedCrop, currentLang) {
                        mandiRows(selectedCrop, currentLang)
                    }

                    prices.forEach { row ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF1F8F4),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7EBD1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(row.market, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14532D))
                                    Text(row.price, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = ForestGreenPrimary)
                                }
                                Text(row.note, fontSize = 11.sp, color = MutedSlate)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMandiDialog = false }) {
                    Text(strings.close, fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
                }
            }
        )
    }

    // 3. Weather Forecast Dialog
    if (showWeatherDialog) {
        AlertDialog(
            onDismissRequest = { showWeatherDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WbSunny, contentDescription = null, tint = Color(0xFFD97706))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.weatherTitle,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14532D),
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.weatherBulletin,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14532D)
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFEF3C7),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(strings.weatherToday, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color(0xFF92400E))
                            Text(strings.weatherSpray, fontSize = 11.5.sp, color = Color(0xFF78350F))
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF1F8F4),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7EBD1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(strings.weatherThreeDay, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF14532D))
                            Text(strings.weatherTomorrow, fontSize = 11.5.sp, color = DarkEarthFg)
                            Text(strings.weatherDayAfter, fontSize = 11.5.sp, color = DarkEarthFg)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWeatherDialog = false }) {
                    Text(strings.ok, fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
                }
            }
        )
    }

    // 4. Pesticide Safety Dialog (Zero Guess CIBRC Guide)
    if (showPesticideDialog) {
        AlertDialog(
            onDismissRequest = { showPesticideDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = ForestGreenPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.pesticideTitle,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14532D),
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = strings.pesticideDoseFor(currentCropDisplay),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14532D)
                    )

                    val rec = remember(selectedCrop, currentLang) {
                        pesticideRecommendation(selectedCrop, currentLang)
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFE8F6ED),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(strings.pesticidePumpLabel, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF14532D))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(rec, fontSize = 12.sp, color = DarkEarthFg, lineHeight = 18.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                showPesticideDialog = false
                                onNavigateToDiseaseCamera()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.cameraDiagnosis, fontSize = 11.5.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPesticideDialog = false }) {
                    Text(strings.close, fontWeight = FontWeight.Bold, color = ForestGreenPrimary)
                }
            }
        )
    }

    // 5. Offline Speech ASR Fallback Dialog (Honest hardware fallback)
    if (showOfflineVoiceFallbackDialog) {
        AlertDialog(
            onDismissRequest = { showOfflineVoiceFallbackDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, tint = ForestGreenPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.offlineVoiceTitle,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14532D),
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = strings.offlineVoiceMessage,
                        fontSize = 12.5.sp,
                        color = DarkEarthFg,
                        lineHeight = 18.sp
                    )

                    // Verified instant questions for selected crop
                    val instantQuestions = remember(selectedCrop, currentLang) {
                        offlineInstantQuestions(selectedCrop, currentLang)
                    }

                    instantQuestions.forEach { q ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF1F8F4),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7EBD1)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showOfflineVoiceFallbackDialog = false
                                    submitQuery(q)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.QuestionAnswer, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(q, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF14532D))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOfflineVoiceFallbackDialog = false }) {
                    Text(
                        text = strings.dismiss,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenPrimary
                    )
                }
            }
        )
    }

    // 6. Mandatory Agrochemical Name & Dosage Confirmation Modal (Phase 4 Safety Gate)
    if (showChemicalConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showChemicalConfirmDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = strings.chemicalTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF14532D)
                )
            },
            text = {
                Column {
                    Text(
                        text = strings.chemicalIdentified,
                        fontSize = 12.5.sp,
                        color = MutedSlate
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFEF3C7),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = detectedChemicalForConfirmation ?: "",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color(0xFF92400E),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.chemicalWarning,
                        fontSize = 12.sp,
                        color = DarkEarthFg,
                        lineHeight = 17.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showChemicalConfirmDialog = false
                        micState = VoiceMicState.PROCESSING
                        submitQuery(editableTranscript)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
                ) {
                    Text(
                        text = strings.confirmChemical,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showChemicalConfirmDialog = false }
                ) {
                    Text(
                        text = strings.editText
                    )
                }
            }
        )
    }
}

// Conversation History Card
@Composable
fun HistoryCard(
    item: VoiceHistoryItem,
    language: String,
    onListen: () -> Unit,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, SproutBorder, RoundedCornerShape(16.dp))
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = LeafGreenSecondary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = localizedCropName(item.crop, language),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreenPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.query,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkEarthFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MintCreamBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ForestGreenPrimary),
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onListen() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "Listen",
                            tint = ForestGreenPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.answer,
                fontSize = 12.5.sp,
                color = DarkEarthFg,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
