package kr.ac.suwon.arsagent.livecall

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kr.ac.suwon.arsagent.data.api.*
import kr.ac.suwon.arsagent.servertest.*
import kr.ac.suwon.arsagent.servertest.voice.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveCallOwnerTest {
    private fun runCase(body: suspend TestScope.(LiveCallOwner, LiveGateway) -> Unit) = runTest {
        val gateway = LiveGateway()
        val owner = LiveCallOwner(this, { gateway }, { testScheduler.currentTime / 1000 })
        try { body(owner, gateway) } finally { owner.close(); runCurrent() }
    }

    @Test fun navigationGoalAndConfirmationNeverCreateOrAuthenticate() = runCase { owner, api ->
        assertTrue(owner.begin("070-1111-2222"))
        owner.editGoal("날씨 조회")
        owner.prepare()
        runCurrent()
        assertNotNull(owner.entry.value?.confirmation)
        owner.dismissConfirmation()
        assertTrue(owner.cancelPurpose())
        assertEquals(0, api.healthChecks)
        assertTrue(api.targets.isEmpty())
        assertNull(owner.entry.value)
    }

    @Test fun onlyAnExplicitConfirmedTargetCanBeCreatedOnce() = runCase { owner, api ->
        owner.begin("070-1111-2222"); owner.editGoal("  날씨 조회  ")
        assertFalse(owner.confirm("private-test-key"))
        owner.prepare()
        assertTrue(owner.confirm("private-test-key"))
        assertFalse(owner.confirm("private-test-key"))
        runCurrent()
        assertEquals(listOf("07011112222" to "날씨 조회"), api.targets)
        assertTrue(owner.server.state.value.hasSession)
        assertFalse(owner.entry.value.toString().contains("private-test-key"))
        assertFalse(owner.server.state.value.toString().contains("private-test-key"))
        assertFalse(owner.begin("131"))
        assertFalse(owner.cancelPurpose())
        assertFalse(owner.returnAfterEnd())
    }

    @Test fun changedGoalInvalidatesConsentAndBlankKeysCannotDial() = runCase { owner, api ->
        owner.begin("131"); owner.editGoal("첫 목적"); owner.prepare()
        owner.editGoal("다른 목적")
        assertFalse(owner.confirm("private-test-key"))
        owner.prepare()
        assertFalse(owner.confirm(" "))
        assertFalse(owner.confirm("키는ASCII만"))
        owner.dismissConfirmation(); runCurrent()
        assertTrue(api.targets.isEmpty())
    }

    @Test fun mobileOrInvalidNumberCannotEnterTheAgentFlow() = runCase { owner, api ->
        listOf("010-1234-5678", "", "12", "abc").forEach { assertFalse(owner.begin(it)) }
        runCurrent()
        assertEquals(0, api.healthChecks)
        assertNull(owner.entry.value)
    }

    @Test fun simUnknownAndBusyBackendsNeverCreateRealCalls() = runTest {
        listOf(HealthResponse("sim", "none", "mock"), HealthResponse("future", "none", "mock"),
            HealthResponse("clawops", "mock", "mock", activeSession = "someone-else")).forEach { health ->
            val api = LiveGateway().apply { healthValue = health }
            val owner = LiveCallOwner(this, { api })
            try {
                owner.begin("131"); owner.editGoal("조회"); owner.prepare(); owner.confirm("test-key")
                runCurrent()
                assertTrue(api.targets.isEmpty())
                assertNotNull(owner.server.state.value.error)
                assertTrue(owner.cancelPurpose())
            } finally { owner.close() }
        }
    }

    @Test fun uncertainCreationCannotBeDismissedOrRepeated() = runCase { owner, api ->
        api.createError = ApiConnectionException()
        owner.begin("131"); owner.editGoal("조회"); owner.prepare(); owner.confirm("test-key")
        runCurrent()
        assertTrue(owner.server.state.value.creationUncertain)
        owner.prepare()
        assertFalse(owner.confirm("test-key"))
        assertFalse(owner.cancelPurpose())
        assertFalse(owner.returnAfterEnd())
        assertEquals(1, api.targets.size)
    }

    @Test fun elapsedStartsOnlyOnConnectionAndFinalServerDurationWins() = runCase { owner, api ->
        owner.begin("131"); owner.editGoal("조회"); owner.prepare(); owner.confirm("test-key")
        runCurrent(); advanceTimeBy(4000); runCurrent()
        assertNull(owner.entry.value?.elapsedSeconds)
        api.events.emit(SessionEvent.StatusChanged("now", StatusChange("listening", "실제 안내")))
        runCurrent(); advanceTimeBy(3000); runCurrent()
        assertEquals(3, owner.entry.value?.elapsedSeconds)
        api.events.emit(SessionEvent.Ended("later", EndNotice("completed", "종료")))
        runCurrent()
        assertEquals(37, owner.entry.value?.elapsedSeconds)
        advanceTimeBy(3000); runCurrent()
        assertEquals(37, owner.entry.value?.elapsedSeconds)
        assertTrue(owner.returnAfterEnd())
        assertNull(owner.entry.value)
        assertFalse(owner.server.state.value.hasSession)
    }

    @Test fun missingSessionHidesTimerAndExplicitReturnReleasesOnlyLocalResources() = runCase { owner, api ->
        owner.begin("131"); owner.editGoal("조회"); owner.prepare(); owner.confirm("fake-test-key"); runCurrent()
        assertFalse(owner.returnAfterUnavailable())
        api.events.emit(SessionEvent.StatusChanged("now", StatusChange("listening", "안내"))); runCurrent()
        advanceTimeBy(3000); runCurrent()
        assertEquals(3, owner.entry.value?.elapsedSeconds)
        api.actionFailure = ApiHttpException(404, "not_found")
        owner.dispatch(ServerTestCommand.Cancel); runCurrent()
        assertNull(owner.entry.value?.elapsedSeconds)
        advanceTimeBy(5000); runCurrent()
        assertNull(owner.entry.value?.elapsedSeconds)
        assertFalse(owner.returnAfterEnd())
        assertTrue(owner.returnAfterUnavailable())
        assertNull(owner.entry.value)
        assertFalse(owner.server.state.value.hasSession)
        assertEquals(listOf(SessionAction.Cancel), api.actions)
        assertEquals(1, api.closes)
        assertTrue(owner.begin("131"))
        runCurrent()
        assertEquals(1, api.targets.size) // Returning/entering again cannot dial.
    }

    @Test fun unavailableReturnCanCancelAnInFlightLocalResultRead() = runCase { owner, api ->
        owner.begin("131"); owner.editGoal("조회"); owner.prepare(); owner.confirm("fake-test-key"); runCurrent()
        api.resultGate = CompletableDeferred()
        owner.dispatch(ServerTestCommand.FetchResult); runCurrent()
        assertTrue(owner.server.state.value.resultLoading)
        api.actionFailure = ApiHttpException(404, "not_found")
        owner.dispatch(ServerTestCommand.Cancel); runCurrent()
        assertTrue(owner.server.state.value.sessionUnavailable)
        assertTrue(owner.returnAfterUnavailable())
        runCurrent()
        assertNull(owner.entry.value)
        assertFalse(owner.server.state.value.hasSession)
        assertEquals(listOf(SessionAction.Cancel), api.actions)
    }

    @Test fun voiceAndButtonShareAReceiptEvenAfterAnOldRequestIsRestored() = runCase { owner, api ->
        owner.begin("131"); owner.editGoal("조회"); owner.prepare(); owner.confirm("test-key"); runCurrent()
        val request = UserRequest("question-1", "clarification", "어떤 업무인가요?", "2099-01-01T00:00:00Z",
            options = listOf(MenuOption("3", "잔액 조회")))
        api.events.emit(SessionEvent.InputRequested("now", request)); runCurrent()
        owner.dispatch(ServerTestCommand.Respond("question-1", "잔액 조회")); runCurrent()
        api.events.emit(SessionEvent.Snapshot("late", SessionSnapshot("awaiting_user", "늦은 상태", pendingRequest = request)))
        runCurrent()
        owner.dispatch(ServerTestCommand.Respond("question-1", "3", selection = true)); runCurrent()
        assertEquals(1, api.actions.size)
        assertTrue(api.actions.single() is SessionAction.Answer)
    }

    @Test fun criticalAndSecureRequestsCannotBeBypassedBySpeechOrRawKeys() = runCase { owner, api ->
        owner.begin("131"); owner.editGoal("조회"); owner.prepare(); owner.confirm("test-key"); runCurrent()
        val critical = UserRequest("critical-1", "clarification", "직접 선택", "2099-01-01T00:00:00Z",
            reason = "critical_action", options = listOf(MenuOption("#", "확인")))
        api.events.emit(SessionEvent.InputRequested("now", critical)); runCurrent()
        owner.dispatch(ServerTestCommand.Respond("critical-1", "확인"))
        owner.dispatch(ServerTestCommand.Keys("#")); runCurrent()
        assertTrue(api.actions.isEmpty())
        owner.dispatch(ServerTestCommand.Respond("critical-1", "#", true)); runCurrent()
        assertTrue(api.actions.single() is SessionAction.Select)
        val secure = UserRequest("secure-1", "secure_input", "번호 입력", "2099-01-01T00:00:00Z")
        api.events.emit(SessionEvent.InputRequested("now", secure)); runCurrent()
        owner.dispatch(ServerTestCommand.Keys("1")); runCurrent()
        assertEquals(1, api.actions.size)
        owner.dispatch(ServerTestCommand.Respond("secure-1", "123456")); runCurrent()
        assertTrue(api.actions.last() is SessionAction.SecureInput)
    }

    @Test fun recognizedPurposeNeverDialsAndQuestionSpeechUsesTheSameLiveOwner() = runCase { owner, api ->
        val speech = Speech()
        val voice = VoiceCoordinator(speech, owner::dispatch, latestState = { owner.server.state.value })
        owner.begin("131"); voice.enable(true); voice.listenGoal(owner::editGoal)
        speech.answer!!.invoke("날씨 조회")
        runCurrent()
        assertEquals("날씨 조회", owner.entry.value?.goal)
        assertTrue(api.targets.isEmpty())
        owner.prepare(); owner.confirm("fake-test-key"); runCurrent()
        voice.sessionChanged("test-session")
        val request = UserRequest("spoken-question", "clarification", "어느 지역인가요?", "2099-01-01T00:00:00Z")
        api.events.emit(SessionEvent.InputRequested("now", request)); runCurrent()
        voice.update(owner.server.state.value)
        assertEquals("어느 지역인가요?", speech.text)
        speech.done!!.invoke()
        val lateResult = speech.answer!!
        lateResult("수원"); lateResult("다른 지역"); runCurrent()
        assertEquals(listOf(SessionAction.Answer("spoken-question", "수원")), api.actions)
        voice.close()
    }

    @Test fun candidateChoiceWinsAgainstLateSpeechEvenBeforeVoiceRecomposition() = runCase { owner, api ->
        owner.begin("131"); owner.editGoal("조회"); owner.prepare(); owner.confirm("fake-test-key"); runCurrent()
        val request = UserRequest("speech-race", "clarification", "업무 선택", "2099-01-01T00:00:00Z",
            options = listOf(MenuOption("3", "잔액 조회")))
        api.events.emit(SessionEvent.InputRequested("now", request)); runCurrent()
        val speech = Speech()
        val voice = VoiceCoordinator(speech, owner::dispatch, latestState = { owner.server.state.value })
        voice.update(owner.server.state.value); voice.enable(true); speech.done!!.invoke()
        owner.dispatch(ServerTestCommand.Respond("speech-race", "3", true))
        speech.answer!!.invoke("잔액 조회"); runCurrent()
        assertEquals(listOf(SessionAction.Select("speech-race", "3")), api.actions)
        voice.foreground(false); speech.answer!!.invoke("서울"); runCurrent()
        assertEquals(1, api.actions.size)
        voice.close()
    }

    private class Speech : SpeechDevice {
        var text = ""
        var done: (() -> Unit)? = null
        var answer: ((String) -> Unit)? = null
        override fun speak(text: String, onDone: () -> Unit) { this.text = text; done = onDone }
        override fun listen(onResult: (String) -> Unit, onError: (String) -> Unit) { answer = onResult }
        override fun stop() = Unit
        override fun close() = Unit
    }
}

private class LiveGateway : ServerTestGateway {
    var healthChecks = 0
    var healthValue = HealthResponse("clawops", "mock", "mock")
    val targets = mutableListOf<Pair<String, String>>()
    val actions = mutableListOf<SessionAction>()
    var createError: Throwable? = null
    var actionFailure: Exception? = null
    var closes = 0
    var resultGate: CompletableDeferred<Unit>? = null
    val events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 32)
    override suspend fun health(): HealthResponse { healthChecks++; return healthValue }
    override suspend fun create(phone: String, goal: String): CreatedSession {
        targets += phone to goal
        createError?.let { throw it }
        return CreatedSession("test-session", "test-token", "wss://example.test/v1/sessions/test-session/events",
            "wss://example.test/v1/sessions/test-session/audio", phone, "dialing", goal)
    }
    override fun events(session: CreatedSession): Flow<SessionEvent> = flow {
        emit(SessionEvent.Snapshot("now", SessionSnapshot("dialing", "연결 중")))
        emitAll(events)
    }
    override suspend fun act(session: CreatedSession, action: SessionAction): ActionResponse {
        actions += action
        actionFailure?.let { throw it }
        return ActionResponse(true)
    }
    override suspend fun result(session: CreatedSession): SessionResult {
        resultGate?.await()
        return SessionResult.Completed(CompletedResult("success", "completed", "실제 결과", durationSec = 37))
    }
    override fun close() { closes++ }
}
