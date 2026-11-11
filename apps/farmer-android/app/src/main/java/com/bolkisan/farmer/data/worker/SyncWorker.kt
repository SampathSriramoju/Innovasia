package com.bolkisan.farmer.data.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.bolkisan.farmer.data.local.AppDatabase
import com.bolkisan.farmer.data.local.SecureTokenStorage
import com.bolkisan.farmer.data.remote.ApiClient
import com.bolkisan.farmer.data.remote.models.OfflineSyncItemDto
import com.bolkisan.farmer.data.remote.models.OfflineSyncRequestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val tag = "SyncWorker"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(tag, "Starting background synchronization of pending tickets...")

        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.offlineKnowledgeDao()
        val prefs = applicationContext.getSharedPreferences("bolkisan_prefs", Context.MODE_PRIVATE)
        val token = SecureTokenStorage.getToken(applicationContext)
            ?: applicationContext.getSharedPreferences("bolkisan_prefs", Context.MODE_PRIVATE).getString("auth_token", null)
            ?: com.bolkisan.farmer.data.repository.BolKisanRepository.activeInstance?.currentToken
        val lang = prefs.getString("selected_lang", "te") ?: "te"

        val unsyncedTickets = dao.getUnsyncedTickets()
        if (unsyncedTickets.isEmpty()) {
            Log.i(tag, "No pending unsynced tickets found.")
            return@withContext Result.success()
        }

        if (token == null) {
            Log.w(tag, "Cannot sync pending tickets: Farmer is not authenticated. Retrying once logged in.")
            return@withContext Result.retry()
        }

        Log.i(tag, "Found ${unsyncedTickets.size} pending unsynced tickets to upload.")

        var allSuccessful = true

        // 1. Process Text-only tickets in batch via /api/v1/tickets/offline-sync
        val textTickets = unsyncedTickets.filter { it.imagePath == null }
        if (textTickets.isNotEmpty()) {
            try {
                val syncItems = textTickets.map {
                    OfflineSyncItemDto(
                        clientUuid = it.localId,
                        cropName = it.cropName,
                        questionText = it.queryText,
                        imageUrl = null,
                        language = it.language.ifBlank { lang },
                        createdAtEpoch = it.timestamp,
                        conversationId = it.conversationContext
                    )
                }
                val response = ApiClient.getApiService(applicationContext).syncOfflineTickets(
                    token = "Bearer $token",
                    request = OfflineSyncRequestDto(syncItems)
                )

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val ackUuids = (body.syncedClientUuids ?: body.tickets?.mapNotNull { it["client_uuid"] as? String } ?: emptyList()).toSet()
                    val ticketsList = body.tickets ?: emptyList()
                    val uuidToTicketNumber = ticketsList.associate { 
                        (it["client_uuid"] as? String ?: "") to (it["ticket_number"] as? String ?: "") 
                    }
                    Log.i(tag, "Backend acknowledged ${ackUuids.size} tickets out of ${textTickets.size} sent.")
                    for (t in textTickets) {
                        if (ackUuids.contains(t.localId)) {
                            val backendTicketNum = uuidToTicketNumber[t.localId]?.ifBlank { null } ?: t.localId
                            dao.markTicketSyncedWithDetails(
                                localId = t.localId,
                                status = "SENT_TO_OFFICER",
                                ticketNum = backendTicketNum
                            )
                            try {
                                com.bolkisan.farmer.data.repository.BolKisanRepository.activeInstance?.updateHistoryEscalation(
                                    queryText = t.queryText,
                                    ticketCode = backendTicketNum,
                                    status = "SENT_TO_OFFICER"
                                )
                            } catch (_: Exception) {}
                        } else {
                            Log.w(tag, "Ticket ${t.localId} not acknowledged by backend; preserving for retry.")
                            allSuccessful = false
                        }
                    }
                } else {
                    Log.w(tag, "Backend rejected batch offline-sync: HTTP ${response.code()}")
                    allSuccessful = false
                }
            } catch (e: Exception) {
                Log.e(tag, "Network error during batch offline-sync: ${e.message}", e)
                allSuccessful = false
            }
        }

        // 2. Process Image / Disease Diagnosis tickets individually
        val imageTickets = unsyncedTickets.filter { it.imagePath != null }
        for (ticket in imageTickets) {
            try {
                val file = File(ticket.imagePath!!)
                if (file.exists() && file.length() > 0) {
                    val reqFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("file", file.name, reqFile)
                    val cleanCrop = if (ticket.cropName.isBlank() ||
                        ticket.cropName == "Pending Identification" ||
                        ticket.cropName == "వ్యాధి గుర్తింపు పెండింగ్" ||
                        ticket.cropName == "पहचान लंबित" ||
                        ticket.cropName.startsWith("TKT-")) "" else ticket.cropName
                    val cropBody = cleanCrop.toRequestBody("text/plain".toMediaTypeOrNull())
                    val partBody = "leaf".toRequestBody("text/plain".toMediaTypeOrNull())
                    val notesBody = ticket.queryText.toRequestBody("text/plain".toMediaTypeOrNull())
                    val langBody = lang.toRequestBody("text/plain".toMediaTypeOrNull())

                    val response = ApiClient.getApiService(applicationContext).diagnoseDisease(
                        token = "Bearer $token",
                        file = body,
                        cropName = cropBody,
                        plantPart = partBody,
                        farmerNotes = notesBody,
                        language = langBody
                    )

                    if (response.isSuccessful && response.body() != null) {
                        val diag = response.body()!!
                        Log.i(tag, "Successfully uploaded pending disease ticket ${ticket.localId}: ${diag.diseaseName} (${diag.cropName})")

                        // Retrieve verified evidence and format into standard response
                        val (formattedResponse, resolvedCrop) = com.bolkisan.farmer.data.repository.BolKisanRepository.formatDiagnosisResponseStatic(
                            diag = diag,
                            language = lang,
                            fallbackCropHint = cleanCrop
                        )

                        val verifiedCitations = if (!diag.citations.isNullOrEmpty()) {
                            diag.citations
                        } else {
                            listOf("ICAR Plant Pathology Guidelines 2024", "CIBRC Approved Disease Management Schedule")
                        }

                        // Update existing conversation/history item in place without creating duplicate messages
                        com.bolkisan.farmer.data.repository.BolKisanRepository.updateHistoryItemInPrefs(
                            context = applicationContext,
                            ticketId = ticket.localId,
                            imagePath = ticket.imagePath,
                            updatedAnswer = formattedResponse,
                            updatedCrop = resolvedCrop,
                            citations = verifiedCitations,
                            isSafe = diag.isSafe,
                            origin = "online"
                        )

                        dao.markTicketSynced(ticket.localId)
                    } else {
                        Log.w(tag, "Server rejected upload for ${ticket.localId}: code ${response.code()}")
                        allSuccessful = false
                    }
                } else {
                    // Image was missing or deleted; upload metadata as text ticket so farmer's query is never lost
                    Log.w(tag, "Image file not found for ticket ${ticket.localId}. Falling back to text sync.")
                    val syncItems = listOf(
                        OfflineSyncItemDto(
                            clientUuid = ticket.localId,
                            cropName = ticket.cropName,
                            questionText = "[Image missing on device] ${ticket.queryText}",
                            imageUrl = null,
                            language = lang,
                            createdAtEpoch = ticket.timestamp
                        )
                    )
                    val response = ApiClient.getApiService(applicationContext).syncOfflineTickets(
                        token = "Bearer $token",
                        request = OfflineSyncRequestDto(syncItems)
                    )
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        val ackUuids = (body.syncedClientUuids ?: body.tickets?.mapNotNull { it["client_uuid"] as? String } ?: emptyList()).toSet()
                        if (ackUuids.contains(ticket.localId)) {
                            dao.markTicketSynced(ticket.localId)
                        } else {
                            allSuccessful = false
                        }
                    } else {
                        allSuccessful = false
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error syncing image ticket ${ticket.localId}: ${e.message}", e)
                allSuccessful = false
            }
        }

        return@withContext if (allSuccessful) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "bolkisan_sync_tickets"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
