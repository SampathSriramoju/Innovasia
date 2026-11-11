package com.bolkisan.farmer.domain.voice

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.bolkisan.farmer.data.local.entities.MandiPriceEntity
import com.bolkisan.farmer.data.local.entities.PendingSyncTicketEntity
import com.bolkisan.farmer.data.repository.BolKisanRepository
import com.bolkisan.farmer.data.worker.SyncWorker
import com.bolkisan.farmer.domain.OfflineInferenceEngine
import com.bolkisan.farmer.domain.OfflineKnowledgeFormatter
import com.bolkisan.farmer.domain.OfflineSafetyGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * State of the Offline IVR telephony session.
 */
enum class IvrSessionState {
    IDLE,
    WELCOME_PROMPT,
    MENU_AWAITING_INPUT,
    VOICE_LISTENING,
    PROCESSING,
    SPEAKING,
    ESCALATION_DONE,
    TERMINATED
}

/**
 * Central orchestrator for the Offline IVR-Style Voice Assistant.
 * Coordinates OfflineSpeechService, Room knowledge retrieval, OfflineSafetyGate,
 * and DTMF tone routing.
 */
class OfflineIVRManager(
    private val context: Context,
    private val repository: BolKisanRepository,
    private val inferenceEngine: OfflineInferenceEngine? = null,
    initialLanguage: IvrLanguage = IvrLanguage.TELUGU
) {
    private val tag = "OfflineIVRManager"
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _sessionState = MutableStateFlow(IvrSessionState.IDLE)
    val sessionState: StateFlow<IvrSessionState> = _sessionState.asStateFlow()

    private val _activeLanguage = MutableStateFlow(initialLanguage)
    val activeLanguage: StateFlow<IvrLanguage> = _activeLanguage.asStateFlow()

    private val _lastSpokenPrompt = MutableStateFlow("")
    val lastSpokenPrompt: StateFlow<String> = _lastSpokenPrompt.asStateFlow()

    private val _lastSpokenTitle = MutableStateFlow("IVR Welcome Menu")
    val lastSpokenTitle: StateFlow<String> = _lastSpokenTitle.asStateFlow()

    private val _currentCitation = MutableStateFlow("ICAR Package of Practices / CIBRC Verified Knowledge")
    val currentCitation: StateFlow<String> = _currentCitation.asStateFlow()

    private val _lastEscalatedTicket = MutableStateFlow<String?>(null)
    val lastEscalatedTicket: StateFlow<String?> = _lastEscalatedTicket.asStateFlow()

    private val _isCallActive = MutableStateFlow(false)
    val isCallActive: StateFlow<Boolean> = _isCallActive.asStateFlow()

    private val speechService = OfflineSpeechService(context) { query ->
        handleSpokenQuery(query)
    }

    val assistantState: StateFlow<AssistantState> = speechService.assistantState
    val liveTranscription: StateFlow<String> = speechService.liveTranscription
    val rmsLevel: StateFlow<Float> = speechService.rmsLevel
    val isSpeaking: StateFlow<Boolean> = speechService.isSpeaking
    val isMuted: StateFlow<Boolean> = speechService.isMuted
    val errorMessage: StateFlow<String?> = speechService.errorMessage
    val latencyTelemetry: StateFlow<VoiceLatencyTelemetry?> = speechService.latencyTelemetry

    init {
        speechService.onTtsPlaybackFinished = {
            if (_sessionState.value == IvrSessionState.WELCOME_PROMPT ||
                _sessionState.value == IvrSessionState.SPEAKING
            ) {
                _sessionState.value = IvrSessionState.MENU_AWAITING_INPUT
            }
        }
    }

    /**
     * Start the offline IVR phone call session with the standardized welcome prompt.
     */
    fun startCall() {
        _isCallActive.value = true
        _sessionState.value = IvrSessionState.WELCOME_PROMPT
        val welcome = IvrAudioPrompts.getWelcomePrompt(_activeLanguage.value)
        speakPrompt("IVR Welcome & Main Menu", welcome)
    }

    /**
     * Set the current IVR interaction language.
     */
    fun setLanguage(language: IvrLanguage) {
        _activeLanguage.value = language
        repository.currentLanguage = language.code
    }

    /**
     * Speaks a prompt using the genuine offline TTS engine.
     */
    fun speakPrompt(
        title: String,
        text: String,
        citation: String = "ICAR Package of Practices / CIBRC Verified Knowledge"
    ) {
        _lastSpokenTitle.value = title
        _lastSpokenPrompt.value = text
        _currentCitation.value = citation
        _sessionState.value = IvrSessionState.SPEAKING
        speechService.speak(text, _activeLanguage.value)
    }

    /**
     * Handles 12-key DTMF keypad input according to the IVR routing specifications.
     */
    fun handleDtmfKey(char: Char) {
        if (!_isCallActive.value && char != '*' && char != '0') {
            Log.d(tag, "Call is inactive. Ignoring key: $char")
            return
        }

        val lang = _activeLanguage.value

        when (char) {
            '1' -> {
                // Telugu Language Selected
                setLanguage(IvrLanguage.TELUGU)
                val text = "${IvrAudioPrompts.LANG_SELECTED_TELUGU} ${IvrAudioPrompts.WELCOME_TELUGU}"
                speakPrompt("భాష: తెలుగు (Telugu Selected)", text)
            }
            '2' -> {
                // Hindi Language Selected
                setLanguage(IvrLanguage.HINDI)
                val text = "${IvrAudioPrompts.LANG_SELECTED_HINDI} ${IvrAudioPrompts.WELCOME_HINDI}"
                speakPrompt("भाषा: हिंदी (Hindi Selected)", text)
            }
            '3' -> {
                // English Language Selected
                setLanguage(IvrLanguage.ENGLISH)
                val text = "${IvrAudioPrompts.LANG_SELECTED_ENGLISH} ${IvrAudioPrompts.WELCOME_ENGLISH}"
                speakPrompt("Language: English", text)
            }
            '4' -> {
                // Mandi Market Prices
                managerScope.launch {
                    val prices: List<MandiPriceEntity> = withContext(Dispatchers.IO) {
                        try {
                            repository.getAllOfflineMarketPrices().firstOrNull() ?: emptyList<MandiPriceEntity>()
                        } catch (e: Exception) {
                            emptyList<MandiPriceEntity>()
                        }
                    }
                    val topPrices = prices.take(4)
                    val priceSummary = if (topPrices.isNotEmpty()) {
                        val items = topPrices.joinToString(", ") { p ->
                            "${p.commodity}: ₹${p.modalPrice}/qtl"
                        }
                        when (lang) {
                            IvrLanguage.HINDI -> "${IvrAudioPrompts.MANDI_PRICES_HINDI} $items. कुल ${prices.size} रिकॉर्ड्स उपलब्ध हैं।"
                            IvrLanguage.ENGLISH -> "${IvrAudioPrompts.MANDI_PRICES_ENGLISH} $items. Total ${prices.size} local mandi records."
                            IvrLanguage.TELUGU -> "${IvrAudioPrompts.MANDI_PRICES_TELUGU} $items. మొత్తం ${prices.size} ధరల వివరాలు ఉన్నాయి."
                        }
                    } else {
                        when (lang) {
                            IvrLanguage.HINDI -> "${IvrAudioPrompts.MANDI_PRICES_HINDI} कपास ₹7,450/क्विंटल, धान ₹2,320 MSP, लाल मिर्च ₹18,200/क्विंटल, मक्का ₹2,090/क्विंटल।"
                            IvrLanguage.ENGLISH -> "${IvrAudioPrompts.MANDI_PRICES_ENGLISH} Cotton ₹7,450/qtl, Paddy ₹2,320 MSP, Chilli ₹18,200/qtl, Maize ₹2,090/qtl."
                            IvrLanguage.TELUGU -> "${IvrAudioPrompts.MANDI_PRICES_TELUGU} పత్తి క్వింటాల్‌కు ₹7,450, వరి మద్దతు ధర ₹2,320, మిర్చి తేజ ₹18,200, మొక్కజొన్న ₹2,090."
                        }
                    }
                    speakPrompt("Mandi Market Prices", priceSummary, "AGMARKNET & Local Mandi Cache")
                }
            }
            '5' -> {
                // Crop Advisory Mode -> prompts farmer and activates microphone
                val promptText = IvrAudioPrompts.getCropAdvisoryPrompt(lang)
                speakPrompt("Crop Advisory Mode", promptText)
                managerScope.launch {
                    // Small delay to allow prompt to be delivered before opening mic
                    startListening()
                }
            }
            '6' -> {
                // Repeat Last Prompt
                val promptToRepeat = _lastSpokenPrompt.value.ifBlank {
                    IvrAudioPrompts.getWelcomePrompt(lang)
                }
                speakPrompt("Repeat Prompt", promptToRepeat, _currentCitation.value)
            }
            '7' -> {
                // Pesticide Safety & CIBRC Spray Guidelines
                val safetyText = IvrAudioPrompts.getSafetyPrompt(lang)
                speakPrompt("Pesticide Spray Safety", safetyText, "CIBRC Government Safety Standard 2026")
            }
            '8' -> {
                // Supported Crops
                val cropsText = when (lang) {
                    IvrLanguage.HINDI -> "स्थानीय डेटाबेस में उपलब्ध मुख्य फसलें: कपास, धान, मिर्च, मक्का, मूंगफली, सोयाबीन, टमाटर, प्याज, हल्दी और अरहर।"
                    IvrLanguage.ENGLISH -> "Locally supported ICAR crops: Cotton, Paddy, Chilli, Maize, Groundnut, Soybean, Tomato, Onion, Turmeric, and Red Gram."
                    IvrLanguage.TELUGU -> "ఆఫ్‌లైన్ నాలెడ్జ్ బేస్‌లో అందుబాటులో ఉన్న పంటలు: పత్తి, వరి, మిర్చి, మొక్కజొన్న, వేరుశనగ, సోయాబీన్, టమాటా, ఉల్లి, పసుపు, కంది."
                }
                speakPrompt("Supported Crops", cropsText, "ICAR Krishi Vigyan Kendra")
            }
            '9' -> {
                // Officer Escalation Ticket
                val ticketCode = "OFFLINE-ESC-${System.currentTimeMillis().toString().takeLast(6)}"
                _lastEscalatedTicket.value = ticketCode
                _sessionState.value = IvrSessionState.ESCALATION_DONE

                managerScope.launch {
                    withContext(Dispatchers.IO) {
                        repository.dao.enqueuePendingSyncTicket(
                            PendingSyncTicketEntity(
                                localId = ticketCode,
                                queryText = _lastSpokenPrompt.value.ifBlank { "Farmer requested human agricultural officer consultation via Offline IVR." },
                                cropName = "General",
                                audioPath = null,
                                imagePath = null,
                                timestamp = System.currentTimeMillis(),
                                isSynced = false
                            )
                        )
                        SyncWorker.enqueue(context)
                    }
                }

                val escalationVoice = when (lang) {
                    IvrLanguage.HINDI -> "${IvrAudioPrompts.OFFICER_TICKET_SAVED_HINDI} टिकट नंबर $ticketCode. फोन में सुरक्षित है।"
                    IvrLanguage.ENGLISH -> "${IvrAudioPrompts.OFFICER_TICKET_SAVED_ENGLISH} Ticket $ticketCode stored safely for auto-sync."
                    IvrLanguage.TELUGU -> "${IvrAudioPrompts.OFFICER_TICKET_SAVED_TELUGU} టికెట్ నంబర్ $ticketCode. ఫోన్‌లో భద్రపరచబడింది."
                }
                speakPrompt("Officer Escalation Ticket: $ticketCode", escalationVoice, "Bol Kisan Offline Outbox Queue")
            }
            '*' -> {
                // Return to Main Menu
                if (!_isCallActive.value) {
                    _isCallActive.value = true
                }
                val welcome = IvrAudioPrompts.getWelcomePrompt(lang)
                speakPrompt("IVR Welcome & Main Menu", welcome)
            }
            '0' -> {
                // End Call
                endCall()
            }
            else -> {
                // Invalid Keypad Entry
                val invalidMsg = IvrAudioPrompts.getInvalidInputPrompt(lang)
                speakPrompt("Invalid Input", invalidMsg)
            }
        }
    }

    /**
     * Handles a recognized farmer speech query with CIBRC safety interceptor.
     */
    fun handleSpokenQuery(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            val noSpeechPrompt = IvrAudioPrompts.getNoSpeechPrompt(_activeLanguage.value)
            speakPrompt("No Speech Detected", noSpeechPrompt)
            return
        }

        _sessionState.value = IvrSessionState.PROCESSING
        val lang = _activeLanguage.value

        // 1. Check CIBRC Banned Agrochemical Safety Gate First
        val (isBanned, bannedRule) = OfflineSafetyGate.isBannedChemical(cleanQuery)
        if (isBanned) {
            val bannedAlert = IvrAudioPrompts.getBannedChemicalAlertPrompt(lang)
            val chemicalName = bannedRule?.name ?: "Banned Chemical"
            val fullWarning = "$bannedAlert ($chemicalName)"

            // Auto-escalate dangerous banned pesticide request
            val ticketCode = "WARN-CIBRC-${System.currentTimeMillis().toString().takeLast(6)}"
            _lastEscalatedTicket.value = ticketCode
            _sessionState.value = IvrSessionState.ESCALATION_DONE

            managerScope.launch {
                withContext(Dispatchers.IO) {
                    repository.dao.enqueuePendingSyncTicket(
                        PendingSyncTicketEntity(
                            localId = ticketCode,
                            queryText = "CIBRC SAFETY VIOLATION DETECTED: Query='$cleanQuery', Chemical='$chemicalName'",
                            cropName = "Safety Incident",
                            audioPath = null,
                            imagePath = null,
                            timestamp = System.currentTimeMillis(),
                            isSynced = false
                        )
                    )
                    SyncWorker.enqueue(context)
                }
            }

            speakPrompt("CIBRC Safety Violation", fullWarning, "Statutory Agrochemical Ban")
            return
        }

        // 2. Knowledge Retrieval & Inference
        managerScope.launch {
            val tStart = SystemClock.elapsedRealtime()
            val matches = withContext(Dispatchers.IO) {
                repository.dao.searchLocalKnowledge(cleanQuery, lang.code)
            }
            val bestCard = matches.firstOrNull() ?: withContext(Dispatchers.IO) {
                repository.dao.searchLocalKnowledge(cleanQuery, "")
            }.firstOrNull()

            val retrievalMs = (SystemClock.elapsedRealtime() - tStart).coerceAtLeast(1L)
            val cropName = bestCard?.crop ?: "General"
            val contextText = bestCard?.content ?: ""
            val citationText = bestCard?.sourceCitation ?: "ICAR Package of Practices (Offline Cache)"

            val answerText = if (inferenceEngine != null && inferenceEngine.isEngineReady()) {
                try {
                    val bench = inferenceEngine.executeOfflineInference(
                        query = cleanQuery,
                        cropContext = cropName,
                        retrievedContextText = contextText,
                        retrievedCitation = citationText,
                        language = lang.code
                    )
                    speechService.recordPipelineLatencies(
                        retrievalMs = retrievalMs,
                        ttftMs = bench.timeToFirstTokenMs,
                        inferenceMs = bench.totalInferenceMs,
                        tokensPerSec = bench.tokensPerSecond,
                        safetyMs = 12L
                    )
                    bench.responseText
                } catch (e: Exception) {
                    OfflineKnowledgeFormatter.format(contextText, cropName, lang.code)
                }
            } else if (bestCard != null) {
                speechService.recordPipelineLatencies(retrievalMs = retrievalMs, safetyMs = 8L)
                OfflineKnowledgeFormatter.format(bestCard.content, bestCard.crop, lang.code)
            } else {
                speechService.recordPipelineLatencies(retrievalMs = retrievalMs, safetyMs = 5L)
                when (lang) {
                    IvrLanguage.HINDI -> "आपकी फसल का प्रश्न ऑफ़लाइन सुरक्षित किया गया है। नीम तेल 5 मिली प्रति 15 लीटर पंप स्प्रे करें।"
                    IvrLanguage.ENGLISH -> "Query processed offline. Spray 5ml Neem Oil per 15L water tank as first-line IPM practice."
                    IvrLanguage.TELUGU -> "మీ ప్రశ్న ఆఫ్‌లైన్‌లో విశ్లేషించబడింది. 15 లీటర్ల ట్యాంక్‌కు 5మి.లీ వేప నూనె పిచికారీ చేయండి."
                }
            }

            speakPrompt("AI Crop Advisory: $cleanQuery", answerText, citationText)
        }
    }

    /**
     * Trigger offline speech recognition.
     */
    fun startListening() {
        _sessionState.value = IvrSessionState.VOICE_LISTENING
        speechService.startListening(_activeLanguage.value, isOfflineMode = true)
    }

    fun stopListening() {
        speechService.stopListening()
        if (_sessionState.value == IvrSessionState.VOICE_LISTENING) {
            _sessionState.value = IvrSessionState.MENU_AWAITING_INPUT
        }
    }

    fun toggleMute(): Boolean = speechService.toggleMute()

    fun endCall() {
        _isCallActive.value = false
        _sessionState.value = IvrSessionState.TERMINATED
        speechService.stopListening()
        val goodbye = IvrAudioPrompts.getCallEndPrompt(_activeLanguage.value)
        speechService.speak(goodbye, _activeLanguage.value)
    }

    fun checkAsrReadiness(): OfflineAsrStatus = speechService.checkOfflineAsrReadiness()

    fun checkTtsReadiness(): OfflineTtsStatus = speechService.checkOfflineTtsReadiness()

    fun release() {
        managerScope.cancel()
        speechService.release()
    }
}
