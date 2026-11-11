package com.bolkisan.farmer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bolkisan.farmer.data.local.entities.MandiPriceEntity
import com.bolkisan.farmer.data.local.entities.OfflineKnowledgeEntity
import com.bolkisan.farmer.data.local.entities.PendingSyncTicketEntity
import com.bolkisan.farmer.data.local.entities.SMSMessageEntity
import com.bolkisan.farmer.data.local.entities.TicketMessageEntity

@Database(
    entities = [
        OfflineKnowledgeEntity::class,
        PendingSyncTicketEntity::class,
        MandiPriceEntity::class,
        TicketMessageEntity::class,
        SMSMessageEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun offlineKnowledgeDao(): OfflineKnowledgeDao
    abstract fun mandiPriceDao(): MandiPriceDao
    abstract fun ticketMessageDao(): TicketMessageDao
    abstract fun smsMessageDao(): SMSMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bolkisan_farmer.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(KnowledgePrepopulator.getDatabaseCallback { getDatabase(context) })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
