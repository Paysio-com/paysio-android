package com.paysio.sdk.core

import android.content.res.Resources
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Browser information the 3-D Secure server needs (CONTRACT.md section 4, step 3).
 * Wire format is snake_case: that is what the backend's `BrowserInfo` reads
 * (`backend/src/lib/vgs-vault/three-ds.ts`).
 */
data class BrowserInfo(
    val javaEnabled: Boolean = false,
    val javascriptEnabled: Boolean = true,
    val colorDepth: Int = 24,
    val screenWidth: Int,
    val screenHeight: Int,
    /** Minutes, as `Date().getTimezoneOffset()` returns in JavaScript. */
    val timezoneOffset: Int,
    val language: String,
    val userAgent: String,
) {
    /** Exactly the keys CONTRACT.md section 4 step 3 lists; `userAgent` stays local. */
    internal fun toJson(): JsonObject = buildJsonObject {
        put("java_enabled", javaEnabled)
        put("javascript_enabled", javascriptEnabled)
        put("color_depth", colorDepth)
        put("screen_width", screenWidth)
        put("screen_height", screenHeight)
        put("tz_offset", timezoneOffset)
        put("language", language)
    }

    companion object {
        /** Used when the fingerprint HTML is missing or the web view failed. */
        fun fallback(): BrowserInfo {
            val metrics = Resources.getSystem().displayMetrics
            return BrowserInfo(
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels,
                timezoneOffset = -TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000,
                language = Locale.getDefault().toLanguageTag(),
                userAgent = "Paysio Android SDK/${PaysioSdk.version}",
            )
        }
    }
}

/** What the issuer sent back for a challenge (CONTRACT.md section 4, step 4). */
data class ThreeDsChallenge(
    /** Self-posting HTML to render as is. */
    val form: String?,
    /** ACS URL to POST `creq` to (or to navigate to when `creq` is null). */
    val url: String?,
    val creq: String?,
    val sessionData: String?,
)

/** The web views the state machine needs. `WebViewThreeDsHost` is the Android implementation. */
interface ThreeDsWebHost {
    /** Loads the fingerprint HTML in a hidden web view for [durationMs], then reads the browser info. Never throws. */
    suspend fun fingerprint(html: String, durationMs: Long): BrowserInfo

    /** Presents the visible challenge; returns once it is on screen. [onCancel] fires if the customer dismisses it. */
    suspend fun presentChallenge(challenge: ThreeDsChallenge, onCancel: () -> Unit)

    /** Removes the challenge from screen. */
    suspend fun dismissChallenge()
}

/**
 * CONTRACT.md section 4 as a state machine:
 * preflight -> create-card -> init (fingerprint 3 s) -> authenticate
 * -> frictionless token | challenge (poll finish every 3 s, max 80) | skipped.
 */
class PaysioThreeDS(
    private val client: PaysioClient,
    private val session: PaysioSession,
    private val host: ThreeDsWebHost,
    private val onEvent: PaysioEventHandler? = null,
) {
    /** Returns the `tds_` token for the charge, or null when 3DS is not required / skipped. */
    suspend fun authenticate(card: PaysioCardAlias, amountMajor: Double, email: String, billingCountry: String): String? {
        val base = buildJsonObject {
            put("workspaceId", session.workspaceId)
            put("sandbox", session.sandbox)
        }
        val identity = buildJsonObject {
            put("panAlias", card.panAlias)
            card.bin?.let { put("vaultBin", it) } ?: put("vaultBin", JsonNull)
            card.brand?.let { put("brand", it) } ?: put("brand", JsonNull)
            put("expMonth", card.expMonth)
            put("expYear", card.expYear)
            put("billingCountry", billingCountry)
            put("email", email)
            card.last4?.let { put("last4", it) } ?: put("last4", JsonNull)
        }

        // 1. preflight
        val pre = post("api/vault/3ds/preflight", base + identity)
        if (pre.bool("required") == false) return null

        onEvent?.invoke(PaysioEvent.ThreeDsStart)

        // 2. create-card
        val created = post("api/vault/3ds/create-card", base + buildJsonObject {
            put("panAlias", card.panAlias)
            put("expMonth", card.expMonth)
            put("expYear", card.expYear)
        })
        val cardRef = created.string("cardRef")
            ?: throw PaysioError(PaysioErrorCode.THREE_DS_FAILED, "Card verification could not start.")
        val withRef = base + buildJsonObject { put("cardRef", cardRef) }

        // 3. init + fingerprint (skip the 3 s wait when the HTML is null)
        val init = post("api/vault/3ds/init", withRef)
        val transactionInfo = init.obj("transactionInfo") ?: JsonObject(emptyMap())
        val html = init.string("deviceFingerprintingHtml")
        val browser = if (!html.isNullOrEmpty()) host.fingerprint(html, FINGERPRINT_MS) else BrowserInfo.fallback()

        // 4. authenticate
        val redirectUrl = client.apiBase.trimEnd('/') + "/api/vault/3ds/challenge-return"
        val auth = post("api/vault/3ds/authenticate", withRef + identity + buildJsonObject {
            put("amount", amountMajor)
            put("currency", session.currency)
            put("browser", browser.toJson())
            put("redirectUrl", redirectUrl)
            put("transactionInfo", transactionInfo)
            put("channel", "app")
        })

        if (auth.bool("skipped") == true) return null

        val challengeRequired = auth.bool("challengeRequired") == true || auth.string("status") == "CHALLENGE_REQUIRED"
        if (!challengeRequired) return finalize(auth)

        // 5. challenge + poll
        val c = auth.obj("challenge") ?: JsonObject(emptyMap())
        val challenge = ThreeDsChallenge(
            form = c.string("form"),
            url = c.string("url"),
            creq = c.string("creq"),
            sessionData = c.string("session_data") ?: c.string("sessionData"),
        )
        if (challenge.form == null && challenge.url == null) {
            throw PaysioError(PaysioErrorCode.THREE_DS_FAILED,
                "Card verification requires a challenge, but the issuer returned no challenge content.")
        }

        onEvent?.invoke(PaysioEvent.ThreeDsChallenge)
        val cancelled = AtomicBoolean(false)
        host.presentChallenge(challenge) { cancelled.set(true) }
        try {
            val finishBody = withRef + buildJsonObject {
                put("amount", amountMajor)
                put("transactionInfo", transactionInfo)
            }
            repeat(MAX_POLLS) {
                delay(POLL_INTERVAL_MS)
                if (cancelled.get()) {
                    onEvent?.invoke(PaysioEvent.ThreeDsResult(false))
                    throw PaysioError(PaysioErrorCode.THREE_DS_CANCELLED, "Card verification was cancelled.")
                }
                val finish = post("api/vault/3ds/finish", finishBody)
                // CONTRACT.md section 4 step 5: stop on a string `threeDsToken` (approved) or on
                // `challengeRequired: false` without a token (failed). Otherwise keep polling.
                val token = finish.string("threeDsToken")
                if (!token.isNullOrEmpty()) return finalize(finish)
                if (finish.bool("challengeRequired") == false) return finalize(finish) // throws with the ACS reason
            }
            onEvent?.invoke(PaysioEvent.ThreeDsResult(false))
            throw PaysioError(PaysioErrorCode.THREE_DS_TIMEOUT, "Card verification did not complete in time. Please try again.")
        } finally {
            host.dismissChallenge()
        }
    }

    /**
     * 3DS call with the contract's mapping: a `502 { error, detail }` (VGS failed) and any
     * other non-2xx that is not a rate limit or transport error becomes `three_ds_failed`.
     */
    private suspend fun post(path: String, body: JsonObject): JsonObject {
        val response = client.post(path, body)
        if (!response.isSuccess) {
            val mapped = PaysioClient.mapFailure(response)
            if (mapped.code == PaysioErrorCode.RATE_LIMITED || mapped.code == PaysioErrorCode.NETWORK) throw mapped
            throw PaysioError(PaysioErrorCode.THREE_DS_FAILED, response.json.string("error") ?: "Card verification failed.",
                httpStatus = response.status)
        }
        return response.json
    }

    private fun finalize(result: JsonObject): String {
        val status = result.string("status") ?: ""
        if (status !in APPROVED_STATUSES && result.bool("approved") != true) {
            onEvent?.invoke(PaysioEvent.ThreeDsResult(false))
            // `message` carries the ACS reason on a failed challenge.
            val message = when {
                status.isEmpty() -> "Card verification did not complete. Please try again."
                result.string("message") != null -> "Card verification was declined: ${result.string("message")}"
                else -> "Card verification was declined. Please use a different card."
            }
            throw PaysioError(PaysioErrorCode.THREE_DS_FAILED, message)
        }
        val token = result.string("threeDsToken")
        if (token.isNullOrEmpty()) {
            onEvent?.invoke(PaysioEvent.ThreeDsResult(false))
            throw PaysioError(PaysioErrorCode.THREE_DS_FAILED, "Card verification did not complete. Please try again.")
        }
        onEvent?.invoke(PaysioEvent.ThreeDsResult(true))
        return token
    }

    companion object {
        const val FINGERPRINT_MS = 3_000L
        const val POLL_INTERVAL_MS = 3_000L
        const val MAX_POLLS = 80

        /** Approved statuses per the hosted checkout (`paysio-storefront.js`). */
        private val APPROVED_STATUSES = setOf("APPROVED", "ATTEMPTS_PERFORMED", "Y", "A")
    }
}

/** Shallow merge; right side wins. */
internal operator fun JsonObject.plus(other: JsonObject): JsonObject = JsonObject(this.toMutableMap().apply { putAll(other) })
