package kr.ac.suwon.arsagent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kr.ac.suwon.arsagent.model.*

/** UI-only clock and scenario driver. Replace with a session repository for real calls. */
class AppViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(AppState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            state.map { it.screen to it.goalDraft }.distinctUntilChanged().collectLatest { (screen, text) ->
                if (screen == Screen.Purpose && text.isNotBlank()) {
                    delay(1_200)
                    dispatch(Action.CommitGoal)
                }
            }
        }
        viewModelScope.launch {
            state.map { Triple(it.screen, it.phase, it.answerDraft) }.distinctUntilChanged()
                .collectLatest { (screen, phase, text) ->
                    if (screen == Screen.Call && phase == Phase.Answer && text.isNotBlank()) {
                        delay(1_200)
                        dispatch(Action.CommitAnswer)
                    }
                }
        }
        viewModelScope.launch {
            state.map { Triple(it.screen, it.phase, it.keypadOpen) }.distinctUntilChanged()
                .collectLatest { (screen, phase, keypad) ->
                    if (screen == Screen.Call && !keypad && state.value.agent && phase in autoPhases) {
                        delay(if (phase == Phase.Sending) 500 else 3_000)
                        dispatch(Action.DemoAdvance)
                    }
                }
        }
        viewModelScope.launch {
            state.map {
                if (it.screen == Screen.Call && it.agent && it.keypadOpen && it.manualDigits.isNotEmpty()) it.manualInputRevision else null
            }.distinctUntilChanged().collectLatest { revision ->
                if (revision != null) {
                    delay(10_000)
                    dispatch(Action.OpenKeypad)
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                dispatch(Action.Tick)
            }
        }
    }

    fun dispatch(action: Action) { mutableState.update { it.reduce(action) } }

    private companion object {
        val autoPhases = setOf(Phase.Dialing, Phase.Listening, Phase.Selecting, Phase.ListeningResult, Phase.Sending)
    }
}
