package kr.ac.suwon.arsagent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kr.ac.suwon.arsagent.data.api.*
import kr.ac.suwon.arsagent.livecall.LiveCallOwner
import kr.ac.suwon.arsagent.livecall.LiveEntry
import kr.ac.suwon.arsagent.model.*
import kr.ac.suwon.arsagent.servertest.ServerTestState
import kr.ac.suwon.arsagent.ui.*
import kr.ac.suwon.arsagent.ui.live.LiveSessionScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Pixel assertions observe visible dots, not test-only animation state or speech/network calls. */
class ListeningProgressUiTest {
    @get:Rule val compose = createComposeRule()
    private val title = "ARS 안내를 듣는 중"
    private fun ink(): Int {
        val pixels = compose.onNodeWithTag("listening-dots").captureToImage().toPixelMap()
        return (0 until pixels.height).sumOf { y -> (0 until pixels.width).count { x ->
            val c = pixels[x, y]
            c.alpha > .5f && c.red < .5f && c.green < .5f && c.blue < .5f
        } }
    }
    private fun advance(milliseconds: Long = 500) { compose.mainClock.advanceTimeBy(milliseconds); compose.waitForIdle() }

    @Test fun dotsCycleEveryHalfSecondWithoutMovingTitleEvenAtNarrowWidth() {
        compose.mainClock.autoAdvance = false
        compose.setContent { ArsTheme { Box(Modifier.width(220.dp).background(Color.White)) { ListeningProgressTitle(true) } } }
        compose.mainClock.advanceTimeByFrame()
        val bounds = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot
        val dotBounds = compose.onNodeWithTag("listening-dots").fetchSemanticsNode().boundsInRoot
        assertEquals(0, ink())
        advance(480); assertEquals(0, ink())
        advance(32); val one = ink(); assertTrue(one > 0)
        advance(); val two = ink(); assertTrue(two > one)
        advance(); assertTrue(ink() > two)
        advance(); assertEquals(0, ink())
        assertEquals(bounds, compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot)
        assertEquals(dotBounds, compose.onNodeWithTag("listening-dots").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("...").assertDoesNotExist() // decorative dots must not be read as changing speech
    }

    @Test fun liveStreamLossAndPendingInputStopDotsAndReconnectRestartsThem() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val owner = LiveCallOwner(scope, factory = { error("No real server calls allowed") })
        val state = mutableStateOf(ServerTestState(hasSession = true, streamConnected = true,
            snapshot = SessionSnapshot("listening", "예시 안내")))
        compose.mainClock.autoAdvance = false
        try {
            compose.setContent { LiveSessionScreen(LiveEntry("131", 1), state.value, owner, null, null) {} }
            compose.mainClock.advanceTimeByFrame()
            advance(); assertTrue(ink() > 0)
            compose.runOnIdle { state.value = state.value.copy(streamConnected = false) }
            advance(); assertEquals(0, ink()); advance(2_000); assertEquals(0, ink())
            compose.runOnIdle { state.value = state.value.copy(streamConnected = true) }
            advance(); advance(); assertTrue(ink() > 0)
            compose.runOnIdle { state.value = state.value.copy(snapshot = state.value.snapshot.copy(
                pendingRequest = UserRequest("pending", "clarification", "어느 지역인가요?", "2099-01-01T00:00:00Z"))) }
            advance(); assertEquals(0, ink()); advance(2_000); assertEquals(0, ink())
            compose.runOnIdle { state.value = state.value.copy(snapshot = SessionSnapshot("awaiting_user", "입력 대기")) }
            advance(); compose.onNodeWithTag("listening-dots").assertDoesNotExist()
        } finally { compose.runOnIdle { owner.close(); scope.cancel() } }
    }

    @Test fun demoUsesTheSameAnimatedTitleAndRemovesItWhenWaitingForAnswer() {
        val state = mutableStateOf(AppState(screen = Screen.Call, agent = true, number = "131", phase = Phase.Listening))
        compose.mainClock.autoAdvance = false
        compose.setContent { ArsApp(state.value) { error("No demo action requested") } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(title).assertIsDisplayed()
        advance(); assertTrue(ink() > 0)
        compose.runOnIdle { state.value = state.value.copy(phase = Phase.Answer) }
        advance(); compose.onNodeWithTag("listening-dots").assertDoesNotExist()
    }

    @Test fun galleryAnimatesDotsWithoutAutomaticallyAdvancingTheSelectedScreen() {
        compose.setContent { B04Preview() }
        compose.onNodeWithText("화면 선택").performClick()
        compose.onNodeWithText("B03 · ARS 안내").performScrollTo().assertIsDisplayed()
        // Compose's auto-advance policy cancels infinite effects; use a manual clock before opening B03.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("B03 · ARS 안내").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.mainClock.advanceTimeByFrame()
        val frames = (1..8).map { advance(); ink() }
        assertTrue("Gallery must show changing visible dots", frames.toSet().size > 1)
        advance(15_000)
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText("이동 경로").assertDoesNotExist()
    }
}
