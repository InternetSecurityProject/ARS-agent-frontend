package kr.ac.suwon.arsagent.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/** A visual clock only: never advances call state or repeatedly announces decorative dots. */
@Composable
internal fun ListeningProgressTitle(running: Boolean) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = running && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val dots = if (active) {
        val transition = rememberInfiniteTransition(label = "listening")
        val phase = transition.animateFloat(0f, 4f,
            infiniteRepeatable(tween(2_000, easing = LinearEasing)), label = "dots")
        val visible by remember { derivedStateOf { phase.value.toInt().coerceIn(0, 3) } }
        visible
    } else 0
    val ink = MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth()) {
        Text("ARS 안내를 듣는 중", Modifier.weight(1f, fill = false).alignByBaseline(),
            style = MaterialTheme.typography.titleLarge)
        // Always lay out all three glyphs; only their color changes, so the title never shifts.
        Text(buildAnnotatedString {
            repeat(3) { index ->
                withStyle(SpanStyle(color = if (active && index < dots) ink else Color.Transparent)) { append('.') }
            }
        }, Modifier.alignByBaseline().padding(start = 4.dp).testTag("listening-dots").clearAndSetSemantics {},
            style = MaterialTheme.typography.titleLarge)
    }
}
