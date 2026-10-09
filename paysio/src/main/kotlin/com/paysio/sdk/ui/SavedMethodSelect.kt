package com.paysio.sdk.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paysio.sdk.core.PaysioSavedMethod

private const val DOTS = "••••"

/**
 * Saved method select, ported from the RN SDK's SavedMethodSelect.tsx (web
 * SavedMethodSelect). Trigger: radius 10, border `input`, background `card`, padding
 * 12 / 10. Row: brand mark 32, gap 10, "••••" 14 sp muted, last4 14 sp medium,
 * optional badge chip, meta 12 sp muted pushed right, chevron 16 dp muted 70 %
 * rotating 180 when open. Open state: border primary 50 %, ring 2 dp primary 15 %.
 * The list is a bottom sheet with the same rows and an optional "Add a payment method".
 */
@Composable
fun SavedMethodSelect(
    methods: List<PaysioSavedMethod>,
    selected: PaysioSavedMethod?,
    onSelect: (PaysioSavedMethod) -> Unit,
    onAdd: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    title: String = "Saved method",
) {
    val theme = LocalPaysioTheme.current
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(methods) { if (selected == null) methods.firstOrNull { !it.disabled }?.let(onSelect) }
    val shape = RoundedCornerShape(10.dp)
    val chevron by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "chevron")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val ringColor = theme.colors.primary.copy(alpha = 0.15f)

    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                if (open) {
                    val w = 2.dp.toPx()
                    drawRoundRect(
                        ringColor,
                        topLeft = androidx.compose.ui.geometry.Offset(-w / 2, -w / 2),
                        size = androidx.compose.ui.geometry.Size(size.width + w, size.height + w),
                        cornerRadius = CornerRadius(11.dp.toPx()),
                        style = Stroke(w),
                    )
                }
            }
            .shadow(1.dp, shape, clip = false, ambientColor = Color(0x0D1A1A1A), spotColor = Color(0x0D1A1A1A))
            .clip(shape)
            .background(
                when {
                    open -> theme.colors.foreground.copy(alpha = 0.03f)
                    pressed -> theme.colors.rowHover
                    else -> theme.colors.card
                },
            )
            .border(
                1.dp,
                when {
                    open -> theme.colors.primary.copy(alpha = 0.5f)
                    pressed -> theme.colors.mutedForeground.copy(alpha = 0.3f)
                    else -> theme.colors.input
                },
                shape,
            )
            .clickable(interactionSource = interaction, indication = null) { open = true }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = selected?.let { "Saved method $DOTS ${it.last4}" } ?: "Select a method"
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (selected != null) {
            MethodRow(selected, 32.dp)
        } else {
            Text("Select a method…", style = theme.font(14.sp, color = theme.colors.mutedForeground), modifier = Modifier.weight(1f))
        }
        Box(Modifier.rotate(chevron)) { TriggerChevron(theme.colors.mutedForeground.copy(alpha = 0.7f)) }
    }

    PaysioBottomSheet(open = open, onClose = { open = false }, title = title) {
        methods.forEach { method ->
            PaysioSheetRow(
                onClick = { open = false; onSelect(method) },
                selected = method.id == selected?.id,
                disabled = method.disabled,
            ) { MethodRow(method, 32.dp) }
        }
        if (onAdd != null) {
            if (methods.isNotEmpty()) {
                Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(theme.colors.border))
            }
            PaysioSheetRow(onClick = { open = false; onAdd() }) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically) {
                    PaysioIcon(PaysioIconName.PLUS, 16.dp, theme.colors.mutedForeground)
                    Text("Add a payment method", style = theme.font(14.sp, FontWeight.Medium, theme.colors.mutedForeground))
                }
            }
        }
    }
}

/** RN SavedMethodIcon: the bank badge for bank methods, else the card brand (or its BIN). */
@Composable
internal fun SavedMethodIcon(method: PaysioSavedMethod, size: Dp) {
    if (method.isBank) CardBrandMark("bank", size) else CardBrandMark(method.brand, size, bin = method.bin)
}

@Composable
private fun Badge(text: String) {
    val theme = LocalPaysioTheme.current
    Text(
        text,
        style = theme.font(11.sp, FontWeight.Medium),
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(theme.colors.secondary).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun RowScope.MethodRow(method: PaysioSavedMethod, size: Dp) {
    val theme = LocalPaysioTheme.current
    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SavedMethodIcon(method, size)
        Text(DOTS, style = theme.font(14.sp, color = theme.colors.mutedForeground))
        Text(method.last4, style = theme.font(14.sp, FontWeight.Medium))
        method.badge?.takeIf { it.isNotEmpty() }?.let { Badge(it) }
        Spacer(Modifier.weight(1f))
        method.meta?.takeIf { it.isNotEmpty() }?.let { meta ->
            Text(meta.replaceFirstChar { it.uppercase() }, style = theme.font(12.sp, color = theme.colors.mutedForeground))
        }
    }
}

/** The preview on the collapsed "Saved method" picker row (RN SavedMethodTrailing). */
@Composable
internal fun SavedMethodTrailing(method: PaysioSavedMethod) {
    val theme = LocalPaysioTheme.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SavedMethodIcon(method, 28.dp)
        Text(DOTS, style = theme.font(12.sp, color = theme.colors.mutedForeground))
        Text(method.last4, style = theme.font(12.sp, FontWeight.Medium))
        method.badge?.takeIf { it.isNotEmpty() }?.let { Badge(it) }
    }
}
