package kr.ac.suwon.arsagent.ui.live

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.ac.suwon.arsagent.livecall.LiveCallOwner
import kr.ac.suwon.arsagent.model.*
import kr.ac.suwon.arsagent.servertest.voice.ServerVoiceUi
import kr.ac.suwon.arsagent.ui.*

/** Composable test boundary: rendering this never opens a network connection or microphone. */
@Composable
fun MainPhoneApp(phoneState: AppState, onPhoneAction: (Action) -> Unit, owner: LiveCallOwner,
    voice: ServerVoiceUi? = null, audio: LiveAudioUi? = null) {
    val entry by owner.entry.collectAsStateWithLifecycle()
    val server by owner.server.state.collectAsStateWithLifecycle()
    var real by rememberSaveable { mutableStateOf(true) }
    var modes by remember { mutableStateOf(false) }
    if (entry != null) {
        key(entry!!.generation) {
            if (server.hasSession || server.starting || server.creationUncertain)
                LiveSessionScreen(entry!!, server, owner, voice, audio) {
                    if (owner.returnAfterEnd()) onPhoneAction(Action.Tab(PhoneTab.Keypad))
                }
            else LivePurposeScreen(entry!!, server, owner, voice)
        }
    } else {
        ArsApp(phoneState, { action ->
            if (real && action == Action.StartAgent) owner.begin(phoneState.number)
            else onPhoneAction(action)
        }, if (real) "실제 ARS" else "UI 데모 · ${phoneState.scenario.title}", { modes = true })
    }
    if (modes && entry == null) ArsTheme {
        AlertDialog(onDismissRequest = { modes = false }, title = { Text("ARS 사용 모드") },
            text = {
                Column {
                    Text("일반 전화·연락처·통화 기록은 예시 화면이에요.")
                    TextButton({ real = true; modes = false }) { Text("실제 ARS") }
                    TextButton({ real = false; modes = false }) { Text("UI 데모") }
                    DemoScenario.entries.forEach { scenario ->
                        TextButton({ real = false; onPhoneAction(Action.Scenario(scenario)); modes = false }) { Text(scenario.title) }
                    }
                }
            }, confirmButton = { TextButton({ modes = false }) { Text("닫기") } })
    }
}
