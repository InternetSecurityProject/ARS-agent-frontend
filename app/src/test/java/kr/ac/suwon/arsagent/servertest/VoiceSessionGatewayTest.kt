package kr.ac.suwon.arsagent.servertest

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.emptyFlow
import kr.ac.suwon.arsagent.data.api.*
import kr.ac.suwon.arsagent.servertest.voice.VoiceSessionRelay
import org.junit.Assert.*
import org.junit.Test

class VoiceSessionGatewayTest {
    @Test fun healthProbeCannotClearActiveAudioSessionOrCreateAnotherCall() = runBlocking {
        var creates = 0; var actions = 0
        val session = CreatedSession("s1", "private-token", "", "", "131", "dialing", "조회")
        val base = object : ServerTestGateway {
            override suspend fun health() = HealthResponse("sim", "none", "mock")
            override suspend fun create(phone: String, goal: String): CreatedSession { creates++; return session }
            override fun events(session: CreatedSession) = emptyFlow<SessionEvent>()
            override suspend fun act(session: CreatedSession, action: SessionAction): ActionResponse { actions++; return ActionResponse(true) }
            override suspend fun result(session: CreatedSession): SessionResult = SessionResult.InProgress("listening")
            override fun close() {}
        }
        val relay = VoiceSessionRelay(); val observed = mutableListOf<CreatedSession?>()
        relay.observe(observed::add); assertEquals(listOf<CreatedSession?>(null), observed)
        val call = relay.decorate(base); call.create("131", "조회")
        val health = relay.decorate(base); health.health(); health.close()
        assertSame(session, observed.last()); assertEquals(1, creates); assertEquals(0, actions)
        call.close(); assertNull(observed.last()); assertEquals(1, creates); assertEquals(0, actions)
    }
}
