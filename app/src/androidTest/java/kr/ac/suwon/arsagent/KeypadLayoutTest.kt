package kr.ac.suwon.arsagent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kr.ac.suwon.arsagent.ui.ArsTheme
import kr.ac.suwon.arsagent.ui.PhoneKeypad
import kr.ac.suwon.arsagent.ui.ArsApp
import kr.ac.suwon.arsagent.model.AppState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class KeypadLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun preCallKeypadMatchesInCallSizingWithCallButtonsAndTabsVisible() {
        compose.setContent { ArsApp(AppState(number = "131"), {}) }
        val first = compose.onNodeWithContentDescription("키 1")
        first.assertWidthIsEqualTo(72.dp).assertHeightIsEqualTo(72.dp)
        val one = first.getUnclippedBoundsInRoot()
        val two = compose.onNodeWithContentDescription("키 2").getUnclippedBoundsInRoot()
        val four = compose.onNodeWithContentDescription("키 4").getUnclippedBoundsInRoot()
        assertTrue("Pre-call row spacing must be 24dp", kotlin.math.abs((four.top - one.bottom).value - 24f) < .5f)
        assertTrue("Pre-call column spacing must be 32dp", kotlin.math.abs((two.left - one.right).value - 32f) < .5f)
        compose.onNodeWithText("일반 전화").assertIsDisplayed()
        compose.onNodeWithText("ARS Agent 전화").assertIsDisplayed()
        compose.onNodeWithText("통화").assertIsDisplayed()
        compose.onNodeWithText("연락처").assertIsDisplayed()
        compose.onNodeWithText("키패드").assertIsDisplayed()
    }

    @Test fun inCallKeypadUsesSamePhoneGridWithAccessibleTouchTargets() {
        compose.setContent {
            ArsTheme { Box(Modifier.size(360.dp, 640.dp)) { PhoneKeypad({}, compact = true) } }
        }
        val first = compose.onNodeWithContentDescription("키 1")
        first.assertWidthIsEqualTo(72.dp).assertHeightIsEqualTo(72.dp)
        val one = first.getUnclippedBoundsInRoot()
        val two = compose.onNodeWithContentDescription("키 2").getUnclippedBoundsInRoot()
        val four = compose.onNodeWithContentDescription("키 4").getUnclippedBoundsInRoot()
        assertTrue("Row spacing must be 24dp", kotlin.math.abs((four.top - one.bottom).value - 24f) < .5f)
        assertTrue("Column spacing must be 32dp", kotlin.math.abs((two.left - one.right).value - 32f) < .5f)
        compose.onNodeWithContentDescription("키 #").assertIsDisplayed()
    }
}
