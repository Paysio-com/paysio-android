package com.paysio.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paysio.sdk.core.PaysioAddressSuggest
import com.paysio.sdk.core.PaysioAddressSuggestion
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * Street address suggestions state, the twin of the RN SDK's `useAddressSuggestions`
 * (and the hosted checkout's AddressAutocomplete): 250 ms debounce, 3 character
 * minimum, a 100-entry cache, and one Places session token per address entry (reused
 * from the first keystroke until a suggestion is picked, which Google bills as a
 * single session). Failures simply yield no suggestions.
 */
class PaysioAddressSuggestionsState internal constructor(private val apiBase: String?) {
    var suggestions: List<PaysioAddressSuggestion> by mutableStateOf(emptyList())
        internal set
    var loading: Boolean by mutableStateOf(false)
        internal set
    internal var sessionToken: String = UUID.randomUUID().toString()
    internal val cache = object : LinkedHashMap<String, List<PaysioAddressSuggestion>>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<PaysioAddressSuggestion>>?): Boolean = size > 100
    }

    /** Resolves the picked place (falls back to the suggestion itself), then starts a fresh session. */
    suspend fun resolve(s: PaysioAddressSuggestion): PaysioAddressSuggestion {
        val token = sessionToken
        sessionToken = UUID.randomUUID().toString()
        suggestions = emptyList()
        val base = apiBase ?: return s
        return PaysioAddressSuggest.details(base, s.id, token) ?: s
    }
}

@Composable
fun rememberPaysioAddressSuggestions(apiBase: String?, query: String, country: String, enabled: Boolean): PaysioAddressSuggestionsState {
    val state = remember(apiBase) { PaysioAddressSuggestionsState(apiBase) }
    LaunchedEffect(state, query, country, enabled) {
        val q = query.trim()
        if (apiBase == null || !enabled || q.length < PaysioAddressSuggest.MIN_QUERY) {
            state.suggestions = emptyList()
            state.loading = false
            return@LaunchedEffect
        }
        val key = "$country|${q.lowercase()}"
        state.cache[key]?.let {
            state.suggestions = it
            state.loading = false
            return@LaunchedEffect
        }
        state.loading = true
        // Debounce: a newer keystroke cancels this effect before the request goes out.
        delay(PaysioAddressSuggest.DEBOUNCE_MS)
        val next = PaysioAddressSuggest.suggest(apiBase, q, country, state.sessionToken)
        state.cache[key] = next
        state.suggestions = next
        state.loading = false
    }
    return state
}

/**
 * The suggestion list (RN AddressSuggestionList): inline under the street field (it
 * pushes the rows below it down rather than floating), `card` background, 1 dp
 * `border`, radius 12, padding 4, the menu shadow; up to 5 rows of map-pin 14 dp +
 * the label with the typed text in semibold. No "Powered by Google" row.
 */
@Composable
fun PaysioAddressSuggestionList(
    suggestions: List<PaysioAddressSuggestion>,
    query: String,
    onPick: (PaysioAddressSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    val theme = LocalPaysioTheme.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .shadow(6.dp, shape, clip = false, ambientColor = Color(0x291A1A1A), spotColor = Color(0x291A1A1A))
            .clip(shape)
            .background(theme.colors.card)
            .border(1.dp, theme.colors.border, shape)
            .padding(4.dp)
            .semantics { contentDescription = "Address suggestions" },
    ) {
        suggestions.take(5).forEach { s ->
            val interaction = remember(s.id) { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (pressed) theme.colors.rowHover else Color.Transparent)
                    .clickable(interactionSource = interaction, indication = null) { onPick(s) }
                    .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = s.label }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PaysioIcon(PaysioIconName.MAP_PIN_SOLID, 14.dp, theme.colors.mutedForeground)
                val q = query.trim()
                val idx = if (q.isEmpty()) -1 else s.label.lowercase().indexOf(q.lowercase())
                val text = buildAnnotatedString {
                    if (idx < 0) {
                        append(s.label)
                    } else {
                        append(s.label.substring(0, idx))
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(s.label.substring(idx, idx + q.length)) }
                        append(s.label.substring(idx + q.length))
                    }
                }
                Text(text, style = theme.font(14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}
