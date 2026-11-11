package com.bolkisan.farmer

import com.bolkisan.farmer.data.local.MandiPriceDao
import com.bolkisan.farmer.data.local.OfflineKnowledgeDao
import com.bolkisan.farmer.data.local.SMSMessageDao
import com.bolkisan.farmer.data.local.entities.MandiPriceEntity
import com.bolkisan.farmer.data.local.entities.OfflineKnowledgeEntity
import com.bolkisan.farmer.data.local.entities.PendingSyncTicketEntity
import com.bolkisan.farmer.data.local.entities.SMSMessageEntity
import com.bolkisan.farmer.data.remote.BolKisanApiService
import com.bolkisan.farmer.data.remote.models.*
import com.bolkisan.farmer.domain.OfflineSafetyGate
import com.bolkisan.farmer.domain.sms.SMSOfflineManager
import com.bolkisan.farmer.domain.sms.SmsSegmentCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class SMSOfflineManagerTest {

    private lateinit var fakeKnowledgeDao: FakeOfflineKnowledgeDao
    private lateinit var fakeSmsDao: FakeSMSMessageDao
    private lateinit var fakeApiService: FakeBolKisanApiService
    private lateinit var manager: SMSOfflineManager

    @Before
    fun setUp() {
        fakeKnowledgeDao = FakeOfflineKnowledgeDao()
        fakeSmsDao = FakeSMSMessageDao()
        fakeApiService = FakeBolKisanApiService()
        manager = SMSOfflineManager(
            knowledgeDao = fakeKnowledgeDao,
            smsDao = fakeSmsDao,
            mandiDao = null,
            serviceNumber = "+91-BOLKISAN-SMS"
        )
    }

    // =========================================================================
    // 1. OUTGOING MESSAGE STORED LOCALLY, NEVER CLAIM CELLULAR DELIVERY
    // =========================================================================

    @Test
    fun test1_OutgoingFarmerMessageStoredLocallyNeverClaimCarrierDelivery() = runBlocking {
        val farmerPhone = "+919876543210"
        val query = "వరి పంటలో ఆకుమచ్చ తెగులు నివారణ ఏమిటి?"

        val response = manager.processOfflineSMS(farmerPhone, query, "te")

        assertNotNull(response)
        assertFalse("Normal query is not a safety alert", response.isSafetyAlert)
        assertTrue("Response is marked offline cached", response.isOfflineCached)
        assertEquals("SIMULATED_DELIVERED", response.deliveryStatus)

        // Check farmer's outgoing message in storage
        val unsynced = fakeSmsDao.getUnsyncedOutgoingMessages()
        assertEquals(1, unsynced.size)
        val farmerMsg = unsynced.first()
        assertEquals(response.farmerMessageId, farmerMsg.id)
        assertEquals(farmerPhone, farmerMsg.senderNumber)
        assertEquals("+91-BOLKISAN-SMS", farmerMsg.recipientNumber)
        assertEquals(query, farmerMsg.messageText)
        assertTrue("Farmer message is outgoing", farmerMsg.isOutgoing)
        // MUST NEVER claim delivered to cellular carrier
        assertEquals("SAVED_LOCALLY", farmerMsg.deliveryStatus)
        assertFalse("Must not be marked synced initially", farmerMsg.isSynced)
        assertFalse("Farmer outgoing message is not an offline cache response", farmerMsg.isOfflineCached)

        // Check response message in storage
        val totalMsgs = fakeSmsDao.messages
        assertEquals(2, totalMsgs.size)
        val respMsg = totalMsgs.first { !it.isOutgoing }
        assertEquals(response.responseMessageId, respMsg.id)
        assertEquals("+91-BOLKISAN-SMS", respMsg.senderNumber)
        assertEquals(farmerPhone, respMsg.recipientNumber)
        assertEquals("SIMULATED_DELIVERED", respMsg.deliveryStatus)
        assertTrue(respMsg.isOfflineCached)
    }

    // =========================================================================
    // 2. OFFLINE CACHED ANSWER TAG PREFIX & CONCISE SMS LENGTH (< 300 CHARS)
    // =========================================================================

    @Test
    fun test2_OfflineCachedAnswerTagPrefixAndConciseLength() = runBlocking {
        val farmerPhone = "+919876543210"
        val query = "టమోటా పంటలో తెగులు"

        val response = manager.processOfflineSMS(farmerPhone, query, "te")

        assertTrue(
            "Response MUST start with offline cached indicator",
            response.responseText.startsWith("[OFFLINE CACHED ANSWER - STORED ON DEVICE]")
        )
        assertTrue(
            "Response length must be concise (< 300 characters), actual: ${response.responseText.length}",
            response.responseText.length < 300
        )
    }

    // =========================================================================
    // 3. STATUTORY CIBRC SAFETY GATE: BANNED PESTICIDE (MONOCROTOPHOS ON VEGETABLES)
    // =========================================================================

    @Test
    fun test3_BannedChemicalMonocrotophosVegetableSafetyWarning() = runBlocking {
        val farmerPhone = "+919876543210"
        val query = "Can I spray Monocrotophos on tomato for insect control?"

        val response = manager.processOfflineSMS(farmerPhone, query, "en")

        assertTrue("Banned chemical query must trigger safety alert", response.isSafetyAlert)
        assertTrue(
            "Response must include offline cached indicator",
            response.responseText.startsWith("[OFFLINE CACHED ANSWER - STORED ON DEVICE]")
        )
        assertTrue(
            "Warning must state Monocrotophos or CIBRC or banned",
            response.responseText.contains("Monocrotophos", ignoreCase = true) &&
                    (response.responseText.contains("banned", ignoreCase = true) || response.responseText.contains("CIBRC", ignoreCase = true))
        )
        assertTrue(
            "Response must remain concise (< 300 chars)",
            response.responseText.length < 300
        )

        // Storage verification
        val messages = fakeSmsDao.messages
        assertEquals(2, messages.size)
        val outgoing = messages.first { it.isOutgoing }
        assertEquals("SAVED_LOCALLY", outgoing.deliveryStatus)
    }

    // =========================================================================
    // 4. STATUTORY CIBRC SAFETY GATE: BANNED PESTICIDE (ENDOSULFAN TOTAL BAN)
    // =========================================================================

    @Test
    fun test4_BannedChemicalEndosulfanTotalBanWarning() = runBlocking {
        val farmerPhone = "+919876543210"
        val query = "Endosulfan 35 EC spray for pest control"

        val response = manager.processOfflineSMS(farmerPhone, query, "en")

        assertTrue("Endosulfan must trigger safety alert", response.isSafetyAlert)
        assertTrue(response.responseText.contains("Endosulfan", ignoreCase = true))
        assertTrue(
            response.responseText.contains("banned", ignoreCase = true) ||
                    response.responseText.contains("prohibited", ignoreCase = true)
        )
    }

    // =========================================================================
    // 5. STATUTORY CIBRC SAFETY GATE: UNAPPROVED TANK MIX
    // =========================================================================

    @Test
    fun test5_HazardousTankMixWarning() = runBlocking {
        val farmerPhone = "+919876543210"
        val query = "Can I mix Coragen and Mancozeb together in the same tank?"

        val response = manager.processOfflineSMS(farmerPhone, query, "en")

        assertTrue("Tank mix query must trigger safety alert", response.isSafetyAlert)
        assertTrue(
            "Response must contain tank mix warning",
            response.responseText.contains("tank mix", ignoreCase = true) ||
                    response.responseText.contains("CIBRC", ignoreCase = true)
        )
    }

    // =========================================================================
    // 6. CROP ADVICE: LATIN BINOMIAL REPLACEMENT & 15L KNAPSACK PUMP DOSAGE
    // =========================================================================

    @Test
    fun test6_CropAdviceIncludes15LKnapsackPumpDosageAndReplacesLatinNames() = runBlocking {
        fakeKnowledgeDao.insertKnowledge(
            listOf(
                OfflineKnowledgeEntity(
                    id = "KB-COTTON-01",
                    collectionId = "KB01",
                    crop = "Cotton",
                    category = "pesticide",
                    title = "Cotton Bollworm Control",
                    content = "For Helicoverpa armigera control, spray Emamectin Benzoate 5% SG.",
                    sourceCitation = "ICAR CICR Nagpur",
                    language = "en",
                    lastVerifiedDate = "2026-01-01"
                )
            )
        )

        val farmerPhone = "+919876543210"
        val query = "How to control bollworm in Cotton crop?"

        val response = manager.processOfflineSMS(farmerPhone, query, "en")

        assertFalse("Safe agricultural query", response.isSafetyAlert)
        assertTrue(
            "Must start with offline tag",
            response.responseText.startsWith("[OFFLINE CACHED ANSWER - STORED ON DEVICE]")
        )
        // Latin name replaced
        assertFalse(
            "Latin binomial 'Helicoverpa armigera' should be replaced",
            response.responseText.contains("Helicoverpa armigera")
        )
        // Knapsack pump dose included
        assertTrue(
            "Response should include 15L knapsack pump guidance",
            response.responseText.contains("15L", ignoreCase = true) ||
                    response.responseText.contains("pump", ignoreCase = true) ||
                    response.responseText.contains("knapsack", ignoreCase = true)
        )
        assertTrue("Concise length (< 300 chars)", response.responseText.length < 300)
    }

    // =========================================================================
    // 7. OFFLINE OFFICER ESCALATION VIA SMS
    // =========================================================================

    @Test
    fun test7_OfficerEscalationViaSMS() = runBlocking {
        val farmerPhone = "+919876543210"
        val query = "రైతు భరోసా కేంద్రం లేదా విత్తనాల సబ్సిడీ సమస్య"

        val escalation = manager.escalateOfficerViaSMS(farmerPhone, query, "te")

        assertNotNull(escalation)
        assertTrue(
            "Ticket ID must follow standard OFFLINE-ESC-2026- prefix",
            escalation.ticketId.startsWith("OFFLINE-ESC-2026-")
        )
        assertEquals("SAVED_LOCALLY_PENDING_SYNC", escalation.status)
        assertTrue(escalation.isSavedLocally)

        // Verify pending sync ticket saved in knowledgeDao
        val pendingTickets = fakeKnowledgeDao.getUnsyncedTickets()
        assertEquals(1, pendingTickets.size)
        val ticket = pendingTickets.first()
        assertEquals(escalation.ticketId, ticket.localId)
        assertEquals(query, ticket.queryText)
        assertFalse(ticket.isSynced)

        // Verify farmer message and confirmation message in SMS storage
        val unsyncedSMS = fakeSmsDao.getUnsyncedOutgoingMessages()
        assertEquals(1, unsyncedSMS.size)
        val farmerSMS = unsyncedSMS.first()
        assertEquals("SAVED_LOCALLY", farmerSMS.deliveryStatus)
        assertEquals(escalation.ticketId, farmerSMS.ticketNumber)

        val totalMsgs = fakeSmsDao.messages
        assertEquals(2, totalMsgs.size)
        val confSMS = totalMsgs.first { !it.isOutgoing }
        assertEquals("SIMULATED_DELIVERED", confSMS.deliveryStatus)
        assertTrue(confSMS.isOfflineCached)
        assertEquals(escalation.ticketId, confSMS.ticketNumber)
        assertTrue(confSMS.messageText.contains(escalation.ticketId))
        assertTrue(confSMS.messageText.contains("మండల వ్యవసాయ అధికారి"))
    }

    // =========================================================================
    // 8. SYNC PENDING MESSAGES SUCCESS
    // =========================================================================

    @Test
    fun test8_SyncPendingMessagesSuccess() = runBlocking {
        val farmerPhone = "+919876543210"
        // Create 2 offline queries
        manager.processOfflineSMS(farmerPhone, "వరి పంట ఎరువులు", "te")
        manager.processOfflineSMS(farmerPhone, "పత్తిలో తెగులు", "te")

        assertEquals(2, manager.getUnsyncedCount())

        // Sync to backend
        val syncResult = manager.syncPendingMessages(fakeApiService)

        assertEquals(2, syncResult.totalPending)
        assertEquals(2, syncResult.syncedCount)
        assertEquals(0, syncResult.failedCount)
        assertTrue(syncResult.isSuccess)

        // Verify messages in DB are marked as SYNCED
        assertEquals(0, manager.getUnsyncedCount())
        val allMsgs = fakeSmsDao.messages
        val outgoingMsgs = allMsgs.filter { it.isOutgoing }
        assertEquals(2, outgoingMsgs.size)
        assertTrue("All outgoing messages should be SYNCED", outgoingMsgs.all { it.deliveryStatus == "SYNCED" && it.isSynced })

        // Verify API service received the payloads
        assertEquals(2, fakeApiService.receivedRequests.size)
        assertEquals(farmerPhone, fakeApiService.receivedRequests[0].senderNumber)
    }

    // =========================================================================
    // 9. SYNC PENDING MESSAGES WITH BACKEND FAILURE
    // =========================================================================

    @Test
    fun test9_SyncPendingMessagesWithBackendFailure() = runBlocking {
        val farmerPhone = "+919876543210"
        manager.processOfflineSMS(farmerPhone, "టమోటా తెగులు", "te")
        assertEquals(1, manager.getUnsyncedCount())

        // Simulate backend failure (500)
        fakeApiService.shouldFail = true
        val syncResult = manager.syncPendingMessages(fakeApiService)

        assertEquals(1, syncResult.totalPending)
        assertEquals(0, syncResult.syncedCount)
        assertEquals(1, syncResult.failedCount)
        assertFalse(syncResult.isSuccess)

        // Must remain unsynced and locally saved
        assertEquals(1, manager.getUnsyncedCount())
        val msg = fakeSmsDao.getUnsyncedOutgoingMessages().first()
        assertEquals("SAVED_LOCALLY", msg.deliveryStatus)
        assertFalse(msg.isSynced)
    }

    // =========================================================================
    // 10. TELECOM SEGMENT ACCOUNTING GSM-7 AND UCS-2
    // =========================================================================

    @Test
    fun test10_TelecomSegmentAccountingGSM7AndUCS2() = runBlocking {
        val farmerPhone = "+919876543210"

        // ASCII query (GSM-7)
        val asciiResponse = manager.processOfflineSMS(farmerPhone, "Paddy leaf blast control", "en")
        assertEquals("GSM-7", asciiResponse.encoding)

        // Telugu query (UCS-2 Unicode)
        val teluguResponse = manager.processOfflineSMS(farmerPhone, "వరి పంటలో ఆకుమచ్చ తెగులు", "te")
        assertTrue(
            "Telugu text must be UCS-2 Unicode encoding",
            teluguResponse.encoding.contains("UCS-2") || teluguResponse.encoding.contains("Unicode")
        )
    }

    // =========================================================================
    // 11. CLEAR CONVERSATION HISTORY FOR PHONE NUMBER
    // =========================================================================

    @Test
    fun test11_ClearHistoryForPhone() = runBlocking {
        val phone1 = "+919876543210"
        val phone2 = "+919123456789"

        manager.processOfflineSMS(phone1, "Query 1", "en")
        manager.processOfflineSMS(phone2, "Query 2", "en")

        assertEquals(4, manager.getMessageCount())

        manager.clearHistoryForPhone(phone1)

        assertEquals(2, manager.getMessageCount())
        val remaining = fakeSmsDao.messages
        assertTrue(remaining.all { it.senderNumber == phone2 || it.recipientNumber == phone2 })
    }

    // =========================================================================
    // 12. 320 CHARACTERS SEGMENTATION RULES
    // =========================================================================

    @Test
    fun test12_320CharactersSegmentationRules() {
        // 1. GSM-7 320 chars:
        // Single segment: 160. Concatenated: 153 septets per segment.
        // 320 septets = ceil(320 / 153) = 3 segments (NOT 2 segments!)
        val english320 = "A".repeat(320)
        val gsmResult = SmsSegmentCalculator.calculateDetails(english320)
        assertEquals(320, gsmResult.charCount)
        assertEquals(3, gsmResult.segments)
        assertEquals("GSM-7", gsmResult.encoding)

        // 2. UCS-2 Unicode (Telugu) 320 chars:
        // Single segment: 70. Concatenated: 67 code units per segment.
        // 320 chars = ceil(320 / 67) = 5 segments.
        val teluguChar = "ప" // 1 UTF-16 code unit
        val telugu320 = teluguChar.repeat(320)
        val ucs2Result = SmsSegmentCalculator.calculateDetails(telugu320)
        assertEquals(320, ucs2Result.charCount)
        assertEquals(5, ucs2Result.segments)
        assertEquals("UCS-2 (Unicode)", ucs2Result.encoding)
    }

    // =========================================================================
    // 13. MULTILINGUAL UNICODE SEGMENT COUNTING
    // =========================================================================

    @Test
    fun test13_MultilingualUnicodeSegmentCounting() {
        val teluguQuery = "పత్తిలో గులాబీ రంగు పురుగు మందు మోతాదు ఎంత?"
        val teResult = SmsSegmentCalculator.calculateDetails(teluguQuery)
        assertTrue(teResult.isUnicode)
        assertEquals("UCS-2 (Unicode)", teResult.encoding)
        assertTrue(teResult.charCount > 0)
        assertEquals(teResult.charCount, teResult.units)
    }
}

// =============================================================================
// TEST FAKES
// =============================================================================

class FakeSMSMessageDao : SMSMessageDao {
    val messages = mutableListOf<SMSMessageEntity>()

    override suspend fun insertMessage(message: SMSMessageEntity) {
        messages.removeAll { it.id == message.id }
        messages.add(message)
    }

    override suspend fun insertMessages(messages: List<SMSMessageEntity>) {
        messages.forEach { insertMessage(it) }
    }

    override fun getMessagesForPhone(phone: String): Flow<List<SMSMessageEntity>> {
        return flowOf(messages.filter { it.senderNumber == phone || it.recipientNumber == phone }.sortedBy { it.timestamp })
    }

    override fun getAllMessages(): Flow<List<SMSMessageEntity>> {
        return flowOf(messages.sortedBy { it.timestamp })
    }

    override suspend fun getAllMessagesSync(): List<SMSMessageEntity> {
        return messages.sortedBy { it.timestamp }
    }

    override suspend fun getUnsyncedOutgoingMessages(): List<SMSMessageEntity> {
        return messages.filter { !it.isSynced && it.isOutgoing }.sortedBy { it.timestamp }
    }

    override suspend fun markAsSynced(id: String) {
        val index = messages.indexOfFirst { it.id == id }
        if (index != -1) {
            messages[index] = messages[index].copy(isSynced = true, deliveryStatus = "SYNCED")
        }
    }

    override suspend fun updateDeliveryStatus(id: String, status: String) {
        val index = messages.indexOfFirst { it.id == id }
        if (index != -1) {
            messages[index] = messages[index].copy(deliveryStatus = status)
        }
    }

    override suspend fun updateTicketNumber(id: String, ticketNumber: String) {
        val index = messages.indexOfFirst { it.id == id }
        if (index != -1) {
            messages[index] = messages[index].copy(ticketNumber = ticketNumber)
        }
    }

    override suspend fun clearHistoryForPhone(phone: String) {
        messages.removeAll { it.senderNumber == phone || it.recipientNumber == phone }
    }

    override suspend fun clearAll() {
        messages.clear()
    }

    override suspend fun getMessageCount(): Int = messages.size
}

class FakeOfflineKnowledgeDao : OfflineKnowledgeDao {
    val knowledgeList = mutableListOf<OfflineKnowledgeEntity>()
    val pendingTickets = mutableListOf<PendingSyncTicketEntity>()

    override suspend fun getKnowledgeCount(): Int = knowledgeList.size
    override fun getAllKnowledge(): Flow<List<OfflineKnowledgeEntity>> = flowOf(knowledgeList)
    override fun getAllKnowledgeByLang(lang: String): Flow<List<OfflineKnowledgeEntity>> =
        flowOf(knowledgeList.filter { it.language == lang })

    override fun getKnowledgeByCropAndLang(crop: String, lang: String): Flow<List<OfflineKnowledgeEntity>> =
        flowOf(knowledgeList.filter { it.crop.equals(crop, ignoreCase = true) && it.language == lang })

    override suspend fun getKnowledgeByCropSync(crop: String, lang: String): List<OfflineKnowledgeEntity> =
        knowledgeList.filter {
            (it.crop.contains(crop, ignoreCase = true) || crop.contains(it.crop, ignoreCase = true)) &&
                    (it.language == lang || lang.isEmpty())
        }

    override fun getKnowledgeByCategory(category: String, lang: String): Flow<List<OfflineKnowledgeEntity>> =
        flowOf(knowledgeList.filter { it.category == category && it.language == lang })

    override suspend fun searchLocalKnowledge(query: String, lang: String): List<OfflineKnowledgeEntity> =
        knowledgeList.filter {
            (it.content.contains(query, ignoreCase = true) ||
                    it.title.contains(query, ignoreCase = true) ||
                    it.crop.contains(query, ignoreCase = true)) &&
                    (it.language == lang || lang.isEmpty())
        }

    override suspend fun getGeneralFarmingCards(lang: String): List<OfflineKnowledgeEntity> =
        knowledgeList.filter { (it.crop == "Organic" || it.category == "cultivation") && (it.language == lang || lang.isEmpty()) }

    override suspend fun insertKnowledge(items: List<OfflineKnowledgeEntity>) {
        knowledgeList.addAll(items)
    }

    override suspend fun enqueuePendingSyncTicket(ticket: PendingSyncTicketEntity) {
        pendingTickets.removeAll { it.localId == ticket.localId }
        pendingTickets.add(ticket)
    }

    override suspend fun getUnsyncedTickets(): List<PendingSyncTicketEntity> =
        pendingTickets.filter { !it.isSynced }

    override fun getUnsyncedCount(): Flow<Int> = flowOf(pendingTickets.count { !it.isSynced })
    override suspend fun getUnsyncedCountSync(): Int = pendingTickets.count { !it.isSynced }
    override fun getAllPendingTickets(): Flow<List<PendingSyncTicketEntity>> = flowOf(pendingTickets)
    override suspend fun getAllPendingTicketsSync(): List<PendingSyncTicketEntity> = pendingTickets.toList()

    override suspend fun getPendingTicketById(localId: String): PendingSyncTicketEntity? =
        pendingTickets.firstOrNull { it.localId == localId }

    override suspend fun markTicketSyncedWithDetails(localId: String, status: String, ticketNum: String?) {
        val idx = pendingTickets.indexOfFirst { it.localId == localId }
        if (idx != -1) {
            pendingTickets[idx] = pendingTickets[idx].copy(
                isSynced = true,
                syncStatus = status,
                backendTicketNumber = ticketNum
            )
        }
    }

    override suspend fun updateSyncStatus(localId: String, status: String) {
        val idx = pendingTickets.indexOfFirst { it.localId == localId }
        if (idx != -1) {
            pendingTickets[idx] = pendingTickets[idx].copy(syncStatus = status)
        }
    }

    override suspend fun markTicketSynced(localId: String) {
        val idx = pendingTickets.indexOfFirst { it.localId == localId }
        if (idx != -1) {
            pendingTickets[idx] = pendingTickets[idx].copy(isSynced = true, syncStatus = "SENT_TO_OFFICER")
        }
    }
}

class FakeBolKisanApiService : BolKisanApiService {
    var shouldFail = false
    val receivedRequests = mutableListOf<SMSIncomingRequestDto>()

    override suspend fun sendSMSIncoming(request: SMSIncomingRequestDto): Response<SMSIncomingResponseDto> {
        receivedRequests.add(request)
        return if (shouldFail) {
            Response.error(500, "Server Error".toResponseBody())
        } else {
            Response.success(
                SMSIncomingResponseDto(
                    success = true,
                    reply_text = "Synced successfully",
                    ticket_id = null
                )
            )
        }
    }

    override suspend fun getHealth(): Response<BackendHealthDto> = Response.success(BackendHealthDto("ok"))
    override suspend fun requestOtp(request: OTPRequestDto): Response<OTPResponseDto> = throw UnsupportedOperationException()
    override suspend fun verifyOtp(request: OTPVerifyDto): Response<AuthTokenResponseDto> = throw UnsupportedOperationException()
    override suspend fun getCrops(): Response<List<CropDto>> = Response.success(emptyList())
    override suspend fun submitVoiceQuery(token: String, query: VoiceQueryDto): Response<VoiceAnswerDto> = throw UnsupportedOperationException()
    override suspend fun getFarmerTickets(token: String): Response<List<TicketItemDto>> = Response.success(emptyList())
    override suspend fun escalateTicket(token: String, ticket: TicketCreateDto): Response<TicketEscalateResponseDto> = throw UnsupportedOperationException()
    override suspend fun syncOfflineTickets(token: String, request: OfflineSyncRequestDto): Response<OfflineSyncResponseDto> = throw UnsupportedOperationException()
    override suspend fun getTicketMessages(ticketId: String, token: String?): Response<List<TicketMessageDto>> = Response.success(emptyList())
    override suspend fun sendTicketMessage(ticketId: String, body: SendTicketMessageRequest, token: String?): Response<TicketMessageDto> = throw UnsupportedOperationException()
    override suspend fun updateTicketStatus(ticketId: String, body: Map<String, String>, token: String?): Response<Map<String, Any>> = throw UnsupportedOperationException()
    override suspend fun diagnoseDisease(token: String?, file: MultipartBody.Part, cropName: RequestBody, plantPart: RequestBody?, farmerNotes: RequestBody?, language: RequestBody?): Response<DiseaseDiagnosisDto> = throw UnsupportedOperationException()
    override suspend fun getConversations(token: String): Response<List<ConversationSummaryDto>> = Response.success(emptyList())
    override suspend fun createConversation(token: String, request: ConversationCreateDto): Response<ConversationSummaryDto> = throw UnsupportedOperationException()
    override suspend fun getConversationDetail(token: String, conversationId: String): Response<ConversationDetailDto> = throw UnsupportedOperationException()
    override suspend fun postConversationMessage(token: String, conversationId: String, message: MessagePostDto): Response<MessagePostResponseDto> = throw UnsupportedOperationException()
    override suspend fun getMarketPrices(commodity: String?, district: String?, state: String?, market: String?, limit: Int): Response<MandiPricesResponseDto> = throw UnsupportedOperationException()
    override suspend fun getMarketPricesSyncStatus(): Response<MandiSyncStatusDto> = throw UnsupportedOperationException()
    override suspend fun getSMSHistory(phoneNumber: String): Response<SMSHistoryResponseDto> = throw UnsupportedOperationException()
    override suspend fun calculateSMSSegments(text: String): Response<SMSSegmentCalcResponseDto> = throw UnsupportedOperationException()
}
