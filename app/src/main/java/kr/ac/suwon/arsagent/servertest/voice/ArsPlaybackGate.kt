package kr.ac.suwon.arsagent.servertest.voice

import java.io.Closeable

/** Sink writes must be non-blocking: this lock also serializes foreground suppression/cleanup. */
interface PcmSink : Closeable {
    fun play()
    fun pauseAndFlush()
    fun write(samples: ShortArray)
}

class ArsPlaybackGate {
    private val lock = Any()
    private var generation = 0L
    private var sink: PcmSink? = null
    private var muted = false

    fun begin(): Long = synchronized(lock) { stopLocked(); generation }
    fun attach(token: Long, player: PcmSink): Boolean = synchronized(lock) {
        if (token != generation) { player.close(); return@synchronized false }
        sink = player
        try { if (muted) player.pauseAndFlush() else player.play() }
        catch (e: Exception) { sink = null; player.close(); throw e }
        true
    }
    fun write(token: Long, samples: ShortArray): Boolean = synchronized(lock) {
        val player = sink
        if (token != generation || muted || player == null) false
        else { player.write(samples); true }
    }
    fun mute(value: Boolean) = synchronized(lock) {
        if (muted == value) return@synchronized
        muted = value
        try { if (value) sink?.pauseAndFlush() else sink?.play() }
        catch (_: Exception) { stopLocked() } // fail silent rather than leave an unflushable speaker running
    }
    fun release(token: Long) = synchronized(lock) { if (token == generation) stopLocked() }
    fun publish(token: Long, action: () -> Unit) = synchronized(lock) { if (token == generation) action() }
    fun stop() = synchronized(lock) { stopLocked() }
    private fun stopLocked() {
        generation++ // invalidate before touching/cancelling any native or coroutine resources
        val previous = sink; sink = null
        runCatching { previous?.pauseAndFlush() }; runCatching { previous?.close() }
    }
}
