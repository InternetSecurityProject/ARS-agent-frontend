package kr.ac.suwon.arsagent.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import kr.ac.suwon.arsagent.data.api.CompletedResult
import kr.ac.suwon.arsagent.data.api.SessionSnapshot
import kr.ac.suwon.arsagent.model.*

/** Opt-in rendering only: does not start sessions, collect events, run demo timers or own credentials. */
@Composable
fun BServerCallScreen(
    number: String,
    snapshot: SessionSnapshot,
    result: CompletedResult? = null,
    onEnd: () -> Unit,
    onBack: () -> Unit,
    choiceInput: CandidateChoiceInput? = null,
    secureInput: SecureInputInput? = null,
) {
    val data = BCallData.from(snapshot, result)
    // Reuse AppState solely for existing call-frame geometry, never its demo reducer/ViewModel.
    val frame = AppState(screen = Screen.Call, number = number, agent = true,
        phase = if (data.status == "dialing") Phase.Dialing else Phase.Listening)
    BackHandler { if (data.isEnded) onBack() }
    ArsTheme {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to ArsColors.CallGradient[0], .32f to ArsColors.CallGradient[1],
            .62f to ArsColors.CallGradient[2], 1f to ArsColors.CallGradient[3],
        )).safeDrawingPadding().imePadding()) {
            CallScreen(frame, { action ->
                when (action) {
                    Action.End -> onEnd()
                    Action.Back -> if (data.isEnded) onBack()
                    else -> Unit // Secondary controls await separate C/audio integration.
                }
            }, serverData = data, choiceInput = choiceInput, secureInput = secureInput)
        }
    }
}
