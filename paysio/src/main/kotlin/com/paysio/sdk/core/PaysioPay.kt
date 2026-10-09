package com.paysio.sdk.core

import android.content.res.Resources
import android.os.Build
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** What is being charged. */
sealed class PaysioPaymentSource {
    /** A fresh card tokenized by [PaysioVault.tokenizeCard]. */
    data class Card(val alias: PaysioCardAlias) : PaysioPaymentSource()

    /**
     * A fresh US bank account. [authorized] is the payer's tick on the ACH consent
     * (`session.achAuthorizationText`); `pay()` refuses the debit without it and only
     * then sends `achAuthorized: true` (CONTRACT.md "ACH consent text").
     */
    data class Bank(val alias: PaysioBankAlias, val authorized: Boolean) : PaysioPaymentSource()

    /** A saved method (phase 2). [cvc] only when the method `requiresCvc`. */
    data class Saved(val billingId: String, val portalToken: String, val cvc: String? = null) : PaysioPaymentSource()

    val kind: PaysioMethodKind
        get() = when (this) {
            is Card -> PaysioMethodKind.CARD
            is Bank -> PaysioMethodKind.BANK
            is Saved -> PaysioMethodKind.SAVED
        }
}

/**
 * `pay()` orchestration (CONTRACT.md sections 3 and 4): validate -> (3DS for cards)
 * -> charge with one `Idempotency-Key`, retrying once on a transport error only.
 */
class PaysioPay(
    val client: PaysioClient,
    val session: PaysioSession,
    /** Required for cards when `session.threeDsEnabled`. */
    val threeDsHost: ThreeDsWebHost? = null,
    /** Package name reported in `clientMetadata.appId`. */
    val appId: String? = null,
    val onEvent: PaysioEventHandler? = null,
) {
    /**
     * Charges the session. Throws [PaysioError]; returns the server's result, including
     * `declined` and `error` statuses (those are results, not throws).
     */
    suspend fun pay(
        source: PaysioPaymentSource,
        customer: PaysioCustomer,
        billingAddress: PaysioAddress,
        options: PaysioPayOptions = PaysioPayOptions(),
        onStep: ((PaysioPayStep) -> Unit)? = null,
    ): PaysioPayResult {
        onEvent?.invoke(PaysioEvent.PayStart(source.kind))
        onStep?.invoke(PaysioPayStep.VALIDATING)

        // 1. Local validation mirrors the server rules.
        val contactErrors = PaysioValidation.validateContact(customer, session.requirePhone)
        // The billing address is required for manually entered cards and banks only (as in RN).
        val addressErrors = if (source is PaysioPaymentSource.Saved) emptyMap() else PaysioValidation.validateBillingAddress(billingAddress)
        if (contactErrors.isNotEmpty() || addressErrors.isNotEmpty()) {
            val code = when {
                addressErrors.isEmpty() -> PaysioErrorCode.CONTACT_REQUIRED
                contactErrors.isEmpty() -> PaysioErrorCode.BILLING_ADDRESS_REQUIRED
                else -> PaysioErrorCode.INVALID_FIELDS
            }
            throw fail(PaysioError(code, "Some details are missing or invalid.", fieldErrors = contactErrors + addressErrors))
        }
        if (source is PaysioPaymentSource.Bank && !source.authorized) {
            throw fail(PaysioError(PaysioErrorCode.INVALID_FIELDS, "Please accept the bank account authorization to continue."))
        }
        if (source !is PaysioPaymentSource.Saved && session.vault == null) {
            throw fail(PaysioError(PaysioErrorCode.NOT_CONFIGURED, "This checkout is not set up for in-app payments."))
        }

        // 2. 3-D Secure for fresh cards.
        var threeDsToken: String? = null
        if (source is PaysioPaymentSource.Card && session.threeDsEnabled) {
            val host = threeDsHost
                ?: throw fail(PaysioError(PaysioErrorCode.NOT_CONFIGURED, "3-D Secure is enabled but no ThreeDsWebHost was provided."))
            val amountMinor = session.totalMinor ?: options.amountMinor
                ?: throw fail(PaysioError(PaysioErrorCode.SESSION_INVALID,
                    "The session has no total, so 3-D Secure cannot run. Pass PaysioPayOptions.amountMinor."))
            onStep?.invoke(PaysioPayStep.THREE_DS)
            try {
                threeDsToken = PaysioThreeDS(client, session, host, onEvent)
                    .authenticate(source.alias, amountMinor / 100.0, customer.email, billingAddress.country)
            } catch (e: PaysioError) {
                throw fail(e)
            }
        }

        // 3. Charge, one idempotency key per attempt.
        onStep?.invoke(PaysioPayStep.CHARGING)
        val idempotencyKey = UUID.randomUUID().toString()
        val body = buildBody(source, customer, billingAddress, options, threeDsToken)
        val response = postWithOneRetry(body, idempotencyKey)
        if (!response.isSuccess) throw fail(PaysioClient.mapFailure(response, idempotencyKey))

        val result = parseResult(response.json, idempotencyKey)
        onStep?.invoke(PaysioPayStep.DONE)
        onEvent?.invoke(PaysioEvent.PayResult(result.status))
        return result
    }

    private fun fail(error: PaysioError): PaysioError {
        onEvent?.invoke(PaysioEvent.Error(error.code))
        return error
    }

    /** POST /pay; on a transport failure retry once with the SAME key. Never retry on a response. */
    private suspend fun postWithOneRetry(body: JsonObject, idempotencyKey: String): PaysioClient.Response {
        val path = "api/checkout/${session.slug}/pay"
        val headers = mapOf("Idempotency-Key" to idempotencyKey)
        return try {
            client.post(path, body, headers)
        } catch (first: PaysioError) {
            if (first.code != PaysioErrorCode.NETWORK) throw fail(first.withIdempotencyKey(idempotencyKey))
            try {
                client.post(path, body, headers)
            } catch (second: PaysioError) {
                throw fail(second.withIdempotencyKey(idempotencyKey))
            }
        }
    }

    internal fun buildBody(
        source: PaysioPaymentSource,
        customer: PaysioCustomer,
        billing: PaysioAddress,
        options: PaysioPayOptions,
        threeDsToken: String?,
    ): JsonObject = buildJsonObject {
        put("email", customer.email.trim())
        put("firstName", customer.firstName.trim())
        put("lastName", customer.lastName.trim())
        customer.phone?.trim()?.takeIf { it.isNotEmpty() }?.let { put("phone", it) }
        // Billing address for manual entry only (a saved method carries its own), as in RN.
        if (source !is PaysioPaymentSource.Saved) {
            put("billingAddress1", billing.address1.trim())
            billing.address2?.trim()?.takeIf { it.isNotEmpty() }?.let { put("billingAddress2", it) }
            put("billingCity", billing.city.trim())
            put("billingState", billing.state.trim())
            put("billingPostalCode", billing.postalCode.trim())
            put("billingCountry", billing.country.trim().uppercase())
        }
        put("clientMetadata", clientMetadata(options.metadata))

        when (source) {
            is PaysioPaymentSource.Card -> {
                val c = source.alias
                put("vaultPanAlias", c.panAlias)
                put("vaultCvcAlias", c.cvcAlias)
                put("vaultExpMonth", c.expMonth)
                put("vaultExpYear", c.expYear)
                c.bin?.let { put("vaultBin", it) }
                c.brand?.let { put("vaultBrand", it) }
                c.last4?.let { put("vaultLast4", it) }
                threeDsToken?.let { put("threeDsToken", it) }
            }
            is PaysioPaymentSource.Bank -> {
                val b = source.alias
                put("paymentMethodType", "ach")
                put("vaultBankAccountAlias", b.accountAlias)
                b.last4?.let { put("vaultBankLast4", it) }
                put("routingNumber", b.routingNumber)
                put("accountType", b.accountType.wire)
                put("accountHolderType", b.holderType.wire)
                put("achAuthorized", true)
            }
            is PaysioPaymentSource.Saved -> {
                put("quickCheckout", true)
                put("billingId", source.billingId)
                put("portalToken", source.portalToken)
                source.cvc?.takeIf { it.isNotEmpty() }?.let { put("cvc", it) }
            }
        }

        val items = options.items ?: session.items
        put("items", buildJsonArray {
            items.forEach { item ->
                add(buildJsonObject {
                    put("productId", item.productId)
                    put("quantity", item.quantity)
                    item.variantId?.let { put("variantId", it) }
                })
            }
        })
        options.discountCode?.trim()?.takeIf { it.isNotEmpty() }?.let { put("discountCode", it) }
        if (options.customFieldValues.isNotEmpty()) {
            putJsonObject("customFieldValues") {
                options.customFieldValues.forEach { (k, v) -> put(k, v.toJson()) }
            }
        }
        options.shippingAddress?.let { s ->
            putJsonObject("shippingAddress") {
                put("address1", s.address1)
                s.address2?.let { put("address2", it) }
                put("city", s.city)
                put("state", s.state)
                put("postalCode", s.postalCode)
                put("country", s.country.uppercase())
            }
        }
    }

    private fun clientMetadata(extra: Map<String, PaysioJsonScalar>): JsonObject = buildJsonObject {
        put("platform", PaysioSdk.platform)
        put("sdk", PaysioSdk.headerValue)
        appId?.let { put("appId", it) }
        put("osVersion", Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString())
        put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
        put("language", Locale.getDefault().toLanguageTag())
        put("timezone", TimeZone.getDefault().id)
        val metrics = Resources.getSystem().displayMetrics
        put("screenWidth", (metrics.widthPixels / metrics.density).toInt())
        put("screenHeight", (metrics.heightPixels / metrics.density).toInt())
        if (extra.isNotEmpty()) {
            putJsonObject("extra") { extra.forEach { (k, v) -> put(k, v.toJson()) } }
        }
    }

    companion object {
        internal fun parseResult(json: JsonObject, idempotencyKey: String): PaysioPayResult {
            val card = json.obj("card")
            return PaysioPayResult(
                status = PaysioPayResult.Status.fromWire(json.string("status")),
                transactionId = json.string("transactionId") ?: "",
                orderNumber = json.string("orderNumber"),
                orderLabel = json.string("orderLabel"),
                confirmationToken = json.string("confirmationToken"),
                successUrl = json.string("successUrl"),
                amount = json.double("amount"),
                currency = json.string("currency"),
                cardBrand = card?.string("brand"),
                cardLast4 = card?.string("last4"),
                bankAuthUrl = json.string("bankAuthUrl"),
                idempotencyKey = idempotencyKey,
            )
        }
    }
}

internal fun PaysioJsonScalar.toJson(): JsonPrimitive = when (this) {
    is PaysioJsonScalar.Str -> JsonPrimitive(value)
    is PaysioJsonScalar.Num -> JsonPrimitive(value)
    is PaysioJsonScalar.Bool -> JsonPrimitive(value)
}
