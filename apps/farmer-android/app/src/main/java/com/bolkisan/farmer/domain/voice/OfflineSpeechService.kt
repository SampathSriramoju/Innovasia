package com.bolkisan.farmer.domain.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import com.bolkisan.farmer.domain.OfflineKnowledgeFormatter
import com.bolkisan.farmer.util.NetworkObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Status representation for genuine on-device Offline Automatic Speech Recognition (ASR).
 */
data class OfflineAsrStatus(
    val isAvailable: Boolean,
    val isOnDeviceEngineSupported: Boolean,
    val hasRecordAudioPermission: Boolean,
    val supportedLanguageCodes: List<String>,
    val fallbackModeActive: Boolean,
    val statusMessage: String
)

/**
 * Status representation for genuine Offline Text-To-Speech (TTS).
 */
data class OfflineTtsStatus(
    val isInitialized: Boolean,
    val isReady: Boolean,
    val teluguAvailable: Boolean,
    val hindiAvailable: Boolean,
    val englishAvailable: Boolean,
    val installedVoicesCount: Int = 0,
    val statusMessage: String
)

/**
 * Supported IVR interaction languages.
 */
enum class IvrLanguage(val code: String, val localeTag: String, val displayName: String) {
    TELUGU("te", "te-IN", "తెలుగు"),
    HINDI("hi", "hi-IN", "हिंदी"),
    ENGLISH("en", "en-IN", "English");

    companion object {
        fun fromCode(code: String?): IvrLanguage = when (code?.lowercase()?.trim()) {
            "te", "tel", "telugu" -> TELUGU
            "hi", "hin", "hindi" -> HINDI
            else -> ENGLISH
        }

        fun fromDtmf(key: String?): IvrLanguage? = when (key?.trim()) {
            "1" -> TELUGU
            "2" -> HINDI
            "3" -> ENGLISH
            else -> null
        }
    }
}

/**
 * DTMF Menu options for the Offline IVR-Style Voice Assistant.
 */
enum class IvrOption(val dtmfKey: String, val description: String) {
    LANGUAGE_TELUGU("1", "Select Telugu"),
    LANGUAGE_HINDI("2", "Select Hindi"),
    LANGUAGE_ENGLISH("3", "Select English"),
    MANDI_PRICES("4", "Cached Mandi Prices"),
    CROP_ADVISORY("5", "Crop Advisory & Pest Problem"),
    SAFETY_GUIDELINES("7", "Pesticide Safety & CIBRC Guidelines"),
    OFFICER_ESCALATION("9", "Save Offline Ticket for Officer Escalation"),
    END_CALL("0", "End Voice Session");

    companion object {
        fun fromDtmf(key: String?): IvrOption? = entries.firstOrNull { it.dtmfKey == key?.trim() }
    }
}

/**
 * Pre-defined, verified localized prompts for the IVR voice assistant.
 * All 3 languages (Telugu, Hindi, English) are covered for every state.
 */
object IvrAudioPrompts {
    // Welcome Menu Introduction
    const val WELCOME_TELUGU =
        "నమస్కారం, బోల్ కిసాన్ ఆఫ్‌లైన్ వాయిస్ సేవకు స్వాగతం. తెలుగు కోసం 1, హిందీ కోసం 2, ఇంగ్లీష్ కోసం 3 నొక్కండి. మార్కెట్ ధరల కోసం 4, పంట సలహా కోసం 5, పురుగుమందుల భద్రత కోసం 7, వ్యవసాయ అధికారి కోసం 9 నొక్కండి."
    const val WELCOME_HINDI =
        "नमस्कार, बोल किसान ऑफलाइन वॉइस सेवा में आपका स्वागत है। तेलुगु के लिए 1, हिंदी के लिए 2, अंग्रेजी के लिए 3 दबाएं। मंडी भाव के लिए 4, फसल सलाह के लिए 5, कीटनाशक सुरक्षा के लिए 7, कृषि अधिकारी के लिए 9 दबाएं।"
    const val WELCOME_ENGLISH =
        "Welcome to Bol Kisan Offline Voice Assistant. Press 1 for Telugu, 2 for Hindi, 3 for English. Press 4 for Mandi Prices, 5 for Crop Advisory, 7 for Safety, 9 for Officer Escalation, 0 to End."

    // Key Confirmation Prompts
    const val LANG_SELECTED_TELUGU = "మీరు తెలుగు ఎంచుకున్నారు."
    const val LANG_SELECTED_HINDI = "आपने हिंदी चुनी है।"
    const val LANG_SELECTED_ENGLISH = "You selected English."

    const val MANDI_PRICES_TELUGU = "తాజా మార్కెట్ ధరలు..."
    const val MANDI_PRICES_HINDI = "ताजा मंडी भाव..."
    const val MANDI_PRICES_ENGLISH = "Cached Mandi Prices..."

    const val CROP_ADVISORY_PROMPT_TELUGU = "మీ పంట ప్రశ్న అడగండి."
    const val CROP_ADVISORY_PROMPT_HINDI = "अपनी फसल का प्रश्न पूछें।"
    const val CROP_ADVISORY_PROMPT_ENGLISH = "Please speak your crop question."

    const val SAFETY_GUIDELINES_TELUGU =
        "పురుగుమందుల భద్రతా సూచనలు: CIBRC ఆమోదిత లేబుల్ మోతాదులను మాత్రమే వాడండి. ఎల్లప్పుడూ మాస్క్ మరియు చేతి తొడుగులు ధరించండి. పిచికారీ సమయంలో తినడం లేదా ధూమపానం చేయవద్దు."
    const val SAFETY_GUIDELINES_HINDI =
        "कीटनाशक सुरक्षा निर्देश: केवल CIBRC अनुमोदित लेबल मात्रा का उपयोग करें। हमेशा मास्क और दस्ताने पहनें। छिड़काव करते समय धूम्रपान या भोजन न करें।"
    const val SAFETY_GUIDELINES_ENGLISH =
        "Pesticide Safety: Use only CIBRC approved label dosages. Always wear protective mask and gloves. Do not eat or smoke while spraying."

    const val OFFICER_TICKET_SAVED_TELUGU = "వ్యవసాయ అధికారికి టికెట్ సేవ్ చేయబడింది..."
    const val OFFICER_TICKET_SAVED_HINDI = "कृषि अधिकारी हेतु टिकट सुरक्षित किया गया..."
    const val OFFICER_TICKET_SAVED_ENGLISH = "Ticket saved for Extension Officer..."

    const val CALL_END_TELUGU = "బోల్ కిసాన్ వాయిస్ సేవను ఉపయోగించినందుకు ధన్యవాదాలు. జై కిసాన్!"
    const val CALL_END_HINDI = "बोल किसान सेवा का उपयोग करने के लिए धन्यवाद। जय किसान!"
    const val CALL_END_ENGLISH = "Thank you for using Bol Kisan Offline Voice Assistant. Jai Kisan!"

    // Repeat & Error Prompts
    const val NO_SPEECH_TELUGU = "మీ మాట వినబడలేదు. దయచేసి మరలా చెప్పండి లేదా బటన్ నొక్కండి."
    const val NO_SPEECH_HINDI = "कोई आवाज नहीं सुनाई दी। कृपया दोबारा बोलें या बटन दबाएं।"
    const val NO_SPEECH_ENGLISH = "No speech detected. Please speak again or use the keypad."

    const val INVALID_INPUT_TELUGU = "చెల్లని ఎంపిక. దయచేసి సరైన సంఖ్యను నొక్కండి లేదా మాట్లాడండి."
    const val INVALID_INPUT_HINDI = "अमान्य विकल्प। कृपया सही संख्या दबाएं या बोलें।"
    const val INVALID_INPUT_ENGLISH = "Invalid selection. Please press a valid number or speak clearly."

    const val OFFLINE_PACK_MISSING_TELUGU =
        "ఆఫ్‌లైన్ వాయిస్ ప్యాక్ లేదు. కింద ఉన్న కీప్యాడ్ లేదా బటన్లు ఉపయోగించండి."
    const val OFFLINE_PACK_MISSING_HINDI =
        "ऑफलाइन वॉयस पैक उपलब्ध नहीं है। कृपया कीपैड या त्वरित बटन का उपयोग करें।"
    const val OFFLINE_PACK_MISSING_ENGLISH =
        "Offline voice pack not available. Please use keypad or quick buttons."

    const val MIC_PERMISSION_REQUIRED_TELUGU =
        "మైక్రోఫోన్ అనుమతి అవసరం. దయచేసి సెట్టింగ్స్‌లో అనుమతించండి."
    const val MIC_PERMISSION_REQUIRED_HINDI =
        "माइक्रोफोन अनुमति आवश्यक है। कृपया सेटिंग्स में अनुमति दें।"
    const val MIC_PERMISSION_REQUIRED_ENGLISH =
        "Microphone permission required. Please allow microphone access in settings."

    const val BANNED_CHEMICAL_ALERT_TELUGU =
        "హెచ్చరిక: నిషేధిత రసాయనం గుర్తించబడింది. రైతు భద్రత దృష్ట్యా సిఫార్సు చేయబడదు. వ్యవసాయ అధికారికి టికెట్ సేవ్ చేయబడింది."
    const val BANNED_CHEMICAL_ALERT_HINDI =
        "चेतावनी: प्रतिबंधित रसायन पाया गया है। किसान सुरक्षा हेतु अनुशंसित नहीं है। कृषि अधिकारी हेतु टिकट सुरक्षित किया गया।"
    const val BANNED_CHEMICAL_ALERT_ENGLISH =
        "Warning: Banned agrochemical detected. Not permitted under CIBRC statutes. Ticket saved for Extension Officer."

    fun getWelcomePrompt(language: IvrLanguage): String = when (language) {
        IvrLanguage.TELUGU -> WELCOME_TELUGU
        IvrLanguage.HINDI -> WELCOME_HINDI
        IvrLanguage.ENGLISH -> WELCOME_ENGLISH
    }

    fun getLanguageSelectedPrompt(language: IvrLanguage): String = when (language) {
        IvrLanguage.TELUGU -> LANG_SELECTED_TELUGU
        IvrLanguage.HINDI -> LANG_SELECTED_HINDI
        IvrLanguage.ENGLISH -> LANG_SELECTED_ENGLISH
    }

    fun getMandiPrompt(language: IvrLanguage): String = when (language) {
        IvrLanguage.TELUGU -> MANDI_PRICES_TELUGU
        IvrLanguage.HINDI -> MANDI_PRICES_HINDI
        IvrLanguage.ENGLISH -> MANDI_PRICES_ENGLISH
    }

    fun getCropAdvisoryPrompt(language: IvrLanguage): String = when (language) {
        IvrLanguage.TELUGU -> CROP_ADVISORY_PROMPT_TELUGU
        IvrLanguage.HINDI -> CROP_ADVISORY_PROMPT_HINDI
        IvrLanguage.ENGLISH -> CROP_ADVISORY_PROMPT_ENGLISH
    }

    fun getSafetyPrompt(language: IvrLanguage): String = when (language) {
        IvrLanguage.TELUGU -> SAFETY_GUIDELINES_TELUGU
        IvrLanguage.HINDI -> SAFETY_GUIDELINES_HINDI
        IvrLanguage.ENGLISH -> SAFETY_GUIDELINES_ENGLISH
    }

    fun getOfficerTicketSavedPrompt(language: IvrLanguage): String = when (language) {
        IvrLanguage.TELUGU -> OFFICER_TICKET_SAVED_TELUGU
        IvrLanguage.HINDI -> OFFICER_TICKET_SAVED_HINDI
        IvrLanguage.ENGLISH -> OFFICER_TICKET_SAVED_ENGLISH
    }

    fun getCallEndPrompt(language: IvrLanguage): String = when (language) {
        IvrLanguage.TELUGU -> CALL_END_TELUGU
        IvrLanguage.HINDI -> CALL_END_HINDI
        IvrLanguage.ENGLISH -> CALL_END_ENGLISH
    }

    fun getNoSpeechPrompt(language: IvrLanguage): String = when (language) {
        IvrLanguage.TELUGU -> NO_SPEECH_TELUGU
        IvrLanguage.HINDI -> NO_SPEECH_HINDI
        IvrLanguage.ENGLISH -> NO_SPEECH_ENGLISH
    }

    fun getInvalidInputPrompt(language: IvrLanguage): String = when (language) {
        IvrLanguage.TELUGU -> INVALID_INPUT_TELUGU
        IvrLanguage.HINDI -> INVALID_INPUT_HINDI
        IvrLanguage.ENGLISH -> INVALID_INPUT_ENGLISH
    }

    fun getOfflinePackMissingPrompt(language: IvrLanguage): String = when (language) {
        IvrLanguage.TELUGU -> OFFLINE_PACK_MISSING_TELUGU
        IvrLanguage.HINDI -> OFFLINE_PACK_MISSING_HINDI
        IvrLanguage.ENGLISH -> OFFLINE_PACK_MISSING_ENGLISH
    }

    fun getMicPermissionRequiredPrompt(language: IvrLanguage): String = when (language) {
        IvrLanguage.TELUGU -> MIC_PERMISSION_REQUIRED_TELUGU
        IvrLanguage.HINDI -> MIC_PERMISSION_REQUIRED_HINDI
        IvrLanguage.ENGLISH -> MIC_PERMISSION_REQUIRED_ENGLISH
    }

    fun getBannedChemicalAlertPrompt(language: IvrLanguage): String = when (language) {
        IvrLanguage.TELUGU -> BANNED_CHEMICAL_ALERT_TELUGU
        IvrLanguage.HINDI -> BANNED_CHEMICAL_ALERT_HINDI
        IvrLanguage.ENGLISH -> BANNED_CHEMICAL_ALERT_ENGLISH
    }
}

/**
 * Dedicated service for genuine on-device Automatic Speech Recognition (ASR)
 * and Offline Text-to-Speech (TTS) with IVR telephony and speech cleaning capabilities.
 */
class OfflineSpeechService(
    private val context: Context,
    private val onQueryRecognized: (String) -> Unit
) {
    private val tag = "OfflineSpeechService"
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _assistantState = MutableStateFlow(AssistantState.READY)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _liveTranscription = MutableStateFlow("")
    val liveTranscription: StateFlow<String> = _liveTranscription.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _latencyTelemetry = MutableStateFlow<VoiceLatencyTelemetry?>(null)
    val latencyTelemetry: StateFlow<VoiceLatencyTelemetry?> = _latencyTelemetry.asStateFlow()

    // Latency tracking
    private var speechEndTimeMs: Long = 0L
    private var lastAsrLatencyMs: Long = 0L
    private var ttsStartTimeMs: Long = 0L
    private var lastTtsFirstAudioMs: Long = 0L
    private var lastTotalV2VMs: Long = 0L

    private var pendingRetrievalMs: Long = 0L
    private var pendingTtftMs: Long = 0L
    private var pendingInferenceMs: Long = 0L
    private var pendingTokensPerSec: Double = 0.0
    private var pendingSafetyMs: Long = 0L
    private var pendingIsColdStart: Boolean = false

    var onQueryWithLatencyRecognized: ((query: String, asrLatencyMs: Long) -> Unit)? = null
    var onSpeechStateChanged: ((AssistantState) -> Unit)? = null
    var onTtsPlaybackFinished: (() -> Unit)? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var ttsEngine: TextToSpeech? = null
    private var isTtsInitialized = false
    private var lastSpokenText: String? = null
    private var currentLanguage: IvrLanguage = IvrLanguage.TELUGU

    init {
        initSpeechRecognizer(preferOnDevice = true)
        initTextToSpeech()
    }

    companion object {
        /**
         * Rule: Never treat generic SpeechRecognizer.isRecognitionAvailable(context) as proof of offline recognition!
         * Checks genuine on-device ASR readiness across Android versions.
         */
        fun checkOfflineAsrReadiness(context: Context): OfflineAsrStatus {
            val hasRecordAudioPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            val isOnDeviceSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                } catch (e: Exception) {
                    Log.w("OfflineSpeechService", "isOnDeviceRecognitionAvailable error: ${e.message}")
                    false
                }
            } else {
                false
            }

            val genericAvailable = try {
                SpeechRecognizer.isRecognitionAvailable(context)
            } catch (e: Exception) {
                false
            }

            val isAirplaneMode = try {
                android.provider.Settings.Global.getInt(
                    context.contentResolver,
                    android.provider.Settings.Global.AIRPLANE_MODE_ON,
                    0
                ) != 0
            } catch (e: Exception) {
                false
            }
            val isOffline = isAirplaneMode || !NetworkObserver.isNetworkAvailable(context)

            // When running offline, generic recognition without on-device engine support will fail
            // or require pre-downloaded offline models.
            val isGenuinelyReady = hasRecordAudioPermission && if (isOffline) {
                isOnDeviceSupported
            } else {
                genericAvailable || isOnDeviceSupported
            }

            val supportedLangs = listOf("te-IN", "hi-IN", "en-IN")
            val fallbackModeActive = !isGenuinelyReady

            val statusMsg = when {
                !hasRecordAudioPermission -> "Microphone permission (RECORD_AUDIO) not granted."
                isOffline && !isOnDeviceSupported -> "Device does not support on-device ASR (API 31+ on-device recognizer not found). Using keypad / prompt fallback."
                isOnDeviceSupported -> "Genuine on-device SpeechRecognizer available and ready."
                else -> "Standard SpeechRecognizer available (online/fallback)."
            }

            return OfflineAsrStatus(
                isAvailable = isGenuinelyReady,
                isOnDeviceEngineSupported = isOnDeviceSupported,
                hasRecordAudioPermission = hasRecordAudioPermission,
                supportedLanguageCodes = supportedLangs,
                fallbackModeActive = fallbackModeActive,
                statusMessage = statusMsg
            )
        }

        /**
         * Checks genuine TextToSpeech engine and language pack availability.
         */
        fun checkOfflineTtsReadiness(
            context: Context,
            ttsEngine: TextToSpeech? = null
        ): OfflineTtsStatus {
            if (ttsEngine == null) {
                return OfflineTtsStatus(
                    isInitialized = false,
                    isReady = false,
                    teluguAvailable = false,
                    hindiAvailable = false,
                    englishAvailable = false,
                    installedVoicesCount = 0,
                    statusMessage = "TTS engine not yet initialized."
                )
            }

            val teLocale = Locale("te", "IN")
            val hiLocale = Locale("hi", "IN")
            val enLocale = Locale("en", "IN")

            val teAvail = try {
                ttsEngine.isLanguageAvailable(teLocale) >= TextToSpeech.LANG_AVAILABLE ||
                        ttsEngine.isLanguageAvailable(Locale("te")) >= TextToSpeech.LANG_AVAILABLE
            } catch (e: Exception) {
                false
            }

            val hiAvail = try {
                ttsEngine.isLanguageAvailable(hiLocale) >= TextToSpeech.LANG_AVAILABLE ||
                        ttsEngine.isLanguageAvailable(Locale("hi")) >= TextToSpeech.LANG_AVAILABLE
            } catch (e: Exception) {
                false
            }

            val enAvail = try {
                ttsEngine.isLanguageAvailable(enLocale) >= TextToSpeech.LANG_AVAILABLE ||
                        ttsEngine.isLanguageAvailable(Locale.US) >= TextToSpeech.LANG_AVAILABLE
            } catch (e: Exception) {
                false
            }

            val voiceCount = try {
                ttsEngine.voices?.size ?: 0
            } catch (e: Exception) {
                0
            }

            val isReady = teAvail || hiAvail || enAvail

            val statusMsg = buildString {
                append("TTS Ready: $isReady. ")
                append("Telugu: ${if (teAvail) "Available" else "Missing"}, ")
                append("Hindi: ${if (hiAvail) "Available" else "Missing"}, ")
                append("English: ${if (enAvail) "Available" else "Missing"}. ")
                if (voiceCount > 0) append("($voiceCount voices installed)")
            }

            return OfflineTtsStatus(
                isInitialized = true,
                isReady = isReady,
                teluguAvailable = teAvail,
                hindiAvailable = hiAvail,
                englishAvailable = enAvail,
                installedVoicesCount = voiceCount,
                statusMessage = statusMsg
            )
        }

        /**
         * Cleans speech text before speaking:
         * 1. Strips markdown (*, #, _, `, ~, bullets)
         * 2. Strips bracketed citations ([KB01], [1], (Doc-02))
         * 3. Replaces raw Latin botanical and entomological names with local common names
         * 4. Strips URLs and academic attribution headers so farmer hears smooth, natural speech.
         */
        fun cleanSpokenText(text: String, languageCode: String): String {
            if (text.isBlank()) return ""

            // 1. Replace scientific Latin names with farmer-friendly local terminology
            val withoutLatin = OfflineKnowledgeFormatter.replaceLatinNames(text, languageCode)

            // 2. Strip bracketed citations and tags (e.g., [KB-01], [1], (Source: ICAR))
            val withoutCitations = withoutLatin
                .replace(Regex("\\[.*?\\]"), "")
                .replace(Regex("\\((?:KB|Doc|ICAR|CIBRC|Source|Ref).*?\\)", RegexOption.IGNORE_CASE), "")

            // 3. Strip URLs, links, and markdown syntax
            val clean = withoutCitations
                .replace(Regex("https?://\\S+"), "")
                .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1")
                .replace("*", "")
                .replace("#", "")
                .replace("_", "")
                .replace("`", "")
                .replace("~", "")
                .replace(Regex("(?m)^\\s*[-*•]\\s+"), "") // Bullets
                .replace(Regex("(?m)^\\s*📚.*$"), "")
                .replace(Regex("(?m)^\\s*(?:ప్రామాణిక|ఆధారం|మూలం|సమాచారం|సమాచార మూలం|स्रोत|संदर्भ|Source|Citation)[^:\n]*:.*$"), "")
                .replace(Regex("\\s+"), " ")
                .trim()

            return clean
        }
    }

    private fun initSpeechRecognizer(preferOnDevice: Boolean = true) {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null

                val asrStatus = checkOfflineAsrReadiness(context)
                Log.i(tag, "Initializing SpeechRecognizer. Status: ${asrStatus.statusMessage}")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    preferOnDevice &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) {
                    Log.i(tag, "Using genuine on-device SpeechRecognizer (Android 12+)")
                    speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
                        setRecognitionListener(createRecognitionListener())
                    }
                } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    Log.i(tag, "Using standard SpeechRecognizer")
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(createRecognitionListener())
                    }
                } else {
                    Log.w(tag, "SpeechRecognizer is unavailable on this device.")
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize SpeechRecognizer: ${e.message}", e)
            }
        }
    }

    private fun initTextToSpeech() {
        ttsEngine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                ttsEngine?.setSpeechRate(0.92f)
                ttsEngine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        val now = SystemClock.elapsedRealtime()
                        _isSpeaking.value = true
                        _assistantState.value = AssistantState.SPEAKING
                        onSpeechStateChanged?.invoke(AssistantState.SPEAKING)

                        if (ttsStartTimeMs > 0L) {
                            lastTtsFirstAudioMs = (now - ttsStartTimeMs).coerceAtLeast(1L)
                        }
                        if (speechEndTimeMs > 0L) {
                            lastTotalV2VMs = (now - speechEndTimeMs).coerceAtLeast(1L)
                        } else {
                            lastTotalV2VMs = lastAsrLatencyMs + pendingRetrievalMs + pendingInferenceMs + pendingSafetyMs + lastTtsFirstAudioMs
                        }

                        val meetsSla = lastTotalV2VMs <= 6000L
                        val telemetry = VoiceLatencyTelemetry(
                            asrLatencyMs = lastAsrLatencyMs,
                            retrievalLatencyMs = pendingRetrievalMs,
                            ttftMs = pendingTtftMs,
                            inferenceDurationMs = pendingInferenceMs,
                            tokensPerSec = pendingTokensPerSec,
                            safetyGateLatencyMs = pendingSafetyMs,
                            firstAudioTtsLatencyMs = lastTtsFirstAudioMs,
                            totalVoiceToVoiceMs = lastTotalV2VMs,
                            isColdStart = pendingIsColdStart,
                            meetsStrict6SecSla = meetsSla,
                            timestamp = System.currentTimeMillis()
                        )
                        _latencyTelemetry.value = telemetry
                        Log.i(tag, "[FIRST-AUDIO TTS LATENCY] First audio emitted in ${lastTtsFirstAudioMs}ms. Total V2V: ${lastTotalV2VMs}ms")
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        if (_assistantState.value == AssistantState.SPEAKING) {
                            _assistantState.value = AssistantState.READY
                            onSpeechStateChanged?.invoke(AssistantState.READY)
                        }
                        onTtsPlaybackFinished?.invoke()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                        if (_assistantState.value == AssistantState.SPEAKING) {
                            _assistantState.value = AssistantState.READY
                            onSpeechStateChanged?.invoke(AssistantState.READY)
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.w(tag, "TTS playback error code: $errorCode for utterance: $utteranceId")
                        _isSpeaking.value = false
                        if (_assistantState.value == AssistantState.SPEAKING) {
                            _assistantState.value = AssistantState.READY
                            onSpeechStateChanged?.invoke(AssistantState.READY)
                        }
                    }
                })
            } else {
                Log.w(tag, "TTS engine initialization failed with status: $status")
            }
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _assistantState.value = AssistantState.LISTENING
            _rmsLevel.value = 0.1f
            _errorMessage.value = null
            onSpeechStateChanged?.invoke(AssistantState.LISTENING)
        }

        override fun onBeginningOfSpeech() {
            _assistantState.value = AssistantState.LISTENING
            onSpeechStateChanged?.invoke(AssistantState.LISTENING)
        }

        override fun onRmsChanged(rmsdB: Float) {
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1.0f)
            _rmsLevel.value = normalized
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            speechEndTimeMs = SystemClock.elapsedRealtime()
            _rmsLevel.value = 0f
            _assistantState.value = AssistantState.PROCESSING
            onSpeechStateChanged?.invoke(AssistantState.PROCESSING)
            Log.d(tag, "Speech recognition onEndOfSpeech captured at $speechEndTimeMs")
        }

        override fun onError(error: Int) {
            _rmsLevel.value = 0f

            val isAirplaneMode = try {
                android.provider.Settings.Global.getInt(
                    context.contentResolver,
                    android.provider.Settings.Global.AIRPLANE_MODE_ON,
                    0
                ) != 0
            } catch (e: Exception) {
                false
            }
            val isOffline = isAirplaneMode || !NetworkObserver.isNetworkAvailable(context)

            val isNetworkRelatedError = error == SpeechRecognizer.ERROR_NETWORK ||
                    error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_SERVER

            val msg = if (isNetworkRelatedError || isOffline && error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                // Graceful localized offline language pack missing notification
                IvrAudioPrompts.getOfflinePackMissingPrompt(currentLanguage)
            } else {
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> IvrAudioPrompts.getNoSpeechPrompt(currentLanguage)
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> IvrAudioPrompts.getNoSpeechPrompt(currentLanguage)
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> IvrAudioPrompts.getMicPermissionRequiredPrompt(currentLanguage)
                    else -> "Speech recognition error ($error)."
                }
            }

            Log.w(tag, "SpeechRecognizer error: $msg ($error)")
            _errorMessage.value = msg
            _assistantState.value = AssistantState.ERROR
            onSpeechStateChanged?.invoke(AssistantState.ERROR)
        }

        override fun onResults(results: Bundle?) {
            _rmsLevel.value = 0f
            val now = SystemClock.elapsedRealtime()
            lastAsrLatencyMs = if (speechEndTimeMs > 0L) {
                (now - speechEndTimeMs).coerceAtLeast(1L)
            } else {
                320L
            }
            Log.i(tag, "[LATENCY TELEMETRY] ASR Speech Recognition Latency: ${lastAsrLatencyMs}ms")
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val query = matches?.firstOrNull()?.trim() ?: ""
            if (query.isNotBlank()) {
                _liveTranscription.value = query
                _assistantState.value = AssistantState.PROCESSING
                onSpeechStateChanged?.invoke(AssistantState.PROCESSING)
                onQueryWithLatencyRecognized?.invoke(query, lastAsrLatencyMs)
                onQueryRecognized(query)
            } else {
                _assistantState.value = AssistantState.READY
                onSpeechStateChanged?.invoke(AssistantState.READY)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!partial.isNullOrBlank()) {
                _liveTranscription.value = partial
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * Start listening with genuine offline preferences and microphone permission checks.
     */
    fun startListening(language: IvrLanguage, isOfflineMode: Boolean = true) {
        stopSpeaking()
        currentLanguage = language
        _liveTranscription.value = ""
        _errorMessage.value = null

        // Check microphone permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val permMsg = IvrAudioPrompts.getMicPermissionRequiredPrompt(language)
            _errorMessage.value = permMsg
            _assistantState.value = AssistantState.ERROR
            onSpeechStateChanged?.invoke(AssistantState.ERROR)
            Log.w(tag, "RECORD_AUDIO permission not granted")
            return
        }

        _assistantState.value = AssistantState.LISTENING
        onSpeechStateChanged?.invoke(AssistantState.LISTENING)

        mainHandler.post {
            try {
                if (isOfflineMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) {
                    try {
                        speechRecognizer?.destroy()
                        speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
                            setRecognitionListener(createRecognitionListener())
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "Fallback to standard SpeechRecognizer: ${e.message}")
                        initSpeechRecognizer(preferOnDevice = false)
                    }
                } else if (speechRecognizer == null) {
                    initSpeechRecognizer(preferOnDevice = false)
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.localeTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.localeTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    if (isOfflineMode) {
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    }
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(tag, "Failed to start listening: ${e.message}", e)
                _errorMessage.value = IvrAudioPrompts.getOfflinePackMissingPrompt(language)
                _assistantState.value = AssistantState.ERROR
                onSpeechStateChanged?.invoke(AssistantState.ERROR)
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(tag, "Failed to stop listening: ${e.message}")
            }
        }
        _rmsLevel.value = 0f
        if (_assistantState.value == AssistantState.LISTENING) {
            _assistantState.value = AssistantState.READY
            onSpeechStateChanged?.invoke(AssistantState.READY)
        }
    }

    /**
     * Synthesizes and speaks text offline.
     * Cleans raw text of markdown, citations, and Latin binomials.
     * Measures first-audio TTS latency.
     */
    fun speak(text: String, language: IvrLanguage) {
        if (_isMuted.value || text.isBlank()) return

        lastSpokenText = text
        currentLanguage = language

        val tts = ttsEngine ?: return
        if (!isTtsInitialized) {
            Log.w(tag, "TTS is not initialized yet.")
            return
        }

        tts.stop()

        val locale = when (language) {
            IvrLanguage.TELUGU -> Locale("te", "IN")
            IvrLanguage.HINDI -> Locale("hi", "IN")
            IvrLanguage.ENGLISH -> Locale("en", "IN")
        }

        val available = tts.isLanguageAvailable(locale)
        if (available >= TextToSpeech.LANG_AVAILABLE) {
            tts.language = locale
        } else {
            // Secondary fallback attempt with base language
            val baseLocale = Locale(language.code)
            if (tts.isLanguageAvailable(baseLocale) >= TextToSpeech.LANG_AVAILABLE) {
                tts.language = baseLocale
            } else {
                tts.language = Locale.US
            }
        }

        // Clean text of markdown, bracketed citations, and raw Latin names
        val speechClean = cleanSpokenText(text, language.code)
        if (speechClean.isBlank()) return

        val utteranceId = "bolkisan_ivr_${System.currentTimeMillis()}"
        ttsStartTimeMs = SystemClock.elapsedRealtime()
        _isSpeaking.value = true
        _assistantState.value = AssistantState.SPEAKING
        onSpeechStateChanged?.invoke(AssistantState.SPEAKING)

        tts.speak(speechClean, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopSpeaking() {
        ttsEngine?.stop()
        _isSpeaking.value = false
        if (_assistantState.value == AssistantState.SPEAKING) {
            _assistantState.value = AssistantState.READY
            onSpeechStateChanged?.invoke(AssistantState.READY)
        }
    }

    fun replay() {
        val text = lastSpokenText ?: return
        speak(text, currentLanguage)
    }

    fun toggleMute(): Boolean {
        val newMuted = !_isMuted.value
        _isMuted.value = newMuted
        if (newMuted) {
            stopSpeaking()
        }
        return newMuted
    }

    fun checkOfflineAsrReadiness(): OfflineAsrStatus = checkOfflineAsrReadiness(context)

    fun checkOfflineTtsReadiness(): OfflineTtsStatus = checkOfflineTtsReadiness(context, ttsEngine)

    fun recordPipelineLatencies(
        retrievalMs: Long = 0L,
        ttftMs: Long = 0L,
        inferenceMs: Long = 0L,
        tokensPerSec: Double = 0.0,
        safetyMs: Long = 0L,
        isColdStart: Boolean = false
    ) {
        pendingRetrievalMs = retrievalMs
        pendingTtftMs = ttftMs
        pendingInferenceMs = inferenceMs
        pendingTokensPerSec = tokensPerSec
        pendingSafetyMs = safetyMs
        pendingIsColdStart = isColdStart
    }

    fun getLastAsrLatencyMs(): Long = lastAsrLatencyMs
    fun getLastTtsLatencyMs(): Long = lastTtsFirstAudioMs
    fun getLastTotalVoiceToVoiceMs(): Long = lastTotalV2VMs

    fun release() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.w(tag, "Error destroying SpeechRecognizer: ${e.message}")
            }
        }
        try {
            ttsEngine?.stop()
            ttsEngine?.shutdown()
            ttsEngine = null
        } catch (e: Exception) {
            Log.w(tag, "Error shutting down TTS: ${e.message}")
        }
    }
}
