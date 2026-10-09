package com.paysio.sdk.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paysio.sdk.core.PaysioAddress
import com.paysio.sdk.core.PaysioClient
import com.paysio.sdk.core.PaysioCustomField
import com.paysio.sdk.core.PaysioCustomer
import com.paysio.sdk.core.PaysioError
import com.paysio.sdk.core.PaysioErrorCode
import com.paysio.sdk.core.PaysioEvent
import com.paysio.sdk.core.PaysioEventHandler
import com.paysio.sdk.core.PaysioField
import com.paysio.sdk.core.PaysioJsonScalar
import com.paysio.sdk.core.PaysioMethodKind
import com.paysio.sdk.core.PaysioPay
import com.paysio.sdk.core.PaysioPayOptions
import com.paysio.sdk.core.PaysioPayResult
import com.paysio.sdk.core.PaysioPayStep
import com.paysio.sdk.core.PaysioPaymentSource
import com.paysio.sdk.core.PaysioSavedAddress
import com.paysio.sdk.core.PaysioSavedMethod
import com.paysio.sdk.core.PaysioSession
import com.paysio.sdk.core.PaysioSessionLoader
import com.paysio.sdk.core.PaysioValidation
import com.paysio.sdk.threeds.WebViewThreeDsHost
import kotlinx.coroutines.launch

/** Authorization for charging a saved method (phase 2). */
data class SavedMethodAuthorization(val portalToken: String, val cvc: String? = null)

/**
 * The drop-in checkout, laid out like the RN SDK's PaymentSheet.tsx: header, contact,
 * the payment method picker (card and bank panels each carry the billing address; the
 * bank panel ends with the ACH consent checkbox), custom fields, an error banner and the
 * Pay button, which stays disabled until everything is valid (RN `canPay`).
 *
 * Keyboard: the content is a scroll container with `imePadding()`, and every field keeps
 * itself (and the address suggestions) above the keyboard in step with the IME
 * animation. For that the host activity must be edge-to-edge (`enableEdgeToEdge()`)
 * with `android:windowSoftInputMode="adjustResize"`; with a non-edge-to-edge window the
 * system resize still keeps the focused field visible, just without the animation sync.
 *
 * @param secureScreen adds `FLAG_SECURE` to the activity window while the sheet is shown
 *   (no screenshots or screen recording of card entry) and removes it on dispose. Pass
 *   `false` if your app manages the flag itself.
 * @param savedMethodAuthorizer required when [savedMethods] is non-empty: returns the
 *   portal token (quick-checkout email code or `POST /api/storefront-account/:id/checkout-authorization`).
 * @param sheetComponents your own bottom sheet / row / empty-state look for the pick-lists.
 * @param disableAddressSuggestions turns off street suggestions (Places through Paysio).
 */
@Composable
fun PaysioPaymentSheet(
    sessionSlug: String,
    apiBase: String = PaysioClient.DEFAULT_API_BASE,
    theme: PaysioTheme = PaysioTheme.Auto,
    customer: PaysioCustomer = PaysioCustomer.EMPTY,
    billingAddress: PaysioAddress = PaysioAddress.EMPTY,
    savedAddresses: List<PaysioSavedAddress> = emptyList(),
    savedMethods: List<PaysioSavedMethod> = emptyList(),
    savedMethodAuthorizer: (suspend (PaysioSavedMethod) -> SavedMethodAuthorization)? = null,
    appId: String? = null,
    secureScreen: Boolean = true,
    onEvent: PaysioEventHandler? = null,
    onSuccess: (PaysioPayResult) -> Unit,
    onError: (PaysioError) -> Unit,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    sheetComponents: PaysioSheetComponents? = null,
    disableAddressSuggestions: Boolean = false,
    /** "Pay $19.99" by default. */
    payLabel: String? = null,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // FLAG_SECURE for the card entry screen (MOBILE_SDK_PLAN.md section 12).
    DisposableEffect(secureScreen, activity) {
        val window = activity?.window
        if (secureScreen) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (secureScreen) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    val model = remember(sessionSlug, apiBase) {
        PaymentSheetModel(
            context = context.applicationContext, activity = activity, slug = sessionSlug, apiBase = apiBase,
            customer = customer, billingAddress = billingAddress, savedAddresses = savedAddresses,
            savedMethods = savedMethods, savedMethodAuthorizer = savedMethodAuthorizer,
            appId = appId ?: context.packageName, onEvent = onEvent,
            onSuccess = onSuccess, onError = onError, onCancel = onCancel,
        )
    }
    LaunchedEffect(model) { model.load() }

    PaysioThemeProvider(theme) {
        val inherited = LocalPaysioSheetComponents.current
        PaysioSheetComponentsProvider(sheetComponents ?: inherited) {
            PaymentSheetBody(model, modifier, if (disableAddressSuggestions) null else apiBase, payLabel)
        }
    }
}

internal class PaymentSheetModel(
    private val context: Context,
    activity: Activity?,
    val slug: String,
    apiBase: String,
    customer: PaysioCustomer,
    billingAddress: PaysioAddress,
    val savedAddresses: List<PaysioSavedAddress>,
    val savedMethods: List<PaysioSavedMethod>,
    val savedMethodAuthorizer: (suspend (PaysioSavedMethod) -> SavedMethodAuthorization)?,
    val appId: String?,
    val onEvent: PaysioEventHandler?,
    val onSuccess: (PaysioPayResult) -> Unit,
    val onError: (PaysioError) -> Unit,
    val onCancel: (() -> Unit)?,
) {
    sealed class Phase {
        object Loading : Phase()
        object Ready : Phase()
        data class Failed(val message: String, val retryable: Boolean = false) : Phase()
    }

    val client = PaysioClient(apiBase)
    private val threeDsHost = activity?.let { WebViewThreeDsHost(it) }

    var phase: Phase by mutableStateOf(Phase.Loading)
    var session: PaysioSession? by mutableStateOf(null)
    var method: PaysioMethodKind by mutableStateOf(PaysioMethodKind.CARD)
    var customer: PaysioCustomer by mutableStateOf(customer)
    var billingAddress: PaysioAddress by mutableStateOf(billingAddress)
    var selectedSaved: PaysioSavedMethod? by mutableStateOf(null)
    var savedCvc: String by mutableStateOf("")
    var achAuthorized: Boolean by mutableStateOf(false)
    val customValues = mutableStateMapOf<String, String>()
    /** Errors returned by the last pay attempt (server or local). */
    var submitErrors: Map<PaysioField, String> by mutableStateOf(emptyMap())
    /** Fields the payer has left at least once: their live validation shows from then on. */
    var visited: Set<PaysioField> by mutableStateOf(emptySet())
    var submitted: Boolean by mutableStateOf(false)
    var errorMessage: String? by mutableStateOf(null)
    var step: PaysioPayStep by mutableStateOf(PaysioPayStep.IDLE)
    var cardController: PaysioCardFieldsController? by mutableStateOf(null)
    var bankController: PaysioBankFieldsController? by mutableStateOf(null)

    val options: List<PaysioMethodKind>
        get() {
            val s = session ?: return emptyList()
            return buildList {
                if (savedMethods.isNotEmpty()) add(PaysioMethodKind.SAVED)
                if (s.cardEnabled) add(PaysioMethodKind.CARD)
                if (s.bankEnabled) add(PaysioMethodKind.BANK)
            }
        }

    val isPaying: Boolean get() = step != PaysioPayStep.IDLE && step != PaysioPayStep.DONE

    private val manualEntry: Boolean get() = method != PaysioMethodKind.SAVED

    private fun liveErrors(s: PaysioSession): Map<PaysioField, String> =
        PaysioValidation.validateContact(customer, s.requirePhone) +
            (if (manualEntry) PaysioValidation.validateBillingAddress(billingAddress) else emptyMap())

    /** What the fields show: live validation for visited fields (all after a submit), plus server errors. */
    val displayErrors: Map<PaysioField, String>
        get() {
            val s = session ?: return submitErrors
            val live = liveErrors(s).filterKeys { submitted || it in visited }
            return submitErrors.filterKeys { it !in live } + live
        }

    private fun customFieldsValid(s: PaysioSession): Boolean =
        s.customFields.none { it.required && (customValues[it.id] ?: "").isBlank() }

    /** RN `canPay`: everything needed for the selected method is present and valid. */
    val canPay: Boolean
        get() {
            val s = session ?: return false
            if (phase != Phase.Ready || step != PaysioPayStep.IDLE) return false
            if (method != PaysioMethodKind.SAVED && s.vault == null) return false
            if (PaysioValidation.validateContact(customer, s.requirePhone).isNotEmpty()) return false
            if (!customFieldsValid(s)) return false
            val addressOk = PaysioValidation.validateBillingAddress(billingAddress).isEmpty()
            return when (method) {
                PaysioMethodKind.CARD -> cardController?.state?.valid == true && addressOk
                PaysioMethodKind.BANK -> bankController?.state?.valid == true && achAuthorized && addressOk
                PaysioMethodKind.SAVED -> {
                    val saved = selectedSaved ?: savedMethods.firstOrNull()
                    saved != null && savedMethodAuthorizer != null &&
                        (!saved.requiresCvc || savedCvc.length in 3..4)
                }
            }
        }

    fun markVisited(field: PaysioField) {
        if (field !in visited) visited = visited + field
    }

    suspend fun load() {
        if (session != null) return
        phase = Phase.Loading
        try {
            val loaded = PaysioSessionLoader.load(slug, client)
            session = loaded
            val vault = loaded.vault
            if (vault != null) {
                cardController = PaysioCardFieldsController(context, vault, loaded.sandbox, onEvent)
                bankController = PaysioBankFieldsController(context, vault, onEvent)
            }
            method = options.firstOrNull() ?: PaysioMethodKind.CARD
            phase = Phase.Ready
            onEvent?.invoke(PaysioEvent.Ready(loaded.sandbox))
        } catch (e: PaysioError) {
            phase = Phase.Failed(e.message, retryable = e.code == PaysioErrorCode.NETWORK)
            onEvent?.invoke(PaysioEvent.Error(e.code))
            onError(e)
        } catch (e: Exception) {
            val wrapped = PaysioError(PaysioErrorCode.UNKNOWN, e.message ?: "Something went wrong.", cause = e)
            phase = Phase.Failed(wrapped.message)
            onError(wrapped)
        }
    }

    fun selectMethod(kind: PaysioMethodKind) {
        method = kind
        errorMessage = null
        onEvent?.invoke(PaysioEvent.MethodChange(kind))
    }

    suspend fun pay() {
        val s = session ?: return
        if (isPaying) return
        errorMessage = null
        submitErrors = emptyMap()
        submitted = true

        val errors = liveErrors(s)
        val missingCustom = s.customFields.firstOrNull { it.required && (customValues[it.id] ?: "").isBlank() }
        if (errors.isNotEmpty() || missingCustom != null) {
            errorMessage = missingCustom?.let { "${it.label} is required." } ?: "Some details are missing or invalid."
            return
        }

        val pay = PaysioPay(client, s, threeDsHost, appId, onEvent)
        try {
            step = PaysioPayStep.ALIASING
            val source: PaysioPaymentSource = when (method) {
                PaysioMethodKind.CARD -> PaysioPaymentSource.Card(
                    (cardController ?: throw PaysioError(PaysioErrorCode.NOT_CONFIGURED, "Card entry is not available.")).tokenize(),
                )
                PaysioMethodKind.BANK -> PaysioPaymentSource.Bank(
                    (bankController ?: throw PaysioError(PaysioErrorCode.NOT_CONFIGURED, "Bank entry is not available.")).tokenize(),
                    authorized = achAuthorized,
                )
                PaysioMethodKind.SAVED -> {
                    val saved = selectedSaved ?: savedMethods.firstOrNull()
                    val authorizer = savedMethodAuthorizer
                    if (saved == null || authorizer == null) {
                        throw PaysioError(PaysioErrorCode.NOT_CONFIGURED, "No saved method authorizer was provided.")
                    }
                    val auth = authorizer(saved)
                    PaysioPaymentSource.Saved(saved.id, auth.portalToken, auth.cvc ?: savedCvc.ifEmpty { null })
                }
            }
            val fieldsById = s.customFields.associateBy { it.id }
            val options = PaysioPayOptions(
                customFieldValues = customValues.filterValues { it.isNotEmpty() }.mapValues { (id, v) ->
                    if (fieldsById[id]?.type == "checkbox") PaysioJsonScalar.Bool(v == "true") else PaysioJsonScalar.Str(v)
                },
            )
            val result = pay.pay(source, customer, billingAddress, options) { step = it }
            step = PaysioPayStep.DONE
            if (result.status == PaysioPayResult.Status.PENDING_SETTLEMENT) {
                onSuccess(result)
            } else {
                // A 200 is always a success per the contract; keep this branch defensive.
                errorMessage = "Something went wrong. Please try again."
                step = PaysioPayStep.IDLE
                onError(PaysioError(PaysioErrorCode.UNKNOWN, errorMessage ?: "", idempotencyKey = result.idempotencyKey))
            }
        } catch (e: PaysioError) {
            step = PaysioPayStep.IDLE
            submitErrors = e.fieldErrors
            errorMessage = e.message
            if (e.code == PaysioErrorCode.SESSION_EXPIRED || e.code == PaysioErrorCode.SESSION_INVALID) {
                phase = Phase.Failed(e.message)
            }
            onError(e)
        } catch (e: Exception) {
            step = PaysioPayStep.IDLE
            errorMessage = e.message ?: "Something went wrong."
            onError(PaysioError(PaysioErrorCode.UNKNOWN, errorMessage ?: "", cause = e))
        }
    }
}

@Composable
private fun PaymentSheetBody(model: PaymentSheetModel, modifier: Modifier, suggestBase: String?, payLabel: String?) {
    val theme = LocalPaysioTheme.current
    val scope = rememberCoroutineScope()

    Column(
        modifier
            .background(theme.colors.background)
            // The viewport shrinks with the keyboard, frame by frame (edge-to-edge hosts).
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (model.session != null) Header(model.session!!)
        when (val phase = model.phase) {
            PaymentSheetModel.Phase.Loading -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = theme.colors.mutedForeground, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
            is PaymentSheetModel.Phase.Failed -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ErrorBanner(phase.message, action = if (phase.retryable) "Try again" else null) {
                    scope.launch { model.load() }
                }
                model.onCancel?.let { cancel ->
                    LinkText("Close") { cancel() }
                }
            }
            PaymentSheetModel.Phase.Ready -> {
                val session = model.session ?: return@Column
                val errors = model.displayErrors
                if (session.vault == null) ErrorBanner("This checkout is not set up for in-app payments yet.")

                ContactFields(
                    model.customer, { model.customer = it }, session.requirePhone, errors,
                    hideName = true,
                    onFieldFocus = { model.onEvent?.invoke(PaysioEvent.FieldFocus(it.wire)) },
                    onFieldBlur = { model.markVisited(it) },
                )

                Column {
                    SectionHeading("Payment method")
                    PaymentMethodPicker(
                        options = model.options,
                        selected = model.method,
                        onSelect = { model.selectMethod(it) },
                        savedMethod = model.selectedSaved ?: model.savedMethods.firstOrNull(),
                    ) { kind -> Panel(model, kind, session, errors, suggestBase) }
                }

                if (session.customFields.isNotEmpty()) CustomFields(model, session.customFields)

                model.errorMessage?.let { ErrorBanner(it) }

                val busy = model.isPaying
                val label = payLabel ?: if (session.totalFormatted.isEmpty()) "Pay" else "Pay ${session.totalFormatted}"
                PayButton(label = label, enabled = model.canPay, busy = busy) { scope.launch { model.pay() } }
            }
        }
    }
}

/** RN PayButton: 48 dp, radius 12, primary, 16 sp medium white; 50 % opacity when disabled. */
@Composable
private fun PayButton(label: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val theme = LocalPaysioTheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val disabled = !enabled || busy
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .alpha(if (!enabled && !busy) 0.5f else if (pressed) 0.9f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.colors.primary)
            .clickable(interactionSource = interaction, indication = null, enabled = !disabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Text(label, style = theme.font(16.sp, FontWeight.Medium, Color.White))
        }
    }
}

@Composable
private fun ErrorBanner(message: String, action: String? = null, onAction: () -> Unit = {}) {
    val theme = LocalPaysioTheme.current
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.colors.destructive.copy(alpha = 0.08f))
            .border(1.dp, theme.colors.destructive.copy(alpha = 0.3f), shape)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(message, style = theme.font(14.sp, color = theme.colors.destructive).copy(lineHeight = 20.sp))
        if (action != null) {
            Text(action, style = theme.font(14.sp, FontWeight.Medium, theme.colors.destructive),
                modifier = Modifier.clickable(onClick = onAction).semantics { role = Role.Button })
        }
    }
}

@Composable
private fun Header(session: PaysioSession) {
    val theme = LocalPaysioTheme.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(session.brand?.name?.ifEmpty { null } ?: session.workspaceName,
                style = theme.font(14.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (session.totalFormatted.isNotEmpty()) {
                Text(session.totalFormatted, style = theme.font(24.sp, FontWeight.SemiBold), modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (session.sandbox) {
            Text(
                "Test mode",
                style = theme.font(11.sp, FontWeight.Medium, theme.colors.mutedForeground),
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(theme.colors.secondary).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun BillingBlock(model: PaymentSheetModel, errors: Map<PaysioField, String>, suggestBase: String?) {
    PaysioBillingAddress(
        customer = model.customer, onCustomerChange = { model.customer = it },
        address = model.billingAddress, onAddressChange = { model.billingAddress = it },
        savedAddresses = model.savedAddresses, errors = errors,
        apiBase = suggestBase,
        onFieldFocus = { model.onEvent?.invoke(PaysioEvent.FieldFocus(it.wire)) },
        onFieldBlur = { model.markVisited(it) },
    )
}

@Composable
private fun Panel(model: PaymentSheetModel, kind: PaysioMethodKind, session: PaysioSession, errors: Map<PaysioField, String>, suggestBase: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        when (kind) {
            PaysioMethodKind.CARD -> {
                model.cardController?.let { PaysioCardFields(it) }
                BillingBlock(model, errors, suggestBase)
            }
            PaysioMethodKind.BANK -> {
                model.bankController?.let { PaysioBankFields(it) }
                BillingBlock(model, errors, suggestBase)
                ConsentRow(
                    checked = model.achAuthorized,
                    onCheckedChange = { model.achAuthorized = it },
                    text = session.achAuthorizationText,
                    contentDescription = "Authorize the bank debit",
                )
            }
            PaysioMethodKind.SAVED -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SavedMethodSelect(model.savedMethods, model.selectedSaved, onSelect = { model.selectedSaved = it })
                if ((model.selectedSaved ?: model.savedMethods.firstOrNull())?.requiresCvc == true) {
                    StackedTextField(
                        value = model.savedCvc, onValueChange = { model.savedCvc = it.filter { c -> c.isDigit() }.take(4) },
                        placeholder = "CVC", contentDescription = "Security code", corners = PaysioCorners.ALL,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
        }
    }
}

/** The 18 dp checkbox (RN CustomFields): radius 5, 1 dp border, primary fill + white check when on. */
@Composable
private fun CheckBox(checked: Boolean, modifier: Modifier = Modifier) {
    val theme = LocalPaysioTheme.current
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier
            .size(18.dp)
            .clip(shape)
            .background(if (checked) theme.colors.primary else Color.Transparent)
            .border(1.dp, if (checked) theme.colors.primary else theme.colors.input, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) PaysioIcon(PaysioIconName.CHECK, 14.dp, Color.White, strokeWidth = 2f)
    }
}

/**
 * A standalone consent checkbox row (RN ConsentRow, the ACH authorization): the box
 * top-aligned with the text, gap 10, text 12 sp / 16 in mutedForeground.
 */
@Composable
fun ConsentRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, text: String, contentDescription: String? = null, modifier: Modifier = Modifier) {
    val theme = LocalPaysioTheme.current
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .semantics { this.contentDescription = contentDescription ?: text },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CheckBox(checked, Modifier.padding(top = 1.dp))
        Text(text, style = theme.font(12.sp, color = theme.colors.mutedForeground).copy(lineHeight = 16.sp), modifier = Modifier.weight(1f))
    }
}

/** RN PaysioCustomFields: label over each field; selects open a bottom sheet; checkboxes last. */
@Composable
private fun CustomFields(model: PaymentSheetModel, fields: List<PaysioCustomField>) {
    val theme = LocalPaysioTheme.current
    var openSelect by remember { mutableStateOf<String?>(null) }
    val label = { f: PaysioCustomField -> if (f.required) f.label else "${f.label} (optional)" }
    Column {
        SectionHeading("Additional information")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            fields.filter { it.type != "checkbox" }.forEach { f ->
                val value = model.customValues[f.id] ?: ""
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label(f), style = theme.font(12.sp, color = theme.colors.mutedForeground))
                    if (f.type == "select") {
                        StackedTrigger(
                            value = value,
                            placeholder = f.placeholder ?: "Select",
                            contentDescription = f.label,
                            onClick = { openSelect = f.id },
                            corners = PaysioCorners.ALL,
                            trailing = { TriggerChevron() },
                        )
                        PaysioBottomSheet(open = openSelect == f.id, onClose = { openSelect = null }, title = f.label) {
                            f.options.forEach { o ->
                                PaysioSheetRow(onClick = { openSelect = null; model.customValues[f.id] = o }, selected = o == value, label = o)
                            }
                        }
                    } else {
                        StackedTextField(
                            value = value,
                            onValueChange = { t -> model.customValues[f.id] = if (f.type == "number") t.filter { it.isDigit() || it in ".,-" } else t },
                            placeholder = f.placeholder ?: f.label,
                            contentDescription = f.label,
                            corners = PaysioCorners.ALL,
                            keyboardType = if (f.type == "number") KeyboardType.Decimal else KeyboardType.Text,
                        )
                    }
                }
            }
            fields.filter { it.type == "checkbox" }.forEach { f ->
                val checked = model.customValues[f.id] == "true"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = checked,
                            interactionSource = remember(f.id) { MutableInteractionSource() },
                            indication = null,
                            role = Role.Checkbox,
                            onValueChange = { model.customValues[f.id] = if (it) "true" else "" },
                        )
                        .semantics { contentDescription = f.label },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckBox(checked)
                    Text(label(f), style = theme.font(14.sp), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Unwraps `ContextWrapper`s until an Activity is found (Compose's `LocalContext` is usually one). */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
