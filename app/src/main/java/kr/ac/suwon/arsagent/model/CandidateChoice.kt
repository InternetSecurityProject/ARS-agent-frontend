package kr.ac.suwon.arsagent.model

import java.time.Instant
import kr.ac.suwon.arsagent.data.api.MenuOption
import kr.ac.suwon.arsagent.data.api.SessionAction
import kr.ac.suwon.arsagent.data.api.UserRequest

/** Pure server-request adapter; neither sends actions nor guesses missing candidates. */
class CandidateChoice(private val request: UserRequest) {
    val options: List<MenuOption> = request.options
    val deadline: Instant? = runCatching { Instant.parse(request.expiresAt) }.getOrNull()

    fun select(key: String, now: Instant): SessionAction.Select? {
        if (request.kind != "clarification" || request.requestId.isBlank() || key.isBlank()) return null
        if (deadline?.isAfter(now) != true || options.count { it.key == key } != 1) return null
        return SessionAction.Select(request.requestId, key)
    }
}
