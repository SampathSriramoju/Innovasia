package com.bolkisan.farmer.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolkisan.farmer.theme.ForestGreenPrimary

@Composable
fun NetworkStatusBar(
    isOnline: Boolean,
    language: String = "te",
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isOnline) Color(0xFFE8F5E9) else Color(0xFFFFF7ED),
        label = "network_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isOnline) Color(0xFFA5D6A7) else Color(0xFFFCD34D),
        label = "network_border"
    )
    val textColor = if (isOnline) ForestGreenPrimary else Color(0xFFB45309)
    val dotColor = if (isOnline) Color(0xFF2E7D32) else Color(0xFFD97706)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        color = bgColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                        .background(dotColor)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    val strings = appStrings(language)
                    Text(
                        text = if (isOnline) strings.onlineTitle else strings.offlineTitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )

                    Text(
                        text = if (isOnline) strings.onlineSubtitle else strings.offlineSubtitle,
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.85f)
                    )
                }
            }

            Icon(
                imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                contentDescription = if (isOnline) "Online" else "Offline",
                tint = dotColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
