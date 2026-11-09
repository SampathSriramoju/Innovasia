package com.bolkisan.farmer.presentation.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.bolkisan.farmer.R
import com.bolkisan.farmer.data.remote.models.VoiceAnswerDto
import com.bolkisan.farmer.data.repository.BolKisanRepository
import com.bolkisan.farmer.data.repository.VoiceHistoryItem
import com.bolkisan.farmer.presentation.common.appStrings
import com.bolkisan.farmer.presentation.common.localizedCropName
import com.bolkisan.farmer.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: BolKisanRepository,
    onNavigateToVoice: () -> Unit = {},
    onNavigateToDiseaseCamera: () -> Unit = {},
    onNavigateToOfflineLibrary: () -> Unit = {},
    onNavigateToOfficerTickets: () -> Unit = {},
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
    var isLoading by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var activeSpeakingText by remember { mutableStateOf<String?>(null) }

    // Attached Image State for Disease Diagnosis Workflow
    var attachedPhotoFile by remember { mutableStateOf<File?>(null) }
    var attachedPhotoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }

    // Escalated tickets tracked by item ID -> ticket number
    var escalatedTickets by remember { mutableStateOf(mapOf<String, String>()) }
    var isEscalatingId by remember { mutableStateOf<String?>(null) }

    // Text-To-Speech engine
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
            tts.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, "bolkisan_chat_tts")
            isSpeaking = true
            activeSpeakingText = text
        }
    }

    fun stopSpeaking() {
        ttsEngine?.stop()
        isSpeaking = false
        activeSpeakingText = null
    }

    // Submit question directly to unified conversational backend
    fun submitMessage(queryText: String) {
        val cleanQuery = queryText.trim()
        val photoFile = attachedPhotoFile
        if (cleanQuery.isBlank() && photoFile == null) return

        stopSpeaking()
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
                val spokenText = ans?.responseText
                    ?.replace(Regex("\\[.*?\\]"), "")
                    ?.replace(Regex("(?m)^\\s*(?:ఆధారం|మూలం|स्रोत|Source|Citation)\\s*:.*$"), "")
                    ?.trim() ?: ""
                if (spokenText.isNotBlank()) {
                    speakText(spokenText)
                }
                // Scroll to bottom
                if (historyList.isNotEmpty()) {
                    listState.animateScrollToItem(historyList.size - 1)
                }
            } else {
                Toast.makeText(context, strings.errorAnswer, Toast.LENGTH_SHORT).show()
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

    // Speech Recognizer Intent Launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                submitMessage(spoken)
            }
        }
    }

    fun startListening() {
        try {
            isListening = true
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
                    "te" -> "మాట్లాడండి... మీ వ్యవసాయ సందేహాన్ని అడగండి"
                    "hi" -> "बोलिए... अपनी कृषि समस्या पूछें"
                    else -> "Speak now... ask your agricultural question"
                })
            }
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            isListening = false
            Toast.makeText(context, "Speech recognition unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    // Starter Questions (Multi-Crop & Multi-Domain)
    val starterQuestions = remember(currentLang) {
        when (currentLang) {
            "te" -> listOf(
                "పత్తిలో గులాబీ రంగు పురుగు నివారణ ఏమిటి?",
                "వరిలో అగ్గి తెగులు లక్షణాలు, మందులు?",
                "మిరపలో తామర పురుగుల ఉధృతి ఎలా తగ్గించాలి?",
                "టమోటా ఆకు ముడత నివారణ మార్గాలు?",
                "సేంద్రీయ జీవామృతం తయారీ విధానం చెప్పండి",
                "ఈ వారం వాతావరణం ఎలా ఉంది?"
            )
            "hi" -> listOf(
                "कपास में गुलाबी सुंडी का नियंत्रण कैसे करें?",
                "धान में झुलसा रोग के लक्षण व दवा?",
                "मिर्च में थ्रिप्स कीट से बचाव के उपाय?",
                "टमाटर में पत्ती मरोड़ रोग की रोकथाम?",
                "जैविक जीवामृत बनाने की आसान विधि बताएं",
                "इस सप्ताह का मौसम कैसा रहेगा?"
            )
            else -> listOf(
                "How to control pink bollworm in cotton?",
                "Remedies for blast disease in paddy/rice?",
                "How to manage thrips infestation in chilli?",
                "Prevention of leaf curl virus in tomato?",
                "Step-by-step preparation of organic Jeevamrit",
                "What is the weekly weather forecast?"
            )
        }
    }

    // Scroll to bottom on initial load if history exists
    LaunchedEffect(historyList.size) {
        if (historyList.isNotEmpty()) {
            listState.animateScrollToItem(historyList.size - 1)
        }
    }

    Scaffold(
        topBar = {
            // =========================================================================
            // TOP STATUS BAR:
            // Display ONLY: ONLINE or OFFLINE with compact status dot.
            // Language switch pill (1-tap: Telugu / Hindi / English).
            // Quick icons for Offline Library and Human Officer Tickets.
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
                    // App Title & Connectivity Dot
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_bol_kisan_logo),
                            contentDescription = "Bol Kisan Logo",
                            modifier = Modifier
                                .size(28.dp)
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
                        // Compact Online/Offline Status Indicator (ONLY ONLINE or OFFLINE)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isOnline) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isOnline) Color(0xFF86EFAC) else Color(0xFFFDE68A)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
                                    text = if (isOnline) strings.chatOnlineStatus else strings.chatOfflineStatus,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOnline) Color(0xFF14532D) else Color(0xFF92400E)
                                )
                            }
                        }
                    }

                    // Action Icons: Language Pill, Tickets, Offline Library
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
                                stopSpeaking()
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

                        // Offline Library Icon Button
                        IconButton(
                            onClick = onNavigateToOfflineLibrary,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = "Offline Library",
                                tint = if (isOnline) ForestGreenPrimary else Color(0xFFD97706),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Officer Tickets Icon Button
                        IconButton(
                            onClick = onNavigateToOfficerTickets,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ConfirmationNumber,
                                contentDescription = "Officer Tickets",
                                tint = ForestGreenPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Clear conversation history if items exist
                        if (historyList.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    stopSpeaking()
                                    repository.clearHistory()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Clear History",
                                    tint = MutedSlate,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // =========================================================================
            // CHAT COMPOSER:
            // [ + ] [ Type your farming question... ] [ Microphone ] [ Send ]
            // Large accessible touch targets.
            // Preview thumbnail strip with [X] when an image is attached.
            // =========================================================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardWhite,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, SproutBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    // Attachment Preview Strip
                    if (attachedPhotoBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MintCreamBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, LeafGreenSecondary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        bitmap = attachedPhotoBitmap!!,
                                        contentDescription = "Attached Image",
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = strings.attachImageSheet,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF14532D)
                                        )
                                        Text(
                                            text = "Ready for disease diagnosis",
                                            fontSize = 11.sp,
                                            color = MutedSlate
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        attachedPhotoFile = null
                                        attachedPhotoBitmap = null
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = strings.removeAttachment,
                                        tint = HazardRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Main Composer Row: [ + ] [ Input ] [ Mic ] [ Send ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // [ + ] Attachment Button
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE8F5E9),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7EBD1)),
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .clickable { showAttachmentSheet = true }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Attach Options",
                                    tint = ForestGreenPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Text Field Composer
                        TextField(
                            value = typedQuery,
                            onValueChange = { typedQuery = it },
                            placeholder = {
                                Text(
                                    text = strings.chatComposerHint,
                                    fontSize = 13.sp,
                                    color = MutedSlate
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color(0xFFF8FAFC),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(22.dp),
                            singleLine = false,
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { submitMessage(typedQuery) }),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(22.dp))
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // [ Microphone ] Button
                        Surface(
                            shape = CircleShape,
                            color = if (isListening) HazardRed else if (isSpeaking) Color(0xFF16A34A) else ForestGreenPrimary,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .size(46.dp)
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
                                Icon(
                                    if (isSpeaking) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = "Voice Assistant",
                                    tint = CardWhite,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // [ Send ] Button
                        if (typedQuery.isNotBlank() || attachedPhotoFile != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = ForestGreenPrimary,
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .clickable { submitMessage(typedQuery) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Send,
                                        contentDescription = "Send",
                                        tint = CardWhite,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        // =========================================================================
        // CHAT AREA:
        // Multi-turn conversational stream.
        // Welcome state if no history yet.
        // Farmer query bubbles + Bol Kisan response cards with audio, citations,
        // CIBRC safety warnings, escalation button, and follow-up suggestion chips.
        // =========================================================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MintCreamBg)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Welcome Card if no messages yet
                if (historyList.isEmpty() && !isLoading) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = CardWhite,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SproutBorder),
                            shadowElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFDCFCE7)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Agriculture,
                                        contentDescription = null,
                                        tint = ForestGreenPrimary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = strings.chatWelcomeTitle,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF14532D),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = strings.chatWelcomeDesc,
                                    fontSize = 12.5.sp,
                                    color = MutedSlate,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Quick Starter Suggestions
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    starterQuestions.forEach { prompt ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFF1F8F4),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7EBD1)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { submitMessage(prompt) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Bolt,
                                                    contentDescription = null,
                                                    tint = HarvestGoldAccent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = prompt,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF14532D)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Render Multi-Turn Conversation History
                items(historyList, key = { it.id }) { item ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Farmer Message Bubble (Right aligned)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                                color = Color(0xFFDCFCE7),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                                modifier = Modifier.widthIn(max = 300.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Optional Attached Image Preview in Chat
                                    if (item.attachedImagePath != null) {
                                        val imgFile = File(item.attachedImagePath)
                                        if (imgFile.exists()) {
                                            val bm = BitmapFactory.decodeFile(imgFile.absolutePath)
                                            if (bm != null) {
                                                Image(
                                                    bitmap = bm.asImageBitmap(),
                                                    contentDescription = "Farmer Photo",
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(140.dp)
                                                        .clip(RoundedCornerShape(8.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                        }
                                    }

                                    Text(
                                        text = item.query,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = DarkEarthFg
                                    )
                                }
                            }
                        }

                        // 2. Bol Kisan Response Bubble / Card (Left aligned)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = CardWhite,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.5.dp,
                                    if (!item.isSafe) HazardRed else SproutBorder
                                ),
                                shadowElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    // Response Card Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFE8F5E9)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = "🌱", fontSize = 14.sp)
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))

                                            // Crop identified chip (if present)
                                            val itemCrop = item.crop ?: ""
                                            val itemOrigin = item.origin ?: "online"
                                            if (itemCrop.isNotBlank()) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF14532D)
                                                ) {
                                                    Text(
                                                        text = localizedCropName(itemCrop, currentLang),
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = CardWhite,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }

                                            // Origin Badge
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = when (itemOrigin) {
                                                    "offline_slm" -> Color(0xFFFEF3C7)
                                                    "offline_rule" -> Color(0xFFF3F4F6)
                                                    "needs_clarification" -> Color(0xFFFEE2E2)
                                                    else -> Color(0xFFDCFCE7)
                                                }
                                            ) {
                                                Text(
                                                    text = when (itemOrigin) {
                                                        "offline_slm" -> "⚡ Offline SLM"
                                                        "offline_rule" -> "📦 Verified ICAR"
                                                        "needs_clarification" -> "❓ Clarification"
                                                        else -> "🟢 Online AI"
                                                    },
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when (itemOrigin) {
                                                        "offline_slm" -> Color(0xFF92400E)
                                                        "offline_rule" -> Color(0xFF374151)
                                                        "needs_clarification" -> HazardRed
                                                        else -> Color(0xFF14532D)
                                                    },
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        // TTS Audio Play/Stop Button
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isSpeaking && activeSpeakingText == item.answer) LeafGreenSecondary else MintCreamBg,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, ForestGreenPrimary),
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clickable {
                                                    if (isSpeaking && activeSpeakingText == item.answer) {
                                                        stopSpeaking()
                                                    } else {
                                                        speakText(item.answer)
                                                    }
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    if (isSpeaking && activeSpeakingText == item.answer) Icons.Default.Stop else Icons.Default.VolumeUp,
                                                    contentDescription = "Speak",
                                                    tint = if (isSpeaking && activeSpeakingText == item.answer) CardWhite else ForestGreenPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Safety Warning if unsafe
                                    if (!item.isSafe) {
                                        Spacer(modifier = Modifier.height(8.dp))
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
                                                Icon(Icons.Default.Warning, contentDescription = null, tint = HazardRed, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = strings.bannedWarning,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = HazardRed
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Cleaned Response Text
                                    Text(
                                        text = item.answer,
                                        fontSize = 13.5.sp,
                                        lineHeight = 20.sp,
                                        color = DarkEarthFg
                                    )

                                    // Citations / Sources Badge
                                    if (item.citations.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Verified,
                                                contentDescription = null,
                                                tint = LeafGreenSecondary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = item.citations.joinToString(" • "),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = ForestGreenPrimary,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Escalation to Human Officer Action
                                    Spacer(modifier = Modifier.height(10.dp))
                                    val escalatedTicket = escalatedTickets[item.id]
                                    if (escalatedTicket != null) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MintCreamBg,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, LeafGreenSecondary),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LeafGreenSecondary, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "${strings.escalatedSuccess} (${escalatedTicket})",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ForestGreenPrimary
                                                )
                                            }
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    isEscalatingId = item.id
                                                    val res = repository.escalateToHumanOfficer(
                                                        questionText = item.query,
                                                        cropName = item.crop
                                                    )
                                                    isEscalatingId = null
                                                    if (res.isSuccess) {
                                                        val tkt = res.getOrNull()?.ticketNumber ?: "BK-OFFICER-2026"
                                                        escalatedTickets = escalatedTickets + (item.id to tkt)
                                                    } else {
                                                        Toast.makeText(context, strings.escalationFailed, Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isEscalatingId == item.id) "..." else strings.escalateButton,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ForestGreenPrimary
                                            )
                                        }
                                    }

                                    // Follow-up Suggestions Chips
                                    if (item.followUpSuggestions.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = strings.followUpHeader,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF14532D)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            item.followUpSuggestions.forEach { suggestion ->
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFFF1F8F4),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7EBD1)),
                                                    modifier = Modifier.clickable { submitMessage(suggestion) }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(Icons.Default.QuestionAnswer, contentDescription = null, tint = LeafGreenSecondary, modifier = Modifier.size(13.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = suggestion,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color(0xFF14532D)
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
                }

                // Loading / Thinking indicator
                if (isLoading) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = CardWhite,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SproutBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = ForestGreenPrimary,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = strings.thinkingIndicator,
                                    fontSize = 12.5.sp,
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

    // =========================================================================
    // MODAL BOTTOM SHEET FOR IMAGE ATTACHMENT:
    // 1. Take Photo
    // 2. Choose from Gallery
    // 3. Attach Crop Photo for Disease Diagnosis
    // =========================================================================
    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            containerColor = CardWhite,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = strings.attachImageSheet,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF14532D)
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Option 1: Take Photo
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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8F5E9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = ForestGreenPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = strings.takePhotoSheet,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF14532D)
                            )
                            Text(
                                text = "Take leaf or crop photo with camera",
                                fontSize = 11.sp,
                                color = MutedSlate
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Option 2: Choose from Gallery
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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8F5E9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = ForestGreenPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = strings.chooseGallerySheet,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF14532D)
                            )
                            Text(
                                text = "Pick existing photo from device",
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
