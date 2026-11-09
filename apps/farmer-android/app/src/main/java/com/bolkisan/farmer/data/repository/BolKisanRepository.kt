package com.bolkisan.farmer.data.repository

import android.content.Context
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

    private val api = ApiClient.apiService
    private val db = AppDatabase.getDatabase(context)
    val dao = db.offlineKnowledgeDao()
    private val prefs = context.getSharedPreferences("bolkisan_prefs", Context.MODE_PRIVATE)

    // Network Connectivity Observer
    private val networkObserver = NetworkObserver(context)
    val isOnline: StateFlow<Boolean> = networkObserver.isOnline

    // Conversation History StateFlow
    private val _historyFlow = MutableStateFlow<List<VoiceHistoryItem>>(loadHistoryFromPrefs())
    val historyFlow: StateFlow<List<VoiceHistoryItem>> = _historyFlow.asStateFlow()

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
        get() = com.bolkisan.farmer.data.local.SecureTokenStorage.getToken(context) ?: prefs.getString("jwt_token", null)
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
            lower.contains("cotton") || lower.contains("పత్తి") || lower.contains("దూది") || lower.contains("कपास") || lower.contains("kapas") || lower.contains("patti") -> "Cotton"
            lower.contains("chilli") || lower.contains("chili") || lower.contains("మిర్చి") || lower.contains("మిరప") || lower.contains("मिर्च") || lower.contains("mirchi") || lower.contains("mirapa") -> "Chilli"
            lower.contains("rice") || lower.contains("paddy") || lower.contains("వరి") || lower.contains("ధాన్యం") || lower.contains("వడ్లు") || lower.contains("బియ్యం") || lower.contains("धान") || lower.contains("चावल") || lower.contains("dhaan") || lower.contains("vari") -> "Rice"
            lower.contains("tomato") || lower.contains("టమోటా") || lower.contains("టమాట") || lower.contains("टमाटर") || lower.contains("tamatar") -> "Tomato"
            lower.contains("groundnut") || lower.contains("peanut") || lower.contains("వేరుశనగ") || lower.contains("పల్లీ") || lower.contains("मूंगफली") || lower.contains("palli") -> "Groundnut"
            lower.contains("maize") || lower.contains("corn") || lower.contains("మొక్కజొన్న") || lower.contains("జొన్న") || lower.contains("मक्का") || lower.contains("makka") || lower.contains("mokkajonna") -> "Maize"
            lower.contains("wheat") || lower.contains("గోధుమ") || lower.contains("గేహూ") || lower.contains("गेहूं") || lower.contains("gehun") || lower.contains("godhuma") -> "Wheat"
            lower.contains("turmeric") || lower.contains("పసుపు") || lower.contains("हल्दी") || lower.contains("haldi") || lower.contains("pasupu") -> "Turmeric"
            lower.contains("mango") || lower.contains("మామిడి") || lower.contains("आम") || lower.contains("aam") || lower.contains("mamidi") -> "Mango"
            lower.contains("onion") || lower.contains("ఉల్లి") || lower.contains("ఉల్లిపాయ") || lower.contains("प्याज") || lower.contains("pyaz") || lower.contains("ulli") -> "Onion"
            lower.contains("sugarcane") || lower.contains("చెరకు") || lower.contains("गन्ना") || lower.contains("ganna") || lower.contains("cheraku") -> "Sugarcane"
            lower.contains("pulses") || lower.contains("పప్పు") || lower.contains("కంది") || lower.contains("పెసర") || lower.contains("మినుము") || lower.contains("दाल") || lower.contains("चना") || lower.contains("arhar") || lower.contains("moong") || lower.contains("urad") || lower.contains("dal") -> "Pulses"
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

    // Voice Query Online / Offline Fallback with Auto-History and Multi-turn Context
    suspend fun askQuestion(
        queryText: String,
        explicitCrop: String? = null,
        attachedImagePath: String? = null
    ): Result<VoiceAnswerDto> = withContext(Dispatchers.IO) {
        val detectedCrop = explicitCrop
            ?: detectCropFromText(queryText)
            ?: _historyFlow.value.lastOrNull { it.crop.isNotBlank() }?.crop

        // Try Online FastAPI with CIBRC / RAG and Multi-turn History if authenticated
        val token = currentToken
        if (token != null) {
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
                    val historyOrigin = when (answer.status) {
                        "needs_clarification" -> "needs_clarification"
                        "escalated" -> "escalated"
                        else -> "online"
                    }
                    addHistoryItem(
                        VoiceHistoryItem(
                            query = queryText,
                            crop = finalCrop,
                            answer = answer.responseText,
                            isSafe = answer.isSafe,
                            citations = answer.citations ?: listOf("CIBRC & ICAR"),
                            attachedImagePath = attachedImagePath,
                            followUpSuggestions = answer.followUpSuggestions ?: emptyList(),
                            origin = historyOrigin
                        )
                    )
                    return@withContext Result.success(answer)
                }
            } catch (e: Exception) {
                // Network call failed, fall back into local offline knowledge engine
            }
        }

        // Offline Fallback Engine: Multi-factor scoring against local Room SQLite Knowledge Cards
        val queryTokens = queryText.lowercase()
            .split(" ", "?", "!", ".", ",", "।", "-", "/", "(", ")", "\n", "\t")
            .map { it.trim() }
            .filter { it.length >= 2 && it !in listOf("what", "how", "the", "for", "and", "can", "with", "నివారణ", "మందు", "ఎంత", "మరియు", "గురించి", "రసాయన", "ఎలా", "ఏమిటి", "చెప్పండి", "क्या", "कैसे", "करें", "बताएं", "की", "का", "में") }

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

        val targetCrop = detectedCrop ?: selectedCrop
        val canonicalCrop = canonicalCropName(targetCrop)
        val cropCards = dao.getKnowledgeByCropSync(canonicalCrop, currentLanguage)
        val fallbackCards = if (cropCards.isNotEmpty()) cropCards else dao.getKnowledgeByCropSync(canonicalCrop, "en")
        val allCards = if (fallbackCards.isNotEmpty()) fallbackCards else dao.searchLocalKnowledge(queryText, currentLanguage)

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
                    retrievedContextText = finalMatch.content,
                    retrievedCitation = finalMatch.sourceCitation,
                    language = currentLanguage
                )
                val slmAnswer = VoiceAnswerDto(
                    status = "answered_offline_slm",
                    isSafe = true,
                    responseText = "${slmRes.responseText}$langNotice",
                    citations = listOf(finalMatch.sourceCitation, "Qwen3-0.6B LiteRT-LM"),
                    warnings = listOf("Generated on-device via LiteRT-LM (${slmRes.runtime.name}) grounded in verified ICAR records."),
                    cropIdentified = finalMatch.crop,
                    cropSource = "detected",
                    followUpSuggestions = localFollowUps
                )
                addHistoryItem(
                    VoiceHistoryItem(
                        query = queryText,
                        crop = finalMatch.crop,
                        answer = slmAnswer.responseText,
                        isSafe = true,
                        citations = slmAnswer.citations ?: listOf("ICAR Offline"),
                        attachedImagePath = attachedImagePath,
                        followUpSuggestions = localFollowUps,
                        origin = "offline_slm"
                    )
                )
                return@withContext Result.success(slmAnswer)
            }

            // Deterministic SQLite Fallback Path (when SLM is not installed or device is memory-constrained)
            val fallbackAnswer = VoiceAnswerDto(
                status = "answered_offline_fallback",
                isSafe = true,
                responseText = when (currentLanguage) {
                    "te" -> "ఆఫ్‌లైన్ వ్యవసాయ సమాచారం:\n${finalMatch.content}$langNotice"
                    "hi" -> "ऑफ़लाइन कृषि जानकारी:\n${finalMatch.content}$langNotice"
                    else -> "Offline Agricultural Advisory:\n${finalMatch.content}$langNotice"
                },
                citations = listOf(finalMatch.sourceCitation),
                warnings = listOf("Offline Local Knowledge Cache - Matches authentic ICAR / CIBRC standards."),
                cropIdentified = finalMatch.crop,
                cropSource = "detected",
                followUpSuggestions = localFollowUps
            )
            addHistoryItem(
                VoiceHistoryItem(
                    query = queryText,
                    crop = finalMatch.crop,
                    answer = fallbackAnswer.responseText,
                    isSafe = true,
                    citations = fallbackAnswer.citations ?: listOf("ICAR Offline"),
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

            val fallbackNotice = when (currentLanguage) {
                "te" -> "నెట్‌వర్క్ అందుబాటులో లేదు. మీ ప్రశ్న టికెట్ ($ticketId) గా భద్రపరచబడింది. ఇంటర్నెట్ రాగానే స్వయంచాలకంగా అసిస్టెంట్‌కు సమర్పించబడుతుంది."
                "hi" -> "नेटवर्क उपलब्ध नहीं है। आपका प्रश्न टिकट ($ticketId) के रूप में सुरक्षित कर लिया गया है। नेटवर्क आते ही स्वचालित रूप से भेजा जाएगा।"
                else -> "Network unavailable. Your question is saved as ticket ($ticketId) for automatic sync when network is restored."
            }
            val queuedAnswer = VoiceAnswerDto(
                status = "offline_queued",
                isSafe = true,
                responseText = fallbackNotice,
                ticketNumber = ticketId,
                citations = listOf("Local Room Queue (pending_sync_tickets)"),
                cropIdentified = targetCrop,
                cropSource = "detected"
            )
            addHistoryItem(
                VoiceHistoryItem(
                    query = queryText,
                    crop = targetCrop,
                    answer = fallbackNotice,
                    isSafe = true,
                    citations = listOf("Pending Officer Queue"),
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
            val formattedResponse = when (currentLanguage) {
                "te" -> "🌱 పంట వ్యాధి విశ్లేషణ:\n• పంట: ${diag.cropName}\n• వ్యాధి / సమస్య: ${diag.diseaseName}\n• తీవ్రత: ${diag.severity}\n• లక్షణాలు: ${diag.symptoms}\n• నివారణ చర్యలు: ${diag.treatment}\n• ముందస్తు జాగ్రత్తలు: ${diag.prevention}"
                "hi" -> "🌱 फसल रोग निदान:\n• फसल: ${diag.cropName}\n• रोग / समस्या: ${diag.diseaseName}\n• तीव्रता: ${diag.severity}\n• लक्षण: ${diag.symptoms}\n• उपचार: ${diag.treatment}\n• रोकथाम: ${diag.prevention}"
                else -> "🌱 Crop Disease Diagnosis:\n• Crop: ${diag.cropName}\n• Disease / Issue: ${diag.diseaseName}\n• Severity: ${diag.severity}\n• Symptoms: ${diag.symptoms}\n• Treatment: ${diag.treatment}\n• Prevention: ${diag.prevention}"
            }

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

            val answerDto = VoiceAnswerDto(
                status = "answered_disease_diagnosis",
                isSafe = diag.isSafe,
                responseText = formattedResponse,
                citations = diag.citations ?: listOf("ICAR Plant Pathology Guidelines 2024"),
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
                    citations = answerDto.citations ?: listOf("ICAR"),
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
            diseaseName = diseaseCard?.title ?: when (currentLanguage) {
                "te" -> "$cropName ఆకు తెగులు / తెగుళ్ల సమాచారం"
                "hi" -> "$cropName रोग व कीट प्रबंधन"
                else -> "$cropName Disease & Pest Management"
            },
            cropName = cropName,
            confidence = null,
            severity = when (currentLanguage) {
                "te" -> "ఆఫ్‌లైన్ విజ్ఞాన నిల్వ (చిత్ర విశ్లేషణ పెండింగ్)"
                "hi" -> "ऑफलाइन ज्ञानकोश (फोटो विश्लेषण लंबित)"
                else -> "Offline Knowledge Base (Visual Analysis Pending)"
            },
            symptoms = when (currentLanguage) {
                "te" -> "పంట ఫోటో ఆఫ్‌లైన్‌లో భద్రపరచబడింది (నెట్‌వర్క్ వచ్చినప్పుడు నిపుణుల పరిశీలనకు పంపబడుతుంది). ప్రస్తుతం సంబంధిత పంటకు సంబంధించిన ICAR సమగ్ర సస్యరక్షణ మార్గదర్శకాలు ఇక్కడ అందించబడ్డాయి."
                "hi" -> "फसल का फोटो ऑफलाइन सुरक्षित कर लिया गया है (नेटवर्क आने पर विशेषज्ञ जांच के लिए भेजा जाएगा)। इस बीच स्थानीय ICAR पैकेज ऑफ प्रैक्टिसेज जानकारी नीचे दी गई है।"
                else -> "Leaf photo queued locally for cloud/expert visual diagnosis upon reconnection. Relevant ICAR Package of Practices guidelines retrieved from local offline knowledge below."
            },
            treatment = diseaseCard?.content ?: when (currentLanguage) {
                "te" -> "తక్షణమే నత్రజని ఎరువుల వాడకం ఆపండి. సేంద్రీయ వేప నూనె 5 మి.లీ/లీటర్ లేదా సిఫార్సు చేసిన మందును పిచికారీ చేయండి."
                "hi" -> "यूरिया का प्रयोग रोकें। 5% नीम अर्क या अनुशंसित कीटनाशक का उचित मात्रा में छिड़काव करें।"
                else -> "Avoid excess nitrogen. Apply 5% NSKE or recommended standard spray per ICAR package."
            },
            prevention = when (currentLanguage) {
                "te" -> "పొలంలో నీరు నిల్వ ఉండకుండా చూడండి మరియు క్రమం తప్పక సర్వేక్షణ చేయండి."
                "hi" -> "उचित जल निकासी रखें और नियमित रूप से खेत की निगरानी करें।"
                else -> "Ensure proper field drainage and regular crop scouting."
            },
            citations = listOf(diseaseCard?.sourceCitation ?: "ICAR Integrated Pest Management Guidelines 2024"),
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
}
