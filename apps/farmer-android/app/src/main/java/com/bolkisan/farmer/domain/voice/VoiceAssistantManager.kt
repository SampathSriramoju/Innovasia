package com.bolkisan.farmer.domain.voice

import android.content.Context
import android.content.Intent
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
import com.bolkisan.farmer.util.NetworkObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AssistantState {
    READY,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

data class VoiceLatencyTelemetry(
    val asrLatencyMs: Long = 0L,
    val retrievalLatencyMs: Long = 0L,
    val ttftMs: Long = 0L,
    val inferenceDurationMs: Long = 0L,
    val tokensPerSec: Double = 0.0,
    val safetyGateLatencyMs: Long = 0L,
    val firstAudioTtsLatencyMs: Long = 0L,
    val totalVoiceToVoiceMs: Long = 0L,
    val isColdStart: Boolean = false,
    val meetsStrict6SecSla: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

class VoiceAssistantManager(
    private val context: Context,
    private val onQueryRecognized: (String) -> Unit
) {
    private val tag = "VoiceAssistantManager"
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

    // Observable Real-Time Latency Telemetry Flow
    private val _latencyTelemetry = MutableStateFlow<VoiceLatencyTelemetry?>(null)
    val latencyTelemetry: StateFlow<VoiceLatencyTelemetry?> = _latencyTelemetry.asStateFlow()

    // Internal timing markers for end-to-end latency measurement
    private var speechEndTimeMs: Long = 0L
    private var lastAsrLatencyMs: Long = 0L
    private var ttsStartTimeMs: Long = 0L
    private var lastTtsFirstAudioMs: Long = 0L
    private var lastTotalV2VMs: Long = 0L

    // Intermediate pipeline latency metrics
    private var pendingRetrievalMs: Long = 0L
    private var pendingTtftMs: Long = 0L
    private var pendingInferenceMs: Long = 0L
    private var pendingTokensPerSec: Double = 0.0
    private var pendingSafetyMs: Long = 0L
    private var pendingIsColdStart: Boolean = false

    var onQueryWithLatencyRecognized: ((query: String, asrLatencyMs: Long) -> Unit)? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var ttsEngine: TextToSpeech? = null
    private var isTtsInitialized = false
    private var lastSpokenText: String? = null
    private var lastSpokenLanguage: String = "te"
    private var currentLanguage: String = "te"

    init {
        initSpeechRecognizer()
        initTextToSpeech()
    }

    private fun initSpeechRecognizer() {
        mainHandler.post {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(createRecognitionListener())
                    }
                } else {
                    Log.w(tag, "SpeechRecognizer is not available on this device.")
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to initialize SpeechRecognizer: ${e.message}")
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
                        logVoiceMetrics(telemetry)
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        if (_assistantState.value == AssistantState.SPEAKING) {
                            _assistantState.value = AssistantState.READY
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                        if (_assistantState.value == AssistantState.SPEAKING) {
                            _assistantState.value = AssistantState.READY
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _isSpeaking.value = false
                        if (_assistantState.value == AssistantState.SPEAKING) {
                            _assistantState.value = AssistantState.READY
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
        }

        override fun onBeginningOfSpeech() {
            _assistantState.value = AssistantState.LISTENING
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Normalize dB (-2 to 10 typical) to 0.0 .. 1.0 range
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1.0f)
            _rmsLevel.value = normalized
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            speechEndTimeMs = SystemClock.elapsedRealtime()
            _rmsLevel.value = 0f
            _assistantState.value = AssistantState.PROCESSING
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
                // Friendly Telugu/English fallback message when offline speech recognition lacks downloaded language pack
                "ఆఫ్‌లైన్ వాయిస్ ప్యాక్ లేదు. కింద ఉన్న బటన్లు లేదా టెక్స్ట్ ఉపయోగించండి / Offline speech pack not available. Please use quick prompt buttons or text."
            } else {
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Please speak clearly."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected within time limit."
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                    else -> "Speech recognition error ($error)."
                }
            }

            Log.w(tag, "SpeechRecognizer error: $msg ($error)")
            _errorMessage.value = msg
            _assistantState.value = AssistantState.ERROR
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
                onQueryWithLatencyRecognized?.invoke(query, lastAsrLatencyMs)
                onQueryRecognized(query)
            } else {
                _assistantState.value = AssistantState.READY
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

    fun startListening(language: String, isOnline: Boolean) {
        stopSpeaking()
        currentLanguage = language
        _liveTranscription.value = ""
        _errorMessage.value = null
        _assistantState.value = AssistantState.LISTENING

        val langCode = when (language) {
            "te" -> "te-IN"
            "hi" -> "hi-IN"
            else -> "en-IN"
        }

        mainHandler.post {
            try {
                if (!isOnline && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) {
                    try {
                        speechRecognizer?.destroy()
                        speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
                            setRecognitionListener(createRecognitionListener())
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "Failed to create on-device SpeechRecognizer: ${e.message}, falling back")
                        initSpeechRecognizer()
                    }
                } else if (speechRecognizer == null) {
                    initSpeechRecognizer()
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langCode)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    if (!isOnline) {
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    }
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(tag, "Failed to start SpeechRecognizer: ${e.message}", e)
                _errorMessage.value = "Speech recognition unavailable: ${e.message}"
                _assistantState.value = AssistantState.ERROR
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(tag, "Failed to stop SpeechRecognizer: ${e.message}")
            }
        }
        _rmsLevel.value = 0f
        if (_assistantState.value == AssistantState.LISTENING) {
            _assistantState.value = AssistantState.READY
        }
    }

    fun setProcessingState() {
        _assistantState.value = AssistantState.PROCESSING
    }

    fun setReadyState() {
        _assistantState.value = AssistantState.READY
    }

    fun speak(text: String, language: String) {
        if (_isMuted.value || text.isBlank()) return

        lastSpokenText = text
        lastSpokenLanguage = language

        val tts = ttsEngine ?: return
        if (!isTtsInitialized) return

        tts.stop()

        val locale = when (language) {
            "te" -> Locale("te", "IN")
            "hi" -> Locale("hi", "IN")
            else -> Locale("en", "IN")
        }

        val available = tts.isLanguageAvailable(locale)
        if (available >= TextToSpeech.LANG_AVAILABLE) {
            tts.language = locale
        } else {
            tts.language = Locale.US
        }

        // Clean text of markdown, brackets, and citations for clean spoken speech
        val speechClean = text
            .replace(Regex("\\[.*?\\]"), "")
            .replace("*", "")
            .replace("#", "")
            .replace(Regex("(?m)^\\s*(?:ఆధారం|మూలం|स्रोत|Source|Citation)\\s*:.*$"), "")
            .trim()

        val utteranceId = "bolkisan_voice_${System.currentTimeMillis()}"
        ttsStartTimeMs = SystemClock.elapsedRealtime()
        _isSpeaking.value = true
        _assistantState.value = AssistantState.SPEAKING
        // Operates fully offline on Android with standard built-in voice data
        tts.speak(speechClean, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun getLastAsrLatencyMs(): Long = lastAsrLatencyMs
    fun getLastTtsLatencyMs(): Long = lastTtsFirstAudioMs
    fun getLastTotalVoiceToVoiceMs(): Long = lastTotalV2VMs
    fun getSpeechEndTimeMs(): Long = speechEndTimeMs

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

    private fun logVoiceMetrics(t: VoiceLatencyTelemetry) {
        val slaTag = if (t.meetsStrict6SecSla) "PASS (<= 6.0s SLA)" else "FAIL (> 6.0s SLA)"
        Log.i(tag, """
            |══════════════════════════════════════════════════════════════════
            | [VOICE ASSISTANT COMPLETE VOICE-TO-VOICE LATENCY TELEMETRY]
            | 1. ASR Latency (Microphone stop -> Transcript text): ${t.asrLatencyMs}ms
            | 2. Knowledge Retrieval (Room SQLite keyword/affinity): ${t.retrievalLatencyMs}ms
            | 3. Model Inference TTFT (Time to first chunk):       ${t.ttftMs}ms
            |    Model Inference Total Duration:                    ${t.inferenceDurationMs}ms
            |    Inference Throughput:                              ${t.tokensPerSec} chunks/sec
            | 4. Offline Safety Gate (Sanitization & Checks):       ${t.safetyGateLatencyMs}ms
            | 5. TTS First-Audio Emitted:                           ${t.firstAudioTtsLatencyMs}ms
            |──────────────────────────────────────────────────────────────────
            | TOTAL END-TO-END VOICE-TO-VOICE LATENCY:              ${t.totalVoiceToVoiceMs}ms (${String.format("%.2f", t.totalVoiceToVoiceMs / 1000.0)}s)
            | STRICT 6-SECOND PS REQUIREMENT:                       $slaTag
            | Cold Start Status:                                    ${if (t.isColdStart) "COLD START" else "WARM START"}
            |══════════════════════════════════════════════════════════════════
        """.trimMargin())
    }

    fun stopSpeaking() {
        ttsEngine?.stop()
        _isSpeaking.value = false
        if (_assistantState.value == AssistantState.SPEAKING) {
            _assistantState.value = AssistantState.READY
        }
    }

    fun pauseSpeaking() {
        ttsEngine?.stop()
        _isSpeaking.value = false
        if (_assistantState.value == AssistantState.SPEAKING) {
            _assistantState.value = AssistantState.READY
        }
    }

    fun replay() {
        val text = lastSpokenText ?: return
        speak(text, lastSpokenLanguage)
    }

    fun toggleMute(): Boolean {
        val newMuted = !_isMuted.value
        _isMuted.value = newMuted
        if (newMuted) {
            stopSpeaking()
        }
        return newMuted
    }

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
