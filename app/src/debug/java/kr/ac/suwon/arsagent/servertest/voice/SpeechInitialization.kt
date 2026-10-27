package kr.ac.suwon.arsagent.servertest.voice

/** A fresh bounded wait for every replacement utterance, independent of native engine init callbacks. */
class SpeechInitialization(private val schedule: (Long, () -> Unit) -> Unit, private val onTimeout: () -> Unit) {
    private var version = 0L
    private var pending: (() -> Unit)? = null
    fun replace(action: () -> Unit) {
        version++; pending = action; val token = version
        schedule(10_000) {
            if (token == version && pending != null) { pending = null; version++; onTimeout() }
        }
    }
    fun clear() { version++; pending = null }
    fun complete() { val current = pending; clear(); current?.invoke() }
}
