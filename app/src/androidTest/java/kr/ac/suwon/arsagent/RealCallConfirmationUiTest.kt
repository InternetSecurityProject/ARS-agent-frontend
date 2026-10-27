package kr.ac.suwon.arsagent

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import kr.ac.suwon.arsagent.data.api.HealthResponse
import kr.ac.suwon.arsagent.servertest.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic UI commands only. No controller or network client is constructed here. */
class RealCallConfirmationUiTest {
    @get:Rule val compose = createComposeRule()
    private val real = ServerTestState(health = HealthResponse("clawops", "whisper", "GeminiInterpreter"))

    private fun prepare(phone: String = "07011112222") {
        compose.onNodeWithTag("server-key").performScrollTo().performTextInput("synthetic-ui-key")
        compose.onNodeWithTag("server-phone").performScrollTo().performTextInput(phone)
        compose.onNodeWithTag("server-goal").performScrollTo().performTextInput("가상 대상 업무 조회")
        compose.onNodeWithText("실통화 준비").performScrollTo().performClick()
    }

    @Test fun realTargetStartsEmptyAndPreparationOnlyShowsConfirmation() {
        val commands = mutableListOf<ServerTestCommand>()
        compose.setContent { ServerTestScreen(real, commands::add, {}) }
        compose.onNodeWithTag("server-phone").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        prepare()
        compose.onNodeWithText("실제로 전화를 걸까요?").assertIsDisplayed()
        compose.onNodeWithText("070-1111-2222").assertIsDisplayed()
        compose.onNodeWithText("확인하면 이 번호로 실제 전화가 걸립니다.").assertIsDisplayed()
        compose.onNode(hasText("가상 대상 업무 조회") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        assertTrue(commands.isEmpty())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "real-call-confirmation.png").outputStream().use {
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun explicitConfirmationDispatchesOneBoundRealStartAndClearsKey() {
        val commands = mutableListOf<ServerTestCommand>()
        compose.setContent { ServerTestScreen(real, commands::add, {}) }
        prepare()
        assertTrue(commands.isEmpty())
        compose.onNodeWithText("실제 발신").performClick()
        val sent = commands.single() as ServerTestCommand.Start
        assertEquals(ServerTestMode.RealCall, sent.mode)
        assertEquals("07011112222", sent.phone)
        assertEquals("가상 대상 업무 조회", sent.goal)
        assertEquals("synthetic-ui-key", sent.key)
        assertFalse(sent.toString().contains("synthetic-ui-key"))
        compose.onNodeWithText("실제 발신").assertDoesNotExist()
        compose.onNodeWithTag("server-key").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("실통화 준비").performScrollTo().assertIsNotEnabled()
        assertEquals(1, commands.size)
    }

    @Test fun cancellationDoesNotDialAndNextConfirmationShowsNewTarget() {
        val commands = mutableListOf<ServerTestCommand>()
        compose.setContent { ServerTestScreen(real, commands::add, {}) }
        prepare()
        compose.onNodeWithText("취소").performClick()
        assertTrue(commands.isEmpty())
        compose.onNodeWithTag("server-phone").performScrollTo().performTextReplacement("07033334444")
        compose.onNodeWithText("실통화 준비").performScrollTo().performClick()
        compose.onNodeWithText("070-3333-4444").assertIsDisplayed()
        compose.onNodeWithText("070-1111-2222").assertDoesNotExist()
        compose.onNodeWithText("실제 발신").performClick()
        assertEquals("07033334444", (commands.single() as ServerTestCommand.Start).phone)
    }

    @Test fun pendingConsentAndKeyAreNotRestored() {
        val commands = mutableListOf<ServerTestCommand>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { ServerTestScreen(real, commands::add, {}) }
        prepare()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("실제 발신").assertDoesNotExist()
        compose.onNodeWithTag("server-key").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertTrue(commands.isEmpty())
    }

    @Test fun backendChangeInvalidatesAnOpenConfirmation() {
        val commands = mutableListOf<ServerTestCommand>()
        val current = mutableStateOf(real)
        compose.setContent { ServerTestScreen(current.value, commands::add, {}) }
        prepare()
        compose.runOnIdle { current.value = real.copy(health = real.health!!.copy(backend = "sim")) }
        compose.onNodeWithText("실제 발신").assertDoesNotExist()
        assertTrue(commands.isEmpty())
        compose.runOnIdle { current.value = real }
        compose.onNodeWithText("실제 발신").assertDoesNotExist()
        compose.onNodeWithTag("server-phone").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test fun anotherActiveCallInvalidatesConfirmationWithoutSending() {
        val commands = mutableListOf<ServerTestCommand>()
        val current = mutableStateOf(real)
        compose.setContent { ServerTestScreen(current.value, commands::add, {}) }
        prepare()
        compose.runOnIdle { current.value = real.copy(health = real.health!!.copy(activeSession = "another-session")) }
        compose.onNodeWithText("실제 발신").assertDoesNotExist()
        compose.onNodeWithText("실통화 준비").performScrollTo().assertIsNotEnabled()
        assertTrue(commands.isEmpty())
    }

    @Test fun unknownBackendCannotPrepareAnyCall() {
        val commands = mutableListOf<ServerTestCommand>()
        compose.setContent { ServerTestScreen(real.copy(health = real.health!!.copy(backend = "future_backend")), commands::add, {}) }
        compose.onNodeWithTag("server-key").performTextInput("synthetic-ui-key")
        compose.onNodeWithTag("server-phone").performTextInput("07011112222")
        compose.onNodeWithTag("server-goal").performTextInput("가상 조회")
        compose.onNodeWithText("테스트 시작").performScrollTo().assertIsNotEnabled().performClick()
        assertTrue(commands.isEmpty())
    }
}
