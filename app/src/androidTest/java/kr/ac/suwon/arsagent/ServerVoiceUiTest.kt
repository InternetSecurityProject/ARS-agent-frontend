package kr.ac.suwon.arsagent

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kr.ac.suwon.arsagent.data.api.*
import kr.ac.suwon.arsagent.servertest.*
import kr.ac.suwon.arsagent.servertest.voice.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic callbacks only: never constructs a recognizer, audio player, or network client. */
class ServerVoiceUiTest {
    @get:Rule val compose = createComposeRule()
    private val ready = ServerTestState(health = HealthResponse("sim", "none", "MockInterpreter"))
    private val request = UserRequest("voice-r", "clarification", "어느 지역인가요?", "2099-01-01T00:00:00Z")
    private fun controls(status: VoiceStatus = VoiceStatus(enabled = true),
        onGoal: ((String) -> Unit) -> Unit = { it("수원 날씨 알려줘") },
        onAnswer: () -> Unit = {}, arsOn: Boolean = false, onArs: () -> Unit = {}) =
        ServerVoiceUi(status, arsOn, false, null, {}, onGoal, onAnswer, {}, onArs)

    @Test fun recognizedGoalFillsExistingFieldWithoutStartingOrAuthenticating() {
        val commands = mutableListOf<ServerTestCommand>()
        compose.setContent { ServerTestScreen(ready, commands::add, {}, controls()) }
        compose.onNodeWithText("목적 말하기").performScrollTo().performClick()
        compose.onNodeWithTag("server-goal").assertTextContains("수원 날씨 알려줘")
        assertTrue(commands.isEmpty())
    }

    @Test fun voiceReplyButtonRequiresOrdinaryUnexpiredSendableRequest() {
        var replies = 0
        val current = mutableStateOf(ready.copy(hasSession = true, streamConnected = true,
            snapshot = SessionSnapshot("awaiting_user", "답변", pendingRequest = request)))
        compose.setContent { ServerTestScreen(current.value, {}, {}, controls(onAnswer = { replies++ })) }
        compose.onNodeWithText("말로 답하기").performScrollTo().performClick(); assertEquals(1, replies)
        compose.runOnIdle { current.value = current.value.copy(snapshot = current.value.snapshot.copy(pendingRequest = request.copy(reason = "critical_action"))) }
        compose.onNodeWithText("말로 답하기").assertDoesNotExist()
        compose.runOnIdle { current.value = current.value.copy(snapshot = current.value.snapshot.copy(pendingRequest = request.copy(kind = "secure_input"))) }
        compose.onNodeWithText("말로 답하기").assertDoesNotExist()
        compose.onNodeWithText("입력 완료").performScrollTo().assertIsNotEnabled()
    }

    @Test fun arsToggleReflectsRuntimeAndIsUnavailableForCounselor() {
        var toggles = 0
        val on = mutableStateOf(false)
        val live = mutableStateOf(ready.copy(hasSession = true, streamConnected = true,
            snapshot = SessionSnapshot("listening", "듣는 중")))
        compose.setContent { ServerTestScreen(live.value, {}, {}, controls(arsOn = on.value, onArs = { toggles++; on.value = !on.value })) }
        compose.onNodeWithText("ARS 소리 켜기").performScrollTo().performClick()
        compose.onNodeWithText("ARS 소리 끄기").assertIsDisplayed(); assertEquals(1, toggles)
        compose.runOnIdle { live.value = live.value.copy(snapshot = live.value.snapshot.copy(status = "connected_to_agent")) }
        compose.onNodeWithText("ARS 소리 끄기").assertIsNotEnabled()
    }

    @Test fun disabledVoiceAndRecognitionFailureKeepTextAndManualActionsAvailable() {
        compose.setContent { ServerTestScreen(ready, {}, {}, controls(VoiceStatus(error = "한국어 음성 인식을 사용할 수 없어요."))) }
        compose.onNodeWithText("음성 대화 켜기").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("목적 말하기").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("server-goal").performScrollTo().performTextInput("직접 입력")
        compose.onNodeWithTag("server-goal").assertTextContains("직접 입력")
        compose.onNodeWithText("테스트 시작").performScrollTo().assertIsNotEnabled()
    }
}
