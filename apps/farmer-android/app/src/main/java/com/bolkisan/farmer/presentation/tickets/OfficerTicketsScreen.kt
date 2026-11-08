package com.bolkisan.farmer.presentation.tickets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolkisan.farmer.data.remote.models.TicketItemDto
import com.bolkisan.farmer.data.repository.BolKisanRepository
import com.bolkisan.farmer.presentation.common.appStrings
import com.bolkisan.farmer.theme.*
import kotlinx.coroutines.launch

@Composable
fun OfficerTicketsScreen(
    repository: BolKisanRepository,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val currentLang by repository.languageFlow.collectAsState()
    val strings = appStrings(currentLang)
    var tickets by remember { mutableStateOf<List<TicketItemDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun fetchTickets() {
        loading = true
        coroutineScope.launch {
            val res = repository.getTickets()
            loading = false
            if (res.isSuccess) {
                tickets = res.getOrNull() ?: emptyList()
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchTickets()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MintCreamBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = DarkEarthFg)
                }
                Spacer(modifier = Modifier.width(8.dp))
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

            Spacer(modifier = Modifier.height(12.dp))

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ForestGreenPrimary)
                }
            } else if (tickets.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SupportAgent, contentDescription = "No tickets", tint = ForestGreenPrimary, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = strings.noTickets,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = DarkEarthFg
                        )
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
                        val isResolved = t.status == "resolved"
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, if (isResolved) SproutBorder else Color(0xFFFDE68A), RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = CardWhite)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = t.ticketNumber,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ForestGreenPrimary
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isResolved) MintCreamBg else Color(0xFFFEF3C7)
                                    ) {
                                        Text(
                                            text = if (isResolved) strings.ticketResolved else strings.ticketReviewing,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isResolved) ForestGreenPrimary else Color(0xFFD97706),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = strings.yourQuestion(t.transcript),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = DarkEarthFg,
                                    modifier = Modifier.padding(top = 8.dp)
                                )

                                t.resolutionPrescription?.let { prescription ->
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MintCreamBg,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = strings.officerPrescription,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ForestGreenPrimary
                                                )
                                                Surface(
                                                    shape = CircleShape,
                                                    color = CardWhite,
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.VolumeUp, contentDescription = "Listen", tint = ForestGreenPrimary, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                            Text(
                                                text = prescription,
                                                fontSize = 13.sp,
                                                color = DarkEarthFg,
                                                modifier = Modifier.padding(top = 4.dp),
                                                lineHeight = 18.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
