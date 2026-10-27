package kr.ac.suwon.arsagent.model

import kr.ac.suwon.arsagent.data.api.*
import org.junit.Assert.*
import org.junit.Test

class BCallDataTest {
    private val path = listOf(
        PathStep(1, "전체 ARS 안내", selected = MenuOption("5", "카드 서비스"), actor = "ai"),
        PathStep(2, "카드 메뉴 안내", selected = MenuOption("2", "결제일 변경"), actor = "user"),
    )
    private val request = UserRequest("r7", "secure_input", "계좌번호를 입력해 주세요", "2099-01-01T00:00:00Z",
        label = "계좌번호", terminator = "#")

    @Test fun selectingUsesSelectedLabelsAndMostRecentArsCaptionNotDemoContent() {
        val data = BCallData.from(SessionSnapshot("selecting", "서버가 보낸 진행 안내", goal = "결제일 바꾸기", path = path,
            captions = listOf(Caption(1, "ars", "처음 안내", "now"), Caption(2, "ars", "결제일 변경은 2번", "now"),
                Caption(3, "user", "사용자 발화", "now"))))
        assertEquals("메뉴 선택 중", data.title)
        assertEquals("서버가 보낸 진행 안내", data.message)
        assertEquals("결제일 바꾸기", data.goal)
        assertEquals(listOf("카드 서비스 (5번)", "결제일 변경 (2번)"), data.path)
        assertEquals("결제일 변경은 2번", data.caption)
        assertNull(data.summary)
        assertFalse(data.isEnded)
        assertNull(data.durationSeconds)
    }

    @Test fun replacementSnapshotDoesNotRetainPriorRouteOrCaption() {
        BCallData.from(SessionSnapshot("selecting", "이전 안내", path = path))
        val data = BCallData.from(SessionSnapshot("listening", "새 연결 안내"))
        assertTrue(data.path.isEmpty())
        assertNull(data.caption)
        assertEquals("새 연결 안내", data.message)
    }

    @Test fun knownStatusesStayDistinctWithoutPretendingTheCallEnded() {
        val titles = mapOf("dialing" to "기관에 연결 중", "listening" to "ARS 안내를 듣는 중",
            "selecting" to "메뉴 선택 중", "listening_result" to "기관 안내", "agent_done" to "안내 결과",
            "waiting_agent" to "상담원 연결 대기", "connected_to_agent" to "상담원 연결")
        titles.forEach { (status, title) ->
            val data = BCallData.from(SessionSnapshot(status, "서버 안내"))
            assertEquals(title, data.title)
            assertFalse("$status is not a call end", data.isEnded)
        }
    }

    @Test fun summaryAndHandoffAreNotTerminalSignals() {
        val data = BCallData.from(SessionSnapshot("connected_to_agent", "상담원 연결됨",
            summary = ResultSummary("handoff", "상담원에게 연결했어요", handoffScript = "결제일 변경 문의입니다")))
        assertEquals("상담원에게 연결했어요", data.summary)
        assertEquals("결제일 변경 문의입니다", data.handoffScript)
        assertFalse(data.isEnded)
    }

    @Test fun pendingInputKeepsRealRequestMetadataInsteadOfInventingSixDigitRules() {
        val data = BCallData.from(SessionSnapshot("awaiting_user", "직접 입력이 필요해요", pendingRequest = request))
        assertEquals("입력이 필요해요", data.title)
        assertEquals("r7", data.pendingRequest?.requestId)
        assertEquals("계좌번호", data.pendingRequest?.label)
        assertEquals("#", data.pendingRequest?.terminator)
        assertFalse(data.isEnded)
    }

    @Test fun unknownStatusRemainsVisibleAndNeverMeansSuccess() {
        val data = BCallData.from(SessionSnapshot("future_transfer", "새 서버 상태"))
        assertEquals("future_transfer", data.status)
        assertEquals("진행 상태 확인 중", data.title)
        assertEquals("새 서버 상태", data.message)
        assertFalse(data.isEnded)
    }

    @Test fun completedRestResultOverridesStaleSnapshotEvenWithNullSummaryAndEmptyPath() {
        val stale = SessionSnapshot("agent_done", "이전 결과", path = path, pendingRequest = request,
            summary = ResultSummary("result", "이전 성공 안내", listOf(KeyInfo("금액", "10,000원"))))
        val final = CompletedResult("failed", "max_duration", durationSec = 42)
        val data = BCallData.from(stale, final)
        assertTrue(data.isEnded)
        assertEquals("ended", data.status)
        assertEquals("통화 종료", data.title)
        assertEquals("max_duration", data.endReason)
        assertEquals(42, data.durationSeconds)
        assertNull(data.summary)
        assertTrue(data.keyInfo.isEmpty())
        assertTrue(data.path.isEmpty())
        assertNull(data.pendingRequest)
        assertNotEquals("이전 결과", data.message)
    }

    @Test fun completedRestResultProvidesFinalSummaryPathAndDuration() {
        val final = CompletedResult("success", "completed", "신청이 접수됐어요", listOf(KeyInfo("신청일", "10월 27일")),
            path = path.take(1), durationSec = 65)
        val data = BCallData.from(SessionSnapshot("listening", "오래된 안내", path = path), final)
        assertEquals("신청이 접수됐어요", data.summary)
        assertEquals(listOf(KeyInfo("신청일", "10월 27일")), data.keyInfo)
        assertEquals(listOf("카드 서비스 (5번)"), data.path)
        assertEquals(65, data.durationSeconds)
        assertTrue(data.isEnded)
    }

    @Test fun endedEventWithoutRestResultDoesNotInventDuration() {
        val data = BCallData.from(SessionSnapshot("ended", "상대방이 종료했어요", pendingRequest = request, endReason = "no_answer"))
        assertTrue(data.isEnded)
        assertEquals("상대방이 종료했어요", data.message)
        assertEquals("no_answer", data.endReason)
        assertNull(data.durationSeconds)
        assertNull(data.pendingRequest)
    }

    @Test fun symbolKeysAreNotMistakenForNavigationOrDropped() {
        val data = BCallData.from(SessionSnapshot("selecting", "진행 중", path = listOf(
            PathStep(1, "안내", selected = MenuOption("*", "분실 신고"), actor = "ai"),
            PathStep(2, "다음 안내", selected = MenuOption("#", "빠른 말 서비스"), actor = "user"))))
        assertEquals(listOf("분실 신고 (*)", "빠른 말 서비스 (#)"), data.path)
    }

    @Test fun invalidNegativeDurationIsNotShownAsACallTime() {
        val data = BCallData.from(SessionSnapshot("ended", "종료"), CompletedResult("failed", "failed", durationSec = -1))
        assertNull(data.durationSeconds)
    }
}
