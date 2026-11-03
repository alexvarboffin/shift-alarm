package com.walhalla.shiftalarm.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B3A4B),
    onPrimary = Color(0xFFF4F1EA),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color(0xFFFFF8E8),
    tertiary = Color(0xFF0F766E),
    background = Color(0xFFF4F1EA),
    surface = Color(0xFFFFFBF5),
    onBackground = Color(0xFF1C1915),
    onSurface = Color(0xFF1C1915),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE0A100),
    onPrimary = Color(0xFF1C1400),
    secondary = Color(0xFF8ECAE6),
    tertiary = Color(0xFF5EEAD4),
    background = Color(0xFF12161C),
    surface = Color(0xFF1C232C),
    onBackground = Color(0xFFF4F1EA),
    onSurface = Color(0xFFF4F1EA),
)

@Composable
fun ShiftAlarmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dynamic = useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    val colorScheme = when {
        dynamic && darkTheme -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

/** Centers a readable column on phones and tablets. */
@Composable
fun ShiftPane(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            content = content,
        )
    }
}
