package kr.ac.suwon.arsagent.data.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ApiContractTest {
    @Test fun baseAddressNormalizesV1WithoutDoublingIt() {
        assertEquals("https://api.example/v1/", ArsApiConfig("https://api.example", "key").baseUrl.toString())
        assertEquals("https://api.example/v1/", ArsApiConfig("https://api.example/v1", "key").baseUrl.toString())
        assertEquals("https://api.example/v1/", ArsApiConfig("https://api.example/v1/", "key").baseUrl.toString())
    }

    @Test fun insecureRemoteAndCredentialBearingAddressesAreRejected() {
        listOf("http://api.example", "https://user:pass@api.example", "https://api.example?token=bad",
            "https://api.example/#bad").forEach { address ->
            assertThrows(IllegalArgumentException::class.java) { ArsApiConfig(address, "key", allowLocalHttp = true) }
        }
        assertThrows(IllegalArgumentException::class.java) { ArsApiConfig("http://127.0.0.1:8000", "key") }
        assertEquals("http://10.0.2.2:8000/v1/", ArsApiConfig("http://10.0.2.2:8000", "key", true).baseUrl.toString())
    }

    @Test fun snapshotRestoresPendingRequestAndStructuredResults() {
        val event = EventCodec.decode("""{"event":"snapshot","at":"2026-10-25T12:00:00+09:00","data":{
            "status":"awaiting_user","message":"선택해 주세요","goal":"조회","goal_summary":"조회해 드릴게요",
            "path":[{"step":1,"text":"조회(3) 선택","options":[],"selected":{"key":"3","label":"조회","category":null,"confidence":0.9},"actor":"ai"}],
            "captions":[{"step":1,"speaker":"ars","text":"조회는 3번","at":"now"}],
            "pending_request":{"request_id":"req_2","kind":"clarification","reason":"ambiguous","message":"어떤 조회인가요?","expires_at":"later","options":[{"key":"1","label":"잔액","category":"잔액 조회","confidence":null}]},
            "summary":{"kind":"result","summary":"조회 결과","key_info":[{"label":"금액","value":"10,000원"}],"handoff_script":null},
            "end_reason":null,"future_field":true}}""")
        assertTrue(event is SessionEvent.Snapshot)
        val snapshot = (event as SessionEvent.Snapshot).data
        assertEquals("awaiting_user", snapshot.status)
        assertEquals("req_2", snapshot.pendingRequest?.requestId)
        assertEquals("잔액", snapshot.pendingRequest?.options?.single()?.label)
        assertEquals("3", snapshot.path.single().selected.key)
        assertEquals("조회는 3번", snapshot.captions.single().text)
        assertEquals("10,000원", snapshot.summary?.keyInfo?.single()?.value)
    }

    @Test fun allDocumentedEventKindsDecodeAndFutureKindsRemainIdentifiable() {
        val fixtures = listOf(
            """{"event":"status_changed","at":"now","data":{"status":"connected_to_agent","message":"연결됨"}}""" to SessionEvent.StatusChanged::class.java,
            """{"event":"caption","at":"now","data":{"step":1,"speaker":"ars","text":"안내","at":"now"}}""" to SessionEvent.CaptionAdded::class.java,
            """{"event":"path_updated","at":"now","data":{"step":2,"text":"선택","options":[],"selected":{"key":"0","label":"상담원"},"actor":"user"}}""" to SessionEvent.PathUpdated::class.java,
            """{"event":"request","at":"now","data":{"request_id":"r","kind":"secure_input","message":"입력","expires_at":"later","label":"비밀번호","terminator":"#"}}""" to SessionEvent.InputRequested::class.java,
            """{"event":"input_rejected","at":"now","data":{"keys":"3","actor":"user","ars_prompt":"다시 입력","retry_count":1,"message":"재시도"}}""" to SessionEvent.InputRejected::class.java,
            """{"event":"summary","at":"now","data":{"kind":"handoff","summary":"연결","handoff_script":"상담 내용"}}""" to SessionEvent.SummaryReceived::class.java,
            """{"event":"ended","at":"now","data":{"end_reason":"completed","message":"종료"}}""" to SessionEvent.Ended::class.java,
            """{"event":"error","at":"now","data":{"code":"failed","message":"오류"}}""" to SessionEvent.ErrorReceived::class.java,
        )
        fixtures.forEach { (json, expected) -> assertEquals(expected, EventCodec.decode(json).javaClass) }
        val unknown = EventCodec.decode("""{"event":"future","at":"now","data":{"extra":true}}""") as SessionEvent.Unknown
        assertEquals("future", unknown.name)
        assertEquals(true, unknown.data["extra"]?.jsonPrimitive?.boolean)
    }

    @Test fun actionsHaveExactDiscriminatorsAndDoNotInventSecureTerminators() {
        val actions = listOf<SessionAction>(SessionAction.Answer("r1", "집으로"), SessionAction.Select("r1", "3"),
            SessionAction.Key("*"), SessionAction.SecureInput("r2", "1234"), SessionAction.ReviseGoal("조회"), SessionAction.Cancel)
        val expected = listOf("answer", "select", "key", "secure_input", "revise_goal", "cancel")
        actions.zip(expected).forEach { (action, type) ->
            val wire = apiJson.parseToJsonElement(apiJson.encodeToString(action)).jsonObject
            assertEquals(type, wire["type"]?.jsonPrimitive?.content)
        }
        val secure = apiJson.parseToJsonElement(apiJson.encodeToString<SessionAction>(actions[3])).jsonObject
        assertEquals("r2", secure["request_id"]?.jsonPrimitive?.content)
        assertEquals("1234", secure["digits"]?.jsonPrimitive?.content)
        assertFalse(actions[3].toString().contains("1234"))
    }

    @Test fun rejectedInputWithNoRecordedKeyStillDecodes() {
        val event = EventCodec.decode("""{"event":"input_rejected","at":"now","data":{"keys":null,"actor":"ai","ars_prompt":"다시 입력","retry_count":1,"message":"재시도"}}""")
        assertNull((event as SessionEvent.InputRejected).data.keys)
    }

    @Test fun diagnosticStringsDoNotExposeKeysTokensOrTokenizedUrls() {
        val config = ArsApiConfig("https://api.example", "private-app-key")
        assertFalse(config.toString().contains("private-app-key"))
        val session = CreatedSession("s", "private-session-token", "wss://api.example/v1/sessions/s/events",
            "wss://api.example/v1/sessions/s/audio", "131", "dialing", "목적")
        assertFalse(session.toString().contains("private-session-token"))
        assertFalse(session.toString().contains("wss://"))
    }
}
