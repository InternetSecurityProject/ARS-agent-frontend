package kr.ac.suwon.arsagent

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kr.ac.suwon.arsagent.data.api.*
import kr.ac.suwon.arsagent.model.AudioRoute
import kr.ac.suwon.arsagent.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LiveCallControlsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun existingRoundControlsInvokeLiveArsKeypadAndRouteCallbacks() {
        var ars by mutableStateOf(false)
        var keypad by mutableStateOf(false)
        var menu by mutableStateOf(false)
        var route by mutableStateOf(AudioRoute.Phone)
        val keys = mutableListOf<Char>()
        compose.setContent {
            BServerCallScreen("131", SessionSnapshot("listening", "서버 안내"), onEnd = {}, onBack = {},
                controls = ServerCallControls(arsEnabled = ars, arsAvailable = true, keypadAvailable = true,
                    keypadOpen = keypad, audioMenu = menu, audioRoute = route,
                    availableRoutes = setOf(AudioRoute.Phone, AudioRoute.Speaker), onToggleArs = { ars = !ars },
                    onKeypad = { keypad = !keypad }, onKey = keys::add, onAudioMenu = { menu = !menu },
                    onRoute = { route = it; menu = false }))
        }
        compose.onNodeWithText("ARS 소리 켜기").performClick()
        compose.onNodeWithText("ARS 소리 끄기").assertIsDisplayed()
        compose.onNodeWithText("키패드").performClick()
        compose.onNodeWithContentDescription("키 *").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf('*'), keys) }
        compose.onNodeWithText("키패드 가리기").performScrollTo().performClick()
        compose.onNodeWithText("오디오").performClick()
        compose.onNodeWithText("스피커").performClick()
        compose.runOnIdle { assertEquals(AudioRoute.Speaker, route) }
    }

    @Test fun unsupportedOutputAndBusyKeypadStayDisabled() {
        var menu by mutableStateOf(false)
        var ended = false
        compose.setContent {
            BServerCallScreen("131", SessionSnapshot("listening", "서버 안내"), onEnd = { ended = true }, onBack = {},
                controls = ServerCallControls(endEnabled = false, keypadAvailable = false, audioMenu = menu,
                    availableRoutes = setOf(AudioRoute.Speaker), onAudioMenu = { menu = !menu }))
        }
        compose.onNodeWithText("종료").assertIsNotEnabled()
        compose.onNodeWithText("키패드").assertIsNotEnabled()
        compose.onNodeWithText("오디오").performClick()
        compose.onNodeWithText("휴대전화").assertIsNotEnabled()
        assertFalse(ended)
    }

    @Test fun shortWindowKeepsEndVisibleAndFreeAnswerReachable() {
        val request = UserRequest("small-question", "clarification", "어느 지역인가요?", "2099-01-01T00:00:00Z")
        var answer: SessionAction.Answer? = null
        compose.setContent {
            // This fixed height already represents space remaining above the keyboard.
            // Do not subtract the real emulator IME height a second time inside the frame.
            Box(Modifier.size(360.dp, 320.dp).consumeWindowInsets(WindowInsets.ime)) {
                BServerCallScreen("131", SessionSnapshot("awaiting_user", "입력", pendingRequest = request),
                    onEnd = {}, onBack = {}, controls = ServerCallControls(),
                    answerInput = ServerAnswerInput(true, { answer = it }, {}, false))
            }
        }
        compose.onNodeWithText("종료").assertIsDisplayed()
        compose.onNodeWithContentDescription("음성 대신 텍스트 입력").performScrollTo().performTextInput("제주")
        compose.onNodeWithContentDescription("음성 대신 텍스트 입력").assertTextContains("제주")
        compose.onNodeWithText("답변 보내기").performScrollTo()
        compose.onNodeWithText("답변 보내기").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(SessionAction.Answer("small-question", "제주"), answer) }
    }
}
