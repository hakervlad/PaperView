package com.example.aiknowledgefeed.frontend.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF245E53), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3EEE6), onPrimaryContainer = Color(0xFF214C42),
    secondary = Color(0xFF725647), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E9E0), onSecondaryContainer = Color(0xFF614535),
    tertiary = Color(0xFF58557C), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECE8F5), onTertiaryContainer = Color(0xFF4F486B),
    background = Color(0xFFF6F5F0), onBackground = Color(0xFF242C29),
    surface = Color(0xFFFFFEFA), onSurface = Color(0xFF242C29),
    surfaceVariant = Color(0xFFECEDE6), onSurfaceVariant = Color(0xFF5F6861),
    surfaceContainer = Color(0xFFEFEEE8), surfaceContainerLow = Color(0xFFF2F1EB),
    surfaceContainerHigh = Color(0xFFE9E9E1),
    outline = Color(0xFF798279), outlineVariant = Color(0xFFDADFD5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA7D2BE), onPrimary = Color(0xFF103A2D),
    primaryContainer = Color(0xFF243C33), onPrimaryContainer = Color(0xFFC2E4D3),
    secondary = Color(0xFFE1BEA7), onSecondary = Color(0xFF402A1C),
    secondaryContainer = Color(0xFF40352D), onSecondaryContainer = Color(0xFFF0D6C3),
    tertiary = Color(0xFFC8C1E7), onTertiary = Color(0xFF302B4A),
    tertiaryContainer = Color(0xFF373348), onTertiaryContainer = Color(0xFFE1DAF8),
    background = Color(0xFF131A17), onBackground = Color(0xFFE5E9DF),
    surface = Color(0xFF1B2420), onSurface = Color(0xFFE5E9DF),
    surfaceVariant = Color(0xFF2C3630), onSurfaceVariant = Color(0xFFB3BEB3),
    surfaceContainer = Color(0xFF202B25), surfaceContainerLow = Color(0xFF18211C),
    surfaceContainerHigh = Color(0xFF2B3630),
    outline = Color(0xFF86968A), outlineVariant = Color(0xFF3D4B41),
)

private val PaperTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal,
        fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal,
        fontSize = 32.sp, lineHeight = 39.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal,
        fontSize = 27.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium,
        fontSize = 24.sp, lineHeight = 31.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
        lineHeight = 16.sp, letterSpacing = 0.7.sp),
)

@Composable
fun AIKnowledgeFeedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PaperTypography,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
