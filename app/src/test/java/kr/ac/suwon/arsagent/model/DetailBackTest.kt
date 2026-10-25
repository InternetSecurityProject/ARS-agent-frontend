package kr.ac.suwon.arsagent.model

import org.junit.Assert.*
import org.junit.Test

class DetailBackTest {
    @Test fun backFromCallDetailsReturnsToCalls() {
        val result = AppState(tab = PhoneTab.Calls).reduce(Action.Detail("01012345678"))
            .reduce(Action.Back)
        assertEquals(Screen.Home, result.screen)
        assertEquals(PhoneTab.Calls, result.tab)
    }

    @Test fun backFromContactDetailsReturnsToContacts() {
        val result = AppState(tab = PhoneTab.Contacts).reduce(Action.Detail("07000000000"))
            .reduce(Action.Back)
        assertEquals(Screen.Home, result.screen)
        assertEquals(PhoneTab.Contacts, result.tab)
    }

    @Test fun backDuringCallClosesAudioThenKeypadAndDoesNotLeaveCall() {
        val call = AppState(screen = Screen.Call, tab = PhoneTab.Calls, agent = true,
            phase = Phase.Selecting, audioMenu = true, keypadOpen = true)
        val audioClosed = call.reduce(Action.Back)
        assertFalse(audioClosed.audioMenu)
        assertTrue(audioClosed.keypadOpen)
        assertEquals(Screen.Call, audioClosed.screen)
        val keypadClosed = audioClosed.reduce(Action.Back)
        assertFalse(keypadClosed.keypadOpen)
        assertEquals(Screen.Call, keypadClosed.screen)
        assertEquals(keypadClosed, keypadClosed.reduce(Action.Back))
    }

    @Test fun purposeCancelAndEndedReturnStillGoToKeypad() {
        listOf(Screen.Purpose, Screen.Ended).forEach { screen ->
            val result = AppState(screen = screen, tab = PhoneTab.Contacts, goalDraft = "날씨")
                .reduce(Action.Back)
            assertEquals(Screen.Home, result.screen)
            assertEquals(PhoneTab.Keypad, result.tab)
            assertEquals("", result.goalDraft)
        }
    }
}
