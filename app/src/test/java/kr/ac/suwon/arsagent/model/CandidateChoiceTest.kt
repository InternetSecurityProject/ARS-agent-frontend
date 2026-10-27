package kr.ac.suwon.arsagent.model

import java.time.Instant
import kr.ac.suwon.arsagent.data.api.*
import org.junit.Assert.*
import org.junit.Test

class CandidateChoiceTest {
    private val now = Instant.parse("2026-10-27T00:00:00Z")
    private val request = UserRequest("request-27", "clarification", "어느 업무인가요?", "2026-10-27T09:00:05+09:00",
        options = listOf(MenuOption("3", "결제 대금 조회"), MenuOption("#", "결제일 변경"), MenuOption("*", "다른 업무")))

    @Test fun preservesServerOrderLabelsAndKeys() {
        assertEquals(request.options, CandidateChoice(request).options)
    }

    @Test fun buildsTypedSelectWithExactRequestIdForNumericAndSymbolKeysIncludingCriticalActions() {
        for (key in listOf("3", "#", "*")) {
            assertEquals(SessionAction.Select("request-27", key), CandidateChoice(request.copy(reason = "critical_action")).select(key, now))
        }
    }

    @Test fun deadlineIsExclusiveAndInvalidDatesFailClosed() {
        assertNotNull(CandidateChoice(request).select("3", now.plusSeconds(4)))
        for (date in listOf("2026-10-27T00:00:00Z", "2026-10-26T00:00:00Z", "invalid", "")) {
            assertNull(CandidateChoice(request.copy(expiresAt = date)).select("3", now))
        }
    }

    @Test fun rejectsWrongKindUnknownKeyMissingIdAndEmptyCandidates() {
        for (invalid in listOf(request.copy(kind = "secure_input"), request.copy(kind = "future_kind"),
            request.copy(requestId = " "), request.copy(options = emptyList()))) {
            assertNull(CandidateChoice(invalid).select("3", now))
        }
        assertNull(CandidateChoice(request).select("9", now))
    }

    @Test fun duplicateKeysCannotAccidentallyChooseTheWrongLabel() {
        val duplicate = request.copy(options = listOf(MenuOption("3", "조회"), MenuOption("3", "해지")))
        assertNull(CandidateChoice(duplicate).select("3", now))
    }
}
