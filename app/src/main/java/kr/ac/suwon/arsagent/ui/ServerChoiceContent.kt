package kr.ac.suwon.arsagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kr.ac.suwon.arsagent.data.api.SessionAction
import kr.ac.suwon.arsagent.data.api.UserRequest
import kr.ac.suwon.arsagent.model.CandidateChoice

/** Owner supplies connection readiness. Merely rendering this object never calls an API.
 * Increment retryVersion only after the owner has confirmed that the previous action was NOT accepted.
 */
data class CandidateChoiceInput(
    val canSend: Boolean,
    val onSelect: (SessionAction.Select) -> Unit,
    val retryVersion: Int = 0,
)

@Composable
internal fun ServerChoiceContent(request: UserRequest, input: CandidateChoiceInput?, submitted: Boolean) {
    val choice = remember(request) { CandidateChoice(request) }
    var expired by remember(request.requestId, request.expiresAt) { mutableStateOf(choice.deadline?.isAfter(Instant.now()) != true) }
    LaunchedEffect(request.requestId, request.expiresAt) {
        val remaining = choice.deadline?.let { deadline ->
            runCatching { Duration.between(Instant.now(), deadline).toMillis() }.getOrDefault(Long.MAX_VALUE)
        } ?: 0L
        if (remaining > 0) delay(remaining)
        expired = true
    }
    Text(request.message)
    if (request.reason == "critical_action") {
        Text("중요한 선택이에요. 버튼으로 선택해 주세요.", color = ArsColors.Secondary,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalTextStyle.current.fontFamily))
    }
    val note = when {
        choice.options.isEmpty() -> "선택 가능한 메뉴가 없어요."
        expired -> "선택 시간이 지났어요. 새 안내를 기다려 주세요."
        submitted -> "선택을 전달했어요. 응답을 기다려 주세요."
        input == null -> "선택 기능 연결 전이에요."
        !input.canSend -> "연결 상태를 확인하고 있어요."
        else -> "시간 내 선택해 주세요."
    }
    Text(note, color = ArsColors.Secondary,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalTextStyle.current.fontFamily))
    choice.options.forEach { option ->
        val enabled = input?.canSend == true && !expired && !submitted && choice.select(option.key, Instant.now()) != null
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).background(ArsColors.Neutral, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button) {
                // Check again at tap time: a request can expire between drawing and interacting.
                if (input?.canSend == true && !submitted && !expired) {
                    val action = choice.select(option.key, Instant.now())
                    if (action != null) {
                        input.onSelect(action)
                    } else expired = choice.deadline?.isAfter(Instant.now()) != true
                }
            }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(option.label.ifBlank { option.key }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(12.dp))
            Text(if (option.key.all { it.isDigit() }) "${option.key}번" else option.key,
                color = ArsColors.Blue, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
