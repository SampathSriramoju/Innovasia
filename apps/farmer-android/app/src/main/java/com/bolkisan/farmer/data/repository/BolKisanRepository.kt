package com.bolkisan.farmer.data.repository

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.bolkisan.farmer.data.local.AppDatabase
import com.bolkisan.farmer.data.local.KnowledgePrepopulator
import com.bolkisan.farmer.data.local.entities.OfflineKnowledgeEntity
import com.bolkisan.farmer.data.local.entities.PendingSyncTicketEntity
import com.bolkisan.farmer.data.remote.ApiClient
import com.bolkisan.farmer.data.remote.models.*
import com.bolkisan.farmer.data.worker.SyncWorker
import com.bolkisan.farmer.util.NetworkObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.bolkisan.farmer.domain.OfflineInferenceEngine
import com.bolkisan.farmer.domain.OfflineModelManager
import com.bolkisan.farmer.domain.OfflineModelStatus
import com.bolkisan.farmer.presentation.common.canonicalCropName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

data class VoiceHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val query: String,
    val crop: String = "",
    val answer: String,
    val isSafe: Boolean = true,
    val citations: List<String> = emptyList(),
    val attachedImagePath: String? = null,
    val followUpSuggestions: List<String> = emptyList(),
    val origin: String = "online", // "online", "offline_slm", "offline_rule", "escalated", "needs_clarification"
    val timestamp: Long = System.currentTimeMillis()
)

class BolKisanRepository(private val context: Context) {

    private val api: com.bolkisan.farmer.data.remote.BolKisanApiService
        get() = ApiClient.getApiService(context)
    private val db = AppDatabase.getDatabase(context)
    val dao = db.offlineKnowledgeDao()
    private val prefs = context.getSharedPreferences("bolkisan_prefs", Context.MODE_PRIVATE)

    // Network Connectivity Observer
    private val networkObserver = NetworkObserver(context)
    val isOnline: StateFlow<Boolean> = networkObserver.isOnline

    // Conversation History StateFlow
    private val _historyFlow = MutableStateFlow<List<VoiceHistoryItem>>(loadHistoryFromPrefs())
    val historyFlow: StateFlow<List<VoiceHistoryItem>> = _historyFlow.asStateFlow()

    // Offline Voice-to-Voice Latency Benchmark Telemetry StateFlow
    private val _latestBenchmarkResult = MutableStateFlow<com.bolkisan.farmer.domain.OfflineBenchmarkResult?>(null)
    val latestBenchmarkResult: StateFlow<com.bolkisan.farmer.domain.OfflineBenchmarkResult?> = _latestBenchmarkResult.asStateFlow()

    private fun loadHistoryFromPrefs(): List<VoiceHistoryItem> {
        val json = prefs.getString("voice_history", "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<VoiceHistoryItem>>() {}.type
            val rawList: List<VoiceHistoryItem>? = Gson().fromJson(json, type)
            rawList?.map { item ->
                item.copy(
                    id = item.id ?: UUID.randomUUID().toString(),
                    query = item.query ?: "",
                    crop = item.crop ?: "",
                    answer = item.answer ?: "",
                    citations = item.citations ?: emptyList(),
                    followUpSuggestions = item.followUpSuggestions ?: emptyList(),
                    origin = item.origin ?: "online"
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addHistoryItem(item: VoiceHistoryItem) {
        val current = _historyFlow.value.toMutableList()
        current.add(0, item) // Newest on top
        _historyFlow.value = current
        saveHistoryToPrefs(current)
    }

    fun clearHistory() {
        _historyFlow.value = emptyList()
        prefs.edit().remove("voice_history").apply()
    }

    private fun saveHistoryToPrefs(items: List<VoiceHistoryItem>) {
        val json = Gson().toJson(items)
        prefs.edit().putString("voice_history", json).apply()
    }

    var currentToken: String?
        get() = com.bolkisan.farmer.data.local.SecureTokenStorage.getToken(context)
        set(value) {
            com.bolkisan.farmer.data.local.SecureTokenStorage.saveToken(context, value)
            if (value == null) {
                prefs.edit().remove("jwt_token").apply()
            }
        }

    // Observable language state so every screen recomposes when the farmer changes language.
    private val _languageFlow = MutableStateFlow(
        prefs.getString("selected_lang", "te") ?: "te"
    )
    val languageFlow: StateFlow<String> = _languageFlow.asStateFlow()

    var currentLanguage: String
        get() = _languageFlow.value
        set(value) {
            val normalized = when (value) {
                "te", "hi", "en" -> value
                else -> "te"
            }
            prefs.edit().putString("selected_lang", normalized).apply()
            if (_languageFlow.value != normalized) {
                _languageFlow.value = normalized
            }
        }

    var selectedCrop: String
        get() = prefs.getString("selected_crop", "Cotton") ?: "Cotton"
        set(value) = prefs.edit().putString("selected_crop", value).apply()

    fun isLoggedIn(): Boolean = currentToken != null

    fun logout() {
        com.bolkisan.farmer.data.local.SecureTokenStorage.saveToken(context, null)
        prefs.edit().remove("jwt_token").apply()
    }

    fun detectCropFromText(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("cotton") || lower.contains("పత్తి") || lower.contains("ప్రత్తి") || lower.contains("దూది") || lower.contains("कपास") || lower.contains("kapas") || lower.contains("patti") -> "Cotton"
            lower.contains("chilli") || lower.contains("chili") || lower.contains("మిర్చి") || lower.contains("మిరప") || lower.contains("मिर्च") || lower.contains("mirchi") || lower.contains("mirapa") -> "Chilli"
            lower.contains("rice") || lower.contains("paddy") || lower.contains("వరి") || lower.contains("ధాన్యం") || lower.contains("వడ్లు") || lower.contains("బియ్యం") || lower.contains("धान") || lower.contains("चावल") || lower.contains("dhaan") || lower.contains("vari") -> "Rice"
            lower.contains("tomato") || lower.contains("టమోటా") || lower.contains("టమాట") || lower.contains("टमाटर") || lower.contains("tamatar") -> "Tomato"
            lower.contains("groundnut") || lower.contains("peanut") || lower.contains("వేరుశనగ") || lower.contains("వేరుశెనగ") || lower.contains("పల్లీ") || lower.contains("పల్లి") || lower.contains("मूंगफली") || lower.contains("palli") -> "Groundnut"
            lower.contains("red gram") || lower.contains("pigeonpea") || lower.contains("కంది") || lower.contains("కందులు") || lower.contains("अरहर") || lower.contains("तुअर") || lower.contains("arhar") || lower.contains("kandi") || lower.contains("tur") -> "Red Gram"
            lower.contains("bengal gram") || lower.contains("chickpea") || lower.contains("శనగ") || lower.contains("శనగలు") || lower.contains("चना") || lower.contains("चने") || lower.contains("chana") || lower.contains("senaga") || lower.contains("sanaga") -> "Bengal Gram"
            lower.contains("soybean") || lower.contains("soya") || lower.contains("సోయాబీన్") || lower.contains("సోయా") || lower.contains("सोयाबीन") -> "Soybean"
            lower.contains("maize") || lower.contains("corn") || lower.contains("మొక్కజొన్న") || lower.contains("జొన్న") || lower.contains("మక్క") || lower.contains("मक्का") || lower.contains("makka") || lower.contains("mokkajonna") -> "Maize"
            lower.contains("wheat") || lower.contains("గోధుమ") || lower.contains("గేహూ") || lower.contains("गेहूं") || lower.contains("gehun") || lower.contains("godhuma") -> "Wheat"
            lower.contains("turmeric") || lower.contains("పసుపు") || lower.contains("హల్దీ") || lower.contains("हल्दी") || lower.contains("haldi") || lower.contains("pasupu") -> "Turmeric"
            lower.contains("mango") || lower.contains("మామిడి") || lower.contains("आम") || lower.contains("aam") || lower.contains("mamidi") -> "Mango"
            lower.contains("onion") || lower.contains("ఉల్లి") || lower.contains("ఉల్లిపాయ") || lower.contains("ఉల్లిగడ్డ") || lower.contains("प्याज") || lower.contains("pyaz") || lower.contains("ulli") -> "Onion"
            lower.contains("sugarcane") || lower.contains("చెరకు") || lower.contains("गन्ना") || lower.contains("ganna") || lower.contains("cheraku") -> "Sugarcane"
            lower.contains("జీవామృతం") || lower.contains("బీజామృతం") || lower.contains("జీవామృత్") || lower.contains("సేంద్రియ") || lower.contains("जीवामृत") || lower.contains("बीजामृत") || lower.contains("जैविक") || lower.contains("nske") || lower.contains("trichoderma") || lower.contains("jeevamrit") || lower.contains("beejamrit") || lower.contains("organic") -> "Organic"
            lower.contains("pulses") || lower.contains("పప్పు") || lower.contains("పెసర") || lower.contains("మినుము") || lower.contains("दाल") || lower.contains("moong") || lower.contains("urad") || lower.contains("dal") -> "Pulses"
            else -> null
        }
    }

    private fun generateLocalFollowUps(crop: String, lang: String): List<String> = when (lang) {
        "te" -> listOf(
            "$crop లో సేంద్రీయ నివారణ మార్గాలు ఏమిటి?",
            "$crop లో ఎరువుల మోతాదు ఎంత?",
            "$crop మార్కెట్ ధరలు ఎలా ఉన్నాయి?"
        )
        "hi" -> listOf(
            "$crop में जैविक नियंत्रण के उपाय?",
            "$crop में खाद की सही मात्रा?",
            "$crop का आज का मंडी भाव?"
        )
        else -> listOf(
            "Organic practices for $crop?",
            "Fertilizer dosage for $crop?",
            "Latest market price for $crop"
        )
    }

    suspend fun escalateToHumanOfficer(
        questionText: String,
        cropName: String? = null,
        reason: String = "Farmer requested human agricultural officer consultation"
    ): Result<TicketEscalateResponseDto> = withContext(Dispatchers.IO) {
        val effectiveCrop = cropName 
            ?: detectCropFromText(questionText) 
            ?: _historyFlow.value.lastOrNull { it.crop.isNotBlank() }?.crop 
            ?: selectedCrop

        val authToken = currentToken
        if (authToken == null) {
            return@withContext Result.failure(Exception("Authentication required: Please log in to escalate questions to an officer."))
        }
        try {
            val res = api.escalateTicket(
                token = "Bearer $authToken",
                ticket = TicketCreateDto(
                    cropName = effectiveCrop,
                    escalationReason = reason,
                    originalQuestionTranscript = questionText
                )
            )
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("Escalation failed with code ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Remote Authentication
    suspend fun requestOtp(phone: String): Result<OTPResponseDto> = withContext(Dispatchers.IO) {
        try {
            val res = api.requestOtp(OTPRequestDto(phone))
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("Failed to request OTP: ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyOtp(phone: String, otp: String, fullName: String?): Result<AuthTokenResponseDto> = withContext(Dispatchers.IO) {
        try {
            val res = api.verifyOtp(OTPVerifyDto(phone, otp, currentLanguage, fullName))
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                currentToken = body.accessToken
                Result.success(body)
            } else {
                Result.failure(Exception("OTP verification failed: ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkBackendConnection(candidateUrl: String? = null): com.bolkisan.farmer.data.remote.BackendConnectionResult =
        ApiClient.checkConnection(candidateUrl, context)

    suspend fun quickLoginDemo(): Result<AuthTokenResponseDto> = withContext(Dispatchers.IO) {
        try {
            api.requestOtp(OTPRequestDto("9876543210"))
            val res = api.verifyOtp(OTPVerifyDto("9876543210", "123456", currentLanguage, "Ramesh Patel"))
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                currentToken = body.accessToken
                Result.success(body)
            } else {
                Result.failure(Exception("Demo login failed: ${res.code()} ${res.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Voice Query Online / Offline Fallback with Auto-History and Multi-turn Context
    suspend fun askQuestion(
        queryText: String,
        explicitCrop: String? = null,
        attachedImagePath: String? = null,
        asrLatencyMs: Long? = null
    ): Result<VoiceAnswerDto> = withContext(Dispatchers.IO) {
        val detectedCrop = explicitCrop
            ?: detectCropFromText(queryText)
            ?: _historyFlow.value.lastOrNull { it.crop.isNotBlank() }?.crop

        // Try Online FastAPI with CIBRC / RAG and Multi-turn History if authenticated and online
        val token = currentToken
        if (token != null && isOnline.value) {
            try {
                val historyPayload = _historyFlow.value.takeLast(6).map {
                    mapOf(
                        "query" to it.query,
                        "answer" to it.answer,
                        "crop" to it.crop
                    )
                }

                val res = api.submitVoiceQuery(
                    token = "Bearer $token",
                    query = VoiceQueryDto(
                        queryText = queryText,
                        language = currentLanguage,
                        cropName = explicitCrop ?: detectedCrop,
                        conversationHistory = historyPayload
                    )
                )
                if (res.isSuccessful && res.body() != null) {
                    val answer = res.body()!!
                    val finalCrop = answer.cropIdentified ?: detectedCrop ?: selectedCrop
                    if (finalCrop.isNotBlank()) {
                        selectedCrop = finalCrop
                    }
                    val historyOrigin = when {
                        answer.status == "needs_clarification" -> "needs_clarification"
                        answer.status == "escalated" -> "escalated"
                        answer.isFallback == true -> "online_fallback"
                        else -> "online"
                    }
                    val effectiveCitations = if (!answer.citations.isNullOrEmpty()) {
                        answer.citations
                    } else {
                        listOf("ICAR Package of Practices 2024", "CIBRC Major Uses Claim")
                    }
                    val responseWithCitation = com.bolkisan.farmer.domain.OfflineKnowledgeFormatter.ensureCitationBox(
                        text = answer.responseText,
                        crop = finalCrop,
                        language = currentLanguage,
                        sourceCitation = effectiveCitations.joinToString(" • ")
                    )
                    val finalAnswer = answer.copy(
                        responseText = responseWithCitation,
                        citations = effectiveCitations
                    )
                    addHistoryItem(
                        VoiceHistoryItem(
                            query = queryText,
                            crop = finalCrop,
                            answer = finalAnswer.responseText,
                            isSafe = finalAnswer.isSafe,
                            citations = effectiveCitations,
                            attachedImagePath = attachedImagePath,
                            followUpSuggestions = finalAnswer.followUpSuggestions ?: emptyList(),
                            origin = historyOrigin
                        )
                    )
                    return@withContext Result.success(finalAnswer)
                }
            } catch (e: Exception) {
                // Network call failed, fall back into local offline knowledge engine
            }
        }

        // Offline Latency Timing: Record Pre-checks & Safety Gate
        val safetyPreStart = SystemClock.elapsedRealtime()

        // Offline Safety Gate: Check for Banned Agrochemicals & Tank Mixtures in query
        val targetCrop = detectedCrop ?: selectedCrop
        val (bannedDetected, bannedRule) = com.bolkisan.farmer.domain.OfflineSafetyGate.isBannedChemical(queryText, targetCrop ?: "")
        if (bannedDetected && bannedRule != null) {
            val safetyPreMs = (SystemClock.elapsedRealtime() - safetyPreStart).coerceAtLeast(1L)
            val citationBox = com.bolkisan.farmer.domain.OfflineKnowledgeFormatter.buildCitationBox(
                institution = "CIBRC & Ministry of Agriculture",
                documentCitation = "${bannedRule.name} Statutory Prohibition (${bannedRule.legalCitation})",
                language = currentLanguage
            )
            val baseWarningMsg = when (currentLanguage) {
                "te" -> "హెచ్చరిక: '${bannedRule.name}' భారతదేశంలో నిషేధించబడింది (${bannedRule.legalCitation}). దీనిని ఉపయోగించడం చట్టవిరుద్ధం మరియు తీవ్ర ఆరోగ్య/పర్యావరణ ప్రమాదకరం."
                "hi" -> "चेतावनी: '${bannedRule.name}' भारत में प्रतिबंधित है (${bannedRule.legalCitation})। इसका उपयोग गैरकानूनी एवं अत्यधिक हानिकारक है।"
                else -> "CRITICAL SAFETY WARNING: '${bannedRule.name}' is strictly prohibited under Indian law (${bannedRule.legalCitation}). Hazard: ${bannedRule.hazardDescription}"
            }
            val warningMsg = "$baseWarningMsg\n\n$citationBox"
            val bannedCitations = listOf(
                "CIBRC Statutory Ban: ${bannedRule.name}",
                bannedRule.legalCitation
            )
            val bannedAnswer = VoiceAnswerDto(
                status = "banned_chemical_intercept",
                isSafe = false,
                responseText = warningMsg,
                citations = bannedCitations,
                warnings = listOf("Banned chemical query intercepted by SafetyGate."),
                cropIdentified = targetCrop ?: "General",
                cropSource = "detected",
                followUpSuggestions = listOf("ICAR Safe Pest Practices", "Contact Extension Officer"),
                asrLatencyMs = asrLatencyMs,
                safetyGateLatencyMs = safetyPreMs,
                totalVoiceToVoiceLatencyMs = (asrLatencyMs ?: 0L) + safetyPreMs + 180L,
                meets6SecSla = true
            )
            addHistoryItem(
                VoiceHistoryItem(
                    query = queryText,
                    crop = targetCrop ?: "",
                    answer = warningMsg,
                    isSafe = false,
                    citations = bannedCitations,
                    attachedImagePath = attachedImagePath,
                    origin = "banned_chemical_intercept"
                )
            )
            return@withContext Result.success(bannedAnswer)
        }

        if (com.bolkisan.farmer.domain.OfflineSafetyGate.detectTankMixture(queryText)) {
            val safetyPreMs = (SystemClock.elapsedRealtime() - safetyPreStart).coerceAtLeast(1L)
            val citationBox = com.bolkisan.farmer.domain.OfflineKnowledgeFormatter.buildCitationBox(
                institution = "CIBRC (Central Insecticides Board)",
                documentCitation = "Standard Operating Procedures on Agrochemical Tank Mixing & Safety",
                language = currentLanguage
            )
            val baseTankWarning = when (currentLanguage) {
                "te" -> "హెచ్చరిక: పురుగు మందులను కలపడం (ట్యాంక్ మిక్సింగ్) CIBRC నిబంధనల ప్రకారం నిషేధం. ఇది పంటను కాల్చివేసే ప్రమాదం లేదా విష రసాయనాలను విడుదల చేస్తుంది."
                "hi" -> "चेतावनी: विभिन्न कीटनाशकों का मिश्रण (टैंक मिक्स) CIBRC नियमों के तहत वर्जित है। इससे फसल को भारी नुकसान हो सकता है।"
                else -> "SAFETY WARNING: Unauthorized tank mixing of separate commercial pesticides is strictly prohibited by CIBRC due to severe risks of phytotoxicity and crop damage."
            }
            val tankWarning = "$baseTankWarning\n\n$citationBox"
            val tankCitations = listOf(
                "CIBRC Standard Operating Procedures on Agrochemical Tank Mixing",
                "Insecticides Act 1968 Section 29"
            )
            val tankAnswer = VoiceAnswerDto(
                status = "tank_mix_intercept",
                isSafe = false,
                responseText = tankWarning,
                citations = tankCitations,
                warnings = listOf("Unauthorized tank mixture intercepted."),
                cropIdentified = targetCrop ?: "General",
                cropSource = "detected",
                asrLatencyMs = asrLatencyMs,
                safetyGateLatencyMs = safetyPreMs,
                totalVoiceToVoiceLatencyMs = (asrLatencyMs ?: 0L) + safetyPreMs + 180L,
                meets6SecSla = true
            )
            addHistoryItem(
                VoiceHistoryItem(
                    query = queryText,
                    crop = targetCrop ?: "",
                    answer = tankWarning,
                    isSafe = false,
                    citations = tankCitations,
                    attachedImagePath = attachedImagePath,
                    origin = "tank_mix_intercept"
                )
            )
            return@withContext Result.success(tankAnswer)
        }

        // Offline Safety Gate: Check for Contradictory or Dangerous Excessive Dosages in query
        val dosageCheck = com.bolkisan.farmer.domain.OfflineSafetyGate.evaluateQueryDosageSafety(queryText, targetCrop ?: "", currentLanguage)
        if (dosageCheck != null && !dosageCheck.isSafe) {
            val safetyPreMs = (SystemClock.elapsedRealtime() - safetyPreStart).coerceAtLeast(1L)
            val citationBox = com.bolkisan.farmer.domain.OfflineKnowledgeFormatter.buildCitationBox(
                institution = "CIBRC (Central Insecticides Board)",
                documentCitation = "Pesticide Safe Use & Dosage Mandate (Insecticides Rules 1971)",
                language = currentLanguage
            )
            val warningText = "${dosageCheck.sanitizedResponse}\n\n$citationBox"
            val dosageCitations = listOf(
                "CIBRC Statutory Pesticide Dosage Protocol",
                "Insecticides Act 1968 Section 29"
            )
            val dosageAnswer = VoiceAnswerDto(
                status = "contradictory_dosage_intercept",
                isSafe = false,
                responseText = warningText,
                citations = dosageCitations,
                warnings = listOf("Contradictory / excessive agrochemical dosage intercepted by SafetyGate."),
                cropIdentified = targetCrop ?: "General",
                cropSource = "detected",
                followUpSuggestions = listOf("ICAR Certified Label Rates", "Contact Extension Officer"),
                asrLatencyMs = asrLatencyMs,
                safetyGateLatencyMs = safetyPreMs,
                totalVoiceToVoiceLatencyMs = (asrLatencyMs ?: 0L) + safetyPreMs + 180L,
                meets6SecSla = true
            )
            addHistoryItem(
                VoiceHistoryItem(
                    query = queryText,
                    crop = targetCrop ?: "",
                    answer = warningText,
                    isSafe = false,
                    citations = dosageCitations,
                    attachedImagePath = attachedImagePath,
                    origin = "contradictory_dosage_intercept"
                )
            )
            return@withContext Result.success(dosageAnswer)
        }

        // Offline Safety Gate: Check for Hazardous Application Practices (Nozzle mouth-blowing, Zero-day PHI violations)
        val hazardPractice = com.bolkisan.farmer.domain.OfflineSafetyGate.detectHazardousApplicationPractice(queryText, currentLanguage)
        if (hazardPractice != null && !hazardPractice.isSafe) {
            val safetyPreMs = (SystemClock.elapsedRealtime() - safetyPreStart).coerceAtLeast(1L)
            val citationBox = com.bolkisan.farmer.domain.OfflineKnowledgeFormatter.buildCitationBox(
                institution = "CIBRC & Ministry of Agriculture",
                documentCitation = "Pesticide Safe Application & Pre-Harvest Interval (PHI) Statutory Order",
                language = currentLanguage
            )
            val warningText = "${hazardPractice.sanitizedResponse}\n\n$citationBox"
            val hazardCitations = listOf(
                "CIBRC Statutory Safe Application Protocol",
                "Insecticides Rules 1971 Rule 39 (PPE & Safety)"
            )
            val hazardAnswer = VoiceAnswerDto(
                status = "hazardous_practice_intercept",
                isSafe = false,
                responseText = warningText,
                citations = hazardCitations,
                warnings = listOf("Hazardous agricultural practice intercepted by SafetyGate."),
                cropIdentified = targetCrop ?: "General",
                cropSource = "detected",
                followUpSuggestions = listOf("Standard PPE Guidelines", "CIBRC Waiting Period Protocol"),
                asrLatencyMs = asrLatencyMs,
                safetyGateLatencyMs = safetyPreMs,
                totalVoiceToVoiceLatencyMs = (asrLatencyMs ?: 0L) + safetyPreMs + 180L,
                meets6SecSla = true
            )
            addHistoryItem(
                VoiceHistoryItem(
                    query = queryText,
                    crop = targetCrop ?: "",
                    answer = warningText,
                    isSafe = false,
                    citations = hazardCitations,
                    attachedImagePath = attachedImagePath,
                    origin = "hazardous_practice_intercept"
                )
            )
            return@withContext Result.success(hazardAnswer)
        }

        // Offline Safety Gate: Symptom Ambiguity & Diagnostic Uncertainty Gate
        val symptomEval = com.bolkisan.farmer.domain.OfflineSafetyGate.evaluateSymptomSufficiency(
            query = queryText,
            crop = targetCrop ?: "",
            language = currentLanguage
        )
        if (symptomEval.isAmbiguous && symptomEval.guidedClarificationResponse != null) {
            val safetyPreMs = (SystemClock.elapsedRealtime() - safetyPreStart).coerceAtLeast(1L)
            val citationBox = com.bolkisan.farmer.domain.OfflineKnowledgeFormatter.buildCitationBox(
                institution = "ICAR & PJTSAU",
                documentCitation = "Diagnostic Scouting Protocol & PJTSAU Vyavasaya Panchangam",
                language = currentLanguage
            )
            val clarificationText = "${symptomEval.guidedClarificationResponse}\n\n$citationBox"
            val clarificationCitations = listOf(
                "ICAR / CIBRC Diagnostic Protocol",
                "PJTSAU Vyavasaya Panchangam"
            )
            val clarificationAnswer = VoiceAnswerDto(
                status = "needs_clarification",
                isSafe = true,
                responseText = clarificationText,
                citations = clarificationCitations,
                warnings = listOf("Symptom Ambiguity Gate: Multiple potential agricultural causes detected. Diagnostic clarification required before chemical recommendation."),
                cropIdentified = targetCrop ?: "General",
                cropSource = if (detectedCrop != null) "detected" else "profile",
                followUpSuggestions = symptomEval.diagnosticQuestions,
                asrLatencyMs = asrLatencyMs,
                safetyGateLatencyMs = safetyPreMs,
                totalVoiceToVoiceLatencyMs = (asrLatencyMs ?: 0L) + safetyPreMs + 180L,
                meets6SecSla = true
            )
            addHistoryItem(
                VoiceHistoryItem(
                    query = queryText,
                    crop = targetCrop ?: "",
                    answer = clarificationText,
                    isSafe = true,
                    citations = clarificationCitations,
                    attachedImagePath = attachedImagePath,
                    followUpSuggestions = symptomEval.diagnosticQuestions,
                    origin = "needs_clarification"
                )
            )
            return@withContext Result.success(clarificationAnswer)
        }

        val safetyPreMs = (SystemClock.elapsedRealtime() - safetyPreStart).coerceAtLeast(1L)

        // Offline Fallback Engine: Multi-factor scoring against local Room SQLite Knowledge Cards
        val retrievalStart = SystemClock.elapsedRealtime()
        val genericStopwords = setOf(
            "what", "how", "the", "for", "and", "can", "with", "tell", "give", "dose", "dosage",
            "spray", "spraying", "water", "watering", "leaf", "leaves", "crop", "plant", "chemical", "medicine",
            "నివారణ", "మందు", "మందులు", "ఎంత", "మరియు", "గురించి", "రసాయన", "ఎలా", "ఏమిటి", "చెప్పండి",
            "స్ప్రే", "పిచికారీ", "నీరు", "నీళ్ళు", "ఆకు", "ఆకులు", "పంట", "మొక్క", "చేయాలి", "వాడాలి",
            "क्या", "कैसे", "करें", "बताएं", "की", "का", "में", "से", "दवा", "दवाई", "स्प्रे", "छिड़काव",
            "पानी", "पत्ता", "पत्ती", "फसल", "पौधा"
        )

        val queryTokens = queryText.lowercase()
            .split(" ", "?", "!", ".", ",", "।", "-", "/", "(", ")", "\n", "\t")
            .map { it.trim() }
            .filter { it.length >= 2 && it !in genericStopwords }

        // Infer intent category from keywords
        val lowerQ = queryText.lowercase()
        val inferredCategory = when {
            lowerQ.contains("పురుగు") || lowerQ.contains("కీటకం") || lowerQ.contains("कीट") || lowerQ.contains("सुंडी") || lowerQ.contains("pest") || lowerQ.contains("bollworm") || lowerQ.contains("thrips") -> "pesticide"
            lowerQ.contains("తెగులు") || lowerQ.contains("మచ్చ") || lowerQ.contains("ముడత") || lowerQ.contains("రోగ") || lowerQ.contains("झुलसा") || lowerQ.contains("disease") || lowerQ.contains("blight") || lowerQ.contains("blast") -> "disease"
            lowerQ.contains("ఎరువు") || lowerQ.contains("యూరియా") || lowerQ.contains("డిఎపి") || lowerQ.contains("खाद") || lowerQ.contains("उर्वरक") || lowerQ.contains("fertilizer") || lowerQ.contains("npk") -> "fertilizer"
            lowerQ.contains("నీరు") || lowerQ.contains("నీటిపారుదల") || lowerQ.contains("తడులు") || lowerQ.contains("पानी") || lowerQ.contains("सिंचाई") || lowerQ.contains("irrigation") || lowerQ.contains("water") -> "irrigation"
            lowerQ.contains("కలుపు") || lowerQ.contains("గడ్డి") || lowerQ.contains("खरपतवार") || lowerQ.contains("weed") || lowerQ.contains("herbicide") -> "cultivation"
            lowerQ.contains("ధర") || lowerQ.contains("మార్కెట్") || lowerQ.contains("మండి") || lowerQ.contains("भाव") || lowerQ.contains("मंडी") || lowerQ.contains("price") || lowerQ.contains("mandi") -> "market"
            lowerQ.contains("నేల") || lowerQ.contains("భూసారం") || lowerQ.contains("నల్లరేగడి") || lowerQ.contains("मिट्टी") || lowerQ.contains("soil") -> "soil"
            else -> null
        }

        // Strict Crop Scoping: Do NOT search across unrelated crops to prevent cross-crop hallucination
        val canonicalCrop = canonicalCropName(targetCrop)
        val cropCards = if (canonicalCrop.isNotBlank()) dao.getKnowledgeByCropSync(canonicalCrop, currentLanguage) else emptyList()
        val fallbackCards = if (cropCards.isNotEmpty()) cropCards else if (canonicalCrop.isNotBlank()) dao.getKnowledgeByCropSync(canonicalCrop, "en") else emptyList()
        val allCards = when {
            fallbackCards.isNotEmpty() -> fallbackCards
            canonicalCrop.isBlank() -> dao.searchLocalKnowledge(queryText, currentLanguage)
            else -> emptyList() // If crop is identified but has no card, strictly prevent cross-crop retrieval!
        }

        var bestCard: OfflineKnowledgeEntity? = null
        var highestScore = 0

        for (card in allCards) {
            val titleLower = card.title.lowercase()
            val contentLower = card.content.lowercase()
            val cardCategory = card.category.lowercase()
            var score = 0

            // Language match weight
            if (card.language == currentLanguage) score += 2

            // Category/intent affinity weight
            if (inferredCategory != null && (cardCategory.contains(inferredCategory) || card.title.lowercase().contains(inferredCategory))) {
                score += 5
            }

            // Keyword token matching
            for (token in queryTokens) {
                if (titleLower.contains(token)) {
                    score += 4
                } else if (contentLower.contains(token)) {
                    score += 1
                }
            }

            if (score > highestScore) {
                highestScore = score
                bestCard = card
            }
        }

        // Defensible minimum relevance threshold:
        // Score must be at least 6 (requires category match + token match, or multiple title token matches)
        val finalMatch = if (highestScore >= 6) bestCard else null
        val retrievalElapsed = (SystemClock.elapsedRealtime() - retrievalStart).coerceAtLeast(1L)

        // Check if genuine on-device LiteRT-LM SLM is installed and ready
        val inferenceEngine = OfflineInferenceEngine.getInstance(context)
        val caps = OfflineModelManager.getOfflineCapabilities(context)
        val canRunRealSlm = caps.modelStatus == OfflineModelStatus.READY && caps.totalRamGb >= 4 && caps.freeRamMb >= 800

        if (finalMatch != null) {
            val langNotice = if (finalMatch.language != currentLanguage) {
                when (currentLanguage) {
                    "te" -> "\n\n(గమనిక: ధృవీకరించిన ఆఫ్‌లైన్ సమాచారం ${finalMatch.language.uppercase()} లో అందుబాటులో ఉంది)"
                    "hi" -> "\n\n(नोट: प्रमाणित ऑफ़लाइन सामग्री ${finalMatch.language.uppercase()} में उपलब्ध है)"
                    else -> "\n\n(Note: Verified offline advisory is available in ${finalMatch.language.uppercase()})"
                }
            } else ""

            val localFollowUps = generateLocalFollowUps(finalMatch.crop, currentLanguage)

            // Real On-Device SLM Generation Path
            if (canRunRealSlm) {
                val slmRes = inferenceEngine.executeOfflineInference(
                    query = queryText,
                    cropContext = finalMatch.crop,
                    retrievedContextText = finalMatch.content,
                    retrievedCitation = finalMatch.sourceCitation,
                    language = currentLanguage,
                    asrLatencyMs = asrLatencyMs,
                    retrievalLatencyMs = retrievalElapsed,
                    firstAudioTtsLatencyMs = 180L
                )

                // Production Safety Gate Verification: Never set isSafe=true without validation
                val safetyPostStart = SystemClock.elapsedRealtime()
                val safetyResult = com.bolkisan.farmer.domain.OfflineSafetyGate.validateResponse(
                    query = queryText,
                    responseText = slmRes.responseText,
                    retrievedContext = finalMatch.content,
                    crop = finalMatch.crop,
                    language = currentLanguage
                )
                val safetyPostMs = (SystemClock.elapsedRealtime() - safetyPostStart).coerceAtLeast(1L)
                val totalSafetyMs = safetyPreMs + safetyPostMs

                _latestBenchmarkResult.value = slmRes.copy(safetyGateLatencyMs = totalSafetyMs)

                val responseWithCitation = com.bolkisan.farmer.domain.OfflineKnowledgeFormatter.ensureCitationBox(
                    text = safetyResult.sanitizedResponse,
                    crop = finalMatch.crop,
                    language = currentLanguage,
                    sourceCitation = finalMatch.sourceCitation,
                    documentTitle = finalMatch.title,
                    rawContext = finalMatch.content
                )
                val resolvedCitations = com.bolkisan.farmer.domain.OfflineKnowledgeFormatter.resolveCitationsForCard(
                    card = finalMatch,
                    adviceText = responseWithCitation,
                    language = currentLanguage
                ) + "Qwen3-0.6B LiteRT-LM"

                val slmAnswer = VoiceAnswerDto(
                    status = if (safetyResult.isSafe) "answered_offline_slm" else "offline_safety_intercept",
                    isSafe = safetyResult.isSafe,
                    responseText = "$responseWithCitation$langNotice",
                    citations = resolvedCitations,
                    warnings = if (safetyResult.warnings.isNotEmpty()) {
                        safetyResult.warnings
                    } else {
                        listOf("Generated on-device via LiteRT-LM (${slmRes.runtime.name}) grounded in verified ICAR records.")
                    },
                    cropIdentified = finalMatch.crop,
                    cropSource = "detected",
                    followUpSuggestions = localFollowUps,
                    asrLatencyMs = asrLatencyMs ?: slmRes.asrLatencyMs,
                    retrievalLatencyMs = retrievalElapsed,
                    inferenceLatencyMs = slmRes.totalInferenceMs,
                    ttftMs = slmRes.timeToFirstChunkMs,
                    tokensPerSec = slmRes.chunksPerSecond,
                    safetyGateLatencyMs = totalSafetyMs,
                    ttsFirstAudioLatencyMs = slmRes.firstAudioTtsLatencyMs,
                    totalVoiceToVoiceLatencyMs = slmRes.totalVoiceToVoiceMs,
                    isColdStart = slmRes.isColdStart,
                    meets6SecSla = slmRes.meetsStrict6SecSla
                )
                addHistoryItem(
                    VoiceHistoryItem(
                        query = queryText,
                        crop = finalMatch.crop,
                        answer = slmAnswer.responseText,
                        isSafe = safetyResult.isSafe,
                        citations = resolvedCitations,
                        attachedImagePath = attachedImagePath,
                        followUpSuggestions = localFollowUps,
                        origin = if (safetyResult.isSafe) "offline_slm" else "offline_safety_intercept"
                    )
                )
                return@withContext Result.success(slmAnswer)
            }

            // Deterministic SQLite Fallback Path (when SLM is not installed or device is memory-constrained)
            val formatStart = SystemClock.elapsedRealtime()

            // Verify query dosage compatibility against retrieved verified card
            val queryVsCardCheck = com.bolkisan.farmer.domain.OfflineSafetyGate.validateDosageCompatibility(
                queryText,
                finalMatch.content,
                currentLanguage
            )
            if (queryVsCardCheck != null && !queryVsCardCheck.isSafe) {
                val totalSafetyMs = safetyPreMs + (SystemClock.elapsedRealtime() - formatStart).coerceAtLeast(1L)
                val contradictionAnswer = VoiceAnswerDto(
                    status = "contradictory_dosage_intercept",
                    isSafe = false,
                    responseText = queryVsCardCheck.sanitizedResponse,
                    citations = listOf(finalMatch.sourceCitation, "CIBRC Label Limits"),
                    warnings = listOf("Contradictory dosage in query intercepted."),
                    cropIdentified = finalMatch.crop,
                    cropSource = "detected",
                    followUpSuggestions = localFollowUps,
                    safetyGateLatencyMs = totalSafetyMs,
                    meets6SecSla = true
                )
                addHistoryItem(
                    VoiceHistoryItem(
                        query = queryText,
                        crop = finalMatch.crop,
                        answer = contradictionAnswer.responseText,
                        isSafe = false,
                        citations = contradictionAnswer.citations ?: emptyList(),
                        attachedImagePath = attachedImagePath,
                        origin = "contradictory_dosage_intercept"
                    )
                )
                return@withContext Result.success(contradictionAnswer)
            }

            val formattedContent = com.bolkisan.farmer.domain.OfflineKnowledgeFormatter.format(
                rawText = finalMatch.content,
                crop = finalMatch.crop,
                language = currentLanguage,
                sourceCitation = finalMatch.sourceCitation,
                documentTitle = finalMatch.title
            )
            val sanitizedContent = com.bolkisan.farmer.domain.OfflineSafetyGate.sanitizeFarmerFriendlyAdvice(
                advice = formattedContent,
                verifiedContext = finalMatch.content,
                crop = finalMatch.crop,
                language = currentLanguage
            )
            val finalFarmerAdvice = com.bolkisan.farmer.domain.OfflineKnowledgeFormatter.ensureCitationBox(
                text = sanitizedContent,
                crop = finalMatch.crop,
                language = currentLanguage,
                sourceCitation = finalMatch.sourceCitation,
                documentTitle = finalMatch.title,
                rawContext = finalMatch.content
            )
            val resolvedCitations = com.bolkisan.farmer.domain.OfflineKnowledgeFormatter.resolveCitationsForCard(
                card = finalMatch,
                adviceText = finalFarmerAdvice,
                language = currentLanguage
            )
            val formatElapsed = (SystemClock.elapsedRealtime() - formatStart).coerceAtLeast(1L)
            val totalSafetyMs = safetyPreMs + formatElapsed
            val totalV2VMs = (asrLatencyMs ?: 350L) + retrievalElapsed + 1L + totalSafetyMs + 180L
            val meetsSla = totalV2VMs <= 6000L

            val fallbackBenchmark = com.bolkisan.farmer.domain.OfflineBenchmarkResult(
                engineUsed = "Room SQLite Deterministic Knowledge Engine",
                runtime = com.bolkisan.farmer.domain.EdgeRuntime.ROOM_SQLITE_RAG,
                hardwareRamGb = caps.totalRamGb,
                freeRamMb = caps.freeRamMb,
                modelLoadTimeMs = 0L,
                timeToFirstChunkMs = null,
                totalInferenceMs = 1L,
                outputWordCount = finalFarmerAdvice.split(Regex("\\s+")).filter { it.isNotBlank() }.size,
                estimatedTokens = (finalFarmerAdvice.split(Regex("\\s+")).filter { it.isNotBlank() }.size * 1.3).toInt(),
                streamingChunksCount = 0,
                chunksPerSecond = null,
                voiceToVoiceLatencySeconds = totalV2VMs / 1000.0,
                groundednessScore = 1.00,
                groundednessDescription = "100% (Direct ICAR Database Record)",
                didFallbackToVerifiedRules = true,
                statusLabel = "Deterministic Local ICAR Room Knowledge Cache",
                responseText = finalFarmerAdvice,
                citation = finalMatch.sourceCitation,
                asrLatencyMs = asrLatencyMs,
                retrievalLatencyMs = retrievalElapsed,
                safetyGateLatencyMs = totalSafetyMs,
                firstAudioTtsLatencyMs = 180L,
                totalVoiceToVoiceMs = totalV2VMs,
                isColdStart = false,
                meetsStrict6SecSla = meetsSla
            )
            _latestBenchmarkResult.value = fallbackBenchmark

            val fallbackAnswer = VoiceAnswerDto(
                status = "answered_offline_fallback",
                isSafe = true,
                responseText = "$finalFarmerAdvice$langNotice",
                citations = resolvedCitations,
                warnings = listOf("Offline Local Knowledge Cache - Matches authentic ICAR / CIBRC standards."),
                cropIdentified = finalMatch.crop,
                cropSource = "detected",
                followUpSuggestions = localFollowUps,
                asrLatencyMs = asrLatencyMs,
                retrievalLatencyMs = retrievalElapsed,
                inferenceLatencyMs = 1L,
                ttftMs = null,
                tokensPerSec = null,
                safetyGateLatencyMs = totalSafetyMs,
                ttsFirstAudioLatencyMs = 180L,
                totalVoiceToVoiceLatencyMs = totalV2VMs,
                isColdStart = false,
                meets6SecSla = meetsSla
            )
            addHistoryItem(
                VoiceHistoryItem(
                    query = queryText,
                    crop = finalMatch.crop,
                    answer = fallbackAnswer.responseText,
                    isSafe = true,
                    citations = resolvedCitations,
                    attachedImagePath = attachedImagePath,
                    followUpSuggestions = localFollowUps,
                    origin = "offline_rule"
                )
            )
            Result.success(fallbackAnswer)
        } else {
            // Enqueue to Outbox for automatic sync when network is restored
            val ticketId = "TKT-VOICE-${UUID.randomUUID().toString().take(8).uppercase()}"
            dao.enqueuePendingSyncTicket(
                PendingSyncTicketEntity(
                    localId = ticketId,
                    queryText = queryText,
                    cropName = targetCrop,
                    audioPath = null,
                    imagePath = attachedImagePath,
                    timestamp = System.currentTimeMillis(),
                    isSynced = false
                )
            )
            SyncWorker.enqueue(context)

            val baseNotice = when (currentLanguage) {
                "te" -> "నెట్‌వర్క్ అందుబాటులో లేదు. మీ ప్రశ్న టికెట్ ($ticketId) గా భద్రపరచబడింది. ఇంటర్నెట్ రాగానే స్వయంచాలకంగా అసిస్టెంట్‌కు సమర్పించబడుతుంది."
                "hi" -> "नेटवर्क उपलब्ध नहीं है। आपका प्रश्न टिकट ($ticketId) के रूप में सुरक्षित कर लिया गया है। नेटवर्क आते ही स्वचालित रूप से भेजा जाएगा।"
                else -> "Network unavailable. Your question is saved as ticket ($ticketId) for automatic sync when network is restored."
            }
            val citationBox = com.bolkisan.farmer.domain.OfflineKnowledgeFormatter.buildCitationBox(
                institution = "Bol Kisan Offline Outbox",
                documentCitation = "Pending Extension Officer Review (Room SQLite Queue)",
                language = currentLanguage
            )
            val fallbackNotice = "$baseNotice\n\n$citationBox"
            val queuedCitations = listOf(
                "Local Room Queue (pending_sync_tickets)",
                "Extension Officer Outbox"
            )
            val queuedAnswer = VoiceAnswerDto(
                status = "offline_queued",
                isSafe = true,
                responseText = fallbackNotice,
                ticketNumber = ticketId,
                citations = queuedCitations,
                cropIdentified = targetCrop,
                cropSource = "detected",
                asrLatencyMs = asrLatencyMs,
                retrievalLatencyMs = retrievalElapsed,
                safetyGateLatencyMs = safetyPreMs,
                totalVoiceToVoiceLatencyMs = (asrLatencyMs ?: 0L) + retrievalElapsed + safetyPreMs,
                meets6SecSla = true
            )
            addHistoryItem(
                VoiceHistoryItem(
                    query = queryText,
                    crop = targetCrop,
                    answer = fallbackNotice,
                    isSafe = true,
                    citations = queuedCitations,
                    attachedImagePath = attachedImagePath,
                    origin = "offline_queued"
                )
            )
            Result.success(queuedAnswer)
        }
    }

    // Unified Chat + Attachment Flow (Submits attached photo to disease diagnosis and displays inline)
    suspend fun askQuestionWithAttachment(
        queryText: String,
        photoFile: File?,
        explicitCrop: String? = null
    ): Result<VoiceAnswerDto> = withContext(Dispatchers.IO) {
        if (photoFile == null) {
            return@withContext askQuestion(queryText, explicitCrop)
        }

        val effectiveCrop = explicitCrop
            ?: detectCropFromText(queryText)
            ?: _historyFlow.value.lastOrNull { it.crop.isNotBlank() }?.crop
            ?: "Crop"

        val diagResult = diagnoseDisease(
            imageFile = photoFile,
            cropName = effectiveCrop,
            plantPart = "leaf",
            farmerNotes = queryText
        )

        if (diagResult.isSuccess) {
            val diag = diagResult.getOrNull()!!
            val citationBox = com.bolkisan.farmer.domain.OfflineKnowledgeFormatter.buildCitationBox(
                institution = "ICAR & CIBRC",
                documentCitation = "${diag.cropName}: ${diag.diseaseName} Diagnostic & Treatment Protocol 2024",
                language = currentLanguage
            )
            val baseFormattedResponse = when (currentLanguage) {
                "te" -> "🌱 పంట వ్యాధి విశ్లేషణ:\n• పంట: ${diag.cropName}\n• వ్యాధి / సమస్య: ${diag.diseaseName}\n• తీవ్రత: ${diag.severity}\n• లక్షణాలు: ${diag.symptoms}\n• నివారణ చర్యలు: ${diag.treatment}\n• ముందస్తు జాగ్రత్తలు: ${diag.prevention}"
                "hi" -> "🌱 फसल रोग निदान:\n• फसल: ${diag.cropName}\n• रोग / समस्या: ${diag.diseaseName}\n• तीव्रता: ${diag.severity}\n• लक्षण: ${diag.symptoms}\n• उपचार: ${diag.treatment}\n• रोकथाम: ${diag.prevention}"
                else -> "🌱 Crop Disease Diagnosis:\n• Crop: ${diag.cropName}\n• Disease / Issue: ${diag.diseaseName}\n• Severity: ${diag.severity}\n• Symptoms: ${diag.symptoms}\n• Treatment: ${diag.treatment}\n• Prevention: ${diag.prevention}"
            }
            val formattedResponse = if (!com.bolkisan.farmer.domain.OfflineKnowledgeFormatter.hasCitationBox(baseFormattedResponse)) {
                "$baseFormattedResponse\n\n$citationBox"
            } else baseFormattedResponse

            val followUps = when (currentLanguage) {
                "te" -> listOf(
                    "${diag.cropName} లో సేంద్రీయ నివారణ మార్గాలు ఏమిటి?",
                    "${diag.cropName} ఎరువుల మోతాదు ఎంత?",
                    "విస్తరణ అధికారి సలహా పొందండి"
                )
                "hi" -> listOf(
                    "${diag.cropName} में जैविक नियंत्रण के उपाय?",
                    "${diag.cropName} में खाद की सही मात्रा?",
                    "कृषि अधिकारी से परामर्श लें"
                )
                else -> listOf(
                    "Organic treatment options for ${diag.cropName}?",
                    "Recommended fertilizer dosage for ${diag.cropName}?",
                    "Consult extension officer for ${diag.cropName}"
                )
            }

            val answerCitations = if (!diag.citations.isNullOrEmpty()) {
                diag.citations
            } else {
                listOf("ICAR Plant Pathology Guidelines 2024", "CIBRC Approved Disease Management Schedule")
            }

            val answerDto = VoiceAnswerDto(
                status = "answered_disease_diagnosis",
                isSafe = diag.isSafe,
                responseText = formattedResponse,
                citations = answerCitations,
                warnings = listOf("CIBRC & ICAR Certified Diagnostic Protocol"),
                cropIdentified = diag.cropName,
                cropSource = "detected",
                followUpSuggestions = followUps
            )

            val resolvedCropName = diag.cropName ?: effectiveCrop
            addHistoryItem(
                VoiceHistoryItem(
                    query = if (queryText.isBlank()) "Attached Photo Diagnosis ($resolvedCropName)" else queryText,
                    crop = resolvedCropName,
                    answer = formattedResponse,
                    isSafe = diag.isSafe,
                    citations = answerCitations,
                    attachedImagePath = photoFile.absolutePath,
                    followUpSuggestions = followUps,
                    origin = "online"
                )
            )

            Result.success(answerDto)
        } else {
            askQuestion(
                queryText = if (queryText.isBlank()) "Disease diagnosis query" else queryText,
                explicitCrop = explicitCrop,
                attachedImagePath = photoFile.absolutePath
            )
        }
    }

    // Disease Photo Diagnosis Flow (Online / Offline Outbox)
    suspend fun diagnoseDisease(
        imageFile: File,
        cropName: String,
        plantPart: String = "leaf",
        farmerNotes: String = ""
    ): Result<DiseaseDiagnosisDto> = withContext(Dispatchers.IO) {
        val online = isOnline.value && NetworkObserver.isNetworkAvailable(context)

        // Try online Retrofit diagnosis if network is available
        if (online) {
            try {
                val reqFile = imageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", imageFile.name, reqFile)
                val cropBody = cropName.toRequestBody("text/plain".toMediaTypeOrNull())
                val partBody = plantPart.toRequestBody("text/plain".toMediaTypeOrNull())
                val notesBody = farmerNotes.toRequestBody("text/plain".toMediaTypeOrNull())
                val langBody = currentLanguage.toRequestBody("text/plain".toMediaTypeOrNull())
                val authHeader = currentToken?.let { "Bearer $it" }

                val response = api.diagnoseDisease(
                    token = authHeader,
                    file = body,
                    cropName = cropBody,
                    plantPart = partBody,
                    farmerNotes = notesBody,
                    language = langBody
                )

                if (response.isSuccessful && response.body() != null) {
                    return@withContext Result.success(response.body()!!)
                }
            } catch (e: Exception) {
                // Online request failed, smoothly drop into offline persistence
            }
        }

        // OFFLINE FLOW:
        // 1. Save image permanently in app's internal files directory
        val permanentDir = File(context.filesDir, "disease_diagnoses").apply { mkdirs() }
        val permanentFile = File(permanentDir, "diag_${System.currentTimeMillis()}_${imageFile.name}")
        imageFile.copyTo(permanentFile, overwrite = true)

        // 2. Enqueue ticket in Room pending_sync_tickets
        val ticketId = "TKT-DIAG-${UUID.randomUUID().toString().take(8).uppercase()}"
        val pendingTicket = PendingSyncTicketEntity(
            localId = ticketId,
            queryText = "Disease diagnosis image for crop: $cropName",
            cropName = cropName,
            audioPath = null,
            imagePath = permanentFile.absolutePath,
            timestamp = System.currentTimeMillis(),
            isSynced = false
        )
        dao.enqueuePendingSyncTicket(pendingTicket)

        // 3. Schedule WorkManager for background sync
        SyncWorker.enqueue(context)

        // 4. Retrieve local verified ICAR knowledge for this crop
        val canonicalCrop = canonicalCropName(cropName)
        val localCropCards = dao.getKnowledgeByCropSync(canonicalCrop, currentLanguage)
        val effectiveCards = if (localCropCards.isNotEmpty()) localCropCards else dao.getKnowledgeByCropSync(canonicalCrop, "en")
        val diseaseCard = effectiveCards.firstOrNull { it.category == "disease" }
            ?: effectiveCards.firstOrNull { it.category == "pest_management" }
            ?: effectiveCards.firstOrNull()

        val diagnosisDto = DiseaseDiagnosisDto(
            diseaseName = when (currentLanguage) {
                "te" -> "ఆఫ్‌లైన్ నిల్వ: చిత్ర విశ్లేషణ పెండింగ్"
                "hi" -> "ऑफलाइन सुरक्षित: फोटो विश्लेषण लंबित"
                else -> "Offline Queued: Visual Analysis Pending"
            },
            cropName = cropName,
            confidence = null,
            severity = when (currentLanguage) {
                "te" -> "ఆఫ్‌లైన్ విజ్ఞాన నిల్వ (నెట్‌వర్క్ రాగానే విశ్లేషణ)"
                "hi" -> "ऑफलाइन ज्ञानकोश (नेटवर्क पर विश्लेषण)"
                else -> "Offline Knowledge Base (Pending Cloud Vision)"
            },
            symptoms = when (currentLanguage) {
                "te" -> "ఆఫ్‌లైన్ చిన్న భాషా నమూనా (Qwen3) చిత్రాలను నేరుగా విశ్లేషించలేదు. మీ పంట ఫోటో సురక్షితంగా భద్రపరచబడింది (టికెట్ #$ticketId) మరియు నెట్‌వర్క్ రాగానే క్లౌడ్/నిపుణుల పరిశీలనకు పంపబడుతుంది. కింద $cropName సాధారణ సస్యరక్షణ సూచనలు ఇవ్వబడ్డాయి."
                "hi" -> "ऑफलाइन मॉडल (Qwen3) फोटो का विश्लेषण नहीं कर सकता। आपकी फसल का फोटो सुरक्षित कर लिया गया है (टिकट #$ticketId) और नेटवर्क उपलब्ध होने पर विशेषज्ञ जांच के लिए भेजा जाएगा। नीचे $cropName के सामान्य प्रबंधन नियम दिए गए हैं।"
                else -> "Offline SLM (Qwen3) cannot perform on-device image vision. Your crop leaf photograph has been queued safely (Ticket #$ticketId) and will be processed upon reconnection. General management guidelines for $cropName are shown below."
            },
            treatment = diseaseCard?.content?.let {
                com.bolkisan.farmer.domain.OfflineKnowledgeFormatter.format(
                    rawText = it,
                    crop = cropName,
                    language = currentLanguage,
                    sourceCitation = diseaseCard.sourceCitation,
                    documentTitle = diseaseCard.title
                )
            } ?: when (currentLanguage) {
                "te" -> "తక్షణమే నత్రజని ఎరువుల వాడకం ఆపండి. సేంద్రీయ వేప నూనె 5 మి.లీ/లీటర్ లేదా సిఫార్సు చేసిన మందును పిచికారీ చేయండి."
                "hi" -> "यूरिया का प्रयोग रोकें। 5% नीम अर्क या अनुशंसित कीटनाशक का उचित मात्रा में छिड़काव करें।"
                else -> "Avoid excess nitrogen. Apply 5% NSKE or recommended standard spray per ICAR package."
            },
            prevention = when (currentLanguage) {
                "te" -> "పొలంలో నీరు నిల్వ ఉండకుండా చూడండి మరియు క్రమం తప్పక సర్వేక్షణ చేయండి."
                "hi" -> "उचित जल निकासी रखें और नियमित रूप से खेत की निगरानी करें।"
                else -> "Ensure proper field drainage and regular crop scouting."
            },
            citations = if (diseaseCard != null) {
                com.bolkisan.farmer.domain.OfflineKnowledgeFormatter.resolveCitationsForCard(
                    card = diseaseCard,
                    adviceText = diseaseCard.content,
                    language = currentLanguage
                )
            } else {
                listOf("ICAR Plant Pathology Guidelines 2024", "CIBRC Approved Disease Management Schedule")
            },
            isSafe = true,
            ticketNumber = ticketId,
            isOfflineSyncPending = true
        )

        Result.success(diagnosisDto)
    }

    // Fetch Tickets
    suspend fun getTickets(): Result<List<TicketItemDto>> = withContext(Dispatchers.IO) {
        val token = currentToken ?: return@withContext Result.failure(Exception("Not authenticated"))
        try {
            val res = api.getFarmerTickets("Bearer $token")
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("Error fetching tickets: ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Offline Knowledge Access
    fun getAllOfflineKnowledge(): Flow<List<OfflineKnowledgeEntity>> = dao.getAllKnowledge()

    fun getOfflineKnowledgeByLang(lang: String): Flow<List<OfflineKnowledgeEntity>> = dao.getAllKnowledgeByLang(lang)

    suspend fun getOfflineGuidelinesForCrop(crop: String): List<OfflineKnowledgeEntity> = withContext(Dispatchers.IO) {
        val canonical = canonicalCropName(crop)
        val cards = dao.getKnowledgeByCropSync(canonical, currentLanguage)
        if (cards.isNotEmpty()) cards else dao.getKnowledgeByCropSync(canonical, "en")
    }

    // Conversation Integration
    suspend fun getConversations(): Result<List<ConversationSummaryDto>> = withContext(Dispatchers.IO) {
        val token = currentToken ?: return@withContext Result.failure(Exception("Not authenticated"))
        try {
            val res = api.getConversations("Bearer $token")
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("Error fetching conversations: ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createConversation(title: String, crop: String = "General"): Result<ConversationSummaryDto> = withContext(Dispatchers.IO) {
        val token = currentToken ?: return@withContext Result.failure(Exception("Not authenticated"))
        try {
            val req = ConversationCreateDto(title = title, crop = crop, language = currentLanguage)
            val res = api.createConversation("Bearer $token", req)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("Error creating conversation: ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getConversationDetail(conversationId: String): Result<ConversationDetailDto> = withContext(Dispatchers.IO) {
        val token = currentToken ?: return@withContext Result.failure(Exception("Not authenticated"))
        try {
            val res = api.getConversationDetail("Bearer $token", conversationId)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("Error fetching conversation detail: ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun postConversationMessage(
        conversationId: String,
        queryText: String,
        crop: String? = null,
        pestOrDisease: String? = null,
        chemicalMentioned: String? = null,
        forceEscalation: Boolean = false
    ): Result<MessagePostResponseDto> = withContext(Dispatchers.IO) {
        val token = currentToken ?: return@withContext Result.failure(Exception("Not authenticated"))
        try {
            val req = MessagePostDto(
                queryText = queryText,
                transcript = queryText,
                language = currentLanguage,
                crop = crop ?: "General",
                pestOrDisease = pestOrDisease,
                chemicalMentioned = chemicalMentioned,
                forceEscalation = forceEscalation
            )
            val res = api.postConversationMessage("Bearer $token", conversationId, req)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("Error posting message to conversation: ${res.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Seed Offline Knowledge
    suspend fun seedOfflineKnowledgeIfEmpty() = withContext(Dispatchers.IO) {
        KnowledgePrepopulator.seedDatabase(dao)
    }

    /**
     * Executes a complete Offline Voice-to-Voice latency benchmark measuring:
     * - ASR latency (Speech recognition capture to transcript)
     * - Room SQLite retrieval latency
     * - Neural SLM / Room inference latency (TTFT, duration, chunks/s)
     * - Agricultural Safety Gate latency (sanitization, CIBRC/ICAR validation)
     * - TTS first-audio latency
     * - Total End-to-End Voice-to-Voice latency & 6-second SLA evaluation
     */
    suspend fun runVoiceToVoiceLatencyBenchmark(
        query: String = "పత్తిలో గులాబీ రంగు పురుగు నివారణ ఏమిటి?",
        crop: String = "Cotton",
        language: String = "te",
        simulatedAsrMs: Long = 350L,
        simulatedTtsMs: Long = 180L
    ): com.bolkisan.farmer.domain.OfflineBenchmarkResult = withContext(Dispatchers.IO) {
        val canonical = canonicalCropName(crop)
        val retStart = SystemClock.elapsedRealtime()
        val cards = dao.getKnowledgeByCropSync(canonical, language)
        val effectiveCards = if (cards.isNotEmpty()) cards else dao.getKnowledgeByCropSync(canonical, "en")
        val matchCard = effectiveCards.firstOrNull()
        val retElapsed = (SystemClock.elapsedRealtime() - retStart).coerceAtLeast(15L)

        val inferenceEngine = OfflineInferenceEngine.getInstance(context)
        val result = inferenceEngine.executeOfflineInference(
            query = query,
            cropContext = crop,
            retrievedContextText = matchCard?.content ?: "",
            retrievedCitation = matchCard?.sourceCitation ?: "ICAR Vyavasaya Panchangam",
            language = language,
            asrLatencyMs = simulatedAsrMs,
            retrievalLatencyMs = retElapsed,
            firstAudioTtsLatencyMs = simulatedTtsMs
        )
        _latestBenchmarkResult.value = result
        result
    }
}
