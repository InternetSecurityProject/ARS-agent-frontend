package kr.ac.suwon.arsagent

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kr.ac.suwon.arsagent.updates.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TeamUpdatesUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun consentCanBeDeferredWithoutStartingSignIn() {
        var logins = 0
        var dismissed by mutableStateOf(false)
        compose.setContent {
            if (!dismissed) UpdatePrompt(UpdateUi(UpdateStage.Consent), { logins++ }, {}, { dismissed = true })
        }
        compose.onNodeWithText("나중에").performClick()
        compose.onNodeWithText("팀 앱 업데이트 안내").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, logins); assertTrue(dismissed) }
    }
    @Test fun updateNeedsExplicitHandoffAndLeavesNoDownloadModal() {
        var ui by mutableStateOf(UpdateUi(UpdateStage.Available, "0.1.0 (12)"))
        var installs = 0
        compose.setContent { UpdatePrompt(ui, {}, { installs++; ui = UpdateUi() }, {}) }
        compose.onNodeWithText("0.1.0 (12)", substring = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, installs) }
        compose.onNodeWithText("업데이트").performClick()
        compose.onNodeWithText("새 버전이 있어요").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, installs) }
    }
}
