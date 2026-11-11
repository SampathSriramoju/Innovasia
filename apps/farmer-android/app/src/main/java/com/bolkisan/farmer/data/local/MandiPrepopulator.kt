package com.bolkisan.farmer.data.local

import com.bolkisan.farmer.data.local.entities.MandiPriceEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MandiPrepopulator {

    suspend fun seedMandiPricesIfEmpty(dao: MandiPriceDao) {
        val count = dao.getRecordCount()
        if (count == 0) {
            dao.insertAll(getInitialMandiPrices())
        }
    }

    fun getInitialMandiPrices(): List<MandiPriceEntity> {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val now = System.currentTimeMillis()

        return listOf(
            // --- COTTON (Kapas) ---
            MandiPriceEntity(
                id = "telangana_warangal_warangal_apmc_cotton_bunny",
                commodity = "Cotton",
                variety = "Bunny / Medium Staple",
                grade = "FAQ",
                market = "Warangal APMC",
                district = "Warangal",
                state = "Telangana",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 3250.0,
                minPrice = 6800.0,
                maxPrice = 7650.0,
                modalPrice = 7420.0,
                mspPrice = 7121.0,
                priceTrend = "rising",
                lastSyncedAt = now
            ),
            MandiPriceEntity(
                id = "telangana_adilabad_adilabad_apmc_cotton_mcu5",
                commodity = "Cotton",
                variety = "MCU-5 / Long Staple",
                grade = "FAQ",
                market = "Adilabad APMC",
                district = "Adilabad",
                state = "Telangana",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 2840.0,
                minPrice = 6950.0,
                maxPrice = 7820.0,
                modalPrice = 7580.0,
                mspPrice = 7521.0,
                priceTrend = "stable",
                lastSyncedAt = now
            ),
            MandiPriceEntity(
                id = "gujarat_rajkot_rajkot_apmc_cotton_shankar6",
                commodity = "Cotton",
                variety = "Shankar-6",
                grade = "FAQ",
                market = "Rajkot APMC",
                district = "Rajkot",
                state = "Gujarat",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 4120.0,
                minPrice = 7100.0,
                maxPrice = 7950.0,
                modalPrice = 7680.0,
                mspPrice = 7521.0,
                priceTrend = "rising",
                lastSyncedAt = now
            ),
            MandiPriceEntity(
                id = "maharashtra_amravati_amravati_apmc_cotton_medium",
                commodity = "Cotton",
                variety = "Medium Staple (Kapas)",
                grade = "FAQ",
                market = "Amravati APMC",
                district = "Amravati",
                state = "Maharashtra",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 1950.0,
                minPrice = 6700.0,
                maxPrice = 7500.0,
                modalPrice = 7350.0,
                mspPrice = 7121.0,
                priceTrend = "stable",
                lastSyncedAt = now
            ),

            // --- PADDY (Dhan / Rice) ---
            MandiPriceEntity(
                id = "telangana_suryapet_suryapet_apmc_paddy_bpt5204",
                commodity = "Paddy",
                variety = "BPT-5204 (Samba Mahsuri)",
                grade = "Super Fine",
                market = "Suryapet APMC",
                district = "Suryapet",
                state = "Telangana",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 5400.0,
                minPrice = 2320.0,
                maxPrice = 2850.0,
                modalPrice = 2680.0,
                mspPrice = 2320.0,
                priceTrend = "rising",
                lastSyncedAt = now
            ),
            MandiPriceEntity(
                id = "telangana_nalgonda_miryalaguda_apmc_paddy_gradea",
                commodity = "Paddy",
                variety = "Grade-A (Fine)",
                grade = "Fine",
                market = "Miryalaguda APMC",
                district = "Nalgonda",
                state = "Telangana",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 6200.0,
                minPrice = 2350.0,
                maxPrice = 2720.0,
                modalPrice = 2550.0,
                mspPrice = 2320.0,
                priceTrend = "stable",
                lastSyncedAt = now
            ),
            MandiPriceEntity(
                id = "haryana_karnal_karnal_apmc_paddy_basmati1509",
                commodity = "Paddy",
                variety = "Basmati 1509",
                grade = "Super Fine",
                market = "Karnal APMC",
                district = "Karnal",
                state = "Haryana",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 4800.0,
                minPrice = 3100.0,
                maxPrice = 3750.0,
                modalPrice = 3520.0,
                mspPrice = null,
                priceTrend = "rising",
                lastSyncedAt = now
            ),
            MandiPriceEntity(
                id = "telangana_nizamabad_nizamabad_apmc_paddy_common",
                commodity = "Paddy",
                variety = "Common (Dhan)",
                grade = "Common",
                market = "Nizamabad APMC",
                district = "Nizamabad",
                state = "Telangana",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 3900.0,
                minPrice = 2203.0,
                maxPrice = 2450.0,
                modalPrice = 2380.0,
                mspPrice = 2300.0,
                priceTrend = "stable",
                lastSyncedAt = now
            ),

            // --- CHILLI (Mirchi) ---
            MandiPriceEntity(
                id = "andhra_pradesh_guntur_guntur_apmc_chilli_teja",
                commodity = "Chilli",
                variety = "Teja / S17",
                grade = "Deluxe",
                market = "Guntur APMC (Mirchi Yard)",
                district = "Guntur",
                state = "Andhra Pradesh",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 8500.0,
                minPrice = 17200.0,
                maxPrice = 22500.0,
                modalPrice = 20400.0,
                mspPrice = null,
                priceTrend = "rising",
                lastSyncedAt = now
            ),
            MandiPriceEntity(
                id = "telangana_khammam_khammam_apmc_chilli_teja",
                commodity = "Chilli",
                variety = "Teja (Dry)",
                grade = "FAQ",
                market = "Khammam APMC",
                district = "Khammam",
                state = "Telangana",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 4200.0,
                minPrice = 16500.0,
                maxPrice = 21200.0,
                modalPrice = 19600.0,
                mspPrice = null,
                priceTrend = "stable",
                lastSyncedAt = now
            ),
            MandiPriceEntity(
                id = "karnataka_haveri_byadagi_apmc_chilli_byadagi",
                commodity = "Chilli",
                variety = "Byadagi Dabbi",
                grade = "Special",
                market = "Byadagi APMC",
                district = "Haveri",
                state = "Karnataka",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 2600.0,
                minPrice = 24000.0,
                maxPrice = 36000.0,
                modalPrice = 31500.0,
                mspPrice = null,
                priceTrend = "rising",
                lastSyncedAt = now
            ),

            // --- TOMATO ---
            MandiPriceEntity(
                id = "andhra_pradesh_annamayya_madanapalle_apmc_tomato_hybrid",
                commodity = "Tomato",
                variety = "Hybrid (Local)",
                grade = "Grade 1",
                market = "Madanapalle APMC",
                district = "Annamayya",
                state = "Andhra Pradesh",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 4500.0,
                minPrice = 1400.0,
                maxPrice = 2400.0,
                modalPrice = 1950.0,
                mspPrice = null,
                priceTrend = "falling",
                lastSyncedAt = now
            ),
            MandiPriceEntity(
                id = "karnataka_kolar_kolar_apmc_tomato_red",
                commodity = "Tomato",
                variety = "Hybrid Red",
                grade = "Grade 1",
                market = "Kolar APMC",
                district = "Kolar",
                state = "Karnataka",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 5800.0,
                minPrice = 1500.0,
                maxPrice = 2600.0,
                modalPrice = 2100.0,
                mspPrice = null,
                priceTrend = "stable",
                lastSyncedAt = now
            ),
            MandiPriceEntity(
                id = "telangana_hyderabad_bowenpally_apmc_tomato_hybrid",
                commodity = "Tomato",
                variety = "Hybrid",
                grade = "FAQ",
                market = "Bowenpally APMC",
                district = "Hyderabad",
                state = "Telangana",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 2400.0,
                minPrice = 1800.0,
                maxPrice = 2800.0,
                modalPrice = 2350.0,
                mspPrice = null,
                priceTrend = "stable",
                lastSyncedAt = now
            ),

            // --- WHEAT (Gehun) ---
            MandiPriceEntity(
                id = "madhya_pradesh_sehore_sehore_apmc_wheat_sharbati",
                commodity = "Wheat",
                variety = "Sharbati (Desi)",
                grade = "Special",
                market = "Sehore APMC",
                district = "Sehore",
                state = "Madhya Pradesh",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 3100.0,
                minPrice = 2900.0,
                maxPrice = 3850.0,
                modalPrice = 3400.0,
                mspPrice = 2425.0,
                priceTrend = "rising",
                lastSyncedAt = now
            ),
            MandiPriceEntity(
                id = "punjab_ludhiana_khanna_apmc_wheat_hd2967",
                commodity = "Wheat",
                variety = "HD-2967 / Mill Quality",
                grade = "FAQ",
                market = "Khanna APMC",
                district = "Ludhiana",
                state = "Punjab",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 5200.0,
                minPrice = 2425.0,
                maxPrice = 2680.0,
                modalPrice = 2540.0,
                mspPrice = 2425.0,
                priceTrend = "stable",
                lastSyncedAt = now
            ),

            // --- MAIZE (Makka) ---
            MandiPriceEntity(
                id = "telangana_nizamabad_nizamabad_apmc_maize_yellow",
                commodity = "Maize",
                variety = "Yellow Hybrid",
                grade = "FAQ",
                market = "Nizamabad APMC",
                district = "Nizamabad",
                state = "Telangana",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 3600.0,
                minPrice = 2080.0,
                maxPrice = 2420.0,
                modalPrice = 2260.0,
                mspPrice = 2225.0,
                priceTrend = "rising",
                lastSyncedAt = now
            ),
            MandiPriceEntity(
                id = "telangana_karimnagar_karimnagar_apmc_maize_yellow",
                commodity = "Maize",
                variety = "Yellow",
                grade = "FAQ",
                market = "Karimnagar APMC",
                district = "Karimnagar",
                state = "Telangana",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 2700.0,
                minPrice = 2090.0,
                maxPrice = 2400.0,
                modalPrice = 2280.0,
                mspPrice = 2225.0,
                priceTrend = "rising",
                lastSyncedAt = now
            ),

            // --- SOYBEAN ---
            MandiPriceEntity(
                id = "madhya_pradesh_indore_indore_apmc_soybean_yellow",
                commodity = "Soybean",
                variety = "Yellow (JS-9560 / JS-335)",
                grade = "FAQ",
                market = "Indore APMC (Laxmibai Nagar)",
                district = "Indore",
                state = "Madhya Pradesh",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 6400.0,
                minPrice = 4200.0,
                maxPrice = 4850.0,
                modalPrice = 4620.0,
                mspPrice = 4892.0,
                priceTrend = "falling",
                lastSyncedAt = now
            ),
            MandiPriceEntity(
                id = "maharashtra_latur_latur_apmc_soybean_yellow",
                commodity = "Soybean",
                variety = "Yellow Grade-1",
                grade = "Grade 1",
                market = "Latur APMC",
                district = "Latur",
                state = "Maharashtra",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 4900.0,
                minPrice = 4250.0,
                maxPrice = 4900.0,
                modalPrice = 4690.0,
                mspPrice = 4892.0,
                priceTrend = "stable",
                lastSyncedAt = now
            ),
            MandiPriceEntity(
                id = "telangana_adilabad_adilabad_apmc_soybean_js335",
                commodity = "Soybean",
                variety = "JS-335",
                grade = "FAQ",
                market = "Adilabad APMC",
                district = "Adilabad",
                state = "Telangana",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 2600.0,
                minPrice = 4200.0,
                maxPrice = 4750.0,
                modalPrice = 4550.0,
                mspPrice = 4892.0,
                priceTrend = "stable",
                lastSyncedAt = now
            ),

            // --- CHICKPEA (Gram / Chana) ---
            MandiPriceEntity(
                id = "madhya_pradesh_vidisha_vidisha_apmc_chickpea_desi",
                commodity = "Chickpea",
                variety = "Desi Chana (JG-11)",
                grade = "FAQ",
                market = "Vidisha APMC",
                district = "Vidisha",
                state = "Madhya Pradesh",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 3200.0,
                minPrice = 5800.0,
                maxPrice = 6700.0,
                modalPrice = 6350.0,
                mspPrice = 5650.0,
                priceTrend = "rising",
                lastSyncedAt = now
            ),
            MandiPriceEntity(
                id = "andhra_pradesh_kurnool_kurnool_apmc_chickpea_desi",
                commodity = "Chickpea",
                variety = "Desi Chana",
                grade = "FAQ",
                market = "Kurnool APMC",
                district = "Kurnool",
                state = "Andhra Pradesh",
                arrivalDate = todayStr,
                arrivalQuantityQuintals = 2400.0,
                minPrice = 5750.0,
                maxPrice = 6650.0,
                modalPrice = 6300.0,
                mspPrice = 5650.0,
                priceTrend = "stable",
                lastSyncedAt = now
            )
        )
    }
}
