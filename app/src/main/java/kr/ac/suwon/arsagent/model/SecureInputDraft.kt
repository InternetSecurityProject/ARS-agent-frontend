package kr.ac.suwon.arsagent.model

import java.time.Instant
import kr.ac.suwon.arsagent.data.api.SessionAction
import kr.ac.suwon.arsagent.data.api.UserRequest

/** Ephemeral input only. Never put this object or its payload in saved state or logs. */
class SecureInputDraft(private val request: UserRequest) {
    private val buffer = CharArray(20)
    var count: Int = 0
        private set
    val deadline: Instant? = runCatching { Instant.parse(request.expiresAt) }.getOrNull()

    fun isCurrent(now: Instant): Boolean = request.kind == "secure_input" && request.requestId.isNotBlank() && deadline?.isAfter(now) == true
    fun accepts(key: Char): Boolean = key in "0123456789*#" && request.terminator != key.toString()

    fun append(key: Char, now: Instant) {
        if (!isCurrent(now)) { clear(); return }
        if (accepts(key) && count < buffer.size) buffer[count++] = key
    }

    fun delete() { if (count > 0) buffer[--count] = '\u0000' }
    fun clear() { buffer.fill('\u0000'); count = 0 }

    fun submit(now: Instant): SessionAction.SecureInput? {
        if (!isCurrent(now)) { clear(); return null }
        if (count == 0) return null
        // A transient String is required by the transport contract; it cannot be reliably zeroed.
        val action = SessionAction.SecureInput(request.requestId, String(buffer, 0, count))
        clear()
        return action
    }

    override fun toString(): String = "SecureInputDraft([redacted])"
}
