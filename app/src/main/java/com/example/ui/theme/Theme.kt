package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RuinsColorScheme =
  darkColorScheme(
    primary = BrightGold,
    onPrimary = ShadowSlate,
    secondary = Sandstone,
    onSecondary = AncientTimber,
    tertiary = MossGreen,
    onTertiary = SunlitCream,
    background = DeepSlate,
    onBackground = SunlitCream,
    surface = ShadowSlate,
    onSurface = SunlitCream,
    surfaceVariant = AncientTimber,
    onSurfaceVariant = FogMist,
  )

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = RuinsColorScheme,
    typography = Typography,
    content = content,
  )
}
