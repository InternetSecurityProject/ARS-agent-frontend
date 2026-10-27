package kr.ac.suwon.arsagent.servertest.voice

import kr.ac.suwon.arsagent.data.api.CreatedSession
import kr.ac.suwon.arsagent.servertest.ServerTestGateway

/** Keeps session credentials out of UI/saved state. No extra API calls or changes to the existing controller. */
class VoiceSessionRelay {
    private var current: CreatedSession? = null
    private var observer: ((CreatedSession?) -> Unit)? = null
    fun observe(callback: ((CreatedSession?) -> Unit)?) { observer = callback; callback?.invoke(current) }
    fun decorate(delegate: ServerTestGateway): ServerTestGateway = object : ServerTestGateway by delegate {
        private var owned: CreatedSession? = null
        override suspend fun create(phone: String, goal: String): CreatedSession = delegate.create(phone, goal).also {
            owned = it; current = it; observer?.invoke(it)
        }
        override fun close() {
            if (owned != null && current === owned) { current = null; observer?.invoke(null) }
            owned = null; delegate.close()
        }
    }
}
