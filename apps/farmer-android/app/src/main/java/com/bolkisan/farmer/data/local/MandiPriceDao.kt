package com.bolkisan.farmer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bolkisan.farmer.data.local.entities.MandiPriceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MandiPriceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(prices: List<MandiPriceEntity>)

    @Query("""
        SELECT * FROM mandi_prices
        WHERE (:state IS NULL OR :state = '' OR state = :state)
          AND (:district IS NULL OR :district = '' OR district = :district)
          AND (:commodity IS NULL OR :commodity = '' OR commodity = :commodity)
          AND (:market IS NULL OR :market = '' OR market = :market)
        ORDER BY commodity ASC, arrivalDate DESC
    """)
    fun getFilteredPrices(
        state: String? = null,
        district: String? = null,
        commodity: String? = null,
        market: String? = null
    ): Flow<List<MandiPriceEntity>>

    @Query("SELECT * FROM mandi_prices ORDER BY commodity ASC, arrivalDate DESC")
    fun getAllPrices(): Flow<List<MandiPriceEntity>>

    @Query("SELECT DISTINCT state FROM mandi_prices WHERE state IS NOT NULL AND state != '' ORDER BY state ASC")
    fun getUniqueStates(): Flow<List<String>>

    @Query("""
        SELECT DISTINCT district FROM mandi_prices
        WHERE (:state IS NULL OR :state = '' OR state = :state)
          AND district IS NOT NULL AND district != ''
        ORDER BY district ASC
    """)
    fun getUniqueDistricts(state: String? = null): Flow<List<String>>

    @Query("SELECT DISTINCT commodity FROM mandi_prices WHERE commodity IS NOT NULL AND commodity != '' ORDER BY commodity ASC")
    fun getUniqueCommodities(): Flow<List<String>>

    @Query("""
        SELECT DISTINCT market FROM mandi_prices
        WHERE (:district IS NULL OR :district = '' OR district = :district)
          AND market IS NOT NULL AND market != ''
        ORDER BY market ASC
    """)
    fun getUniqueMarkets(district: String? = null): Flow<List<String>>

    @Query("SELECT MAX(lastSyncedAt) FROM mandi_prices")
    suspend fun getLastSyncedTimestamp(): Long?

    @Query("SELECT COUNT(*) FROM mandi_prices")
    suspend fun getRecordCount(): Int

    @Query("DELETE FROM mandi_prices")
    suspend fun clearAll()
}
