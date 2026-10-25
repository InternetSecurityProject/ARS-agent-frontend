package kr.ac.suwon.arsagent

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kr.ac.suwon.arsagent.model.*
import kr.ac.suwon.arsagent.ui.ArsApp
import org.junit.Rule
import org.junit.Test

/** Test-only, synthetic states. Captures never contain real user input. */
class ScreenCaptureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captureRepresentativeScreensAndSmallLargeFontLayout() {
        val state = mutableStateOf(AppState(number = "131"))
        val compact = mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (compact.value) 1.3f else 1f)) {
                Box(Modifier.size(360.dp, if (compact.value) 640.dp else 800.dp)) {
                    ArsApp(state.value) { state.value = state.value.reduce(it) }
                }
            }
        }
        val samples = listOf(
            "keypad" to AppState(number = "131"),
            "purpose" to AppState(screen = Screen.Purpose, number = "131", agent = true),
            "progress" to AppState(screen = Screen.Call, number = "131", agent = true, phase = Phase.Selecting, elapsedSeconds = 18),
            "secure" to AppState(screen = Screen.Call, number = "131", agent = true, phase = Phase.Secure),
            "normal" to AppState(screen = Screen.Call, number = "01012345678", phase = Phase.Listening),
        )
        samples.forEach { (name, sample) ->
            compose.runOnIdle { state.value = sample }
            compose.waitForIdle()
            if (sample.screen == Screen.Call) compose.onNodeWithText("종료").assertIsDisplayed()
            capture(name)
        }
        compose.runOnIdle { compact.value = true; state.value = samples[2].second }
        compose.onNodeWithText("종료").assertIsDisplayed()
        compose.onNodeWithText("ARS 소리 켜기").assertIsDisplayed()
        capture("progress-small-large-font")
        compose.runOnIdle { state.value = samples[3].second }
        compose.onNodeWithText("종료").assertIsDisplayed()
        compose.onNodeWithContentDescription("키 #").performScrollTo().assertIsDisplayed()
        capture("secure-small-large-font")
    }

    private fun capture(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
