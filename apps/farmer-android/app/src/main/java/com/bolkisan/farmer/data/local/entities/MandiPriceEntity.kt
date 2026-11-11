package com.bolkisan.farmer.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mandi_prices",
    indices = [
        Index(value = ["commodity"]),
        Index(value = ["district"]),
        Index(value = ["market"]),
        Index(value = ["arrivalDate"])
    ]
)
data class MandiPriceEntity(
    @PrimaryKey
    val id: String,
    val market: String,
    val state: String,
    val district: String,
    val commodity: String,
    val variety: String = "",
    val grade: String = "",
    val arrivalDate: String,
    val minPrice: Double,
    val maxPrice: Double,
    val modalPrice: Double,
    val arrivalQuantityQuintals: Double = 0.0,
    val mspPrice: Double? = null,
    val priceTrend: String = "stable",
    val lastSyncedAt: Long = System.currentTimeMillis()
)
