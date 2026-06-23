package com.biliclient.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// B站粉 + 辅助蓝（贴近官方手机版）
val BiliPink = Color(0xFFFB7299)
val BiliBlue = Color(0xFF00AEEC)

private val LightColors = lightColorScheme(
    primary = BiliPink,
    secondary = BiliBlue,
)

private val DarkColors = darkColorScheme(
    primary = BiliPink,
    secondary = BiliBlue,
)

@Composable
fun BiliClientTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
