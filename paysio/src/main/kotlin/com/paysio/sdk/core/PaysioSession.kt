package com.paysio.sdk.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** The subset of `GET /api/checkout/:slug` the SDK keeps (CONTRACT.md section 1). */
data class PaysioSession(
    val slug: String,
    val workspaceId: String,
    val workspaceName: String,
    val sandbox: Boolean,
    val currency: String,
    /** Total in minor units when the server provides it; null for pay-what-you-want. */
    val totalMinor: Long?,
    val totalFormatted: String,
    val cardEnabled: Boolean,
    val bankEnabled: Boolean,
    val threeDsEnabled: Boolean,
    val requirePhone: Boolean,
    val collectShippingAddress: Boolean,
    val customFields: List<PaysioCustomField>,
    val items: List<PaysioSessionItem>,
    val vault: PaysioVaultConfig?,
    val achAuthorizationText: String,
    val brand: PaysioBrand?,
    /** Raw server payload for anything the typed subset does not cover. */
    val raw: JsonObject,
) {
    /** Amount in major units for 3-D Secure (`amount: 12.34`). */
    val totalMajor: Double? get() = totalMinor?.let { it / 100.0 }

    companion object {
        /**
         * ACH consent template used only when an older backend omits
         * `workspace.achAuthorizationText`. Same wording as `backend/src/lib/checkout-copy.ts`.
         */
        const val DEFAULT_ACH_AUTHORIZATION_TEMPLATE =
            "By continuing, you authorize {merchant} to debit {amount} from the bank account above by " +
                "electronic funds transfer (ACH). You understand that this authorization will remain in effect " +
                "until the payment is processed, and that if the payment is returned unpaid you may be charged " +
                "a return fee. Transactions are processed securely by Paysio."

        /** Fills `{merchant}` and `{amount}` in the consent template (CONTRACT.md section 1). */
        fun fillAchTemplate(template: String, merchant: String, amount: String): String = template
            // Same as RN fillAchTemplate: case-insensitive, spaces allowed inside the braces.
            .replace(Regex("\\{\\s*merchant\\s*\\}", RegexOption.IGNORE_CASE), Regex.escapeReplacement(merchant.ifEmpty { "this merchant" }))
            .replace(Regex("\\{\\s*amount\\s*\\}", RegexOption.IGNORE_CASE), Regex.escapeReplacement(amount.ifEmpty { "the amount shown" }))
    }
}

/** Loads and parses a checkout session. */
object PaysioSessionLoader {

    /**
     * `GET /api/checkout/:slug` -> [PaysioSession].
     *
     * Throws `session_expired` for the 404 every inactive, expired, deleted or paid session
     * answers (CONTRACT.md section 1), and the usual `network` / `rate_limited` / `unknown` codes.
     */
    suspend fun load(slug: String, client: PaysioClient): PaysioSession {
        val response = client.get("api/checkout/$slug")
        if (!response.isSuccess) throw PaysioClient.mapFailure(response)
        return parse(slug, response.json)
    }

    /** Pure mapping from the server payload. Exposed for contract fixture tests. */
    fun parse(slug: String, json: JsonObject): PaysioSession {
        val link = json.obj("paymentLink") ?: JsonObject(emptyMap())
        val workspace = json.obj("workspace") ?: JsonObject(emptyMap())
        // No status / expiry fields exist on the payload: an unusable session is a 404
        // (mapped to SESSION_EXPIRED by PaysioClient.mapFailure).

        val vaultConfig = json.obj("vaultConfig")
        val vault: PaysioVaultConfig? = if (vaultConfig != null && vaultConfig.bool("enabled") == true) {
            val vaultId = vaultConfig.string("vaultId")
            val env = vaultConfig.string("environment")
            if (vaultId != null && env != null) {
                PaysioVaultConfig(vaultId, if (env.lowercase().contains("live")) PaysioEnvironment.LIVE else PaysioEnvironment.SANDBOX)
            } else null
        } else null

        val products = json.array("products")?.mapNotNull { it.asObjectOrNull() } ?: emptyList()
        val items = products.mapNotNull { p ->
            val productId = p.string("productId") ?: p.string("id") ?: return@mapNotNull null
            PaysioSessionItem(
                productId = productId,
                // `items` for the charge are `{ productId, quantity: defaultQuantity }`.
                quantity = maxOf(1, p.int("defaultQuantity") ?: p.int("quantity") ?: 1),
                variantId = p.string("variantId"),
                name = p.string("productName") ?: p.string("name"),
                unitAmount = p.long("productPrice") ?: p.long("price") ?: p.long("unitAmount"),
            )
        }

        val currency = (json.string("currency")
            ?: products.firstOrNull()?.string("productCurrency")
            ?: products.firstOrNull()?.string("currency")
            ?: "USD").uppercase()

        var totalMinor: Long? = json.long("totalAmount") ?: json.long("total")
        if (totalMinor == null && items.isNotEmpty() && items.all { it.unitAmount != null }) {
            totalMinor = items.sumOf { (it.unitAmount ?: 0L) * it.quantity }
        }

        val customFields = link.array("customFields")?.mapNotNull { element ->
            val f = element.asObjectOrNull() ?: return@mapNotNull null
            val id = f.string("id") ?: return@mapNotNull null
            PaysioCustomField(
                id = id,
                label = f.string("label") ?: id,
                type = f.string("type") ?: "text",
                required = f.bool("required") ?: false,
                options = f.array("options")?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList(),
                placeholder = f.string("placeholder"),
            )
        } ?: emptyList()

        val brand = json.obj("brand")?.let { b ->
            b.string("name")?.let { PaysioBrand(it, b.string("logoUrl"), b.string("accent")) }
        }

        val workspaceName = workspace.string("name") ?: brand?.name ?: ""
        val totalFormatted = PaysioMoney.format(totalMinor, currency)
        // Blank or missing (older backend): the shared default wording (CONTRACT.md "ACH consent text").
        val achTemplate = workspace.string("achAuthorizationText")?.takeIf { it.isNotBlank() }
            ?: PaysioSession.DEFAULT_ACH_AUTHORIZATION_TEMPLATE

        return PaysioSession(
            slug = slug,
            workspaceId = json.string("workspaceId") ?: "",
            workspaceName = workspaceName,
            sandbox = link.bool("sandbox") ?: false,
            currency = currency,
            totalMinor = totalMinor,
            totalFormatted = totalFormatted,
            cardEnabled = json.bool("cardEnabled") ?: false,
            bankEnabled = workspace.bool("achEnabled") ?: false,
            threeDsEnabled = json.bool("threeDsEnabled") ?: false,
            requirePhone = workspace.bool("requireCheckoutPhone") ?: false,
            collectShippingAddress = link.bool("collectShippingAddress") ?: false,
            customFields = customFields,
            items = items,
            vault = vault,
            achAuthorizationText = PaysioSession.fillAchTemplate(achTemplate, workspaceName, totalFormatted),
            brand = brand,
            raw = json,
        )
    }
}

/** Money formatting for display only; the server recomputes every total. */
object PaysioMoney {
    fun format(minor: Long?, currency: String, locale: Locale = Locale.getDefault()): String {
        if (minor == null) return ""
        return try {
            val nf = NumberFormat.getCurrencyInstance(locale)
            nf.currency = Currency.getInstance(currency)
            nf.format(minor / 100.0)
        } catch (_: Exception) {
            "${minor / 100.0} $currency"
        }
    }
}
