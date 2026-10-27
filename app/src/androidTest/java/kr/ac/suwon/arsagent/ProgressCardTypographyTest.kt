package kr.ac.suwon.arsagent

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import kr.ac.suwon.arsagent.data.api.*
import kr.ac.suwon.arsagent.model.*
import kr.ac.suwon.arsagent.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The body font must not leak into headings, labels, editable text or actions. */
class ProgressCardTypographyTest {
    @get:Rule val compose = createComposeRule()
    private val batang = FontFamily(Font(R.font.gowun_batang_regular))
    private fun font(node: SemanticsNodeInteraction): FontFamily? {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().layoutInput.style.fontFamily
    }
    private fun body(text: String) = assertEquals(batang, font(compose.onNodeWithText(text)))
    private fun sans(text: String) = assertNotEquals(batang, font(compose.onNodeWithText(text)))

    @Test fun serverCardUsesBatangForParagraphsButNotHeadingsLabelsOrControls() {
        compose.setContent { BServerCallScreen("131", SessionSnapshot("selecting", "이동 중",
            path = listOf(PathStep(1, "메뉴", selected = MenuOption("1", "단기예보"), actor = "ai")),
            captions = listOf(Caption(1, "ars", "오늘의 날씨 안내입니다", "now"))), onEnd = {}, onBack = {}) }
        body("단기예보 (1번)"); body("오늘의 날씨 안내입니다")
        listOf("131", "메뉴 선택 중", "이동 경로", "ARS 안내", "종료", "키패드").forEach(::sans)
    }

    @Test fun demoAndGalleryShareTheSameBodyFontWithoutChangingInputOrPlaceholder() {
        val state = mutableStateOf(AppState(screen = Screen.Call, agent = true, number = "131", phase = Phase.Listening))
        compose.setContent { ArsApp(state.value) {} }
        body("“단기예보는 1번, 현재 날씨는 3번…”")
        sans("ARS 안내를 듣는 중"); sans("ARS 안내")
        compose.runOnIdle { state.value = state.value.copy(phase = Phase.Answer) }
        body("답변을 듣고 있어요. 내용이 확인되면 자동으로 진행해요.")
        sans("어느 지역의 날씨인가요?"); sans("지역을 말씀해 주세요")
        val field = compose.onNodeWithContentDescription("음성 대신 텍스트 입력")
        field.performTextInput("수원")
        assertNotEquals(batang, font(field))
    }

    @Test fun galleryB04MatchesFigmaBodyAndKeepsBlueEyebrowInSans() {
        compose.setContent { B04Preview() }
        body("경기 지역 → 수원 → 단기예보 (1번)")
        sans("이동 경로"); sans("131"); sans("종료")
    }

    @Test fun candidateInstructionsUseBodyFontButChoiceButtonsKeepTheirExistingFont() {
        val request = UserRequest("font-choice", "clarification", "어느 업무인가요?", "2099-01-01T00:00:00Z",
            reason = "critical_action", options = listOf(MenuOption("3", "카드 발급")))
        compose.setContent { BServerCallScreen("131", SessionSnapshot("awaiting_user", "입력 대기", pendingRequest = request),
            onEnd = {}, onBack = {}, choiceInput = CandidateChoiceInput(true, {})) }
        body("어느 업무인가요?"); body("중요한 선택이에요. 버튼으로 선택해 주세요."); body("시간 내 선택해 주세요.")
        sans("카드 발급"); sans("3번")
    }
}
