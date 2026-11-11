package com.bolkisan.farmer.presentation.sms

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolkisan.farmer.data.local.entities.PendingSyncTicketEntity
import com.bolkisan.farmer.data.local.entities.SMSMessageEntity
import com.bolkisan.farmer.data.repository.BolKisanRepository
import com.bolkisan.farmer.data.worker.SyncWorker
import com.bolkisan.farmer.domain.sms.SmsSegmentCalculator
import com.bolkisan.farmer.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data model for simulated SMS messages in Bol Kisan Fallback module.
 */
data class SmsMessage(
    val id: String = UUID.randomUUID().toString(),
    val isOutgoing: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val senderNumber: String,
    val recipientNumber: String,
    val segmentCount: Int,
    val encodingType: String, // "GSM-7" or "UCS-2"
    val deliveryStatus: String, // "SIMULATED / DELIVERED" or "SAVED LOCALLY (PENDING SYNC)"
    val originTag: String? = null, // "[SIMULATED - MOCK SMS GATEWAY]" or "[OFFLINE CACHED ANSWER - STORED ON DEVICE]"
    val ticketCode: String? = null,
    val ticketStatus: String? = null // "Saved locally", "Submitted to Mock Gateway", "Received by backend", "Awaiting officer response"
)

/**
 * Demo Farmer Profile for SMS sender selection.
 */
data class DemoFarmerAccount(
    val name: String,
    val phone: String,
    val location: String
) {
    val displayLabel: String get() = "$phone ($name)"
}

val DEMO_FARMERS = listOf(
    DemoFarmerAccount("Ramesh Reddy", "+91 98480 12345", "Warangal, Telangana"),
    DemoFarmerAccount("Surendra Sharma", "+91 94140 56789", "Kota, Rajasthan"),
    DemoFarmerAccount("Anil Deshmukh", "+91 98200 11223", "Akola, Maharashtra")
)

const val KISAN_SEVA_RECIPIENT = "Bol Kisan Kisan Seva (57575)"

/**
 * SMS Metrics calculation based on GSM-7 (160 char / 153 concat) vs UCS-2 Unicode (70 char / 67 concat).
 */
data class SmsMetrics(
    val charCount: Int,
    val maxLimitForCurrentSegments: Int,
    val segments: Int,
    val encoding: String
)

fun calculateSmsMetrics(text: String): SmsMetrics {
    val details = SmsSegmentCalculator.calculateDetails(text)
    val effectiveSegments = if (details.segments == 0) 1 else details.segments
    val maxLimit = if (effectiveSegments <= 1) details.maxSingle else effectiveSegments * details.maxMultipart
    val encodingLabel = if (details.isUnicode) "UCS-2 Unicode" else "GSM-7"
    return SmsMetrics(details.charCount, maxLimit, effectiveSegments, encodingLabel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SMSFallbackScreen(
    repository: BolKisanRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    // 1. Language state
    var selectedLanguage by remember {
        mutableStateOf(repository.currentLanguage.ifBlank { "te" })
    }

    // 2. Gateway Connectivity toggle (Simulated Online vs Offline Local Storage)
    var isOnlineMode by remember { mutableStateOf(true) }

    // 3. Sender profile
    var selectedFarmer by remember { mutableStateOf(DEMO_FARMERS[0]) }
    var showSenderDropdown by remember { mutableStateOf(false) }

    // 4. Input text & metrics
    var inputText by remember { mutableStateOf("") }
    val metrics = remember(inputText) { calculateSmsMetrics(inputText) }
    var isSending by remember { mutableStateOf(false) }

    // 5. Reactive Room Database Persistence (sms_messages entity)
    val storedEntities by repository.smsDao.getAllMessages().collectAsState(initial = emptyList())

    // Convert Room SMSMessageEntity to UI SmsMessage model
    fun entityToModel(entity: SMSMessageEntity): SmsMessage {
        val originTag = if (entity.isOfflineCached) {
            "[OFFLINE CACHED ANSWER - STORED ON DEVICE]"
        } else if (!entity.isOutgoing) {
            "[SIMULATED - MOCK SMS GATEWAY]"
        } else null

        val ticketStatus = if (entity.ticketNumber != null) {
            when (entity.deliveryStatus) {
                "SAVED_LOCALLY" -> "Saved locally"
                "SUBMITTED" -> "Submitted to Mock Gateway"
                "RECEIVED_BY_BACKEND" -> "Received by backend"
                "AWAITING_OFFICER_RESPONSE" -> "Awaiting officer response"
                "SYNCED" -> "Received by backend"
                else -> if (entity.isSynced) "Received by backend" else "Saved locally"
            }
        } else null

        return SmsMessage(
            id = entity.id,
            isOutgoing = entity.isOutgoing,
            text = entity.messageText,
            timestamp = entity.timestamp,
            senderNumber = entity.senderNumber,
            recipientNumber = entity.recipientNumber,
            segmentCount = entity.segments,
            encodingType = entity.encoding,
            deliveryStatus = entity.deliveryStatus,
            originTag = originTag,
            ticketCode = entity.ticketNumber,
            ticketStatus = ticketStatus
        )
    }

    val messages = remember(storedEntities) {
        storedEntities.map { entityToModel(it) }
    }

    // Prepopulate initial SMS seed into Room SQLite if database is empty
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (repository.smsDao.getMessageCount() == 0) {
                val now = System.currentTimeMillis()
                val seedEntities = listOf(
                    SMSMessageEntity(
                        id = "seed-msg-1",
                        senderNumber = DEMO_FARMERS[0].displayLabel,
                        recipientNumber = KISAN_SEVA_RECIPIENT,
                        messageText = "Cotton pink bollworm dose",
                        isOutgoing = true,
                        timestamp = now - 180000,
                        deliveryStatus = "SIMULATED / DELIVERED",
                        isOfflineCached = false,
                        ticketNumber = null,
                        language = "en",
                        charCount = 26,
                        segments = 1,
                        encoding = "GSM-7",
                        isSynced = true
                    ),
                    SMSMessageEntity(
                        id = "seed-msg-2",
                        senderNumber = KISAN_SEVA_RECIPIENT,
                        recipientNumber = DEMO_FARMERS[0].displayLabel,
                        messageText = "BOL KISAN ADVISORY [COTTON]: Pink bollworm management - Spray Profenofos 50% EC @ 2ml/L water or Emamectin Benzoate 5% SG @ 5g/10L water. Set 8 pheromone traps/acre. - Kisan Seva 57575",
                        isOutgoing = false,
                        timestamp = now - 175000,
                        deliveryStatus = "SIMULATED / DELIVERED",
                        isOfflineCached = false,
                        ticketNumber = null,
                        language = "en",
                        charCount = 186,
                        segments = 2,
                        encoding = "GSM-7",
                        isSynced = true
                    ),
                    SMSMessageEntity(
                        id = "seed-msg-3",
                        senderNumber = DEMO_FARMERS[0].displayLabel,
                        recipientNumber = KISAN_SEVA_RECIPIENT,
                        messageText = "క్లోరిపైరిఫాస్ మరియు కాపర్ ఆక్సిక్లోరైడ్ కలపవచ్చా? పత్తిలో కొట్టాలి",
                        isOutgoing = true,
                        timestamp = now - 90000,
                        deliveryStatus = "SIMULATED / DELIVERED",
                        isOfflineCached = false,
                        ticketNumber = null,
                        language = "te",
                        charCount = 63,
                        segments = 2,
                        encoding = "UCS-2",
                        isSynced = true
                    ),
                    SMSMessageEntity(
                        id = "seed-msg-4",
                        senderNumber = KISAN_SEVA_RECIPIENT,
                        recipientNumber = DEMO_FARMERS[0].displayLabel,
                        messageText = "హెచ్చరిక: క్లోరిపైరిఫాస్ మరియు కాపర్ ఆక్సిక్లోరైడ్ కలపరాదు! రసాయన చర్య జరిగి ఆకులు మాడిపోతాయి మరియు విషపూరితం అవుతుంది. వ్యక్తిగత అధికారి సహాయం కోసం టికెట్ రిజిస్టర్ చేయబడింది.",
                        isOutgoing = false,
                        timestamp = now - 85000,
                        deliveryStatus = "RECEIVED_BY_BACKEND",
                        isOfflineCached = false,
                        ticketNumber = "OFFLINE-ESC-78421",
                        language = "te",
                        charCount = 168,
                        segments = 3,
                        encoding = "UCS-2",
                        isSynced = true
                    )
                )
                repository.smsDao.insertMessages(seedEntities)
            }
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Sample question sets by language
    val sampleQuestions = remember(selectedLanguage) {
        when (selectedLanguage) {
            "te" -> listOf(
                "పత్తిలో గులాబీ రంగు పురుగు మందు మోతాదు ఎంత?",
                "టమాటా ఆకుమచ్చ తెగులు నివారణ ఏమిటి?",
                "క్లోరిపైరిఫాస్ మరియు కాపర్ ఆక్సిక్లోరైడ్ కలపవచ్చా?",
                "వ్యవసాయ అధికారిని సంప్రదించండి"
            )
            "hi" -> listOf(
                "कपास में गुलाबी सुंडी की दवा की खुराक क्या है?",
                "टमाटर अगेती झुलसा रोग का उपचार क्या है?",
                "क्या क्लोरपाइरीफॉस और कॉपर ऑक्सीक्लोराइड मिला सकते हैं?",
                "कृषि अधिकारी से संपर्क करें"
            )
            else -> listOf(
                "Cotton pink bollworm dose",
                "Tomato blight cure",
                "Mix chlorpyrifos & copper oxychloride?",
                "Contact Agricultural Officer"
            )
        }
    }

    // Function to handle dispatch of SMS
    fun handleSendMessage(query: String, forceOfficerEscalation: Boolean = false) {
        val trimmed = query.trim()
        if (trimmed.isBlank() || isSending) return

        isSending = true
        keyboardController?.hide()
        inputText = ""

        val queryMetrics = calculateSmsMetrics(trimmed)
        val outgoingStatus = if (isOnlineMode) "SIMULATED / DELIVERED" else "SAVED LOCALLY (PENDING SYNC)"
        val outgoingId = UUID.randomUUID().toString()
        val outgoingEntity = SMSMessageEntity(
            id = outgoingId,
            senderNumber = selectedFarmer.displayLabel,
            recipientNumber = KISAN_SEVA_RECIPIENT,
            messageText = trimmed,
            isOutgoing = true,
            timestamp = System.currentTimeMillis(),
            deliveryStatus = outgoingStatus,
            isOfflineCached = false,
            ticketNumber = null,
            language = selectedLanguage,
            charCount = trimmed.length,
            segments = queryMetrics.segments,
            encoding = queryMetrics.encoding,
            isSynced = isOnlineMode
        )

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                repository.smsDao.insertMessage(outgoingEntity)
            }
            delay(750) // Simulate transmission latency

            val lower = trimmed.lowercase()
            val isChemicalAlert = lower.contains("chlorpyrifos") || lower.contains("copper oxychloride") ||
                    lower.contains("క్లోరిపైరిఫాస్") || lower.contains("కాపర్") ||
                    lower.contains("क्लोरपाइरीफॉस") || lower.contains("ऑक्सीक्लोराइड") ||
                    lower.contains("mix") || lower.contains("toxic") || lower.contains("విష")

            val isEscalationRequest = forceOfficerEscalation || lower.contains("officer") ||
                    lower.contains("అధికారి") || lower.contains("अधिकारी") ||
                    lower.contains("contact") || lower.contains("help") || lower.contains("emergency")

            val needsTicket = isChemicalAlert || isEscalationRequest

            var ticketId: String? = null
            var initialTicketStatus: String? = null

            if (needsTicket) {
                ticketId = "OFFLINE-ESC-${(System.currentTimeMillis() % 900000) + 100000}"
                initialTicketStatus = if (isOnlineMode) "Submitted to Mock Gateway" else "Saved locally"

                // Enqueue to room database for sync guarantees
                withContext(Dispatchers.IO) {
                    try {
                        repository.dao.enqueuePendingSyncTicket(
                            PendingSyncTicketEntity(
                                localId = ticketId,
                                queryText = "[SMS Fallback] $trimmed",
                                cropName = repository.detectCropFromText(trimmed) ?: "General",
                                audioPath = null,
                                imagePath = null,
                                timestamp = System.currentTimeMillis(),
                                isSynced = false
                            )
                        )
                        SyncWorker.enqueue(context)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Formulate response text
            val responseText: String
            val originTag: String

            if (isOnlineMode) {
                originTag = "[SIMULATED - MOCK SMS GATEWAY]"
                responseText = when {
                    isChemicalAlert -> {
                        when (selectedLanguage) {
                            "te" -> "హెచ్చరిక: క్లోరిపైరిఫాస్ & కాపర్ ఆక్సిక్లోరైడ్ కలపరాదు! పంటకు నష్టం జరుగుతుంది. మీ కోసం టికెట్ $ticketId నమోదు చేశాము. వ్యవసాయ అధికారి సమీక్షిస్తారు. - కిసాన్ సేవ 57575"
                            "hi" -> "चेतावनी: क्लोरपाइरीफॉस और कॉपर ऑक्सीक्लोराइड को एक साथ न मिलाएं! फसल झुलस सकती है। टिकट $ticketId दर्ज है, कृषि अधिकारी समीक्षा करेंगे। - किसान सेवा 57575"
                            else -> "SAFETY ALERT: Do NOT mix Chlorpyrifos with Copper Oxychloride! Incompatible tank mix causes phytotoxicity. Escalation Ticket $ticketId logged for officer review. - Kisan Seva 57575"
                        }
                    }
                    isEscalationRequest -> {
                        when (selectedLanguage) {
                            "te" -> "వ్యవసాయ అధికారి సంప్రదింపు అభ్యర్థన అందింది. టికెట్ కోడ్: $ticketId. సమీప కృషి విజ్ఞాన కేంద్రం (KVK) అధికారి 24 గంటల్లో ప్రత్యుత్తరమిస్తారు. - కిసాన్ సేవ 57575"
                            "hi" -> "कृषि अधिकारी परामर्श अनुरोध प्राप्त हुआ। टिकट कोड: $ticketId. निकटतम केवीके अधिकारी 24 घंटे में जवाब देंगे। - किसान सेवा 57575"
                            else -> "Agricultural Officer Consultation request confirmed. Ticket Code: $ticketId. An extension officer will review your query within 24 hours. - Kisan Seva 57575"
                        }
                    }
                    lower.contains("pink bollworm") || lower.contains("గులాబీ") || lower.contains("सुंडी") -> {
                        when (selectedLanguage) {
                            "te" -> "పత్తి సలహా: గులాబీ పురుగు నివారణకు ప్రొఫెనోఫాస్ 50% EC 2 మి.లీ లేదా ఇమామెక్టిన్ బెంజోయేట్ 5% SG 5 గ్రా/10 లీటర్ల నీటికి పిచికారీ చేయండి. - కిసాన్ సేవ 57575"
                            "hi" -> "कपास परामर्श: गुलाबी सुंडी के लिए प्रोफेनोफॉस 50% EC 2 मिली/ली या एमामेक्टिन बेंजोएट 5% SG 5 ग्राम/10 ली पानी में छिड़कें। - किसान सेवा 57575"
                            else -> "COTTON ADVISORY: Pink bollworm management - Spray Profenofos 50% EC @ 2ml/L or Emamectin Benzoate 5% SG @ 5g/10L water. Install pheromone traps. - Kisan Seva 57575"
                        }
                    }
                    lower.contains("tomato") || lower.contains("blight") || lower.contains("టమాటా") || lower.contains("झुलसा") -> {
                        when (selectedLanguage) {
                            "te" -> "టమాటా సలహా: ఆకుమచ్చ లేదా అల్టర్నేరియా తెగులుకు మాంకోజెబ్ 75% WP @ 2.5 గ్రా/లీ లేదా అజోక్సిస్ట్రోబిన్ @ 1 మి.లీ/లీ స్ప్రే చేయండి. - కిసాన్ సేవ 57575"
                            "hi" -> "टमाटर सलाह: अगेती झुलसा के लिए मैंकोजेब 75% WP @ 2.5 ग्राम/ली या एज़ोक्सीस्ट्रोबिन @ 1 मिली/ली पानी में छिड़कें। - किसान सेवा 57575"
                            else -> "TOMATO ADVISORY: Early/Late Blight control - Spray Mancozeb 75% WP @ 2.5g/L or Azoxystrobin @ 1ml/L water. Ensure proper drainage. - Kisan Seva 57575"
                        }
                    }
                    else -> {
                        when (selectedLanguage) {
                            "te" -> "బోల్ కిసాన్ SMS కేంద్రం: మీ ప్రశ్న నమోదైంది. సిఫార్సు: పంటకు సమతుల్య ఎరువులు & నీటి యాజమాన్యం పాటించండి. మరింత సమాచారం కోసం కిసాన్ సేవ 57575 నంబరును సంప్రదించండి."
                            "hi" -> "बोल किसान एसएमएस केंद्र: आपका प्रश्न दर्ज किया गया। संतुलित खाद और उचित जल प्रबंधन अपनाएं। अधिक जानकारी के लिए किसान सेवा 57575 पर संपर्क करें।"
                            else -> "BOL KISAN SMS GATEWAY: Question received. Recommend integrated nutrient and pest management for optimal yield. For specific doses contact Kisan Seva 57575."
                        }
                    }
                }
            } else {
                // Offline Local Storage mode
                originTag = "[OFFLINE CACHED ANSWER - STORED ON DEVICE]"
                responseText = when {
                    isChemicalAlert -> {
                        when (selectedLanguage) {
                            "te" -> "[ఆఫ్‌లైన్ రక్షణ]: క్లోరిపైరిఫాస్ + కాపర్ కలపడం నిషేధం! టికెట్ $ticketId ఫోన్ మెమరీలో భద్రపరచబడింది. ఇంటర్నెట్ రాగానే కేంద్రానికి చేరుతుంది."
                            "hi" -> "[ऑफलाइन सुरक्षा]: क्लोरपाइरीफॉस + कॉपर मिलाना मना है! टिकट $ticketId फोन मेमोरी में सुरक्षित है। नेटवर्क मिलने पर सिंक होगा।"
                            else -> "[OFFLINE SAFETY LOCK]: Never mix Chlorpyrifos with Copper! Phytotoxicity hazard. Ticket $ticketId cached locally on device for automatic sync."
                        }
                    }
                    isEscalationRequest -> {
                        when (selectedLanguage) {
                            "te" -> "[ఆఫ్‌లైన్ టికెట్]: అధికారి సహాయం కోసం అభ్యర్థన పరికరంలో భద్రపరచబడింది (టికెట్: $ticketId). నెట్‌వర్క్ లభించగానే సర్వర్‌కు చేరుతుంది."
                            "hi" -> "[ऑफलाइन टिकट]: अधिकारी परामर्श अनुरोध डिवाइस में सुरक्षित है (टिकट: $ticketId)। नेटवर्क मिलने पर स्वतः सिंक हो जाएगा।"
                            else -> "[OFFLINE TICKET]: Escalation request saved to local Room storage (Ticket: $ticketId). Will sync to Mock Gateway on network restoration."
                        }
                    }
                    lower.contains("pink bollworm") || lower.contains("గులాబీ") || lower.contains("सुंडी") -> {
                        when (selectedLanguage) {
                            "te" -> "[ఆఫ్‌లైన్ కాష్]: పత్తిలో గులాబీ రంగు పురుగుకు ప్రొఫెనోఫాస్ 2 మి.లీ/లీ నీటికి పిచికారీ చేయండి. ఎకరానికి 8 లింగాకర్షక బుట్టలు పెట్టండి."
                            "hi" -> "[ऑफलाइन कैश]: कपास में गुलाबी सुंडी हेतु प्रोफेनोफॉस 2 मिली/ली पानी में छिड़कें। 8 फेरोमोन ट्रैप प्रति एकड़ लगाएं।"
                            else -> "[OFFLINE CACHE]: Pink Bollworm control - Spray Profenofos @ 2ml/L water. Use 8 pheromone traps per acre. Stored on device."
                        }
                    }
                    else -> {
                        when (selectedLanguage) {
                            "te" -> "[ఆఫ్‌లైన్ నిల్వ]: స్థానిక డేటాబేస్ నుండి పొందిన సమాచారం: క్రమం తప్పకుండా పొలం తనిఖీ చేయండి. సందేహం ఉంటే వ్యవసాయ అధికారి బటన్ నొక్కండి."
                            "hi" -> "[ऑफलाइन स्टोरेज]: स्थानीय डेटाबेस से सलाह: खेत का नियमित निरीक्षण करें। संदेह होने पर कृषि अधिकारी बटन दबाएं।"
                            else -> "[OFFLINE STORAGE]: Local crop advisory retrieved from on-device SQLite database. Press 'Request Officer' for complex issues."
                        }
                    }
                }
            }

            val respMetrics = calculateSmsMetrics(responseText)
            val incomingId = UUID.randomUUID().toString()
            val incomingStatus = if (isOnlineMode) {
                if (ticketId != null) "SUBMITTED" else "SIMULATED / DELIVERED"
            } else {
                "SAVED LOCALLY (PENDING SYNC)"
            }

            val incomingEntity = SMSMessageEntity(
                id = incomingId,
                senderNumber = KISAN_SEVA_RECIPIENT,
                recipientNumber = selectedFarmer.displayLabel,
                messageText = responseText,
                isOutgoing = false,
                timestamp = System.currentTimeMillis(),
                deliveryStatus = incomingStatus,
                isOfflineCached = !isOnlineMode,
                ticketNumber = ticketId,
                language = selectedLanguage,
                charCount = responseText.length,
                segments = respMetrics.segments,
                encoding = respMetrics.encoding,
                isSynced = isOnlineMode
            )

            withContext(Dispatchers.IO) {
                repository.smsDao.insertMessage(incomingEntity)
            }
            isSending = false

            // If online mode with ticket, simulate asynchronous backend progression
            if (isOnlineMode && ticketId != null) {
                delay(1200)
                withContext(Dispatchers.IO) {
                    repository.smsDao.updateDeliveryStatus(incomingId, "RECEIVED_BY_BACKEND")
                }
                delay(1500)
                withContext(Dispatchers.IO) {
                    repository.smsDao.updateDeliveryStatus(incomingId, "AWAITING_OFFICER_RESPONSE")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardWhite)
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = DarkEarthFg
                            )
                        }
                        Column {
                            Text(
                                text = "Bol Kisan SMS Fallback",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkEarthFg
                            )
                            Text(
                                text = "Low Bandwidth Telecom Simulator",
                                fontSize = 11.sp,
                                color = MutedSlate
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Interactive Connection Status Pill
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isOnlineMode) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                            border = BorderStroke(1.dp, if (isOnlineMode) SproutBorder else Color(0xFFFDE68A)),
                            modifier = Modifier
                                .clickable {
                                    isOnlineMode = !isOnlineMode
                                    Toast.makeText(
                                        context,
                                        if (isOnlineMode) "Switched to Online Mock Gateway" else "Switched to Offline Local Storage",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isOnlineMode) ForestGreenPrimary else SoftAmber)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isOnlineMode) "Online (Mock)" else "Offline (Local)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOnlineMode) DarkEarthFg else Color(0xFF92400E)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Clear Conversation Button
                        IconButton(
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    repository.smsDao.clearAll()
                                }
                                Toast.makeText(context, "Conversation cleared from local Room database", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Clear Conversation",
                                tint = MutedSlate
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)

                // b) Permanent Mode & Disclaimer Banner
                Surface(
                    color = if (isOnlineMode) Color(0xFFF0FDF4) else Color(0xFFFFFBEB),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isOnlineMode) Icons.Default.Dns else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = if (isOnlineMode) ForestGreenPrimary else SoftAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isOnlineMode) {
                                    "BOL KISAN MOCK SMS GATEWAY — SIMULATED DEMO — NO REAL TELEPHONE OR CARRIER CHARGES"
                                } else {
                                    "OFFLINE SMS SIMULATOR — STORED ON DEVICE — DEFERRED SYNCHRONIZATION"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isOnlineMode) DarkEarthFg else Color(0xFF92400E)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Carrier note: Actual carrier SMS requires an external telecom gateway integration. This is an in-app simulated demo.",
                            fontSize = 9.5.sp,
                            color = MutedSlate,
                            lineHeight = 12.sp
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)

                // c) Sender & Recipient Bar
                Surface(
                    color = CardWhite,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Sender selector
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showSenderDropdown = true }
                            ) {
                                Text(
                                    text = "From: ",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MutedSlate
                                )
                                Text(
                                    text = selectedFarmer.displayLabel,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DarkEarthFg,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Farmer",
                                    tint = MutedSlate,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showSenderDropdown,
                                onDismissRequest = { showSenderDropdown = false }
                            ) {
                                DEMO_FARMERS.forEach { farmer ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(farmer.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("${farmer.phone} • ${farmer.location}", fontSize = 11.sp, color = MutedSlate)
                                            }
                                        },
                                        onClick = {
                                            selectedFarmer = farmer
                                            showSenderDropdown = false
                                        }
                                    )
                                }
                            }

                            // Recipient
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF1F5F9),
                                border = BorderStroke(0.6.dp, Color(0xFFCBD5E1))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "To: ",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MutedSlate
                                    )
                                    Text(
                                        text = "57575",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ForestGreenPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)

                // d) Language Selection Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MintCreamBg)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Language: ",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkEarthFg
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    val languages = listOf("te" to "తెలుగు", "hi" to "हिन्दी", "en" to "English")
                    languages.forEach { (code, label) ->
                        val isSelected = selectedLanguage == code
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) ForestGreenPrimary else CardWhite,
                            border = BorderStroke(1.dp, if (isSelected) ForestGreenPrimary else SproutBorder),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clickable {
                                    selectedLanguage = code
                                    repository.currentLanguage = code
                                }
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) CardWhite else DarkEarthFg,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = SproutBorder, thickness = 0.8.dp)
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardWhite)
            ) {
                // f) Sample Question Chips (Horizontal Scroll)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Queries:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MutedSlate,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    sampleQuestions.forEach { question ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFF1F8F4),
                            border = BorderStroke(1.dp, Color(0xFFC7EBD1)),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clickable {
                                    inputText = question
                                }
                        ) {
                            Text(
                                text = question,
                                fontSize = 11.sp,
                                color = DarkEarthFg,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)

                // g) Bottom Input Bar with Live Counter
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Live Character and Segment Counter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SMS Payload Counter",
                            fontSize = 10.sp,
                            color = MutedSlate
                        )
                        Text(
                            text = "${metrics.charCount} / ${metrics.maxLimitForCurrentSegments} chars (${metrics.segments} segment${if (metrics.segments > 1) "s" else ""} ${metrics.encoding})",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (metrics.segments > 2) SoftAmber else ForestGreenPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Escalation Request Icon Button
                        IconButton(
                            onClick = {
                                val query = inputText.ifBlank { "Contact Agricultural Officer" }
                                handleSendMessage(query, forceOfficerEscalation = true)
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEF3C7))
                        ) {
                            Icon(
                                imageVector = Icons.Default.ConfirmationNumber,
                                contentDescription = "Request Agricultural Officer",
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Text Input Field
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    text = if (selectedLanguage == "te") "సందేశం టైప్ చేయండి..." else if (selectedLanguage == "hi") "संदेश टाइप करें..." else "Type SMS text...",
                                    fontSize = 13.sp,
                                    color = MutedSlate
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ForestGreenPrimary,
                                unfocusedBorderColor = SproutBorder,
                                focusedContainerColor = CardWhite,
                                unfocusedContainerColor = CardWhite
                            ),
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { handleSendMessage(inputText) })
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Send Button
                        FilledIconButton(
                            onClick = { handleSendMessage(inputText) },
                            enabled = inputText.isNotBlank() && !isSending,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = ForestGreenPrimary,
                                disabledContainerColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send SMS",
                                tint = CardWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        // e) Message History Thread
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MintCreamBg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No messages yet.\nSelect a sample query or type an SMS to start the simulated session.",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = MutedSlate
                        )
                    }
                }
            }

            items(messages, key = { it.id }) { message ->
                SmsBubbleItem(message = message)
            }

            if (isSending) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CardWhite,
                            border = BorderStroke(0.8.dp, SproutBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = ForestGreenPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Processing SMS Gateway simulation...",
                                    fontSize = 11.sp,
                                    color = MutedSlate
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Message bubble item for both Farmer Outgoing and System Incoming messages.
 */
@Composable
fun SmsBubbleItem(message: SmsMessage) {
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormatter.format(Date(message.timestamp)) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (message.isOutgoing) 14.dp else 2.dp,
                bottomEnd = if (message.isOutgoing) 2.dp else 14.dp
            ),
            color = if (message.isOutgoing) Color(0xFFDCFCE7) else CardWhite,
            border = BorderStroke(1.dp, if (message.isOutgoing) SproutBorder else Color(0xFFE2E8F0)),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Origin Tag if incoming
                if (!message.isOutgoing && message.originTag != null) {
                    val isOffline = message.originTag.contains("OFFLINE")
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isOffline) Color(0xFFFEF3C7) else Color(0xFFEFF6FF),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = message.originTag,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isOffline) Color(0xFFB45309) else Color(0xFF1D4ED8),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                // Message Text
                Text(
                    text = message.text,
                    fontSize = 13.5.sp,
                    color = DarkEarthFg,
                    lineHeight = 18.sp
                )

                // Ticket Escalation Badge (if attached)
                if (message.ticketCode != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFFBEB),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "🎫 Ticket: ${message.ticketCode}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF92400E)
                                )
                            }
                            if (message.ticketStatus != null) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Status: ${message.ticketStatus}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when (message.ticketStatus) {
                                        "Saved locally" -> SoftAmber
                                        "Submitted to Mock Gateway" -> Color(0xFF2563EB)
                                        "Received by backend" -> Color(0xFF0D9488)
                                        "Awaiting officer response" -> ForestGreenPrimary
                                        else -> MutedSlate
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Footer Metadata: Time + Segment Count Tag + Delivery Status Tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        fontSize = 9.5.sp,
                        color = MutedSlate
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Segment count tag
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = "${message.segmentCount} seg (${message.encodingType})",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MutedSlate,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Status tag
                        val isDelivered = message.deliveryStatus.contains("DELIVERED")
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isDelivered) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
                        ) {
                            Text(
                                text = message.deliveryStatus,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDelivered) ForestGreenPrimary else Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
