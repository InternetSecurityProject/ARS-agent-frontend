package kr.ac.suwon.arsagent.data.api

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ArsApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ArsApiClient

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        api = ArsApiClient(ArsApiConfig(server.url("/v1").toString(), "test-app-key", allowLocalHttp = true))
    }
    @After fun cleanup() { api.close(); server.shutdown() }

    private fun session(eventsUrl: String = server.url("/v1/sessions/ses_1/events").toString().replace("http://", "ws://")) =
        CreatedSession("ses_1", "test-session-token", eventsUrl,
            server.url("/v1/sessions/ses_1/audio").toString().replace("http://", "ws://"), "15771000", "dialing", "조회")

    private fun takeRequest() = server.takeRequest(5, TimeUnit.SECONDS) ?: error("No API request received")
    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
    private val snapshot = """{"event":"snapshot","at":"now","data":{"status":"listening","message":"안내 듣는 중","pending_request":null}}"""
    private val ended = """{"event":"ended","at":"later","data":{"end_reason":"completed","message":"종료"}}"""

    private fun socketResponse(vararg messages: String) = MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) { messages.forEach { webSocket.send(it) } }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
    })

    @Test fun healthUsesVersionedRouteWithoutAuthentication() = runBlocking<Unit> {
        server.enqueue(json("""{"backend":"sim","stt":"none","interpreter":"interpreter:MockInterpreter","mock_goals":["잔액 조회"],"active_session":null} """))
        val response = api.health()
        assertEquals("sim", response.backend)
        assertEquals(listOf("잔액 조회"), response.mockGoals)
        val request = takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1/health", request.path)
        assertNull(request.getHeader("X-App-Key"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test fun createSessionSendsNormalizedPhoneAndAppKeyOnce() = runBlocking<Unit> {
        val ws = server.url("/v1/sessions/ses_1/events").toString().replace("http://", "ws://")
        server.enqueue(json("""{"session_id":"ses_1","session_token":"test-session-token","events_url":"$ws","audio_url":"${ws.replace("events", "audio")}","phone_number":"15771000","status":"dialing","goal_summary":"조회해 드릴게요","new_field":true}""", 201))
        val response = api.createSession("1577-1000", " 조회하고 싶어 ")
        assertEquals("ses_1", response.sessionId)
        val request = takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/sessions", request.path)
        assertEquals("test-app-key", request.getHeader("X-App-Key"))
        assertNull(request.getHeader("Authorization"))
        val body = apiJson.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("15771000", body["phone_number"]?.jsonPrimitive?.content)
        assertEquals("조회하고 싶어", body["goal"]?.jsonPrimitive?.content)
        assertEquals(1, server.requestCount)
    }

    @Test fun actionsSendSessionAuthAndOnlyTheSpecifiedFields() = runBlocking<Unit> {
        val cases = listOf(
            SessionAction.Answer("r1", "집으로") to """{"type":"answer","request_id":"r1","text":"집으로"}""",
            SessionAction.Select("r1", "3") to """{"type":"select","request_id":"r1","key":"3"}""",
            SessionAction.Key("*#") to """{"type":"key","key":"*#"}""",
            SessionAction.SecureInput("r2", "1234") to """{"type":"secure_input","request_id":"r2","digits":"1234"}""",
            SessionAction.ReviseGoal("조회") to """{"type":"revise_goal","text":"조회"}""",
            SessionAction.Cancel to """{"type":"cancel"}""",
        )
        cases.forEach { (action, expected) ->
            server.enqueue(json("""{"accepted":true}"""))
            assertTrue(api.sendAction(session(), action).accepted)
            val request = takeRequest()
            assertEquals("/v1/sessions/ses_1/actions", request.path)
            assertEquals("Bearer test-session-token", request.getHeader("Authorization"))
            assertNull(request.getHeader("X-App-Key"))
            assertEquals(apiJson.parseToJsonElement(expected), apiJson.parseToJsonElement(request.body.readUtf8()))
        }
    }

    @Test fun resultDistinguishes202FromCompleted200() = runBlocking<Unit> {
        server.enqueue(json("""{"status":"agent_done"}""", 202))
        val active = api.getResult(session()) as SessionResult.InProgress
        assertEquals("agent_done", active.status)
        server.enqueue(json("""{"outcome":"success","end_reason":"completed","summary":"조회 완료","key_info":[{"label":"금액","value":"10,000원"}],"handoff_script":null,"path":[],"duration_sec":85}"""))
        val completed = (api.getResult(session()) as SessionResult.Completed).data
        assertEquals(85, completed.durationSec)
        assertEquals("10,000원", completed.keyInfo.single().value)
        repeat(2) {
            val request = takeRequest()
            assertEquals("/v1/sessions/ses_1/result", request.path)
            assertEquals("Bearer test-session-token", request.getHeader("Authorization"))
        }
    }

    @Test fun errorsKeepStatusAndCodeButNotRawSensitiveBodies() = runBlocking<Unit> {
        listOf(400 to "invalid_request", 401 to "unauthorized", 404 to "not_found", 409 to "invalid_state",
            409 to "sensitive_input_blocked", 503 to "unavailable").forEach { (status, code) ->
            server.enqueue(json("""{"error":{"code":"$code","message":"test-session-token 123456"}}""", status))
            val error = assertThrows(ApiHttpException::class.java) { runBlocking { api.sendAction(session(), SessionAction.Cancel) } }
            assertEquals(status, error.status)
            assertEquals(code, error.code)
            assertFalse(error.toString().contains("test-session-token"))
            assertFalse(error.toString().contains("123456"))
            assertNull(error.cause)
        }
    }

    @Test fun malformedAndOversizedResponsesFailWithoutEchoingPayload() = runBlocking<Unit> {
        listOf("not json test-session-token", "{}", "x".repeat(1_048_577)).forEach { body ->
            server.enqueue(json(body))
            val error = assertThrows(ApiProtocolException::class.java) { runBlocking { api.health() } }
            assertFalse(error.toString().contains("test-session-token"))
        }
    }

    @Test fun mutationIsNotRetriedAfterResponseIsLost() = runBlocking<Unit> {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertThrows(ApiConnectionException::class.java) { runBlocking { api.createSession("131", "날씨") } }
        assertEquals("/v1/sessions", takeRequest().path)
        assertEquals(1, server.requestCount)
    }

    @Test fun createIsNotRetriedWhen503RequestsImmediateRetry() = runBlocking<Unit> {
        server.enqueue(json("""{"error":{"code":"unavailable","message":"busy"}}""", 503).setHeader("Retry-After", "0"))
        server.enqueue(json("{}", 201))
        val error = assertThrows(ApiHttpException::class.java) { runBlocking { api.createSession("131", "날씨") } }
        assertEquals(503, error.status)
        assertEquals(1, server.requestCount)
    }

    @Test fun actionIsNotRetriedWhen503RequestsImmediateRetry() = runBlocking<Unit> {
        server.enqueue(json("""{"error":{"code":"unavailable","message":"busy"}}""", 503).setHeader("Retry-After", "0"))
        server.enqueue(json("""{"accepted":true}"""))
        val error = assertThrows(ApiHttpException::class.java) { runBlocking { api.sendAction(session(), SessionAction.Key("3")) } }
        assertEquals(503, error.status)
        assertEquals(1, server.requestCount)
    }

    @Test fun redirectsAreNotFollowedWithCredentials() = runBlocking<Unit> {
        MockWebServer().use { other ->
            other.start()
            server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", other.url("/stolen")))
            val error = assertThrows(ApiHttpException::class.java) { runBlocking { api.createSession("131", "날씨") } }
            assertEquals(307, error.status)
            assertEquals(0, other.requestCount)
        }
    }

    @Test fun invalidActionIsRejectedBeforeAnyNetworkRequest() = runBlocking<Unit> {
        listOf<SessionAction>(SessionAction.Key("12345"), SessionAction.Key("a"), SessionAction.SecureInput("r", ""),
            SessionAction.SecureInput("r", "1".repeat(21)), SessionAction.Answer("", "답"),
            SessionAction.Select("r", ""), SessionAction.ReviseGoal(" ")).forEach { action ->
            assertThrows(IllegalArgumentException::class.java) { runBlocking { api.sendAction(session(), action) } }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun cancelingCoroutineReleasesStalledRequestPromptly() = runBlocking<Unit> {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = async(Dispatchers.Default) { api.health() }
        takeRequest()
        withTimeout(2_000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
    }

    @Test fun websocketUsesTokenQueryAndEndsOnlyOnEndedEvent() = runBlocking<Unit> {
        val summary = """{"event":"summary","at":"now","data":{"kind":"result","summary":"結果","key_info":[]}}"""
        server.enqueue(socketResponse(snapshot, summary, ended))
        val events = withTimeout(5_000) { api.events(session()).toList() }
        assertTrue(events[0] is SessionEvent.Snapshot)
        assertTrue(events[1] is SessionEvent.SummaryReceived)
        assertTrue(events[2] is SessionEvent.Ended)
        val request = takeRequest()
        assertEquals("/v1/sessions/ses_1/events", request.requestUrl?.encodedPath)
        assertEquals("test-session-token", request.requestUrl?.queryParameter("token"))
        assertNull(request.getHeader("X-App-Key"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test fun reconnectStartsANewStreamWithCurrentSnapshotWithoutSendingActions() = runBlocking<Unit> {
        server.enqueue(socketResponse(snapshot))
        assertEquals("listening", (withTimeout(5_000) { api.events(session()).first() } as SessionEvent.Snapshot).data.status)
        server.enqueue(socketResponse(snapshot.replace("listening", "agent_done")))
        assertEquals("agent_done", (withTimeout(5_000) { api.events(session()).first() } as SessionEvent.Snapshot).data.status)
        repeat(2) { assertEquals("GET", takeRequest().method) }
        assertEquals(2, server.requestCount)
    }

    @Test fun reconnectToAnEndedSessionCompletesFromSnapshot() = runBlocking<Unit> {
        server.enqueue(socketResponse("""{"event":"snapshot","at":"now","data":{"status":"ended","message":"종료","end_reason":"completed"}}"""))
        val events = withTimeout(1_000) { api.events(session()).toList() }
        assertEquals(1, events.size)
        assertEquals("ended", (events.single() as SessionEvent.Snapshot).data.status)
    }

    @Test fun foreignWebsocketOriginIsRejectedBeforeSendingToken() = runBlocking<Unit> {
        assertThrows(ApiProtocolException::class.java) {
            runBlocking { api.events(session("wss://other.example/v1/sessions/ses_1/events")).first() }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun websocketRejectsWrongPathExistingQueryAndEmbeddedCredentials() = runBlocking<Unit> {
        val valid = session().eventsUrl
        listOf(valid.replace("/events", "/audio"), "$valid?token=unexpected", "$valid#fragment",
            valid.replace("ws://", "ws://user:pass@")).forEach { url ->
            assertThrows(ApiProtocolException::class.java) { runBlocking { api.events(session(url)).first() } }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun secureOriginDoesNotPermitWebsocketDowngrade() = runBlocking<Unit> {
        ArsApiClient(ArsApiConfig("https://api.example", "test-key")).use { secureApi ->
            assertThrows(ApiProtocolException::class.java) {
                runBlocking { secureApi.events(session("ws://api.example/v1/sessions/ses_1/events")).first() }
            }
        }
    }

    @Test fun closingClientTerminatesAnActiveStreamAndRejectsNewRequests() = runBlocking<Unit> {
        server.enqueue(socketResponse(snapshot))
        val ready = CompletableDeferred<Unit>()
        val result = async { runCatching { api.events(session()).onEach { ready.complete(Unit) }.toList() } }
        withTimeout(5_000) { ready.await() }
        api.close()
        assertTrue(withTimeout(2_000) { result.await().exceptionOrNull() } is ApiStreamException)
        assertThrows(IllegalStateException::class.java) { runBlocking { api.health() } }
    }

    @Test fun cancelingFlowClosesTheUnderlyingWebsocket() = runBlocking<Unit> {
        val disconnected = CompletableDeferred<Unit>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send(snapshot) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null); disconnected.complete(Unit) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { disconnected.complete(Unit) }
        }))
        val ready = CompletableDeferred<Unit>()
        val job = launch { api.events(session()).collect { ready.complete(Unit) } }
        withTimeout(5_000) { ready.await() }
        job.cancelAndJoin()
        withTimeout(2_000) { disconnected.await() }
    }

    @Test fun slowConsumerGetsExplicitFailureAndSocketIsClosed() = runBlocking<Unit> {
        val disconnected = CompletableDeferred<Unit>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(snapshot)
                repeat(256) { n -> webSocket.send("""{"event":"caption","at":"now","data":{"step":$n,"speaker":"ars","text":"안내","at":"now"}}""") }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null); disconnected.complete(Unit) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { disconnected.complete(Unit) }
        }))
        val gate = CompletableDeferred<Unit>()
        val result = async { runCatching { api.events(session()).onEach { if (it is SessionEvent.Snapshot) gate.await() }.toList() } }
        withTimeout(5_000) { disconnected.await() }
        gate.complete(Unit)
        assertTrue(withTimeout(2_000) { result.await().exceptionOrNull() } is ApiStreamException)
    }

    @Test fun malformedEventsFailWithoutLeakingTheEventBody() = runBlocking<Unit> {
        server.enqueue(socketResponse("bad-json-test-session-token"))
        val error = assertThrows(ApiProtocolException::class.java) {
            runBlocking { withTimeout(5_000) { api.events(session()).first() } }
        }
        assertFalse(error.toString().contains("test-session-token"))
    }

    @Test fun websocketDisconnectIsExplicitAndIsNotAutomaticallyReconnected() = runBlocking<Unit> {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send(snapshot); webSocket.close(1008, "private-token") }
        }))
        val error = assertThrows(ApiStreamException::class.java) {
            runBlocking { withTimeout(5_000) { api.events(session()).toList() } }
        }
        assertEquals(1008, error.closeCode)
        assertFalse(error.toString().contains("private-token"))
        assertEquals(1, server.requestCount)
    }
}
