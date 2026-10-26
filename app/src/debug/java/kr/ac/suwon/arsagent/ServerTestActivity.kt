package kr.ac.suwon.arsagent

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.ac.suwon.arsagent.servertest.*

/** Debug-only: this Activity never receives credentials through intents or saved state. */
class ServerTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            val model: ServerTestViewModel = viewModel()
            val state by model.controller.state.collectAsStateWithLifecycle()
            ServerTestScreen(state, model.controller::dispatch, ::finish)
        }
    }
}

class ServerTestViewModel : ViewModel() {
    val controller = ServerTestController(viewModelScope)
    override fun onCleared() { controller.close() }
}
