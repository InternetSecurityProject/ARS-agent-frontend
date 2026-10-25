package kr.ac.suwon.arsagent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import kr.ac.suwon.arsagent.model.AppState
import kr.ac.suwon.arsagent.model.Phase
import kr.ac.suwon.arsagent.model.Screen
import kr.ac.suwon.arsagent.ui.ArsApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TextWrappingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun routeUsesAvailableWidthAndReflowsInNarrowWindows() {
        val width = mutableStateOf(400.dp)
        compose.setContent {
            Box(Modifier.width(width.value).fillMaxHeight()) {
                ArsApp(AppState(screen = Screen.Call, number = "131", agent = true, phase = Phase.Selecting), {})
            }
        }
        fun routeLayout(): TextLayoutResult {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("경기 지역", substring = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single()
        }

        val wide = routeLayout()
        assertEquals("The route should use a single line when it fits", 1, wide.lineCount)
        assertFits(wide)

        compose.runOnIdle { width.value = 260.dp }
        val narrow = routeLayout()
        assertTrue("The route should wrap as the available width shrinks", narrow.lineCount > wide.lineCount)
        assertEquals(wide.layoutInput.text, narrow.layoutInput.text)
        assertFits(narrow)
    }

    private fun assertFits(layout: TextLayoutResult) {
        assertFalse("All route lines should fit vertically", layout.didOverflowHeight)
        repeat(layout.lineCount) { line ->
            // Glyph positions are fractional pixels; the measured node size is integral.
            assertTrue("Route line $line exceeds its width: right=${layout.getLineRight(line)}, size=${layout.size}",
                layout.getLineRight(line) <= layout.size.width + 1f)
            assertFalse("The complete route should remain visible", layout.isLineEllipsized(line))
        }
    }
}
