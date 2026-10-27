package kr.ac.suwon.arsagent.servertest

import java.io.Closeable
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kr.ac.suwon.arsagent.data.api.*
import kr.ac.suwon.arsagent.model.PhoneNumber

class ServerTestController(
    scope: CoroutineScope,
    private val factory: (String) -> ServerTestGateway = { HttpServerTestGateway(it) },
    private val clock: () -> Instant = Instant::now,
) : Closeable {
    private val owner = SupervisorJob(scope.coroutineContext[Job])
    private val work = CoroutineScope(scope.coroutineContext + owner)
    private val mutable = MutableStateFlow(ServerTestState())
    val state = mutable.asStateFlow()
    // These credentials never enter observable/saved state or exception messages.
    private var api: ServerTestGateway? = null
    private var session: CreatedSession? = null
    private var stream: Job? = null
    private var closed = false

    fun dispatch(command: ServerTestCommand) = when (command) {
        ServerTestCommand.CheckConnection -> checkConnection()
        is ServerTestCommand.Start -> start(command.key, command.phone, command.goal, command.mode)
        is ServerTestCommand.Respond -> respond(command.requestId, command.value, command.selection)
        is ServerTestCommand.Keys -> sendKeys(command.keys)
        is ServerTestCommand.ReviseGoal -> reviseGoal(command.text)
        ServerTestCommand.Cancel -> cancel()
        ServerTestCommand.Reconnect -> reconnect()
        ServerTestCommand.FetchResult -> fetchResult()
        ServerTestCommand.Reset -> reset()
    }

    fun checkConnection() {
        if (closed || state.value.checking || state.value.starting || state.value.hasSession) return
        mutable.update { it.copy(checking = true, error = null) }
        work.launch {
            try {
                // health does not use authentication. This placeholder is never sent in a header.
                factory("health-check-only").use { probe ->
                    val health = probe.health()
                    mutable.update { it.copy(health = health, error = healthProblem(health)) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { fail(e) }
            finally { mutable.update { it.copy(checking = false) } }
        }
    }

    fun start(key: String, phone: String, goal: String, mode: ServerTestMode = ServerTestMode.Sim) {
        val current = state.value
        if (closed || current.starting || current.checking || current.hasSession || current.creationUncertain) return
        if (key.isBlank() || key.any { it.code !in 32..126 }) { error("API 키를 입력해 주세요."); return }
        val number = PhoneNumber(phone)
        if (!number.canCall || goal.isBlank()) { error("전화번호와 목적을 확인해 주세요."); return }
        mutable.update { it.copy(starting = true, error = null, resultNote = null) }
        work.launch {
            var attemptedCreation = false
            try {
                val client = factory(key)
                api = client
                val health = client.health() // Recheck before every explicit creation, not just on entering the screen.
                mutable.update { it.copy(health = health) }
                healthProblem(health, mode)?.let { error(it); return@launch }
                attemptedCreation = true
                val created = client.create(number.digits, goal.trim())
                session = created
                mutable.update { it.copy(hasSession = true, snapshot = SessionSnapshot(created.status,
                    "서버 이벤트에 연결하고 있어요.", goal = goal.trim(), goalSummary = created.goalSummary)) }
                connectStream(client, created)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val uncertain = attemptedCreation && uncertainDelivery(e)
                mutable.update { it.copy(creationUncertain = uncertain) }
                if (uncertain) error("생성 요청의 처리 여부를 확인할 수 없어요. 다시 시작하지 말고 서버 담당자에게 확인해 주세요.")
                else fail(e)
            } finally {
                mutable.update { it.copy(starting = false) }
                if (session == null) { api?.close(); api = null }
            }
        }
    }

    private fun healthProblem(health: HealthResponse, expectedMode: ServerTestMode? = null): String? = when {
        ServerTestMode.fromBackend(health.backend) == null -> "지원하지 않는 서버 통화 모드예요."
        expectedMode != null && health.backend != expectedMode.backend ->
            if (expectedMode == ServerTestMode.Sim) "실통화 서버예요. 실제 발신 확인이 필요해요."
            else "서버 통화 모드가 바뀌었어요. 연결 확인 후 다시 진행해 주세요."
        health.activeSession != null -> "다른 세션이 진행 중이에요. 해당 테스트가 끝난 뒤 다시 확인해 주세요."
        else -> null
    }

    fun respond(requestId: String, value: String, selection: Boolean = false) {
        if (!state.value.canSend) return
        val request = state.value.snapshot.pendingRequest
        if (request?.requestId != requestId || !requestCurrent(request)) {
            error("입력 요청이 만료됐거나 바뀌었어요. 상태를 다시 연결해 확인해 주세요."); return
        }
        val action = when (request.kind) {
            "clarification" -> if (selection) {
                if (request.options.none { it.key == value }) { error("서버가 제공한 선택지를 골라 주세요."); return }
                SessionAction.Select(requestId, value)
            } else {
                if (request.reason == "critical_action" || value.isBlank()) { error("중요한 선택은 화면의 항목을 직접 골라 주세요."); return }
                SessionAction.Answer(requestId, value.trim())
            }
            "secure_input" -> {
                if (selection || !value.matches(Regex("[0-9*#]{1,20}"))) { error("요청한 번호를 1~20자리로 입력해 주세요."); return }
                SessionAction.SecureInput(requestId, value) // Server, not app, appends its terminator.
            }
            else -> { error("지원하지 않는 입력 요청이에요. 서버 상태를 확인해 주세요."); return }
        }
        send(action, answeredRequest = requestId)
    }

    fun requestCurrent(request: UserRequest): Boolean = try {
        Instant.parse(request.expiresAt).isAfter(clock())
    } catch (_: Exception) { false }

    fun sendKeys(keys: String) {
        if (!state.value.canSend || state.value.snapshot.pendingRequest != null || state.value.snapshot.status == "dialing") return
        if (!keys.matches(Regex("[0-9*#]{1,4}"))) { error("키는 숫자·*·# 1~4자만 입력해 주세요."); return }
        send(SessionAction.Key(keys))
    }

    fun reviseGoal(text: String) {
        val current = state.value
        if (!current.canSend || current.snapshot.pendingRequest != null) return
        if (text.isBlank() || current.snapshot.path.isNotEmpty() || current.snapshot.status == "connected_to_agent") {
            error("목적은 첫 메뉴를 선택하기 전까지만 바꿀 수 있어요."); return
        }
        send(SessionAction.ReviseGoal(text.trim()))
    }

    fun cancel() {
        if (!state.value.active || state.value.busy || state.value.starting) return
        send(SessionAction.Cancel)
    }

    private fun send(action: SessionAction, answeredRequest: String? = null) {
        val client = api ?: return
        val activeSession = session ?: return
        mutable.update { it.copy(busy = true, error = null) }
        work.launch {
            try {
                val response = client.act(activeSession, action)
                if (action == SessionAction.Cancel) {
                    // The backend's accepted cancel response is sent only after finish().
                    mutable.update { it.copy(snapshot = it.snapshot.copy(status = "ended", message = "서버에서 테스트를 종료했어요.", pendingRequest = null)) }
                    fetchResult()
                } else mutable.update {
                    val snapshot = it.snapshot
                    it.copy(snapshot = snapshot.copy(
                        pendingRequest = if (answeredRequest != null && snapshot.pendingRequest?.requestId == answeredRequest) null else snapshot.pendingRequest,
                        goalSummary = response.goalSummary ?: snapshot.goalSummary))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val uncertain = uncertainDelivery(e) ||
                    (e is ApiHttpException && e.status in setOf(401, 409))
                mutable.update { it.copy(actionUncertain = uncertain) }
                if (uncertain) error("요청 처리 상태를 확인할 수 없어요. 자동 재전송하지 않아요. 상태를 다시 연결해 주세요.")
                else fail(e)
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }

    fun reconnect() {
        if (closed || !state.value.active || state.value.starting || state.value.busy) return
        val client = api ?: return
        val activeSession = session ?: return
        connectStream(client, activeSession)
    }

    private fun connectStream(client: ServerTestGateway, activeSession: CreatedSession) {
        stream?.cancel()
        mutable.update { it.copy(streamConnected = false, error = null) }
        // Each generation owns its updates; a canceled older stream cannot overwrite the new one.
        val nextStream = work.launch(start = CoroutineStart.LAZY) {
            val me = coroutineContext.job
            try {
                client.events(activeSession).collect { event ->
                    if (stream !== me) return@collect
                    applyEvent(event)
                    if (state.value.snapshot.status == "ended" && state.value.result == null) fetchResult()
                }
                if (state.value.active) error("이벤트 연결이 종료됐어요. 상태를 다시 연결해 주세요.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (stream === me) fail(e) }
            finally { if (stream === me) mutable.update { it.copy(streamConnected = false) } }
        }
        stream = nextStream
        nextStream.start()
    }

    private fun applyEvent(event: SessionEvent) {
        mutable.update { current ->
            val s = current.snapshot
            if (s.status == "ended" && event !is SessionEvent.Ended) return@update current
            when (event) {
                is SessionEvent.Snapshot -> current.copy(snapshot = event.data.copy(path = event.data.path.takeLast(100),
                    captions = event.data.captions.takeLast(100)), streamConnected = true, actionUncertain = false, error = null)
                is SessionEvent.StatusChanged -> current.copy(snapshot = s.copy(status = event.data.status, message = event.data.message,
                    pendingRequest = if (event.data.status == "awaiting_user") s.pendingRequest else null))
                is SessionEvent.CaptionAdded -> current.copy(snapshot = s.copy(captions = (s.captions + event.data).takeLast(100)))
                is SessionEvent.PathUpdated -> current.copy(snapshot = s.copy(path = (s.path.filterNot { it.step == event.data.step } + event.data).takeLast(100)))
                is SessionEvent.InputRequested -> current.copy(snapshot = s.copy(status = "awaiting_user", pendingRequest = event.data))
                is SessionEvent.InputRejected -> current.copy(error = event.data.message)
                is SessionEvent.SummaryReceived -> current.copy(snapshot = s.copy(summary = event.data))
                is SessionEvent.Ended -> current.copy(snapshot = s.copy(status = "ended", message = event.data.message,
                    pendingRequest = null, endReason = event.data.endReason))
                is SessionEvent.ErrorReceived -> current.copy(error = "서버에서 오류를 알렸어요. 상태를 확인하거나 테스트를 종료해 주세요.")
                is SessionEvent.Unknown -> current
            }
        }
    }

    fun fetchResult() {
        if (closed || state.value.resultLoading) return
        val client = api ?: return
        val activeSession = session ?: return
        mutable.update { it.copy(resultLoading = true, resultNote = null) }
        work.launch {
            try {
                when (val result = client.result(activeSession)) {
                    is SessionResult.Completed -> {
                        mutable.update { it.copy(result = result.data, streamConnected = false,
                            snapshot = it.snapshot.copy(status = "ended", pendingRequest = null,
                                endReason = result.data.endReason, message = "최종 결과로 서버 종료를 확인했어요.")) }
                        stream?.cancel()
                        stream = null
                    }
                    is SessionResult.InProgress -> mutable.update { it.copy(resultNote = "아직 최종 결과가 없어요. 잠시 후 다시 조회해 주세요.") }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { fail(e) }
            finally { mutable.update { it.copy(resultLoading = false) } }
        }
    }

    fun reset() {
        if (closed || state.value.active || state.value.starting || state.value.busy || state.value.creationUncertain) return
        owner.cancelChildren()
        stream = null
        api?.close(); api = null; session = null
        mutable.value = ServerTestState()
    }

    private fun error(message: String) { mutable.update { it.copy(error = message) } }
    private fun uncertainDelivery(e: Exception): Boolean = e is ApiConnectionException || e is ApiProtocolException ||
        (e is ApiHttpException && (e.status in 500..599 || e.status == 408))
    private fun fail(e: Exception) = error(when (e) {
        is ApiHttpException -> when (e.status) {
            401 -> "인증에 실패했어요. API 키 또는 세션 인증을 확인해 주세요."
            409 -> "서버 상태가 바뀌었어요. 다른 테스트가 진행 중인지 확인해 주세요."
            404 -> "세션을 찾을 수 없어요. 서버 담당자에게 확인해 주세요."
            else -> "서버 요청에 실패했어요 (HTTP ${e.status})."
        }
        is ApiStreamException -> "이벤트 연결이 끊겼어요. 상태를 다시 연결해 주세요."
        is ApiProtocolException -> "서버 응답 또는 보안 연결 주소가 API 계약과 달라요. 서버 담당자에게 확인해 주세요."
        is IllegalArgumentException -> "입력값 형식을 확인해 주세요."
        else -> "서버에 연결하지 못했어요. 네트워크를 확인해 주세요."
    })

    override fun close() {
        closed = true
        owner.cancel()
        api?.close(); api = null; session = null; stream = null
        mutable.value = ServerTestState()
    }
}
