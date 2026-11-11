package com.bolkisan.farmer.domain.ivr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.bolkisan.farmer.domain.voice.VoiceAssistantManager
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Speech Recognition and Text-To-Speech interface for Bol Kisan Offline IVR.
 * Provides on-device voice I/O with zero cloud dependency.
 */
interface OfflineSpeechService {
    fun speak(text: String, language: String, onStart: (() -> Unit)? = null, onDone: (() -> Unit)? = null)
    fun stopSpeaking()
    fun startListening(language: String, onSpeechRecognized: (String) -> Unit, onError: ((String) -> Unit)? = null)
    fun stopListening()
    fun isSpeaking(): Boolean
    fun release()
}

/**
 * Adapter bridging [VoiceAssistantManager] to [OfflineSpeechService].
 */
class VoiceAssistantSpeechAdapter(
    private val voiceAssistantManager: VoiceAssistantManager
) : OfflineSpeechService {

    override fun speak(text: String, language: String, onStart: (() -> Unit)?, onDone: (() -> Unit)?) {
        voiceAssistantManager.speak(text, language)
        onStart?.invoke()
    }

    override fun stopSpeaking() {
        voiceAssistantManager.stopSpeaking()
    }

    override fun startListening(language: String, onSpeechRecognized: (String) -> Unit, onError: ((String) -> Unit)?) {
        voiceAssistantManager.startListening(language, isOnline = false)
    }

    override fun stopListening() {
        voiceAssistantManager.stopListening()
    }

    override fun isSpeaking(): Boolean = voiceAssistantManager.isSpeaking.value

    override fun release() {
        voiceAssistantManager.release()
    }
}

/**
 * Standalone offline Android TextToSpeech & SpeechRecognizer implementation.
 */
class AndroidOfflineSpeechService(
    private val context: Context
) : OfflineSpeechService {

    private val tag = "OfflineSpeechService"
    private var mainHandler: Handler? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var activeSpeechCallback: ((String) -> Unit)? = null
    private var activeErrorCallback: ((String) -> Unit)? = null
    private val isSpeakingFlag = AtomicBoolean(false)

    init {
        try {
            val looper = Looper.getMainLooper()
            if (looper != null) {
                mainHandler = Handler(looper)
                mainHandler?.post {
                    initTts()
                    initSpeechRecognizer()
                }
            }
        } catch (t: Throwable) {
            Log.w(tag, "Looper not available in non-Android environment: ${t.message}")
        }
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                textToSpeech?.setSpeechRate(0.92f)
            } else {
                Log.w(tag, "Offline TTS init failed: $status")
            }
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        activeErrorCallback?.invoke("Speech recognition error code: $error")
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim()
                        if (!text.isNullOrBlank()) {
                            activeSpeechCallback?.invoke(text)
                        } else {
                            activeErrorCallback?.invoke("No voice matches found")
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    override fun speak(text: String, language: String, onStart: (() -> Unit)?, onDone: (() -> Unit)?) {
        val handler = mainHandler
        if (handler == null) {
            onStart?.invoke()
            onDone?.invoke()
            return
        }
        handler.post {
            val tts = textToSpeech ?: run {
                onStart?.invoke()
                onDone?.invoke()
                return@post
            }
            val targetLocale = when (language) {
                "te" -> Locale("te", "IN")
                "hi" -> Locale("hi", "IN")
                else -> Locale.ENGLISH
            }

            try {
                tts.language = targetLocale
            } catch (e: Exception) {
                Log.w(tag, "Failed to set locale $targetLocale", e)
            }

            val utteranceId = "IVR_TTS_${System.currentTimeMillis()}"
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    isSpeakingFlag.set(true)
                    onStart?.invoke()
                }

                override fun onDone(id: String?) {
                    isSpeakingFlag.set(false)
                    onDone?.invoke()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    isSpeakingFlag.set(false)
                    onDone?.invoke()
                }

                override fun onError(id: String?, errorCode: Int) {
                    isSpeakingFlag.set(false)
                    onDone?.invoke()
                }
            })

            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    override fun stopSpeaking() {
        mainHandler?.post {
            textToSpeech?.stop()
            isSpeakingFlag.set(false)
        } ?: run {
            isSpeakingFlag.set(false)
        }
    }

    override fun startListening(language: String, onSpeechRecognized: (String) -> Unit, onError: ((String) -> Unit)?) {
        activeSpeechCallback = onSpeechRecognized
        activeErrorCallback = onError

        val handler = mainHandler ?: return
        handler.post {
            val recognizer = speechRecognizer ?: return@post
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                val langTag = when (language) {
                    "te" -> "te-IN"
                    "hi" -> "hi-IN"
                    else -> "en-IN"
                }
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            }
            try {
                recognizer.startListening(intent)
            } catch (e: Exception) {
                Log.e(tag, "Failed to start listening", e)
                onError?.invoke(e.message ?: "Recognition failed")
            }
        }
    }

    override fun stopListening() {
        mainHandler?.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(tag, "Failed to stop listening", e)
            }
        }
    }

    override fun isSpeaking(): Boolean = isSpeakingFlag.get()

    override fun release() {
        mainHandler?.post {
            try {
                textToSpeech?.stop()
                textToSpeech?.shutdown()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(tag, "Error releasing speech resources", e)
            }
        }
    }
}

/**
 * Test implementation of [OfflineSpeechService] for deterministic testing.
 */
class TestOfflineSpeechService : OfflineSpeechService {
    val spokenPrompts = mutableListOf<Pair<String, String>>()
    var isCurrentlySpeaking = false
    var speechCallback: ((String) -> Unit)? = null
    var errorCallback: ((String) -> Unit)? = null

    override fun speak(text: String, language: String, onStart: (() -> Unit)?, onDone: (() -> Unit)?) {
        spokenPrompts.add(text to language)
        isCurrentlySpeaking = true
        onStart?.invoke()
        onDone?.invoke()
        isCurrentlySpeaking = false
    }

    override fun stopSpeaking() {
        isCurrentlySpeaking = false
    }

    override fun startListening(language: String, onSpeechRecognized: (String) -> Unit, onError: ((String) -> Unit)?) {
        speechCallback = onSpeechRecognized
        errorCallback = onError
    }

    override fun stopListening() {
        speechCallback = null
        errorCallback = null
    }

    fun triggerVoiceInput(speechText: String) {
        speechCallback?.invoke(speechText)
    }

    override fun isSpeaking(): Boolean = isCurrentlySpeaking

    override fun release() {
        spokenPrompts.clear()
        speechCallback = null
        errorCallback = null
        isCurrentlySpeaking = false
    }
}
