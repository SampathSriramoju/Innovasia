package com.bolkisan.farmer.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = ForestGreenPrimary,
    onPrimary = CardWhite,
    secondary = LeafGreenSecondary,
    onSecondary = DarkEarthFg,
    tertiary = HarvestGoldAccent,
    background = MintCreamBg,
    onBackground = DarkEarthFg,
    surface = CardWhite,
    onSurface = DarkEarthFg,
    error = HazardRed,
    onError = CardWhite
)

@Composable
fun BolKisanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
