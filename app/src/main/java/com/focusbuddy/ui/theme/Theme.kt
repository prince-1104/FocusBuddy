package com.focusbuddy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbuddy.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF5545A5), onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E0FF), onPrimaryContainer = Color(0xFF30216B),
    secondary = Color(0xFF655779), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE5F5), onSecondaryContainer = Color(0xFF332A45),
    tertiary = Color(0xFF914B37), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCCE), onTertiaryContainer = Color(0xFF542515),
    background = Color(0xFFFAF8F3), onBackground = Color(0xFF262331),
    surface = Color(0xFFFAF8F3), onSurface = Color(0xFF262331),
    surfaceVariant = Color(0xFFEAE5EF), onSurfaceVariant = Color(0xFF615A6B),
    surfaceContainer = Color(0xFFF2EDF5), surfaceContainerLow = Color(0xFFFFFCF8),
    surfaceContainerHigh = Color(0xFFEDE7F2),
    outline = Color(0xFF7A7283), outlineVariant = Color(0xFFD1C9DA),
    error = Color(0xFFA02F37), onError = Color.White,
    errorContainer = Color(0xFFFFDADB), onErrorContainer = Color(0xFF641B23),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCCBCFF), onPrimary = Color(0xFF30216B),
    primaryContainer = Color(0xFF433481), onPrimaryContainer = Color(0xFFE9E0FF),
    secondary = Color(0xFFD0C0E4), onSecondary = Color(0xFF362B47),
    secondaryContainer = Color(0xFF4E405F), onSecondaryContainer = Color(0xFFEDE5F5),
    tertiary = Color(0xFFFFB69C), onTertiary = Color(0xFF572819),
    tertiaryContainer = Color(0xFF743B29), onTertiaryContainer = Color(0xFFFFDCCE),
    background = Color(0xFF19171F), onBackground = Color(0xFFECE6F2),
    surface = Color(0xFF19171F), onSurface = Color(0xFFECE6F2),
    surfaceVariant = Color(0xFF49424F), onSurfaceVariant = Color(0xFFCCC3D5),
    surfaceContainer = Color(0xFF26222D), surfaceContainerLow = Color(0xFF211E26),
    surfaceContainerHigh = Color(0xFF312C39),
    outline = Color(0xFF978DA3), outlineVariant = Color(0xFF504858),
    error = Color(0xFFFFB2B7), onError = Color(0xFF60141E),
    errorContainer = Color(0xFF812830), onErrorContainer = Color(0xFFFFDADB),
)

private val BuddyTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun FocusBuddyTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = BuddyTypography,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp),
        ),
        content = content,
    )
}
