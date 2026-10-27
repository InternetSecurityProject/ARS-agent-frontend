package kr.ac.suwon.arsagent.servertest

import java.time.Instant
import kr.ac.suwon.arsagent.data.api.*
import kr.ac.suwon.arsagent.servertest.voice.*
import org.junit.Assert.*
import org.junit.Test

class VoiceCoordinatorTest {
    private var now = Instant.parse("2026-10-27T00:00:00Z")
    private fun request(id: String = "r1", kind: String = "clarification", reason: String? = null) =
        UserRequest(id, kind, "어느 지역인가요?", "2026-10-27T00:01:00Z", reason,
            listOf(MenuOption("3", "수원")), label = "생년월일", terminator = "#")
    private fun live(r: UserRequest? = request()) = ServerTestState(hasSession = true, streamConnected = true,
        snapshot = SessionSnapshot("awaiting_user", "답변이 필요해요", pendingRequest = r))
    private class Device : SpeechDevice {
        val spoken = mutableListOf<String>()
        var done: (() -> Unit)? = null
        var answer: ((String) -> Unit)? = null
        var error: ((String) -> Unit)? = null
        var listens = 0
        override fun speak(text: String, onDone: () -> Unit) { spoken.add(text); done = onDone }
        override fun listen(onResult: (String) -> Unit, onError: (String) -> Unit) {
            listens++; answer = onResult; error = onError
        }
        override fun stop() {}
        override fun close() {}
    }

    @Test fun questionIsSpokenBeforeListeningAndFinalAnswerSendsOnce() {
        val d = Device(); val commands = mutableListOf<ServerTestCommand>()
        val c = VoiceCoordinator(d, commands::add, { now })
        c.update(live()); assertTrue(d.spoken.isEmpty()); assertEquals(0, d.listens)
        c.enable(true); assertEquals(listOf("어느 지역인가요?"), d.spoken); assertEquals(0, d.listens)
        d.done!!.invoke(); assertTrue(c.state.value.listening)
        val callback = d.answer!!; callback(" 수원 "); callback("서울")
        val sent = commands.single() as ServerTestCommand.Respond
        assertEquals("r1", sent.requestId); assertEquals("수원", sent.value); assertFalse(sent.selection)
        c.update(live()); assertEquals(1, d.spoken.size); assertEquals(1, d.listens)
    }

    @Test fun replacedOrExpiredQuestionCannotSendDelayedSpeech() {
        val d = Device(); val commands = mutableListOf<ServerTestCommand>()
        val c = VoiceCoordinator(d, commands::add, { now }); c.update(live()); c.enable(true)
        d.done!!.invoke(); val old = d.answer!!
        c.update(live(request("r2"))); old("수원"); assertTrue(commands.isEmpty())
        d.done!!.invoke(); now = now.plusSeconds(61); d.answer!!("부산")
        assertTrue(commands.isEmpty()); assertFalse(c.state.value.listening)
    }

    @Test fun sensitiveAndCriticalNeverStartMicrophoneOrReadSensitivePrompt() {
        for (r in listOf(request(kind = "secure_input").copy(message = "주민번호 123456 입력"), request(reason = "critical_action"))) {
            val d = Device(); val c = VoiceCoordinator(d, {}, { now })
            c.update(live(r)); c.enable(true); d.done?.invoke(); c.listenAnswer()
            assertEquals(0, d.listens); assertFalse(d.spoken.joinToString().contains("123456"))
        }
    }

    @Test fun backgroundDisabledDisconnectedAndNewSessionInvalidateOldCallbacks() {
        val d = Device(); val commands = mutableListOf<ServerTestCommand>()
        val c = VoiceCoordinator(d, commands::add, { now }); c.sessionChanged("first"); c.update(live()); c.enable(true)
        d.done!!.invoke(); val old = d.answer!!
        c.foreground(false); old("수원"); assertTrue(commands.isEmpty()); assertFalse(c.state.value.enabled)
        c.foreground(true); c.enable(true); d.done!!.invoke(); val next = d.answer!!
        c.update(live().copy(streamConnected = false)); next("수원"); assertTrue(commands.isEmpty())
        c.update(live()); c.enable(false); c.enable(true); d.done!!.invoke(); val previousSession = d.answer!!
        c.sessionChanged("second"); previousSession("서울"); assertTrue(commands.isEmpty())
        c.close(); d.answer?.invoke("제주"); assertTrue(commands.isEmpty())
    }

    @Test fun goalSpeechOnlyFillsFieldAndNeverCreatesOrResponds() {
        val d = Device(); val commands = mutableListOf<ServerTestCommand>(); var goal = ""
        val c = VoiceCoordinator(d, commands::add, { now }); c.enable(true)
        c.listenGoal { goal = it }; val result = d.answer!!; result("수원 날씨 알려줘"); result("두 번째")
        assertEquals("수원 날씨 알려줘", goal); assertTrue(commands.isEmpty())
    }

    @Test fun oldTtsCompletionAndRecognitionErrorCannotRestartMicrophone() {
        val d = Device(); val commands = mutableListOf<ServerTestCommand>()
        val c = VoiceCoordinator(d, commands::add, { now }); c.update(live()); c.enable(true)
        val oldDone = d.done!!; c.update(live(request("r2"))); oldDone(); assertEquals(0, d.listens)
        d.done!!.invoke(); d.error!!.invoke("음성을 인식하지 못했어요.")
        assertFalse(c.state.value.listening); assertNotNull(c.state.value.error)
        c.update(live(request("r2"))); assertEquals(1, d.listens); assertTrue(commands.isEmpty())
    }

    @Test fun progressDoesNotReadCaptionsAndDoesNotRepeatSameSnapshot() {
        val d = Device(); val c = VoiceCoordinator(d, {}, { now }); c.enable(true)
        val state = live(null).copy(snapshot = SessionSnapshot("selecting", "메뉴를 선택 중",
            captions = listOf(Caption(1, "ars", "기관 원문 123456", "now"))))
        c.update(state); c.update(state)
        assertEquals(1, d.spoken.size); assertFalse(d.spoken.single().contains("123456"))
        assertEquals(0, d.listens)
    }

    @Test fun counselorStateNeverListensAndCancelsPendingRecognition() {
        val d = Device(); val commands = mutableListOf<ServerTestCommand>()
        val c = VoiceCoordinator(d, commands::add, { now }); c.update(live()); c.enable(true); d.done!!.invoke()
        val old = d.answer!!; c.update(live(null).copy(snapshot = SessionSnapshot("connected_to_agent", "연결됨")))
        old("안녕하세요"); d.done?.invoke(); c.listenAnswer()
        assertTrue(commands.isEmpty()); assertFalse(c.state.value.listening)
    }

    @Test fun refreshedQuestionWithSameIdIsReadButExpiredQuestionStopsCapture() {
        val d = Device(); val c = VoiceCoordinator(d, {}, { now }); c.update(live()); c.enable(true)
        c.update(live(request().copy(message = "어느 날짜인가요?")))
        assertEquals("어느 날짜인가요?", d.spoken.last())
        d.done!!.invoke(); assertTrue(c.state.value.listening)
        now = now.plusSeconds(61); c.update(live(request().copy(message = "어느 날짜인가요?")))
        assertFalse(c.state.value.listening)
    }

    @Test fun startingCallInvalidatesPendingGoalRecognition() {
        val d = Device(); var goal = ""
        val c = VoiceCoordinator(d, {}, { now }); c.enable(true); c.listenGoal { goal = it }
        val old = d.answer!!; c.update(ServerTestState(starting = true)); old("이전 목적")
        assertEquals("", goal)
    }

    @Test fun arsIsSuppressedSynchronouslyBeforeTtsOrMicrophoneStarts() {
        var suppressed = false
        val d = object : SpeechDevice {
            var done: (() -> Unit)? = null
            override fun speak(text: String, onDone: () -> Unit) { assertTrue(suppressed); done = onDone }
            override fun listen(onResult: (String) -> Unit, onError: (String) -> Unit) { assertTrue(suppressed) }
            override fun stop() {}
            override fun close() {}
        }
        val c = VoiceCoordinator(d, {}, { now }, { suppressed = it })
        c.update(live()); c.enable(true); d.done!!.invoke(); assertTrue(suppressed)
        c.cancelInput(); assertFalse(suppressed)
    }

    @Test fun authoritativeQuestionReplacementBeforeCompositionCannotReceiveOldAnswer() {
        val d = Device(); val commands = mutableListOf<ServerTestCommand>()
        var server = live()
        val c = VoiceCoordinator(d, commands::add, { now }, latestState = { server })
        c.update(server); c.enable(true); d.done!!.invoke()
        // Same ID, new text/options, before voice.update/the Compose SideEffect can run.
        server = live(request().copy(message = "어느 날짜인가요?"))
        d.answer!!.invoke("수원")
        assertTrue(commands.isEmpty())
    }
}
