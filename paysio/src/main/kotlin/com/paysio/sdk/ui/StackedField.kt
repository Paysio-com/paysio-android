package com.paysio.sdk.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/** Which corners of a stacked box are rounded (radius 8). */
data class PaysioCorners(
    val topLeft: Boolean = false,
    val topRight: Boolean = false,
    val bottomLeft: Boolean = false,
    val bottomRight: Boolean = false,
) {
    fun shape(radius: Dp = FieldRadius) = RoundedCornerShape(
        topStart = if (topLeft) radius else 0.dp,
        topEnd = if (topRight) radius else 0.dp,
        bottomStart = if (bottomLeft) radius else 0.dp,
        bottomEnd = if (bottomRight) radius else 0.dp,
    )

    companion object {
        val NONE = PaysioCorners()
        val TOP = PaysioCorners(topLeft = true, topRight = true)
        val BOTTOM = PaysioCorners(bottomLeft = true, bottomRight = true)
        val ALL = PaysioCorners(true, true, true, true)
        val TOP_LEFT = PaysioCorners(topLeft = true)
        val TOP_RIGHT = PaysioCorners(topRight = true)
        val BOTTOM_LEFT = PaysioCorners(bottomLeft = true)
        val BOTTOM_RIGHT = PaysioCorners(bottomRight = true)
    }
}

/** RN StackedBox stacking order: focused 40, invalid 20, otherwise 10. */
internal fun stackZ(focused: Boolean, invalid: Boolean): Float = if (focused) 40f else if (invalid) 20f else 10f

/** How far below a revealed element the keyboard's top edge stays (RN REVEAL_GAP). */
internal val RevealGap: Dp = 24.dp

/**
 * Keeps this element (plus [gap] below it) clear of the keyboard while [active], the
 * Compose twin of the RN SDK's KeyboardReveal. It re-requests "bring into view" on
 * every frame of the IME animation (WindowInsets.ime) and whenever the element
 * changes size (the address suggestions opening), so the enclosing scroll container
 * slides up together with the keyboard instead of after it.
 *
 * Needs the host to receive IME insets: an edge-to-edge activity with
 * `windowSoftInputMode="adjustResize"` and `imePadding()` on the scroll container
 * (PaysioPaymentSheet does this itself). Without a scrolling parent it is a no-op.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.paysioKeyboardReveal(active: Boolean, gap: Dp = RevealGap): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val size = remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val gapPx = with(density) { gap.toPx() }
        snapshotFlow { ime.getBottom(density) to size.value }.collect { (_, s) ->
            if (s.height > 0) requester.bringIntoView(Rect(0f, 0f, s.width.toFloat(), s.height + gapPx))
        }
    }
    this.onSizeChanged { size.value = it }.bringIntoViewRequester(requester)
}

/**
 * The focus ring of the hosted checkout: 2 dp of `ring` drawn OUTSIDE the box, with
 * the box's corner radii plus 2 (RN draws it as a sibling 2 px outside the box).
 */
private fun Modifier.focusRing(show: Boolean, color: Color, corners: PaysioCorners, radius: Dp): Modifier =
    if (!show) this else drawBehind {
        val width = 2.dp.toPx()
        val half = width / 2f
        fun r(on: Boolean) = CornerRadius((if (on) radius.toPx() else 0f) + half)
        val rr = RoundRect(
            left = -half, top = -half, right = size.width + half, bottom = size.height + half,
            topLeftCornerRadius = r(corners.topLeft),
            topRightCornerRadius = r(corners.topRight),
            bottomRightCornerRadius = r(corners.bottomRight),
            bottomLeftCornerRadius = r(corners.bottomLeft),
        )
        drawPath(Path().apply { addRoundRect(rr) }, color, style = Stroke(width))
    }

/**
 * The connected-field box of the hosted checkout (RN StackedBox): 36 dp high, 1 dp
 * `input` border, transparent background, clipped to its own corners. Adjacent boxes
 * overlap by 1 dp in their parent (`Arrangement.spacedBy((-1).dp)`) so two borders
 * collapse into one hairline seam; the focused box paints a 2 dp `ring` outside its
 * border and rises above its siblings; an invalid box (after blur) paints a 1 dp
 * `destructive` border and sits between the two.
 *
 * The border is drawn over the content, so nothing inside (VGS widgets included) can
 * draw its own outline, background or indicator line on top of it.
 */
@Composable
fun StackedFieldBox(
    corners: PaysioCorners,
    focused: Boolean = false,
    invalid: Boolean = false,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    trailingEnd: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val theme = LocalPaysioTheme.current
    val shape = corners.shape()
    val borderColor = if (invalid && !focused) theme.colors.destructive else theme.colors.input
    Box(
        modifier
            .zIndex(stackZ(focused, invalid))
            .fillMaxWidth()
            .height(FieldHeight)
            .focusRing(focused, theme.colors.ring, corners, FieldRadius)
            .clip(shape)
            .border(1.dp, borderColor, shape),
    ) {
        content()
        if (leading != null) {
            Box(Modifier.align(Alignment.CenterStart).padding(start = 10.dp)) { leading() }
        }
        if (trailing != null) {
            Box(Modifier.align(Alignment.CenterEnd).padding(end = trailingEnd)) { trailing() }
        }
    }
}

/** 16 sp field text, vertically centred in a 24 sp line (also the VGS widgets' style). */
internal fun PaysioResolvedTheme.fieldTextStyle(color: Color = colors.foreground): TextStyle =
    font(FieldFontSize, color = color).copy(
        lineHeight = 24.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

/**
 * A native text input in a [StackedFieldBox] (RN StackedInput): 16 sp text in
 * `foreground`, placeholder `mutedForeground` at 90 %, padding start 12 (32 with a
 * leading icon), end 12 (34 with a trailing element). The placeholder doubles as the
 * accessibility label unless [contentDescription] is given.
 */
@Composable
fun StackedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    corners: PaysioCorners,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalize: Boolean = false,
    capitalization: KeyboardCapitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None,
    invalid: Boolean = false,
    leading: (@Composable (focused: Boolean) -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    trailingEnd: Dp = 10.dp,
    paddingStart: Dp? = null,
    paddingEnd: Dp? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    contentDescription: String? = null,
    autoCorrect: Boolean = true,
    onFocusChange: ((Boolean) -> Unit)? = null,
) {
    val theme = LocalPaysioTheme.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { onFocusChange?.invoke(focused) }

    StackedFieldBox(
        corners = corners,
        focused = focused,
        invalid = invalid,
        modifier = modifier.paysioKeyboardReveal(focused),
        leading = leading?.let { l -> { l(focused) } },
        trailing = trailing,
        trailingEnd = trailingEnd,
    ) {
        val label = contentDescription ?: placeholder
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxSize()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .semantics { this.contentDescription = label },
            textStyle = theme.fieldTextStyle(),
            singleLine = true,
            interactionSource = interaction,
            cursorBrush = SolidColor(theme.colors.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = capitalization,
                autoCorrectEnabled = autoCorrect,
                imeAction = imeAction,
            ),
            keyboardActions = if (onImeAction != null) KeyboardActions(onAny = { onImeAction() }) else KeyboardActions.Default,
            visualTransformation = visualTransformation,
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = paddingStart ?: if (leading != null) 32.dp else 12.dp,
                            end = paddingEnd ?: if (trailing != null) 34.dp else 12.dp,
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = theme.fieldTextStyle(theme.colors.mutedForeground.copy(alpha = 0.9f)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

/**
 * A select trigger in the box (RN StackedTrigger: country, state, custom selects):
 * same height, text and padding as the input; `value` in foreground, the placeholder
 * in muted at 90 %; a chevron (or any [trailing]) on the right; `rowHover` fill while
 * pressed. Its accessibility label is [contentDescription] (default: the placeholder).
 */
@Composable
fun StackedTrigger(
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    corners: PaysioCorners,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    invalid: Boolean = false,
    contentDescription: String? = null,
) {
    val theme = LocalPaysioTheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    StackedFieldBox(corners = corners, invalid = invalid, modifier = modifier, leading = leading, trailing = trailing) {
        Box(
            Modifier
                .fillMaxSize()
                .background(if (pressed) theme.colors.rowHover else Color.Transparent)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    this.contentDescription = contentDescription ?: placeholder
                }
                .padding(start = if (leading != null) 32.dp else 12.dp, end = if (trailing != null) 34.dp else 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                value.ifEmpty { placeholder },
                style = theme.fieldTextStyle(if (value.isNotEmpty()) theme.colors.foreground else theme.colors.mutedForeground.copy(alpha = 0.9f)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The chevron every trigger uses: Mynaui chevron-down, 16 dp. */
@Composable
internal fun TriggerChevron(color: Color = LocalPaysioTheme.current.colors.mutedForeground) {
    PaysioIcon(PaysioIconName.CHEVRON_DOWN, 16.dp, color)
}

/**
 * Lays a Material3-based VGS widget (56 dp minimum, 16 dp content padding) out at its
 * own height, centred in the 36 dp box, and shifted so its text starts [start] from
 * the box edge and ends [end] before the other edge. The box clips the overflow, so
 * the widget shows exactly like a 36 dp input with RN's paddings.
 */
internal fun Modifier.vgsFieldInset(start: Dp = 12.dp, end: Dp = 12.dp): Modifier = layout { measurable, constraints ->
    val material = 16.dp.roundToPx()
    val left = material - start.roundToPx()
    val right = material - end.roundToPx()
    val width = constraints.maxWidth
    val childWidth = (width + left + right).coerceAtLeast(0)
    val childHeight = VgsMaterialHeight.roundToPx()
    val placeable = measurable.measure(Constraints.fixed(childWidth, childHeight))
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else FieldHeight.roundToPx()
    layout(width, height) { placeable.place(-left, (height - childHeight) / 2) }
}

/** The Material3 TextField minimum height the VGS Compose widgets are built on. */
private val VgsMaterialHeight: Dp = 56.dp

/** Section heading: 14 sp medium, muted, 8 dp to the fields. */
@Composable
internal fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    val theme = LocalPaysioTheme.current
    Text(text, style = theme.font(14.sp, FontWeight.Medium, theme.colors.mutedForeground), modifier = modifier.padding(bottom = 8.dp))
}

/**
 * Inline cycle toggle (RN InlineChip, web InlineFieldToggle): tapping advances to the
 * next option. 11 sp medium muted, radius 6, padding 6 / 4, `background` fill while pressed.
 */
@Composable
internal fun InlineChip(label: String, nextLabel: String, onClick: () -> Unit) {
    val theme = LocalPaysioTheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Text(
        text = label,
        style = theme.font(11.sp, FontWeight.Medium, theme.colors.mutedForeground),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (pressed) theme.colors.background else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$label. Switch to $nextLabel"
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/** Inline validation message under a stack (destructive, 12 sp). */
@Composable
internal fun FieldErrorText(message: String?) {
    if (message == null) return
    val theme = LocalPaysioTheme.current
    Text(message, style = theme.font(12.sp, color = theme.colors.destructive).copy(lineHeight = 16.sp), modifier = Modifier.padding(top = 8.dp))
}

/** 12 sp muted helper text. */
@Composable
internal fun HelpText(text: String, modifier: Modifier = Modifier) {
    val theme = LocalPaysioTheme.current
    Text(text, style = theme.font(12.sp, color = theme.colors.mutedForeground).copy(lineHeight = 16.sp), modifier = modifier)
}

/** 12 sp primary link ("Use existing address"). */
@Composable
internal fun LinkText(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = LocalPaysioTheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Text(
        text,
        style = theme.font(12.sp, color = theme.colors.primary.copy(alpha = if (pressed) 0.8f else 1f)),
        modifier = modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { role = Role.Button },
    )
}

/** Transparent Material3 colours so the VGS TextFields sit flat inside our boxes. */
@Composable
internal fun flatTextFieldColors(): androidx.compose.material3.TextFieldColors {
    val theme = LocalPaysioTheme.current
    val muted = theme.colors.mutedForeground.copy(alpha = 0.9f)
    return androidx.compose.material3.TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        cursorColor = theme.colors.primary,
        errorCursorColor = theme.colors.primary,
        focusedTextColor = theme.colors.foreground,
        unfocusedTextColor = theme.colors.foreground,
        errorTextColor = theme.colors.foreground,
        focusedPlaceholderColor = muted,
        unfocusedPlaceholderColor = muted,
        errorPlaceholderColor = muted,
        errorSupportingTextColor = theme.colors.destructive,
    )
}
