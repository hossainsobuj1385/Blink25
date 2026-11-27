package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.domain.ThemeMode

private val BlinkLightColorScheme = lightColorScheme(
    primary = PinePrimaryLight,
    onPrimary = OnPinePrimaryLight,
    primaryContainer = EucalyptusContainerLight,
    onPrimaryContainer = OnEucalyptusContainerLight,
    secondary = SageSecondaryLight,
    onSecondary = OnSageSecondaryLight,
    secondaryContainer = SageContainerLight,
    onSecondaryContainer = OnSageContainerLight,
    tertiary = AmberTertiaryLight,
    onTertiary = OnAmberTertiaryLight,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = OnAmberContainerLight,
    background = OatBackgroundLight,
    onBackground = OnOatInkLight,
    surface = LinenSurfaceLight,
    onSurface = OnOatInkLight,
    surfaceVariant = MistSurfaceVariantLight,
    onSurfaceVariant = OnMistSurfaceVariantLight,
    outline = OutlineMistLight,
    outlineVariant = OutlineSoftLight,
    error = SoftErrorLight,
    errorContainer = SoftErrorContainerLight
)

private val BlinkDarkColorScheme = darkColorScheme(
    primary = PinePrimaryDark,
    onPrimary = OnPinePrimaryDark,
    primaryContainer = EucalyptusContainerDark,
    onPrimaryContainer = OnEucalyptusContainerDark,
    secondary = SageSecondaryDark,
    onSecondary = OnSageSecondaryDark,
    secondaryContainer = SageContainerDark,
    onSecondaryContainer = OnSageContainerDark,
    tertiary = AmberTertiaryDark,
    onTertiary = OnAmberTertiaryDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    background = MidnightMossBackgroundDark,
    onBackground = OnMidnightInkDark,
    surface = CharcoalPineSurfaceDark,
    onSurface = OnMidnightInkDark,
    surfaceVariant = ElevatedPineSurfaceDark,
    onSurfaceVariant = OnElevatedPineDark,
    outline = OutlineDark,
    outlineVariant = OutlineSoftDark,
    error = SoftErrorDark,
    errorContainer = SoftErrorContainerDark
)

val BlinkShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun Blink25Theme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) BlinkDarkColorScheme else BlinkLightColorScheme,
        typography = BlinkTypography,
        shapes = BlinkShapes,
        content = content
    )
}
