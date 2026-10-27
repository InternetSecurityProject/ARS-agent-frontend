package kr.ac.suwon.arsagent.servertest.voice

import java.io.Closeable
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.ac.suwon.arsagent.data.api.UserRequest
import kr.ac.suwon.arsagent.servertest.*

interface SpeechDevice : Closeable {
    fun speak(text: String, onDone: () -> Unit)
    fun listen(onResult: (String) -> Unit, onError: (String) -> Unit)
    fun stop()
}

data class VoiceStatus(val enabled: Boolean = false, val listening: Boolean = false,
    val speaking: Boolean = false, val error: String? = null)

/** Main-thread owner. Tokens invalidate callbacks even when the platform delivers them after cancel(). */
class VoiceCoordinator(private val device: SpeechDevice,
    private val dispatch: (ServerTestCommand) -> Unit, private val now: () -> Instant = Instant::now,
    private val suppressArs: (Boolean) -> Unit = {},
    private val latestState: (() -> ServerTestState)? = null) : Closeable {
    private val mutable = MutableStateFlow(VoiceStatus())
    val state = mutable.asStateFlow()
    private var current = ServerTestState()
    private var generation = 0L
    private var closed = false
    private var visible = true
    private var sessionId: String? = null
    private var lastAnnouncement: Any? = null
    private val submitted = mutableSetOf<String>()

    fun sessionChanged(id: String?) {
        if (sessionId == id) return
        sessionId = id; submitted.clear(); lastAnnouncement = null; stopTurn()
    }

    fun enable(enabled: Boolean) {
        if (closed) return
        stopTurn(); mutable.value = VoiceStatus(enabled = enabled && visible)
        if (enabled) { lastAnnouncement = null; announce() }
    }

    fun update(value: ServerTestState) {
        if (closed) return
        val old = current
        current = value
        val identityChanged = old.snapshot.pendingRequest != value.snapshot.pendingRequest ||
            old.snapshot.status != value.snapshot.status || old.hasSession != value.hasSession
        if (identityChanged || !value.canSend && old.canSend || value.starting || value.checking ||
            value.creationUncertain || mutable.value.listening && value.hasSession && !validAnswer(value.snapshot.pendingRequest)) stopTurn()
        if (!value.hasSession && old.hasSession) { submitted.clear(); lastAnnouncement = null }
        announce()
    }

    private fun authoritative() = latestState?.invoke() ?: current
    private fun validAnswer(request: UserRequest?): Boolean {
        val live = authoritative()
        return request != null && live.snapshot.pendingRequest == request &&
        live.canSend && live.snapshot.status == "awaiting_user" &&
        request.kind == "clarification" && request.reason != "critical_action" && request.requestId.isNotBlank() &&
        request.requestId !in submitted && runCatching { Instant.parse(request.expiresAt).isAfter(now()) }.getOrDefault(false)
    }

    private fun announce() {
        if (!mutable.value.enabled || !visible || !current.hasSession) return
        val request = current.snapshot.pendingRequest
        val key: Any = request ?:
            "status:${current.snapshot.status}:${current.snapshot.summary?.summary}:${current.result?.summary}"
        if (lastAnnouncement == key) return
        // Never read raw ARS captions or secure prompts/read-back digits aloud.
        val text = when {
            request?.kind == "secure_input" -> "개인정보는 화면의 키패드로 입력해 주세요."
            request?.reason == "critical_action" -> "중요한 선택이에요. 화면의 버튼으로 선택해 주세요."
            request != null && validAnswer(request) -> request.message
            request != null -> return
            current.snapshot.status == "dialing" -> "기관에 전화를 걸고 있어요."
            current.snapshot.status == "listening" -> "ARS 안내를 확인하고 있어요."
            current.snapshot.status == "selecting" -> "목적에 맞는 메뉴를 선택하고 있어요."
            current.snapshot.status == "listening_result" -> "안내 결과를 확인하고 있어요."
            current.snapshot.status == "waiting_agent" -> "상담원 연결 대기 상태예요. 이 앱은 상담원 통화를 지원하지 않아요."
            current.snapshot.status == "connected_to_agent" -> "상담원이 연결됐어요. 이 앱은 상담원 통화를 지원하지 않아요."
            current.snapshot.status == "ended" -> current.result?.summary ?: current.snapshot.summary?.summary ?: "통화가 종료됐어요."
            current.snapshot.status == "agent_done" -> current.snapshot.summary?.summary ?: "Agent가 업무를 마쳤어요."
            else -> return
        }.replace(Regex("[0-9][0-9 -]{5,}[0-9]"), "입력 정보").take(600)
        lastAnnouncement = key
        stopTurn()
        val token = generation
        mutable.value = mutable.value.copy(speaking = true, error = null)
        suppressArs(true)
        device.speak(text) {
            if (token == generation && !closed && visible && mutable.value.enabled) {
                mutable.value = mutable.value.copy(speaking = false)
                suppressArs(false)
                if (request != null && current.snapshot.pendingRequest == request && validAnswer(request)) listenAnswer()
            }
        }
    }

    fun listenGoal(onResult: (String) -> Unit) {
        fun canFill(): Boolean { val live = authoritative(); return !live.hasSession && !live.starting && !live.checking && !live.creationUncertain }
        if (closed || !visible || !mutable.value.enabled || !canFill()) return
        recognize { if (canFill()) onResult(it) }
    }

    fun listenAnswer() {
        val request = current.snapshot.pendingRequest
        if (closed || !visible || !mutable.value.enabled || !validAnswer(request)) return
        val bound = requireNotNull(request)
        recognize { answer ->
            if (current.snapshot.pendingRequest == bound && validAnswer(bound)) {
                submitted.add(bound.requestId)
                dispatch(ServerTestCommand.Respond(bound.requestId, answer))
            } else mutable.value = mutable.value.copy(error = "요청이 바뀌었거나 기한이 지났어요. 화면을 확인해 주세요.")
        }
    }

    private fun recognize(onResult: (String) -> Unit) {
        stopTurn(); val token = generation
        mutable.value = mutable.value.copy(listening = true, error = null)
        suppressArs(true)
        device.listen({ text ->
            if (token == generation && !closed && visible && mutable.value.enabled) {
                generation++ // consume once before invoking owner
                mutable.value = mutable.value.copy(listening = false)
                suppressArs(false)
                val value = text.trim().take(500)
                if (value.isNotEmpty()) onResult(value)
                else mutable.value = mutable.value.copy(error = "음성을 인식하지 못했어요. 다시 말하거나 글로 입력해 주세요.")
            }
        }, { message ->
            if (token == generation && !closed) {
                generation++; mutable.value = mutable.value.copy(listening = false, error = message); suppressArs(false)
            }
        })
    }

    fun foreground(value: Boolean) {
        visible = value
        if (!value) { stopTurn(); mutable.value = VoiceStatus() }
    }

    fun cancelInput() = stopTurn()
    fun deviceError(message: String) {
        if (!closed) { stopTurn(); mutable.value = mutable.value.copy(error = message) }
    }
    private fun stopTurn() {
        generation++; device.stop()
        mutable.value = mutable.value.copy(listening = false, speaking = false)
        suppressArs(false)
    }
    override fun close() {
        if (closed) return
        closed = true; stopTurn(); mutable.value = VoiceStatus(); submitted.clear(); device.close()
    }
}
