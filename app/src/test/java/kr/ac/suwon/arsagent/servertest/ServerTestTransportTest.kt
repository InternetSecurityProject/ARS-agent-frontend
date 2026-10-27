package kr.ac.suwon.arsagent.servertest

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import kr.ac.suwon.arsagent.data.api.apiJson
import kr.ac.suwon.arsagent.model.BCallData
import kr.ac.suwon.arsagent.model.CandidateChoice
import java.time.Instant
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class ServerTestTransportTest {
    @Test fun controllerUsesRealRestAndWebsocketForRequestSelectionAndResult() = runBlocking {
        verifyLocalTransport(ServerTestMode.Sim)
    }

    @Test fun confirmedRealModeUsesOnlyLocalRestAndWebsocketForSelectionAndResult() = runBlocking {
        verifyLocalTransport(ServerTestMode.RealCall)
    }

    private suspend fun verifyLocalTransport(mode: ServerTestMode) {
        val server = MockWebServer().apply { start() }
        val socket = AtomicReference<WebSocket>()
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        fun json(body: String, status: Int = 200) = MockResponse().setResponseCode(status)
            .setHeader("Content-Type", "application/json").setBody(body)
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                return when (request.requestUrl?.encodedPath) {
                    "/v1/health" -> json("""{"backend":"${mode.backend}","stt":"none","interpreter":"MockInterpreter"}""")
                    "/v1/sessions" -> {
                        val ws = server.url("/v1/sessions/s1/events").toString().replace("http://", "ws://")
                        json("""{"session_id":"s1","session_token":"test-session-secret","events_url":"$ws","audio_url":"${ws.replace("events", "audio")}","phone_number":"07000000000","status":"dialing","goal_summary":"잔액 조회"}""", 201)
                    }
                    "/v1/sessions/s1/events" -> MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            socket.set(webSocket)
                            webSocket.send("""{"event":"snapshot","at":"now","data":{"status":"awaiting_user","message":"어떤 조회인가요?","pending_request":{"request_id":"real-r","kind":"clarification","message":"선택해 주세요","expires_at":"2099-01-01T00:00:00Z","options":[{"key":"3","label":"잔액 조회"}]}}}""")
                            webSocket.send("""{"event":"caption","at":"now","data":{"step":1,"speaker":"ars","text":"잔액 조회는 3번입니다","at":"now"}}""")
                            webSocket.send("""{"event":"path_updated","at":"now","data":{"step":1,"text":"기관의 긴 메뉴 원문","selected":{"key":"5","label":"은행 업무"},"actor":"ai"}}""")
                        }
                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
                    })
                    "/v1/sessions/s1/actions" -> {
                        socket.get().send("""{"event":"summary","at":"now","data":{"kind":"result","summary":"잔액 10,000원"}}""")
                        socket.get().send("""{"event":"ended","at":"now","data":{"end_reason":"completed","message":"테스트 종료"}}""")
                        json("""{"accepted":true,"key_sent":"3"}""")
                    }
                    "/v1/sessions/s1/result" -> json("""{"outcome":"success","end_reason":"completed","summary":"잔액 10,000원","path":[{"step":2,"text":"최종 선택","selected":{"key":"3","label":"잔액 조회"},"actor":"user"}],"duration_sec":23}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val c = ServerTestController(scope, { key -> HttpServerTestGateway(key, server.url("/v1").toString(), true) })
        try {
            c.start("test-development-key", "070-0000-0000", "잔액 조회", mode)
            val live = withTimeout(5_000) { c.state.first {
                it.snapshot.pendingRequest?.requestId == "real-r" && it.snapshot.captions.isNotEmpty() && it.snapshot.path.isNotEmpty()
            } }
            val progress = BCallData.from(live.snapshot)
            assertEquals("잔액 조회는 3번입니다", progress.caption)
            assertEquals(listOf("은행 업무 (5번)"), progress.path)
            assertFalse(progress.isEnded)
            // The C screen prepares this typed action; only an explicitly connected owner sends it.
            val selected = requireNotNull(CandidateChoice(requireNotNull(live.snapshot.pendingRequest)).select("3", Instant.now()))
            c.respond(selected.requestId, selected.key, selection = true)
            val final = withTimeout(5_000) { c.state.first { it.result != null } }
            assertEquals("잔액 10,000원", final.result?.summary)
            assertEquals(23, final.result?.durationSec)
            val displayedResult = BCallData.from(final.snapshot, final.result)
            assertEquals("잔액 10,000원", displayedResult.summary)
            assertEquals(listOf("잔액 조회 (3번)"), displayedResult.path)
            assertEquals(23, displayedResult.durationSeconds)
            assertTrue(displayedResult.isEnded)
            val create = requests.single { it.method == "POST" && it.requestUrl?.encodedPath == "/v1/sessions" }
            assertEquals("test-development-key", create.getHeader("X-App-Key"))
            assertEquals("07000000000", apiJson.parseToJsonElement(create.body.readUtf8()).jsonObject["phone_number"]?.jsonPrimitive?.content)
            val action = requests.single { it.requestUrl?.encodedPath == "/v1/sessions/s1/actions" }
            assertEquals("Bearer test-session-secret", action.getHeader("Authorization"))
            assertEquals(apiJson.parseToJsonElement("""{"type":"select","request_id":"real-r","key":"3"}"""), apiJson.parseToJsonElement(action.body.readUtf8()))
            assertNull(requests.first().getHeader("X-App-Key"))
        } finally {
            c.close(); scope.cancel(); server.shutdown()
        }
    }
}
