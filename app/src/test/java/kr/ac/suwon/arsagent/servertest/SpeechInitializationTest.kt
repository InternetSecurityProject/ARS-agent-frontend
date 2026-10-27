package kr.ac.suwon.arsagent.servertest

import kr.ac.suwon.arsagent.servertest.voice.SpeechInitialization
import org.junit.Assert.*
import org.junit.Test

class SpeechInitializationTest {
    @Test fun replacementKeepsItsOwnTimeoutWhenFirstUtteranceWasCancelled() {
        val timers = mutableListOf<() -> Unit>(); var failures = 0; var spoken = 0
        val pending = SpeechInitialization({ _, action -> timers.add(action) }, { failures++ })
        pending.replace { spoken++ }; pending.clear(); pending.replace { spoken++ }
        timers.first().invoke(); assertEquals(0, failures)
        timers.last().invoke(); assertEquals(1, failures)
        pending.complete(); assertEquals(0, spoken)
    }
    @Test fun readyInvokesOnlyLatestUtteranceOnceAndDisarmsOldTimers() {
        val timers = mutableListOf<() -> Unit>(); var first = 0; var latest = 0; var failures = 0
        val pending = SpeechInitialization({ _, action -> timers.add(action) }, { failures++ })
        pending.replace { first++ }; pending.replace { latest++ }; pending.complete(); pending.complete()
        timers.forEach { it() }
        assertEquals(0, first); assertEquals(1, latest); assertEquals(0, failures)
    }
}
