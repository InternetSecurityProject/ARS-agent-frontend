package kr.ac.suwon.arsagent.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import kr.ac.suwon.arsagent.model.BCallData

/** Reuses the existing progress card; content comes only from the supplied server presentation. */
@Composable
internal fun BServerContent(data: BCallData, choiceInput: CandidateChoiceInput? = null, choiceSubmitted: Boolean = false) {
    val pending = data.pendingRequest
    if (!data.isEnded) Text(data.title, style = MaterialTheme.typography.titleLarge)
    if (data.title == "진행 상태 확인 중") {
        Text("서버 상태 · ${data.status}", color = ArsColors.Secondary, style = MaterialTheme.typography.bodyMedium)
    }
    if (pending != null) {
        if (pending.kind == "clarification") {
            ServerChoiceContent(pending, choiceInput?.copy(canSend = choiceInput.canSend && data.status == "awaiting_user"), choiceSubmitted)
            return
        }
        Text(pending.message)
        Text("입력 화면 연결 전이에요.", color = ArsColors.Secondary, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val hasSummary = !data.summary.isNullOrBlank()
    if (hasSummary) Text(data.summary.orEmpty(), style = MaterialTheme.typography.titleLarge)
    data.keyInfo.forEach { Text("${it.label} · ${it.value}") }
    data.handoffScript?.takeIf { it.isNotBlank() }?.let { Text(it) }
    if (!hasSummary) {
        if (data.status == "dialing") data.goal?.let { Text(it) }
        if (data.path.isNotEmpty()) {
            Text("이동 경로", style = MaterialTheme.typography.labelMedium, color = ArsColors.Blue)
            Text(data.path.joinToString(" → "), fontSize = 17.sp, lineHeight = 28.sp)
        }
        if (!data.caption.isNullOrBlank()) {
            Text("ARS 안내", style = MaterialTheme.typography.labelMedium, color = ArsColors.Blue)
            Text(data.caption, fontSize = 17.sp, lineHeight = 28.sp)
        }
    }
    val needsMessage = if (hasSummary) !data.isEnded else data.path.isEmpty() && data.caption.isNullOrBlank()
    if (needsMessage && data.message.isNotBlank() && data.message != data.summary) Text(data.message)
}
