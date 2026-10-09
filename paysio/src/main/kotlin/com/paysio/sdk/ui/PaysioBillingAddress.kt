package com.paysio.sdk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.paysio.sdk.core.PaysioAddress
import com.paysio.sdk.core.PaysioAddressSuggestion
import com.paysio.sdk.core.PaysioCustomer
import com.paysio.sdk.core.PaysioField
import com.paysio.sdk.core.PaysioSavedAddress
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One line for a saved address (RN formatSavedAddress). */
fun formatSavedAddress(a: PaysioAddress): String {
    val line = listOfNotNull(a.address1, a.address2).filter { it.isNotBlank() }.joinToString(", ")
    val cityState = listOf(a.city, a.state).filter { it.isNotBlank() }.joinToString(" ")
    val second = listOf(cityState, a.postalCode).filter { it.isNotEmpty() }.joinToString(" ")
    return listOf(line, second, a.country).filter { it.isNotBlank() }.joinToString(", ")
}

private val RevealEasing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f) // Easing.out(Easing.cubic)

/**
 * Billing address, ported from the RN SDK's BillingAddress.tsx (web BillingAddressFields
 * + AddressFields variant="stacked").
 *
 * Saved addresses: a select trigger (radius 12, `secondary`, padding 12 / 10, name 14 sp
 * medium over the address 12 sp muted, chevron 16) opening a bottom sheet whose list
 * ends with plus + "Enter a new address" in primary.
 *
 * New address: First (user icon) | Last, radius 8 top; Country (flag + name on one line,
 * one chevron) full width, opening a searchable bottom sheet; Address, radius 8 bottom
 * until the next row appears; then City | State (US: a bottom-sheet picker of USPS
 * codes) | ZIP revealed over 300 ms, radius 8 bottom. All boxes 36 dp, 1 dp overlaps.
 *
 * Street suggestions come from `GET {apiBase}/api/checkout/address-suggest` and
 * `address-details` (Places through Paysio, no Google key in the app). With [apiBase]
 * null or [disableSuggestions] true the street line is a plain text field.
 */
@Composable
fun PaysioBillingAddress(
    customer: PaysioCustomer,
    onCustomerChange: (PaysioCustomer) -> Unit,
    address: PaysioAddress,
    onAddressChange: (PaysioAddress) -> Unit,
    savedAddresses: List<PaysioSavedAddress> = emptyList(),
    errors: Map<PaysioField, String> = emptyMap(),
    showHeading: Boolean = true,
    modifier: Modifier = Modifier,
    /** Paysio API base for street suggestions (the sheet passes its own). Null = no suggestions. */
    apiBase: String? = null,
    /** Turn street suggestions off even when an API base is known. */
    disableSuggestions: Boolean = false,
    heading: String = "Billing address",
    /** Hide the name row (when the contact block already collected it). */
    hideName: Boolean = false,
    /** Keep city / state / ZIP visible even when empty. */
    alwaysExpanded: Boolean = false,
    /** Restrict the country list (AptPay rails are US-only). */
    allowedCountries: List<String>? = null,
    onFieldFocus: ((PaysioField) -> Unit)? = null,
    /** Called when a field loses focus or a picker closes (the sheet shows its error from then on). */
    onFieldBlur: ((PaysioField) -> Unit)? = null,
) {
    var selectedSavedId by remember { mutableStateOf<String?>(null) }
    var enteringNew by remember { mutableStateOf(false) }
    val usingSaved = savedAddresses.isNotEmpty() && !enteringNew
    val selectedSaved = savedAddresses.firstOrNull { it.id == selectedSavedId } ?: savedAddresses.firstOrNull()

    LaunchedEffect(usingSaved, selectedSaved?.id) {
        if (usingSaved && selectedSaved != null && selectedSaved.address != address) onAddressChange(selectedSaved.address)
    }

    Column(modifier) {
        if (showHeading) SectionHeading(heading)
        if (usingSaved && selectedSaved != null) {
            SavedAddressPicker(savedAddresses, selectedSaved, heading,
                onSelect = { selectedSavedId = it.id; onAddressChange(it.address) },
                onEnterNew = {
                    enteringNew = true
                    onAddressChange(PaysioAddress(country = address.country.ifEmpty { "US" }))
                },
            )
        } else {
            AddressStack(
                customer, onCustomerChange, address, onAddressChange, errors,
                suggestBase = if (disableSuggestions) null else apiBase,
                hideName = hideName, alwaysExpanded = alwaysExpanded,
                allowedCountries = allowedCountries, onFieldFocus = onFieldFocus, onFieldBlur = onFieldBlur,
            )
            if (savedAddresses.isNotEmpty()) {
                LinkText("Use existing address", Modifier.padding(top = 8.dp)) { enteringNew = false }
            }
        }
    }
}

@Composable
private fun SavedAddressPicker(
    saved: List<PaysioSavedAddress>,
    selected: PaysioSavedAddress,
    title: String,
    onSelect: (PaysioSavedAddress) -> Unit,
    onEnterNew: () -> Unit,
) {
    val theme = LocalPaysioTheme.current
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(12.dp)
    val name = selected.name.ifEmpty { "No name" }
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(1.dp, shape, clip = false, ambientColor = Color(0x0F1A1A1A), spotColor = Color(0x0F1A1A1A))
            .clip(shape)
            .background(if (pressed) theme.colors.rowHover else theme.colors.secondary)
            // Inset highlight: 1 dp white 18 % on top, 1 dp black 8 % at the bottom, no sides.
            .drawWithContent {
                drawContent()
                val px = 1.dp.toPx()
                drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, px / 2), Offset(size.width, px / 2), px)
                drawLine(Color.Black.copy(alpha = 0.08f), Offset(0f, size.height - px / 2), Offset(size.width, size.height - px / 2), px)
            }
            .clickable(interactionSource = interaction, indication = null) { open = true }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Billing address: $name, ${formatSavedAddress(selected.address)}"
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = theme.font(14.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatSavedAddress(selected.address), style = theme.font(12.sp, color = theme.colors.mutedForeground), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TriggerChevron()
    }

    PaysioBottomSheet(open = open, onClose = { open = false }, title = title) {
        saved.forEach { addr ->
            PaysioSheetRow(onClick = { open = false; onSelect(addr) }, selected = addr.id == selected.id) {
                TwoLineRowContent(addr.name.ifEmpty { "No name" }, formatSavedAddress(addr.address))
            }
        }
        // The one row that is an action, not an address: a glyph and the weight of the names above it.
        PaysioSheetRow(
            onClick = { open = false; onEnterNew() },
            label = "Enter a new address",
            accent = true,
            leading = { PaysioIcon(PaysioIconName.PLUS, 16.dp, theme.colors.primary) },
        )
    }
}

@Composable
private fun AddressStack(
    customer: PaysioCustomer,
    onCustomerChange: (PaysioCustomer) -> Unit,
    a: PaysioAddress,
    onAddressChange: (PaysioAddress) -> Unit,
    errors: Map<PaysioField, String>,
    suggestBase: String?,
    hideName: Boolean,
    alwaysExpanded: Boolean,
    allowedCountries: List<String>?,
    onFieldFocus: ((PaysioField) -> Unit)?,
    onFieldBlur: ((PaysioField) -> Unit)?,
) {
    val theme = LocalPaysioTheme.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val current by rememberUpdatedState(a)
    fun set(transform: (PaysioAddress) -> PaysioAddress) = onAddressChange(transform(current))

    var sheet by remember { mutableStateOf<String?>(null) } // "country" | "state"
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
    val iconTint = { f: Boolean -> if (f) theme.colors.foreground.copy(alpha = 0.8f) else theme.colors.mutedForeground.copy(alpha = 0.7f) }

    val country = a.country.trim().uppercase()
    val isUs = country == "US"
    val expanded = alwaysExpanded || a.address1.isNotEmpty() || a.city.isNotEmpty() || a.state.isNotEmpty() || a.postalCode.isNotEmpty()
    val countries = if (!allowedCountries.isNullOrEmpty()) {
        val allowed = allowedCountries.map { it.uppercase() }
        PaysioCountries.list.filter { it.code in allowed }
    } else PaysioCountries.list

    // US: fold a full name or lower-case code onto the USPS code; a value that is no US
    // state at all (left over from switching the country) is cleared rather than
    // submitted invisibly behind an empty picker.
    val normalizedUsState = if (isUs) PaysioCountries.toUsStateCode(a.state) else null
    LaunchedEffect(isUs, normalizedUsState, a.state) {
        if (!isUs || a.state.isEmpty()) return@LaunchedEffect
        val next = normalizedUsState ?: ""
        if (next != a.state) set { it.copy(state = next) }
    }

    val last = remember { FocusRequester() }
    val street = remember { FocusRequester() }
    val city = remember { FocusRequester() }
    val stateField = remember { FocusRequester() }
    val zip = remember { FocusRequester() }

    // Street suggestions: shown while the street field has focus and the shopper has
    // typed since their last pick. Losing focus hides the list after 200 ms, so a tap on
    // a row lands before it goes away.
    var streetActive by remember { mutableStateOf(false) }
    var typedSincePick by remember { mutableStateOf(false) }
    var blurJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { blurJob?.cancel() } }
    val suggestions = rememberPaysioAddressSuggestions(
        apiBase = suggestBase,
        query = a.address1,
        country = country,
        enabled = suggestBase != null && streetActive && typedSincePick,
    )
    val listOpen = streetActive && typedSincePick && suggestions.suggestions.isNotEmpty()

    fun pick(s: PaysioAddressSuggestion) {
        typedSincePick = false
        scope.launch {
            val d = suggestions.resolve(s)
            val picked = d.countryCode.uppercase()
            val allowed = picked.isNotEmpty() && countries.any { it.code == picked }
            val nextCountry = if (allowed) picked else country
            val st = if (nextCountry == "US") d.stateCode.ifEmpty { d.state } else d.state.ifEmpty { d.stateCode }
            set {
                it.copy(
                    address1 = d.address1.ifEmpty { s.address1.ifEmpty { it.address1 } },
                    city = d.city.ifEmpty { it.city },
                    state = st.ifEmpty { it.state },
                    postalCode = d.postalCode.ifEmpty { it.postalCode },
                    country = if (allowed) picked else it.country,
                )
            }
            if (d.postalCode.isEmpty()) {
                delay(250)
                runCatching { zip.requestFocus() }
            } else {
                focusManager.clearFocus()
            }
        }
    }

    fun focusLater(requester: FocusRequester) {
        scope.launch {
            delay(250)
            runCatching { requester.requestFocus() }
        }
    }

    val hasName = !hideName
    Column(verticalArrangement = Arrangement.spacedBy((-1).dp)) {
        if (hasName) {
            val nameFocused = focus == PaysioField.FIRST_NAME || focus == PaysioField.LAST_NAME
            val nameInvalid = errors.containsKey(PaysioField.FIRST_NAME) || errors.containsKey(PaysioField.LAST_NAME)
            Row(Modifier.zIndex(stackZ(nameFocused, nameInvalid)), horizontalArrangement = Arrangement.spacedBy((-1).dp)) {
                StackedTextField(
                    value = customer.firstName, onValueChange = { onCustomerChange(customer.copy(firstName = it)) },
                    placeholder = "First Name", contentDescription = "First name", corners = PaysioCorners.TOP_LEFT,
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next, onImeAction = { runCatching { last.requestFocus() } },
                    leading = { f -> PaysioIcon(PaysioIconName.USER_SOLID, 16.dp, iconTint(f)) },
                    invalid = errors.containsKey(PaysioField.FIRST_NAME),
                    onFocusChange = { focusOn(PaysioField.FIRST_NAME, it) },
                    modifier = Modifier.weight(1f),
                )
                StackedTextField(
                    value = customer.lastName, onValueChange = { onCustomerChange(customer.copy(lastName = it)) },
                    placeholder = "Last Name", contentDescription = "Last name", corners = PaysioCorners.TOP_RIGHT,
                    capitalization = KeyboardCapitalization.Words, focusRequester = last,
                    imeAction = ImeAction.Next, onImeAction = { focusManager.clearFocus(); sheet = "country" },
                    invalid = errors.containsKey(PaysioField.LAST_NAME),
                    onFocusChange = { focusOn(PaysioField.LAST_NAME, it) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        StackedTrigger(
            value = if (country.isNotEmpty()) PaysioCountries.countryName(country) else "",
            placeholder = "Country",
            contentDescription = "Country",
            onClick = { sheet = "country" },
            corners = if (hasName) PaysioCorners.NONE else PaysioCorners.TOP,
            invalid = errors.containsKey(PaysioField.COUNTRY),
            leading = {
                if (country.isNotEmpty()) {
                    Text(PaysioCountries.flagEmoji(country), style = theme.font(14.sp).copy(lineHeight = 18.sp))
                } else {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(theme.colors.mutedForeground.copy(alpha = 0.35f)))
                }
            },
            trailing = { TriggerChevron() },
            modifier = Modifier.zIndex(30f),
        )

        // Carries the street field's stacking order and keeps field + list above the keyboard.
        val streetFocused = focus == PaysioField.ADDRESS1
        Column(
            Modifier
                .zIndex(stackZ(streetFocused, errors.containsKey(PaysioField.ADDRESS1)))
                .paysioKeyboardReveal(streetFocused || listOpen),
        ) {
            StackedTextField(
                value = a.address1,
                onValueChange = { v -> typedSincePick = true; set { it.copy(address1 = v) } },
                placeholder = "Address", contentDescription = "Street address",
                corners = if (expanded) PaysioCorners.NONE else PaysioCorners.BOTTOM,
                capitalization = KeyboardCapitalization.Words, focusRequester = street,
                imeAction = ImeAction.Next, onImeAction = { runCatching { city.requestFocus() } },
                invalid = errors.containsKey(PaysioField.ADDRESS1),
                leading = if (suggestBase != null) { f -> PaysioIcon(PaysioIconName.MAP_PIN_SOLID, 16.dp, iconTint(f)) } else null,
                onFocusChange = { f ->
                    focusOn(PaysioField.ADDRESS1, f)
                    blurJob?.cancel()
                    if (f) streetActive = true
                    else blurJob = scope.launch { delay(200); streetActive = false }
                },
            )
            if (streetActive && typedSincePick) {
                PaysioAddressSuggestionList(suggestions.suggestions, a.address1, onPick = { pick(it) })
            }
        }

        // City / State / ZIP stay hidden until there's an address to qualify (300 ms ease-out).
        val rowFocused = focus == PaysioField.CITY || focus == PaysioField.STATE || focus == PaysioField.POSTAL_CODE
        val rowInvalid = errors.containsKey(PaysioField.CITY) || errors.containsKey(PaysioField.STATE) || errors.containsKey(PaysioField.POSTAL_CODE)
        AnimatedVisibility(
            visible = expanded,
            modifier = Modifier.zIndex(stackZ(rowFocused, rowInvalid)),
            enter = expandVertically(tween(300, easing = RevealEasing)) + fadeIn(tween(300, easing = RevealEasing)),
            exit = shrinkVertically(tween(300, easing = RevealEasing)) + fadeOut(tween(300, easing = RevealEasing)),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy((-1).dp)) {
                StackedTextField(
                    value = a.city, onValueChange = { v -> set { it.copy(city = v) } },
                    placeholder = "City", corners = PaysioCorners.BOTTOM_LEFT,
                    capitalization = KeyboardCapitalization.Words, focusRequester = city,
                    imeAction = ImeAction.Next,
                    onImeAction = { if (isUs) { focusManager.clearFocus(); sheet = "state" } else runCatching { stateField.requestFocus() } },
                    invalid = errors.containsKey(PaysioField.CITY),
                    onFocusChange = { focusOn(PaysioField.CITY, it) },
                    modifier = Modifier.weight(1f),
                )
                if (isUs) {
                    StackedTrigger(
                        value = normalizedUsState ?: "",
                        placeholder = "State",
                        contentDescription = "State",
                        onClick = { sheet = "state" },
                        corners = PaysioCorners.NONE,
                        trailing = { TriggerChevron() },
                        invalid = errors.containsKey(PaysioField.STATE),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    StackedTextField(
                        value = a.state, onValueChange = { v -> set { it.copy(state = v) } },
                        placeholder = "State", contentDescription = "State or region", corners = PaysioCorners.NONE,
                        capitalization = KeyboardCapitalization.Words, focusRequester = stateField,
                        imeAction = ImeAction.Next, onImeAction = { runCatching { zip.requestFocus() } },
                        invalid = errors.containsKey(PaysioField.STATE),
                        onFocusChange = { focusOn(PaysioField.STATE, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
                StackedTextField(
                    value = a.postalCode, onValueChange = { v -> set { it.copy(postalCode = v) } },
                    placeholder = if (isUs) "ZIP" else "Postal code",
                    contentDescription = if (isUs) "ZIP code" else "Postal code",
                    corners = PaysioCorners.BOTTOM_RIGHT,
                    keyboardType = if (isUs) KeyboardType.Number else KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Characters, focusRequester = zip,
                    imeAction = ImeAction.Done, onImeAction = { focusManager.clearFocus() },
                    invalid = errors.containsKey(PaysioField.POSTAL_CODE),
                    onFocusChange = { focusOn(PaysioField.POSTAL_CODE, it) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    val order = listOf(PaysioField.FIRST_NAME, PaysioField.LAST_NAME, PaysioField.COUNTRY, PaysioField.ADDRESS1,
        PaysioField.CITY, PaysioField.STATE, PaysioField.POSTAL_CODE)
    FieldErrorText(order.firstNotNullOfOrNull { errors[it] })

    PaysioBottomSheet(
        open = sheet == "country", onClose = { sheet = null; onFieldBlur?.invoke(PaysioField.COUNTRY) },
        title = "Country", searchable = true, searchPlaceholder = "Search country",
    ) { query ->
        val q = query.trim().lowercase()
        val list = if (q.isEmpty()) countries else countries.filter { it.name.lowercase().contains(q) || it.code.lowercase().contains(q) }
        if (list.isEmpty()) {
            PaysioSheetEmpty("No countries found.")
        } else {
            list.forEach { c ->
                PaysioSheetRow(
                    onClick = {
                        sheet = null
                        set { if (c.code != country) it.copy(country = c.code, state = "") else it.copy(country = c.code) }
                        focusLater(street)
                    },
                    selected = c.code == country,
                    label = c.name,
                    detail = c.code,
                    leading = { Text(c.flag, style = theme.font(16.sp)) },
                )
            }
        }
    }

    PaysioBottomSheet(
        open = sheet == "state", onClose = { sheet = null; onFieldBlur?.invoke(PaysioField.STATE) },
        title = "State", searchable = true, searchPlaceholder = "Search state",
    ) { query ->
        val q = query.trim().lowercase()
        val list = if (q.isEmpty()) PaysioCountries.usStates
        else PaysioCountries.usStates.filter { it.name.lowercase().contains(q) || it.code.lowercase().contains(q) }
        if (list.isEmpty()) {
            PaysioSheetEmpty("No states found.")
        } else {
            list.forEach { s ->
                PaysioSheetRow(
                    onClick = {
                        sheet = null
                        set { it.copy(state = s.code) }
                        focusLater(zip)
                    },
                    selected = s.code == normalizedUsState,
                    label = s.name,
                    detail = s.code,
                )
            }
        }
    }
}
