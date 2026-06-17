package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = GymLime,
    secondary = ForestGreen,
    tertiary = VoltGreen,
    background = RawCarbonBlack,
    surface = RawObsidianGray,
    onPrimary = RawCarbonBlack,
    onSecondary = RawBorderGraphite, // used for background border color
    onTertiary = RawCarbonBlack,
    onBackground = RawTextWhite,
    onSurface = RawTextWhite,
    surfaceVariant = RawDeepSlate,
    onSurfaceVariant = RawTextMuted
  )

private val LightColorScheme =
  lightColorScheme(
    primary = ForestGreen,
    secondary = RawDeepSlate,
    tertiary = GymLime,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = LightBorder, // used for background border color
    onTertiary = Color.White,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    surfaceVariant = LightCardBg,
    onSurfaceVariant = LightTextMuted
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
