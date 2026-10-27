package kr.ac.suwon.arsagent.livecall

import java.io.Closeable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kr.ac.suwon.arsagent.model.PhoneNumber
import kr.ac.suwon.arsagent.servertest.*

data class LiveConsent(val number: String, val goal: String) {
    override fun toString() = "LiveConsent([redacted])"
}

data class LiveEntry(val number: String, val generation: Long, val goal: String = "",
    val confirmation: LiveConsent? = null, val elapsedSeconds: Int? = null) {
    override fun toString() = "LiveEntry(generation=$generation, content=[redacted])"
}

/** Main-thread session owner. UI/debounce can prepare consent but can never create a call. */
class LiveCallOwner(scope: CoroutineScope,
    factory: (String) -> ServerTestGateway = { HttpServerTestGateway(it) },
    private val monotonicSeconds: () -> Long = { System.nanoTime() / 1_000_000_000 },
) : Closeable {
    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val work = CoroutineScope(scope.coroutineContext + job)
    val server = ServerTestController(work, factory)
    private val mutable = MutableStateFlow<LiveEntry?>(null)
    val entry = mutable.asStateFlow()
    private var generation = 0L
    private var connectedAt: Long? = null
    private var closed = false
    private val replies = mutableSetOf<String>()

    init {
        work.launch {
            server.state.collectLatest { state ->
                if (entry.value == null) return@collectLatest
                if (state.sessionUnavailable) {
                    mutable.update { it?.copy(elapsedSeconds = null) }
                    return@collectLatest
                }
                val finalSeconds = state.result?.durationSec?.coerceAtLeast(0)
                if (finalSeconds != null) {
                    mutable.update { it?.copy(elapsedSeconds = finalSeconds) }
                    return@collectLatest
                }
                if (state.hasSession && state.snapshot.status !in setOf("idle", "dialing", "ended") && connectedAt == null)
                    connectedAt = monotonicSeconds()
                val start = connectedAt ?: return@collectLatest
                do {
                    val seconds = (monotonicSeconds() - start).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
                    mutable.update { it?.copy(elapsedSeconds = seconds) }
                    if (!state.active) break
                    delay(1_000)
                } while (currentCoroutineContext().isActive)
            }
        }
    }

    fun begin(number: String): Boolean {
        val phone = PhoneNumber(number)
        if (closed || entry.value != null || !phone.canUseAgent || server.state.value.hasSession || server.state.value.creationUncertain) return false
        connectedAt = null
        replies.clear()
        mutable.value = LiveEntry(phone.digits, ++generation)
        return true
    }

    private fun canPrepare(): Boolean {
        val state = server.state.value
        return !closed && entry.value != null && !state.hasSession && !state.starting && !state.checking && !state.creationUncertain
    }

    fun editGoal(text: String) {
        if (canPrepare()) mutable.update { it?.copy(goal = text.take(500), confirmation = null) }
    }

    fun prepare() {
        if (!canPrepare()) return
        mutable.update { current ->
            current?.takeIf { it.goal.isNotBlank() }?.let {
                it.copy(confirmation = LiveConsent(it.number, it.goal.trim()))
            } ?: current
        }
    }

    fun dismissConfirmation() { mutable.update { it?.copy(confirmation = null) } }

    fun confirm(key: String): Boolean {
        if (!canPrepare() || key.isBlank() || key.any { it.code !in 32..126 }) return false
        val current = entry.value ?: return false
        val target = current.confirmation ?: return false
        if (target.number != current.number || target.goal != current.goal.trim()) return false
        mutable.value = current.copy(confirmation = null)
        server.start(key, target.number, target.goal, ServerTestMode.RealCall)
        return true
    }

    /** All response sources (speech, candidate and text/keypad) share one request receipt. */
    fun canRespond(requestId: String): Boolean {
        val state = server.state.value
        val request = state.snapshot.pendingRequest ?: return false
        return !closed && requestId.isNotBlank() && state.canSend && state.snapshot.status == "awaiting_user" &&
            request.requestId == requestId && requestId !in replies && server.requestCurrent(request)
    }

    fun dispatch(command: ServerTestCommand) {
        if (closed || entry.value == null) return
        if (command is ServerTestCommand.Respond) {
            val state = server.state.value
            val request = state.snapshot.pendingRequest ?: return
            if (!canRespond(command.requestId)) return
            val valid = when (request.kind) {
                "clarification" -> if (command.selection)
                    request.options.count { it.key == command.value } == 1
                    else request.reason != "critical_action" && command.value.isNotBlank()
                "secure_input" -> !command.selection && command.value.matches(Regex("[0-9*#]{1,20}"))
                else -> false
            }
            if (!valid) return
            replies += request.requestId
            server.dispatch(command)
        } else when (command) {
            is ServerTestCommand.Keys, ServerTestCommand.Cancel, ServerTestCommand.Reconnect,
            ServerTestCommand.FetchResult -> server.dispatch(command)
            else -> Unit // Confirmation is the only route to Start; UI cannot bypass it.
        }
    }

    fun cancelPurpose(): Boolean {
        if (!canPrepare()) return false
        server.reset()
        connectedAt = null
        mutable.value = null
        return true
    }

    fun returnAfterEnd(): Boolean {
        val state = server.state.value
        if (closed || !state.hasSession || state.active || state.busy || state.starting || state.creationUncertain) return false
        server.reset()
        connectedAt = null
        mutable.value = null
        return true
    }

    /** Explicit UI-only escape. Never send Cancel or claim the telephone call has ended. */
    fun returnAfterUnavailable(): Boolean {
        val state = server.state.value
        if (closed || !state.sessionUnavailable || state.busy || state.starting) return false
        server.reset()
        connectedAt = null
        mutable.value = null
        replies.clear()
        return true
    }

    override fun close() {
        if (closed) return
        closed = true
        server.close() // Release transport, never implicitly send a remote cancellation.
        job.cancel()
        mutable.value = null
        connectedAt = null
        replies.clear()
    }
}
