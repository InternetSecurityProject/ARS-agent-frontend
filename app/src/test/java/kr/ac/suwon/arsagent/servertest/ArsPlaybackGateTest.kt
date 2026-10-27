package kr.ac.suwon.arsagent.servertest

import kr.ac.suwon.arsagent.servertest.voice.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ArsPlaybackGateTest {
    private open class Sink : PcmSink {
        var plays = 0; var flushes = 0; var closed = false; var writes = 0
        override fun play() { plays++ }
        override fun pauseAndFlush() { flushes++ }
        override fun write(samples: ShortArray) { writes++ }
        override fun close() { closed = true }
    }
    @Test fun lateOldConstructionAndCleanupCannotReplaceOrReleaseCurrentPlayer() {
        val gate = ArsPlaybackGate(); val old = gate.begin(); val current = gate.begin()
        val active = Sink(); assertTrue(gate.attach(current, active))
        val delayed = Sink(); assertFalse(gate.attach(old, delayed)); assertTrue(delayed.closed)
        gate.release(old); assertFalse(active.closed)
        gate.mute(true); assertEquals(1, active.flushes); assertFalse(gate.write(current, shortArrayOf(1)))
        gate.mute(false); assertTrue(gate.write(current, shortArrayOf(2)))
        gate.stop(); assertTrue(active.closed); assertFalse(gate.write(current, shortArrayOf(3)))
    }
    @Test fun microphoneSuppressionFlushesOnlyAfterAnyOutstandingWriteFinishes() {
        val gate = ArsPlaybackGate(); val token = gate.begin()
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val attemptingMute = CountDownLatch(1); val muted = CountDownLatch(1)
        val order = java.util.concurrent.CopyOnWriteArrayList<String>()
        gate.attach(token, object : Sink() {
            override fun write(samples: ShortArray) { entered.countDown(); release.await(2, TimeUnit.SECONDS); order.add("write") }
            override fun pauseAndFlush() { order.add("flush") }
        })
        val writer = Thread { gate.write(token, shortArrayOf(1)) }
        val muter = Thread { attemptingMute.countDown(); gate.mute(true); muted.countDown() }
        writer.start()
        try {
            assertTrue(entered.await(1, TimeUnit.SECONDS)); muter.start()
            assertTrue(attemptingMute.await(1, TimeUnit.SECONDS)); assertFalse(muted.await(100, TimeUnit.MILLISECONDS))
        } finally { release.countDown(); writer.join(2_000); muter.join(2_000) }
        assertEquals(listOf("write", "flush"), order)
        assertFalse(gate.write(token, shortArrayOf(2))); gate.stop()
    }
    @Test fun startingWhileAgentIsSpeakingDoesNotPlayUntilSuppressionEnds() {
        val gate = ArsPlaybackGate(); gate.mute(true); val token = gate.begin(); val sink = Sink()
        gate.attach(token, sink); assertEquals(0, sink.plays)
        gate.mute(false); assertEquals(1, sink.plays); gate.stop()
    }
    @Test fun stoppedOrReplacedPlayerCannotPublishLatePlaybackStatus() {
        val gate = ArsPlaybackGate(); val old = gate.begin(); var updates = 0
        val current = gate.begin()
        gate.publish(old) { updates++ }; assertEquals(0, updates)
        gate.publish(current) { updates++ }; assertEquals(1, updates)
        gate.stop(); gate.publish(current) { updates++ }; assertEquals(1, updates)
    }
}
