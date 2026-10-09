package com.paysio.sdk.core

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin JSON client for the Paysio checkout client API (CONTRACT.md).
 *
 * - 20 second timeouts on every call (CONTRACT.md section 6).
 * - Sends `X-Paysio-SDK: android/<version>` on every request.
 * - Transport failures become [PaysioError] with [PaysioErrorCode.NETWORK]; HTTP
 *   statuses map to the contract's codes in [mapFailure].
 */
class PaysioClient(
    /** `https://api.paysio.com` unless you are pointing at staging. No trailing slash needed. */
    val apiBase: String = DEFAULT_API_BASE,
    okHttpClient: OkHttpClient? = null,
) {
    class Response(val status: Int, val json: JsonObject) {
        val isSuccess: Boolean get() = status in 200..299
    }

    private val http: OkHttpClient = (okHttpClient?.newBuilder() ?: OkHttpClient.Builder())
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val base: String = apiBase.trimEnd('/')

    /** `GET <apiBase>/<path>`. */
    suspend fun get(path: String): Response {
        val request = Request.Builder()
            .url(url(path))
            .get()
            .header("Accept", "application/json")
            .header("X-Paysio-SDK", PaysioSdk.headerValue)
            .build()
        return perform(request)
    }

    /** `POST <apiBase>/<path>` with a JSON body and optional extra headers. */
    suspend fun post(path: String, body: JsonObject, headers: Map<String, String> = emptyMap()): Response {
        val builder = Request.Builder()
            .url(url(path))
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Paysio-SDK", PaysioSdk.headerValue)
        headers.forEach { (k, v) -> builder.header(k, v) }
        return perform(builder.build())
    }

    /** Like [post] but throws a mapped [PaysioError] for any non-2xx status. */
    suspend fun postExpectingSuccess(path: String, body: JsonObject, headers: Map<String, String> = emptyMap()): JsonObject {
        val response = post(path, body, headers)
        if (!response.isSuccess) throw mapFailure(response)
        return response.json
    }

    private fun url(path: String): String = "$base/${path.trimStart('/')}"

    private suspend fun perform(request: Request): Response = suspendCancellableCoroutine { cont ->
        val call = http.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(PaysioError(PaysioErrorCode.NETWORK, describe(e), cause = e))
            }

            // Fully qualified: the nested PaysioClient.Response shadows the OkHttp import.
            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use {
                    val text = try { it.body.string() } catch (e: IOException) {
                        if (cont.isActive) cont.resumeWithException(PaysioError(PaysioErrorCode.NETWORK, describe(e), cause = e))
                        return
                    }
                    if (cont.isActive) cont.resume(Response(it.code, decode(text)))
                }
            }
        })
    }

    companion object {
        const val DEFAULT_API_BASE = "https://api.paysio.com"
        const val TIMEOUT_SECONDS = 20L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Decodes a JSON object body; anything else (HTML, empty) becomes `{}`. */
        fun decode(text: String): JsonObject {
            if (text.isBlank()) return JsonObject(emptyMap())
            return try {
                val element = json.parseToJsonElement(text)
                if (element is JsonObject) element else JsonObject(emptyMap())
            } catch (_: Exception) {
                JsonObject(emptyMap())
            }
        }

        /** Status to error code mapping per CONTRACT.md sections 3 and 5. */
        fun mapFailure(response: Response, idempotencyKey: String? = null): PaysioError {
            val json = response.json
            val message = json.string("error") ?: json.string("message") ?: "Request failed (${response.status})"
            val serverCode = json.string("code")
            val declineCode = json.string("declineCode")
            return when (response.status) {
                // A clean decline is a 400 with code "card_declined" (CONTRACT.md section 3).
                400 -> if (serverCode == "card_declined" || json.string("status") == "declined") {
                    PaysioError(PaysioErrorCode.DECLINED, message, declineCode = declineCode, idempotencyKey = idempotencyKey, httpStatus = 400)
                } else {
                    PaysioError(classify400(message), message, idempotencyKey = idempotencyKey, httpStatus = 400)
                }
                // Only "3ds_required" / "3ds_failed": refused before the gateway was contacted.
                402 -> PaysioError(PaysioErrorCode.THREE_DS_FAILED, message, idempotencyKey = idempotencyKey, httpStatus = 402)
                // "Payment link not found or inactive": expired, inactive, deleted or already paid.
                404 -> PaysioError(PaysioErrorCode.SESSION_EXPIRED, message, idempotencyKey = idempotencyKey, httpStatus = 404)
                // 409 duplicate_request, 422 idempotency_key_reused: do not retry blindly.
                409, 422 -> PaysioError(PaysioErrorCode.UNKNOWN, json.string("message") ?: message,
                    idempotencyKey = idempotencyKey, httpStatus = response.status)
                429 -> PaysioError(PaysioErrorCode.RATE_LIMITED, message, idempotencyKey = idempotencyKey, httpStatus = 429)
                else -> PaysioError(PaysioErrorCode.UNKNOWN, message, idempotencyKey = idempotencyKey, httpStatus = response.status)
            }
        }

        /** Exact validation strings from CONTRACT.md section 3. */
        const val BILLING_ADDRESS_MESSAGE = "Please enter your full billing address (street, city and postal code)."
        const val CONTACT_MESSAGE = "Missing required contact fields"
        const val ACH_AUTHORIZATION_MESSAGE = "You must authorize the bank debit to continue"

        private fun classify400(message: String): PaysioErrorCode {
            if (message == BILLING_ADDRESS_MESSAGE) return PaysioErrorCode.BILLING_ADDRESS_REQUIRED
            if (message == CONTACT_MESSAGE) return PaysioErrorCode.CONTACT_REQUIRED
            if (message == ACH_AUTHORIZATION_MESSAGE) return PaysioErrorCode.INVALID_FIELDS
            val lower = message.lowercase()
            return when {
                lower.contains("billing address") || lower.contains("postal") -> PaysioErrorCode.BILLING_ADDRESS_REQUIRED
                lower.contains("contact") -> PaysioErrorCode.CONTACT_REQUIRED
                else -> PaysioErrorCode.INVALID_FIELDS
            }
        }

        private fun describe(e: IOException): String = when (e) {
            is SocketTimeoutException -> "The request timed out."
            is UnknownHostException -> "Could not reach Paysio."
            else -> e.message ?: "Network error."
        }
    }
}

// ── JSON helpers shared by the core ──────────────────────────────────────────

internal fun JsonObject.string(key: String): String? {
    val v = this[key] ?: return null
    if (v is JsonNull) return null
    val p = v as? JsonPrimitive ?: return null
    return p.content
}

internal fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject.int(key: String): Int? {
    val p = this[key] as? JsonPrimitive ?: return null
    return p.intOrNull ?: p.doubleOrNull?.toInt()
}

internal fun JsonObject.long(key: String): Long? {
    val p = this[key] as? JsonPrimitive ?: return null
    return p.longOrNull ?: p.doubleOrNull?.toLong()
}

internal fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement.asPrimitiveContent(): String? = (this as? JsonPrimitive)?.content
