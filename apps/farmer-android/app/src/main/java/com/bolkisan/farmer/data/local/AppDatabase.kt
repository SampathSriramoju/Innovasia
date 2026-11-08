package com.bolkisan.farmer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bolkisan.farmer.data.local.entities.OfflineKnowledgeEntity
import com.bolkisan.farmer.data.local.entities.PendingSyncTicketEntity

@Database(
    entities = [OfflineKnowledgeEntity::class, PendingSyncTicketEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun offlineKnowledgeDao(): OfflineKnowledgeDao

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
                    .addCallback(KnowledgePrepopulator.getDatabaseCallback { getDatabase(context) })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
