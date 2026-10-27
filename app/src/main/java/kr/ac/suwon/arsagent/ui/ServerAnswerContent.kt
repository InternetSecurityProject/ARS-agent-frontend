package kr.ac.suwon.arsagent.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kr.ac.suwon.arsagent.data.api.SessionAction
import kr.ac.suwon.arsagent.data.api.UserRequest

data class ServerAnswerInput(val canSend: Boolean, val onAnswer: (SessionAction.Answer) -> Unit,
    val onMic: () -> Unit, val canUseMic: Boolean = true)

@Composable
internal fun ServerAnswerContent(request: UserRequest, input: ServerAnswerInput) {
    if (request.kind != "clarification" || request.reason == "critical_action" || request.requestId.isBlank()) return
    var value by remember(request) { mutableStateOf("") }
    val expiry = remember(request) { runCatching { Instant.parse(request.expiresAt) }.getOrNull() }
    var expired by remember(request) { mutableStateOf(expiry?.isAfter(Instant.now()) != true) }
    LaunchedEffect(request) {
        val remaining = expiry?.let { Duration.between(Instant.now(), it).toMillis() } ?: 0L
        if (remaining > 0) delay(remaining)
        expired = true; value = ""
    }
    LaunchedEffect(input.canSend) { if (!input.canSend) value = "" }
    val enabled = input.canSend && !expired
    VoiceInput(value, "답변을 말하거나 입력해 주세요", { value = it.take(500) },
        input.onMic, microphoneDescription = "말로 답하기", microphoneEnabled = enabled && input.canUseMic,
        inputEnabled = enabled)
    Button({
        if (enabled && expiry?.isAfter(Instant.now()) == true && value.isNotBlank()) {
            val answer = SessionAction.Answer(request.requestId, value.trim())
            value = ""; input.onAnswer(answer)
        }
    }, enabled = enabled && value.isNotBlank()) { Text("답변 보내기") }
}
