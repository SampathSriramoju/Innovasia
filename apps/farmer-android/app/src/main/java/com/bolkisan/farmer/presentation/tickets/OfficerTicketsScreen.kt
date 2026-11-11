package com.bolkisan.farmer.presentation.tickets

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolkisan.farmer.data.remote.models.TicketItemDto
import com.bolkisan.farmer.data.remote.models.TicketMessageDto
import com.bolkisan.farmer.data.repository.BolKisanRepository
import com.bolkisan.farmer.presentation.common.appStrings
import com.bolkisan.farmer.presentation.common.localizedCropName
import com.bolkisan.farmer.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficerTicketsScreen(
    repository: BolKisanRepository,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val currentLang by repository.languageFlow.collectAsState()
    val strings = remember(currentLang) { appStrings(currentLang) }

    var tickets by remember { mutableStateOf<List<TicketItemDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedTicket by remember { mutableStateOf<TicketItemDto?>(null) }

    // Intercept back button when chat is open to return to tickets list
    BackHandler(enabled = selectedTicket != null) {
        selectedTicket = null
    }

    fun fetchTickets(showLoading: Boolean = false) {
        if (showLoading) loading = true
        coroutineScope.launch {
            val res = repository.getTickets()
            loading = false
            if (res.isSuccess) {
                val list = res.getOrNull() ?: emptyList()
                tickets = list
                // If a ticket was selected, update its reference with latest status/fields
                selectedTicket?.let { sel ->
                    val updated = list.firstOrNull { it.id == sel.id }
                    if (updated != null) {
                        selectedTicket = updated
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchTickets(showLoading = true)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MintCreamBg
    ) {
        if (selectedTicket != null) {
            // Interactive Farmer–Officer Chat View
            TicketChatView(
                ticket = selectedTicket!!,
                repository = repository,
                currentLang = currentLang,
                onBack = {
                    selectedTicket = null
                    fetchTickets(showLoading = false)
                }
            )
        } else {
            // Tickets Overview List
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = DarkEarthFg)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = strings.ticketsTitle,
                                style = Typography.titleLarge,
                                color = DarkEarthFg
                            )
                            Text(
                                text = strings.ticketsSubtitle,
                                fontSize = 12.sp,
                                color = MutedSlate
                            )
                        }
                    }

                    IconButton(onClick = { fetchTickets(showLoading = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ForestGreenPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ForestGreenPrimary)
                    }
                } else if (tickets.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SupportAgent,
                                contentDescription = "No tickets",
                                tint = ForestGreenPrimary,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = strings.noTickets,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = DarkEarthFg
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = strings.noTicketsHint,
                                fontSize = 12.sp,
                                color = MutedSlate
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(tickets) { t ->
                            TicketItemCard(
                                ticket = t,
                                currentLang = currentLang,
                                onClick = { selectedTicket = t }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TicketItemCard(
    ticket: TicketItemDto,
    currentLang: String,
    onClick: () -> Unit
) {
    val normStatus = ticket.status.uppercase()
    val isResolved = normStatus in setOf("RESOLVED", "CLOSED")
    val isResponded = normStatus in setOf("OFFICER RESPONDED", "RESPONDED", "WAITING_FOR_FARMER")

    val (badgeBg, badgeFg, badgeText) = when {
        isResolved -> Triple(
            Color(0xFFDCFCE7),
            Color(0xFF15803D),
            when (currentLang) {
                "te" -> "పరిష్కరించబడింది"
                "hi" -> "समाधान हो गया"
                else -> "RESOLVED"
            }
        )
        isResponded -> Triple(
            Color(0xFFE0F2FE),
            Color(0xFF0369A1),
            when (currentLang) {
                "te" -> "అధికారి సమాధానం ఇచ్చారు"
                "hi" -> "अधिकारी ने उत्तर दिया"
                else -> "OFFICER RESPONDED"
            }
        )
        else -> Triple(
            Color(0xFFFEF3C7),
            Color(0xFFB45309),
            when (currentLang) {
                "te" -> "పరిశీలనలో ఉంది"
                "hi" -> "समीक्षा में है"
                else -> "PENDING"
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, if (isResolved) SproutBorder else if (isResponded) Color(0xFFBAE6FD) else Color(0xFFFDE68A), RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ticket.ticketNumber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF14532D)
                    ) {
                        Text(
                            text = localizedCropName(ticket.displayCrop, currentLang),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = ticket.transcript,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = DarkEarthFg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val rx = ticket.officerResponse ?: ticket.resolutionPrescription
            if (!rx.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(0.5.dp, Color(0xFF86EFAC)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = ForestGreenPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = rx,
                            fontSize = 12.sp,
                            color = Color(0xFF14532D),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Action Row: Open Officer Chat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Chat,
                        contentDescription = null,
                        tint = ForestGreenPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (currentLang) {
                            "te" -> "అధికారితో చాట్ చేయండి"
                            "hi" -> "अधिकारी से चैट करें"
                            else -> "Chat with Extension Officer"
                        },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenPrimary
                    )
                }

                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MutedSlate,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Interactive Farmer–Officer Chat View
 */
@Composable
fun TicketChatView(
    ticket: TicketItemDto,
    repository: BolKisanRepository,
    currentLang: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf<List<TicketMessageDto>>(emptyList()) }
    var isLoadingMessages by remember { mutableStateOf(true) }
    var isSending by remember { mutableStateOf(false) }
    var inputMessage by remember { mutableStateOf("") }
    var attachedMediaUrl by remember { mutableStateOf<String?>(null) }
    var attachedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val tempFile = File(context.cacheDir, "crop_chat_${System.currentTimeMillis()}.jpg")
                val outputStream = FileOutputStream(tempFile)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()

                val bm = BitmapFactory.decodeFile(tempFile.absolutePath)
                if (bm != null) {
                    attachedBitmap = bm.asImageBitmap()
                    attachedMediaUrl = tempFile.absolutePath
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Could not attach image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Refresh messages
    fun fetchMessages(scrollAfter: Boolean = false) {
        coroutineScope.launch {
            val res = repository.getTicketMessages(ticket.id)
            isLoadingMessages = false
            if (res.isSuccess) {
                val newMessages = res.getOrNull() ?: emptyList()
                if (newMessages.isNotEmpty()) {
                    val hadFewer = newMessages.size > messages.size
                    messages = newMessages
                    if (hadFewer || scrollAfter) {
                        coroutineScope.launch {
                            delay(100)
                            if (messages.isNotEmpty()) {
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        }
                    }
                }
            }
        }
    }

    // Periodic Polling: fetch every 4 seconds so officer replies appear automatically
    LaunchedEffect(ticket.id) {
        fetchMessages(scrollAfter = true)
        while (isActive) {
            delay(4000)
            fetchMessages(scrollAfter = false)
        }
    }

    // Send Message Handler
    fun sendMessage() {
        val textToSend = inputMessage.trim()
        val mediaToSend = attachedMediaUrl
        if (textToSend.isBlank() && mediaToSend == null) return

        isSending = true
        inputMessage = ""
        attachedMediaUrl = null
        attachedBitmap = null

        coroutineScope.launch {
            val res = repository.sendTicketMessage(
                ticketId = ticket.id,
                content = if (textToSend.isNotBlank()) textToSend else "Attached field crop photo",
                mediaUrl = mediaToSend
            )
            isSending = false
            if (res.isSuccess) {
                val created = res.getOrNull()
                if (created != null && !messages.any { it.id == created.id }) {
                    messages = messages + created
                }
                coroutineScope.launch {
                    delay(100)
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
            } else {
                Toast.makeText(context, "Failed to send: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val normStatus = ticket.status.uppercase()
    val isResolved = normStatus in setOf("RESOLVED", "CLOSED")
    val isResponded = normStatus in setOf("OFFICER RESPONDED", "RESPONDED", "WAITING_FOR_FARMER")

    val (badgeBg, badgeFg, badgeText) = when {
        isResolved -> Triple(
            Color(0xFFDCFCE7),
            Color(0xFF15803D),
            when (currentLang) {
                "te" -> "పరిష్కరించబడింది"
                "hi" -> "समाधान"
                else -> "RESOLVED"
            }
        )
        isResponded -> Triple(
            Color(0xFFE0F2FE),
            Color(0xFF0369A1),
            when (currentLang) {
                "te" -> "అధికారి స్పందించారు"
                "hi" -> "अधिकारी उत्तर"
                else -> "OFFICER RESPONDED"
            }
        )
        else -> Triple(
            Color(0xFFFEF3C7),
            Color(0xFFB45309),
            when (currentLang) {
                "te" -> "పరిశీలనలో ఉంది"
                "hi" -> "प्रतीक्षारत"
                else -> "PENDING"
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Chat Header
        Surface(
            color = CardWhite,
            shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = DarkEarthFg)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = ticket.ticketNumber,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF14532D)
                            ) {
                                Text(
                                    text = localizedCropName(ticket.displayCrop, currentLang),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (currentLang) {
                                "te" -> "మండల వ్యవసాయ విస్తరణ అధికారి (MAO)"
                                "hi" -> "मंडल कृषि विस्तार अधिकारी (MAO)"
                                else -> "Mandal Agricultural Extension Officer (MAO)"
                            },
                            fontSize = 11.sp,
                            color = MutedSlate
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = { fetchMessages(scrollAfter = false) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ForestGreenPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // Messages List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            if (isLoadingMessages && messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ForestGreenPrimary)
                }
            } else if (messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Default.SupportAgent, contentDescription = null, tint = ForestGreenPrimary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = when (currentLang) {
                                "te" -> "మీ ప్రశ్న ప్రభుత్వ వ్యవసాయ అధికారికి పంపబడింది"
                                "hi" -> "आपका प्रश्न कृषि विस्तार अधिकारी को भेज दिया गया है"
                                else -> "Your ticket is assigned to Agricultural Extension Officer"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkEarthFg
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (currentLang) {
                                "te" -> "అధికారి త్వరలోనే ప్రత్యుత్తరం ఇస్తారు. ఇక్కడ మీరు సందేశాలు పంపవచ్చు."
                                "hi" -> "अधिकारी जल्द ही उत्तर देंगे। आप यहाँ अतिरिक्त विवरण भेज सकते हैं।"
                                else -> "The officer will reply shortly. Type below to send details or photos."
                            },
                            fontSize = 11.5.sp,
                            color = MutedSlate,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 12.dp)
                ) {
                    items(messages) { msg ->
                        ChatMessageItem(msg = msg, currentLang = currentLang)
                    }
                }
            }
        }

        // Attachment Preview bar (if photo attached)
        if (attachedBitmap != null || attachedMediaUrl != null) {
            Surface(
                color = Color(0xFFF1F5F9),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (attachedBitmap != null) {
                            Image(
                                bitmap = attachedBitmap!!,
                                contentDescription = "Attached photo preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        } else {
                            Icon(Icons.Default.Image, contentDescription = null, tint = ForestGreenPrimary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (currentLang) {
                                "te" -> "పంట ఫోటో జోడించబడింది"
                                "hi" -> "फसल की तस्वीर संलग्न है"
                                else -> "Crop photo attached"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = DarkEarthFg
                        )
                    }

                    IconButton(
                        onClick = {
                            attachedBitmap = null
                            attachedMediaUrl = null
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove attachment", tint = HazardRed, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Bottom Chat Input Bar
        Surface(
            color = CardWhite,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Photo Attachment Button
                IconButton(
                    onClick = { photoPickerLauncher.launch("image/*") },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.AddPhotoAlternate,
                        contentDescription = "Attach photo",
                        tint = ForestGreenPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Quick Field Sample Photo Button (for quick testing)
                IconButton(
                    onClick = {
                        try {
                            val sampleFile = File(context.cacheDir, "sample_leaf_inspection.jpg")
                            if (!sampleFile.exists()) {
                                sampleFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
                            }
                            attachedMediaUrl = sampleFile.absolutePath
                            Toast.makeText(context, "Field sample photo selected", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            attachedMediaUrl = "https://images.unsplash.com/photo-1597848212624-a19eb35e2651?w=500"
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Sample crop photo",
                        tint = ForestGreenPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Text Input
                OutlinedTextField(
                    value = inputMessage,
                    onValueChange = { inputMessage = it },
                    placeholder = {
                        Text(
                            text = when (currentLang) {
                                "te" -> "అధికారికి సందేశం రాయండి..."
                                "hi" -> "अधिकारी को संदेश लिखें..."
                                else -> "Type your message to officer..."
                            },
                            fontSize = 13.sp,
                            color = MutedSlate
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestGreenPrimary,
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Send Button
                IconButton(
                    onClick = { sendMessage() },
                    enabled = !isSending && (inputMessage.isNotBlank() || attachedMediaUrl != null),
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (inputMessage.isNotBlank() || attachedMediaUrl != null) ForestGreenPrimary else Color(0xFFE2E8F0),
                            CircleShape
                        )
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (inputMessage.isNotBlank() || attachedMediaUrl != null) Color.White else MutedSlate,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Chat Message Bubble (Farmer aligned right, Officer aligned left)
 */
@Composable
fun ChatMessageItem(
    msg: TicketMessageDto,
    currentLang: String
) {
    val isFarmer = msg.sender_type.uppercase() == "FARMER"

    val alignment = if (isFarmer) Alignment.End else Alignment.Start
    val bubbleColor = if (isFarmer) Color(0xFFDCFCE7) else Color(0xFFF0FDF4)
    val borderColor = if (isFarmer) ForestGreenPrimary.copy(alpha = 0.3f) else Color(0xFF38BDF8)
    val textColor = if (isFarmer) Color(0xFF14532D) else DarkEarthFg

    val formattedTime = remember(msg.created_at) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val date = parser.parse(msg.created_at.substringBefore(".")) ?: Date()
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            ""
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isFarmer) 14.dp else 2.dp,
                bottomEnd = if (isFarmer) 2.dp else 14.dp
            ),
            color = bubbleColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Officer Badge Header (if officer message)
                if (!isFarmer) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF0284C7)
                        ) {
                            Text(
                                text = "🏛️ MAO",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = msg.sender_name.ifBlank { "Mandal Agricultural Officer" },
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0369A1)
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Text(
                            text = when (currentLang) {
                                "te" -> "రైతు (మీరు)"
                                "hi" -> "किसान (आप)"
                                else -> "Farmer (You)"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreenPrimary
                        )
                    }
                }

                // If media attached, display image preview thumbnail
                if (!msg.media_url.isNullOrBlank()) {
                    MessageThumbnailView(mediaUrl = msg.media_url)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Message Text
                Text(
                    text = msg.content,
                    fontSize = 13.5.sp,
                    color = textColor,
                    lineHeight = 19.sp
                )

                // Timestamp
                if (formattedTime.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formattedTime,
                        fontSize = 9.5.sp,
                        color = MutedSlate,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

/**
 * Thumbnail renderer for chat attachments
 */
@Composable
fun MessageThumbnailView(mediaUrl: String) {
    var bitmap by remember(mediaUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(mediaUrl) {
        withContext(Dispatchers.IO) {
            try {
                if (mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://")) {
                    val url = java.net.URL(mediaUrl)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.doInput = true
                    conn.connect()
                    val input = conn.inputStream
                    val bm = BitmapFactory.decodeStream(input)
                    bm?.let { bitmap = it.asImageBitmap() }
                } else {
                    val file = File(mediaUrl)
                    if (file.exists()) {
                        val bm = BitmapFactory.decodeFile(file.absolutePath)
                        bm?.let { bitmap = it.asImageBitmap() }
                    }
                }
            } catch (e: Exception) {
                // Fallback gracefully
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = "Message Attachment",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFE2E8F0),
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = MutedSlate, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("📷 Field Crop Photo", fontSize = 11.5.sp, color = MutedSlate, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
