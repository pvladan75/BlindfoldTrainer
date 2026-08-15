package com.program.blindfoldtrainer.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = AccentLight,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    background = SurfaceDay,
    surface = SurfaceDay,
    surfaceContainer = SurfaceDayRaised,
    error = SquareError
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    background = SurfaceNight,
    surface = SurfaceNight,
    surfaceContainer = SurfaceNightRaised,
    error = SquareError
)

@Composable
fun BlindfoldTrainerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = BlindfoldTypography,
        content = content
    )
}
