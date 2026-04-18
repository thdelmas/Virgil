package com.virgil.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VirgilColorScheme = darkColorScheme(
    primary = Color(0xFF81C784),       // calm green
    onPrimary = Color(0xFF1B5E20),
    primaryContainer = Color(0xFF2E7D32),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFF90CAF9),      // soft blue
    onSecondary = Color(0xFF0D47A1),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    error = Color(0xFFEF5350),
)

@Composable
fun VirgilTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VirgilColorScheme,
        typography = VirgilTypography,
        content = content,
    )
}
