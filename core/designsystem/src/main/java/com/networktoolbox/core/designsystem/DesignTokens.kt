package com.networktoolbox.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The first stable visual vocabulary for NetworkToolbox.
 *
 * The tokens intentionally stay small: screens can use MaterialTheme for
 * component defaults and only reach for these values when a product-level
 * visual decision is needed.
 */
object NetworkToolboxColors {
    // Dark-first brand presentation.
    val DarkBackground = Color(0xFF0E1420)
    val DarkSurface = Color(0xFF151D2A)
    val DarkSurfaceVariant = Color(0xFF1C2635)
    val DarkPrimary = Color(0xFF4C8DFF)
    val DarkSecondary = Color(0xFF76C8D2)
    val DarkPrimaryText = Color(0xFFF3F7FC)
    val DarkSecondaryText = Color(0xFFAAB6C5)
    val DarkOutline = Color(0xFF2B394C)

    // Light keeps the same semantic roles while preserving readable contrast.
    val LightBackground = Color(0xFFF7F9FC)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFE9EEF5)
    val LightPrimary = Color(0xFF2F6FED)
    val LightSecondary = Color(0xFF2A7380)
    val LightPrimaryText = Color(0xFF172033)
    val LightSecondaryText = Color(0xFF4F5E70)
    val LightOutline = Color(0xFF738197)

    // Status colors use dark-theme values from the brand baseline. The light
    // values are darker tonal counterparts so status text remains readable.
    val DarkSuccess = Color(0xFF35C98B)
    val LightSuccess = Color(0xFF147A54)
    val DarkNotice = Color(0xFFE7B94C)
    val LightNotice = Color(0xFF7A5A00)
    val DarkError = Color(0xFFFF5C68)
    val LightError = Color(0xFFB3261E)
    val DarkUnknown = Color(0xFFAAB6C5)
    val LightUnknown = Color(0xFF506070)
}

object NetworkToolboxSpacing {
    val XS = 4.dp
    val SM = 8.dp
    val MD = 12.dp
    val LG = 16.dp
    val XL = 24.dp
    val XXL = 32.dp
}

object NetworkToolboxComponentShapes {
    val Card = RoundedCornerShape(18.dp)
    val Button = RoundedCornerShape(16.dp)
    val Chip = RoundedCornerShape(percent = 50)
}

object NetworkToolboxTextStyles {
    /** Use only for IP, IPv6, MAC, hostname, and other technical values. */
    val TechnicalData = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
}
