package kr.ac.suwon.arsagent.model

import org.junit.Assert.*
import org.junit.Test

class PhoneStateTest {
    @Test fun mobileOnlyOffersNormalCall() {
        assertTrue(PhoneNumber("010-1234-5678").canCall)
        assertFalse(PhoneNumber("010-1234-5678").canUseAgent)
    }
    @Test fun serviceAndTestNumbersOfferAgentWithoutClaimingSupport() {
        listOf("131", "070-0000-0000", "1588-1300").forEach {
            assertTrue(PhoneNumber(it).canUseAgent)
        }
    }
    @Test fun invalidOrShortNumbersCannotStartCall() {
        listOf("", "0", "01", "abcdef", "010abc", "12345678901234", "12#").forEach {
            assertFalse("must reject $it", PhoneNumber(it).canCall)
        }
    }
    @Test fun numberNormalizationPreservesLeadingZero() {
        assertEquals("01012345678", PhoneNumber(" 010-1234-5678 ").digits)
    }
    @Test fun typingGoalDoesNotCallUntilInputSettles() {
        val state = AppState().reduce(Action.Dial("131")).reduce(Action.StartAgent)
            .reduce(Action.GoalChanged("수원 날씨"))
        assertEquals(Screen.Purpose, state.screen)
        val started = state.reduce(Action.CommitGoal)
        assertEquals(Screen.Call, started.screen)
        assertEquals(Phase.Dialing, started.phase)
        assertEquals("수원 날씨", started.goal)
    }
    @Test fun emptyGoalDoesNotCall() {
        val s = AppState().reduce(Action.Dial("131")).reduce(Action.StartAgent)
        assertEquals(Screen.Purpose, s.reduce(Action.CommitGoal).screen)
    }
    @Test fun staleGoalCallbackAfterCancelCannotStartCall() {
        val cancelled = AppState().reduce(Action.Dial("131")).reduce(Action.StartAgent)
            .reduce(Action.GoalChanged("날씨")).reduce(Action.Back)
        assertEquals(Screen.Home, cancelled.reduce(Action.CommitGoal).screen)
    }
    @Test fun answerContinuesAutomatically() {
        val s = AppState(screen = Screen.Call, agent = true, phase = Phase.Answer)
            .reduce(Action.AnswerChanged("수원"))
        assertEquals(Phase.Answer, s.phase)
        assertEquals(Phase.Selecting, s.reduce(Action.CommitAnswer).phase)
    }
    @Test fun secureEntryClearsImmediatelyAtExpectedLength() {
        var s = AppState(screen = Screen.Call, agent = true, phase = Phase.Secure)
        "12345".forEach { s = s.reduce(Action.Key(it)) }
        assertEquals(5, s.secureEntry.count)
        assertFalse(s.toString().contains("12345"))
        s = s.reduce(Action.Key('6'))
        assertEquals(Phase.Sending, s.phase)
        assertEquals(0, s.secureEntry.count)
    }
    @Test fun secureInputRejectsNonDigitAndSupportsDelete() {
        val s = AppState(screen = Screen.Call, phase = Phase.Secure)
            .reduce(Action.Key('1')).reduce(Action.Key('#')).reduce(Action.DeleteSecure)
        assertEquals(0, s.secureEntry.count)
    }
    @Test fun endClearsPrivateInputAndStopsLateDemoEvents() {
        val ended = AppState(screen = Screen.Call, phase = Phase.Secure)
            .reduce(Action.Key('9')).reduce(Action.End)
        assertEquals(0, ended.secureEntry.count)
        assertEquals(Screen.Ended, ended.reduce(Action.DemoAdvance).screen)
    }
    @Test fun timerDoesNotRunBeforeConnectionOrAfterEnd() {
        assertEquals(0, AppState(screen = Screen.Purpose).reduce(Action.Tick).elapsedSeconds)
        assertEquals(0, AppState(screen = Screen.Call, phase = Phase.Dialing).reduce(Action.Tick).elapsedSeconds)
        assertEquals(1, AppState(screen = Screen.Call, phase = Phase.Listening).reduce(Action.Tick).elapsedSeconds)
        assertEquals(10, AppState(screen = Screen.Ended, elapsedSeconds = 10).reduce(Action.Tick).elapsedSeconds)
    }
    @Test fun normalCallCannotBecomeAgentMidCall() {
        val call = AppState().reduce(Action.Dial("01012345678")).reduce(Action.StartNormal)
        assertFalse(call.reduce(Action.StartAgent).agent)
    }
}
