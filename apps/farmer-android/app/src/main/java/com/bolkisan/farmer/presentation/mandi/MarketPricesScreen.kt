package com.bolkisan.farmer.presentation.mandi

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolkisan.farmer.data.local.entities.MandiPriceEntity
import com.bolkisan.farmer.data.repository.BolKisanRepository
import com.bolkisan.farmer.presentation.common.localizedCropName
import com.bolkisan.farmer.theme.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketPricesScreen(
    repository: BolKisanRepository,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isOnline by repository.isOnline.collectAsState()
    val currentLang by repository.languageFlow.collectAsState()

    var selectedCommodity by remember { mutableStateOf<String?>(null) }
    var selectedState by remember { mutableStateOf<String?>(null) }
    var selectedDistrict by remember { mutableStateOf<String?>(null) }
    var selectedMarket by remember { mutableStateOf<String?>(null) }

    var isRefreshing by remember { mutableStateOf(false) }
    var lastSyncTimestamp by remember { mutableStateOf<Long?>(null) }

    // Dynamic filter options collected from Room DB
    val statesList by repository.getMandiStates().collectAsState(initial = emptyList())
    val districtsList by repository.getMandiDistricts(selectedState).collectAsState(initial = emptyList())
    val commoditiesList by repository.getMandiCommodities().collectAsState(initial = emptyList())
    val marketsList by repository.getMandiMarkets(selectedDistrict).collectAsState(initial = emptyList())

    // Filtered Mandi prices flow from Room DB
    val pricesList by repository.getOfflineMarketPrices(
        state = selectedState,
        district = selectedDistrict,
        commodity = selectedCommodity,
        market = selectedMarket
    ).collectAsState(initial = emptyList())

    // Load initial sync timestamp
    LaunchedEffect(Unit) {
        lastSyncTimestamp = repository.getLastMandiSyncTime()
        // If empty on first launch, seed baseline records immediately
        repository.seedOfflineKnowledgeIfEmpty()
    }

    // Refresh action: Fetch latest prices from FastAPI and upsert to Room
    fun doSync() {
        if (isRefreshing) return
        isRefreshing = true
        coroutineScope.launch {
            val result = repository.syncMarketPrices(
                state = selectedState,
                district = selectedDistrict,
                commodity = selectedCommodity,
                market = selectedMarket
            )
            isRefreshing = false
            lastSyncTimestamp = repository.getLastMandiSyncTime()
            if (result.isSuccess) {
                val count = result.getOrDefault(0)
                val msg = when (currentLang) {
                    "te" -> "$count మార్కెట్ ధరలు విజయవంతంగా నవీకరించబడ్డాయి"
                    "hi" -> "$count मंडी भाव सफलतापूर्वक अपडेट किए गए"
                    else -> "Successfully synchronized $count market price records"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } else {
                val err = when (currentLang) {
                    "te" -> "ఆఫ్‌లైన్: నిల్వ చేసిన మార్కెట్ ధరలు చూపిస్తున్నాము"
                    "hi" -> "ऑफ़लाइन: सहेजे गए मंडी भाव दिखाए जा रहे हैं"
                    else -> "Offline: Showing cached market prices"
                }
                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Evaluate sync age badge
    val now = System.currentTimeMillis()
    val syncAgeMs = lastSyncTimestamp?.let { now - it }
    val isWithin24Hours = syncAgeMs != null && syncAgeMs < (24 * 60 * 60 * 1000L)

    val (badgeText, badgeBg, badgeFg) = when {
        pricesList.isEmpty() -> Triple(
            when (currentLang) {
                "te" -> "డేటా అందుబాటులో లేదు"
                "hi" -> "डेटा उपलब्ध नहीं है"
                else -> "No Data Available"
            },
            Color(0xFFF3F4F6),
            Color(0xFF6B7280)
        )
        isWithin24Hours -> Triple(
            when (currentLang) {
                "te" -> "తాజా ప్రభుత్వ ధరలు (AGMARKNET)"
                "hi" -> "ताजा सरकारी भाव (AGMARKNET)"
                else -> "Latest Govt Data (AGMARKNET)"
            },
            Color(0xFFDCFCE7),
            Color(0xFF14532D)
        )
        else -> Triple(
            when (currentLang) {
                "te" -> "గతంలో నిల్వ చేసిన ధరలు (ఆఫ్‌లైన్)"
                "hi" -> "पहले सहेजे गए भाव (ऑफ़लाइन)"
                else -> "Previously Synced (Offline Cache)"
            },
            Color(0xFFFEF3C7),
            Color(0xFF92400E)
        )
    }

    val screenTitle = when (currentLang) {
        "te" -> "మండి మార్కెట్ ధరలు"
        "hi" -> "मंडी बाजार भाव"
        else -> "Mandi Market Prices"
    }

    val offlineBannerText = when (currentLang) {
        "te" -> "చివరిగా సమకాలీకరించబడిన మార్కెట్ ధరలు చూపిస్తున్నాము. ఇంటర్నెట్ కనెక్షన్ అందుబాటులో లేదు."
        "hi" -> "अंतिम बार समन्वयित मंडी भाव प्रदर्शित किए जा रहे हैं। इंटरनेट कनेक्शन उपलब्ध नहीं है।"
        else -> "Showing last synchronized market prices. Internet connection unavailable."
    }

    // Modal Bottom Sheet state for filter selection
    var filterDialogType by remember { mutableStateOf<String?>(null) } // "state", "district", "commodity", "market"

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardWhite,
                shadowElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, SproutBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = ForestGreenPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = screenTitle,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF14532D)
                            )
                            Text(
                                text = "AGMARKNET / APMC Live & Offline Rates",
                                fontSize = 11.sp,
                                color = MutedSlate
                            )
                        }
                    }

                    // Refresh Button
                    IconButton(
                        onClick = { doSync() },
                        enabled = !isRefreshing,
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = ForestGreenPrimary
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = ForestGreenPrimary
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MintCreamBg)
        ) {
            // 1. Offline Top Warning Banner (Shown when device is offline)
            if (!isOnline) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFFFBEB),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = offlineBannerText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF92400E),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // 2. Status Badge & Sync Timestamp Strip
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardWhite,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, SproutBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = badgeBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, badgeFg.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(badgeFg)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = badgeText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeFg
                            )
                        }
                    }

                    // Sync Time
                    val formattedSyncTime = remember(lastSyncTimestamp) {
                        lastSyncTimestamp?.let {
                            SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(it))
                        } ?: "Pre-populated"
                    }
                    Text(
                        text = "Sync: $formattedSyncTime",
                        fontSize = 11.sp,
                        color = MutedSlate,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 3. Interactive Filter Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardWhite,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // All / Reset Filter Chip
                        val hasActiveFilters = selectedCommodity != null || selectedState != null || selectedDistrict != null || selectedMarket != null
                        FilterChip(
                            selected = !hasActiveFilters,
                            onClick = {
                                selectedCommodity = null
                                selectedState = null
                                selectedDistrict = null
                                selectedMarket = null
                            },
                            label = {
                                Text(
                                    text = when (currentLang) {
                                        "te" -> "అన్ని ధరలు (${pricesList.size})"
                                        "hi" -> "सभी भाव (${pricesList.size})"
                                        else -> "All (${pricesList.size})"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = if (!hasActiveFilters) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ForestGreenPrimary,
                                selectedLabelColor = Color.White
                            )
                        )

                        // Commodity Filter Chip
                        FilterChip(
                            selected = selectedCommodity != null,
                            onClick = { filterDialogType = "commodity" },
                            label = {
                                Text(
                                    text = selectedCommodity?.let { localizedCropName(it, currentLang) }
                                        ?: when (currentLang) {
                                            "te" -> "పంట / Commodity"
                                            "hi" -> "फसल / Commodity"
                                            else -> "Crop"
                                        },
                                    fontSize = 12.sp
                                )
                            },
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )

                        // State Filter Chip
                        FilterChip(
                            selected = selectedState != null,
                            onClick = { filterDialogType = "state" },
                            label = {
                                Text(
                                    text = selectedState ?: when (currentLang) {
                                        "te" -> "రాష్ట్రం / State"
                                        "hi" -> "राज्य / State"
                                        else -> "State"
                                    },
                                    fontSize = 12.sp
                                )
                            },
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )

                        // District Filter Chip
                        FilterChip(
                            selected = selectedDistrict != null,
                            onClick = { filterDialogType = "district" },
                            label = {
                                Text(
                                    text = selectedDistrict ?: when (currentLang) {
                                        "te" -> "జిల్లా / District"
                                        "hi" -> "जिला / District"
                                        else -> "District"
                                    },
                                    fontSize = 12.sp
                                )
                            },
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )

                        // Market Filter Chip
                        FilterChip(
                            selected = selectedMarket != null,
                            onClick = { filterDialogType = "market" },
                            label = {
                                Text(
                                    text = selectedMarket ?: when (currentLang) {
                                        "te" -> "మార్కెట్ / Market"
                                        "hi" -> "मंडी / Market"
                                        else -> "Market"
                                    },
                                    fontSize = 12.sp
                                )
                            },
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }

            // 4. Mandi Prices List
            if (pricesList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, SproutBorder, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = CardWhite)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Storefront,
                                contentDescription = null,
                                tint = MutedSlate,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = when (currentLang) {
                                    "te" -> "ఎంపిక చేసిన ఫిల్టర్లకు మార్కెట్ ధరలు అందుబాటులో లేవు"
                                    "hi" -> "चयनित फिल्टर के लिए कोई मंडी भाव उपलब्ध नहीं है"
                                    else -> "No market prices found for selected filters"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkEarthFg,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    selectedCommodity = null
                                    selectedState = null
                                    selectedDistrict = null
                                    selectedMarket = null
                                    doSync()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (currentLang) {
                                        "te" -> "అన్ని ధరలను చూపించు"
                                        "hi" -> "सभी भाव दिखाएं"
                                        else -> "Show All Prices"
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(pricesList, key = { it.id }) { item ->
                        MandiPriceCard(item = item, currentLang = currentLang)
                    }
                }
            }
        }
    }

    // Filter Selection Bottom Sheet
    if (filterDialogType != null) {
        val dialogType = filterDialogType!!
        val title = when (dialogType) {
            "commodity" -> when (currentLang) {
                "te" -> "పంటను ఎంచుకోండి"
                "hi" -> "फसल का चयन करें"
                else -> "Select Crop"
            }
            "state" -> when (currentLang) {
                "te" -> "రాష్ట్రాన్ని ఎంచుకోండి"
                "hi" -> "राज्य का चयन करें"
                else -> "Select State"
            }
            "district" -> when (currentLang) {
                "te" -> "జిల్లాను ఎంచుకోండి"
                "hi" -> "जिले का चयन करें"
                else -> "Select District"
            }
            "market" -> when (currentLang) {
                "te" -> "మార్కెట్ యార్డును ఎంచుకోండి"
                "hi" -> "मंडी यार्ड का चयन करें"
                else -> "Select Market Yard"
            }
            else -> "Filter"
        }

        val options: List<String> = when (dialogType) {
            "commodity" -> commoditiesList
            "state" -> statesList
            "district" -> districtsList
            "market" -> marketsList
            else -> emptyList()
        }

        ModalBottomSheet(
            onDismissRequest = { filterDialogType = null },
            containerColor = CardWhite,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14532D)
                    )
                    TextButton(
                        onClick = {
                            when (dialogType) {
                                "commodity" -> selectedCommodity = null
                                "state" -> selectedState = null
                                "district" -> selectedDistrict = null
                                "market" -> selectedMarket = null
                            }
                            filterDialogType = null
                        }
                    ) {
                        Text("Clear", color = ForestGreenPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                ) {
                    items(options) { opt ->
                        val isSelected = when (dialogType) {
                            "commodity" -> selectedCommodity == opt
                            "state" -> selectedState == opt
                            "district" -> selectedDistrict == opt
                            "market" -> selectedMarket == opt
                            else -> false
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFFDCFCE7) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when (dialogType) {
                                        "commodity" -> selectedCommodity = opt
                                        "state" -> {
                                            selectedState = opt
                                            selectedDistrict = null
                                            selectedMarket = null
                                        }
                                        "district" -> {
                                            selectedDistrict = opt
                                            selectedMarket = null
                                        }
                                        "market" -> selectedMarket = opt
                                    }
                                    filterDialogType = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (dialogType == "commodity") localizedCropName(opt, currentLang) else opt,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF14532D) else DarkEarthFg
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun MandiPriceCard(
    item: MandiPriceEntity,
    currentLang: String
) {
    val currencyFormat = remember { NumberFormat.getNumberInstance(Locale("en", "IN")) }
    val modalFormatted = currencyFormat.format(item.modalPrice.toLong())
    val minFormatted = currencyFormat.format(item.minPrice.toLong())
    val maxFormatted = currencyFormat.format(item.maxPrice.toLong())

    val (trendText, trendColor) = when (item.priceTrend.lowercase()) {
        "rising" -> Pair(
            when (currentLang) {
                "te" -> "ధర పెరుగుతోంది ↗"
                "hi" -> "भाव बढ़ रहा है ↗"
                else -> "Price Rising ↗"
            },
            Color(0xFF16A34A)
        )
        "falling" -> Pair(
            when (currentLang) {
                "te" -> "ధర తగ్గుతోంది ↘"
                "hi" -> "भाव घट रहा है ↘"
                else -> "Price Falling ↘"
            },
            Color(0xFFDC2626)
        )
        else -> Pair(
            when (currentLang) {
                "te" -> "స్థిరమైన ధర →"
                "hi" -> "स्थिर भाव →"
                else -> "Stable Price →"
            },
            Color(0xFF2563EB)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, SproutBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Crop Name + Variety Chip + Price Trend Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = localizedCropName(item.commodity, currentLang),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF14532D)
                    )
                    if (item.variety.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF1F8F4),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, SproutBorder)
                        ) {
                            Text(
                                text = item.variety,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF14532D),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Trend badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = trendColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = trendText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = trendColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Market & Location
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = ForestGreenPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${item.market}, ${item.district} (${item.state})",
                    fontSize = 12.sp,
                    color = DarkEarthFg,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Large Modal Price Display + Min/Max Band
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Highlighted Modal Price
                Column {
                    Text(
                        text = when (currentLang) {
                            "te" -> "సగటు మోడల్ ధర (Modal)"
                            "hi" -> "औसत मॉडल भाव (Modal)"
                            else -> "Modal Price"
                        },
                        fontSize = 10.5.sp,
                        color = MutedSlate,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "₹$modalFormatted",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF14532D)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (currentLang) {
                                "te" -> "/ క్వింటా"
                                "hi" -> "/ क्विंटल"
                                else -> "/ quintal"
                            },
                            fontSize = 12.sp,
                            color = MutedSlate,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }

                // Min / Max Price Range
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF9FAFB),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = when (currentLang) {
                                "te" -> "ధర పరిధి (Min - Max)"
                                "hi" -> "मूल्य दायरा (Min - Max)"
                                else -> "Min - Max Range"
                            },
                            fontSize = 10.sp,
                            color = MutedSlate
                        )
                        Text(
                            text = "₹$minFormatted - ₹$maxFormatted",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkEarthFg
                        )
                    }
                }
            }

            // Optional MSP Comparison & Arrival Info
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.mspPrice != null && item.mspPrice > 0) {
                    val mspDiff = item.modalPrice - item.mspPrice
                    val mspNote = if (mspDiff >= 0) {
                        "MSP (₹${item.mspPrice.toInt()}) +₹${mspDiff.toInt()}"
                    } else {
                        "MSP (₹${item.mspPrice.toInt()}) -₹${(-mspDiff).toInt()}"
                    }
                    val mspBg = if (mspDiff >= 0) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                    val mspFg = if (mspDiff >= 0) Color(0xFF14532D) else Color(0xFFB91C1C)

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = mspBg
                    ) {
                        Text(
                            text = mspNote,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = mspFg,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Arrival Date & Volume
                Text(
                    text = "రాక: ${item.arrivalDate}" + (if (item.arrivalQuantityQuintals > 0) " (${item.arrivalQuantityQuintals.toInt()} q)" else ""),
                    fontSize = 10.5.sp,
                    color = MutedSlate
                )
            }
        }
    }
}
