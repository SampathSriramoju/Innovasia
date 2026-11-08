package com.bolkisan.farmer.data.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.bolkisan.farmer.data.local.AppDatabase
import com.bolkisan.farmer.data.remote.ApiClient
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
        val token = prefs.getString("jwt_token", null)
        val lang = prefs.getString("selected_lang", "te") ?: "te"

        val unsyncedTickets = dao.getUnsyncedTickets()
        if (unsyncedTickets.isEmpty()) {
            Log.i(tag, "No pending unsynced tickets found.")
            return@withContext Result.success()
        }

        Log.i(tag, "Found ${unsyncedTickets.size} pending unsynced tickets to upload.")

        var allSuccessful = true

        for (ticket in unsyncedTickets) {
            try {
                if (ticket.imagePath != null) {
                    val file = File(ticket.imagePath)
                    if (file.exists()) {
                        val reqFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                        val body = MultipartBody.Part.createFormData("file", file.name, reqFile)
                        val cropBody = ticket.cropName.toRequestBody("text/plain".toMediaTypeOrNull())
                        val langBody = lang.toRequestBody("text/plain".toMediaTypeOrNull())
                        val authHeader = token?.let { "Bearer $it" }

                        val response = ApiClient.apiService.diagnoseDisease(
                            token = authHeader,
                            file = body,
                            cropName = cropBody,
                            language = langBody
                        )

                        if (response.isSuccessful) {
                            Log.i(tag, "Successfully uploaded pending disease ticket ${ticket.localId}")
                            dao.markTicketSynced(ticket.localId)
                        } else {
                            Log.w(tag, "Server rejected upload for ${ticket.localId}: code ${response.code()}")
                            allSuccessful = false
                        }
                    } else {
                        // Image file was removed or missing, mark as synced to prevent infinite loop
                        Log.w(tag, "Image file not found for ticket ${ticket.localId}, marking synced")
                        dao.markTicketSynced(ticket.localId)
                    }
                } else {
                    // Voice or text query ticket
                    Log.i(tag, "Synced text ticket ${ticket.localId}")
                    dao.markTicketSynced(ticket.localId)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error syncing ticket ${ticket.localId}: ${e.message}", e)
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
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                workRequest
            )
            Log.i("SyncWorker", "Enqueued background sync worker with NetworkType.CONNECTED constraint.")
        }
    }
}
