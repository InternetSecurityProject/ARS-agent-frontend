package kr.ac.suwon.arsagent.livecall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kr.ac.suwon.arsagent.servertest.HttpServerTestGateway
import kr.ac.suwon.arsagent.servertest.voice.VoiceSessionRelay

class MainCallViewModel : ViewModel() {
    val sessions = VoiceSessionRelay()
    val owner = LiveCallOwner(viewModelScope, { key -> sessions.decorate(HttpServerTestGateway(key)) })
    override fun onCleared() { owner.close() }
}
