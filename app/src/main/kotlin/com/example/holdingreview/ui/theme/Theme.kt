package com.example.holdingreview.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF147C72),
    onPrimary = Color.White,
    secondary = Color(0xFFE8A14A),
    onSecondary = Color(0xFF17211F),
    tertiary = Color(0xFF4B65A2),
    background = Color(0xFFF6F7F2),
    onBackground = Color(0xFF17211F),
    surface = Color.White,
    onSurface = Color(0xFF17211F),
    surfaceVariant = Color(0xFFE8EEE9),
    onSurfaceVariant = Color(0xFF4D5C58),
    error = Color(0xFFC93A3A)
)

@Composable
fun HoldingReviewTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
