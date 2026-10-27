package kr.ac.suwon.arsagent.livecall

import kr.ac.suwon.arsagent.model.AudioRoute
import org.junit.Assert.*
import org.junit.Test

class AudioRoutingTest {
    private class Device : AudioRouteDevice {
        var enters = 0
        var restores = 0
        var output: AudioRoute? = AudioRoute.Phone
        var routes = setOf(AudioRoute.Phone, AudioRoute.Speaker)
        var accept = true
        var fail = false
        override fun enter() { enters++; if (fail) error("device unavailable") }
        override fun available() = routes
        override fun current() = output
        override fun select(route: AudioRoute): Boolean {
            if (accept) output = route
            return accept
        }
        override fun restore() { restores++ }
    }

    @Test fun onlyLiveActivationChangesAudioAndRepeatedActivationIsIdempotent() {
        val d = Device(); val routing = AudioRouting(d)
        assertEquals(0, d.enters)
        assertTrue(routing.state.value.available.isEmpty())
        routing.activate(); routing.activate()
        assertEquals(1, d.enters)
        assertEquals(AudioRoute.Phone, routing.state.value.route)
        routing.select(AudioRoute.Speaker)
        assertEquals(AudioRoute.Speaker, routing.state.value.route)
        routing.restore(); routing.restore(); routing.close()
        assertEquals(1, d.restores)
        assertTrue(routing.state.value.available.isEmpty())
    }

    @Test fun rejectedOrUnsupportedSelectionNeverShowsFalseSuccess() {
        val d = Device(); val routing = AudioRouting(d)
        routing.select(AudioRoute.Speaker)
        assertNull(routing.state.value.route)
        routing.activate(); d.accept = false
        routing.select(AudioRoute.Speaker)
        assertEquals(AudioRoute.Phone, routing.state.value.route)
        assertNotNull(routing.state.value.error)
        d.accept = true; d.routes = setOf(AudioRoute.Phone)
        routing.refresh(); routing.select(AudioRoute.Speaker)
        assertEquals(AudioRoute.Phone, d.output)
        assertNotNull(routing.state.value.error)
    }

    @Test fun externalHeadsetIsNotMislabeledAsPhoneAndReconnectRefreshesAvailability() {
        val d = Device(); val routing = AudioRouting(d)
        routing.activate(); d.output = null; d.routes = setOf(AudioRoute.Speaker)
        routing.refresh()
        assertNull(routing.state.value.route)
        assertEquals(setOf(AudioRoute.Speaker), routing.state.value.available)
        routing.restore(); routing.activate()
        assertEquals(2, d.enters)
    }

    @Test fun failedActivationRestoresPartiallyChangedDeviceAndRemainsInactive() {
        val d = Device().apply { fail = true }; val routing = AudioRouting(d)
        routing.activate()
        assertFalse(routing.state.value.active)
        assertEquals(1, d.restores)
        assertNotNull(routing.state.value.error)
        routing.close()
        assertEquals(1, d.restores)
    }
}
