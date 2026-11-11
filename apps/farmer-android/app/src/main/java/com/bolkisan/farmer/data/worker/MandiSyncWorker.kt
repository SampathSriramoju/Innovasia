package com.bolkisan.farmer.data.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.bolkisan.farmer.data.local.AppDatabase
import com.bolkisan.farmer.data.local.MandiPrepopulator
import com.bolkisan.farmer.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MandiSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val tag = "MandiSyncWorker"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(tag, "Starting background sync of AGMARKNET Mandi market prices...")

        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.mandiPriceDao()
        val prefs = applicationContext.getSharedPreferences("bolkisan_prefs", Context.MODE_PRIVATE)

        // If local room table is completely empty, ensure baseline verified prices exist first
        MandiPrepopulator.seedMandiPricesIfEmpty(dao)

        try {
            val response = ApiClient.apiService.getMarketPrices(limit = 200)
            if (response.isSuccessful && response.body() != null) {
                val records = response.body()!!.records
                if (records.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    val entities = records.map { it.toEntity(lastSyncedAt = now) }
                    dao.insertAll(entities)
                    prefs.edit().putLong("last_mandi_sync_time", now).apply()
                    Log.i(tag, "Successfully synchronized ${entities.size} mandi market price records from backend.")
                    Result.success()
                } else {
                    Log.w(tag, "Backend returned empty mandi price records. Retaining cached records.")
                    Result.success()
                }
            } else {
                Log.w(tag, "Backend returned HTTP ${response.code()} for mandi sync. Retaining offline cache.")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(tag, "Network error during Mandi prices background sync: ${e.message}. Retaining offline cache.")
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "bolkisan_periodic_mandi_sync"
        private const val ONE_TIME_WORK_NAME = "bolkisan_onetime_mandi_sync"

        /**
         * Enqueues periodic 12-hour background synchronization requiring network connectivity.
         */
        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<MandiSyncWorker>(
                12, TimeUnit.HOURS,
                30, TimeUnit.MINUTES // 30-minute flex interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .addTag(PERIODIC_WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }

        /**
         * Triggers an immediate one-time sync request if connected.
         */
        fun syncOnce(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<MandiSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .addTag(ONE_TIME_WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
        }
    }
}
