package kr.ac.suwon.arsagent

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kr.ac.suwon.arsagent.model.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var model: AppViewModel
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        model = ViewModelProvider(store, ViewModelProvider.NewInstanceFactory())[AppViewModel::class.java]
    }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    private fun withModel(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try { body() } finally { store.clear(); runCurrent() }
    }

    private fun purpose() {
        model.dispatch(Action.Dial("131"))
        model.dispatch(Action.StartAgent)
        model.dispatch(Action.GoalChanged("수원 날씨"))
    }

    @Test fun goalDebounceRestartsAndCancelStopsPendingCall() = withModel {
        purpose(); runCurrent(); advanceTimeBy(1_000)
        model.dispatch(Action.GoalChanged("수원 오늘 날씨")); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(Screen.Purpose, model.state.value.screen)
        model.dispatch(Action.Back); runCurrent(); advanceTimeBy(10_000); runCurrent()
        assertEquals(Screen.Home, model.state.value.screen)
    }

    @Test fun clearGoalStopsAutostart() = withModel {
        purpose(); runCurrent(); advanceTimeBy(1_000)
        model.dispatch(Action.GoalChanged("")); runCurrent(); advanceTimeBy(5_000); runCurrent()
        assertEquals(Screen.Purpose, model.state.value.screen)
    }

    @Test fun settledGoalAutoCallsAndEndingStopsClockAndProgress() = withModel {
        purpose(); runCurrent(); advanceTimeBy(1_200); runCurrent()
        assertEquals(Phase.Dialing, model.state.value.phase)
        assertEquals(Screen.Call, model.state.value.screen)
        advanceTimeBy(3_000); runCurrent()
        assertEquals(Phase.Listening, model.state.value.phase)
        model.dispatch(Action.End); runCurrent()
        val seconds = model.state.value.elapsedSeconds
        advanceTimeBy(20_000); runCurrent()
        assertEquals(Screen.Ended, model.state.value.screen)
        assertEquals(seconds, model.state.value.elapsedSeconds)
    }

    @Test fun manualKeypadResumesAgentTenSecondsAfterLastDigit() = withModel {
        purpose(); runCurrent(); advanceTimeBy(4_200); runCurrent()
        model.dispatch(Action.OpenKeypad); model.dispatch(Action.Key('1')); runCurrent()
        advanceTimeBy(9_000); runCurrent()
        assertTrue(model.state.value.keypadOpen)
        model.dispatch(Action.Key('2')); runCurrent(); advanceTimeBy(9_000); runCurrent()
        assertTrue(model.state.value.keypadOpen)
        advanceTimeBy(1_000); runCurrent()
        assertFalse(model.state.value.keypadOpen)
    }
}
