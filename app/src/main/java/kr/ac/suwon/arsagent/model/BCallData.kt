package kr.ac.suwon.arsagent.model

import kr.ac.suwon.arsagent.data.api.*

/** A display-only projection. Session ownership, credentials and events stay outside the UI. */
data class BCallData(
    val status: String,
    val title: String,
    val message: String,
    val goal: String?,
    val path: List<String>,
    val caption: String?,
    val summary: String?,
    val keyInfo: List<KeyInfo>,
    val handoffScript: String?,
    val pendingRequest: UserRequest?,
    val endReason: String?,
    val isEnded: Boolean,
    val durationSeconds: Int?,
) {
    companion object {
        /** Pass the latest snapshot every time; reconnect snapshots replace rather than append. */
        fun from(snapshot: SessionSnapshot, result: CompletedResult? = null): BCallData {
            val ended = result != null || snapshot.status == "ended"
            val status = if (ended) "ended" else snapshot.status
            val title = when (status) {
                "dialing" -> "기관에 연결 중"
                "listening" -> "ARS 안내를 듣는 중"
                "selecting" -> "메뉴 선택 중"
                "listening_result" -> "기관 안내"
                "agent_done" -> "안내 결과"
                "awaiting_user" -> "입력이 필요해요"
                "waiting_agent" -> "상담원 연결 대기"
                "connected_to_agent" -> "상담원 연결"
                "ended" -> "통화 종료"
                else -> "진행 상태 확인 중"
            }
            val steps = if (result != null) result.path else snapshot.path
            return BCallData(
                status = status,
                title = title,
                message = if (result != null) "서버에서 통화 종료를 확인했어요." else snapshot.message,
                goal = snapshot.goalSummary?.takeIf { it.isNotBlank() } ?: snapshot.goal?.takeIf { it.isNotBlank() },
                path = steps.sortedBy { it.step }.map {
                    val key = it.selected.key
                    val suffix = if (key.isNotEmpty() && key.all { digit -> digit in '0'..'9' }) "번" else ""
                    "${it.selected.label} ($key$suffix)"
                },
                caption = if (result != null) null else snapshot.captions.lastOrNull { it.speaker == "ars" }?.text,
                // An explicit null/empty final result must never resurrect an old success summary.
                summary = if (result != null) result.summary else snapshot.summary?.summary,
                keyInfo = if (result != null) result.keyInfo else snapshot.summary?.keyInfo.orEmpty(),
                handoffScript = if (result != null) result.handoffScript else snapshot.summary?.handoffScript,
                pendingRequest = if (ended) null else snapshot.pendingRequest,
                endReason = if (result != null) result.endReason else snapshot.endReason,
                isEnded = ended,
                durationSeconds = result?.durationSec?.takeIf { it >= 0 },
            )
        }
    }
}
