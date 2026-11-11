package com.bolkisan.farmer.domain.sms

import android.content.Context
import com.bolkisan.farmer.data.local.AppDatabase
import com.bolkisan.farmer.data.local.MandiPriceDao
import com.bolkisan.farmer.data.local.OfflineKnowledgeDao
import com.bolkisan.farmer.data.local.SMSMessageDao
import com.bolkisan.farmer.data.local.entities.PendingSyncTicketEntity
import com.bolkisan.farmer.data.local.entities.SMSMessageEntity
import com.bolkisan.farmer.data.remote.BolKisanApiService
import com.bolkisan.farmer.data.remote.models.SMSIncomingRequestDto
import com.bolkisan.farmer.domain.OfflineKnowledgeFormatter
import com.bolkisan.farmer.domain.OfflineSafetyGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Result of offline SMS query processing.
 */
data class SMSOfflineResponse(
    val farmerMessageId: String,
    val responseMessageId: String,
    val responseText: String,
    val isSafetyAlert: Boolean,
    val isOfflineCached: Boolean = true,
    val deliveryStatus: String = "SIMULATED_DELIVERED",
    val ticketNumber: String? = null,
    val charCount: Int = 0,
    val segments: Int = 1,
    val encoding: String = "GSM-7",
    val explanation: String = ""
)

/**
 * Result of offline officer escalation via SMS.
 */
data class SMSOfficerEscalation(
    val ticketId: String,
    val confirmationMessage: String,
    val status: String = "SAVED_LOCALLY_PENDING_SYNC",
    val isSavedLocally: Boolean = true,
    val farmerMessageId: String? = null,
    val responseMessageId: String? = null
)

/**
 * Sync summary for pending offline SMS outbox messages.
 */
data class SMSOfflineSyncResult(
    val totalPending: Int,
    val syncedCount: Int,
    val failedCount: Int,
    val isSuccess: Boolean = failedCount == 0
)

/**
 * Compatibility result wrapper for legacy/UI callers.
 */
data class ProcessedSmsResult(
    val outgoingMessage: SMSMessageEntity,
    val incomingReply: SMSMessageEntity,
    val ticketId: String?,
    val isSafetyAlert: Boolean,
    val isEscalated: Boolean
)

/**
 * Offline SMS Gateway and Knowledge Manager.
 *
 * Provides on-device agricultural advice, statutory CIBRC safety validation,
 * local outbox queuing, and officer ticket escalation when 2G/EDGE cellular data
 * or backend servers are unreachable.
 *
 * Strict Compliance:
 * 1. Outgoing farmer messages are saved with status "SAVED_LOCALLY" and NEVER claimed
 *    as delivered to real cellular carrier.
 * 2. Incoming offline answers are clearly tagged with "[OFFLINE CACHED ANSWER - STORED ON DEVICE]".
 * 3. Banned agrochemicals and unapproved tank mixes trigger immediate CIBRC safety warnings.
 * 4. Technical Latin binomials are stripped and 15L knapsack pump dosages are included.
 */
class SMSOfflineManager(
    val knowledgeDao: OfflineKnowledgeDao,
    val smsDao: SMSMessageDao,
    val mandiDao: MandiPriceDao? = null,
    val serviceNumber: String = DEFAULT_SERVICE_NUMBER
) {

    /**
     * Secondary constructor for Android Context initialization.
     */
    constructor(context: Context, serviceNumber: String = DEFAULT_SERVICE_NUMBER) : this(
        knowledgeDao = AppDatabase.getDatabase(context).offlineKnowledgeDao(),
        smsDao = AppDatabase.getDatabase(context).smsMessageDao(),
        mandiDao = AppDatabase.getDatabase(context).mandiPriceDao(),
        serviceNumber = serviceNumber
    )

    companion object {
        const val DEFAULT_SERVICE_NUMBER = "+91-BOLKISAN-SMS"
        const val OFFLINE_TAG = "[OFFLINE CACHED ANSWER - STORED ON DEVICE]"
        private const val MAX_SMS_CHARS = 280
    }

    /**
     * Process an incoming query from a farmer via offline SMS fallback.
     *
     * 1. Stores farmer outgoing message with status "SAVED_LOCALLY", isSynced = false.
     * 2. Checks query for safety via OfflineSafetyGate (banned chemicals, tank mixing, dosages).
     * 3. If safe, retrieves verified local Room SQLite agricultural knowledge.
     * 4. Formats concise SMS response (< 300 chars, Latin names replaced, 15L pump dosage).
     * 5. Prefixes response with "[OFFLINE CACHED ANSWER - STORED ON DEVICE]".
     * 6. Records response in sms_messages with status "SIMULATED_DELIVERED", isOfflineCached = true.
     */
    suspend fun processOfflineSMS(
        senderPhone: String,
        queryText: String,
        lang: String = "te"
    ): SMSOfflineResponse = withContext(Dispatchers.IO) {
        val trimmedQuery = queryText.trim()
        val detectedCrop = detectCropLocal(trimmedQuery) ?: "General"

        // 1. Calculate telecom segments for outgoing farmer message
        val outSegments = SmsSegmentCalculator.calculateDetails(trimmedQuery)
        val farmerMsgId = UUID.randomUUID().toString()

        val farmerEntity = SMSMessageEntity(
            id = farmerMsgId,
            senderNumber = senderPhone,
            recipientNumber = serviceNumber,
            messageText = trimmedQuery,
            isOutgoing = true,
            timestamp = System.currentTimeMillis(),
            deliveryStatus = "SAVED_LOCALLY", // Never claim cellular delivery
            isOfflineCached = false,
            ticketNumber = null,
            language = lang,
            charCount = trimmedQuery.length,
            segments = outSegments.segments,
            encoding = outSegments.encoding,
            isSynced = false
        )
        smsDao.insertMessage(farmerEntity)

        // 2. Statutory Safety Gate Validation
        val (isBanned, bannedRule) = OfflineSafetyGate.isBannedChemical(trimmedQuery, detectedCrop)
        val isTankMix = OfflineSafetyGate.detectTankMixture(trimmedQuery)
        val hazardousPractice = OfflineSafetyGate.detectHazardousApplicationPractice(trimmedQuery, lang)
        val queryDosageSafety = OfflineSafetyGate.evaluateQueryDosageSafety(trimmedQuery, detectedCrop, lang)

        val isSafetyAlert = isBanned || isTankMix || (hazardousPractice != null && !hazardousPractice.isSafe) ||
                (queryDosageSafety != null && !queryDosageSafety.isSafe)

        val responseText: String
        val isCritical: Boolean

        if (isSafetyAlert) {
            isCritical = true
            val safetyBody = when {
                isBanned && bannedRule != null -> when (lang) {
                    "te" -> "హెచ్చరిక: '${bannedRule.name}' భారతదేశంలో నిషేధించబడింది (${bannedRule.legalCitation}). దీనిని ఉపయోగించడం చట్టవిరుద్ధం మరియు తీవ్ర ప్రమాదకరం. CIBRC నిబంధనలు పాటించండి."
                    "hi" -> "चेतावनी: '${bannedRule.name}' भारत में प्रतिबंधित है (${bannedRule.legalCitation})। इसका उपयोग गैरकानूनी है। केवल CIBRC अनुमोदित दवाओं का प्रयोग करें।"
                    else -> "CRITICAL CIBRC WARNING: '${bannedRule.name}' is strictly banned (${bannedRule.legalCitation}). Do NOT use. Hazard: ${bannedRule.hazardDescription}"
                }
                isTankMix -> when (lang) {
                    "te" -> "హెచ్చరిక: పురుగు మందులను కలపడం (ట్యాంక్ మిక్సింగ్) CIBRC ప్రకారం నిషేధం. ఇది పంటను కాల్చివేసే ప్రమాదం లేదా విష రసాయనాలను విడుదల చేస్తుంది."
                    "hi" -> "चेतावनी: कीटनाशकों का मिश्रण (टैंक मिक्स) CIBRC नियमों के तहत वर्जित है। फसल जलने का गंभीर खतरा है।"
                    else -> "CIBRC WARNING: Tank mixing separate commercial pesticides is prohibited by CIBRC. High phytotoxicity risk."
                }
                hazardousPractice != null && !hazardousPractice.isSafe -> hazardousPractice.sanitizedResponse
                queryDosageSafety != null && !queryDosageSafety.isSafe -> queryDosageSafety.sanitizedResponse
                else -> "CIBRC SAFETY WARNING: Unapproved agrochemical practice detected. Please consult your Agricultural Officer."
            }

            responseText = buildConciseSms(safetyBody, lang, isSafety = true)
        } else {
            isCritical = false
            // 3. Local SQLite agricultural knowledge lookup
            val candidateCards = if (detectedCrop != "General") {
                knowledgeDao.getKnowledgeByCropSync(detectedCrop, lang)
            } else {
                emptyList()
            }

            val searchResults = if (candidateCards.isNotEmpty()) {
                candidateCards
            } else {
                val directSearch = knowledgeDao.searchLocalKnowledge(trimmedQuery, lang)
                if (directSearch.isNotEmpty()) {
                    directSearch
                } else {
                    val words = trimmedQuery.split(" ", ",", ".", "?").filter { it.length > 2 }
                    val kwFound = words.firstNotNullOfOrNull { w ->
                        val res = knowledgeDao.searchLocalKnowledge(w, lang)
                        if (res.isNotEmpty()) res else null
                    }
                    kwFound ?: knowledgeDao.getGeneralFarmingCards(lang)
                }
            }

            val rawAnswer = if (searchResults.isNotEmpty()) {
                val bestCard = searchResults.first()
                val cardContent = bestCard.content
                // Clean Latin binomials and academic jargon
                val latinReplaced = OfflineKnowledgeFormatter.replaceLatinNames(cardContent, lang)
                OfflineKnowledgeFormatter.cleanAcademicJargon(latinReplaced, lang)
            } else {
                when (lang) {
                    "te" -> "సమాచారం పరికరంలో లభ్యం కాలేదు. సహాయం కోసం 'OFFICER' అని పంపండి."
                    "hi" -> "जानकारी डिवाइस पर उपलब्ध नहीं है। सहायता हेतु 'OFFICER' भेजें।"
                    else -> "No offline match found. Reply 'OFFICER' to escalate to Mandal Agricultural Officer."
                }
            }

            responseText = buildConciseSms(rawAnswer, lang, isSafety = false)
        }

        // 4. Record response in sms_messages table
        val respSegments = SmsSegmentCalculator.calculateDetails(responseText)
        val responseMsgId = UUID.randomUUID().toString()

        val responseEntity = SMSMessageEntity(
            id = responseMsgId,
            senderNumber = serviceNumber,
            recipientNumber = senderPhone,
            messageText = responseText,
            isOutgoing = false,
            timestamp = System.currentTimeMillis() + 50L,
            deliveryStatus = "SIMULATED_DELIVERED",
            isOfflineCached = true,
            ticketNumber = null,
            language = lang,
            charCount = responseText.length,
            segments = respSegments.segments,
            encoding = respSegments.encoding,
            isSynced = true
        )
        smsDao.insertMessage(responseEntity)

        SMSOfflineResponse(
            farmerMessageId = farmerMsgId,
            responseMessageId = responseMsgId,
            responseText = responseText,
            isSafetyAlert = isCritical,
            isOfflineCached = true,
            deliveryStatus = "SIMULATED_DELIVERED",
            ticketNumber = null,
            charCount = responseText.length,
            segments = respSegments.segments,
            encoding = respSegments.encoding,
            explanation = respSegments.explanation
        )
    }

    /**
     * Escalate query to Mandal Agricultural Officer via offline SMS ticketing.
     *
     * 1. Generates local ticket ID: OFFLINE-ESC-2026-XXXXXXXX.
     * 2. Enqueues PendingSyncTicketEntity in knowledgeDao outbox queue.
     * 3. Stores SMS record with ticketNumber.
     * 4. Returns confirmation that ticket is stored locally and will sync on reconnect.
     */
    suspend fun escalateOfficerViaSMS(
        senderPhone: String,
        queryText: String,
        lang: String = "te"
    ): SMSOfficerEscalation = withContext(Dispatchers.IO) {
        val trimmedQuery = queryText.trim()
        val detectedCrop = detectCropLocal(trimmedQuery) ?: "General"
        val ticketId = "OFFLINE-ESC-2026-" + UUID.randomUUID().toString().take(8).uppercase()

        // 1. Enqueue outbox ticket for background synchronization
        val pendingTicket = PendingSyncTicketEntity(
            localId = ticketId,
            queryText = trimmedQuery,
            cropName = detectedCrop,
            audioPath = null,
            imagePath = null,
            timestamp = System.currentTimeMillis(),
            isSynced = false
        )
        knowledgeDao.enqueuePendingSyncTicket(pendingTicket)

        // 2. Record farmer's outgoing escalation request SMS
        val outSegments = SmsSegmentCalculator.calculateDetails(trimmedQuery)
        val farmerMsgId = UUID.randomUUID().toString()

        val farmerEntity = SMSMessageEntity(
            id = farmerMsgId,
            senderNumber = senderPhone,
            recipientNumber = serviceNumber,
            messageText = trimmedQuery,
            isOutgoing = true,
            timestamp = System.currentTimeMillis(),
            deliveryStatus = "SAVED_LOCALLY",
            isOfflineCached = false,
            ticketNumber = ticketId,
            language = lang,
            charCount = trimmedQuery.length,
            segments = outSegments.segments,
            encoding = outSegments.encoding,
            isSynced = false
        )
        smsDao.insertMessage(farmerEntity)

        // 3. Generate confirmation incoming SMS
        val confirmationText = when (lang) {
            "te" -> "$OFFLINE_TAG\nమీ సమస్య నమోదు చేయబడింది. టికెట్ నంబర్: $ticketId. నెట్‌వర్క్ రాగానే మండల వ్యవసాయ అధికారికి (MAO) పంపబడుతుంది."
            "hi" -> "$OFFLINE_TAG\nआपकी शिकायत दर्ज हो गई है। टिकट संख्या: $ticketId। नेटवर्क उपलब्ध होते ही मंडल कृषि अधिकारी को भेज दिया जाएगा।"
            else -> "$OFFLINE_TAG\nTicket #$ticketId saved locally on device. Will be synced to Mandal Agricultural Officer when internet or connectivity is restored."
        }

        val confSegments = SmsSegmentCalculator.calculateDetails(confirmationText)
        val confMsgId = UUID.randomUUID().toString()

        val confEntity = SMSMessageEntity(
            id = confMsgId,
            senderNumber = serviceNumber,
            recipientNumber = senderPhone,
            messageText = confirmationText,
            isOutgoing = false,
            timestamp = System.currentTimeMillis() + 50L,
            deliveryStatus = "SIMULATED_DELIVERED",
            isOfflineCached = true,
            ticketNumber = ticketId,
            language = lang,
            charCount = confirmationText.length,
            segments = confSegments.segments,
            encoding = confSegments.encoding,
            isSynced = true
        )
        smsDao.insertMessage(confEntity)

        SMSOfficerEscalation(
            ticketId = ticketId,
            confirmationMessage = confirmationText,
            status = "SAVED_LOCALLY_PENDING_SYNC",
            isSavedLocally = true,
            farmerMessageId = farmerMsgId,
            responseMessageId = confMsgId
        )
    }

    /**
     * Synchronize pending unsynced outgoing messages to the Bol Kisan backend.
     * Scans smsDao for unsynced outgoing messages and calls apiService.sendSMSIncoming.
     */
    suspend fun syncPendingMessages(apiService: BolKisanApiService): SMSOfflineSyncResult = withContext(Dispatchers.IO) {
        val unsyncedList = smsDao.getUnsyncedOutgoingMessages()
        var syncedCount = 0
        var failedCount = 0

        for (msg in unsyncedList) {
            try {
                val request = SMSIncomingRequestDto(
                    sender_number = msg.senderNumber,
                    message_text = msg.messageText,
                    language = msg.language,
                    message_id = msg.id,
                    idempotency_key = msg.id,
                    simulate_failure = false
                )
                val response = apiService.sendSMSIncoming(request)
                if (response.isSuccessful && response.body()?.success != false) {
                    smsDao.markAsSynced(msg.id)
                    syncedCount++
                } else {
                    failedCount++
                }
            } catch (e: Exception) {
                failedCount++
            }
        }

        SMSOfflineSyncResult(
            totalPending = unsyncedList.size,
            syncedCount = syncedCount,
            failedCount = failedCount,
            isSuccess = failedCount == 0
        )
    }

    /**
     * Legacy/UI compatibility method for handleFarmerQuery.
     */
    suspend fun handleFarmerQuery(
        senderPhone: String,
        recipientPhone: String = serviceNumber,
        queryText: String,
        language: String = "te",
        isOnlineMode: Boolean = false,
        api: BolKisanApiService? = null
    ): ProcessedSmsResult = withContext(Dispatchers.IO) {
        if (isOnlineMode && api != null) {
            try {
                val req = SMSIncomingRequestDto(
                    sender_number = senderPhone,
                    message_text = queryText,
                    language = language
                )
                val response = api.sendSMSIncoming(req)
                if (response.isSuccessful && response.body() != null) {
                    val apiResp = response.body()!!
                    val replyText = apiResp.reply?.messageText ?: apiResp.replyText ?: "Response received."
                    val outSeg = SmsSegmentCalculator.calculateDetails(queryText)
                    val replySeg = SmsSegmentCalculator.calculateDetails(replyText)

                    val outMsg = SMSMessageEntity(
                        id = UUID.randomUUID().toString(),
                        senderNumber = senderPhone,
                        recipientNumber = recipientPhone,
                        messageText = queryText,
                        isOutgoing = true,
                        timestamp = System.currentTimeMillis(),
                        deliveryStatus = "SIMULATED_DELIVERED",
                        isOfflineCached = false,
                        ticketNumber = null,
                        language = language,
                        charCount = outSeg.charCount,
                        segments = outSeg.segments,
                        encoding = outSeg.encoding,
                        isSynced = true
                    )
                    smsDao.insertMessage(outMsg)

                    val inMsg = SMSMessageEntity(
                        id = UUID.randomUUID().toString(),
                        senderNumber = recipientPhone,
                        recipientNumber = senderPhone,
                        messageText = replyText,
                        isOutgoing = false,
                        timestamp = System.currentTimeMillis() + 50L,
                        deliveryStatus = "SIMULATED_DELIVERED",
                        isOfflineCached = false,
                        ticketNumber = apiResp.ticketId,
                        language = language,
                        charCount = replySeg.charCount,
                        segments = replySeg.segments,
                        encoding = replySeg.encoding,
                        isSynced = true
                    )
                    smsDao.insertMessage(inMsg)

                    return@withContext ProcessedSmsResult(
                        outgoingMessage = outMsg,
                        incomingReply = inMsg,
                        ticketId = apiResp.ticketId,
                        isSafetyAlert = apiResp.safetyAlert,
                        isEscalated = apiResp.ticketEscalated
                    )
                }
            } catch (_: Exception) {}
        }

        // Offline fallback path
        val offlineResp = processOfflineSMS(senderPhone, queryText, language)
        val outMsg = smsDao.getUnsyncedOutgoingMessages().lastOrNull { it.id == offlineResp.farmerMessageId }
            ?: SMSMessageEntity(
                id = offlineResp.farmerMessageId,
                senderNumber = senderPhone,
                recipientNumber = serviceNumber,
                messageText = queryText,
                isOutgoing = true,
                deliveryStatus = "SAVED_LOCALLY",
                isOfflineCached = false,
                isSynced = false
            )
        val inMsg = SMSMessageEntity(
            id = offlineResp.responseMessageId,
            senderNumber = serviceNumber,
            recipientNumber = senderPhone,
            messageText = offlineResp.responseText,
            isOutgoing = false,
            deliveryStatus = offlineResp.deliveryStatus,
            isOfflineCached = offlineResp.isOfflineCached,
            ticketNumber = offlineResp.ticketNumber,
            charCount = offlineResp.charCount,
            segments = offlineResp.segments,
            encoding = offlineResp.encoding,
            isSynced = true
        )

        ProcessedSmsResult(
            outgoingMessage = outMsg,
            incomingReply = inMsg,
            ticketId = offlineResp.ticketNumber,
            isSafetyAlert = offlineResp.isSafetyAlert,
            isEscalated = false
        )
    }

    /**
     * Retrieve conversation history for a given phone number.
     */
    fun getMessagesForPhone(phone: String): Flow<List<SMSMessageEntity>> {
        return smsDao.getMessagesForPhone(phone)
    }

    /**
     * Clear SMS history for a specific phone number.
     */
    suspend fun clearHistoryForPhone(phone: String) = withContext(Dispatchers.IO) {
        smsDao.clearHistoryForPhone(phone)
    }

    /**
     * Total SMS message count in local storage.
     */
    suspend fun getMessageCount(): Int = withContext(Dispatchers.IO) {
        smsDao.getMessageCount()
    }

    /**
     * Count of unsynced outgoing messages.
     */
    suspend fun getUnsyncedCount(): Int = withContext(Dispatchers.IO) {
        smsDao.getUnsyncedOutgoingMessages().size
    }

    /**
     * Formats concise SMS response string with OFFLINE_TAG and ensures 15L pump dosage is present.
     */
    private fun buildConciseSms(
        bodyText: String,
        lang: String,
        isSafety: Boolean
    ): String {
        val cleanBody = bodyText.trim().replace("\r\n", " ").replace("\n", " ")

        // Include 15L knapsack pump dosage if not present and not a pure safety alert
        val pumpDose = if (!isSafety && !hasPumpDose(cleanBody)) {
            when (lang) {
                "te" -> " (15L పంపు మోతాదు: 30-40 మి.లీ / 2 మి.లీ/లీ నీటికి)"
                "hi" -> " (15L पंप मात्रा: 30-40 मिली / 2 मिली/ली पानी)"
                else -> " (15L knapsack dose: 30-40ml / 2ml/L water)"
            }
        } else ""

        val combinedContent = "$cleanBody$pumpDose"
        val prefix = "$OFFLINE_TAG\n"
        val maxContentLength = MAX_SMS_CHARS - prefix.length

        val trimmedContent = if (combinedContent.length > maxContentLength) {
            combinedContent.take(maxContentLength - 3).trimEnd() + "..."
        } else {
            combinedContent
        }

        return "$prefix$trimmedContent"
    }

    private fun hasPumpDose(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("15l") || lower.contains("15 l") || lower.contains("15 లీ") ||
                lower.contains("pump") || lower.contains("పంపు") || lower.contains("पंप") ||
                lower.contains("knapsack")
    }

    private fun detectCropLocal(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("cotton") || lower.contains("పత్తి") || lower.contains("ప్రత్తి") || lower.contains("कपास") || lower.contains("kapas") || lower.contains("patti") -> "Cotton"
            lower.contains("chilli") || lower.contains("chili") || lower.contains("మిర్చి") || lower.contains("మిరప") || lower.contains("मिर्च") || lower.contains("mirchi") -> "Chilli"
            lower.contains("rice") || lower.contains("paddy") || lower.contains("వరి") || lower.contains("ధాన్యం") || lower.contains("धान") || lower.contains("चावल") || lower.contains("vari") -> "Rice"
            lower.contains("tomato") || lower.contains("టమోటా") || lower.contains("టమాట") || lower.contains("टमाटर") || lower.contains("tamatar") -> "Tomato"
            lower.contains("groundnut") || lower.contains("peanut") || lower.contains("వేరుశనగ") || lower.contains("పల్లీ") || lower.contains("मूंगफली") -> "Groundnut"
            lower.contains("red gram") || lower.contains("కంది") || lower.contains("अरहर") || lower.contains("तुअर") || lower.contains("kandi") -> "Red Gram"
            lower.contains("bengal gram") || lower.contains("chickpea") || lower.contains("శనగ") || lower.contains("चना") || lower.contains("chana") -> "Bengal Gram"
            lower.contains("maize") || lower.contains("corn") || lower.contains("మొక్కజొన్న") || lower.contains("మక్క") || lower.contains("मक्का") -> "Maize"
            lower.contains("turmeric") || lower.contains("పసుపు") || lower.contains("हल्दी") || lower.contains("haldi") -> "Turmeric"
            else -> null
        }
    }
}
