package kr.ac.suwon.arsagent

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.text.AnnotatedString
import kr.ac.suwon.arsagent.data.api.*
import kr.ac.suwon.arsagent.servertest.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ServerTestUiTest {
    @get:Rule val compose = createComposeRule()
    private val ready = ServerTestState(health = HealthResponse("sim", "none", "MockInterpreter", listOf("통장 잔액 알고 싶어")))
    private fun live(request: UserRequest? = null) = ready.copy(hasSession = true, streamConnected = true,
        snapshot = SessionSnapshot("awaiting_user", "서버에서 받은 상태", pendingRequest = request))
    private fun request(kind: String = "clarification", reason: String? = null, expires: String = "2099-01-01T00:00:00Z") =
        UserRequest("server-r1", kind, "서버의 입력 요청", expires, reason, listOf(MenuOption("3", "잔액 조회")))

    @Test fun initialScreenDoesNotStartOrPretendToAuthenticate() {
        val actions = mutableListOf<ServerTestCommand>()
        compose.setContent { ServerTestScreen(ServerTestState(), actions::add, {}) }
        compose.onNodeWithText("서버 테스트").assertIsDisplayed()
        compose.onNodeWithText("연결 확인").assertIsDisplayed()
        compose.onNodeWithTag("server-key").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithText("테스트 시작").performScrollTo().assertIsNotEnabled()
        assertTrue(actions.isEmpty())
    }

    @Test fun runtimeKeyIsPassedOnlyOnExplicitStartAndThenCleared() {
        val actions = mutableListOf<ServerTestCommand>()
        compose.setContent { ServerTestScreen(ready, actions::add, {}) }
        compose.onNodeWithTag("server-key").performTextInput("test-only-key")
        compose.onNodeWithTag("server-goal").performTextInput("잔액 조회")
        compose.onNodeWithText("테스트 시작").performScrollTo().performClick()
        val start = actions.single() as ServerTestCommand.Start
        assertEquals("test-only-key", start.key)
        assertEquals("07000000000", start.phone)
        assertEquals("잔액 조회", start.goal)
        compose.onNodeWithTag("server-key").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertFalse(start.toString().contains("test-only-key"))
    }

    @Test fun keyIsNotRestoredFromSavedInstanceState() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { ServerTestScreen(ready, {}, {}) }
        compose.onNodeWithTag("server-key").performTextInput("test-only-key")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("server-key")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test fun criticalRequestShowsOnlyServerOptionsAndNoFreeTextAnswer() {
        val actions = mutableListOf<ServerTestCommand>()
        compose.setContent { ServerTestScreen(live(request(reason = "critical_action")), actions::add, {}) }
        compose.onNodeWithTag("server-input").assertDoesNotExist()
        compose.onNodeWithText("3 · 잔액 조회").performScrollTo().performClick()
        val reply = actions.single() as ServerTestCommand.Respond
        assertEquals("server-r1", reply.requestId)
        assertEquals("3", reply.value)
        assertTrue(reply.selection)
    }

    @Test fun secureInputWaitsForExplicitSendAndDoesNotAppendTerminator() {
        val actions = mutableListOf<ServerTestCommand>()
        compose.setContent { ServerTestScreen(live(request("secure_input")), actions::add, {}) }
        compose.onNodeWithTag("server-input").performScrollTo().performTextInput("1234567890")
        assertTrue(actions.isEmpty())
        compose.onNodeWithText("입력 보내기").performScrollTo().performClick()
        val reply = actions.single() as ServerTestCommand.Respond
        assertEquals("1234567890", reply.value)
        assertFalse(reply.toString().contains("1234567890"))
        compose.onNodeWithTag("server-input")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test fun expiredAndUnsupportedRequestsCannotBeSubmitted() {
        compose.setContent { ServerTestScreen(live(request(expires = "2000-01-01T00:00:00Z")), {}, {}) }
        compose.onNodeWithText("요청 기한이 지났어요. 상태를 다시 연결해 주세요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3 · 잔액 조회").assertIsNotEnabled()
    }

    @Test fun activeSessionBackRequiresExplicitCancellationWithoutFakingExit() {
        val actions = mutableListOf<ServerTestCommand>()
        var exited = false
        compose.setContent { ServerTestScreen(live(), actions::add, { exited = true }) }
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("테스트를 종료할까요?").assertIsDisplayed()
        compose.onNodeWithText("서버에서 종료").performClick()
        assertEquals(ServerTestCommand.Cancel, actions.single())
        assertFalse(exited)
    }

    @Test fun failedCancellationAllowsOnlyAnExplicitWarnedDisconnect() {
        val actions = mutableListOf<ServerTestCommand>()
        var exited = false
        compose.setContent {
            ServerTestScreen(live().copy(error = "종료 확인 실패", actionUncertain = true), actions::add, { exited = true })
        }
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("서버 세션은 종료되지 않았을 수 있어요.").assertIsDisplayed()
        compose.onNodeWithText("화면만 닫기").performClick()
        assertTrue(exited)
        assertTrue(actions.isEmpty())
    }

    @Test fun serverCaptionsAndPathAreShownInsteadOfWeatherDemoContent() {
        val state = live().copy(snapshot = SessionSnapshot("selecting", "은행 메뉴를 선택하고 있어요",
            path = listOf(PathStep(1, "잔액 조회로 이동", selected = MenuOption("3", "잔액 조회"), actor = "ai")),
            captions = listOf(Caption(1, "ars", "잔액 조회는 3번입니다", "now"))))
        compose.setContent { ServerTestScreen(state, {}, {}) }
        compose.onNodeWithText("은행 메뉴를 선택하고 있어요").assertIsDisplayed()
        compose.onNodeWithText("진행 기록 보기").performScrollTo().performClick()
        compose.onNodeWithText("잔액 조회로 이동").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("잔액 조회는 3번입니다").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("수원", substring = true).assertDoesNotExist()
    }
}
