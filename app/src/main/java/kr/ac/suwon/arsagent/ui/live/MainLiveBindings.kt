package kr.ac.suwon.arsagent.ui.live

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kr.ac.suwon.arsagent.livecall.*
import kr.ac.suwon.arsagent.servertest.ServerTestState
import kr.ac.suwon.arsagent.servertest.voice.*
import kr.ac.suwon.arsagent.ui.LiveAudioUi

/** Native resources exist only inside one live entry. A and the frozen gallery never enter here. */
@Composable
fun MainLiveBindings(entry: LiveEntry?, state: ServerTestState, owner: LiveCallOwner,
    sessions: VoiceSessionRelay, content: @Composable (ServerVoiceUi?, LiveAudioUi?) -> Unit) {
    if (entry == null) { content(null, null); return }
    key(entry.generation) {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val device = remember { AndroidAudioRoutes(context) }
        val routing = remember { AudioRouting(device) }
        val audio by routing.state.collectAsState()
        var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
        DisposableEffect(lifecycle, routing) {
            device.onChange = routing::refresh
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> { foreground = false; routing.restore() }
                    Lifecycle.Event.ON_START -> foreground = true
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose { device.onChange = null; lifecycle.removeObserver(observer); routing.close() }
        }
        ServerVoiceBinding(state, owner::dispatch, sessions, latestState = { owner.server.state.value },
            communicationAudio = true) { voice ->
            val finished = state.hasSession && !state.active || state.snapshot.status == "connected_to_agent"
            SideEffect {
                // Allow the last end/handoff announcement, then relinquish communication audio.
                if (foreground && (!finished || voice.status.speaking)) routing.activate() else routing.restore()
            }
            content(voice, LiveAudioUi(audio.route, audio.available, audio.error, routing::select))
        }
    }
}
