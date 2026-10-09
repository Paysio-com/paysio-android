package com.paysio.sdk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.paysio.sdk.core.PaysioCustomer
import com.paysio.sdk.core.PaysioField

/**
 * Email, optional name row, and phone (when the workspace requires it), in the same
 * stacked look as the hosted page's contact block (RN ContactFields.tsx). The drop-in
 * sheet collects the name inside the billing address, so [hideName] defaults to true.
 * No leading icons on email or phone, as in RN; the first-name box carries user-solid.
 */
@Composable
fun ContactFields(
    customer: PaysioCustomer,
    onCustomerChange: (PaysioCustomer) -> Unit,
    requirePhone: Boolean,
    errors: Map<PaysioField, String> = emptyMap(),
    showHeading: Boolean = true,
    modifier: Modifier = Modifier,
    hideName: Boolean = true,
    /** Show a phone field even when not required ("Phone (optional)"). */
    showPhone: Boolean = false,
    onFieldFocus: ((PaysioField) -> Unit)? = null,
    /** Called when a field loses focus (the sheet shows its error from then on). */
    onFieldBlur: ((PaysioField) -> Unit)? = null,
) {
    val theme = LocalPaysioTheme.current
    var focus by remember { mutableStateOf<PaysioField?>(null) }
    fun focusOn(field: PaysioField, on: Boolean) {
        if (on) {
            focus = field
            onFieldFocus?.invoke(field)
        } else if (focus == field) {
            focus = null
            onFieldBlur?.invoke(field)
        }
    }
    val phoneVisible = requirePhone || showPhone
    val rows = 1 + (if (hideName) 0 else 1) + (if (phoneVisible) 1 else 0)
    val last = remember { FocusRequester() }
    val phone = remember { FocusRequester() }
    val iconTint = { f: Boolean -> if (f) theme.colors.foreground.copy(alpha = 0.8f) else theme.colors.mutedForeground.copy(alpha = 0.7f) }

    Column(modifier) {
        if (showHeading) SectionHeading("Contact")
        Column(verticalArrangement = Arrangement.spacedBy((-1).dp)) {
            StackedTextField(
                value = customer.email,
                onValueChange = { onCustomerChange(customer.copy(email = it)) },
                placeholder = "Email",
                contentDescription = "Email address",
                corners = if (rows == 1) PaysioCorners.ALL else PaysioCorners.TOP,
                keyboardType = KeyboardType.Email,
                autoCorrect = false,
                imeAction = if (rows > 1) ImeAction.Next else ImeAction.Done,
                invalid = errors.containsKey(PaysioField.EMAIL),
                onFocusChange = { focusOn(PaysioField.EMAIL, it) },
            )
            if (!hideName) {
                val nameFocused = focus == PaysioField.FIRST_NAME || focus == PaysioField.LAST_NAME
                val nameInvalid = errors.containsKey(PaysioField.FIRST_NAME) || errors.containsKey(PaysioField.LAST_NAME)
                Row(Modifier.zIndex(stackZ(nameFocused, nameInvalid)), horizontalArrangement = Arrangement.spacedBy((-1).dp)) {
                    StackedTextField(
                        value = customer.firstName,
                        onValueChange = { onCustomerChange(customer.copy(firstName = it)) },
                        placeholder = "First Name",
                        contentDescription = "First name",
                        corners = if (phoneVisible) PaysioCorners.NONE else PaysioCorners.BOTTOM_LEFT,
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                        onImeAction = { last.requestFocus() },
                        leading = { f -> PaysioIcon(PaysioIconName.USER_SOLID, 16.dp, iconTint(f)) },
                        invalid = errors.containsKey(PaysioField.FIRST_NAME),
                        onFocusChange = { focusOn(PaysioField.FIRST_NAME, it) },
                        modifier = Modifier.weight(1f),
                    )
                    StackedTextField(
                        value = customer.lastName,
                        onValueChange = { onCustomerChange(customer.copy(lastName = it)) },
                        placeholder = "Last Name",
                        contentDescription = "Last name",
                        corners = if (phoneVisible) PaysioCorners.NONE else PaysioCorners.BOTTOM_RIGHT,
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = if (phoneVisible) ImeAction.Next else ImeAction.Done,
                        onImeAction = if (phoneVisible) ({ phone.requestFocus() }) else null,
                        focusRequester = last,
                        invalid = errors.containsKey(PaysioField.LAST_NAME),
                        onFocusChange = { focusOn(PaysioField.LAST_NAME, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (phoneVisible) {
                StackedTextField(
                    value = customer.phone ?: "",
                    onValueChange = { onCustomerChange(customer.copy(phone = it.ifEmpty { null })) },
                    placeholder = if (requirePhone) "Phone" else "Phone (optional)",
                    contentDescription = "Phone number",
                    corners = PaysioCorners.BOTTOM,
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done,
                    focusRequester = phone,
                    invalid = errors.containsKey(PaysioField.PHONE),
                    onFocusChange = { focusOn(PaysioField.PHONE, it) },
                )
            }
        }
        FieldErrorText(errors[PaysioField.EMAIL] ?: errors[PaysioField.FIRST_NAME] ?: errors[PaysioField.LAST_NAME] ?: errors[PaysioField.PHONE])
    }
}
