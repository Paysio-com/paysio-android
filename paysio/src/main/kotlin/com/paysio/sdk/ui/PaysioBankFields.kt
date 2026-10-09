package com.paysio.sdk.ui

import android.content.Context
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paysio.sdk.core.PaysioBankAccountType
import com.paysio.sdk.core.PaysioBankAlias
import com.paysio.sdk.core.PaysioBankFieldsState
import com.paysio.sdk.core.PaysioBankHolderType
import com.paysio.sdk.core.PaysioError
import com.paysio.sdk.core.PaysioErrorCode
import com.paysio.sdk.core.PaysioEventHandler
import com.paysio.sdk.core.PaysioField
import com.paysio.sdk.core.PaysioFieldState
import com.paysio.sdk.core.PaysioEvent
import com.paysio.sdk.core.PaysioValidation
import com.paysio.sdk.core.PaysioVault
import com.paysio.sdk.core.PaysioVaultConfig
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.verygoodsecurity.vgscollect.widget.compose.material3.VgsTextField
import com.verygoodsecurity.vgscollect.widget.compose.state.VgsTextFieldState
import com.verygoodsecurity.vgscollect.widget.compose.state.rememberVgsTextFieldState
import com.verygoodsecurity.vgscollect.widget.compose.validator.VgsRegexValidator
import com.verygoodsecurity.vgscollect.widget.compose.validator.VgsRequiredFieldValidator

/**
 * Owns a separate `VGSCollect` for the bank account field, validates the routing
 * number natively, and produces a [PaysioBankAlias].
 */
class PaysioBankFieldsController(
    context: Context,
    vaultConfig: PaysioVaultConfig,
    var onEvent: PaysioEventHandler? = null,
) {
    val vault = PaysioVault(context, vaultConfig)

    var state: PaysioBankFieldsState by mutableStateOf(PaysioBankFieldsState())
        private set

    // JvmName: the generated setter would clash with the public setRoutingNumber() below.
    @set:JvmName("updateRoutingNumber")
    var routingNumber: String by mutableStateOf("")
        private set

    internal var accountState: VgsTextFieldState? = null

    fun setRoutingNumber(value: String) {
        val digits = value.filter { it.isDigit() }.take(9)
        routingNumber = digits
        val valid = PaysioValidation.isRoutingNumber(digits)
        state = state.copy(
            routing = state.routing.copy(
                valid = valid, empty = digits.isEmpty(), dirty = state.routing.dirty || digits.isNotEmpty(),
                error = if (valid) null else if (digits.isEmpty()) "Enter the routing number." else "Check the routing number.",
            ),
        )
    }

    fun setAccountType(type: PaysioBankAccountType) { state = state.copy(accountType = type) }
    fun setHolderType(type: PaysioBankHolderType) { state = state.copy(holderType = type) }

    internal fun cycleAccountType() =
        setAccountType(if (state.accountType == PaysioBankAccountType.CHECKING) PaysioBankAccountType.SAVINGS else PaysioBankAccountType.CHECKING)

    internal fun cycleHolderType() =
        setHolderType(if (state.holderType == PaysioBankHolderType.PERSONAL) PaysioBankHolderType.BUSINESS else PaysioBankHolderType.PERSONAL)

    internal fun setRoutingFocus(focused: Boolean, dirty: Boolean) {
        state = state.copy(routing = state.routing.copy(focused = focused, dirty = state.routing.dirty || dirty))
    }

    internal fun syncAccount(account: VgsTextFieldState, focused: Boolean, dirty: Boolean) {
        accountState = account
        state = state.copy(
            account = PaysioFieldState(
                valid = account.isValid, empty = account.isEmpty, focused = focused,
                dirty = dirty || !account.isEmpty || state.account.dirty,
                error = if (account.isValid) null else if (account.isEmpty) "Enter the account number." else "Check the account number.",
            ),
        )
    }

    fun touchAll() {
        state = state.copy(routing = state.routing.copy(dirty = true), account = state.account.copy(dirty = true))
    }

    suspend fun tokenize(): PaysioBankAlias {
        val account = accountState
        if (account == null || !state.valid) {
            touchAll()
            val errors = LinkedHashMap<PaysioField, String>()
            if (!state.routing.valid) errors[PaysioField.ROUTING] = state.routing.error ?: "Check the routing number."
            if (!state.account.valid) errors[PaysioField.ACCOUNT] = state.account.error ?: "Check the account number."
            throw PaysioError(PaysioErrorCode.INVALID_FIELDS, "Check your bank details.", fieldErrors = errors)
        }
        return vault.tokenizeBank(account, routingNumber, state.accountType, state.holderType)
    }

    fun destroy() { vault.destroy() }
}

/**
 * Bank details, ported from the RN SDK's BankFields.tsx (web VaultBankFields): the
 * routing number is public and stays a native input (9 digits, ABA checksum) with the
 * inline "Personal / Business" chip; the account number is a VGS field named
 * `bank_account_number` with the "Checking / Savings" chip. Both are 36 dp stacked
 * boxes, radius 8 on the top corners of the routing box and the bottom corners of the
 * account box only, sharing one 1 dp seam. [achAuthorizationText], when given, renders
 * below as help text; the drop-in sheet shows it as a consent checkbox instead.
 */
@Composable
fun PaysioBankFields(
    controller: PaysioBankFieldsController,
    achAuthorizationText: String? = null,
    showHeading: Boolean = true,
    modifier: Modifier = Modifier,
    /** Hide the account-type / holder-type chips (AptPay ignores both). */
    hideToggles: Boolean = false,
) {
    val theme = LocalPaysioTheme.current
    var accountState by rememberVgsTextFieldState(
        collect = controller.vault.collect,
        fieldName = PaysioVault.FieldName.BANK_ACCOUNT_NUMBER,
        validators = listOf(VgsRequiredFieldValidator(), VgsRegexValidator("^[0-9]{4,17}$")),
    )
    val accountInteraction = remember { MutableInteractionSource() }
    val accountFocused by accountInteraction.collectIsFocusedAsState()
    var accountDirty by remember { mutableStateOf(false) }
    FocusTracker(accountFocused, PaysioField.ACCOUNT, controller.onEvent) { accountDirty = true }
    LaunchedEffect(accountState, accountFocused, accountDirty) { controller.syncAccount(accountState, accountFocused, accountDirty) }
    DisposableEffect(controller) { onDispose { controller.destroy() } }

    val s = controller.state
    Column(modifier) {
        if (showHeading) SectionHeading("Bank details")
        val chipEnd = 6.dp
        val textEnd = if (hideToggles) 12.dp else 92.dp
        Column(verticalArrangement = Arrangement.spacedBy((-1).dp)) {
            StackedTextField(
                value = controller.routingNumber,
                onValueChange = { controller.setRoutingNumber(it) },
                placeholder = "Routing number",
                corners = PaysioCorners.TOP,
                keyboardType = KeyboardType.Number,
                autoCorrect = false,
                invalid = s.routing.showsInvalid,
                paddingEnd = textEnd,
                trailingEnd = chipEnd,
                trailing = if (hideToggles) null else {
                    {
                        val next = if (s.holderType == PaysioBankHolderType.PERSONAL) PaysioBankHolderType.BUSINESS else PaysioBankHolderType.PERSONAL
                        InlineChip(s.holderType.label, next.label) { controller.cycleHolderType() }
                    }
                },
                onFocusChange = { focused ->
                    if (focused) controller.onEvent?.invoke(PaysioEvent.FieldFocus(PaysioField.ROUTING.wire))
                    controller.setRoutingFocus(focused, dirty = !focused && s.routing.focused)
                },
            )
            StackedFieldBox(
                PaysioCorners.BOTTOM,
                focused = accountFocused,
                invalid = s.account.showsInvalid,
                modifier = Modifier.paysioKeyboardReveal(accountFocused),
                trailingEnd = chipEnd,
                trailing = if (hideToggles) null else {
                    {
                        val next = if (s.accountType == PaysioBankAccountType.CHECKING) PaysioBankAccountType.SAVINGS else PaysioBankAccountType.CHECKING
                        InlineChip(s.accountType.label, next.label) { controller.cycleAccountType() }
                    }
                },
            ) {
                VgsTextField(
                    state = accountState,
                    onStateChange = { accountState = it },
                    modifier = Modifier.vgsFieldInset(start = 12.dp, end = textEnd).semantics { contentDescription = "Account number" },
                    textStyle = theme.fieldTextStyle(),
                    placeholder = { Text("Account number", style = theme.fieldTextStyle(theme.colors.mutedForeground.copy(alpha = 0.9f)), maxLines = 1) },
                    isError = s.account.showsInvalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    interactionSource = accountInteraction,
                    shape = RectangleShape,
                    colors = flatTextFieldColors(),
                )
            }
        }
        FieldErrorText(if (s.routing.showsInvalid) s.routing.error else if (s.account.showsInvalid) s.account.error else null)
        if (!achAuthorizationText.isNullOrEmpty()) {
            HelpText(achAuthorizationText, Modifier.padding(top = 12.dp))
        }
    }
}
