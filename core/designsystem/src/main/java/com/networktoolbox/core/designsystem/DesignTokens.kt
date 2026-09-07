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
    val DarkSurfaceDim = Color(0xFF0B1019)
    val DarkSurfaceContainerLowest = Color(0xFF0A1019)
    val DarkSurfaceContainerLow = Color(0xFF111A27)
    val DarkBackground = Color(0xFF0E1420)
    val DarkSurface = Color(0xFF151D2A)
    val DarkSurfaceVariant = Color(0xFF1C2635)
    val DarkSurfaceContainerHigh = DarkSurfaceVariant
    val DarkSurfaceContainerHighest = Color(0xFF243246)
    val DarkSurfaceBright = Color(0xFF212D3D)
    val DarkPrimary = Color(0xFF4C8DFF)
    val DarkOnPrimary = Color(0xFF06204A)
    val DarkPrimaryContainer = Color(0xFF1B3560)
    val DarkOnPrimaryContainer = Color(0xFFD9E7FF)
    val DarkSecondary = Color(0xFF76C8D2)
    val DarkOnSecondary = Color(0xFF07353B)
    val DarkSecondaryContainer = Color(0xFF204B52)
    val DarkOnSecondaryContainer = Color(0xFFB7EEF3)
    val DarkPrimaryText = Color(0xFFF3F7FC)
    val DarkSecondaryText = Color(0xFFAAB6C5)
    val DarkOutline = Color(0xFF2B394C)
    val DarkOutlineVariant = Color(0xFF3A4B63)
    val DarkInverseSurface = Color(0xFFE7EEF8)
    val DarkInverseOnSurface = Color(0xFF182232)
    val DarkInversePrimary = Color(0xFF2D6EDB)

    // Light keeps the same semantic roles while preserving readable contrast.
    val LightSurfaceDim = Color(0xFFD6DCE5)
    val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
    val LightSurfaceContainerLow = Color(0xFFFBFCFE)
    val LightBackground = Color(0xFFF7F9FC)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFE9EEF5)
    val LightSurfaceContainerHigh = LightSurfaceVariant
    val LightSurfaceContainerHighest = Color(0xFFDFE6EF)
    val LightSurfaceBright = Color(0xFFFFFFFF)
    val LightPrimary = Color(0xFF2F6FED)
    val LightOnPrimary = Color.White
    val LightPrimaryContainer = Color(0xFFDCE8FF)
    val LightOnPrimaryContainer = Color(0xFF0D2A63)
    val LightSecondary = Color(0xFF2A7380)
    val LightOnSecondary = Color.White
    val LightSecondaryContainer = Color(0xFFD0EEF2)
    val LightOnSecondaryContainer = Color(0xFF0B343B)
    val LightPrimaryText = Color(0xFF172033)
    val LightSecondaryText = Color(0xFF4F5E70)
    val LightOutline = Color(0xFF738197)
    val LightOutlineVariant = Color(0xFFC4CBD7)
    val LightInverseSurface = Color(0xFF2D3542)
    val LightInverseOnSurface = Color(0xFFEFF3F8)
    val LightInversePrimary = Color(0xFFA8C7FF)

    // Status colors use dark-theme values from the brand baseline. The light
    // values are darker tonal counterparts so status text remains readable.
    val DarkSuccess = Color(0xFF35C98B)
    val DarkSuccessContainer = Color(0xFF143D31)
    val DarkOnSuccessContainer = Color(0xFFD4F9E7)
    val LightSuccess = Color(0xFF147A54)
    val LightSuccessContainer = Color(0xFFD7F5E8)
    val LightOnSuccessContainer = Color(0xFF0B3A29)
    val DarkNotice = Color(0xFFE7B94C)
    val DarkNoticeContainer = Color(0xFF403617)
    val DarkOnNoticeContainer = Color(0xFFFFE8A6)
    val LightNotice = Color(0xFF7A5A00)
    val LightNoticeContainer = Color(0xFFFFF1C2)
    val LightOnNoticeContainer = Color(0xFF4D3800)
    val DarkError = Color(0xFFFF5C68)
    val DarkOnError = Color(0xFF3B0710)
    val DarkErrorContainer = Color(0xFF491D28)
    val DarkOnErrorContainer = Color(0xFFFFDADF)
    val LightError = Color(0xFFB3261E)
    val LightOnError = Color.White
    val LightErrorContainer = Color(0xFFFFDAD6)
    val LightOnErrorContainer = Color(0xFF601410)
    val DarkUnknown = Color(0xFFAAB6C5)
    val LightUnknown = Color(0xFF506070)
    val DarkUnknownContainer = Color(0xFF283444)
    val DarkOnUnknownContainer = Color(0xFFE2E8F0)
    val LightUnknownContainer = Color(0xFFE7ECF3)
    val LightOnUnknownContainer = Color(0xFF273445)
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
