package kr.ac.suwon.arsagent

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Opens only the idle screen. Never clicks health/start/voice/microphone/audio controls. */
class ServerVoiceActivityTest {
    @get:Rule val compose = createAndroidComposeRule<ServerTestActivity>()
    @Test fun realEntryPointStartsWithVoiceAndAudioOffWithoutAskingToRecord() {
        compose.onNodeWithText("음성 대화 켜기").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("ARS 소리 켜기").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("목적 말하기").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("테스트 시작").performScrollTo().assertIsNotEnabled()
    }
}
