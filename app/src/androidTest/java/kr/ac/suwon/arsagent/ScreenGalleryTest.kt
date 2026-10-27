package kr.ac.suwon.arsagent

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class ScreenGalleryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun jumpToSecureInputAndKeepItFrozen() {
        compose.setContent { B04Preview() }
        compose.onNodeWithText("화면 선택").performClick()
        compose.onNodeWithText("C").performClick()
        compose.onNodeWithText("C03 · 민감정보 입력").performScrollTo().performClick()
        compose.onNodeWithText("생년월일 6자리 입력").assertIsDisplayed()
        compose.onNodeWithText("입력 완료").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithContentDescription("키 1").performClick()
        compose.mainClock.advanceTimeBy(15_000)
        compose.onNodeWithText("○○○○○○").assertIsDisplayed()
        compose.onNodeWithText("종료").assertIsDisplayed()
    }

    @Test fun switchBetweenMobileAndArsContactDetails() {
        compose.setContent { B04Preview() }
        compose.onNodeWithText("화면 선택").performClick()
        compose.onNodeWithText("A").performClick()
        compose.onNodeWithText("A · 연락처 상세 (010)").performScrollTo().performClick()
        compose.onNodeWithText("010-1234-5678").assertIsDisplayed()
        compose.onNodeWithText("일반 전화").assertIsDisplayed()
        compose.onNodeWithText("ARS Agent 전화").assertDoesNotExist()

        compose.onNodeWithText("화면 선택").performClick()
        compose.onNodeWithText("A · 연락처 상세 (070)").performScrollTo().performClick()
        compose.onNodeWithText("070-0000-0000").assertIsDisplayed()
        compose.onNodeWithText("일반 전화").assertIsDisplayed()
        compose.onNodeWithText("ARS Agent 전화").assertIsDisplayed()
    }

    @Test fun selectedProgressStaysVisibleWithoutAutoAdvancing() {
        compose.setContent { B04Preview() }
        compose.mainClock.advanceTimeBy(15_000)
        compose.onNodeWithText("이동 경로").assertIsDisplayed()
        compose.onNodeWithText("화면 선택").performClick()
        compose.onNodeWithText("B05 · 기관 안내").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(15_000)
        compose.onNodeWithText("기관 안내").assertIsDisplayed()
        compose.onNodeWithText("강수확률은", substring = true).assertIsDisplayed()
    }

    @Test fun groupTabsFilterScreensAndResetScrollWithoutHidingTabs() {
        compose.setContent { B04Preview() }
        compose.onNodeWithText("화면 선택").performClick()
        compose.onNodeWithText("B").assertIsSelected()
        compose.onNodeWithText("A · 키패드 (빈 번호)").assertDoesNotExist()

        compose.onNodeWithText("A").performClick()
        compose.onNodeWithText("A · 일반 통화 종료 (070)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("C").assertIsDisplayed().performClick()
        compose.onNodeWithText("C01 · 후보 선택").assertIsDisplayed()
        compose.onNodeWithText("C04 · 수동 키패드").assertIsDisplayed()
        compose.onNodeWithText("A · 일반 통화 종료 (070)").assertDoesNotExist()

        compose.onNodeWithText("D").performClick()
        compose.onNodeWithText("D01 · 상담원 연결 대기").assertIsDisplayed().performClick()
        compose.onNodeWithText("상담원 연결을 기다리고 있어요").assertIsDisplayed()
        compose.onNodeWithText("화면 선택").performClick()
        compose.onNodeWithText("D").assertIsSelected()
    }
}
