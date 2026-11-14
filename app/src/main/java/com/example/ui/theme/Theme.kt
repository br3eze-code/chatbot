package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AtelierLightColorScheme = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = OnTerracotta,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = OnTerracottaContainer,
    secondary = SageSecondary,
    onSecondary = OnSage,
    secondaryContainer = SageContainer,
    onSecondaryContainer = OnSageContainer,
    tertiary = OchreTertiary,
    onTertiary = OnOchre,
    tertiaryContainer = OchreContainer,
    onTertiaryContainer = OnOchreContainer,
    background = ParchmentBackground,
    onBackground = InkCharcoal,
    surface = WarmAlabasterSurface,
    onSurface = InkCharcoal,
    surfaceVariant = WarmSandSurfaceVariant,
    onSurfaceVariant = MutedSlate,
    surfaceContainerLowest = ElevatedPaper,
    surfaceContainerLow = WarmAlabasterSurface,
    surfaceContainer = WarmSandSurfaceVariant,
    surfaceContainerHigh = Color(0xFFE5DDD0),
    outline = HairlineParchment,
    outlineVariant = HairlineParchment,
    error = ErrorLight,
    errorContainer = ErrorContainerLight
)

private val AtelierDarkColorScheme = darkColorScheme(
    primary = TerracottaDarkPrimary,
    onPrimary = OnTerracottaDark,
    primaryContainer = TerracottaDarkContainer,
    onPrimaryContainer = OnTerracottaDarkContainer,
    secondary = SageDarkSecondary,
    onSecondary = OnSageDark,
    secondaryContainer = SageDarkContainer,
    onSecondaryContainer = OnSageDarkContainer,
    tertiary = OchreDarkTertiary,
    onTertiary = OnOchreDark,
    tertiaryContainer = OchreDarkContainer,
    onTertiaryContainer = OnOchreDarkContainer,
    background = ObsidianBackground,
    onBackground = AlabasterInk,
    surface = EspressoSurface,
    onSurface = AlabasterInk,
    surfaceVariant = WarmAshSurfaceVariant,
    onSurfaceVariant = WarmAshMuted,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = EspressoSurface,
    surfaceContainer = ElevatedGraphite,
    surfaceContainerHigh = WarmAshSurfaceVariant,
    outline = HairlineObsidian,
    outlineVariant = HairlineObsidian,
    error = ErrorDark,
    errorContainer = ErrorContainerDark
)

val AtelierShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AtelierTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (isDark) AtelierDarkColorScheme else AtelierLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AtelierTypography,
        shapes = AtelierShapes,
        content = content
    )
}
