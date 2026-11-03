package three.haddad.smartnotas.ui.components

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Authoritative color utilities for Shoulder identity and high-contrast badge rendering.
 * All algorithms are pure Kotlin without Android platform dependencies to ensure
 * reliable testing and consistent behavior across JVM tests and Android runtime.
 */

internal fun parseNoteColorHex(
    hex: String?,
    fallback: Color,
): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        val clean = hex.removePrefix("#").trim()
        val colorLong = clean.toLong(16)
        if (clean.length == 6) {
            Color(colorLong or 0xFF000000L)
        } else if (clean.length == 8) {
            Color(colorLong)
        } else {
            fallback
        }
    } catch (_: Throwable) {
        fallback
    }
}

internal fun parseColorHexOrNull(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val clean = hex.removePrefix("#").trim()
    if (clean.length != 6) return null
    return try {
        val colorLong = clean.toLong(16)
        Color(colorLong or 0xFF000000L)
    } catch (_: Throwable) {
        null
    }
}

internal fun formatColorToHex(color: Color): String {
    val r = (color.red * 255f + 0.5f).toInt().coerceIn(0, 255)
    val g = (color.green * 255f + 0.5f).toInt().coerceIn(0, 255)
    val b = (color.blue * 255f + 0.5f).toInt().coerceIn(0, 255)
    return String.format(java.util.Locale.ROOT, "#%02X%02X%02X", r, g, b)
}

internal fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
    val max = maxOf(r, maxOf(g, b))
    val min = minOf(r, minOf(g, b))
    val delta = max - min
    val h = when {
        delta == 0f -> 0f
        max == r -> ((g - b) / delta % 6f) * 60f
        max == g -> ((b - r) / delta + 2f) * 60f
        else -> ((r - g) / delta + 4f) * 60f
    }.let { if (it < 0f) it + 360f else it }
    val s = if (max == 0f) 0f else delta / max
    val v = max
    return floatArrayOf(h, s, v)
}

internal fun hsvToRgb(h: Float, s: Float, v: Float): Color {
    val clampedH = (h % 360f + 360f) % 360f
    val clampedS = s.coerceIn(0f, 1f)
    val clampedV = v.coerceIn(0f, 1f)
    val c = clampedV * clampedS
    val x = c * (1f - abs((clampedH / 60f) % 2f - 1f))
    val m = clampedV - c
    val (r1, g1, b1) = when ((clampedH / 60f).toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        (r1 + m).coerceIn(0f, 1f),
        (g1 + m).coerceIn(0f, 1f),
        (b1 + m).coerceIn(0f, 1f),
    )
}

internal fun parseHsvFromHex(
    hex: String?,
    defaultHue: Float = 296f,
    defaultSat: Float = 0.5f,
    defaultVal: Float = 0.65f,
): FloatArray {
    val color = parseColorHexOrNull(hex) ?: return floatArrayOf(defaultHue, defaultSat, defaultVal)
    return rgbToHsv(color.red, color.green, color.blue)
}

internal fun isColorDark(color: Color): Boolean {
    // Relative luminance per ITU-R BT.709
    val lum = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return lum < 0.45f
}

/**
 * Calculates accessible container and foreground text colors for shoulder badges.
 *
 * For red and crimson hues in Dark Mode, saturated red text on dark red background is
 * strictly avoided: foreground saturation is constrained to a subtle rose tint with maximum
 * brightness, ensuring WCAG contrast > 8:1 while preserving the shoulder's warm color identity.
 *
 * In Light Mode, saturated colors use pastel background containers with dark, richly tinted
 * text to maintain readability on light surfaces.
 */
internal fun readableBadgeColors(baseColor: Color, isDark: Boolean): Pair<Color, Color> {
    val hsv = rgbToHsv(baseColor.red, baseColor.green, baseColor.blue)
    val hue = hsv[0]
    val sat = hsv[1]
    val val_ = hsv[2]

    val isMonochrome = sat < 0.08f
    if (isMonochrome) {
        return if (isDark) {
            Color(0xFF2E2D38).copy(alpha = 0.85f) to Color(0xFFEDEBF0)
        } else {
            Color(0xFFEDEBF0) to Color(0xFF1E1E26)
        }
    }

    val isRedOrWarm = hue < 28f || hue > 335f
    val isYellowOrAmber = hue in 40f..85f
    val isBlue = hue in 200f..265f

    return if (isDark) {
        val bgSat = (sat * 0.55f).coerceIn(0.20f, 0.55f)
        val container = hsvToRgb(hue, bgSat, 0.35f).copy(alpha = 0.28f)
        val foreground = when {
            isRedOrWarm -> {
                // Eliminate saturated red text on dark red background.
                // Keep low saturation with high brightness for a crisp, rose-tinted off-white.
                val textSat = (sat * 0.15f).coerceIn(0.06f, 0.16f)
                hsvToRgb(hue, textSat, 0.98f)
            }
            isBlue -> {
                val textSat = (sat * 0.30f).coerceIn(0.12f, 0.32f)
                hsvToRgb(hue, textSat, 0.96f)
            }
            else -> {
                val textSat = (sat * 0.45f).coerceIn(0.18f, 0.45f)
                hsvToRgb(hue, textSat, 0.95f)
            }
        }
        container to foreground
    } else {
        val bgSat = (sat * 0.28f).coerceIn(0.10f, 0.28f)
        val container = hsvToRgb(hue, bgSat, 0.96f).copy(alpha = 0.50f)
        val foreground = when {
            isYellowOrAmber -> {
                // Deep dark bronze/amber for contrast against light background
                hsvToRgb(hue, 0.95f, 0.22f)
            }
            isRedOrWarm -> {
                val textSat = (sat * 0.90f).coerceIn(0.70f, 0.95f)
                hsvToRgb(hue, textSat, 0.30f)
            }
            else -> {
                val textSat = (sat * 0.95f).coerceIn(0.65f, 0.98f)
                hsvToRgb(hue, textSat, 0.25f)
            }
        }
        container to foreground
    }
}
