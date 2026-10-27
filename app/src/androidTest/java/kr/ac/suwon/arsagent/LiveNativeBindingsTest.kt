package kr.ac.suwon.arsagent

import android.media.AudioManager
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import kr.ac.suwon.arsagent.livecall.AndroidAudioRoutes
import kr.ac.suwon.arsagent.livecall.AudioRouting
import kr.ac.suwon.arsagent.servertest.ServerTestState
import kr.ac.suwon.arsagent.servertest.voice.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LiveNativeBindingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun purposeMicRequestsDisclosureAndCancelNeverStartsRecognitionOrNetwork() {
        val state = ServerTestState()
        compose.setContent {
            ServerVoiceBinding(state, { fail("No call or answer may be sent") }, VoiceSessionRelay(), { state }) { voice ->
                Button({ voice.goal { fail("Recognition must not start before consent") } }) { Text("목적 말하기 테스트") }
            }
        }
        compose.onNodeWithText("목적 말하기 테스트").performClick()
        compose.onNodeWithText("Agent와 음성으로 대화할까요?").assertIsDisplayed()
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithText("Agent와 음성으로 대화할까요?").assertDoesNotExist()
    }

    @Test fun nativeCommunicationModeIsRestoredOnReleaseWithoutMicOrRemoteCall() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val audio = context.getSystemService(AudioManager::class.java)
        val before = audio.mode
        val routing = AudioRouting(AndroidAudioRoutes(context))
        try {
            compose.runOnIdle { routing.activate() }
            assertTrue(routing.state.value.active)
            assertEquals(AudioManager.MODE_IN_COMMUNICATION, audio.mode)
        } finally { compose.runOnIdle { routing.close() } }
        assertEquals(before, audio.mode)
    }
}
