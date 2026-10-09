package io.github.garminaicoach.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF161616), onPrimary = Color.White,
    secondary = Color(0xFF505050), onSecondary = Color.White,
    tertiary = Color(0xFF505050), onTertiary = Color.White,
    primaryContainer = Color(0xFFE0E0E0), onPrimaryContainer = Color(0xFF161616),
    secondaryContainer = Color(0xFFE8E8E8), onSecondaryContainer = Color(0xFF161616),
    tertiaryContainer = Color(0xFFE8E8E8), onTertiaryContainer = Color(0xFF161616),
    background = Color(0xFFFAFAFA), onBackground = Color(0xFF161616),
    surface = Color.White, onSurface = Color(0xFF161616),
    surfaceVariant = Color(0xFFEDEDED), onSurfaceVariant = Color(0xFF505050),
    outline = Color(0xFF909090), error = Color(0xFF303030), onError = Color.White,
    outlineVariant = Color(0xFFD0D0D0), surfaceTint = Color(0xFF161616),
    surfaceDim = Color(0xFFE0E0E0), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF6F6F6),
    surfaceContainer = Color(0xFFF0F0F0), surfaceContainerHigh = Color(0xFFE8E8E8), surfaceContainerHighest = Color(0xFFE2E2E2),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFF2F2F2), onPrimary = Color(0xFF161616),
    secondary = Color(0xFFBDBDBD), onSecondary = Color(0xFF161616),
    tertiary = Color(0xFFBDBDBD), onTertiary = Color(0xFF161616),
    primaryContainer = Color(0xFF353535), onPrimaryContainer = Color(0xFFF2F2F2),
    secondaryContainer = Color(0xFF303030), onSecondaryContainer = Color(0xFFF2F2F2),
    tertiaryContainer = Color(0xFF303030), onTertiaryContainer = Color(0xFFF2F2F2),
    background = Color(0xFF101010), onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF181818), onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF292929), onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF777777), error = Color(0xFFE0E0E0), onError = Color(0xFF161616),
    outlineVariant = Color(0xFF484848), surfaceTint = Color(0xFFF2F2F2),
    surfaceDim = Color(0xFF101010), surfaceBright = Color(0xFF353535),
    surfaceContainerLowest = Color(0xFF0B0B0B), surfaceContainerLow = Color(0xFF181818),
    surfaceContainer = Color(0xFF202020), surfaceContainerHigh = Color(0xFF292929), surfaceContainerHighest = Color(0xFF323232),
)
@Composable fun CoachTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
