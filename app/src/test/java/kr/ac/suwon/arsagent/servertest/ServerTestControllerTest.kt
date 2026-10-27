package kr.ac.suwon.arsagent.servertest

import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kr.ac.suwon.arsagent.data.api.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerTestControllerTest {
    private val now = Instant.parse("2026-10-26T00:00:00Z")
    private fun request(kind: String = "clarification", reason: String? = null, expires: String = "2026-10-26T01:00:00Z") =
        UserRequest("r1", kind, "입력이 필요해요", expires, reason,
            options = listOf(MenuOption("3", "잔액 조회")), label = "계좌번호", terminator = "#")

    private fun runCase(body: suspend TestScope.(ServerTestController, FakeGateway) -> Unit) = runTest {
        val fake = FakeGateway()
        val controller = ServerTestController(this, { fake }, { now })
        try { body(controller, fake) } finally { controller.close(); runCurrent() }
    }

    private suspend fun TestScope.start(c: ServerTestController) {
        c.start("local-test-key", "070-0000-0000", "통장 잔액 알고 싶어")
        runCurrent()
    }

    @Test fun constructionAndHealthCheckDoNotCreateASessionOrAuthenticateKey() = runCase { c, f ->
        assertFalse(c.state.value.hasSession)
        assertNull(c.state.value.health)
        c.checkConnection(); runCurrent()
        assertEquals("sim", c.state.value.health?.backend)
        assertEquals(0, f.creates)
        assertFalse(c.state.value.hasSession)
    }

    @Test fun startRechecksBackendAndBlocksRealCallsEvenAfterSuccessfulHealth() = runCase { c, f ->
        c.checkConnection(); runCurrent()
        f.healthValue = f.healthValue.copy(backend = "clawops")
        start(c)
        assertEquals(0, f.creates)
        assertFalse(c.state.value.hasSession)
        assertTrue(c.state.value.error!!.contains("확인"))
    }

    @Test fun realBackendHealthCheckIsAllowedButDoesNotDial() = runCase { c, f ->
        f.healthValue = f.healthValue.copy(backend = "clawops")
        c.checkConnection(); runCurrent()
        assertEquals("clawops", c.state.value.health?.backend)
        assertNull(c.state.value.error)
        assertEquals(0, f.creates)
    }

    @Test fun explicitlyConfirmedRealCallUsesExactTargetOnceWithoutExposingCredentials() = runCase { c, f ->
        f.healthValue = f.healthValue.copy(backend = "clawops")
        c.start("test-real-key", "070-1111-2222", "  가상 대상 조회  ", ServerTestMode.RealCall)
        runCurrent()
        assertEquals(listOf("07011112222" to "가상 대상 조회"), f.createdTargets)
        assertTrue(c.state.value.hasSession)
        assertFalse(c.state.value.toString().contains("test-real-key"))
    }

    @Test fun confirmedRealCallCannotRunIfServerBecameSim() = runCase { c, f ->
        f.healthValue = f.healthValue.copy(backend = "clawops")
        c.checkConnection(); runCurrent()
        f.healthValue = f.healthValue.copy(backend = "sim")
        c.start("test-real-key", "07011112222", "조회", ServerTestMode.RealCall); runCurrent()
        assertEquals(0, f.creates)
        assertFalse(c.state.value.hasSession)
        assertNotNull(c.state.value.error)
    }

    @Test fun unknownBackendCannotDialEvenWithRealConfirmation() = runCase { c, f ->
        f.healthValue = f.healthValue.copy(backend = "future_backend")
        c.start("test-real-key", "07011112222", "조회", ServerTestMode.RealCall); runCurrent()
        assertEquals(0, f.creates)
        assertNotNull(c.state.value.error)
    }

    @Test fun confirmedRealCallStillRejectsAnotherActiveSession() = runCase { c, f ->
        f.healthValue = f.healthValue.copy(backend = "clawops", activeSession = "other-session")
        c.start("test-real-key", "07011112222", "조회", ServerTestMode.RealCall); runCurrent()
        assertEquals(0, f.creates)
        assertTrue(c.state.value.error!!.contains("다른"))
    }

    @Test fun duplicateRealConfirmationCannotCreateTwice() = runCase { c, f ->
        f.healthValue = f.healthValue.copy(backend = "clawops")
        repeat(2) { c.start("test-real-key", "07011112222", "조회", ServerTestMode.RealCall) }
        runCurrent()
        c.start("test-real-key", "07011112222", "조회", ServerTestMode.RealCall); runCurrent()
        assertEquals(1, f.creates)
    }

    @Test fun realCreateResponseLossStillBlocksConfirmedRetry() = runCase { c, f ->
        f.healthValue = f.healthValue.copy(backend = "clawops")
        f.createFailure = ApiConnectionException()
        c.start("test-real-key", "07011112222", "조회", ServerTestMode.RealCall); runCurrent()
        assertTrue(c.state.value.creationUncertain)
        f.createFailure = null
        c.start("test-real-key", "07011112222", "조회", ServerTestMode.RealCall); runCurrent()
        assertEquals(1, f.creates)
        assertFalse(c.state.value.hasSession)
    }

    @Test fun busyServerAndMissingKeyDoNotStart() = runCase { c, f ->
        c.start("", "131", "조회"); runCurrent()
        assertTrue(c.state.value.error!!.contains("키"))
        f.healthValue = f.healthValue.copy(activeSession = "someone-else")
        start(c)
        assertEquals(0, f.creates)
        assertFalse(c.state.value.hasSession)
        assertTrue(c.state.value.error!!.contains("다른"))
    }

    @Test fun duplicateStartCannotCreateAnotherSessionAndStateDoesNotExposeKey() = runCase { c, f ->
        start(c)
        assertTrue(c.state.value.hasSession)
        assertTrue(c.state.value.streamConnected)
        assertEquals("listening", c.state.value.snapshot.status)
        c.start("local-test-key", "131", "다른 목적"); runCurrent()
        assertEquals(1, f.creates)
        assertFalse(c.state.value.toString().contains("local-test-key"))
        assertFalse(c.state.value.toString().contains("session-secret"))
    }

    @Test fun lostCreationResponseBlocksRetriesWithoutClaimingFailureToDial() = runCase { c, f ->
        f.createFailure = ApiConnectionException()
        start(c)
        assertTrue(c.state.value.creationUncertain)
        assertTrue(c.state.value.error!!.contains("확인"))
        start(c)
        assertEquals(1, f.creates)
        assertFalse(c.state.value.hasSession)
    }

    @Test fun gatewayServerErrorDuringCreationDoesNotAllowAnotherCreate() = runCase { c, f ->
        f.createFailure = ApiHttpException(502, "http_error")
        start(c)
        assertTrue(c.state.value.creationUncertain)
        start(c)
        assertEquals(1, f.creates)
    }

    @Test fun serverErrorDuringKeyActionRequiresSnapshotBeforeAnyResend() = runCase { c, f ->
        start(c)
        f.actionFailure = ApiHttpException(500, "http_error")
        c.sendKeys("3"); runCurrent()
        assertTrue(c.state.value.actionUncertain)
        c.sendKeys("3"); runCurrent()
        assertEquals(1, f.actions.size)
        f.actionFailure = null
        c.reconnect(); runCurrent()
        assertFalse(c.state.value.actionUncertain)
    }

    @Test fun unauthorizedCreationCanBeCorrectedWithoutLeakingResponse() = runCase { c, f ->
        f.createFailure = ApiHttpException(401, "unauthorized")
        start(c)
        assertFalse(c.state.value.creationUncertain)
        assertFalse(c.state.value.hasSession)
        assertTrue(c.state.value.error!!.contains("인증"))
        f.createFailure = null
        start(c)
        assertTrue(c.state.value.hasSession)
    }

    @Test fun eventsShowServerCaptionsPathsAndSummaryWithoutEndingSession() = runCase { c, f ->
        start(c)
        val step = PathStep(1, "잔액 조회 선택", selected = MenuOption("3", "잔액 조회"), actor = "ai")
        f.emit(SessionEvent.CaptionAdded("now", Caption(1, "ars", "서버 안내", "now")))
        f.emit(SessionEvent.PathUpdated("now", step))
        f.emit(SessionEvent.SummaryReceived("now", ResultSummary("result", "잔액은 10,000원")))
        f.emit(SessionEvent.StatusChanged("now", StatusChange("agent_done", "안내 완료")))
        runCurrent()
        assertEquals("서버 안내", c.state.value.snapshot.captions.single().text)
        assertEquals("잔액 조회 선택", c.state.value.snapshot.path.single().text)
        assertEquals("잔액은 10,000원", c.state.value.snapshot.summary?.summary)
        assertEquals("agent_done", c.state.value.snapshot.status)
        assertTrue(c.state.value.hasSession)
        assertNull(c.state.value.result)
    }

    @Test fun clarificationUsesActualRequestIdAndSelectionNotStaticWeatherOptions() = runCase { c, f ->
        start(c)
        f.emit(SessionEvent.InputRequested("now", request())); runCurrent()
        c.respond("r1", "3", selection = true); runCurrent()
        val sent = f.actions.single() as SessionAction.Select
        assertEquals("r1", sent.requestId)
        assertEquals("3", sent.key)
        assertNull(c.state.value.snapshot.pendingRequest)
    }

    @Test fun criticalExpiredUnknownAndInvalidOptionsCannotSubmit() = runCase { c, f ->
        start(c)
        f.emit(SessionEvent.InputRequested("now", request(reason = "critical_action"))); runCurrent()
        c.respond("r1", "동의합니다"); runCurrent()
        c.respond("r1", "9", selection = true); runCurrent()
        assertTrue(f.actions.isEmpty())
        f.emit(SessionEvent.InputRequested("now", request(expires = "2026-10-25T00:00:00Z"))); runCurrent()
        c.respond("r1", "3", selection = true); runCurrent()
        assertTrue(f.actions.isEmpty())
        f.emit(SessionEvent.InputRequested("now", request(kind = "future_kind"))); runCurrent()
        c.respond("r1", "1234"); runCurrent()
        assertTrue(f.actions.isEmpty())
        assertNotNull(c.state.value.error)
    }

    @Test fun secureInputHasNoInventedLengthOrTerminatorAndDoesNotEnterState() = runCase { c, f ->
        start(c)
        f.emit(SessionEvent.InputRequested("now", request(kind = "secure_input"))); runCurrent()
        c.respond("r1", "1234567890"); runCurrent()
        val sent = f.actions.single() as SessionAction.SecureInput
        assertEquals("1234567890", sent.digits)
        assertFalse(c.state.value.toString().contains("1234567890"))
        assertNull(c.state.value.snapshot.pendingRequest)
    }

    @Test fun responseDoesNotClearANewerRequestArrivingDuringSend() = runCase { c, f ->
        start(c)
        f.emit(SessionEvent.InputRequested("now", request())); runCurrent()
        f.onAction = { f.emit(SessionEvent.InputRequested("now", request().copy(requestId = "r2"))); yield() }
        c.respond("r1", "잔액"); runCurrent()
        assertEquals("r2", c.state.value.snapshot.pendingRequest?.requestId)
    }

    @Test fun reconnectReplacesSnapshotAndNeverReplaysMutations() = runCase { c, f ->
        start(c)
        f.emit(SessionEvent.InputRequested("now", request())); runCurrent()
        c.respond("r1", "잔액"); runCurrent()
        f.initial = SessionSnapshot("selecting", "새 상태", captions = listOf(Caption(9, "ars", "최신 안내", "now")))
        c.reconnect(); runCurrent()
        assertTrue(c.state.value.streamConnected)
        assertEquals("selecting", c.state.value.snapshot.status)
        assertNull(c.state.value.snapshot.pendingRequest)
        assertEquals("최신 안내", c.state.value.snapshot.captions.single().text)
        assertEquals(1, f.actions.size)
        assertEquals(1, f.creates)
    }

    @Test fun failedActionRequiresSnapshotBeforeAnotherInputAndCancelDoesNotFakeSuccess() = runCase { c, f ->
        start(c)
        f.actionFailure = ApiConnectionException()
        c.sendKeys("3"); runCurrent()
        assertTrue(c.state.value.actionUncertain)
        assertTrue(c.state.value.hasSession)
        assertNotEquals("ended", c.state.value.snapshot.status)
        c.sendKeys("1"); runCurrent()
        assertEquals(1, f.actions.size)
        c.cancel(); runCurrent()
        assertNotEquals("ended", c.state.value.snapshot.status)
        assertNotNull(c.state.value.error)
        f.actionFailure = null
        c.reconnect(); runCurrent()
        assertFalse(c.state.value.actionUncertain)
        c.cancel(); runCurrent()
        assertEquals("ended", c.state.value.snapshot.status)
        assertEquals("user_canceled", c.state.value.result?.endReason)
    }

    @Test fun endedSnapshotFetchesResultAnd202CanBeRefreshed() = runCase { c, f ->
        f.initial = SessionSnapshot("ended", "종료", endReason = "completed")
        f.resultValue = SessionResult.InProgress("ended")
        start(c)
        assertEquals("ended", c.state.value.snapshot.status)
        assertNull(c.state.value.result)
        assertNotNull(c.state.value.resultNote)
        f.resultValue = SessionResult.Completed(f.completed)
        c.fetchResult(); runCurrent()
        assertEquals(37, c.state.value.result?.durationSec)
    }

    @Test fun completedResultRecoversTerminationWhenEndedEventWasLost() = runCase { c, f ->
        f.initial = SessionSnapshot("awaiting_user", "답변 대기", pendingRequest = request())
        start(c)
        c.fetchResult(); runCurrent()
        assertEquals("ended", c.state.value.snapshot.status)
        assertNull(c.state.value.snapshot.pendingRequest)
        assertFalse(c.state.value.active)
        assertEquals(37, c.state.value.result?.durationSec)
    }

    @Test fun completedResultCannotBeResurrectedByLateStreamEvents() = runCase { c, f ->
        start(c)
        c.fetchResult(); runCurrent()
        f.emit(SessionEvent.StatusChanged("late", StatusChange("listening", "늦은 상태")))
        f.emit(SessionEvent.InputRequested("late", request()))
        runCurrent()
        assertFalse(c.state.value.active)
        assertEquals("ended", c.state.value.snapshot.status)
        assertNull(c.state.value.snapshot.pendingRequest)
        c.reconnect(); runCurrent()
        assertFalse(c.state.value.active)
        c.respond("r1", "잔액"); c.sendKeys("3"); runCurrent()
        assertTrue(f.actions.isEmpty())
    }

    @Test fun resetCannotAbandonAnActiveSessionButReleasesEndedSession() = runCase { c, f ->
        start(c)
        c.reset(); runCurrent()
        assertTrue(c.state.value.hasSession)
        c.cancel(); runCurrent()
        c.reset(); runCurrent()
        assertFalse(c.state.value.hasSession)
        assertNull(c.state.value.health)
        assertTrue(f.closed)
    }

    @Test fun closeReleasesNetworkWithoutImplicitlySendingCancel() = runCase { c, f ->
        start(c)
        c.close(); runCurrent()
        assertTrue(f.closed)
        assertTrue(f.actions.isEmpty())
    }

    @Test fun missingSessionStopsLocalInputWithoutPretendingTheCallEnded() = runCase { c, f ->
        start(c)
        f.actionFailure = ApiHttpException(404, "not_found")
        c.cancel(); runCurrent()
        assertTrue(c.state.value.sessionUnavailable)
        assertNotEquals("ended", c.state.value.snapshot.status)
        assertNull(c.state.value.result)
        assertFalse(c.state.value.canSend)
        assertFalse(c.state.value.streamConnected)
        c.cancel(); c.sendKeys("1"); c.reconnect(); runCurrent()
        assertEquals(listOf(SessionAction.Cancel), f.actions)
        f.emit(SessionEvent.StatusChanged("late", StatusChange("listening", "늦은 이전 안내"))); runCurrent()
        assertTrue(c.state.value.sessionUnavailable)
        c.reset(); runCurrent()
        assertFalse(c.state.value.hasSession)
        assertTrue(f.closed)
    }

    @Test fun missingResultMarksAnActiveSessionUnavailableButNeverDowngradesKnownEnd() = runTest {
        for (status in listOf("listening", "ended")) {
            val f = FakeGateway().apply {
                initial = SessionSnapshot(status, "가상 상태")
                resultFailure = ApiHttpException(404, "not_found")
            }
            val c = ServerTestController(this, { f }, { now })
            try {
                start(c); c.fetchResult(); runCurrent()
                assertEquals(status == "listening", c.state.value.sessionUnavailable)
                assertEquals(status, c.state.value.snapshot.status)
            } finally { c.close() }
        }
    }

    @Test fun acceptedCancelAfterConcurrentMissingReadStillProvesTermination() = runCase { c, f ->
        start(c)
        val gate = CompletableDeferred<Unit>()
        f.onAction = { gate.await() }
        c.cancel(); runCurrent()
        f.resultFailure = ApiHttpException(404, "not_found")
        c.fetchResult(); runCurrent()
        assertTrue(c.state.value.sessionUnavailable)
        gate.complete(Unit); runCurrent()
        assertEquals("ended", c.state.value.snapshot.status)
        assertFalse(c.state.value.sessionUnavailable)
        assertEquals(listOf(SessionAction.Cancel), f.actions)
    }

    @Test fun onlyMissingSessionStreamCodesOfferUnavailableRecovery() = runTest {
        for (code in listOf(4404, 404, 1006)) {
            val f = FakeGateway().apply { streamFailure = ApiStreamException(code) }
            val c = ServerTestController(this, { f }, { now })
            try {
                start(c)
                assertEquals(code != 1006, c.state.value.sessionUnavailable)
                assertNotEquals("ended", c.state.value.snapshot.status)
            } finally { c.close() }
        }
    }

    @Test fun aCreate404WithoutASessionDoesNotEnableAbandoningAnUnknownCall() = runCase { c, f ->
        f.createFailure = ApiHttpException(404, "not_found")
        start(c)
        assertFalse(c.state.value.hasSession)
        assertFalse(c.state.value.sessionUnavailable)
    }

    @Test fun immediateReconnectSnapshotIsNotDroppedByMainImmediateDispatcher() = runTest {
        val fake = FakeGateway()
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        val c = ServerTestController(scope, { fake }, { now })
        try {
            c.start("test-key", "07000000000", "조회")
            assertTrue(c.state.value.streamConnected)
            c.reconnect()
            assertTrue(c.state.value.streamConnected)
            assertEquals("listening", c.state.value.snapshot.status)
        } finally { c.close(); scope.cancel() }
    }
}

private class FakeGateway : ServerTestGateway {
    var healthValue = HealthResponse("sim", "none", "MockInterpreter", listOf("통장 잔액 알고 싶어"))
    var initial = SessionSnapshot("listening", "듣는 중")
    val bus = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 32)
    var creates = 0
    val createdTargets = mutableListOf<Pair<String, String>>()
    var createFailure: Throwable? = null
    var actionFailure: Throwable? = null
    var resultFailure: Throwable? = null
    var streamFailure: Throwable? = null
    var onAction: suspend () -> Unit = {}
    var closed = false
    val actions = mutableListOf<SessionAction>()
    val completed = CompletedResult("success", "user_canceled", "테스트 종료", durationSec = 37)
    var resultValue: SessionResult = SessionResult.Completed(completed)
    override suspend fun health() = healthValue
    override suspend fun create(phone: String, goal: String): CreatedSession {
        creates++
        createdTargets.add(phone to goal)
        createFailure?.let { throw it }
        return CreatedSession("s1", "session-secret", "wss://example.test/v1/sessions/s1/events", "wss://example.test/v1/sessions/s1/audio", phone, "dialing", goal)
    }
    override fun events(session: CreatedSession) = flow {
        emit(SessionEvent.Snapshot("now", initial))
        streamFailure?.let { throw it }
        emitAll(bus)
    }
    suspend fun emit(event: SessionEvent) { bus.emit(event) }
    override suspend fun act(session: CreatedSession, action: SessionAction): ActionResponse {
        actions.add(action)
        actionFailure?.let { throw it }
        onAction()
        return ActionResponse(true, status = if (action == SessionAction.Cancel) "ended" else null)
    }
    override suspend fun result(session: CreatedSession): SessionResult {
        resultFailure?.let { throw it }
        return resultValue
    }
    override fun close() { closed = true }
}
