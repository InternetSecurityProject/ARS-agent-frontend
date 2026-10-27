package kr.ac.suwon.arsagent

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kr.ac.suwon.arsagent.data.api.*
import kr.ac.suwon.arsagent.livecall.LiveCallOwner
import kr.ac.suwon.arsagent.model.*
import kr.ac.suwon.arsagent.ui.*
import kr.ac.suwon.arsagent.ui.live.MainPhoneApp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Replacing the input when the IME crosses the compact threshold must fail these tests. */
class CallInputFocusTest {
    @get:Rule val compose = createComposeRule()
    private val height = mutableStateOf(740.dp)

    @Composable private fun ResizingFrame(content: @Composable () -> Unit) {
        // Simulate keyboard-driven constraints explicitly; do not double-apply the real IME inset.
        Box(Modifier.size(360.dp, height.value).consumeWindowInsets(WindowInsets.ime)) { content() }
    }

    private fun focus() = compose.onNodeWithContentDescription("음성 대신 텍스트 입력").apply {
        performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        assertIsFocused()
    }

    @Test fun realPurposeKeepsFocusAcrossKeyboardResizeWithoutPreparingACall() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val owner = LiveCallOwner(scope, factory = { error("No network allowed") })
        try {
            compose.runOnIdle { assertTrue(owner.begin("131")) }
            compose.setContent { ResizingFrame { MainPhoneApp(AppState(), {}, owner) } }
            val input = focus()
            compose.runOnIdle { height.value = 460.dp }
            input.assertIsFocused()
            compose.runOnIdle { height.value = 740.dp }
            input.assertIsFocused()
            compose.runOnIdle {
                assertEquals("", owner.entry.value?.goal)
                assertNull(owner.entry.value?.confirmation)
                assertFalse(owner.server.state.value.hasSession)
            }
        } finally { compose.runOnIdle { owner.close(); scope.cancel() } }
    }

    @Test fun demoPurposeKeepsFocusAndTextAcrossKeyboardResize() {
        val state = mutableStateOf(AppState(screen = Screen.Purpose, number = "131", agent = true))
        compose.setContent { ResizingFrame { ArsApp(state.value) { state.value = state.value.reduce(it) } } }
        val input = focus()
        input.performTextInput("날씨")
        compose.runOnIdle { height.value = 460.dp }
        input.assertIsFocused().assertTextContains("날씨")
        input.performTextInput(" 조회")
        compose.runOnIdle { height.value = 740.dp }
        input.assertIsFocused().assertTextContains("날씨 조회")
    }

    @Test fun serverAnswerKeepsUnsentDraftAndFocusAcrossKeyboardResize() {
        val sent = mutableListOf<SessionAction.Answer>()
        val request = UserRequest("focus-answer", "clarification", "어느 지역인가요?", "2099-01-01T00:00:00Z")
        compose.setContent { ResizingFrame {
            BServerCallScreen("131", SessionSnapshot("awaiting_user", "입력 대기", pendingRequest = request),
                onEnd = {}, onBack = {}, answerInput = ServerAnswerInput(true, { sent += it }, {}))
        } }
        val input = focus()
        input.performTextInput("부산")
        compose.runOnIdle { height.value = 460.dp }
        input.assertIsFocused().assertTextContains("부산")
        compose.runOnIdle { height.value = 740.dp }
        input.assertIsFocused().assertTextContains("부산")
        assertTrue(sent.isEmpty())
    }
}
