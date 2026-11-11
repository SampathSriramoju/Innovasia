package com.bolkisan.farmer.data.remote.models

import com.bolkisan.farmer.data.local.entities.MandiPriceEntity
import com.google.gson.annotations.SerializedName

data class MandiPriceRecordDto(
    @SerializedName("commodity") val commodity: String,
    @SerializedName("variety") val variety: String = "",
    @SerializedName("market") val market: String,
    @SerializedName("district") val district: String,
    @SerializedName("state") val state: String,
    @SerializedName("arrival_date") val arrivalDate: String,
    @SerializedName("arrival_quantity_quintals") val arrivalQuantityQuintals: Double = 0.0,
    @SerializedName("min_price_inr") val minPriceInr: Double = 0.0,
    @SerializedName("max_price_inr") val maxPriceInr: Double = 0.0,
    @SerializedName("modal_price_inr") val modalPriceInr: Double = 0.0,
    @SerializedName("msp_price_inr") val mspPriceInr: Double? = null,
    @SerializedName("price_trend") val priceTrend: String? = "stable"
) {
    fun toEntity(lastSyncedAt: Long = System.currentTimeMillis()): MandiPriceEntity {
        val rawKey = "${state}_${district}_${market}_${commodity}_${variety}_${arrivalDate}"
        val sanitizedId = rawKey.replace(Regex("[^a-zA-Z0-9_]"), "_").lowercase()
        return MandiPriceEntity(
            id = sanitizedId,
            market = market,
            state = state,
            district = district,
            commodity = commodity,
            variety = variety,
            grade = "",
            arrivalDate = arrivalDate,
            minPrice = minPriceInr,
            maxPrice = maxPriceInr,
            modalPrice = modalPriceInr,
            arrivalQuantityQuintals = arrivalQuantityQuintals,
            mspPrice = mspPriceInr,
            priceTrend = priceTrend ?: "stable",
            lastSyncedAt = lastSyncedAt
        )
    }
}

data class MandiPricesResponseDto(
    @SerializedName("total_records") val totalRecords: Int = 0,
    @SerializedName("data_source") val dataSource: String = "",
    @SerializedName("is_fallback") val isFallback: Boolean = false,
    @SerializedName("as_of_date") val asOfDate: String = "",
    @SerializedName("filters_applied") val filtersApplied: Map<String, Any?>? = null,
    @SerializedName("records") val records: List<MandiPriceRecordDto> = emptyList()
)

data class MandiSyncStatusDto(
    @SerializedName("status") val status: String = "healthy",
    @SerializedName("last_synced_at") val lastSyncedAt: String? = null,
    @SerializedName("total_records") val totalRecords: Int = 0,
    @SerializedName("data_source") val dataSource: String = "",
    @SerializedName("is_fallback") val isFallback: Boolean = false
)
