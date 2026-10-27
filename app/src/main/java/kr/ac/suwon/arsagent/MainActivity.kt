package kr.ac.suwon.arsagent

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import kr.ac.suwon.arsagent.livecall.MainCallViewModel
import kr.ac.suwon.arsagent.ui.live.MainLiveBindings
import kr.ac.suwon.arsagent.ui.live.MainPhoneApp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import kr.ac.suwon.arsagent.updates.TeamUpdates

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: AppViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            val calls: MainCallViewModel = viewModel()
            val entry by calls.owner.entry.collectAsStateWithLifecycle()
            val server by calls.owner.server.state.collectAsStateWithLifecycle()
            DisposableEffect(entry != null) {
                val wasSecure = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
                if (entry != null) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                onDispose { if (!wasSecure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            }
            MainLiveBindings(entry, server, calls.owner, calls.sessions) { voice, audio ->
                MainPhoneApp(state, model::dispatch, calls.owner, voice, audio) { eligible -> TeamUpdates(eligible) }
            }
        }
    }
}
