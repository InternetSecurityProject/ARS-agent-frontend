package kr.ac.suwon.arsagent.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal val apiJson = Json { ignoreUnknownKeys = true; encodeDefaults = false; classDiscriminator = "type" }

@Serializable
data class CreateSessionRequest(@SerialName("phone_number") val phoneNumber: String, val goal: String)

@Serializable
class CreatedSession(
    @SerialName("session_id") val sessionId: String,
    @SerialName("session_token") val sessionToken: String,
    @SerialName("events_url") val eventsUrl: String,
    @SerialName("audio_url") val audioUrl: String,
    @SerialName("phone_number") val phoneNumber: String,
    val status: String,
    @SerialName("goal_summary") val goalSummary: String,
) {
    override fun toString() = "CreatedSession(sessionId=$sessionId, credentials=[redacted])"
}

@Serializable
sealed interface SessionAction {
    @Serializable @SerialName("answer")
    data class Answer(@SerialName("request_id") val requestId: String, val text: String) : SessionAction
    @Serializable @SerialName("select")
    data class Select(@SerialName("request_id") val requestId: String, val key: String) : SessionAction
    @Serializable @SerialName("key")
    data class Key(val key: String) : SessionAction
    @Serializable @SerialName("secure_input")
    class SecureInput(@SerialName("request_id") val requestId: String, val digits: String) : SessionAction {
        override fun toString() = "SecureInput(requestId=$requestId, digits=[redacted])"
    }
    @Serializable @SerialName("revise_goal")
    data class ReviseGoal(val text: String) : SessionAction
    @Serializable @SerialName("cancel")
    data object Cancel : SessionAction
}

@Serializable
data class ActionResponse(val accepted: Boolean, @SerialName("key_sent") val keySent: String? = null,
    val status: String? = null, @SerialName("goal_summary") val goalSummary: String? = null)

@Serializable
data class MenuOption(val key: String, val label: String, val category: String? = null, val confidence: Double? = null)

@Serializable
data class PathStep(val step: Int, val text: String, val options: List<MenuOption> = emptyList(),
    val selected: MenuOption, val actor: String)

@Serializable
data class Caption(val step: Int, val speaker: String, val text: String, val at: String)

@Serializable
data class UserRequest(@SerialName("request_id") val requestId: String, val kind: String,
    val message: String, @SerialName("expires_at") val expiresAt: String,
    val reason: String? = null, val options: List<MenuOption> = emptyList(),
    val label: String? = null, @SerialName("ars_prompt") val arsPrompt: String? = null, val terminator: String? = null)

@Serializable
data class KeyInfo(val label: String, val value: String)

@Serializable
data class ResultSummary(val kind: String, val summary: String,
    @SerialName("key_info") val keyInfo: List<KeyInfo> = emptyList(),
    @SerialName("handoff_script") val handoffScript: String? = null)

@Serializable
data class SessionSnapshot(val status: String, val message: String,
    val goal: String? = null, @SerialName("goal_summary") val goalSummary: String? = null,
    val path: List<PathStep> = emptyList(), val captions: List<Caption> = emptyList(),
    @SerialName("pending_request") val pendingRequest: UserRequest? = null,
    val summary: ResultSummary? = null, @SerialName("end_reason") val endReason: String? = null)

@Serializable
data class StatusChange(val status: String, val message: String)

@Serializable
data class RejectedInput(val keys: String?, val actor: String, @SerialName("ars_prompt") val arsPrompt: String,
    @SerialName("retry_count") val retryCount: Int, val message: String)

@Serializable
data class EndNotice(@SerialName("end_reason") val endReason: String, val message: String, val detail: String? = null)

@Serializable
data class ErrorNotice(val code: String, val message: String)

@Serializable
data class CompletedResult(val outcome: String, @SerialName("end_reason") val endReason: String,
    val summary: String? = null, @SerialName("key_info") val keyInfo: List<KeyInfo> = emptyList(),
    @SerialName("handoff_script") val handoffScript: String? = null,
    val path: List<PathStep> = emptyList(), @SerialName("duration_sec") val durationSec: Int)

sealed interface SessionResult {
    data class InProgress(val status: String) : SessionResult
    data class Completed(val data: CompletedResult) : SessionResult
}

@Serializable
data class HealthResponse(val backend: String, val stt: String, val interpreter: String,
    @SerialName("mock_goals") val mockGoals: List<String> = emptyList(),
    @SerialName("active_session") val activeSession: String? = null)

sealed interface SessionEvent {
    val at: String
    data class Snapshot(override val at: String, val data: SessionSnapshot) : SessionEvent
    data class StatusChanged(override val at: String, val data: StatusChange) : SessionEvent
    data class CaptionAdded(override val at: String, val data: Caption) : SessionEvent
    data class PathUpdated(override val at: String, val data: PathStep) : SessionEvent
    data class InputRequested(override val at: String, val data: UserRequest) : SessionEvent
    data class InputRejected(override val at: String, val data: RejectedInput) : SessionEvent
    data class SummaryReceived(override val at: String, val data: ResultSummary) : SessionEvent
    data class Ended(override val at: String, val data: EndNotice) : SessionEvent
    data class ErrorReceived(override val at: String, val data: ErrorNotice) : SessionEvent
    data class Unknown(override val at: String, val name: String, val data: JsonObject) : SessionEvent
}
