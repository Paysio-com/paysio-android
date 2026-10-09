package com.paysio.sdk.ui

import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * The mobile stand-in for the web's Select / Popover lists, ported from the RN SDK's
 * BottomSheet.tsx: a sheet on a 50 % black backdrop, 18 dp top radius, `card`
 * background, a 36x4 grabber, an optional 14 sp title, an optional search field
 * (`secondary`, radius 8, 36 dp) and the rows the caller renders for the current query.
 * Opens over 260 ms with cubic-bezier(0.32, 0.72, 0, 1), closes over 200 ms; at most
 * [maxHeightRatio] of the window. With the keyboard up (its own search field) the
 * sheet sits on top of it, keeping a 48 dp strip of backdrop above.
 */
data class PaysioBottomSheetProps(
    val open: Boolean,
    val onClose: () -> Unit,
    val title: String? = null,
    val searchPlaceholder: String = "Search",
    val searchable: Boolean = false,
    val maxHeightRatio: Float = 0.72f,
    /** The rows for the current search query ("" when not searchable). */
    val content: @Composable (query: String) -> Unit,
)

/** A list row: optional leading element, label, optional detail on the right, selected fill. */
data class PaysioSheetRowProps(
    val selected: Boolean = false,
    val onClick: () -> Unit,
    val leading: (@Composable () -> Unit)? = null,
    val label: String? = null,
    val detail: String? = null,
    val trailing: (@Composable () -> Unit)? = null,
    val disabled: Boolean = false,
    /** Primary-coloured text (the "Enter a new address" action row). */
    val accent: Boolean = false,
    /** Custom row content (two-line address, saved card). Replaces label + detail. */
    val content: (@Composable RowScope.() -> Unit)? = null,
)

/**
 * An app that already has its own bottom sheet and list rows can render the SDK's
 * pick-lists (country, state, saved address, saved method, custom select fields) with
 * them, so they match the rest of the app (RN `PaysioSheetComponents`). Each part is
 * optional; anything not provided falls back to the SDK's built-in look. Provide it
 * with [PaysioSheetComponentsProvider] around the SDK UI, or pass it to
 * `PaysioPaymentSheet(sheetComponents = ...)`.
 */
data class PaysioSheetComponents(
    val sheet: (@Composable (PaysioBottomSheetProps) -> Unit)? = null,
    val row: (@Composable (PaysioSheetRowProps) -> Unit)? = null,
    val empty: (@Composable (text: String) -> Unit)? = null,
)

val LocalPaysioSheetComponents = staticCompositionLocalOf<PaysioSheetComponents?> { null }

@Composable
fun PaysioSheetComponentsProvider(components: PaysioSheetComponents?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPaysioSheetComponents provides components, content = content)
}

/** The sheet: the host's [PaysioSheetComponents.sheet] when provided, else the built-in one. */
@Composable
fun PaysioBottomSheet(props: PaysioBottomSheetProps) {
    val custom = LocalPaysioSheetComponents.current?.sheet
    if (custom != null) custom(props) else DefaultBottomSheet(props)
}

@Composable
fun PaysioBottomSheet(
    open: Boolean,
    onClose: () -> Unit,
    title: String? = null,
    searchable: Boolean = false,
    searchPlaceholder: String = "Search",
    maxHeightRatio: Float = 0.72f,
    content: @Composable (query: String) -> Unit,
) = PaysioBottomSheet(PaysioBottomSheetProps(open, onClose, title, searchPlaceholder, searchable, maxHeightRatio, content))

private val SheetOpenEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

@Composable
private fun DefaultBottomSheet(props: PaysioBottomSheetProps) {
    val theme = LocalPaysioTheme.current
    var visible by remember { mutableStateOf(props.open) }
    val progress = remember { Animatable(0f) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(props.open) {
        if (props.open) {
            // RN: Keyboard.dismiss() before the sheet comes up.
            focusManager.clearFocus()
            keyboard?.hide()
            visible = true
            progress.animateTo(1f, tween(260, easing = SheetOpenEasing))
        } else if (visible) {
            progress.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
            visible = false
        }
    }
    if (!visible) return

    Dialog(
        onDismissRequest = props.onClose,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Our own backdrop and motion: no platform dim, no window animation, and the
        // window resizes for the IME so the sheet can sit on top of the keyboard.
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setDimAmount(0f)
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setWindowAnimations(0)
                @Suppress("DEPRECATION")
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
        }
        var query by remember { mutableStateOf("") }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val windowHeight = maxHeight
            val density = LocalDensity.current
            val imeBottom = WindowInsets.ime.getBottom(density)
            val keyboardUp = imeBottom > 0
            val imeDp = with(density) { imeBottom.toDp() }
            val maxSheet = if (keyboardUp) minOf(windowHeight * props.maxHeightRatio, windowHeight - imeDp - 48.dp)
            else windowHeight * props.maxHeightRatio
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value }
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = props.onClose)
                    .semantics { contentDescription = "Close"; role = Role.Button },
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .graphicsLayer { translationY = (1f - progress.value) * size.height }
                    .fillMaxWidth()
                    .heightIn(max = maxSheet.coerceAtLeast(120.dp))
                    .shadow(24.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), clip = false,
                        ambientColor = Color(0x291A1A1A), spotColor = Color(0x291A1A1A))
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(theme.colors.card)
                    // Swallow taps so they don't reach the backdrop.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .then(if (keyboardUp) Modifier else Modifier.navigationBarsPadding())
                    .padding(top = 8.dp, bottom = if (keyboardUp) 8.dp else 16.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(theme.colors.mutedForeground.copy(alpha = 0.35f)),
                )
                props.title?.let {
                    Text(it, style = theme.font(14.sp, FontWeight.Medium), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                if (props.searchable) {
                    Box(
                        Modifier
                            .padding(start = 12.dp, end = 12.dp, bottom = 4.dp)
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.colors.secondary)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = theme.fieldTextStyle(),
                            cursorBrush = SolidColor(theme.colors.primary),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = props.searchPlaceholder },
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (query.isEmpty()) {
                                        Text(props.searchPlaceholder, style = theme.fieldTextStyle(theme.colors.mutedForeground.copy(alpha = 0.9f)), maxLines = 1)
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp),
                ) {
                    props.content(if (props.searchable) query else "")
                }
            }
        }
    }
}

/** A list row: the host's [PaysioSheetComponents.row] when provided, else the built-in one. */
@Composable
fun PaysioSheetRow(props: PaysioSheetRowProps) {
    val custom = LocalPaysioSheetComponents.current?.row
    if (custom != null) custom(props) else DefaultSheetRow(props)
}

@Composable
fun PaysioSheetRow(
    onClick: () -> Unit,
    selected: Boolean = false,
    label: String? = null,
    detail: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    disabled: Boolean = false,
    accent: Boolean = false,
    content: (@Composable RowScope.() -> Unit)? = null,
) = PaysioSheetRow(PaysioSheetRowProps(selected, onClick, leading, label, detail, trailing, disabled, accent, content))

/** RN DefaultSheetRow: min 44 dp, padding 10 / 8, radius 6, gap 10, 14 sp label, `secondary` when selected. */
@Composable
private fun DefaultSheetRow(props: PaysioSheetRowProps) {
    val theme = LocalPaysioTheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = when {
        props.selected -> theme.colors.secondary
        pressed -> theme.colors.rowHover
        else -> Color.Transparent
    }
    Row(
        Modifier
            .padding(bottom = 2.dp)
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .alpha(if (props.disabled) 0.5f else 1f)
            .clip(RoundedCornerShape(6.dp))
            .background(fill)
            .clickable(interactionSource = interaction, indication = null, enabled = !props.disabled, onClick = props.onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                selected = props.selected
            }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        props.leading?.invoke()
        val content = props.content
        if (content != null) {
            content()
        } else {
            Text(
                props.label ?: "",
                style = theme.font(14.sp, if (props.accent) FontWeight.Medium else FontWeight.Normal,
                    if (props.accent) theme.colors.primary else theme.colors.foreground),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        props.detail?.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = theme.font(12.sp, color = theme.colors.mutedForeground))
        }
        props.trailing?.invoke()
        if (props.selected && props.trailing == null) {
            PaysioIcon(PaysioIconName.CHECK, 16.dp, theme.colors.foreground)
        }
    }
}

/** The "nothing matches" line: the host's [PaysioSheetComponents.empty] when provided. */
@Composable
fun PaysioSheetEmpty(text: String) {
    val custom = LocalPaysioSheetComponents.current?.empty
    if (custom != null) {
        custom(text)
    } else {
        val theme = LocalPaysioTheme.current
        Text(text, style = theme.font(14.sp, color = theme.colors.mutedForeground), modifier = Modifier.padding(12.dp))
    }
}

/** Row content used by the saved-address list: name (14 sp medium) over the address (12 sp muted). */
@Composable
internal fun RowScope.TwoLineRowContent(title: String, subtitle: String) {
    val theme = LocalPaysioTheme.current
    Column(Modifier.weight(1f)) {
        Text(title, style = theme.font(14.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = theme.font(12.sp, color = theme.colors.mutedForeground), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
