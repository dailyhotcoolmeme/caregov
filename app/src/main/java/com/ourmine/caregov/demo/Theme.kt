package com.ourmine.caregov.demo

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CaregovColors = lightColorScheme(
    primary = Color(0xFF007B60),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2EB),
    onPrimaryContainer = Color(0xFF00543F),
    secondary = Color(0xFF996215),
    secondaryContainer = Color(0xFFFFEAC8),
    onSecondaryContainer = Color(0xFF67400B),
    background = Color(0xFFF8FAF9),
    onBackground = Color(0xFF202B33),
    surface = Color.White,
    onSurface = Color(0xFF202B33),
    onSurfaceVariant = Color(0xFF58666B),
    outlineVariant = Color(0xFFDCE4E0),
)

private fun textStyle(size: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size + 8).sp,
    letterSpacing = 0.sp,
)

private val CaregovTypography = Typography(
    headlineSmall = textStyle(26, FontWeight.Bold),
    titleLarge = textStyle(22, FontWeight.Bold),
    titleMedium = textStyle(18, FontWeight.SemiBold),
    bodyLarge = textStyle(18),
    bodyMedium = textStyle(16),
    bodySmall = textStyle(14),
    labelLarge = textStyle(16, FontWeight.SemiBold),
    labelMedium = textStyle(14, FontWeight.Medium),
    labelSmall = textStyle(12, FontWeight.Medium),
)

@Composable
fun CaregovTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CaregovColors,
        typography = CaregovTypography,
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(8.dp),
            large = RoundedCornerShape(8.dp),
        ),
        content = content,
    )
}
