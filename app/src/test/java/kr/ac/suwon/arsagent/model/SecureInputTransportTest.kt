package kr.ac.suwon.arsagent.model

import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kr.ac.suwon.arsagent.data.api.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class SecureInputTransportTest {
    @Test fun explicitDraftCompletionSerializesAsSecureInputWithoutTerminatorOrExtraRequest() = runBlocking {
        val server = MockWebServer().apply { start() }
        val api = ArsApiClient(ArsApiConfig(server.url("/v1").toString(), "synthetic-test-key", allowLocalHttp = true))
        try {
            val request = UserRequest("secure-r1", "secure_input", "가상 번호 입력", "2099-01-01T00:00:00Z", terminator = "#")
            val draft = SecureInputDraft(request)
            "11223344".forEach { draft.append(it, Instant.now()) }
            assertEquals(0, server.requestCount)
            val action = requireNotNull(draft.submit(Instant.now()))
            assertEquals(0, draft.count)
            assertNull(draft.submit(Instant.now()))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"accepted":true}"""))
            val session = CreatedSession("s1", "synthetic-session-token", "", "", "07000000000", "awaiting_user", "가상 테스트")
            api.sendAction(session, action)
            val sent = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("/v1/sessions/s1/actions", sent.path)
            assertEquals("Bearer synthetic-session-token", sent.getHeader("Authorization"))
            assertEquals(apiJson.parseToJsonElement("""{"type":"secure_input","request_id":"secure-r1","digits":"11223344"}"""), apiJson.parseToJsonElement(sent.body.readUtf8()))
            assertEquals(1, server.requestCount)
        } finally { api.close(); server.shutdown() }
    }
}
