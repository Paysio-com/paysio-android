package com.paysio.sdk.core

import android.content.Context
import com.verygoodsecurity.vgscollect.core.Environment
import com.verygoodsecurity.vgscollect.core.HTTPMethod
import com.verygoodsecurity.vgscollect.core.VGSCollect
import com.verygoodsecurity.vgscollect.core.model.network.VGSResponse
import com.verygoodsecurity.vgscollect.widget.compose.state.VgsCardNumberTextFieldState
import com.verygoodsecurity.vgscollect.widget.compose.state.core.BaseFieldState
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject

/**
 * Wraps one `VGSCollect` instance and turns a submit to the vault's inbound route
 * into aliases (CONTRACT.md section 2). The raw PAN, CVC and account number live only
 * inside the VGS Compose states and the TLS request VGS makes.
 *
 * VGS API relied on (vgs-collect-android 2.1.0, `core/VGSCollect.kt`):
 * ```kotlin
 * constructor(context: Context, id: String, environment: Environment = Environment.SANDBOX)
 * suspend fun submitAsync(path: String, method: HTTPMethod = HTTPMethod.POST,
 *                         fieldsStates: List<BaseFieldState>? = null): VGSResponse   // runs on Dispatchers.IO
 * fun onDestroy()
 * sealed class VGSResponse(val code: Int, val body: String?) {
 *     data class SuccessResponse(... successCode ...)
 *     data class ErrorResponse(val localizeMessage: String, val errorCode: Int, ...)
 * }
 * ```
 * Compose states are passed straight to `submitAsync` ("no bindView() needed" per the
 * VGS Android agent guide section 2B).
 */
class PaysioVault(context: Context, val config: PaysioVaultConfig) {

    object FieldName {
        const val CARD_NUMBER = "card_number"
        const val CARD_EXPIRY = "card_exp"
        const val CARD_CVC = "card_cvc"
        const val BANK_ACCOUNT_NUMBER = "bank_account_number"
    }

    /** The collector the SDK's fields register with. */
    val collect: VGSCollect = VGSCollect(
        context,
        config.vaultId,
        if (config.environment == PaysioEnvironment.LIVE) Environment.LIVE else Environment.SANDBOX,
    )

    /** Submits the three card states to `/post` and reads the aliases from the echo. */
    suspend fun tokenizeCard(
        numberState: VgsCardNumberTextFieldState,
        expiryState: BaseFieldState,
        cvcState: BaseFieldState,
    ): PaysioCardAlias {
        val body = submit(listOf(numberState, expiryState, cvcState))
        val panAlias = body.string(FieldName.CARD_NUMBER)
        val cvcAlias = body.string(FieldName.CARD_CVC)
        val exp = body.string(FieldName.CARD_EXPIRY)?.let { PaysioValidation.parseExpiry(it) }
        if (panAlias.isNullOrEmpty() || cvcAlias.isNullOrEmpty() || exp == null) {
            throw PaysioError(PaysioErrorCode.VAULT_UNAVAILABLE, "The vault did not return the card aliases.")
        }
        return PaysioCardAlias(
            panAlias = panAlias,
            cvcAlias = cvcAlias,
            expMonth = exp.first,
            expYear = exp.second,
            bin = numberState.bin?.takeIf { it.isNotEmpty() },
            brand = PaysioValidation.normalizeBrand(numberState.cardBrand.name),
            last4 = numberState.last4?.takeIf { it.isNotEmpty() },
        )
    }

    /** Submits the bank account state. The routing number is not sensitive and passes through. */
    suspend fun tokenizeBank(
        accountState: BaseFieldState,
        routingNumber: String,
        accountType: PaysioBankAccountType,
        holderType: PaysioBankHolderType,
    ): PaysioBankAlias {
        val body = submit(listOf(accountState))
        val alias = body.string(FieldName.BANK_ACCOUNT_NUMBER)
        if (alias.isNullOrEmpty()) {
            throw PaysioError(PaysioErrorCode.VAULT_UNAVAILABLE, "The vault did not return the bank account alias.")
        }
        // GENERIC_T_FOUR aliases keep the last four digits at the end.
        return PaysioBankAlias(alias, alias.takeLast(4), routingNumber, accountType, holderType)
    }

    /** Releases VGS resources. Call from your screen's disposal. */
    fun destroy() {
        collect.onDestroy()
    }

    /** `submitAsync` with a 20 s ceiling; returns the echo body (`json.json ?? json.data ?? json`). */
    private suspend fun submit(states: List<BaseFieldState>): JsonObject {
        val response: VGSResponse = try {
            withTimeout(SUBMIT_TIMEOUT_MS) {
                collect.submitAsync("/post", HTTPMethod.POST, states)
            }
        } catch (e: TimeoutCancellationException) {
            throw PaysioError(PaysioErrorCode.VAULT_UNAVAILABLE,
                "Securing your details took too long. Check your connection and try again.", cause = e)
        }
        return when (response) {
            is VGSResponse.SuccessResponse -> {
                val root = PaysioClient.decode(response.body ?: "")
                root.obj("json") ?: root.obj("data") ?: root
            }
            is VGSResponse.ErrorResponse -> throw PaysioError(
                PaysioErrorCode.VAULT_UNAVAILABLE,
                response.localizeMessage.ifBlank { "The vault rejected the request (${response.errorCode})." },
                httpStatus = response.errorCode,
            )
        }
    }

    companion object {
        const val SUBMIT_TIMEOUT_MS = 20_000L
    }
}
