package com.paysio.sdk.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colour tokens (MOBILE_SDK_PLAN.md section 8.6). Same names as the RN `PaysioThemeColors`. */
data class PaysioThemeColors(
    val primary: Color,
    val foreground: Color,
    val mutedForeground: Color,
    val card: Color,
    val background: Color,
    val secondary: Color,
    val border: Color,
    val input: Color,
    val rowHover: Color,
    val ring: Color,
    val destructive: Color,
) {
    companion object {
        // Same values as the RN SDK's src/ui/tokens.ts (synced from the web app's index.css).
        // `ring` follows the HOSTED checkout: rgba(0,0,0,0.35) light, rgba(255,255,255,0.4) dark.
        val Light = PaysioThemeColors(
            primary = Color(0xFF0B57D0),
            foreground = Color(0xFF1B1B1B),
            mutedForeground = Color(0xFF5D5D5F),
            card = Color(0xFFFCFCFD),
            background = Color(0xFFF3F3F4),
            secondary = Color(0xFFEAEAED),
            border = Color(0xFFECECEC),
            input = Color(0xFFDBDBDC),
            rowHover = Color.Black.copy(alpha = 0.04f),
            ring = Color.Black.copy(alpha = 0.35f), // hosted checkout focus ring
            destructive = Color(0xFFC70A24),
        )

        val Dark = PaysioThemeColors(
            primary = Color(0xFF0B57D0),
            foreground = Color(0xFFF0F0F0),
            mutedForeground = Color(0xFFA6A6A6),
            card = Color(0xFF101010),
            background = Color(0xFF0A0A0A),
            secondary = Color(0xFF181818),
            border = Color.White.copy(alpha = 0.09f),
            input = Color(0xFF404040),
            rowHover = Color.White.copy(alpha = 0.05f),
            ring = Color.White.copy(alpha = 0.4f), // hosted checkout focus ring
            destructive = Color(0xFFDC2626),
        )
    }
}

/** Partial overrides applied on top of the light or dark set. */
data class PaysioThemeColorOverrides(
    val primary: Color? = null,
    val foreground: Color? = null,
    val mutedForeground: Color? = null,
    val card: Color? = null,
    val background: Color? = null,
    val secondary: Color? = null,
    val border: Color? = null,
    val input: Color? = null,
    val rowHover: Color? = null,
    val ring: Color? = null,
    val destructive: Color? = null,
) {
    internal fun applyTo(base: PaysioThemeColors): PaysioThemeColors = base.copy(
        primary = primary ?: base.primary,
        foreground = foreground ?: base.foreground,
        mutedForeground = mutedForeground ?: base.mutedForeground,
        card = card ?: base.card,
        background = background ?: base.background,
        secondary = secondary ?: base.secondary,
        border = border ?: base.border,
        input = input ?: base.input,
        rowHover = rowHover ?: base.rowHover,
        // The focus ring stays the checkout's translucent grey; it does not follow primary.
        ring = ring ?: base.ring,
        destructive = destructive ?: base.destructive,
    )
}

/** Theme input. `AUTO` follows `isSystemInDarkTheme()`. */
data class PaysioTheme(
    val scheme: Scheme = Scheme.AUTO,
    val colors: PaysioThemeColorOverrides = PaysioThemeColorOverrides(),
    val darkColors: PaysioThemeColorOverrides = PaysioThemeColorOverrides(),
    /** Font family for every text in the SDK. null = platform font. */
    val fontFamily: FontFamily? = null,
    /** Outer radius of the picker (web: 18). Everything else scales from it. */
    val radius: Dp = 18.dp,
) {
    enum class Scheme { LIGHT, DARK, AUTO }

    fun resolve(systemDark: Boolean): PaysioResolvedTheme {
        val dark = when (scheme) {
            Scheme.LIGHT -> false
            Scheme.DARK -> true
            Scheme.AUTO -> systemDark
        }
        val base = if (dark) PaysioThemeColors.Dark else PaysioThemeColors.Light
        val merged = if (dark) darkColors.applyTo(colors.applyTo(base)) else colors.applyTo(base)
        return PaysioResolvedTheme(dark, merged, fontFamily, radius)
    }

    companion object {
        val Auto = PaysioTheme()
        val Light = PaysioTheme(Scheme.LIGHT)
        val Dark = PaysioTheme(Scheme.DARK)
    }
}

/** What composables read. */
data class PaysioResolvedTheme(
    val dark: Boolean,
    val colors: PaysioThemeColors,
    val fontFamily: FontFamily?,
    val radius: Dp,
) {
    fun font(size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = colors.foreground): TextStyle =
        TextStyle(fontSize = size, fontWeight = weight, fontFamily = fontFamily, color = color)

    /** Row radii: 14 on the outer rows, 7 inside (web values at radius 18). */
    val rowRadiusOuter: Dp get() = (radius - 4.dp).coerceAtLeast(4.dp)
    val rowRadiusInner: Dp get() = ((radius - 4.dp) / 2).coerceAtLeast(2.dp)

    companion object {
        val DefaultLight = PaysioResolvedTheme(false, PaysioThemeColors.Light, null, 18.dp)
    }
}

val LocalPaysioTheme = staticCompositionLocalOf { PaysioResolvedTheme.DefaultLight }

/** Resolves a [PaysioTheme] against the system scheme and provides it to [content]. */
@Composable
fun PaysioThemeProvider(theme: PaysioTheme = PaysioTheme.Auto, content: @Composable () -> Unit) {
    val resolved = theme.resolve(isSystemInDarkTheme())
    CompositionLocalProvider(LocalPaysioTheme provides resolved, content = content)
}

/**
 * Stacked boxes are 36 dp high, as in the RN SDK and the hosted checkout. The VGS
 * Compose widgets are Material3 TextFields (56 dp minimum); they are laid out at their
 * own height and centred inside the 36 dp box ([vgsFieldInset]).
 */
internal val FieldHeight: Dp = 36.dp
internal val FieldRadius: Dp = 8.dp
internal val FieldFontSize: TextUnit = 16.sp
