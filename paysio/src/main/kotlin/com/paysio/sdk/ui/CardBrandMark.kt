package com.paysio.sdk.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Card brand marks, ported from the React Native SDK's `src/ui/CardBrand.tsx`
 * (artwork in [BrandArtwork], byte for byte the same paths). Width = [size],
 * height = size * 0.65.
 *
 * Wallets (`walletType` "apple_pay" / "google_pay") replace the brand. "bank" draws
 * the bank badge. With no brand, a [bin] resolves one through the web's BIN table;
 * with neither, a neutral generic card in [fallbackColor].
 *
 * The RN SDK can draw a remote bank logo (`bankLogoUrl`); the Android SDK has no image
 * loader, so banks always get the bank badge (the RN fallback for a failed logo).
 */
@Composable
fun CardBrandMark(
    brand: String?,
    size: Dp = 30.dp,
    isBank: Boolean = false,
    modifier: Modifier = Modifier,
    bin: String? = null,
    walletType: String? = null,
    fallbackColor: Color = Color(0xFF6E6E6E),
) {
    val key = resolveBrandArtKey(if (isBank) "bank" else brand, bin, walletType)
    val vector = when (key) {
        null -> BrandArtwork.vector("generic-${fallbackColor.value}") { BrandArtwork.genericCard(fallbackColor) }
        else -> BrandArtwork.vector(key) { brandLayers(key) }
    }
    Image(
        painter = rememberVectorPainter(vector),
        contentDescription = null,
        modifier = modifier
            .size(width = size, height = size * BrandArtwork.RATIO)
            .semantics { contentDescription = brandLabel(key) },
    )
}

private fun brandLayers(key: String): List<ArtLayer> = when (key) {
    "apple_pay" -> BrandArtwork.applePay
    "google_pay" -> BrandArtwork.googlePay
    "bank" -> BrandArtwork.bank
    "visa" -> BrandArtwork.visa
    "mastercard" -> BrandArtwork.mastercard()
    "amex" -> BrandArtwork.amex
    "discover" -> BrandArtwork.discover
    "dinersclub" -> BrandArtwork.dinersClub
    "jcb" -> BrandArtwork.jcb
    "unionpay" -> BrandArtwork.unionPay
    "maestro" -> BrandArtwork.mastercard(left = "#eb001b", right = "#00a2e5", centre = "#7375cf", card = "#fff")
    else -> BrandArtwork.genericCard(Color(0xFF6E6E6E))
}

private fun brandLabel(key: String?): String = when (key) {
    "apple_pay" -> "Apple Pay"
    "google_pay" -> "Google Pay"
    "bank" -> "Bank account"
    "visa" -> "Visa"
    "mastercard" -> "Mastercard"
    "amex" -> "American Express"
    "discover" -> "Discover"
    "dinersclub" -> "Diners Club"
    "jcb" -> "JCB"
    "unionpay" -> "UnionPay"
    "maestro" -> "Maestro"
    else -> "Card"
}

/** RN `normalize`: lower case, spaces / dashes / underscores removed. */
internal fun normalizeBrandKey(brand: String?): String =
    brand?.lowercase()?.replace(Regex("[\\s\\-_]"), "") ?: ""

/** Same BIN table the web uses, for rows that stored a number but no brand. */
fun brandFromBin(bin: String): String {
    val n = bin.replace(Regex("\\s"), "")
    return when {
        Regex("^4").containsMatchIn(n) -> "visa"
        Regex("^5[1-5]").containsMatchIn(n) || Regex("^2[2-7]").containsMatchIn(n) -> "mastercard"
        Regex("^3[47]").containsMatchIn(n) -> "amex"
        Regex("^6(?:011|5)").containsMatchIn(n) -> "discover"
        Regex("^3(?:0[0-5]|[68])").containsMatchIn(n) -> "dinersclub"
        Regex("^35").containsMatchIn(n) -> "jcb"
        Regex("^62").containsMatchIn(n) -> "unionpay"
        Regex("^(?:5018|5020|5038|6304|6759|676[1-3])").containsMatchIn(n) -> "maestro"
        else -> ""
    }
}

/** The artwork key for a brand / bin / wallet, or null for the generic card (RN rules). */
internal fun resolveBrandArtKey(brand: String?, bin: String?, walletType: String?): String? {
    if (walletType == "apple_pay") return "apple_pay"
    if (walletType == "google_pay") return "google_pay"
    var key = normalizeBrandKey(brand)
    if (key == "bank") return "bank"
    if ((key.isEmpty() || key == "unknown") && !bin.isNullOrEmpty()) key = brandFromBin(bin)
    return when (key) {
        "visa" -> "visa"
        "mastercard" -> "mastercard"
        "amex", "americanexpress" -> "amex"
        "discover" -> "discover"
        "dinersclub", "diners" -> "dinersclub"
        "jcb" -> "jcb"
        "unionpay" -> "unionpay"
        "maestro" -> "maestro"
        else -> null
    }
}

/** Whether a brand (or bin) resolves to artwork; used to decide on the fallback. */
fun hasBrandArt(brand: String?, bin: String? = null, walletType: String? = null): Boolean =
    resolveBrandArtKey(brand, bin, walletType) != null

/**
 * RN `CardBrand`: the mark, or [fallback] when no brand resolves, with the web's
 * 150 ms scale (0.95 -> 1) / opacity (0.7 -> 1) blip on every change when [animate].
 */
@Composable
fun CardBrand(
    brand: String?,
    size: Dp = 30.dp,
    modifier: Modifier = Modifier,
    bin: String? = null,
    walletType: String? = null,
    fallbackColor: Color = Color(0xFF6E6E6E),
    animate: Boolean = false,
    fallback: (@Composable () -> Unit)? = null,
) {
    val key = "${walletType ?: ""}|${normalizeBrandKey(brand)}|${bin ?: ""}"
    val blip = remember { Animatable(1f) }
    val lastKey = remember { arrayOf(key) }
    LaunchedEffect(animate, key) {
        if (!animate || lastKey[0] == key) return@LaunchedEffect
        lastKey[0] = key
        blip.snapTo(0f)
        blip.animateTo(1f, tween(150))
    }
    Box(
        modifier.graphicsLayer {
            val t = blip.value
            alpha = 0.7f + 0.3f * t
            val s = 0.95f + 0.05f * t
            scaleX = s
            scaleY = s
        },
    ) {
        if (!hasBrandArt(brand, bin, walletType) && fallback != null) {
            fallback()
        } else {
            CardBrandMark(brand, size, bin = bin, walletType = walletType, fallbackColor = fallbackColor)
        }
    }
}

/** The brands accepted by default, in stack order. */
val PaysioAcceptedCardBrands: List<String> = listOf("visa", "mastercard", "amex", "discover")

/**
 * Overlapping stack of the accepted brands (RN `AcceptedBrandStack`): each mark after
 * the first shifted -8 dp, z-order first on top. The placeholder before a BIN
 * identifies the card.
 */
@Composable
fun AcceptedBrandStack(size: Dp = 22.dp, modifier: Modifier = Modifier, brands: List<String> = PaysioAcceptedCardBrands) {
    val overlap = 8.dp
    Row(
        modifier
            // Report the overlapped width, like RN's negative marginLeft does.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val shrink = (overlap.roundToPx() * (brands.size - 1).coerceAtLeast(0))
                val width = (placeable.width - shrink).coerceAtLeast(0)
                layout(width, placeable.height) { placeable.place(0, 0) }
            }
            .semantics { contentDescription = brands.joinToString(", ") { brandLabel(resolveBrandArtKey(it, null, null)) } },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        brands.forEachIndexed { index, brand ->
            CardBrandMark(
                brand = brand,
                size = size,
                modifier = Modifier
                    .zIndex((brands.size - index).toFloat())
                    .offset(x = -overlap * index),
            )
        }
    }
}

/** Hairline circle outline used by the radio when unchecked. */
@Composable
internal fun StrokeCircle(color: Color, size: Dp, strokeWidth: Dp = 1.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        drawCircle(color, radius = this.size.minDimension / 2 - strokeWidth.toPx() / 2, style = Stroke(strokeWidth.toPx()))
    }
}
