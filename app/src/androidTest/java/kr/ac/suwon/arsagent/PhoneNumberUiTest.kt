package kr.ac.suwon.arsagent

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kr.ac.suwon.arsagent.model.*
import kr.ac.suwon.arsagent.ui.ArsApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PhoneNumberUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun contactsFindRawFormattedAndSpacedNumbersWithoutBreakingNameSearch() {
        verifySearch(PhoneTab.Contacts)
    }

    @Test fun callsFindRawFormattedAndSpacedNumbersWithoutBreakingNameSearch() {
        verifySearch(PhoneTab.Calls)
    }

    private fun verifySearch(tab: PhoneTab) {
        compose.setContent { ArsApp(AppState(tab = tab), {}) }
        val search = compose.onNode(hasSetTextAction())
        listOf("01012345678", "010-1234-5678", "010 1234 5678", "12345678", "김민수").forEach { query ->
            search.performTextReplacement(query)
            compose.onNode(hasText("김민수") and !hasSetTextAction()).assertIsDisplayed()
            compose.onNodeWithText("박지연").assertDoesNotExist()
        }
        listOf("없는이름", "---", "010abc").forEach { query ->
            search.performTextReplacement(query)
            compose.onNodeWithText("검색 결과가 없어요.").assertIsDisplayed()
        }
        search.performTextReplacement("ars")
        compose.onNodeWithText("테스트 ARS (예시)").assertIsDisplayed()
        search.performTextReplacement("")
        compose.onNodeWithText("김민수").assertIsDisplayed()
        compose.onNodeWithText("박지연").assertIsDisplayed()
    }

    @Test fun dialingAddsVisualHyphensAndDeleteStillRemovesOneDigit() {
        val state = mutableStateOf(AppState())
        compose.setContent { ArsApp(state.value) { state.value = state.value.reduce(it) } }
        "0101".forEach { compose.onNodeWithContentDescription("키 $it").performClick() }
        compose.onNodeWithText("010-1").assertIsDisplayed()
        compose.onNodeWithText("⌫").performClick()
        compose.onNodeWithText("010", substring = false).assertIsDisplayed()
        "12345".forEach { compose.onNodeWithContentDescription("키 $it").performClick() }
        compose.onNodeWithText("010-1234-5").assertIsDisplayed()
        compose.onNodeWithText("⌫").performClick()
        compose.onNodeWithText("010-1234", substring = false).assertIsDisplayed()
        "5678".forEach { compose.onNodeWithContentDescription("키 $it").performClick() }
        compose.onNodeWithText("010-1234-5678").assertIsDisplayed()
        compose.runOnIdle { assertEquals("01012345678", state.value.number) }
        compose.onNodeWithText("일반 전화").performClick()
        compose.runOnIdle { assertEquals("01012345678", state.value.number) }
    }

    @Test fun inCallKeypadDoesNotInsertHyphensIntoArsInput() {
        val state = mutableStateOf(AppState(screen = Screen.Call, number = "131", agent = true,
            phase = Phase.Selecting, keypadOpen = true))
        compose.setContent { ArsApp(state.value) { state.value = state.value.reduce(it) } }
        "01012345*#".forEach { compose.onNodeWithContentDescription("키 $it").performClick() }
        compose.onNodeWithText("01012345*#").assertIsDisplayed()
        compose.runOnIdle { assertEquals("01012345*#", state.value.manualDigits) }
    }
}
