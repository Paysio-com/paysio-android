package com.paysio.sdk.core

/**
 * Client-side rules that mirror the server (CONTRACT.md section 3): street, city,
 * postal code and country for any manually entered card or bank; email, first name
 * and last name; phone when the workspace requires it.
 */
object PaysioValidation {

    fun validateContact(customer: PaysioCustomer, requirePhone: Boolean): Map<PaysioField, String> {
        val errors = LinkedHashMap<PaysioField, String>()
        if (!isEmail(customer.email)) errors[PaysioField.EMAIL] = "Enter a valid email address."
        if (customer.firstName.isBlank()) errors[PaysioField.FIRST_NAME] = "Enter your first name."
        if (customer.lastName.isBlank()) errors[PaysioField.LAST_NAME] = "Enter your last name."
        if (requirePhone && (customer.phone ?: "").trim().length < 7) errors[PaysioField.PHONE] = "Enter your phone number."
        return errors
    }

    fun validateBillingAddress(address: PaysioAddress): Map<PaysioField, String> {
        val errors = LinkedHashMap<PaysioField, String>()
        if (address.address1.isBlank()) errors[PaysioField.ADDRESS1] = "Enter your street address."
        if (address.city.isBlank()) errors[PaysioField.CITY] = "Enter your city."
        if (address.postalCode.isBlank()) errors[PaysioField.POSTAL_CODE] = "Enter your postal code."
        if (address.country.trim().length != 2) errors[PaysioField.COUNTRY] = "Choose your country."
        if (address.country.uppercase() in setOf("US", "CA") && address.state.isBlank()) {
            errors[PaysioField.STATE] = "Choose your state."
        }
        return errors
    }

    fun isEmail(value: String): Boolean {
        val v = value.trim()
        val at = v.indexOf('@')
        if (at <= 0 || at >= v.length - 1) return false
        val domain = v.substring(at + 1)
        return domain.contains('.') && !v.contains(' ')
    }

    /** ABA routing number: 9 digits and the 3,7,1 checksum. */
    fun isRoutingNumber(value: String): Boolean {
        val digits = value.trim()
        if (digits.length != 9 || !digits.all { it.isDigit() }) return false
        val weights = intArrayOf(3, 7, 1, 3, 7, 1, 3, 7, 1)
        var sum = 0
        digits.forEachIndexed { i, ch -> sum += (ch - '0') * weights[i] }
        return sum % 10 == 0
    }

    /** Bank account numbers: 4 to 17 digits (the US ACH range). */
    fun isAccountNumberLength(length: Int): Boolean = length in 4..17

    /**
     * Normalizes a VGS brand name (`VgsCardBrand.name`, e.g. "VISA", "MASTERCARD",
     * "AMERICAN_EXPRESS", "DISCOVER", "DINCLUB", "JCB", "UNIONPAY", "MAESTRO") to the
     * identifiers the Paysio API and the brand marks use.
     */
    fun normalizeBrand(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val key = name.lowercase().replace(" ", "").replace("_", "")
        return when (key) {
            "visa", "visaelectron" -> "visa"
            "mastercard", "master", "mc" -> "mastercard"
            "amex", "americanexpress" -> "amex"
            "discover" -> "discover"
            "dinersclub", "diners", "dinclub" -> "dinersclub"
            "jcb" -> "jcb"
            "unionpay" -> "unionpay"
            "maestro" -> "maestro"
            "unknown", "" -> null
            else -> key
        }
    }

    /** Parses "MM/YY", "MM / YY" or "MM/YYYY" into ("MM", "YYYY"). */
    fun parseExpiry(raw: String): Pair<String, String>? {
        val parts = raw.split('/', '-').map { it.trim() }
        if (parts.size != 2) return null
        val m = parts[0].toIntOrNull() ?: return null
        var y = parts[1].toIntOrNull() ?: return null
        if (m !in 1..12) return null
        if (y < 100) y += 2000
        return Pair(m.toString().padStart(2, '0'), y.toString())
    }
}
