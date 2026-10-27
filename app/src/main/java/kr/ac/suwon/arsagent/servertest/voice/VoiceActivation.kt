package kr.ac.suwon.arsagent.servertest.voice

/** An explicit user intent survives disclosure/permission, but never cancellation/backgrounding. */
class VoiceActivation(private val enable: () -> Unit,
    private val listenGoal: ((String) -> Unit) -> Unit) {
    private var requested = false
    private var goal: ((String) -> Unit)? = null
    fun requestEnable() { requested = true; goal = null }
    fun requestGoal(result: (String) -> Unit) { requested = true; goal = result }
    fun grant() {
        if (!requested) return
        val result = goal
        cancel() // consume before platform callbacks can re-enter
        enable()
        if (result != null) listenGoal(result)
    }
    fun cancel() { requested = false; goal = null }
}
