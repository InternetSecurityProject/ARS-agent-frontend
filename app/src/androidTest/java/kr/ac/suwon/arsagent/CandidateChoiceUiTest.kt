package kr.ac.suwon.arsagent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.io.File
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import kr.ac.suwon.arsagent.data.api.*
import kr.ac.suwon.arsagent.ui.BServerCallScreen
import kr.ac.suwon.arsagent.ui.CandidateChoiceInput
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CandidateChoiceUiTest {
    @get:Rule val compose = createComposeRule()
    private val request = UserRequest("candidate-27", "clarification", "결제 대금 조회와 결제일 변경 중 어느 쪽인가요?",
        "2099-01-01T00:00:00Z", options = listOf(MenuOption("3", "결제 대금 조회"), MenuOption("#", "결제일 변경")))
    private fun snapshot(r: UserRequest = request) = SessionSnapshot("awaiting_user", "선택 대기", pendingRequest = r)

    @Test fun dynamicOptionsKeepDesignAndProduceTypedSelectionOnceWithoutAdvancingLocally() {
        val sent = mutableListOf<SessionAction.Select>()
        compose.setContent { BServerCallScreen("07012345678", snapshot(), onEnd = {}, onBack = {},
            choiceInput = CandidateChoiceInput(true, { sent.add(it) })) }
        compose.onNodeWithText(request.message).assertIsDisplayed()
        compose.onNodeWithText("결제 대금 조회").assertIsDisplayed()
        compose.onNodeWithText("결제일 변경").assertIsDisplayed()
        compose.onNodeWithText("오늘·내일·모레 단기예보").assertDoesNotExist()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "c-server-candidates.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("결제일 변경").performClick().performClick()
        assertEquals(listOf(SessionAction.Select("candidate-27", "#")), sent)
        compose.onNodeWithText("선택을 전달했어요. 응답을 기다려 주세요.").assertIsDisplayed()
        compose.onNodeWithText("종료").assertIsDisplayed()
        compose.onNodeWithText("통화 종료", substring = true).assertDoesNotExist()
    }

    @Test fun absentCallbackOrUnavailableConnectionCannotSend() {
        val ready = mutableStateOf<CandidateChoiceInput?>(null)
        val sent = mutableListOf<SessionAction.Select>()
        compose.setContent { BServerCallScreen("131", snapshot(), onEnd = {}, onBack = {}, choiceInput = ready.value) }
        compose.onNodeWithText("결제 대금 조회").assertIsNotEnabled().performClick()
        compose.runOnIdle { ready.value = CandidateChoiceInput(false, { sent.add(it) }) }
        compose.onNodeWithText("결제 대금 조회").assertIsNotEnabled().performClick()
        assertTrue(sent.isEmpty())
        compose.runOnIdle { ready.value = CandidateChoiceInput(true, { sent.add(it) }) }
        compose.onNodeWithText("결제 대금 조회").performClick()
        assertEquals(1, sent.size)
    }

    @Test fun criticalActionsOnlyOfferButtonsAndKeepStarAsBusinessOption() {
        val sent = mutableListOf<SessionAction.Select>()
        val critical = request.copy(reason = "critical_action", options = listOf(MenuOption("*", "해지 진행"), MenuOption("#", "유지")))
        compose.setContent { BServerCallScreen("131", snapshot(critical), onEnd = {}, onBack = {},
            choiceInput = CandidateChoiceInput(true, { sent.add(it) })) }
        compose.onNodeWithText("해지 진행").performClick()
        assertEquals(listOf(SessionAction.Select("candidate-27", "*")), sent)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("다시 말하기").assertDoesNotExist()
    }

    @Test fun newRequestReplacesOptionsAndUnlocksButRecompositionDoesNotResend() {
        val current = mutableStateOf(snapshot())
        val sent = mutableListOf<SessionAction.Select>()
        compose.setContent { BServerCallScreen("131", current.value, onEnd = {}, onBack = {},
            choiceInput = CandidateChoiceInput(true, { sent.add(it) })) }
        compose.onNodeWithText("결제 대금 조회").performClick()
        compose.runOnIdle { current.value = snapshot(request.copy(options = listOf(MenuOption("7", "새 후보")))) }
        compose.onNodeWithText("결제 대금 조회").assertDoesNotExist()
        compose.onNodeWithText("새 후보").assertIsNotEnabled().performClick()
        assertEquals(1, sent.size)
        compose.runOnIdle { current.value = snapshot(request.copy(requestId = "candidate-28", options = listOf(MenuOption("7", "새 후보")))) }
        compose.onNodeWithText("새 후보").performClick()
        assertEquals(SessionAction.Select("candidate-28", "7"), sent.last())
        assertEquals(2, sent.size)
    }

    @Test fun explicitOwnerRetryCanUnlockKnownUnacceptedAction() {
        val revision = mutableStateOf(0)
        val sent = mutableListOf<SessionAction.Select>()
        compose.setContent { BServerCallScreen("131", snapshot(), onEnd = {}, onBack = {},
            choiceInput = CandidateChoiceInput(true, { sent.add(it) }, retryVersion = revision.value)) }
        compose.onNodeWithText("결제 대금 조회").performClick()
        compose.onNodeWithText("결제 대금 조회").assertIsNotEnabled()
        compose.runOnIdle { revision.value++ }
        compose.onNodeWithText("결제일 변경").performClick()
        assertEquals(2, sent.size)
    }

    @Test fun invalidExpiredAndEmptyRequestsFailClosed() {
        val current = mutableStateOf(snapshot(request.copy(expiresAt = "not-a-date")))
        val sent = mutableListOf<SessionAction.Select>()
        compose.setContent { BServerCallScreen("131", current.value, onEnd = {}, onBack = {},
            choiceInput = CandidateChoiceInput(true, { sent.add(it) })) }
        compose.onNodeWithText("결제 대금 조회").assertIsNotEnabled().performClick()
        compose.runOnIdle { current.value = snapshot(request.copy(expiresAt = "2000-01-01T00:00:00Z")) }
        compose.onNodeWithText("결제 대금 조회").assertIsNotEnabled().performClick()
        compose.runOnIdle { current.value = snapshot(request.copy(options = emptyList())) }
        compose.onNodeWithText("선택 가능한 메뉴가 없어요.").assertIsDisplayed()
        assertTrue(sent.isEmpty())
    }

    @Test fun requestExpiresWhileVisibleEvenWithoutNewSnapshot() {
        compose.mainClock.autoAdvance = false
        val sent = mutableListOf<SessionAction.Select>()
        val expiring = snapshot(request.copy(expiresAt = Instant.now().plusMillis(500).toString()))
        compose.setContent { BServerCallScreen("131", expiring,
            onEnd = {}, onBack = {}, choiceInput = CandidateChoiceInput(true, { sent.add(it) })) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("선택 시간이 지났어요. 새 안내를 기다려 주세요.").assertIsDisplayed()
        compose.onNodeWithText("결제 대금 조회").assertIsNotEnabled().performClick()
        assertTrue(sent.isEmpty())
    }

    @Test fun longListInCompactFrameScrollsWithoutLosingEndButton() {
        val sent = mutableListOf<SessionAction.Select>()
        val many = request.copy(options = (1..12).map { MenuOption("$it", "긴 후보 업무 $it") })
        compose.setContent { Box(Modifier.size(280.dp, 480.dp)) {
            BServerCallScreen("131", snapshot(many), onEnd = {}, onBack = {}, choiceInput = CandidateChoiceInput(true, { sent.add(it) }))
        } }
        compose.onNodeWithText("긴 후보 업무 12").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("종료").assertIsDisplayed()
        assertEquals(SessionAction.Select("candidate-27", "12"), sent.single())
    }

    @Test fun retainedRequestOutsideAwaitingUserCannotBeSelected() {
        val sent = mutableListOf<SessionAction.Select>()
        compose.setContent { BServerCallScreen("131", snapshot().copy(status = "selecting"), onEnd = {}, onBack = {},
            choiceInput = CandidateChoiceInput(true, { sent.add(it) })) }
        compose.onNodeWithText("결제 대금 조회").assertIsNotEnabled().performClick()
        assertTrue(sent.isEmpty())
    }

    @Test fun resizingAcrossCompactThresholdDoesNotUnlockSubmittedRequest() {
        val height = mutableStateOf(700.dp)
        val sent = mutableListOf<SessionAction.Select>()
        compose.setContent { Box(Modifier.size(320.dp, height.value)) {
            BServerCallScreen("131", snapshot(), onEnd = {}, onBack = {}, choiceInput = CandidateChoiceInput(true, { sent.add(it) }))
        } }
        compose.onNodeWithText("결제 대금 조회").performClick()
        compose.runOnIdle { height.value = 480.dp }
        compose.onNodeWithText("결제 대금 조회").performScrollTo().assertIsNotEnabled().performClick()
        compose.runOnIdle { height.value = 700.dp }
        compose.onNodeWithText("결제일 변경").performScrollTo().assertIsNotEnabled().performClick()
        assertEquals(1, sent.size)
    }

    @Test fun temporarilyRemovedRequestDoesNotUnlockItWhenRestored() {
        val current = mutableStateOf(snapshot())
        val sent = mutableListOf<SessionAction.Select>()
        compose.setContent { BServerCallScreen("131", current.value, onEnd = {}, onBack = {},
            choiceInput = CandidateChoiceInput(true, { sent.add(it) })) }
        compose.onNodeWithText("결제 대금 조회").performClick()
        compose.runOnIdle { current.value = SessionSnapshot("selecting", "메뉴로 이동 중") }
        compose.onNodeWithText("결제 대금 조회").assertDoesNotExist()
        compose.runOnIdle { current.value = snapshot() }
        compose.onNodeWithText("결제일 변경").assertIsNotEnabled().performClick()
        assertEquals(1, sent.size)
    }

    @Test fun temporarilyRemovedCallbackDoesNotResetExplicitRetryReceipt() {
        val sent = mutableListOf<SessionAction.Select>()
        val input = CandidateChoiceInput(true, { sent.add(it) }, retryVersion = 1)
        val current = mutableStateOf<CandidateChoiceInput?>(input)
        compose.setContent { BServerCallScreen("131", snapshot(), onEnd = {}, onBack = {}, choiceInput = current.value) }
        compose.onNodeWithText("결제 대금 조회").performClick()
        compose.runOnIdle { current.value = null }
        compose.onNodeWithText("결제일 변경").assertIsNotEnabled()
        compose.runOnIdle { current.value = input }
        compose.onNodeWithText("결제일 변경").assertIsNotEnabled().performClick()
        assertEquals(1, sent.size)
    }
}
