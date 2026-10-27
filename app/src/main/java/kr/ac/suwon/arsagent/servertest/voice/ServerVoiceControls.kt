package kr.ac.suwon.arsagent.servertest.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kr.ac.suwon.arsagent.data.api.CreatedSession
import kr.ac.suwon.arsagent.servertest.*
import kr.ac.suwon.arsagent.ui.ArsColors

data class ServerVoiceUi(val status: VoiceStatus, val arsEnabled: Boolean, val arsPlaying: Boolean,
    val arsError: String?, val toggleVoice: () -> Unit,
    val goal: ((String) -> Unit) -> Unit, val answer: () -> Unit, val cancelInput: () -> Unit, val toggleArs: () -> Unit)

@Composable
fun ServerVoicePanel(state: ServerTestState, voice: ServerVoiceUi) {
    var tick by remember { mutableIntStateOf(0) }
    val request = state.snapshot.pendingRequest
    LaunchedEffect(request?.requestId, request?.expiresAt) {
        val duration = request?.expiresAt?.let { runCatching { Duration.between(Instant.now(), Instant.parse(it)).toMillis() }.getOrDefault(0) }
        if (duration != null && duration > 0) { delay(duration); tick++ }
    }
    val canAnswer = remember(request, state.canSend, state.snapshot.status, tick) {
        request?.kind == "clarification" && request.reason != "critical_action" && state.canSend &&
            state.snapshot.status == "awaiting_user" && runCatching { Instant.parse(request.expiresAt).isAfter(Instant.now()) }.getOrDefault(false)
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Agent 음성 대화", style = MaterialTheme.typography.titleLarge)
            Text(when {
                voice.status.listening -> "듣고 있어요. Agent에게 말씀해 주세요."
                voice.status.speaking -> "Agent가 안내하고 있어요."
                voice.status.enabled -> "질문을 읽은 뒤 답변을 들어요. 개인정보·중요 선택은 화면에서 입력해 주세요."
                else -> "켜면 Agent의 질문과 진행 안내를 음성으로 들을 수 있어요."
            }, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(voice.toggleVoice, Modifier.fillMaxWidth()) {
                Text(if (voice.status.enabled) "음성 대화 끄기" else "음성 대화 켜기")
            }
            if (canAnswer) OutlinedButton(voice.answer, Modifier.fillMaxWidth(), enabled = voice.status.enabled) { Text("말로 답하기") }
            if (voice.status.listening || voice.status.speaking)
                TextButton(voice.cancelInput) { Text("음성 입력·안내 멈추기") }
            voice.status.error?.let { Text(it, color = ArsColors.Red) }
            OutlinedButton(voice.toggleArs, Modifier.fillMaxWidth(),
                enabled = state.active && state.snapshot.status != "connected_to_agent") {
                Text(if (voice.arsEnabled) "ARS 소리 끄기" else "ARS 소리 켜기")
            }
            if (voice.arsEnabled) Text(if (voice.status.listening || voice.status.speaking)
                "Agent와 대화하는 동안 ARS 소리를 잠시 멈춰요." else if (voice.arsPlaying) "ARS 소리 재생 중" else "ARS 소리 연결 중",
                style = MaterialTheme.typography.bodyMedium)
            voice.arsError?.let { Text(it, color = ArsColors.Red) }
            if (state.snapshot.status == "connected_to_agent") Text("상담원과의 음성 통화는 지원하지 않아요.")
        }
    }
}

@Composable
fun ServerVoiceBinding(state: ServerTestState, dispatch: (ServerTestCommand) -> Unit,
    sessions: VoiceSessionRelay, latestState: () -> ServerTestState,
    content: @Composable (ServerVoiceUi) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val currentDispatch by rememberUpdatedState(dispatch)
    val currentState by rememberUpdatedState(latestState)
    val player = remember { AndroidArsPlayer(scope) }
    val voice = remember {
        lateinit var owner: VoiceCoordinator
        val device = AndroidSpeechDevice(context) { owner.deviceError(it) }
        owner = VoiceCoordinator(device, { currentDispatch(it) }, suppressArs = player::mute,
            latestState = { currentState() })
        owner
    }
    val status by voice.state.collectAsState()
    val audio by player.state.collectAsState()
    var session by remember { mutableStateOf<CreatedSession?>(null) }
    var consent by remember { mutableStateOf(false) }
    var disclosed by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && visible) voice.enable(true)
        else voice.deviceError("음성 입력을 사용하려면 마이크 권한을 허용해 주세요. 글자 입력은 계속 사용할 수 있어요.")
    }
    val enable = {
        if (visible) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voice.enable(true)
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    DisposableEffect(lifecycle, sessions, voice, player) {
        sessions.observe { value ->
            if (session !== value) { player.stop(); voice.sessionChanged(value?.sessionId); session = value }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                visible = false; consent = false; voice.foreground(false); player.stop()
            } else if (event == Lifecycle.Event.ON_START) { visible = true; voice.foreground(true) }
        }
        lifecycle.addObserver(observer)
        onDispose { sessions.observe(null); lifecycle.removeObserver(observer); player.close(); voice.close() }
    }
    SideEffect {
        voice.update(state)
        if (!state.active || state.snapshot.status == "connected_to_agent") player.stop()
    }
    // Expiry during a long TTS/STT turn must stop capture, not just reject the final result.
    LaunchedEffect(state.snapshot.pendingRequest, state.canSend) {
        val request = state.snapshot.pendingRequest
        val remaining = request?.expiresAt?.let { runCatching { Duration.between(Instant.now(), Instant.parse(it)).toMillis() }.getOrDefault(0) }
        if (remaining != null && remaining > 0) { delay(remaining); voice.cancelInput() }
    }
    content(ServerVoiceUi(status, audio.enabled, audio.playing, audio.error,
        toggleVoice = {
            if (status.enabled) voice.enable(false)
            else if (disclosed) enable() else consent = true
        }, goal = voice::listenGoal, answer = voice::listenAnswer, cancelInput = voice::cancelInput,
        toggleArs = {
            if (audio.enabled) player.stop()
            else if (visible && state.active && state.snapshot.status != "connected_to_agent") session?.let(player::start)
        }))
    if (consent) AlertDialog(onDismissRequest = { consent = false },
        title = { Text("Agent와 음성으로 대화할까요?") },
        text = { Text("마이크 음성을 글자로 바꿔 Agent에게 전달해요. 기기의 음성 인식 서비스에 따라 음성이 외부 서버로 전송될 수 있어요. 기관이나 상담원에게 내 목소리를 보내지 않아요. 개인정보와 중요한 선택은 화면에서 입력해 주세요.") },
        confirmButton = { TextButton({ consent = false; disclosed = true; enable() }) { Text("허용하고 켜기") } },
        dismissButton = { TextButton({ consent = false }) { Text("취소") } })
}
