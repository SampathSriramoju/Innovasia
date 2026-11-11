package com.bolkisan.farmer

import android.app.Application
import com.bolkisan.farmer.data.repository.BolKisanRepository
import com.bolkisan.farmer.data.worker.MandiSyncWorker
import com.bolkisan.farmer.data.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BolKisanApp : Application() {

    lateinit var repository: BolKisanRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = BolKisanRepository(this)

        // Seed initial offline ICAR & CIBRC knowledge on IO thread with background priority
        CoroutineScope(Dispatchers.IO).launch {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            repository.seedOfflineKnowledgeIfEmpty()
            // Evaluate device hardware capabilities and offline subsystem readiness
            com.bolkisan.farmer.domain.OfflineModelManager.getOfflineCapabilities(this@BolKisanApp)
        }

        // Enqueue background sync workers
        SyncWorker.enqueue(this)
        MandiSyncWorker.enqueuePeriodic(this)
    }
}
