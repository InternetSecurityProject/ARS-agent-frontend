package kr.ac.suwon.arsagent.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kr.ac.suwon.arsagent.data.api.SessionAction
import kr.ac.suwon.arsagent.data.api.UserRequest
import kr.ac.suwon.arsagent.model.SecureInputDraft

/** Opt-in callback only. The screen does not own a client, credentials or a server session. */
data class SecureInputInput(
    val canSend: Boolean,
    val onSubmit: (SessionAction.SecureInput) -> Unit,
    val retryVersion: Int = 0,
)

@Stable
internal class ServerSecureForm(request: UserRequest) {
    private val draft = SecureInputDraft(request)
    val deadline = draft.deadline
    var count by mutableIntStateOf(0)
        private set
    var expired by mutableStateOf(!draft.isCurrent(Instant.now()))
        private set
    var stopped by mutableStateOf(false)
        private set
    var foreground by mutableStateOf(false)
        private set

    fun usable(): Boolean = !expired && !stopped && foreground && draft.isCurrent(Instant.now())
    fun accepts(key: Char): Boolean = draft.accepts(key)
    fun append(key: Char) { if (usable()) { draft.append(key, Instant.now()); count = draft.count } }
    fun delete() { if (usable()) { draft.delete(); count = draft.count } }
    fun clear() { draft.clear(); count = 0 }
    fun expire() { expired = true; clear() }
    fun stop() { stopped = true; clear() }
    fun updateForeground(active: Boolean) { foreground = active; if (!active) clear() }
    fun submit(): SessionAction.SecureInput? {
        if (!usable()) { clear(); return null }
        val action = draft.submit(Instant.now())
        count = 0
        return action
    }
    override fun toString() = "ServerSecureForm([redacted])"
}

/** Lives above responsive branches. Raw digits intentionally do not use rememberSaveable. */
@Composable
internal fun rememberServerSecureForm(request: UserRequest?, retryVersion: Int, canSend: Boolean): ServerSecureForm? {
    val form = remember(request, retryVersion) { request?.let { ServerSecureForm(it) } }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(form, owner) {
        form?.updateForeground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, _ ->
            form?.updateForeground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); form?.clear() }
    }
    LaunchedEffect(form, canSend) { if (!canSend) form?.clear() }
    LaunchedEffect(form) {
        if (form != null) {
            val remaining = form.deadline?.let { runCatching { Duration.between(Instant.now(), it).toMillis() }.getOrDefault(Long.MAX_VALUE) } ?: 0L
            if (remaining > 0) delay(remaining)
            form.expire()
        }
    }
    return form
}

internal data class ServerSecureContent(
    val request: UserRequest,
    val form: ServerSecureForm,
    val ready: Boolean,
    val submitted: Boolean,
)

@Composable
internal fun ServerSecureKeypad(content: ServerSecureContent, modifier: Modifier, scrollable: Boolean = true,
    extraContent: @Composable () -> Unit = {}) {
    val (request, form, ready, submitted) = content
    val scroll = rememberScrollState()
    Column(modifier.fillMaxWidth().then(if (scrollable) Modifier.verticalScroll(scroll) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(request.label?.takeIf { it.isNotBlank() } ?: "개인정보 입력", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        val prompt = request.message.takeIf { it.isNotBlank() } ?: request.arsPrompt.orEmpty()
        if (prompt.isNotBlank() && prompt != request.label) Text(prompt, textAlign = TextAlign.Center)
        val note = when {
            form.expired -> "입력 시간이 지났어요. 새 안내를 기다려 주세요."
            form.stopped -> "입력을 중단했어요."
            submitted -> "입력을 전달했어요. 응답을 기다려 주세요."
            !ready -> "입력 기능 연결 전이거나 연결을 확인하고 있어요."
            else -> null // The fixed completion button already explains the next action.
        }
        if (note != null) Text(note, color = ArsColors.Secondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        extraContent()
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${form.count}/20", color = ArsColors.Secondary, style = MaterialTheme.typography.bodyMedium)
            Text("●".repeat(form.count), fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(horizontal = 8.dp).testTag("secure-mask").semantics {
                    password(); contentDescription = "개인정보 ${form.count}자리 입력됨"
                })
            TextButton({ form.delete() }, Modifier.semantics { contentDescription = "입력 한 자리 지우기" },
                enabled = ready && form.count > 0) { Text("⌫") }
        }
        PhoneKeypad({ form.append(it) }, Modifier.testTag("server-secure-keypad"), compact = true,
            enabled = ready, keyEnabled = { form.count < 20 && form.accepts(it) }, rowSpacing = 12.dp)
        Spacer(Modifier.height(8.dp))
    }
}
