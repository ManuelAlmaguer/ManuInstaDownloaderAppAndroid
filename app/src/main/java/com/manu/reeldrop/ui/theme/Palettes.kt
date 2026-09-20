package com.manu.reeldrop.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.manu.reeldrop.domain.AppTheme

/**
 * A brand palette drives every surface: background, cards, gradient and accents.
 * Adding a new theme is a one-line addition to [Palettes].
 */
data class ReelPalette(
    val theme: AppTheme,
    val label: String,
    val gradient: List<Color>,
    val darkBackground: Color,
    val darkSurface: Color,
    val darkCard: Color,
    val darkOnSurface: Color,
    val darkMuted: Color,
    val lightBackground: Color,
    val lightSurface: Color,
    val lightCard: Color,
    val lightOnSurface: Color,
    val lightMuted: Color,
    val accent: Color,
    val success: Color = Color(0xFF34D399),
    val warning: Color = Color(0xFFFBBF24),
    val danger: Color = Color(0xFFF87171),
) {
    fun gradientBrush(): Brush = Brush.linearGradient(gradient)
    fun softGradient(): Brush = Brush.verticalGradient(gradient.map { it.copy(alpha = 0.20f) })
}

object Palettes {

    val neon = ReelPalette(
        theme = AppTheme.NEON,
        label = "Neón Púrpura",
        gradient = listOf(Color(0xFFA855F7), Color(0xFFEC4899), Color(0xFFF97316)),
        darkBackground = Color(0xFF0B0B10),
        darkSurface = Color(0xFF111119),
        darkCard = Color(0xFF16161F),
        darkOnSurface = Color(0xFFECECF3),
        darkMuted = Color(0xFF8A8AA3),
        lightBackground = Color(0xFFF7F5FB),
        lightSurface = Color(0xFFFFFFFF),
        lightCard = Color(0xFFF1EDF9),
        lightOnSurface = Color(0xFF14121C),
        lightMuted = Color(0xFF6B6880),
        accent = Color(0xFFA855F7),
    )

    val amoled = ReelPalette(
        theme = AppTheme.AMOLED,
        label = "Medianoche AMOLED",
        gradient = listOf(Color(0xFF22D3EE), Color(0xFF6366F1), Color(0xFFA855F7)),
        darkBackground = Color(0xFF000000),
        darkSurface = Color(0xFF050508),
        darkCard = Color(0xFF0C0C12),
        darkOnSurface = Color(0xFFE8E8F0),
        darkMuted = Color(0xFF7A7A8C),
        lightBackground = Color(0xFFF4F6F9),
        lightSurface = Color(0xFFFFFFFF),
        lightCard = Color(0xFFE8EDF5),
        lightOnSurface = Color(0xFF0B0F16),
        lightMuted = Color(0xFF5C6579),
        accent = Color(0xFF22D3EE),
    )

    val ocean = ReelPalette(
        theme = AppTheme.OCEAN,
        label = "Océano",
        gradient = listOf(Color(0xFF0EA5E9), Color(0xFF14B8A6), Color(0xFF22D3EE)),
        darkBackground = Color(0xFF05121C),
        darkSurface = Color(0xFF08202E),
        darkCard = Color(0xFF0B2A3B),
        darkOnSurface = Color(0xFFE2F4FB),
        darkMuted = Color(0xFF7BA6BC),
        lightBackground = Color(0xFFF0F8FC),
        lightSurface = Color(0xFFFFFFFF),
        lightCard = Color(0xFFDCEEF8),
        lightOnSurface = Color(0xFF06202C),
        lightMuted = Color(0xFF4B7186),
        accent = Color(0xFF0EA5E9),
    )

    val sunset = ReelPalette(
        theme = AppTheme.SUNSET,
        label = "Atardecer",
        gradient = listOf(Color(0xFFF97316), Color(0xFFEF4444), Color(0xFFEC4899)),
        darkBackground = Color(0xFF160A08),
        darkSurface = Color(0xFF23100C),
        darkCard = Color(0xFF2C1512),
        darkOnSurface = Color(0xFFFCEAE2),
        darkMuted = Color(0xFFBF8E7E),
        lightBackground = Color(0xFFFFF5F0),
        lightSurface = Color(0xFFFFFFFF),
        lightCard = Color(0xFFFFE4D6),
        lightOnSurface = Color(0xFF2A0F09),
        lightMuted = Color(0xFF8A5A47),
        accent = Color(0xFFF97316),
    )

    val forest = ReelPalette(
        theme = AppTheme.FOREST,
        label = "Bosque",
        gradient = listOf(Color(0xFF22C55E), Color(0xFF14B8A6), Color(0xFF84CC16)),
        darkBackground = Color(0xFF07130D),
        darkSurface = Color(0xFF0C1F15),
        darkCard = Color(0xFF10291C),
        darkOnSurface = Color(0xFFE3F5E9),
        darkMuted = Color(0xFF7FAA92),
        lightBackground = Color(0xFFF2FAF4),
        lightSurface = Color(0xFFFFFFFF),
        lightCard = Color(0xFFDDF2E3),
        lightOnSurface = Color(0xFF0A1F12),
        lightMuted = Color(0xFF4C7359),
        accent = Color(0xFF22C55E),
    )

    val candy = ReelPalette(
        theme = AppTheme.CANDY,
        label = "Chicle",
        gradient = listOf(Color(0xFFF472B6), Color(0xFFC084FC), Color(0xFF818CF8)),
        darkBackground = Color(0xFF150C1A),
        darkSurface = Color(0xFF21122A),
        darkCard = Color(0xFF2A1834),
        darkOnSurface = Color(0xFFF6E9FB),
        darkMuted = Color(0xFFB598C4),
        lightBackground = Color(0xFFFDF4FF),
        lightSurface = Color(0xFFFFFFFF),
        lightCard = Color(0xFFF7E4FA),
        lightOnSurface = Color(0xFF230F2B),
        lightMuted = Color(0xFF7A5C88),
        accent = Color(0xFFF472B6),
    )

    val rose = ReelPalette(
        theme = AppTheme.ROSE,
        label = "Cereza",
        gradient = listOf(Color(0xFFF43F5E), Color(0xFFEC4899), Color(0xFFF97316)),
        darkBackground = Color(0xFF1A080E),
        darkSurface = Color(0xFF28101B),
        darkCard = Color(0xFF351522),
        darkOnSurface = Color(0xFFFDE7EF),
        darkMuted = Color(0xFFC98CA0),
        lightBackground = Color(0xFFFFF4F7),
        lightSurface = Color(0xFFFFFFFF),
        lightCard = Color(0xFFFFE3EC),
        lightOnSurface = Color(0xFF2B0D18),
        lightMuted = Color(0xFF8B5365),
        accent = Color(0xFFF43F5E),
    )

    val arctic = ReelPalette(
        theme = AppTheme.ARCTIC,
        label = "Ártico",
        gradient = listOf(Color(0xFF38BDF8), Color(0xFF60A5FA), Color(0xFFA5B4FC)),
        darkBackground = Color(0xFF06121F),
        darkSurface = Color(0xFF0B1C2E),
        darkCard = Color(0xFF102A43),
        darkOnSurface = Color(0xFFE5F4FF),
        darkMuted = Color(0xFF85A9C7),
        lightBackground = Color(0xFFF1F8FF),
        lightSurface = Color(0xFFFFFFFF),
        lightCard = Color(0xFFDCEEFF),
        lightOnSurface = Color(0xFF08203A),
        lightMuted = Color(0xFF4B6C86),
        accent = Color(0xFF0EA5E9),
    )

    val aurora = ReelPalette(
        theme = AppTheme.AURORA,
        label = "Aurora",
        gradient = listOf(Color(0xFF34D399), Color(0xFF22D3EE), Color(0xFF8B5CF6)),
        darkBackground = Color(0xFF061410),
        darkSurface = Color(0xFF0C211B),
        darkCard = Color(0xFF123027),
        darkOnSurface = Color(0xFFE4FFF5),
        darkMuted = Color(0xFF86B9A8),
        lightBackground = Color(0xFFF0FCF8),
        lightSurface = Color(0xFFFFFFFF),
        lightCard = Color(0xFFDDF7EE),
        lightOnSurface = Color(0xFF09251C),
        lightMuted = Color(0xFF4E7669),
        accent = Color(0xFF10B981),
    )

    val graphite = ReelPalette(
        theme = AppTheme.GRAPHITE,
        label = "Grafito",
        gradient = listOf(Color(0xFFCBD5E1), Color(0xFF94A3B8), Color(0xFF475569)),
        darkBackground = Color(0xFF0B0E12),
        darkSurface = Color(0xFF131820),
        darkCard = Color(0xFF1B2430),
        darkOnSurface = Color(0xFFEEF2F7),
        darkMuted = Color(0xFF9AA8B8),
        lightBackground = Color(0xFFF5F7FA),
        lightSurface = Color(0xFFFFFFFF),
        lightCard = Color(0xFFE7ECF2),
        lightOnSurface = Color(0xFF141A22),
        lightMuted = Color(0xFF5F6B78),
        accent = Color(0xFF64748B),
    )

    val system = ReelPalette(
        theme = AppTheme.SYSTEM,
        label = "Sistema",
        gradient = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFF0EA5E9)),
        darkBackground = Color(0xFF101114),
        darkSurface = Color(0xFF17181C),
        darkCard = Color(0xFF1E1F24),
        darkOnSurface = Color(0xFFE7E7EA),
        darkMuted = Color(0xFF8E8E96),
        lightBackground = Color(0xFFF6F6F8),
        lightSurface = Color(0xFFFFFFFF),
        lightCard = Color(0xFFEAEAEF),
        lightOnSurface = Color(0xFF16171A),
        lightMuted = Color(0xFF5F6066),
        accent = Color(0xFF6366F1),
    )

    /** Dynamic (Material You) uses the system colours and only borrows the gradient stops. */
    val dynamic = neon.copy(
        theme = AppTheme.DYNAMIC,
        label = "Color dinámico",
        gradient = listOf(Color(0xFF6750A4), Color(0xFF7D5260), Color(0xFF625B71)),
    )

    val all: List<ReelPalette> = listOf(
        neon, amoled, ocean, sunset, forest, candy, rose, arctic, aurora, graphite, dynamic, system,
    )

    fun of(theme: AppTheme): ReelPalette = all.firstOrNull { it.theme == theme } ?: neon
}
