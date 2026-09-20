package com.manu.reeldrop.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.manu.reeldrop.domain.AppTheme
import com.manu.reeldrop.domain.ThemeMode

val LocalReelPalette = staticCompositionLocalOf { Palettes.neon }
val LocalIsDarkTheme = staticCompositionLocalOf { true }

/**
 * Applies one of the ReelDrop themes.
 *
 *  * [theme] picks one of twelve palettes (Neón Púrpura, AMOLED, Océano, Atardecer, Bosque,
 *    Chicle, Cereza, Ártico, Aurora, Grafito, Material You dynamic colour, or Sistema).
 *  * [mode] picks dark / light / follow-system.
 *  * [dynamicColor] enables Material You wallpaper colours on Android 12+.
 */
@Composable
fun ReelDropTheme(
    theme: AppTheme = AppTheme.NEON,
    mode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = Palettes.of(theme)
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        dynamicColor && supportsDynamic && dark -> dynamicDarkColorScheme(context)
        dynamicColor && supportsDynamic -> dynamicLightColorScheme(context)
        dark -> darkColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.gradient.getOrElse(1) { palette.accent },
            tertiary = palette.gradient.last(),
            background = palette.darkBackground,
            onBackground = palette.darkOnSurface,
            surface = palette.darkSurface,
            onSurface = palette.darkOnSurface,
            surfaceVariant = palette.darkCard,
            onSurfaceVariant = palette.darkMuted,
            outline = palette.darkMuted.copy(alpha = 0.35f),
            error = palette.danger,
        )
        else -> lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.gradient.getOrElse(1) { palette.accent },
            tertiary = palette.gradient.last(),
            background = palette.lightBackground,
            onBackground = palette.lightOnSurface,
            surface = palette.lightSurface,
            onSurface = palette.lightOnSurface,
            surfaceVariant = palette.lightCard,
            onSurfaceVariant = palette.lightMuted,
            outline = palette.lightMuted.copy(alpha = 0.35f),
            error = palette.danger,
        )
    }

    CompositionLocalProvider(
        LocalReelPalette provides palette,
        LocalIsDarkTheme provides dark,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ReelTypography,
            shapes = ReelShapes,
            content = content,
        )
    }
}
