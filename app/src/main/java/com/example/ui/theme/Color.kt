package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

// Raw Dark Gym Theme Colors
val RawCarbonBlack = Color(0xFF0C0C0D)
val RawObsidianGray = Color(0xFF151518)
val RawDeepSlate = Color(0xFF1F1F24)
val RawTextWhite = Color(0xFFFAFAFA)
val RawTextMuted = Color(0xFF8E8E93)
val RawCardGraphite = Color(0xFF1E1E23)
val RawBorderGraphite = Color(0xFF2A2A32)

// Raw Light Gym Theme Colors
val LightBackground = Color(0xFFF2F4F7) // Soft slate/grey background
val LightSurface = Color(0xFFFFFFFF)    // Clean white fields/cards
val LightCardBg = Color(0xFFE5E7EB)     // Card background in light mode
val LightBorder = Color(0xFFD1D5DB)     // Border in light mode
val LightTextPrimary = Color(0xFF111827)// Clean dark text
val LightTextMuted = Color(0xFF6B7280)  // Muted grey text

val GymLime = Color(0xFF00FF66)       // Neon energetic green
val VoltGreen = Color(0xFFCCFF00)      // Volt yellow-green accent
val ForestGreen = Color(0xFF059669)     // Soft functional forest green

// M3 Color Scheming
val Purple80 = GymLime
val PurpleGrey80 = ForestGreen
val Pink80 = VoltGreen

val Purple40 = ForestGreen
val PurpleGrey40 = RawDeepSlate
val Pink40 = GymLime

// Dynamically mapped color getters matching the current theme color scheme
val CarbonBlack: Color
    @Composable
    get() = MaterialTheme.colorScheme.background

val ObsidianGray: Color
    @Composable
    get() = MaterialTheme.colorScheme.surface

val DeepSlate: Color
    @Composable
    get() = MaterialTheme.colorScheme.surfaceVariant

val CardGraphite: Color
    @Composable
    get() = MaterialTheme.colorScheme.surfaceVariant

val BorderGraphite: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSecondary

val TextWhite: Color
    @Composable
    get() = MaterialTheme.colorScheme.onBackground

val TextMuted: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

