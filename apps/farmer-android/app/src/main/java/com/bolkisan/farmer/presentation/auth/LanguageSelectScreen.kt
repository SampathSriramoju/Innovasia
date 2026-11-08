package com.bolkisan.farmer.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolkisan.farmer.presentation.common.appStrings
import com.bolkisan.farmer.theme.*

@Composable
fun LanguageSelectScreen(
    currentLanguage: String = "te",
    onLanguageSelected: (String) -> Unit
) {
    val strings = appStrings(currentLanguage)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MintCreamBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "BOL KISAN",
                style = Typography.headlineLarge,
                color = ForestGreenPrimary
            )
            Text(
                text = strings.languageScreenSubtitle,
                style = Typography.bodyMedium,
                color = MutedSlate,
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
            )

            Text(
                text = strings.languageChoosePrompt,
                style = Typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = DarkEarthFg,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Telugu Option
            LanguageCard(
                nativeName = "తెలుగు",
                englishName = "Telugu",
                description = strings.languageTeluguDesc,
                onClick = { onLanguageSelected("te") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Hindi Option
            LanguageCard(
                nativeName = "हिन्दी",
                englishName = "Hindi",
                description = strings.languageHindiDesc,
                onClick = { onLanguageSelected("hi") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // English Option
            LanguageCard(
                nativeName = "English",
                englishName = "English",
                description = strings.languageEnglishDesc,
                onClick = { onLanguageSelected("en") }
            )
        }
    }
}

@Composable
fun LanguageCard(
    nativeName: String,
    englishName: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(2.dp, SproutBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = nativeName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForestGreenPrimary
                )
                Text(
                    text = "$englishName • $description",
                    fontSize = 12.sp,
                    color = MutedSlate
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MintCreamBg,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "➔", color = ForestGreenPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
