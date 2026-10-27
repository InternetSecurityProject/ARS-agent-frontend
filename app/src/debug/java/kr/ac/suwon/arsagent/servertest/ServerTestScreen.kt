package kr.ac.suwon.arsagent.servertest

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import kr.ac.suwon.arsagent.data.api.UserRequest
import kr.ac.suwon.arsagent.model.PhoneNumber
import kr.ac.suwon.arsagent.model.formatDuration
import kr.ac.suwon.arsagent.model.formatPhone
import kr.ac.suwon.arsagent.ui.*
import kr.ac.suwon.arsagent.servertest.voice.ServerVoiceUi
import kr.ac.suwon.arsagent.servertest.voice.ServerVoicePanel

@Composable
fun ServerTestScreen(state: ServerTestState, dispatch: (ServerTestCommand) -> Unit, onExit: () -> Unit,
    voice: ServerVoiceUi? = null) {
    var confirmExit by remember { mutableStateOf(false) }
    val exit = {
        if (state.active || state.starting || state.creationUncertain) confirmExit = true else onExit()
    }
    BackHandler { exit() }
    ArsTheme {
        Column(Modifier.fillMaxSize().background(ArsColors.Neutral).safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("서버 테스트", Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
                TextButton(exit) { Text("닫기") }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(when (ServerTestMode.fromBackend(state.health?.backend)) {
                    ServerTestMode.Sim -> "시뮬레이터 · 실제 전화가 걸리지 않아요"
                    ServerTestMode.RealCall -> "실통화 서버 · 발신 전 확인이 필요해요"
                    null -> "연결 확인 후 통화 모드를 확인해 주세요"
                }, color = ArsColors.Secondary)
                Text(SERVER_TEST_URL, style = MaterialTheme.typography.bodyMedium)
                state.error?.let { message ->
                    Section("확인이 필요해요") { Text(message, color = ArsColors.Red) }
                }
                voice?.let { ServerVoicePanel(state, it) }
                if (!state.hasSession) SetupForm(state, dispatch, voice)
                else SessionContent(state, dispatch)
                Spacer(Modifier.height(12.dp))
            }
            if (state.hasSession) {
                Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ dispatch(ServerTestCommand.Reconnect) }, Modifier.weight(1f),
                            enabled = state.active && !state.busy && !state.starting) { Text("상태 다시 연결") }
                        OutlinedButton({ dispatch(ServerTestCommand.FetchResult) }, Modifier.weight(1f),
                            enabled = !state.resultLoading) { Text(if (state.resultLoading) "결과 조회 중" else "결과 조회") }
                    }
                    if (state.active) Button({ dispatch(ServerTestCommand.Cancel) },
                        Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !state.busy && !state.starting,
                        colors = ButtonDefaults.buttonColors(containerColor = ArsColors.Red)) {
                        Text(if (state.busy) "요청 처리 중" else "테스트 종료")
                    } else Button({ dispatch(ServerTestCommand.Reset) }, Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        enabled = !state.busy && !state.resultLoading) { Text("새 테스트 준비") }
                }
            }
        }
        if (confirmExit) {
            val uncertain = state.creationUncertain
            AlertDialog(onDismissRequest = { confirmExit = false },
                title = { Text(if (uncertain) "종료 여부를 확인할 수 없어요" else "테스트를 종료할까요?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(when {
                            uncertain -> "화면을 닫아도 서버 세션은 남아 있을 수 있어요. 서버 담당자에게 상태를 확인해 주세요."
                            state.starting -> "세션 생성 응답을 기다리고 있어요. 아직 화면을 닫지 말아 주세요."
                            else -> "먼저 서버에 종료를 요청해요. 종료 확인 후 다시 닫기를 누르면 나갈 수 있어요."
                        })
                        if (state.active && state.error != null && !state.starting && !state.busy) {
                            Text("서버 세션은 종료되지 않았을 수 있어요.", color = ArsColors.Red)
                            TextButton({ confirmExit = false; onExit() }) { Text("화면만 닫기") }
                        }
                    }
                },
                confirmButton = {
                    TextButton({
                        confirmExit = false
                        if (uncertain) onExit() else if (state.active) dispatch(ServerTestCommand.Cancel)
                    }, enabled = uncertain || (state.active && !state.busy && !state.starting)) {
                        Text(if (uncertain) "연결만 닫기" else "서버에서 종료")
                    }
                },
                dismissButton = { TextButton({ confirmExit = false }) { Text("계속 보기") } })
        }
    }
}

@Composable
private fun SetupForm(state: ServerTestState, dispatch: (ServerTestCommand) -> Unit, voice: ServerVoiceUi?) {
    // Deliberately NOT rememberSaveable: never place credentials in Android saved instance state.
    var key by remember { mutableStateOf("") }
    val mode = ServerTestMode.fromBackend(state.health?.backend)
    var phone by remember(state.health?.backend) {
        mutableStateOf(if (mode == ServerTestMode.Sim) "07000000000" else "")
    }
    var goal by remember { mutableStateOf("") }
    var examplesOpen by remember { mutableStateOf(false) }
    var pendingCall by remember { mutableStateOf<PendingRealCall?>(null) }
    val editable = !state.starting && !state.checking && !state.creationUncertain
    val ready = editable && mode != null && state.health?.activeSession == null &&
        key.isNotBlank() && PhoneNumber(phone).canCall && goal.isNotBlank()
    val pendingValid = ready && mode == ServerTestMode.RealCall && pendingCall?.let {
        it.phone == PhoneNumber(phone).digits && it.goal == goal.trim()
    } == true
    LaunchedEffect(pendingValid) { if (!pendingValid) pendingCall = null }
    Section("서버 연결") {
        state.health?.let {
            Text("연결 확인 완료 · ${it.backend}")
            Text("키 인증은 테스트 시작 시 확인해요.", style = MaterialTheme.typography.bodyMedium, color = ArsColors.Secondary)
        }
        OutlinedButton({ dispatch(ServerTestCommand.CheckConnection) }, enabled = editable) {
            Text(if (state.checking) "연결 확인 중" else "연결 확인")
        }
    }
    Section("테스트 준비") {
        OutlinedTextField(key, { key = it.take(1024) }, Modifier.fillMaxWidth().testTag("server-key"),
            label = { Text("API 키") }, singleLine = true, enabled = editable,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password))
        Text("키는 저장하지 않아요. 테스트 시작 후 입력칸에서도 지워요.",
            style = MaterialTheme.typography.bodyMedium, color = ArsColors.Secondary)
        OutlinedTextField(phone, { phone = it.filter { digit -> digit in '0'..'9' }.take(13) },
            Modifier.fillMaxWidth().testTag("server-phone"), label = { Text("테스트 전화번호") }, singleLine = true,
            enabled = editable, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        OutlinedTextField(goal, { goal = it.take(500) }, Modifier.fillMaxWidth().testTag("server-goal"),
            label = { Text("원하는 업무") }, enabled = editable, minLines = 2)
        voice?.let {
            OutlinedButton({ it.goal { recognized -> if (editable) goal = recognized.take(500) } },
                Modifier.fillMaxWidth(), enabled = editable && it.status.enabled) { Text("목적 말하기") }
        }
        if (!state.health?.mockGoals.isNullOrEmpty()) {
            Box {
                TextButton({ examplesOpen = true }, enabled = editable) { Text("서버 예시 목적 선택") }
                DropdownMenu(examplesOpen, { examplesOpen = false }, Modifier.heightIn(max = 300.dp)) {
                    state.health?.mockGoals?.take(50)?.forEach { example ->
                        DropdownMenuItem(text = { Text(example) }, onClick = { goal = example; examplesOpen = false })
                    }
                }
            }
        }
        Button({
            if (ready) {
                if (mode == ServerTestMode.RealCall) {
                    pendingCall = PendingRealCall(PhoneNumber(phone).digits, goal.trim())
                } else {
                    val command = ServerTestCommand.Start(key, phone, goal)
                    key = ""
                    dispatch(command)
                }
            }
        }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = ready) {
            Text(when {
                state.starting -> "서버 응답 기다리는 중"
                mode == ServerTestMode.RealCall -> "실통화 준비"
                else -> "테스트 시작"
            })
        }
        Text(if (mode == ServerTestMode.RealCall)
            if (voice == null) "서버의 070 전화로 발신해요. 현재 앱은 통화 음성 재생·마이크 전달을 지원하지 않아요. 진행 상황과 입력 요청을 확인할 수 있어요."
            else "서버의 070 전화로 발신해요. 음성은 Agent와의 대화에만 사용하고, ARS는 듣기만 지원해요. 상담원 통화는 지원하지 않아요."
            else "시작 직전에 서버 상태를 다시 확인해요. 시뮬레이터는 글과 선택지로 테스트해요.",
            style = MaterialTheme.typography.bodyMedium, color = ArsColors.Secondary)
    }
    if (pendingValid) {
        val target = requireNotNull(pendingCall)
        AlertDialog(
            onDismissRequest = { pendingCall = null },
            title = { Text("실제로 전화를 걸까요?") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("확인하면 이 번호로 실제 전화가 걸립니다.")
                    Text(formatPhone(target.phone), style = MaterialTheme.typography.titleLarge)
                    Text(target.goal)
                    Text("서버의 070 전화로 발신하며, 실제 통화 시간과 요금이 발생할 수 있어요.")
                    Text(if (voice == null) "앱의 음성 재생·마이크 전달은 아직 지원하지 않아요."
                        else "ARS 듣기와 Agent 음성 대화를 지원해요. 상담원과의 음성 통화는 지원하지 않아요.")
                }
            },
            confirmButton = {
                TextButton({
                    // Consume current consent before dispatch: never reuse a captured/stale confirmation.
                    val confirmed = pendingCall
                    if (pendingValid && confirmed != null && key.isNotBlank()) {
                        pendingCall = null
                        val command = ServerTestCommand.Start(key, confirmed.phone, confirmed.goal, ServerTestMode.RealCall)
                        key = ""
                        dispatch(command)
                    }
                }) { Text("실제 발신") }
            },
            dismissButton = { TextButton({ pendingCall = null }) { Text("취소") } }
        )
    }
}

private data class PendingRealCall(val phone: String, val goal: String)

@Composable
private fun SessionContent(state: ServerTestState, dispatch: (ServerTestCommand) -> Unit) {
    val snapshot = state.snapshot
    var showHistory by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    Section(if (state.active) "서버 진행 상황" else "테스트 종료") {
        Text(snapshot.message, style = MaterialTheme.typography.titleLarge)
        Text(snapshot.status, color = ArsColors.Secondary, style = MaterialTheme.typography.bodyMedium)
        if (state.active) Text(if (state.streamConnected) "실시간 상태 수신 중" else "연결 중 또는 수신 끊김 · 다시 연결할 수 있어요.",
            color = ArsColors.Secondary, style = MaterialTheme.typography.bodyMedium)
        if (snapshot.status in setOf("agent_done", "connected_to_agent")) {
            Text("Agent 업무 상태예요. 서버 세션은 아직 종료되지 않았어요.", style = MaterialTheme.typography.bodyMedium)
        }
    }
    snapshot.pendingRequest?.let { request ->
        key(request.requestId, request.kind) { RequestForm(request, state.canSend, dispatch) }
    }
    snapshot.summary?.let { summary ->
        Section("Agent 안내 결과") {
            Text(summary.summary)
            summary.keyInfo.forEach { Text("${it.label} · ${it.value}") }
            summary.handoffScript?.let { Text(it) }
        }
    }
    state.result?.let { result ->
        Section("최종 결과") {
            result.summary?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
            result.keyInfo.forEach { Text("${it.label} · ${it.value}") }
            result.handoffScript?.let { Text(it) }
            Text("서버 기준 소요 시간 · ${formatDuration(result.durationSec)}")
            Text("${result.outcome} · ${result.endReason}", style = MaterialTheme.typography.bodyMedium, color = ArsColors.Secondary)
        }
    }
    state.resultNote?.let { Text(it) }
    TextButton({ showHistory = !showHistory }) { Text(if (showHistory) "진행 기록 접기" else "진행 기록 보기") }
    if (showHistory) {
        Section("이동 경로") {
            if (snapshot.path.isEmpty()) Text("아직 선택한 메뉴가 없어요.")
            snapshot.path.forEach { Text(it.text) }
        }
        Section("최근 ARS 안내") {
            if (snapshot.captions.isEmpty()) Text("아직 받은 안내가 없어요.")
            snapshot.captions.takeLast(10).forEach { Text(it.text) }
        }
    }
    if (state.active && snapshot.pendingRequest == null) {
        TextButton({ showManual = !showManual }) { Text(if (showManual) "수동 조작 접기" else "수동 조작") }
        if (showManual) ManualActions(state, dispatch)
    }
}

@Composable
private fun RequestForm(request: UserRequest, canSend: Boolean, dispatch: (ServerTestCommand) -> Unit) {
    var value by remember { mutableStateOf("") }
    val expiry = remember(request.expiresAt) { runCatching { Instant.parse(request.expiresAt) }.getOrNull() }
    var expired by remember(request.expiresAt) { mutableStateOf(expiry == null || !expiry.isAfter(Instant.now())) }
    LaunchedEffect(request.expiresAt) {
        if (expiry != null && !expired) {
            delay(Duration.between(Instant.now(), expiry).toMillis().coerceAtLeast(0))
            expired = true
            value = ""
        }
    }
    val enabled = canSend && !expired
    val secure = request.kind == "secure_input"
    Section(if (secure) request.label ?: "번호 직접 입력" else "답변이 필요해요") {
        Text(request.message)
        if (expired) Text("요청 기한이 지났어요. 상태를 다시 연결해 주세요.", color = ArsColors.Red)
        when (request.kind) {
            "clarification" -> {
                request.options.forEach { option ->
                    OutlinedButton({ dispatch(ServerTestCommand.Respond(request.requestId, option.key, true)) },
                        Modifier.fillMaxWidth(), enabled = enabled) { Text("${option.key} · ${option.label}") }
                }
                if (request.reason != "critical_action") {
                    OutlinedTextField(value, { value = it.take(500) }, Modifier.fillMaxWidth().testTag("server-input"),
                        label = { Text("답변 입력") }, enabled = enabled)
                    Button({
                        val reply = ServerTestCommand.Respond(request.requestId, value)
                        value = ""; dispatch(reply)
                    }, enabled = enabled && value.isNotBlank()) { Text("답변 보내기") }
                } else Text("중요한 선택은 위의 항목을 직접 골라 주세요.", style = MaterialTheme.typography.bodyMedium)
            }
            "secure_input" -> {
                OutlinedTextField(value, { value = it.filter { digit -> digit in "0123456789*#" }.take(20) },
                    Modifier.fillMaxWidth().testTag("server-input"), label = { Text("요청한 번호") },
                    enabled = enabled, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password))
                Text("입력값은 저장하지 않아요. 종료 기호는 서버가 붙여요.", style = MaterialTheme.typography.bodyMedium)
                Button({
                    val reply = ServerTestCommand.Respond(request.requestId, value)
                    value = ""; dispatch(reply)
                }, enabled = enabled && value.isNotEmpty()) { Text("입력 완료") }
            }
            else -> Text("지원하지 않는 입력 요청이에요. 상태를 확인하거나 테스트를 종료해 주세요.", color = ArsColors.Red)
        }
    }
}

@Composable
private fun ManualActions(state: ServerTestState, dispatch: (ServerTestCommand) -> Unit) {
    var keys by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    Section("직접 키 전송") {
        OutlinedTextField(keys, { keys = it.filter { digit -> digit in "0123456789*#" }.take(4) },
            Modifier.fillMaxWidth().testTag("server-manual-keys"), label = { Text("숫자·*·# (1~4자)") },
            singleLine = true, enabled = state.canSend)
        Button({ val action = ServerTestCommand.Keys(keys); keys = ""; dispatch(action) },
            enabled = state.canSend && keys.isNotEmpty() && state.snapshot.status != "dialing") { Text("키 보내기") }
    }
    if (state.snapshot.path.isEmpty() && state.snapshot.status != "connected_to_agent") {
        Section("첫 메뉴 선택 전 목적 수정") {
            OutlinedTextField(goal, { goal = it.take(500) }, Modifier.fillMaxWidth().testTag("server-revise"),
                label = { Text("새 목적") }, enabled = state.canSend)
            Button({ val action = ServerTestCommand.ReviseGoal(goal); goal = ""; dispatch(action) },
                enabled = state.canSend && goal.isNotBlank()) { Text("목적 수정 보내기") }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
