package com.paysio.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.paysio.sdk.core.PaysioAddress
import com.paysio.sdk.core.PaysioCustomer
import com.paysio.sdk.ui.PaysioPaymentSheet
import com.paysio.sdk.ui.PaysioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Minimal Compose host for the Paysio Mobile SDK: API base presets, "New sandbox
 * session", the test card list and the sheet.
 *
 * "New sandbox session" calls `POST /api/dev/mobile-session` (CONTRACT.md section 7),
 * a sandbox-only helper present when the backend runs with `MOBILE_SDK_DEV_WORKSPACE_ID`,
 * so no secret key lives in the app. It answers `201 { slug, url, sandbox, expires_at }`.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge + adjustResize (manifest) so Compose receives the IME insets and
        // the sheet's fields move up together with the keyboard animation.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // testTagsAsResourceId: Maestro can address the testTags below as ids.
                Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) { ExampleScreen() }
            }
        }
    }
}

private val presets = listOf(
    "Staging" to "https://staging.paysio.com",
    "Production" to "https://api.paysio.com",
    "Emulator" to "http://10.0.2.2:3000",
)

private val testCards = listOf(
    "4111 1111 1111 1111  12/30  123" to "Plain purchase (sandbox ACS challenges it when 3DS is on)",
    "4147 4630 1111 0134  12/30  123" to "3DS frictionless",
    "4016 3600 0000 0493  12/30  123" to "3DS challenge (approve / deny)",
    "4111 1111 1111 1112" to "Invalid number (Luhn)",
)

@Composable
private fun ExampleScreen() {
    var apiBase by remember { mutableStateOf(presets.first().second) }
    var slug by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var dark by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    val events = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    if (showSheet) {
        PaysioPaymentSheet(
            sessionSlug = slug,
            apiBase = apiBase,
            theme = if (dark) PaysioTheme.Dark else PaysioTheme.Light,
            customer = PaysioCustomer(email = "buyer@example.com", firstName = "Test", lastName = "Buyer"),
            billingAddress = PaysioAddress("1 Main St", null, "Austin", "TX", "78701", "US"),
            onEvent = { events.add(it.toString()) },
            onSuccess = { result ->
                status = "Paid: ${result.orderLabel ?: result.transactionId} (${result.status.wire})"
                showSheet = false
            },
            onError = { error -> status = "Error ${error.code.wire}: ${error.message}" },
            onCancel = { showSheet = false },
            // The sheet pads for the IME itself; keep it clear of the status and nav bars.
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
        )
        BackHandler { showSheet = false }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            // Status bar, nav bar, cutouts and the keyboard, so nothing draws under them
            // and "Open payment sheet" scrolls above the keyboard.
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Paysio SDK", style = MaterialTheme.typography.headlineSmall)
        // The last result, same format as the iOS example's ContentView; stays visible after the sheet closes.
        if (status.isNotEmpty()) {
            Text(status, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("result").semantics { contentDescription = status })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { (label, base) ->
                FilterChip(selected = apiBase == base, onClick = { apiBase = base }, label = { Text(label) },
                    modifier = Modifier.testTag("preset-${label.lowercase()}"))
            }
        }
        OutlinedTextField(
            value = slug,
            onValueChange = { slug = it },
            label = { Text("Session slug (api-...)") },
            modifier = Modifier.fillMaxWidth().testTag("slug").semantics { contentDescription = "Session slug" },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )
        Button(onClick = {
            focusManager.clearFocus()
            scope.launch {
                status = "Creating session..."
                status = newSession(apiBase)?.let { slug = it.first; "Session ${it.first} (expires ${it.second})" }
                    ?: "Session helper unavailable. Paste a slug instead."
            }
        }, modifier = Modifier.testTag("new-session")) { Text("New sandbox session") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dark theme", Modifier.weight(1f))
            Switch(checked = dark, onCheckedChange = { dark = it },
                modifier = Modifier.testTag("dark-theme").semantics { contentDescription = "Dark theme" })
        }

        Text("Test cards (sandbox)", style = MaterialTheme.typography.titleMedium)
        testCards.forEach { (card, note) ->
            Column {
                Text(card, fontFamily = FontFamily.Monospace)
                Text(note, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("Bank (NMI sandbox): routing 490000018, account 24413815, Checking, Personal",
            style = MaterialTheme.typography.bodySmall)

        Button(
            onClick = { focusManager.clearFocus(); showSheet = true },
            enabled = slug.isNotBlank(),
            modifier = Modifier.fillMaxWidth().testTag("open-sheet"),
        ) {
            Text("Open payment sheet")
        }
        events.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

/** Returns (slug, expires_at) or null. */
private suspend fun newSession(apiBase: String): Pair<String, String>? = withContext(Dispatchers.IO) {
    try {
        val body = """{"amount":1999,"currency":"USD","name":"Mobile SDK test"}"""
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url("${apiBase.trimEnd('/')}/api/dev/mobile-session").post(body).build()
        OkHttpClient().newCall(request).execute().use { response ->
            if (response.code !in 200..201) return@withContext null
            val json = JSONObject(response.body?.string() ?: return@withContext null)
            Pair(json.getString("slug"), json.optString("expires_at", ""))
        }
    } catch (_: Exception) {
        null
    }
}
