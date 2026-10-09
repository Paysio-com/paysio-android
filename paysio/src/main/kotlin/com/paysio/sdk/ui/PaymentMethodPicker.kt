package com.paysio.sdk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paysio.sdk.core.PaysioMethodKind
import com.paysio.sdk.core.PaysioSavedMethod

/** The web picker's easing: cubic-bezier(0.32, 0.72, 0, 1), 240 ms. */
internal val PanelEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
internal const val PanelDurationMs = 240

/**
 * 1:1 port of the web "Payment method" picker (MOBILE_SDK_PLAN.md section 8.1).
 *
 * - Container radius 18 (theme.radius), 1 dp border, hairline shadow, clipped.
 * - Rows: margin 4, padding 12, gap 12: radio + icon + label + trailing.
 * - Radio 18 dp; checked = primary fill + 4 dp ring at 15 % + 6 dp white dot (200 ms).
 * - Selected row background: foreground at 7 %. Press scale 0.985 / 150 ms.
 * - Panel: padding 16 / 4 top / 16 bottom; expands over 240 ms with the web curve.
 *   `AnimatedVisibility` does not animate the initially visible panel, so there is no
 *   animation on first paint.
 * - With a single option there is no picker chrome, only the panel.
 */
@Composable
fun PaymentMethodPicker(
    options: List<PaysioMethodKind>,
    selected: PaysioMethodKind,
    onSelect: (PaysioMethodKind) -> Unit,
    savedMethod: PaysioSavedMethod? = null,
    modifier: Modifier = Modifier,
    panel: @Composable (PaysioMethodKind) -> Unit,
) {
    val theme = LocalPaysioTheme.current
    if (options.size <= 1) {
        options.firstOrNull()?.let { panel(it) }
        return
    }
    val shape = RoundedCornerShape(theme.radius)
    val hasSaved = options.contains(PaysioMethodKind.SAVED)

    Column(
        modifier
            .shadow(1.dp, shape, clip = false, ambientColor = Color(0x0A1A1A1A), spotColor = Color(0x0A1A1A1A))
            .clip(shape)
            .border(1.dp, theme.colors.border, shape)
            .animateContentSize(tween(PanelDurationMs, easing = PanelEasing)),
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(theme.colors.border))
            }
            val isSelected = selected == option
            PickerRow(
                option = option,
                label = labelFor(option, hasSaved),
                isSelected = isSelected,
                isFirst = index == 0,
                isLast = index == options.lastIndex,
                savedMethod = savedMethod,
                onClick = { if (!isSelected) onSelect(option) },
            )
            AnimatedVisibility(
                visible = isSelected,
                enter = expandVertically(tween(PanelDurationMs, easing = PanelEasing)) + fadeIn(tween(PanelDurationMs)),
                exit = shrinkVertically(tween(PanelDurationMs, easing = PanelEasing)) + fadeOut(tween(PanelDurationMs)),
            ) {
                Box(Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp)) {
                    panel(option)
                }
            }
        }
    }
}

private fun labelFor(option: PaysioMethodKind, hasSaved: Boolean): String = when (option) {
    PaysioMethodKind.SAVED -> "Saved method"
    PaysioMethodKind.CARD -> if (hasSaved) "New card" else "Card"
    PaysioMethodKind.BANK -> if (hasSaved) "New bank account" else "Bank account"
}

@Composable
private fun PickerRow(
    option: PaysioMethodKind,
    label: String,
    isSelected: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    savedMethod: PaysioSavedMethod?,
    onClick: () -> Unit,
) {
    val theme = LocalPaysioTheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(150), label = "rowPress")
    val trailingAlpha by animateFloatAsState(if (isSelected) 0f else 1f, tween(200), label = "trailing")

    val top = if (isFirst) theme.rowRadiusOuter else theme.rowRadiusInner
    val bottom = if (isLast && !isSelected) theme.rowRadiusOuter else theme.rowRadiusInner
    val rowShape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)

    Row(
        Modifier
            .padding(4.dp)
            .scale(scale)
            .clip(rowShape)
            .background(if (isSelected) theme.colors.foreground.copy(alpha = 0.07f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { selected = isSelected }
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioDot(checked = isSelected)
        // Same Mynaui glyphs as the RN sheet: saved = credit-card-solid,
        // card = credit-card-plus-solid, bank = bank-solid; 20 dp, mutedForeground.
        PaysioIcon(
            when (option) {
                PaysioMethodKind.SAVED -> PaysioIconName.CREDIT_CARD_SOLID
                PaysioMethodKind.CARD -> PaysioIconName.CREDIT_CARD_PLUS_SOLID
                PaysioMethodKind.BANK -> PaysioIconName.BANK_SOLID
            },
            20.dp,
            theme.colors.mutedForeground,
        )
        Text(label, style = theme.font(14.sp, FontWeight.Medium))
        Spacer(Modifier.weight(1f).width(8.dp))
        Box(Modifier.alpha(trailingAlpha)) {
            when (option) {
                PaysioMethodKind.CARD -> AcceptedBrandStack(size = 28.dp)
                PaysioMethodKind.SAVED -> if (savedMethod != null) SavedMethodTrailing(savedMethod)
                PaysioMethodKind.BANK -> Unit
            }
        }
    }
}

/**
 * 18 dp radio: 1 dp border (muted 35 %) unchecked; primary fill + border, 4 dp ring at
 * 15 % and a 6 dp white dot scaling 0 -> 1 in 200 ms when checked.
 */
@Composable
internal fun RadioDot(checked: Boolean) {
    val theme = LocalPaysioTheme.current
    val dot by animateFloatAsState(if (checked) 1f else 0f, tween(200), label = "radioDot")
    val ring by animateFloatAsState(if (checked) 1f else 0f, tween(200), label = "radioRing")
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(26.dp).alpha(ring).clip(CircleShape).background(theme.colors.primary.copy(alpha = 0.15f))
        )
        if (checked) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(theme.colors.primary))
        } else {
            StrokeCircle(theme.colors.mutedForeground.copy(alpha = 0.35f), 18.dp)
        }
        Box(Modifier.size(6.dp).scale(dot).clip(CircleShape).background(Color.White))
    }
}
