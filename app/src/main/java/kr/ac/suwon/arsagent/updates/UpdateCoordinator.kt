package kr.ac.suwon.arsagent.updates

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** SDK callbacks and UI entry points run on the Android main thread. */
interface UpdateGateway {
    val enabled: Boolean
    val signedIn: Boolean
    fun check(done: (Result<String?>) -> Unit)
    fun signIn(done: (Result<Unit>) -> Unit)
    fun install(done: (Result<Unit>) -> Unit)
}

enum class UpdateStage { Hidden, Consent, Checking, SigningIn, Available, Installing, Failed }
data class UpdateUi(val stage: UpdateStage = UpdateStage.Hidden, val version: String = "")

/** Independent of call state: its caller grants eligibility only while idle and foreground. */
class UpdateCoordinator(private val gateway: UpdateGateway, private val now: () -> Long = System::currentTimeMillis) {
    private val mutable = MutableStateFlow(UpdateUi())
    val ui = mutable.asStateFlow()
    private var eligible = false
    private var closed = false
    private var deferred = false
    private var busy = false
    private var busyStage = UpdateStage.Hidden
    private var generation = 0L
    private var lastCheck: Long? = null

    fun setEligible(value: Boolean) {
        if (closed || value == eligible) return
        eligible = value
        generation++
        if (!value) { mutable.value = UpdateUi(); return }
        if (busy) { mutable.value = UpdateUi(busyStage); return }
        if (!gateway.enabled || deferred) return
        if (!gateway.signedIn) mutable.value = UpdateUi(UpdateStage.Consent)
        else if (lastCheck == null || now() - lastCheck!! >= 600_000) check()
    }

    private fun check() {
        val ticket = ++generation
        mutable.value = UpdateUi(UpdateStage.Checking)
        gateway.check { result ->
            if (closed || !eligible || ticket != generation) return@check
            lastCheck = now()
            mutable.value = result.fold(
                onSuccess = { if (it == null) UpdateUi() else UpdateUi(UpdateStage.Available, it) },
                onFailure = { UpdateUi(UpdateStage.Failed) },
            )
        }
    }

    fun signIn() {
        if (closed || !eligible || busy || ui.value.stage != UpdateStage.Consent) return
        busy = true
        busyStage = UpdateStage.SigningIn
        mutable.value = UpdateUi(UpdateStage.SigningIn)
        gateway.signIn { result ->
            busy = false
            if (closed || !eligible) return@signIn
            if (result.isSuccess && gateway.signedIn) check()
            else mutable.value = UpdateUi(UpdateStage.Failed)
        }
    }

    fun install() {
        if (closed || !eligible || busy || ui.value.stage != UpdateStage.Available) return
        busy = true
        busyStage = UpdateStage.Installing
        deferred = true
        mutable.value = UpdateUi(UpdateStage.Installing)
        gateway.install { result ->
            busy = false
            if (closed || !eligible) return@install
            mutable.value = if (result.isSuccess) UpdateUi() else UpdateUi(UpdateStage.Failed)
        }
    }

    fun dismiss() {
        if (busy) return
        deferred = true
        generation++
        mutable.value = UpdateUi()
    }

    fun close() { closed = true; generation++; mutable.value = UpdateUi() }
}
