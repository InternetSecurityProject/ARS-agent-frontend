package kr.ac.suwon.arsagent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.io.File
import android.graphics.Bitmap
import kr.ac.suwon.arsagent.data.api.*
import kr.ac.suwon.arsagent.ui.BServerCallScreen
import kr.ac.suwon.arsagent.ui.SecureInputInput
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ServerSecureInputUiTest {
    @get:Rule val compose = createComposeRule()
    private val request = UserRequest("secure-27", "secure_input", "계좌번호를 직접 입력해 주세요", "2099-01-01T00:00:00Z", label = "계좌번호", terminator = "#")
    private fun snapshot(r: UserRequest = request) = SessionSnapshot("awaiting_user", "입력 대기", pendingRequest = r)
    private fun type(digits: String) { digits.forEach { compose.onNodeWithContentDescription("키 $it").performScrollTo().performClick() } }

    @Test fun serverLabelsMaskedInputAndCompletionReplaceSixDigitAutoSubmit() {
        val sent = mutableListOf<SessionAction.SecureInput>()
        compose.setContent { BServerCallScreen("07012345678", snapshot(), onEnd = {}, onBack = {},
            secureInput = SecureInputInput(true, { sent.add(it) })) }
        compose.onNodeWithText("계좌번호").assertIsDisplayed()
        compose.onNodeWithText(request.message).assertIsDisplayed()
        compose.onNodeWithText("생년월일 6자리 입력").assertDoesNotExist()
        compose.onNodeWithText("입력 완료").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithContentDescription("키 0").assertIsDisplayed()
        assertTrue(compose.onNodeWithContentDescription("키 0").getUnclippedBoundsInRoot().bottom <=
            compose.onNodeWithText("입력 완료").getUnclippedBoundsInRoot().top)
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "c-server-secure.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        type("1122334455")
        assertTrue(sent.isEmpty())
        compose.onNodeWithTag("secure-mask").performScrollTo().assertTextEquals("●●●●●●●●●●")
        compose.onNodeWithText("1122334455").assertDoesNotExist()
        compose.onNodeWithText("입력 완료").performClick().performClick()
        assertEquals(1, sent.size)
        assertEquals("secure-27", sent.single().requestId)
        assertEquals("1122334455", sent.single().digits)
        assertFalse(sent.single().toString().contains("1122334455"))
        compose.onNodeWithTag("secure-mask").assertTextEquals("")
        compose.onNodeWithText("종료").assertIsDisplayed()
        compose.onNodeWithText("입력 완료").assertIsNotEnabled()
    }

    @Test fun deleteEditsMaskedValueAndTerminatorIsNotAddedByApp() {
        val sent = mutableListOf<SessionAction.SecureInput>()
        compose.setContent { BServerCallScreen("131", snapshot(), onEnd = {}, onBack = {}, secureInput = SecureInputInput(true, { sent.add(it) })) }
        type("12")
        compose.onNodeWithContentDescription("키 #").performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("입력 한 자리 지우기").performScrollTo().performClick()
        compose.onNodeWithText("입력 완료").performClick()
        assertEquals("1", sent.single().digits)
    }

    @Test fun connectionLossClearsInputAndMissingOwnerCannotAcceptInput() {
        val sent = mutableListOf<SessionAction.SecureInput>()
        val input = mutableStateOf<SecureInputInput?>(null)
        compose.setContent { BServerCallScreen("131", snapshot(), onEnd = {}, onBack = {}, secureInput = input.value) }
        compose.onNodeWithContentDescription("키 1").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { input.value = SecureInputInput(true, { sent.add(it) }) }
        type("123")
        compose.runOnIdle { input.value = SecureInputInput(false, { sent.add(it) }) }
        compose.onNodeWithTag("secure-mask").performScrollTo().assertTextEquals("")
        compose.runOnIdle { input.value = SecureInputInput(true, { sent.add(it) }) }
        compose.onNodeWithText("입력 완료").assertIsNotEnabled()
        assertTrue(sent.isEmpty())
    }

    @Test fun requestReplacementAndExplicitRetryNeverReuseDigits() {
        val sent = mutableListOf<SessionAction.SecureInput>()
        val current = mutableStateOf(snapshot())
        val retry = mutableStateOf(0)
        compose.setContent { BServerCallScreen("131", current.value, onEnd = {}, onBack = {},
            secureInput = SecureInputInput(true, { sent.add(it) }, retry.value)) }
        type("123")
        compose.runOnIdle { current.value = snapshot(request.copy(requestId = "secure-28", label = "다른 입력")) }
        compose.onNodeWithTag("secure-mask").performScrollTo().assertTextEquals("")
        type("7")
        compose.onNodeWithText("입력 완료").performClick()
        compose.runOnIdle { retry.value++ }
        compose.onNodeWithText("입력 완료").assertIsNotEnabled()
        type("8")
        compose.onNodeWithText("입력 완료").performClick()
        assertEquals(listOf("7", "8"), sent.map { it.digits })
        assertEquals(listOf("secure-28", "secure-28"), sent.map { it.requestId })
    }

    @Test fun resizeAndTemporaryRequestRemovalDoNotResendCompletedInput() {
        val sent = mutableListOf<SessionAction.SecureInput>()
        val height = mutableStateOf(700.dp)
        val current = mutableStateOf(snapshot())
        compose.setContent { Box(Modifier.size(320.dp, height.value)) {
            BServerCallScreen("131", current.value, onEnd = {}, onBack = {}, secureInput = SecureInputInput(true, { sent.add(it) }))
        } }
        type("12")
        compose.runOnIdle { height.value = 480.dp }
        compose.onNodeWithTag("secure-mask").performScrollTo().assertTextEquals("●●")
        compose.onNodeWithText("입력 완료").performClick()
        compose.runOnIdle { height.value = 700.dp; current.value = SessionSnapshot("selecting", "처리 중") }
        compose.runOnIdle { current.value = snapshot() }
        compose.onNodeWithText("입력 완료").assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("키 1").performScrollTo().assertIsNotEnabled()
        assertEquals(1, sent.size)
    }

    @Test fun expirationClearsDigitsWithoutSubmission() {
        compose.mainClock.autoAdvance = false
        val sent = mutableListOf<SessionAction.SecureInput>()
        val expiring = snapshot(request.copy(expiresAt = Instant.now().plusSeconds(5).toString()))
        compose.setContent { BServerCallScreen("131", expiring, onEnd = {}, onBack = {}, secureInput = SecureInputInput(true, { sent.add(it) })) }
        type("1")
        compose.mainClock.advanceTimeBy(6_000)
        compose.onNodeWithTag("secure-mask").performScrollTo().assertTextEquals("")
        compose.onNodeWithText("입력 완료").assertIsNotEnabled().performClick()
        assertTrue(sent.isEmpty())
    }

    @Test fun invalidDeadlineAndStaleStatusFailClosed() {
        val current = mutableStateOf(snapshot(request.copy(expiresAt = "invalid")))
        val sent = mutableListOf<SessionAction.SecureInput>()
        compose.setContent { BServerCallScreen("131", current.value, onEnd = {}, onBack = {}, secureInput = SecureInputInput(true, { sent.add(it) })) }
        compose.onNodeWithContentDescription("키 1").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { current.value = snapshot().copy(status = "selecting") }
        compose.onNodeWithContentDescription("키 1").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("입력 완료").assertIsNotEnabled()
        assertTrue(sent.isEmpty())
    }

    @Test fun endClearsInputImmediatelyAndDoesNotTransmitDigits() {
        val sent = mutableListOf<SessionAction.SecureInput>()
        var ends = 0
        compose.setContent { BServerCallScreen("131", snapshot(), onEnd = { ends++ }, onBack = {}, secureInput = SecureInputInput(true, { sent.add(it) })) }
        type("123")
        compose.onNodeWithText("종료").performClick()
        compose.onNodeWithTag("secure-mask").performScrollTo().assertTextEquals("")
        compose.onNodeWithText("입력 완료").assertIsNotEnabled()
        assertEquals(1, ends)
        assertTrue(sent.isEmpty())
    }

    @Test fun rawDigitsAreNotRestoredFromSavedInstanceState() {
        val sent = mutableListOf<SessionAction.SecureInput>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { BServerCallScreen("131", snapshot(), onEnd = {}, onBack = {}, secureInput = SecureInputInput(true, { sent.add(it) })) }
        type("123")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("secure-mask").performScrollTo().assertTextEquals("")
        compose.onNodeWithText("입력 완료").assertIsNotEnabled()
        assertTrue(sent.isEmpty())
    }

    @Test fun backgroundingClearsInputBeforeReturning() {
        val owner = object : LifecycleOwner { override val lifecycle = LifecycleRegistry(this) }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        val sent = mutableListOf<SessionAction.SecureInput>()
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            BServerCallScreen("131", snapshot(), onEnd = {}, onBack = {}, secureInput = SecureInputInput(true, { sent.add(it) }))
        } }
        type("123")
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithTag("secure-mask").performScrollTo().assertTextEquals("")
        compose.onNodeWithText("입력 완료").assertIsNotEnabled()
        assertTrue(sent.isEmpty())
    }
}
