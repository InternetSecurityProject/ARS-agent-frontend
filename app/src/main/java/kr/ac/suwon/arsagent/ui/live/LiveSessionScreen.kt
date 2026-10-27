package kr.ac.suwon.arsagent.ui.live

import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kr.ac.suwon.arsagent.data.api.SessionSnapshot
import kr.ac.suwon.arsagent.livecall.*
import kr.ac.suwon.arsagent.model.AudioRoute
import kr.ac.suwon.arsagent.servertest.*
import kr.ac.suwon.arsagent.servertest.voice.ServerVoiceUi
import kr.ac.suwon.arsagent.ui.*

@Composable
internal fun LiveSessionScreen(entry: LiveEntry, server: ServerTestState, owner: LiveCallOwner,
    voice: ServerVoiceUi?, audio: LiveAudioUi?, onReturn: () -> Unit) {
    var keypad by remember { mutableStateOf(false) }
    var keys by remember { mutableStateOf("") }
    var audioMenu by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val pending = server.snapshot.pendingRequest
    LaunchedEffect(pending, server.active) { if (pending != null || !server.active) { keypad = false; keys = "" } }
    val canKey = server.canSend && pending == null && server.snapshot.status !in setOf("dialing", "connected_to_agent")
    val canReply = pending != null && owner.canRespond(pending.requestId)
    val snapshot = if (server.hasSession) server.snapshot else SessionSnapshot("dialing",
        if (server.creationUncertain) "발신 처리 여부를 확인할 수 없어요." else "기관에 전화를 걸고 있어요.", goal = entry.goal)
    val controls = ServerCallControls(
        elapsedSeconds = entry.elapsedSeconds,
        endEnabled = server.active && !server.starting && !server.busy,
        arsEnabled = voice?.arsEnabled == true,
        arsAvailable = voice != null && server.active && server.snapshot.status != "connected_to_agent",
        keypadAvailable = canKey, keypadOpen = keypad, manualKeys = keys,
        audioMenu = audioMenu, audioRoute = audio?.route, availableRoutes = audio?.available.orEmpty(),
        onToggleArs = { voice?.toggleArs?.invoke() },
        onKeypad = { if (keypad || canKey) { keypad = !keypad; keys = ""; audioMenu = false } },
        onAudioMenu = { audioMenu = !audioMenu },
        onRoute = { audio?.select?.invoke(it); audioMenu = false },
        onKey = { digit ->
            // Read authoritative state again; an incoming request can precede recomposition.
            val live = owner.server.state.value
            if (live.canSend && live.snapshot.pendingRequest == null && live.snapshot.status !in setOf("dialing", "connected_to_agent")) {
                keys = (keys + digit).takeLast(16)
                owner.dispatch(ServerTestCommand.Keys(digit.toString()))
            }
        }, extras = {
            LiveSessionExtras(server, voice, audio, keypad || pending?.kind == "secure_input", owner)
        })
    BServerCallScreen(entry.number, snapshot, server.result,
        onEnd = { voice?.cancelInput?.invoke(); owner.dispatch(ServerTestCommand.Cancel) },
        onBack = { if (!server.active && server.hasSession) onReturn() else confirmExit = true },
        choiceInput = CandidateChoiceInput(canReply, { owner.dispatch(ServerTestCommand.Respond(it.requestId, it.key, true)) }),
        secureInput = SecureInputInput(canReply, { owner.dispatch(ServerTestCommand.Respond(it.requestId, it.digits)) }),
        answerInput = ServerAnswerInput(canReply, { owner.dispatch(ServerTestCommand.Respond(it.requestId, it.text)) },
            { voice?.answer?.invoke() }, canUseMic = voice?.status?.enabled == true),
        controls = controls)
    if (confirmExit) ArsTheme {
        AlertDialog(onDismissRequest = { confirmExit = false }, title = { Text("통화 상태를 확인해 주세요") },
            text = { Text(if (server.creationUncertain) "발신 처리 여부가 불확실해요. 다시 발신하지 말고 서버 담당자에게 확인해 주세요."
                else "이 화면을 닫아도 서버 통화가 자동으로 끝나지 않아요. 종료를 요청하고 완료를 확인해 주세요.") },
            confirmButton = { TextButton({ confirmExit = false; owner.dispatch(ServerTestCommand.Cancel) },
                enabled = server.active && !server.busy && !server.starting) { Text("종료 요청") } },
            dismissButton = { TextButton({ confirmExit = false }) { Text("계속 보기") } })
    }
}

@Composable
private fun LiveSessionExtras(server: ServerTestState, voice: ServerVoiceUi?, audio: LiveAudioUi?,
    compactInput: Boolean, owner: LiveCallOwner) {
    server.error?.let { Text(it, color = ArsColors.Red) }
    if (server.busy) Text("요청을 전달하고 있어요.", color = ArsColors.Secondary)
    if (server.hasSession && server.active && (!server.streamConnected || server.actionUncertain)) {
        Text("서버 상태를 다시 확인해 주세요.", color = ArsColors.Secondary)
        TextButton({ owner.dispatch(ServerTestCommand.Reconnect) }, enabled = !server.busy) { Text("상태 다시 연결") }
    }
    if (server.hasSession && (!server.active || server.error != null))
        TextButton({ owner.dispatch(ServerTestCommand.FetchResult) }, enabled = !server.resultLoading) { Text("결과 조회") }
    server.resultNote?.let { Text(it, color = ArsColors.Secondary) }
    voice?.status?.error?.let { Text(it, color = ArsColors.Red) }
    voice?.arsError?.let { Text(it, color = ArsColors.Red) }
    audio?.error?.let { Text(it, color = ArsColors.Red) }
    if (server.snapshot.status == "connected_to_agent") {
        Text("Agent의 음성 답변과 ARS 재생을 멈췄어요. 상담원과의 음성 통화는 지원하지 않아요.", color = ArsColors.Secondary)
        return
    }
    if (!compactInput && voice != null && server.active) {
        Text(when {
            voice.status.listening -> "듣고 있어요. Agent에게 말씀해 주세요."
            voice.status.speaking -> "Agent가 안내하고 있어요."
            voice.status.enabled -> "Agent 음성 대화 켜짐"
            else -> "Agent 음성 대화 꺼짐"
        }, color = ArsColors.Blue)
        TextButton(voice.toggleVoice) { Text(if (voice.status.enabled) "음성 대화 끄기" else "음성 대화 켜기") }
        if (voice.status.listening || voice.status.speaking)
            TextButton(voice.cancelInput) { Text("음성 입력·안내 멈추기") }
        val request = server.snapshot.pendingRequest
        if (voice.status.enabled && request?.kind == "clarification" && request.reason != "critical_action" && owner.canRespond(request.requestId))
            TextButton(voice.answer) { Text("말로 답하기") }
    }
}
