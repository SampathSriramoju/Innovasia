package com.bolkisan.farmer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 5,
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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pending_sync_tickets ADD COLUMN farmerId TEXT")
                db.execSQL("ALTER TABLE pending_sync_tickets ADD COLUMN conversationContext TEXT")
                db.execSQL("ALTER TABLE pending_sync_tickets ADD COLUMN language TEXT NOT NULL DEFAULT 'te'")
                db.execSQL("ALTER TABLE pending_sync_tickets ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'PENDING_SYNC'")
                db.execSQL("ALTER TABLE pending_sync_tickets ADD COLUMN backendTicketNumber TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bolkisan_farmer.db"
                )
                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .addCallback(KnowledgePrepopulator.getDatabaseCallback { getDatabase(context) })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
