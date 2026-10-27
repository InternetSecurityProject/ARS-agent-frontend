package kr.ac.suwon.arsagent.servertest

import kr.ac.suwon.arsagent.data.api.*

const val SERVER_TEST_URL = "https://ars.mcsmtp.org/v1"

enum class ServerTestMode(val backend: String) {
    Sim("sim"), RealCall("clawops");
    companion object { fun fromBackend(backend: String?): ServerTestMode? = entries.firstOrNull { it.backend == backend } }
}

sealed class ServerTestCommand {
    final override fun toString() = "ServerTestCommand([redacted])"
    data object CheckConnection : ServerTestCommand()
    data class Start(val key: String, val phone: String, val goal: String, val mode: ServerTestMode = ServerTestMode.Sim) : ServerTestCommand()
    data class Respond(val requestId: String, val value: String, val selection: Boolean = false) : ServerTestCommand()
    data class Keys(val keys: String) : ServerTestCommand()
    data class ReviseGoal(val text: String) : ServerTestCommand()
    data object Cancel : ServerTestCommand()
    data object Reconnect : ServerTestCommand()
    data object FetchResult : ServerTestCommand()
    data object Reset : ServerTestCommand()
}

data class ServerTestState(
    val health: HealthResponse? = null,
    val checking: Boolean = false,
    val starting: Boolean = false,
    val busy: Boolean = false,
    val hasSession: Boolean = false,
    val streamConnected: Boolean = false,
    val creationUncertain: Boolean = false,
    val actionUncertain: Boolean = false,
    val resultLoading: Boolean = false,
    val snapshot: SessionSnapshot = SessionSnapshot("idle", "연결을 확인해 주세요."),
    val result: CompletedResult? = null,
    val resultNote: String? = null,
    val error: String? = null,
) {
    val active: Boolean get() = hasSession && snapshot.status != "ended"
    val canSend: Boolean get() = active && streamConnected && !busy && !actionUncertain
}
