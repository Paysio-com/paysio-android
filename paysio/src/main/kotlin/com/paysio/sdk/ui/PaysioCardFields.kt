package com.paysio.sdk.ui

import android.content.Context
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.zIndex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.paysio.sdk.core.PaysioCardAlias
import com.paysio.sdk.core.PaysioCardFieldsState
import com.paysio.sdk.core.PaysioError
import com.paysio.sdk.core.PaysioErrorCode
import com.paysio.sdk.core.PaysioEventHandler
import com.paysio.sdk.core.PaysioEvent
import com.paysio.sdk.core.PaysioField
import com.paysio.sdk.core.PaysioFieldState
import com.paysio.sdk.core.PaysioValidation
import com.paysio.sdk.core.PaysioVault
import com.paysio.sdk.core.PaysioVaultConfig
import com.verygoodsecurity.vgscollect.widget.compose.date.VgsExpiryDateFormat
import com.verygoodsecurity.vgscollect.widget.compose.material3.VgsCardNumberTextField
import com.verygoodsecurity.vgscollect.widget.compose.material3.VgsCvcTextField
import com.verygoodsecurity.vgscollect.widget.compose.material3.VgsExpiryTextField
import com.verygoodsecurity.vgscollect.widget.compose.state.VgsCardNumberTextFieldState
import com.verygoodsecurity.vgscollect.widget.compose.state.VgsCvcTextFieldState
import com.verygoodsecurity.vgscollect.widget.compose.state.VgsExpiryTextFieldState
import com.verygoodsecurity.vgscollect.widget.compose.state.rememberVgsCardNumberTextFieldState
import com.verygoodsecurity.vgscollect.widget.compose.state.rememberVgsCvcTextFieldState
import com.verygoodsecurity.vgscollect.widget.compose.state.rememberVgsExpiryTextFieldState
import com.verygoodsecurity.vgscollect.widget.compose.validator.VgsRequiredFieldValidator
import com.verygoodsecurity.vgscollect.widget.compose.validator.VgsTextLengthValidator

/**
 * Owns the `VGSCollect` instance, keeps the latest VGS Compose states, tracks UI state,
 * and turns the fields into a [PaysioCardAlias] on demand.
 *
 * VGS API relied on (vgs-collect-android 2.1.0, package
 * `com.verygoodsecurity.vgscollect.widget.compose`):
 * - `rememberVgsCardNumberTextFieldState(collect, fieldName, validators, isCardBrandValidationEnabled, ...)`
 * - `rememberVgsExpiryTextFieldState(collect, fieldName, validators, inputDateFormat, outputDateFormat, ...)`
 * - `rememberVgsCvcTextFieldState(collect, fieldName, ...)` and `VgsCvcTextFieldState.withCardBrand(brand)`
 * - `BaseFieldState.isValid / isEmpty / contentLength`, `VgsCardNumberTextFieldState.bin / last4 / cardBrand`
 * - material3 `VgsCardNumberTextField`, `VgsExpiryTextField`, `VgsCvcTextField` (state, onStateChange,
 *   modifier, textStyle, placeholder, isError, keyboardOptions, interactionSource, shape, colors)
 * - validators `VgsRequiredFieldValidator()`, `VgsTextLengthValidator(lengths: Array<Int>)`.
 */
class PaysioCardFieldsController(
    context: Context,
    vaultConfig: PaysioVaultConfig,
    /** Relaxes the Luhn check; the VGS 3DS sandbox cards are not Luhn-valid. */
    val sandbox: Boolean,
    var onEvent: PaysioEventHandler? = null,
) {
    val vault = PaysioVault(context, vaultConfig)

    var state: PaysioCardFieldsState by mutableStateOf(PaysioCardFieldsState())
        private set

    internal var numberState: VgsCardNumberTextFieldState? = null
    internal var expiryState: VgsExpiryTextFieldState? = null
    internal var cvcState: VgsCvcTextFieldState? = null

    internal fun sync(
        number: VgsCardNumberTextFieldState, numberFocused: Boolean, numberDirty: Boolean,
        expiry: VgsExpiryTextFieldState, expiryFocused: Boolean, expiryDirty: Boolean,
        cvc: VgsCvcTextFieldState, cvcFocused: Boolean, cvcDirty: Boolean,
    ) {
        numberState = number
        expiryState = expiry
        cvcState = cvc
        state = PaysioCardFieldsState(
            number = PaysioFieldState(
                valid = number.isValid, empty = number.isEmpty, focused = numberFocused,
                dirty = numberDirty || !number.isEmpty || state.number.dirty,
                error = if (number.isValid) null else if (number.isEmpty) "Enter your card number." else "Check your card number.",
            ),
            expiry = PaysioFieldState(
                valid = expiry.isValid, empty = expiry.isEmpty, focused = expiryFocused,
                dirty = expiryDirty || !expiry.isEmpty || state.expiry.dirty,
                error = if (expiry.isValid) null else if (expiry.isEmpty) "Enter the expiry date." else "Check the expiry date.",
            ),
            cvc = PaysioFieldState(
                valid = cvc.isValid, empty = cvc.isEmpty, focused = cvcFocused,
                dirty = cvcDirty || !cvc.isEmpty || state.cvc.dirty,
                error = if (cvc.isValid) null else if (cvc.isEmpty) "Enter the security code." else "Check the security code.",
            ),
            brand = if (number.isEmpty) null else PaysioValidation.normalizeBrand(number.cardBrand.name),
            bin = number.bin?.takeIf { it.isNotEmpty() },
            last4 = number.last4?.takeIf { it.isNotEmpty() },
        )
    }

    /** Marks every field dirty so invalid borders show after a failed Pay tap. */
    fun touchAll() {
        state = state.copy(
            number = state.number.copy(dirty = true),
            expiry = state.expiry.copy(dirty = true),
            cvc = state.cvc.copy(dirty = true),
        )
    }

    /** Tokenizes with VGS. Throws `invalid_fields` first if any field is invalid. */
    suspend fun tokenize(): PaysioCardAlias {
        val n = numberState; val e = expiryState; val c = cvcState
        if (n == null || e == null || c == null || !state.valid) {
            touchAll()
            val errors = LinkedHashMap<PaysioField, String>()
            if (!state.number.valid) errors[PaysioField.NUMBER] = state.number.error ?: "Check your card number."
            if (!state.expiry.valid) errors[PaysioField.EXPIRY] = state.expiry.error ?: "Check the expiry date."
            if (!state.cvc.valid) errors[PaysioField.CVC] = state.cvc.error ?: "Check the security code."
            throw PaysioError(PaysioErrorCode.INVALID_FIELDS, "Check your card details.", fieldErrors = errors)
        }
        return vault.tokenizeCard(n, e, c)
    }

    /** Releases VGS resources. The composable calls this when it leaves composition. */
    fun destroy() {
        vault.destroy()
    }
}

/**
 * The stacked card fields (MOBILE_SDK_PLAN.md section 8.2): number on top, expiry and
 * CVC below, 1 dp overlaps, focus ring, invalid border after blur, brand mark on the
 * right of the number, lock in the CVC box. Ported 1:1 from the RN SDK's CardFields.tsx:
 *
 *   number on top (radius 8 top corners, text end padding 92 for the brand mark);
 *   below it expiry (weight 1, radius 8 bottom-left) and CVC (weight 1, radius 8
 *   bottom-right, end padding 34 for the lock), all 36 dp high and overlapping 1 dp,
 *   so the three boxes read as one group with shared 1 dp seams and no inner radius.
 *   Placeholders "0000 0000 0000 0000", "MM / YY", "CVC"; the detected brand mark at
 *   30 dp (before a brand is known: the accepted-brand stack at 24 dp); lock-solid
 *   16 dp in mutedForeground at 70 % in the CVC box.
 *
 * The CVC is shown as digits, not masked, like the hosted checkout (the value never
 * leaves the VGS field anyway). The sheet applies `FLAG_SECURE`.
 */
@Composable
fun PaysioCardFields(controller: PaysioCardFieldsController, showHeading: Boolean = true, modifier: Modifier = Modifier) {
    val theme = LocalPaysioTheme.current
    val collect = controller.vault.collect
    val sandbox = controller.sandbox

    var numberState by rememberVgsCardNumberTextFieldState(
        collect = collect,
        fieldName = PaysioVault.FieldName.CARD_NUMBER,
        // Sandbox: length-only rule (same idea as `relaxNumberValidation` on the web).
        validators = if (sandbox) listOf(VgsRequiredFieldValidator(), VgsTextLengthValidator(arrayOf(13, 14, 15, 16, 19))) else null,
        isCardBrandValidationEnabled = !sandbox,
    )
    var expiryState by rememberVgsExpiryTextFieldState(
        collect = collect,
        fieldName = PaysioVault.FieldName.CARD_EXPIRY,
        // Output "MM/yy" -> the contract's "MM/YY" (CONTRACT.md section 2).
        inputDateFormat = VgsExpiryDateFormat.MonthShortYear,
        outputDateFormat = VgsExpiryDateFormat.MonthShortYear,
    )
    var cvcState by rememberVgsCvcTextFieldState(
        collect = collect,
        fieldName = PaysioVault.FieldName.CARD_CVC,
    )
    // CVC length follows the detected brand (Amex = 4), per the VGS agent guide.
    LaunchedEffect(numberState.cardBrand) { cvcState = cvcState.withCardBrand(numberState.cardBrand) }

    val numberInteraction = remember { MutableInteractionSource() }
    val expiryInteraction = remember { MutableInteractionSource() }
    val cvcInteraction = remember { MutableInteractionSource() }
    val numberFocused by numberInteraction.collectIsFocusedAsState()
    val expiryFocused by expiryInteraction.collectIsFocusedAsState()
    val cvcFocused by cvcInteraction.collectIsFocusedAsState()
    var numberDirty by remember { mutableStateOf(false) }
    var expiryDirty by remember { mutableStateOf(false) }
    var cvcDirty by remember { mutableStateOf(false) }
    FocusTracker(numberFocused, PaysioField.NUMBER, controller.onEvent) { numberDirty = true }
    FocusTracker(expiryFocused, PaysioField.EXPIRY, controller.onEvent) { expiryDirty = true }
    FocusTracker(cvcFocused, PaysioField.CVC, controller.onEvent) { cvcDirty = true }

    LaunchedEffect(numberState, expiryState, cvcState, numberFocused, expiryFocused, cvcFocused, numberDirty, expiryDirty, cvcDirty) {
        controller.sync(numberState, numberFocused, numberDirty, expiryState, expiryFocused, expiryDirty, cvcState, cvcFocused, cvcDirty)
    }
    DisposableEffect(controller) { onDispose { controller.destroy() } }

    val s = controller.state
    val colors = flatTextFieldColors()
    val textStyle = theme.fieldTextStyle()
    val placeholderStyle = theme.fieldTextStyle(theme.colors.mutedForeground.copy(alpha = 0.9f))
    val numberInvalid = s.number.showsInvalid
    val expiryInvalid = s.expiry.showsInvalid
    val cvcInvalid = s.cvc.showsInvalid

    Column(modifier) {
        if (showHeading) SectionHeading("Card information")

        Column(verticalArrangement = Arrangement.spacedBy((-1).dp)) {
            StackedFieldBox(
                PaysioCorners.TOP,
                focused = numberFocused,
                invalid = numberInvalid,
                modifier = Modifier.paysioKeyboardReveal(numberFocused),
                trailing = {
                    Box(Modifier.offset(y = 1.dp)) {
                        CardBrand(
                            brand = s.brand,
                            bin = s.bin,
                            size = 30.dp,
                            animate = true,
                            fallbackColor = theme.colors.mutedForeground,
                            fallback = { AcceptedBrandStack(size = 24.dp) },
                        )
                    }
                },
            ) {
                VgsCardNumberTextField(
                    state = numberState,
                    onStateChange = { numberState = it },
                    modifier = Modifier.vgsFieldInset(start = 12.dp, end = 92.dp).semantics { contentDescription = "Card number" },
                    textStyle = textStyle,
                    placeholder = { Text("0000 0000 0000 0000", style = placeholderStyle, maxLines = 1) },
                    isError = numberInvalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    interactionSource = numberInteraction,
                    shape = RectangleShape,
                    colors = colors,
                )
            }
            // The row rises with whichever of its boxes is raised, so a ring or red
            // border on its top edge draws over the number box's bottom border.
            Row(
                Modifier.zIndex(maxOf(stackZ(expiryFocused, expiryInvalid), stackZ(cvcFocused, cvcInvalid))),
                horizontalArrangement = Arrangement.spacedBy((-1).dp),
            ) {
                StackedFieldBox(
                    PaysioCorners.BOTTOM_LEFT,
                    focused = expiryFocused,
                    invalid = expiryInvalid,
                    modifier = Modifier.weight(1f).paysioKeyboardReveal(expiryFocused),
                ) {
                    VgsExpiryTextField(
                        state = expiryState,
                        onStateChange = { expiryState = it },
                        modifier = Modifier.vgsFieldInset(start = 12.dp, end = 12.dp)
                            .semantics { contentDescription = "Expiration date, MM slash YY" },
                        textStyle = textStyle,
                        placeholder = { Text("MM / YY", style = placeholderStyle, maxLines = 1) },
                        isError = expiryInvalid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        interactionSource = expiryInteraction,
                        shape = RectangleShape,
                        colors = colors,
                    )
                }
                StackedFieldBox(
                    PaysioCorners.BOTTOM_RIGHT,
                    focused = cvcFocused,
                    invalid = cvcInvalid,
                    modifier = Modifier.weight(1f).paysioKeyboardReveal(cvcFocused),
                    trailing = { PaysioIcon(PaysioIconName.LOCK_SOLID, 16.dp, theme.colors.mutedForeground.copy(alpha = 0.7f)) },
                ) {
                    // Not masked: the hosted checkout shows the CVC digits, and the value
                    // never leaves the VGS field anyway.
                    VgsCvcTextField(
                        state = cvcState,
                        onStateChange = { cvcState = it },
                        modifier = Modifier.vgsFieldInset(start = 12.dp, end = 34.dp).semantics { contentDescription = "Security code" },
                        textStyle = textStyle,
                        placeholder = { Text("CVC", style = placeholderStyle, maxLines = 1) },
                        isError = cvcInvalid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        interactionSource = cvcInteraction,
                        shape = RectangleShape,
                        colors = colors,
                    )
                }
            }
        }

        FieldErrorText(
            when {
                s.number.showsInvalid -> s.number.error
                s.expiry.showsInvalid -> s.expiry.error
                s.cvc.showsInvalid -> s.cvc.error
                else -> null
            },
        )
    }
}

/** Emits a focus event on focus and marks the field dirty on blur. */
@Composable
internal fun FocusTracker(focused: Boolean, field: PaysioField, onEvent: PaysioEventHandler?, onBlur: () -> Unit) {
    var wasFocused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) {
            wasFocused = true
            onEvent?.invoke(PaysioEvent.FieldFocus(field.wire))
        } else if (wasFocused) {
            onBlur()
        }
    }
}
