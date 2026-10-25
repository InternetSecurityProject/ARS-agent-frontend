package kr.ac.suwon.arsagent

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kr.ac.suwon.arsagent.model.*
import kr.ac.suwon.arsagent.ui.ArsApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DetailBackUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun androidBackFromCallDetailsReturnsToCallList() = verifyBack(PhoneTab.Calls, true)
    @Test fun androidBackFromContactDetailsReturnsToContactList() = verifyBack(PhoneTab.Contacts, true)
    @Test fun screenBackFromCallDetailsReturnsToCallList() = verifyBack(PhoneTab.Calls, false)
    @Test fun screenBackFromContactDetailsReturnsToContactList() = verifyBack(PhoneTab.Contacts, false)

    private fun verifyBack(origin: PhoneTab, androidBack: Boolean) {
        val state = mutableStateOf(AppState(tab = origin))
        lateinit var dispatcher: OnBackPressedDispatcher
        compose.setContent {
            dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            ArsApp(state.value) { state.value = state.value.reduce(it) }
        }
        compose.onNodeWithText("김민수").performClick()
        val backLabel = if (origin == PhoneTab.Calls) "‹ 통화" else "‹ 연락처"
        compose.onNodeWithText(backLabel).assertIsDisplayed()
        compose.onNodeWithText("010-1234-5678").assertIsDisplayed()
        if (androidBack) compose.runOnIdle { dispatcher.onBackPressed() }
        else compose.onNodeWithText(backLabel).performClick()
        compose.onNodeWithText(if (origin == PhoneTab.Calls) "통화 기록 검색" else "이름 또는 전화번호 검색")
            .assertIsDisplayed()
        compose.onNodeWithText(backLabel).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(Screen.Home, state.value.screen)
            assertEquals(origin, state.value.tab)
        }
    }
}
