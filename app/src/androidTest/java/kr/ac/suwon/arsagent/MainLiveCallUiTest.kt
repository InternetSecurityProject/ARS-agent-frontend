package kr.ac.suwon.arsagent

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kr.ac.suwon.arsagent.data.api.*
import kr.ac.suwon.arsagent.livecall.LiveCallOwner
import kr.ac.suwon.arsagent.model.*
import kr.ac.suwon.arsagent.servertest.*
import kr.ac.suwon.arsagent.ui.live.MainPhoneApp
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MainLiveCallUiTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gateway = MainUiGateway()
    private val owner = LiveCallOwner(scope, { gateway })
    private val phone = mutableStateOf(AppState(number = "07011112222"))
    private fun content() {
        compose.setContent { MainPhoneApp(phone.value, { phone.value = phone.value.reduce(it) }, owner) }
    }
    @After fun close() { compose.runOnIdle { owner.close(); scope.cancel() } }

    private fun purpose() {
        compose.onNodeWithText("ARS Agent 전화").performClick()
        compose.onNodeWithText("어떤 일을 도와드릴까요?").assertIsDisplayed()
        compose.onNodeWithContentDescription("음성 대신 텍스트 입력").performTextInput("테스트 업무")
        compose.mainClock.advanceTimeBy(1_400)
        compose.onNodeWithText("실제로 전화를 걸까요?").assertIsDisplayed()
    }
    private fun start() {
        purpose()
        compose.onNodeWithTag("live-key").performTextInput("fake-ui-key")
        compose.onNodeWithText("실제 발신").performClick()
        compose.waitUntil(5_000) { owner.server.state.value.streamConnected }
    }
    private fun request(value: UserRequest) = compose.runOnIdle {
        gateway.bus.tryEmit(SessionEvent.InputRequested("now", value))
    }

    @Test fun normalPhoneRemainsDemoAndMobileHasNoAgentButton() {
        phone.value = AppState(number = "01012345678")
        content()
        compose.onNodeWithText("실제 ARS").assertIsDisplayed()
        compose.onNodeWithText("ARS Agent 전화").assertDoesNotExist()
        compose.onNodeWithText("일반 전화").performClick()
        compose.onNodeWithText("UI 데모 · 실제 통화 아님").assertIsDisplayed()
        assertTrue(gateway.targets.isEmpty())
    }

    @Test fun preparationDoesNotDialAndOnlyExplicitConfirmationCreatesOneSession() {
        content(); purpose()
        compose.runOnIdle { assertTrue(gateway.targets.isEmpty()) }
        compose.onNodeWithTag("live-key").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithText("실제 발신").assertIsNotEnabled()
        compose.onNodeWithTag("live-key").performTextInput("fake-ui-key")
        compose.onNodeWithText("실제 발신").performClick()
        compose.waitUntil(5_000) { owner.server.state.value.streamConnected }
        compose.onNodeWithTag("live-key").assertDoesNotExist()
        compose.onNodeWithText("서버에서 받은 실제 진행").assertIsDisplayed()
        compose.onNodeWithText("UI 데모 · 실제 통화 아님").assertDoesNotExist()
        compose.onNodeWithText("수원", substring = true).assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("07011112222" to "테스트 업무"), gateway.targets) }
    }

    @Test fun canceledConfirmationNeverReopensItselfOrKeepsTheKey() {
        content(); purpose()
        compose.onNodeWithTag("live-key").performTextInput("fake-ui-key")
        compose.onNodeWithTag("live-cancel-dial").performClick()
        compose.mainClock.advanceTimeBy(5_000)
        compose.onNodeWithText("실제로 전화를 걸까요?").assertDoesNotExist()
        compose.onNodeWithText("발신 확인").performClick()
        compose.onNodeWithTag("live-key").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertTrue(gateway.targets.isEmpty())
    }

    @Test fun legacyAgentDemoCanStillBeSelectedInTheExistingMenu() {
        content()
        compose.onNodeWithText("실제 ARS").performClick()
        compose.onNodeWithText("UI 데모").performClick()
        compose.onNodeWithText("ARS Agent 전화").performClick()
        compose.onNodeWithContentDescription("예시 음성 문장 입력, 녹음하지 않음").assertIsDisplayed()
        assertNull(owner.entry.value)
        assertTrue(gateway.targets.isEmpty())
    }

    @Test fun recreatingTheConfirmationDoesNotRestoreTheApiKeyOrDial() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MainPhoneApp(phone.value, { phone.value = phone.value.reduce(it) }, owner) }
        purpose()
        compose.onNodeWithTag("live-key").performTextInput("fake-ui-key")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("live-key").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("실제 발신").assertIsNotEnabled()
        assertTrue(gateway.targets.isEmpty())
    }

    @Test fun serverCandidatesAndCriticalSelectionUseSelectOnly() {
        content(); start()
        request(UserRequest("critical-ui", "clarification", "어느 메뉴인가요?", "2099-01-01T00:00:00Z",
            reason = "critical_action", options = listOf(MenuOption("*", "이전 업무"), MenuOption("#", "다른 업무"))))
        compose.onNodeWithText("다른 업무").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { gateway.actions.isNotEmpty() }
        compose.runOnIdle {
            val selected = gateway.actions.single() as SessionAction.Select
            assertEquals("critical-ui", selected.requestId); assertEquals("#", selected.key)
        }
        compose.onNodeWithText("말로 답하기").assertDoesNotExist()
    }

    @Test fun secureInputUsesServerPromptAndWaitsForCompletion() {
        content(); start()
        request(UserRequest("secure-ui", "secure_input", "요청한 숫자를 입력해 주세요", "2099-01-01T00:00:00Z",
            label = "서버의 개인정보 안내", terminator = "#"))
        compose.onNodeWithText("서버의 개인정보 안내").assertIsDisplayed()
        compose.onNodeWithContentDescription("키 #").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("키 1").performScrollTo().performClick()
        assertTrue(gateway.actions.isEmpty())
        compose.onNodeWithText("입력 완료").performClick()
        compose.waitUntil(5_000) { gateway.actions.isNotEmpty() }
        compose.runOnIdle {
            val input = gateway.actions.single() as SessionAction.SecureInput
            assertEquals("secure-ui", input.requestId); assertEquals("1", input.digits)
        }
    }

    @Test fun freeTextAndManualKeysUseDifferentApiActions() {
        content(); start()
        compose.onNodeWithText("키패드").performClick()
        compose.onNodeWithContentDescription("키 3").performScrollTo().performClick()
        compose.waitUntil(5_000) { gateway.actions.isNotEmpty() }
        request(UserRequest("answer-ui", "clarification", "어느 지역인가요?", "2099-01-01T00:00:00Z"))
        // The preceding key's fake HTTP receipt can arrive after the request event.
        // A person cannot type into the disabled field; the semantics input action can.
        compose.waitUntil(5_000) { owner.canRespond("answer-ui") }
        compose.onNodeWithContentDescription("음성 대신 텍스트 입력").performScrollTo().assertIsEnabled().performTextInput("부산")
        compose.onNodeWithText("답변 보내기").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(5_000) { gateway.actions.size == 2 }
        compose.runOnIdle {
            assertEquals(SessionAction.Key("3"), gateway.actions[0])
            assertEquals(SessionAction.Answer("answer-ui", "부산"), gateway.actions[1])
        }
    }

    @Test fun endingWaitsForTheServerInsteadOfLocallyPretendingToEnd() {
        gateway.cancelGate = CompletableDeferred()
        content(); start()
        compose.onNodeWithText("종료").performClick()
        compose.runOnIdle { assertTrue(owner.server.state.value.active) }
        compose.onNodeWithText("키패드로 돌아가기").assertDoesNotExist()
        compose.runOnIdle { gateway.cancelGate?.complete(Unit) }
        compose.waitUntil(5_000) { owner.server.state.value.result != null }
        compose.onNodeWithText("통화 종료 · 00:37").assertIsDisplayed()
        compose.onNodeWithText("키패드로 돌아가기").performClick()
        compose.onNodeWithText("실제 ARS").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(SessionAction.Cancel), gateway.actions) }
    }

    @Test fun uncertainActionCanReconnectAnOpenStreamWithoutResendingOldInput() {
        content(); start()
        compose.runOnIdle { gateway.actionFailure = ApiConnectionException() }
        val oldRequest = UserRequest("uncertain-ui", "clarification", "업무를 선택해 주세요", "2099-01-01T00:00:00Z",
            options = listOf(MenuOption("3", "기존 업무")))
        request(oldRequest)
        compose.onNodeWithText("기존 업무").performScrollTo().performClick()
        compose.waitUntil(5_000) { owner.server.state.value.actionUncertain }
        compose.runOnIdle {
            assertTrue(owner.server.state.value.streamConnected)
            gateway.actionFailure = null
            // The reconnected server may repeat the same unanswered request.
            gateway.reconnectSnapshot = SessionSnapshot("awaiting_user", "새 스냅샷", pendingRequest = oldRequest)
        }
        compose.onNodeWithText("상태 다시 연결").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { gateway.streams == 2 && owner.server.state.value.streamConnected && !owner.server.state.value.actionUncertain }
        compose.runOnIdle { assertEquals(1, gateway.actions.size); assertFalse(owner.canRespond("uncertain-ui")) }
        compose.onNodeWithText("기존 업무").assertIsNotEnabled()
        request(oldRequest.copy(requestId = "fresh-ui", options = listOf(MenuOption("4", "새 업무"))))
        compose.onNodeWithText("새 업무").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(5_000) { gateway.actions.size == 2 }
        compose.runOnIdle { assertEquals(SessionAction.Select("fresh-ui", "4"), gateway.actions.last()) }
    }
}

private class MainUiGateway : ServerTestGateway {
    val targets = mutableListOf<Pair<String, String>>()
    val actions = mutableListOf<SessionAction>()
    val bus = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 32)
    var cancelGate: CompletableDeferred<Unit>? = null
    var actionFailure: Exception? = null
    var streams = 0
    var reconnectSnapshot: SessionSnapshot? = null
    override suspend fun health() = HealthResponse("clawops", "fake", "fake")
    override suspend fun create(phone: String, goal: String): CreatedSession {
        targets += phone to goal
        return CreatedSession("ui-session", "fake-token", "wss://example.test/v1/sessions/ui-session/events",
            "wss://example.test/v1/sessions/ui-session/audio", phone, "dialing", goal)
    }
    override fun events(session: CreatedSession) = flow {
        streams++
        emit(SessionEvent.Snapshot("now", reconnectSnapshot ?: SessionSnapshot("listening", "서버에서 받은 실제 진행")))
        emitAll(bus)
    }
    override suspend fun act(session: CreatedSession, action: SessionAction): ActionResponse {
        actions += action
        actionFailure?.let { throw it }
        if (action == SessionAction.Cancel) cancelGate?.await()
        return ActionResponse(true)
    }
    override suspend fun result(session: CreatedSession): SessionResult =
        SessionResult.Completed(CompletedResult("success", "completed", "서버의 최종 결과", durationSec = 37))
    override fun close() = Unit
}
