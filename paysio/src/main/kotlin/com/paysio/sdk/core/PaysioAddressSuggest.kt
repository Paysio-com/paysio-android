package com.paysio.sdk.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** One street address suggestion (placeId + display text, plus parsed parts after details). */
data class PaysioAddressSuggestion(
    val id: String,
    val label: String,
    val address1: String = "",
    val city: String = "",
    val state: String = "",
    val stateCode: String = "",
    val postalCode: String = "",
    val countryCode: String = "",
)

/**
 * Street address autocomplete, ported from the RN SDK's `src/core/address.ts`, through
 * the same two public endpoints the hosted checkout uses:
 *
 *   GET {apiBase}/api/checkout/address-suggest?q=&country=&sessiontoken=
 *       -> { suggestions: AddressSuggestion[] }   (placeId + display text only)
 *   GET {apiBase}/api/checkout/address-details?placeId=&sessiontoken=
 *       -> { suggestion: AddressSuggestion | null } (parsed components)
 *
 * The backend holds the Google Places key, so nothing secret ships in the app. Both
 * functions never throw: any failure is an empty list / null and the street field
 * keeps working as plain text. Calls carry `X-Paysio-SDK` and time out after 8 s.
 */
object PaysioAddressSuggest {
    const val MIN_QUERY = 3
    const val DEBOUNCE_MS = 250L
    private const val TIMEOUT_MS = 8_000L

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    /** Suggestions for [query] (at least [MIN_QUERY] characters), or an empty list. */
    suspend fun suggest(apiBase: String, query: String, country: String? = null, sessionToken: String? = null): List<PaysioAddressSuggestion> {
        val text = query.trim()
        if (text.length < MIN_QUERY) return emptyList()
        val url = build(apiBase, "api/checkout/address-suggest", listOf(
            "q" to text,
            "country" to (country ?: "").lowercase().take(2),
            "sessiontoken" to sessionToken,
        )) ?: return emptyList()
        val json = fetch(url) ?: return emptyList()
        val list = json.array("suggestions") ?: return emptyList()
        return list.mapNotNull { it.asObjectOrNull()?.let(::parse) }.filter { it.id.isNotEmpty() && it.label.isNotEmpty() }
    }

    /** The parsed place for [placeId], or null when it cannot be resolved. */
    suspend fun details(apiBase: String, placeId: String, sessionToken: String? = null): PaysioAddressSuggestion? {
        if (placeId.isEmpty()) return null
        val url = build(apiBase, "api/checkout/address-details", listOf("placeId" to placeId, "sessiontoken" to sessionToken))
            ?: return null
        val json = fetch(url) ?: return null
        return json.obj("suggestion")?.let(::parse)?.takeIf { it.id.isNotEmpty() || it.address1.isNotEmpty() }
    }

    private fun parse(o: JsonObject) = PaysioAddressSuggestion(
        id = o.string("id") ?: "",
        label = o.string("label") ?: "",
        address1 = o.string("address1") ?: "",
        city = o.string("city") ?: "",
        state = o.string("state") ?: "",
        stateCode = o.string("stateCode") ?: "",
        postalCode = o.string("postalCode") ?: "",
        countryCode = o.string("countryCode") ?: "",
    )

    private fun build(apiBase: String, path: String, params: List<Pair<String, String?>>): String? {
        val base = "${apiBase.trimEnd('/')}/$path".toHttpUrlOrNull() ?: return null
        val builder = base.newBuilder()
        params.forEach { (k, v) -> if (!v.isNullOrEmpty()) builder.addQueryParameter(k, v) }
        return builder.build().toString()
    }

    private suspend fun fetch(url: String): JsonObject? = try {
        suspendCancellableCoroutine { cont ->
            val request = Request.Builder().url(url).get()
                .header("Accept", "application/json")
                .header("X-Paysio-SDK", PaysioSdk.headerValue)
                .build()
            val call = http.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = response.use {
                        if (!it.isSuccessful) null
                        else try { PaysioClient.decode(it.body.string()) } catch (_: Exception) { null }
                    }
                    if (cont.isActive) cont.resume(result)
                }
            })
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
