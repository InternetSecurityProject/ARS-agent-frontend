package kr.ac.suwon.arsagent.servertest

import java.io.Closeable
import kotlinx.coroutines.flow.Flow
import kr.ac.suwon.arsagent.data.api.*

interface ServerTestGateway : Closeable {
    suspend fun health(): HealthResponse
    suspend fun create(phone: String, goal: String): CreatedSession
    fun events(session: CreatedSession): Flow<SessionEvent>
    suspend fun act(session: CreatedSession, action: SessionAction): ActionResponse
    suspend fun result(session: CreatedSession): SessionResult
}

class HttpServerTestGateway(key: String, baseUrl: String = SERVER_TEST_URL, allowLocalHttp: Boolean = false) : ServerTestGateway {
    private val api = ArsApiClient(ArsApiConfig(baseUrl, key, allowLocalHttp))
    override suspend fun health() = api.health()
    override suspend fun create(phone: String, goal: String) = api.createSession(phone, goal)
    override fun events(session: CreatedSession) = api.events(session)
    override suspend fun act(session: CreatedSession, action: SessionAction) = api.sendAction(session, action)
    override suspend fun result(session: CreatedSession) = api.getResult(session)
    override fun close() = api.close()
}
