package kr.ac.suwon.arsagent

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kr.ac.suwon.arsagent.model.*
import kr.ac.suwon.arsagent.ui.ArsApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PhoneFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun mobileNumberHasNoAgentCallAction() {
        compose.setContent { ArsApp(AppState(number = "01012345678"), {}) }
        compose.onNodeWithText("일반 전화").assertIsDisplayed()
        compose.onNodeWithText("ARS Agent 전화").assertDoesNotExist()
    }

    @Test fun agentCallOpensPurposeAndCircularCancelReturnsHome() {
        val state = mutableStateOf(AppState(number = "07000000000"))
        compose.setContent { ArsApp(state.value) { state.value = state.value.reduce(it) } }
        compose.onNodeWithText("ARS Agent 전화").performClick()
        compose.onNodeWithText("어떤 일을 도와드릴까요?").assertIsDisplayed()
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithText("ARS Agent 전화").assertIsDisplayed()
    }

    @Test fun keypadIsOutsideCardAndSixDigitsContinueWithoutConfirmButton() {
        val state = mutableStateOf(AppState(screen = Screen.Call, agent = true, phase = Phase.Secure))
        compose.setContent { ArsApp(state.value) { state.value = state.value.reduce(it) } }
        compose.onNodeWithTag("call-keypad").assertIsDisplayed()
        compose.onNodeWithText("입력 완료").assertDoesNotExist()
        "123456".forEach { compose.onNodeWithContentDescription("키 $it").performClick() }
        compose.runOnIdle {
            assertEquals(0, state.value.secureEntry.count)
            assertEquals(Phase.Sending, state.value.phase)
        }
    }

    @Test fun audioPopoverSelectsSpeakerAndCloses() {
        val state = mutableStateOf(AppState(screen = Screen.Call, agent = true, phase = Phase.Selecting))
        compose.setContent { ArsApp(state.value) { state.value = state.value.reduce(it) } }
        compose.onNodeWithText("오디오").performClick()
        compose.onNodeWithText("스피커").performClick()
        compose.runOnIdle { assertEquals(AudioRoute.Speaker, state.value.audioRoute) }
        compose.onNodeWithText("휴대전화").assertDoesNotExist()
    }

    @Test fun endCallShowsDurationAndReturnAction() {
        val state = mutableStateOf(AppState(screen = Screen.Call, elapsedSeconds = 18))
        compose.setContent { ArsApp(state.value) { state.value = state.value.reduce(it) } }
        compose.onNodeWithText("종료").performClick()
        compose.onNodeWithText("통화 종료 · 00:18").assertIsDisplayed()
        compose.onNodeWithText("키패드로 돌아가기").performClick()
        compose.onNodeWithText("통화").assertIsDisplayed()
    }

    @Test fun endingDuringSecureInputRemovesKeypad() {
        val state = mutableStateOf(AppState(screen = Screen.Call, agent = true, phase = Phase.Secure, elapsedSeconds = 18))
        compose.setContent { ArsApp(state.value) { state.value = state.value.reduce(it) } }
        compose.onNodeWithText("종료").performClick()
        compose.onNodeWithTag("call-keypad").assertDoesNotExist()
        compose.onNodeWithText("통화 종료 · 00:18").assertIsDisplayed()
    }

    @Test fun purposeAndAnswerStayReachableInImeSizedWindow() {
        val state = mutableStateOf(AppState(screen = Screen.Purpose, number = "131", agent = true))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                // Represents remaining app space after the soft keyboard opens on a small phone.
                Box(Modifier.size(360.dp, 320.dp)) { ArsApp(state.value, {}) }
            }
        }
        compose.onNodeWithContentDescription("음성 대신 텍스트 입력").assertIsDisplayed()
        compose.onNodeWithText("취소").assertIsDisplayed()
        compose.runOnIdle { state.value = AppState(screen = Screen.Call, number = "131", agent = true, phase = Phase.Answer) }
        compose.onNodeWithContentDescription("음성 대신 텍스트 입력").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("종료").assertIsDisplayed()
    }
}
