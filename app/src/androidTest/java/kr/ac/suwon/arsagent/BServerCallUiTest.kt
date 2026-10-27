package kr.ac.suwon.arsagent

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kr.ac.suwon.arsagent.data.api.*
import kr.ac.suwon.arsagent.ui.BServerCallScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BServerCallUiTest {
    @get:Rule val compose = createComposeRule()
    private val selected = SessionSnapshot("selecting", "카드 메뉴로 이동하고 있어요",
        path = listOf(PathStep(1, "긴 ARS 원문", selected = MenuOption("5", "카드 서비스"), actor = "ai")),
        captions = listOf(Caption(1, "ars", "카드 서비스는 5번입니다", "now")))

    @Test fun serverPathAndCaptionReplaceWeatherExamplesInsideExistingCallFrame() {
        compose.setContent { BServerCallScreen("07012345678", selected, onEnd = {}, onBack = {}) }
        compose.onNodeWithText("070-1234-5678").assertIsDisplayed()
        compose.onNodeWithText("카드 서비스 (5번)").assertIsDisplayed()
        compose.onNodeWithText("카드 서비스는 5번입니다").assertIsDisplayed()
        compose.onNodeWithText("수원", substring = true).assertDoesNotExist()
        compose.onNodeWithText("강수확률", substring = true).assertDoesNotExist()
        compose.onNodeWithText("종료").assertIsDisplayed()
        capture("b-server-selecting")
    }

    @Test fun newSnapshotReplacesDisplayedCaptionAndRouteWithoutLocalTimers() {
        val snapshot = mutableStateOf(selected)
        compose.setContent { BServerCallScreen("131", snapshot.value, onEnd = {}, onBack = {}) }
        compose.runOnIdle { snapshot.value = SessionSnapshot("listening", "새 안내를 듣고 있어요") }
        compose.onNodeWithText("새 안내를 듣고 있어요").assertIsDisplayed()
        compose.onNodeWithText("카드 서비스 (5번)").assertDoesNotExist()
        compose.onNodeWithText("카드 서비스는 5번입니다").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(20_000)
        compose.onNodeWithText("새 안내를 듣고 있어요").assertIsDisplayed()
        compose.onNodeWithText("안내 결과").assertDoesNotExist()
    }

    @Test fun summaryDoesNotEndCallButCompletedResultShowsServerDuration() {
        val result = mutableStateOf<CompletedResult?>(null)
        val summary = SessionSnapshot("agent_done", "안내가 끝났어요",
            summary = ResultSummary("result", "이번 달 결제금액입니다", listOf(KeyInfo("결제금액", "35,000원"))))
        compose.setContent { BServerCallScreen("131", summary, result.value, onEnd = {}, onBack = {}) }
        compose.onNodeWithText("이번 달 결제금액입니다").assertIsDisplayed()
        compose.onNodeWithText("결제금액 · 35,000원").assertIsDisplayed()
        compose.onNodeWithText("종료").assertIsDisplayed()
        compose.onNodeWithText("통화 종료", substring = true).assertDoesNotExist()
        compose.runOnIdle { result.value = CompletedResult("success", "completed", "최종 결제금액 안내", durationSec = 65) }
        compose.onNodeWithText("통화 종료 · 01:05").assertIsDisplayed()
        compose.onNodeWithText("최종 결제금액 안내").assertIsDisplayed()
        compose.onNodeWithText("이번 달 결제금액입니다").assertDoesNotExist()
        compose.onNodeWithText("키패드로 돌아가기").assertIsDisplayed()
        capture("b-server-ended")
    }

    @Test fun unsupportedSecondaryControlsAreVisibleButCannotTriggerDemoActions() {
        var endRequests = 0
        compose.setContent { BServerCallScreen("131", selected, onEnd = { endRequests++ }, onBack = {}) }
        listOf("ARS 소리 켜기", "키패드", "오디오").forEach { compose.onNodeWithText(it).assertIsDisplayed().assertIsNotEnabled() }
        compose.onNodeWithText("종료").performClick()
        assertEquals(1, endRequests)
        // The owner, not the renderer, decides when cancellation is confirmed.
        compose.onNodeWithText("종료").assertIsDisplayed()
        compose.onNodeWithText("통화 종료", substring = true).assertDoesNotExist()
    }

    @Test fun finalBackIsACallbackRatherThanAnImplicitLocalNavigation() {
        var backRequests = 0
        compose.setContent { BServerCallScreen("131", SessionSnapshot("ended", "종료됨"),
            onEnd = {}, onBack = { backRequests++ }) }
        compose.onNodeWithText("키패드로 돌아가기").performClick()
        assertEquals(1, backRequests)
        compose.onNodeWithText("종료됨").assertIsDisplayed()
    }

    @Test fun unknownStatusAndPendingInputDoNotRenderHardcodedChoiceOrSecureKeypad() {
        val snapshot = mutableStateOf(SessionSnapshot("future_state", "서버의 새 상태입니다"))
        compose.setContent { BServerCallScreen("131", snapshot.value, onEnd = {}, onBack = {}) }
        compose.onNodeWithText("서버의 새 상태입니다").assertIsDisplayed()
        compose.onNodeWithText("서버 상태 · future_state").assertIsDisplayed()
        compose.runOnIdle { snapshot.value = SessionSnapshot("awaiting_user", "계좌정보가 필요해요",
            pendingRequest = UserRequest("r9", "secure_input", "계좌번호를 직접 입력해 주세요", "2099-01-01T00:00:00Z", label = "계좌번호")) }
        compose.onNodeWithText("계좌번호를 직접 입력해 주세요").assertIsDisplayed()
        compose.onNodeWithText("입력 기능 연결 전이거나 연결을 확인하고 있어요.").assertIsDisplayed()
        compose.onNodeWithText("생년월일 6자리 입력").assertDoesNotExist()
        compose.onNodeWithText("오늘·내일·모레 단기예보").assertDoesNotExist()
    }

    @Test fun narrowShortWindowKeepsFooterVisibleAndLongServerTextScrollable() {
        compose.setContent {
            Box(Modifier.size(280.dp, 480.dp)) {
                BServerCallScreen("07012345678", selected.copy(captions = listOf(Caption(1, "ars", "긴 기관 안내 ".repeat(60), "now"))),
                    onEnd = {}, onBack = {})
            }
        }
        compose.onNodeWithText("종료").assertIsDisplayed()
        compose.onNodeWithText("카드 서비스 (5번)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("ARS 소리 켜기").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("종료").assertIsDisplayed()
    }

    @Test fun missingServerPhoneNumberDoesNotPretendToBeTheWeatherCenter() {
        compose.setContent { BServerCallScreen("", SessionSnapshot("dialing", "연결 준비"), onEnd = {}, onBack = {}) }
        compose.onNodeWithText("131").assertDoesNotExist()
        compose.onNodeWithText("번호 확인 전").assertIsDisplayed()
    }

    @Test fun compactServerLayoutStillShowsPhoneAndConfirmedDuration() {
        compose.setContent {
            Box(Modifier.size(280.dp, 480.dp)) {
                BServerCallScreen("07012345678", SessionSnapshot("ended", "종료"),
                    CompletedResult("success", "completed", "최종 안내", durationSec = 65), onEnd = {}, onBack = {})
            }
        }
        compose.onNodeWithText("070-1234-5678").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("통화 종료 · 01:05").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("키패드로 돌아가기").assertIsDisplayed()
    }

    @Test fun retainedSummaryDoesNotHideANewerStatusOrUnknownStatusCode() {
        val snapshot = mutableStateOf(SessionSnapshot("agent_done", "업무 안내 완료",
            summary = ResultSummary("result", "이전 조회 안내")))
        compose.setContent { BServerCallScreen("131", snapshot.value, onEnd = {}, onBack = {}) }
        compose.runOnIdle { snapshot.value = snapshot.value.copy(status = "waiting_agent", message = "서버가 상담원을 연결하고 있어요") }
        compose.onNodeWithText("상담원 연결 대기").assertIsDisplayed()
        compose.onNodeWithText("서버가 상담원을 연결하고 있어요").assertIsDisplayed()
        compose.runOnIdle { snapshot.value = snapshot.value.copy(status = "future_state", message = "새로운 진행 상황") }
        compose.onNodeWithText("서버 상태 · future_state").assertIsDisplayed()
        compose.onNodeWithText("새로운 진행 상황").assertIsDisplayed()
        compose.onNodeWithText("이전 조회 안내").assertIsDisplayed()
        compose.onNodeWithText("종료").assertIsDisplayed()
    }

    @Test fun finalKeyInformationAndHandoffTextDoNotRequireSummaryText() {
        val result = mutableStateOf(CompletedResult("partial", "agent_call_ended", summary = null,
            keyInfo = listOf(KeyInfo("접수번호", "ABC-27")), handoffScript = "카드 문의 내용을 전달해 주세요", durationSec = 12))
        compose.setContent { BServerCallScreen("131", SessionSnapshot("ended", "종료"), result.value, onEnd = {}, onBack = {}) }
        compose.onNodeWithText("접수번호 · ABC-27").assertIsDisplayed()
        compose.onNodeWithText("카드 문의 내용을 전달해 주세요").assertIsDisplayed()
        compose.runOnIdle { result.value = result.value.copy(summary = " ", keyInfo = listOf(KeyInfo("접수번호", "ABC-28"))) }
        compose.onNodeWithText("접수번호 · ABC-28").assertIsDisplayed()
        compose.onNodeWithText("카드 문의 내용을 전달해 주세요").assertIsDisplayed()
    }

    /** Synthetic API fixtures only. No Activity credentials or real sessions are captured. */
    private fun capture(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
