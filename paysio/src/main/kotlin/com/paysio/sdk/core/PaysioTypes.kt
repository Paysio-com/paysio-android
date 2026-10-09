package com.paysio.sdk.core

// Public value types. These mirror `sdks/react-native/src/types.ts` one for one.

// ── Session ──────────────────────────────────────────────────────────────────

enum class PaysioEnvironment(val wire: String) { SANDBOX("sandbox"), LIVE("live") }

/** VGS Collect target returned by `GET /api/checkout/:slug` as `vaultConfig`. */
data class PaysioVaultConfig(val vaultId: String, val environment: PaysioEnvironment)

/** A custom field the merchant added to the payment link. */
data class PaysioCustomField(
    val id: String,
    val label: String,
    /** `text`, `textarea`, `select`, `checkbox`, `number` or any future type. */
    val type: String,
    val required: Boolean = false,
    val options: List<String> = emptyList(),
    val placeholder: String? = null,
)

/** One line of the session; sent back to the server as `items`. */
data class PaysioSessionItem(
    val productId: String,
    val quantity: Int,
    val variantId: String? = null,
    val name: String? = null,
    /** Unit amount in minor units when the server provides it. */
    val unitAmount: Long? = null,
)

/** White-label brand of the selling workspace (null means Paysio). */
data class PaysioBrand(val name: String, val logoUrl: String? = null, val accent: String? = null)

// ── Customer and addresses ───────────────────────────────────────────────────

data class PaysioAddress(
    val address1: String = "",
    val address2: String? = null,
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val country: String = "US",
) {
    companion object { val EMPTY = PaysioAddress() }
}

data class PaysioCustomer(
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val phone: String? = null,
) {
    companion object { val EMPTY = PaysioCustomer() }
}

data class PaysioBillingDetails(val customer: PaysioCustomer, val address: PaysioAddress)

data class PaysioSavedAddress(val id: String, val name: String, val address: PaysioAddress)

// ── Payment methods ──────────────────────────────────────────────────────────

enum class PaysioMethodKind(val wire: String) { CARD("card"), BANK("bank"), SAVED("saved") }

/** A previously saved payment method (phase 2: quick checkout). */
data class PaysioSavedMethod(
    val id: String,
    val brand: String? = null,
    val bin: String? = null,
    val bankName: String? = null,
    val isBank: Boolean = false,
    val last4: String,
    /** Right-aligned detail: an expiry ("12/32") or an account type ("Checking"). */
    val meta: String? = null,
    val badge: String? = null,
    val disabled: Boolean = false,
    /** Whether a CVC must be collected before charging this method. */
    val requiresCvc: Boolean = false,
)

enum class PaysioBankAccountType(val wire: String, val label: String) {
    CHECKING("checking", "Checking"), SAVINGS("savings", "Savings")
}

enum class PaysioBankHolderType(val wire: String, val label: String) {
    PERSONAL("personal", "Personal"), BUSINESS("business", "Business")
}

// ── Tokenized results (what leaves the fields: aliases only) ─────────────────

/** What the card fields produce. Never contains the PAN or the CVC. */
data class PaysioCardAlias(
    val panAlias: String,
    val cvcAlias: String,
    /** "MM" */
    val expMonth: String,
    /** "YYYY" */
    val expYear: String,
    val bin: String?,
    val brand: String?,
    val last4: String?,
)

/** What the bank fields produce. The account number is an alias. */
data class PaysioBankAlias(
    val accountAlias: String,
    val last4: String?,
    val routingNumber: String,
    val accountType: PaysioBankAccountType,
    val holderType: PaysioBankHolderType,
)

// ── Field state ──────────────────────────────────────────────────────────────

data class PaysioFieldState(
    val valid: Boolean = false,
    val empty: Boolean = true,
    val focused: Boolean = false,
    val dirty: Boolean = false,
    /** First validation message, once the field has been touched. */
    val error: String? = null,
) {
    /** Red border rule from the web: only after blur, never while focused. */
    val showsInvalid: Boolean get() = dirty && !focused && !valid
}

data class PaysioCardFieldsState(
    val number: PaysioFieldState = PaysioFieldState(),
    val expiry: PaysioFieldState = PaysioFieldState(),
    val cvc: PaysioFieldState = PaysioFieldState(),
    val brand: String? = null,
    val bin: String? = null,
    val last4: String? = null,
) {
    val valid: Boolean get() = number.valid && expiry.valid && cvc.valid
}

data class PaysioBankFieldsState(
    val routing: PaysioFieldState = PaysioFieldState(),
    val account: PaysioFieldState = PaysioFieldState(),
    val accountType: PaysioBankAccountType = PaysioBankAccountType.CHECKING,
    val holderType: PaysioBankHolderType = PaysioBankHolderType.PERSONAL,
) {
    val valid: Boolean get() = routing.valid && account.valid
}

// ── Pay flow ─────────────────────────────────────────────────────────────────

enum class PaysioPayStep(val wire: String) {
    IDLE("idle"), VALIDATING("validating"), ALIASING("aliasing"), THREE_DS("three_ds"),
    CHARGING("charging"), DONE("done"),
}

/** A JSON scalar for custom fields and merchant metadata. */
sealed class PaysioJsonScalar {
    data class Str(val value: String) : PaysioJsonScalar()
    data class Num(val value: Double) : PaysioJsonScalar()
    data class Bool(val value: Boolean) : PaysioJsonScalar()
}

/** Options for one `pay()` call. */
data class PaysioPayOptions(
    /** Overrides the items from the session. */
    val items: List<PaysioSessionItem>? = null,
    val discountCode: String? = null,
    val customFieldValues: Map<String, PaysioJsonScalar> = emptyMap(),
    val shippingAddress: PaysioAddress? = null,
    /** Merchant metadata merged into `clientMetadata.extra` (never card data). */
    val metadata: Map<String, PaysioJsonScalar> = emptyMap(),
    /**
     * Amount in minor units for 3-D Secure when the session has no fixed total
     * (pay what you want). Ignored when the session carries a total.
     */
    val amountMinor: Long? = null,
)

data class PaysioPayResult(
    val status: Status,
    val transactionId: String,
    val orderNumber: String? = null,
    val orderLabel: String? = null,
    val confirmationToken: String? = null,
    val successUrl: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    val cardBrand: String? = null,
    val cardLast4: String? = null,
    /** Instant ACH authorization URL when the bank rail needs it. */
    val bankAuthUrl: String? = null,
    val idempotencyKey: String,
) {
    enum class Status(val wire: String) {
        PENDING_SETTLEMENT("pending_settlement"), DECLINED("declined"), ERROR("error");

        companion object {
            fun fromWire(value: String?): Status = entries.firstOrNull { it.wire == value } ?: ERROR
        }
    }
}

// ── Events (analytics hook; never contains card data) ────────────────────────

sealed class PaysioEvent {
    data class Ready(val sandbox: Boolean) : PaysioEvent()
    data class FieldFocus(val field: String) : PaysioEvent()
    data class MethodChange(val method: PaysioMethodKind) : PaysioEvent()
    data class PayStart(val method: PaysioMethodKind) : PaysioEvent()
    object ThreeDsStart : PaysioEvent()
    object ThreeDsChallenge : PaysioEvent()
    data class ThreeDsResult(val approved: Boolean) : PaysioEvent()
    data class PayResult(val status: PaysioPayResult.Status) : PaysioEvent()
    data class Error(val code: PaysioErrorCode) : PaysioEvent()
}

typealias PaysioEventHandler = (PaysioEvent) -> Unit
