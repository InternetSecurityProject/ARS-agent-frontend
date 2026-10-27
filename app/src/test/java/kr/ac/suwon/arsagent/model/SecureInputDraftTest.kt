package kr.ac.suwon.arsagent.model

import java.time.Instant
import kr.ac.suwon.arsagent.data.api.*
import org.junit.Assert.*
import org.junit.Test

class SecureInputDraftTest {
    private val now = Instant.parse("2026-10-27T00:00:00Z")
    private val request = UserRequest("secure-r1", "secure_input", "계좌번호를 입력해 주세요", "2026-10-27T00:01:00Z", label = "계좌번호", terminator = "#")

    @Test fun storesUpToTwentyWithoutAutoSubmissionAndExplicitSubmitConsumesIt() {
        for (digits in listOf("1", "112233", "12345678901234567890")) {
            val draft = SecureInputDraft(request)
            digits.forEach { draft.append(it, now) }
            assertEquals(digits.length, draft.count)
            val action = requireNotNull(draft.submit(now))
            assertEquals("secure-r1", action.requestId)
            assertEquals(digits, action.digits)
            assertEquals(0, draft.count)
            assertNull(draft.submit(now))
        }
    }

    @Test fun invalidSymbolsAndConfiguredTerminatorAreNeverAppended() {
        val draft = SecureInputDraft(request)
        "12#abc".forEach { draft.append(it, now) }
        assertEquals("12", requireNotNull(draft.submit(now)).digits)
        val symbols = SecureInputDraft(request.copy(terminator = null))
        "1*#".forEach { symbols.append(it, now) }
        assertEquals("1*#", requireNotNull(symbols.submit(now)).digits)
    }

    @Test fun overflowDoesNotTruncateAndSubmitAutomatically() {
        val draft = SecureInputDraft(request)
        "1".repeat(21).forEach { draft.append(it, now) }
        assertEquals(20, draft.count)
        assertEquals("1".repeat(20), requireNotNull(draft.submit(now)).digits)
    }

    @Test fun emptyWrongKindBlankIdAndExpiredRequestsCannotProduceAnAction() {
        for (invalid in listOf(request.copy(kind = "clarification"), request.copy(requestId = " "),
            request.copy(expiresAt = "invalid"), request.copy(expiresAt = now.toString()))) {
            val draft = SecureInputDraft(invalid)
            draft.append('1', now)
            assertEquals(0, draft.count)
            assertNull(draft.submit(now))
        }
        assertNull(SecureInputDraft(request).submit(now))
    }

    @Test fun expirationClearsPreviouslyEnteredDigitsRatherThanReusingThem() {
        val draft = SecureInputDraft(request)
        draft.append('7', now)
        assertNull(draft.submit(now.plusSeconds(60)))
        assertEquals(0, draft.count)
    }

    @Test fun deleteAndClearRemoveDigitsAndStringRepresentationIsRedacted() {
        val draft = SecureInputDraft(request)
        "112233".forEach { draft.append(it, now) }
        assertFalse(draft.toString().contains("112233"))
        draft.delete()
        assertEquals("11223", requireNotNull(draft.submit(now)).digits)
        draft.append('8', now)
        draft.clear()
        assertEquals(0, draft.count)
        assertNull(draft.submit(now))
    }
}
