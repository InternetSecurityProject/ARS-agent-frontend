package kr.ac.suwon.arsagent.servertest

import kr.ac.suwon.arsagent.servertest.voice.*
import org.junit.Assert.*
import org.junit.Test

class VoiceActivationTest {
    private class Device : SpeechDevice {
        var result: ((String) -> Unit)? = null
        var listens = 0
        override fun listen(onResult: (String) -> Unit, onError: (String) -> Unit) { listens++; result = onResult }
        override fun speak(text: String, onDone: () -> Unit) { onDone() }
        override fun stop() = Unit
        override fun close() = Unit
    }

    @Test fun purposeWaitsForConsentThenListensOnceAndOnlyFillsText() {
        val d = Device(); val commands = mutableListOf<ServerTestCommand>()
        val voice = VoiceCoordinator(d, commands::add)
        val activation = VoiceActivation({ voice.enable(true) }, voice::listenGoal)
        var goal = ""
        activation.requestGoal { goal = it }
        assertEquals(0, d.listens)
        activation.grant(); activation.grant()
        assertEquals(1, d.listens)
        d.result!!.invoke("수원 날씨")
        assertEquals("수원 날씨", goal)
        assertTrue(commands.isEmpty())
    }

    @Test fun cancellationOrBackgroundCannotBeReactivatedByLatePermissionResult() {
        val d = Device(); val voice = VoiceCoordinator(d, {})
        val activation = VoiceActivation({ voice.enable(true) }, voice::listenGoal)
        activation.requestGoal { fail("late purpose") }
        activation.cancel(); activation.grant()
        assertEquals(0, d.listens)
        assertFalse(voice.state.value.enabled)
        activation.requestEnable(); activation.cancel(); activation.grant()
        assertFalse(voice.state.value.enabled)
    }

    @Test fun explicitEnableWithoutPurposeDoesNotOpenMicrophoneBeforeAQuestion() {
        val d = Device(); val voice = VoiceCoordinator(d, {})
        val activation = VoiceActivation({ voice.enable(true) }, voice::listenGoal)
        activation.requestEnable(); activation.grant()
        assertTrue(voice.state.value.enabled)
        assertEquals(0, d.listens)
    }
}
