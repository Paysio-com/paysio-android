package com.paysio.sdk.core

/**
 * Error codes every Paysio Mobile SDK exposes (CONTRACT.md section 5). The `wire`
 * values match the React Native and iOS SDKs.
 */
enum class PaysioErrorCode(val wire: String) {
    /** One or more fields are invalid. See [PaysioError.fieldErrors]. */
    INVALID_FIELDS("invalid_fields"),
    /** Street, city, postal code or country is missing. */
    BILLING_ADDRESS_REQUIRED("billing_address_required"),
    /** Email, first name, last name (or phone when required) is missing. */
    CONTACT_REQUIRED("contact_required"),
    /** 3-D Secure was declined or errored. */
    THREE_DS_FAILED("three_ds_failed"),
    /** The customer closed the 3-D Secure challenge. Nothing was charged. */
    THREE_DS_CANCELLED("three_ds_cancelled"),
    /** The challenge did not resolve within four minutes. */
    THREE_DS_TIMEOUT("three_ds_timeout"),
    /** The processor declined. See [PaysioError.declineCode]. */
    DECLINED("declined"),
    /** The session is no longer active; create a new one on your server. */
    SESSION_EXPIRED("session_expired"),
    /** The slug does not exist. */
    SESSION_INVALID("session_invalid"),
    /** Too many attempts for this session from this device. */
    RATE_LIMITED("rate_limited"),
    /** The request never reached Paysio. */
    NETWORK("network"),
    /** Outcome unknown (5xx or unexpected payload); [PaysioError.idempotencyKey] is set. */
    UNKNOWN("unknown"),
    /** VGS Collect could not secure the details. */
    VAULT_UNAVAILABLE("vault_unavailable"),
    /** The session has no vault configuration. */
    NOT_CONFIGURED("not_configured"),
}

/** Field identifiers for [PaysioError.fieldErrors] and focus events. */
enum class PaysioField(val wire: String) {
    NUMBER("number"), EXPIRY("expiry"), CVC("cvc"), ROUTING("routing"), ACCOUNT("account"),
    EMAIL("email"), FIRST_NAME("firstName"), LAST_NAME("lastName"), PHONE("phone"),
    ADDRESS1("address1"), CITY("city"), STATE("state"), POSTAL_CODE("postalCode"), COUNTRY("country"),
}

/** The single exception type thrown by the SDK. Mirrors `PaysioErrorShape` in RN. */
class PaysioError(
    val code: PaysioErrorCode,
    override val message: String,
    /** Processor decline code when [code] is [PaysioErrorCode.DECLINED]. */
    val declineCode: String? = null,
    /** Per-field messages for the validation codes. */
    val fieldErrors: Map<PaysioField, String> = emptyMap(),
    /** The `Idempotency-Key` of the charge attempt, when one was minted. */
    val idempotencyKey: String? = null,
    /** HTTP status of the response that produced this error, if any. */
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** Copy carrying the idempotency key of the attempt that failed. */
    internal fun withIdempotencyKey(key: String?): PaysioError =
        PaysioError(code, message, declineCode, fieldErrors, key ?: idempotencyKey, httpStatus, cause)

    override fun toString(): String = "PaysioError(${code.wire}): $message"
}
