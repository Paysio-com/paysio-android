package com.paysio.sdk.threeds

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.paysio.sdk.core.BrowserInfo
import com.paysio.sdk.core.PaysioClient
import com.paysio.sdk.core.ThreeDsChallenge
import com.paysio.sdk.core.ThreeDsWebHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.coroutines.resume

/**
 * `android.webkit.WebView` implementation of [ThreeDsWebHost].
 *
 * - Fingerprint: a hidden 1 x 1 web view attached to the activity's content view for
 *   3 seconds, then `evaluateJavascript` reads the browser information from it.
 * - Challenge: a full-screen white [Dialog] with the title "Verify your card", a Cancel
 *   button and a web view that loads the issuer's HTML (`loadDataWithBaseURL`).
 * - `shouldOverrideUrlLoading` allows https (and about:blank) only.
 * - JavaScript is enabled on these two web views only; both are destroyed afterwards.
 * - [secure] adds `FLAG_SECURE` to the challenge window (no screenshots / recording).
 */
class WebViewThreeDsHost(
    private val activity: Activity,
    private val secure: Boolean = true,
) : ThreeDsWebHost {

    private var challengeDialog: Dialog? = null
    private var challengeWebView: WebView? = null

    override suspend fun fingerprint(html: String, durationMs: Long): BrowserInfo = withContext(Dispatchers.Main) {
        val container = activity.findViewById<ViewGroup>(android.R.id.content) ?: return@withContext BrowserInfo.fallback()
        val webView = newWebView().apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
            alpha = 0f
            isFocusable = false
            isClickable = false
            webViewClient = HttpsOnlyClient()
        }
        container.addView(webView)
        try {
            webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            delay(durationMs)
            val js = """
                JSON.stringify({
                  java_enabled: false,
                  javascript_enabled: true,
                  color_depth: (window.screen && window.screen.colorDepth) || 24,
                  screen_width: (window.screen && window.screen.width) || 0,
                  screen_height: (window.screen && window.screen.height) || 0,
                  tz_offset: new Date().getTimezoneOffset(),
                  language: navigator.language || 'en-US',
                  user_agent: navigator.userAgent || ''
                })
            """.trimIndent()
            val raw: String? = suspendCancellableCoroutine { cont ->
                webView.evaluateJavascript(js) { value -> if (cont.isActive) cont.resume(value) }
            }
            parseBrowserInfo(raw)
        } catch (_: Exception) {
            BrowserInfo.fallback()
        } finally {
            container.removeView(webView)
            webView.destroy()
        }
    }

    override suspend fun presentChallenge(challenge: ThreeDsChallenge, onCancel: () -> Unit) = withContext(Dispatchers.Main) {
        dismissChallenge()
        val dialog = Dialog(activity, android.R.style.Theme_Light_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        val density = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            fitsSystemWindows = true
        }
        val header = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
            setBackgroundColor(Color.WHITE)
        }
        val title = TextView(activity).apply {
            text = "Verify your card"
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        var cancelled = false
        val cancel = Button(activity, null, android.R.attr.borderlessButtonStyle).apply {
            text = "Cancel"
            setTextColor(Color.parseColor("#0B57D0"))
            isAllCaps = false
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                marginEnd = dp(8)
            }
            setOnClickListener {
                if (!cancelled) {
                    cancelled = true
                    onCancel()
                    dialog.dismiss()
                }
            }
        }
        header.addView(title)
        header.addView(cancel)

        val separator = android.view.View(activity).apply {
            setBackgroundColor(Color.parseColor("#E5E5E5"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        }

        val webView = newWebView().apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            webViewClient = HttpsOnlyClient()
        }
        root.addView(header)
        root.addView(separator)
        root.addView(webView)
        dialog.setContentView(root)
        dialog.window?.let { w ->
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            w.setBackgroundDrawableResource(android.R.color.white)
            if (secure) w.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        challengeDialog = dialog
        challengeWebView = webView
        dialog.show()

        when {
            !challenge.form.isNullOrEmpty() ->
                webView.loadDataWithBaseURL(null, challenge.form, "text/html", "utf-8", null)
            !challenge.url.isNullOrEmpty() && !challenge.creq.isNullOrEmpty() ->
                webView.loadDataWithBaseURL(null, formHtml(challenge.url, challenge.creq, challenge.sessionData), "text/html", "utf-8", null)
            !challenge.url.isNullOrEmpty() ->
                webView.loadUrl(challenge.url)
        }
    }

    override suspend fun dismissChallenge() = withContext(Dispatchers.Main) {
        challengeDialog?.let { if (it.isShowing) it.dismiss() }
        challengeDialog = null
        challengeWebView?.destroy()
        challengeWebView = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(): WebView = WebView(activity).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportMultipleWindows(false)
        setBackgroundColor(Color.WHITE)
    }

    /** Allows https and about:blank only. */
    private class HttpsOnlyClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            val allowed = url.scheme.equals("https", ignoreCase = true) || url.toString() == "about:blank"
            return !allowed
        }
    }

    private fun parseBrowserInfo(raw: String?): BrowserInfo {
        // evaluateJavascript returns a JSON string literal (quoted); unwrap it first.
        val unquoted = raw?.takeIf { it != "null" }?.let { PaysioClient.json.parseToJsonElement(it) } ?: return BrowserInfo.fallback()
        val text = (unquoted as? JsonPrimitive)?.content ?: return BrowserInfo.fallback()
        val obj = PaysioClient.decode(text)
        val fb = BrowserInfo.fallback()
        fun int(key: String) = (obj[key] as? JsonPrimitive)?.intOrNull
        fun str(key: String) = (obj[key] as? JsonPrimitive)?.content
        return BrowserInfo(
            javaEnabled = (obj["java_enabled"] as? JsonPrimitive)?.booleanOrNull ?: false,
            javascriptEnabled = true,
            colorDepth = int("color_depth") ?: fb.colorDepth,
            screenWidth = int("screen_width")?.takeIf { it > 0 } ?: fb.screenWidth,
            screenHeight = int("screen_height")?.takeIf { it > 0 } ?: fb.screenHeight,
            timezoneOffset = int("tz_offset") ?: fb.timezoneOffset,
            language = str("language") ?: fb.language,
            userAgent = str("user_agent")?.takeIf { it.isNotEmpty() } ?: fb.userAgent,
        )
    }

    companion object {
        /** EMV 3DS challenge request: POST `creq` (and `threeDSSessionData`), the form paysio.js builds. */
        internal fun formHtml(action: String, creq: String, sessionData: String?): String {
            val inputs = StringBuilder("<input type=\"hidden\" name=\"creq\" value=\"${escape(creq)}\">")
            if (!sessionData.isNullOrEmpty()) {
                inputs.append("<input type=\"hidden\" name=\"threeDSSessionData\" value=\"${escape(sessionData)}\">")
            }
            return """
                <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                <body style="margin:0;background:#fff">
                <form id="f" method="POST" action="${escape(action)}">$inputs</form>
                <script>document.getElementById('f').submit();</script>
                </body></html>
            """.trimIndent()
        }

        private fun escape(s: String): String = s
            .replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
    }
}
