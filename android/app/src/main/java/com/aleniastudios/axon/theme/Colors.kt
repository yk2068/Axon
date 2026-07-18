package com.aleniastudios.axon.theme

import androidx.compose.ui.graphics.Color

/**
 * Centralized color palette for the Axon app.
 * All UI screens should reference these values instead of defining their own.
 */
object AxonColors {
    // Background gradient
    val BgGradStart  = Color(0xFF0F172A)
    val BgGradEnd    = Color(0xFF020617)

    // Accent colors
    val BlueAccent   = Color(0xFF3B82F6)
    val VioletAccent = Color(0xFF8B5CF6)
    val GreenAccent  = Color(0xFF10B981)
    val RedAccent    = Color(0xFFEF4444)
    val AccentGlow   = Color(0xFF1E3A8A)

    // Card / surface
    val CardColor    = Color(0xFF0F172A)
    val CardInner    = Color(0xFF1E293B)

    // Text
    val TextPrimary  = Color(0xFFF9FAFB)
    val TextSecond   = Color(0xFF94A3B8)

    // Borders
    val BorderColor  = Color(0xFF334155)
}
