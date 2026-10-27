package kr.ac.suwon.arsagent

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Entry only: no key, permission, microphone, health or actual dial interaction. */
class MainLiveEntryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun mainAppDefaultsToRealArsWithoutChangingTheNormalPhoneDemo() {
        compose.onNodeWithText("실제 ARS").assertIsDisplayed()
        compose.onNodeWithText("일반 전화").assertIsDisplayed().assertIsNotEnabled()
    }
}
