package com.bolkisan.farmer.presentation.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolkisan.farmer.domain.OfflineModelManager
import com.bolkisan.farmer.domain.OfflineModelStatus

enum class NetworkEngineState {
    ONLINE,             // 1) "ONLINE (FastAPI Cloud/LAN)" - green dot
    OFFLINE_SLM,        // 2) "OFFLINE SLM (Qwen3-0.6B On-Device AI)" - blue/purple dot (when offline and SLM installed + RAM >= 4GB)
    OFFLINE_KNOWLEDGE   // 3) "OFFLINE KNOWLEDGE (Room SQLite ICAR)" - orange/amber dot (when offline and SQLite fallback)
}

@Composable
fun NetworkStatusBar(
    isOnline: Boolean,
    language: String = "te",
    modifier: Modifier = Modifier,
    engineState: NetworkEngineState? = null,
    isVisionAvailable: Boolean? = null,
    outboxCount: Int = 0
) {
    val context = LocalContext.current
    val modelStatus by OfflineModelManager.modelStatus.collectAsState()

    val currentEngineState = remember(isOnline, engineState, modelStatus) {
        if (engineState != null) {
            engineState
        } else if (isOnline) {
            NetworkEngineState.ONLINE
        } else {
            val caps = OfflineModelManager.getOfflineCapabilities(context)
            val canRunSlm = (modelStatus == OfflineModelStatus.READY || caps.modelStatus == OfflineModelStatus.READY) &&
                    caps.totalRamGb >= 4 && caps.freeRamMb >= 800
            if (canRunSlm) NetworkEngineState.OFFLINE_SLM else NetworkEngineState.OFFLINE_KNOWLEDGE
        }
    }

    val stateConfig = when (currentEngineState) {
        NetworkEngineState.ONLINE -> {
            val title = when (language) {
                "te" -> "NETWORK: ONLINE (FastAPI Cloud/LAN)"
                "hi" -> "NETWORK: ONLINE (FastAPI Cloud/LAN)"
                else -> "NETWORK: ONLINE (FastAPI Cloud/LAN)"
            }
            val subtitle = when {
                isVisionAvailable == true -> when (language) {
                    "te" -> "క్లౌడ్ నెట్‌వర్క్ & Gemini Vision అందుబాటులో ఉన్నాయి"
                    "hi" -> "क्लाउड नेटवर्क और जेमिनी विज़न उपलब्ध हैं"
                    else -> "Cloud Network & Gemini Vision verified available"
                }
                isVisionAvailable == false -> when (language) {
                    "te" -> "నెట్‌వర్క్ కనెక్ట్ అయింది • ఫోటో విశ్లేషణ ఆఫ్‌లైన్ నిల్వలో ఉంది"
                    "hi" -> "नेटवर्क कनेक्टेड • फोटो विश्लेषण ऑफ़लाइन कतारबद्ध है"
                    else -> "Network connected • Vision analysis queued in Outbox"
                }
                else -> when (language) {
                    "te" -> "క్లౌడ్ & AI నెట్‌వర్క్ అనుసంధానించబడింది"
                    "hi" -> "क्लाउड व AI नेटवर्क से जुड़ा है"
                    else -> "Cloud & AI Server Connected"
                }
            }
            StateConfig(
                bgColor = Color(0xFFDCFCE7),
                borderColor = Color(0xFF86EFAC),
                textColor = Color(0xFF14532D),
                dotColor = Color(0xFF16A34A), // Green dot
                title = title,
                subtitle = subtitle,
                icon = Icons.Default.CloudDone
            )
        }
        NetworkEngineState.OFFLINE_SLM -> {
            val title = when (language) {
                "te" -> "OFFLINE SLM (Qwen3-0.6B On-Device AI)"
                "hi" -> "OFFLINE SLM (Qwen3-0.6B On-Device AI)"
                else -> "OFFLINE SLM (Qwen3-0.6B On-Device AI)"
            }
            val subtitle = when (language) {
                "te" -> "పరికరంలో నడిచే మోడల్ (టెక్స్ట్ మాత్రమే • ఫోటోలు అవుట్‌బాక్స్‌లో నిల్వ)"
                "hi" -> "ऑन-डिवाइस मॉडल (केवल टेक्स्ट • फोटो आउटबॉक्स में कतारबद्ध)"
                else -> "Edge SLM active (Text only • Photos queued in Outbox)"
            }
            StateConfig(
                bgColor = Color(0xFFF5F3FF),
                borderColor = Color(0xFFDDD6FE),
                textColor = Color(0xFF5B21B6),
                dotColor = Color(0xFF7C3AED), // Blue/Purple dot
                title = title,
                subtitle = subtitle,
                icon = Icons.Default.Bolt
            )
        }
        NetworkEngineState.OFFLINE_KNOWLEDGE -> {
            val title = when (language) {
                "te" -> "OFFLINE KNOWLEDGE (Room SQLite ICAR)"
                "hi" -> "OFFLINE KNOWLEDGE (Room SQLite ICAR)"
                else -> "OFFLINE KNOWLEDGE (Room SQLite ICAR)"
            }
            val subtitle = when (language) {
                "te" -> "ధృవీకరించిన ICAR స్థానిక డేటాబేస్ (ఆఫ్‌లైన్ ఫాల్‌బ్యాక్)"
                "hi" -> "सत्यापित ICAR स्थानीय ज्ञानकोश (ऑफ़लाइन फ़ॉलबैक)"
                else -> "Verified ICAR local database fallback active"
            }
            StateConfig(
                bgColor = Color(0xFFFFFBEB),
                borderColor = Color(0xFFFDE68A),
                textColor = Color(0xFF92400E),
                dotColor = Color(0xFFD97706), // Orange/Amber dot
                title = title,
                subtitle = subtitle,
                icon = Icons.Default.CloudOff
            )
        }
    }

    val animatedBg by animateColorAsState(targetValue = stateConfig.bgColor, label = "network_bg")
    val animatedBorder by animateColorAsState(targetValue = stateConfig.borderColor, label = "network_border")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, animatedBorder, RoundedCornerShape(12.dp)),
        color = animatedBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Status indicator pulse dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(stateConfig.dotColor)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = stateConfig.title,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = stateConfig.textColor
                        )

                        Text(
                            text = stateConfig.subtitle,
                            fontSize = 10.sp,
                            color = stateConfig.textColor.copy(alpha = 0.85f)
                        )
                    }
                }

                Icon(
                    imageVector = stateConfig.icon,
                    contentDescription = stateConfig.title,
                    tint = stateConfig.dotColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Separated Status Badges (Task 3):
            // Shows distinct badges for Gemini Vision availability and Outbox Queue Status
            if (isVisionAvailable != null || outboxCount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Vision Status Badge (Never display generic ONLINE as proof that Gemini Vision is available!)
                    val visionReady = isVisionAvailable == true
                    val visionBadgeBg = if (visionReady) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
                    val visionBadgeBorder = if (visionReady) Color(0xFF86EFAC) else Color(0xFFFCD34D)
                    val visionBadgeFg = if (visionReady) Color(0xFF14532D) else Color(0xFF92400E)
                    val visionDot = if (visionReady) Color(0xFF16A34A) else Color(0xFFD97706)
                    val visionText = if (visionReady) {
                        "👁️ Vision: Cloud Active"
                    } else {
                        "📷 Vision: Offline Queued"
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = visionBadgeBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, visionBadgeBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(visionDot)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = visionText,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = visionBadgeFg
                            )
                        }
                    }

                    // Outbox Sync Status Badge
                    if (outboxCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFFFBEB),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCD34D))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFD97706))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "⏳ Outbox: $outboxCount Pending",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF0FDF4),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0))
                        ) {
                            Text(
                                text = "✓ Outbox: Synced",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF166534),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class StateConfig(
    val bgColor: Color,
    val borderColor: Color,
    val textColor: Color,
    val dotColor: Color,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
