package com.example.privatevault.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0A111F); val Card = Color(0xFF131C33); val Card2 = Color(0xFF1A2644)
val Line = Color(0xFF243153); val Accent = Color(0xFF2F7BFF); val Muted = Color(0xFF8D9BBD)

@Composable
fun VaultTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Card,
        onBackground = Color(0xFFEAF0FF), onSurface = Color(0xFFEAF0FF)),
    content = content)