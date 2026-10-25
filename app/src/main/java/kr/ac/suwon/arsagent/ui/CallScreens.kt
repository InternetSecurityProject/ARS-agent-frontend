package kr.ac.suwon.arsagent.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kr.ac.suwon.arsagent.model.*

@Composable
fun CallScreen(state: AppState, dispatch: (Action) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val topSpace = if (maxHeight >= 720.dp) 80.dp else 40.dp
        val secure = state.phase == Phase.Secure || state.phase == Phase.Sending
        val keypad = state.screen == Screen.Call && (secure || state.keypadOpen)
        val compact = maxHeight < 600.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (compact) {
                CompactCallBody(state, dispatch, keypad, Modifier.weight(1f))
            } else {
                Spacer(Modifier.height(topSpace))
                CallHeader(state)
                Spacer(Modifier.height(24.dp))
                when {
                keypad -> CardFreeKeypad(state, dispatch, Modifier.weight(1f))
                !state.agent && state.screen == Screen.Call -> {
                    Spacer(Modifier.weight(1f))
                    NormalControls(state, dispatch)
                    Spacer(Modifier.height(16.dp))
                }
                state.screen == Screen.Ended -> Spacer(Modifier.weight(1f))
                else -> {
                    ProgressCard(Modifier.fillMaxWidth().weight(1f)) { AgentContent(state, dispatch) }
                    Spacer(Modifier.height(20.dp))
                    if (state.screen == Screen.Purpose) Spacer(Modifier.height(88.dp))
                    else AgentControls(state, dispatch)
                    Spacer(Modifier.height(16.dp))
                }
                }
                if (keypad) Spacer(Modifier.height(16.dp))
            }
            if (state.screen == Screen.Ended) {
                PrimaryTextButton("키패드로 돌아가기", { dispatch(Action.Back) })
                Spacer(Modifier.height(30.dp))
            } else {
                Box(Modifier.heightIn(min = 104.dp), contentAlignment = Alignment.TopCenter) {
                    if (state.screen == Screen.Purpose) RoundAction("취소", "close", { dispatch(Action.Back) }, size = 72.dp)
                    else RoundAction("종료", "end_call", { dispatch(Action.End) }, size = 72.dp, iconSize = 42.dp, color = ArsColors.Red)
                }
            }
            Spacer(Modifier.height(22.dp))
        }
        Text(if (compact) "${formatPhone(state.number)} · UI 데모" else "UI 데모 · 실제 통화 아님", color = ArsColors.Secondary, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
        if (state.audioMenu && state.screen == Screen.Call) AudioPopover(state, dispatch)
    }
}

/** Keyboard/short-window layout: scroll the body, never the terminal action. */
@Composable
private fun CompactCallBody(state: AppState, dispatch: (Action) -> Unit, keypad: Boolean, modifier: Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        when {
            keypad -> CardFreeKeypad(state, dispatch, Modifier, scrollable = false)
            state.screen == Screen.Ended -> CallHeader(state)
            !state.agent -> NormalControls(state, dispatch)
            else -> {
                ProgressCard(Modifier.fillMaxWidth(), scrollable = false) { AgentContent(state, dispatch) }
                if (state.screen == Screen.Call) {
                    Spacer(Modifier.height(16.dp))
                    AgentControls(state, dispatch)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CallHeader(state: AppState) {
    Column(Modifier.fillMaxWidth().heightIn(min = 76.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(formatPhone(state.number).ifEmpty { "131" }, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        val subtitle = when {
            state.screen == Screen.Purpose -> contactName(state.number)
            state.screen == Screen.Ended -> "통화 종료 · ${formatDuration(state.elapsedSeconds)}"
            state.phase == Phase.Dialing -> "${contactName(state.number)} · 연결 중"
            !state.agent -> "일반 전화 · ${formatDuration(state.elapsedSeconds)}"
            else -> "${contactName(state.number)} · ${formatDuration(state.elapsedSeconds)}"
        }
        Text(subtitle, color = ArsColors.Secondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AgentContent(state: AppState, dispatch: (Action) -> Unit) {
    if (state.screen == Screen.Purpose) {
        Text("어떤 일을 도와드릴까요?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        VoiceInput(state.goalDraft, "원하는 일을 말하거나 입력해 주세요", { dispatch(Action.GoalChanged(it)) },
            { dispatch(Action.GoalChanged(if (state.scenario == DemoScenario.Handoff) "상담원 연결해줘" else "수원 오늘 날씨 알려줘")) })
        Text("목적이 확인되면 자동으로 전화해요.", color = ArsColors.Secondary)
        return
    }
    when (state.phase) {
        Phase.Dialing -> {
            Text(state.goal, style = MaterialTheme.typography.titleLarge)
            Text("연결되면 Agent가 메뉴를 찾아요.", color = ArsColors.Secondary)
        }
        Phase.Listening -> {
            CardEyebrow("ARS 안내")
            Text("“단기예보는 1번, 현재 날씨는 3번…”", fontSize = 17.sp, lineHeight = 28.sp)
        }
        Phase.Selecting -> {
            CardEyebrow("이동 경로")
            val label = when (state.selectedKey) { "3" -> "현재 날씨"; "5" -> "중기예보"; else -> "단기예보" }
            Text("경기 지역 → 수원 → $label (${state.selectedKey}번)", fontSize = 17.sp, lineHeight = 28.sp)
        }
        Phase.ListeningResult -> {
            CardEyebrow("기관 안내")
            Text("“오후에는 구름이 많고 한때 비가 옵니다. 강수확률은 20~60%입니다.”", fontSize = 17.sp, lineHeight = 28.sp)
        }
        Phase.Done -> {
            Text("오늘 오후, 비가 올 수 있어요", style = MaterialTheme.typography.titleLarge)
            Text("수원 · 강수확률 20~60%", color = ArsColors.Secondary)
        }
        Phase.Choice -> {
            Text("어떤 정보를 원하시나요?", style = MaterialTheme.typography.titleLarge)
            Text("시간 내 선택해 주세요.", color = ArsColors.Secondary, style = MaterialTheme.typography.bodyMedium)
            listOf("1" to "오늘·내일·모레 단기예보", "3" to "현재 날씨", "5" to "중기 10일 예보").forEach { (key, label) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).background(ArsColors.Neutral, RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { dispatch(Action.Select(key)) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${key}번", color = ArsColors.Blue, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Phase.Answer -> {
            Text("어느 지역의 날씨인가요?", style = MaterialTheme.typography.titleLarge)
            VoiceInput(state.answerDraft, "지역을 말씀해 주세요", { dispatch(Action.AnswerChanged(it)) },
                { dispatch(Action.AnswerChanged("수원시 날씨가 궁금해요")) })
            Text("답변을 듣고 있어요. 내용이 확인되면 자동으로 진행해요.", color = ArsColors.Secondary,
                style = MaterialTheme.typography.bodyMedium)
        }
        Phase.WaitingAgent -> {
            Text("상담원 연결을 기다리고 있어요", style = MaterialTheme.typography.titleLarge)
            Text("상담원과 연결되면 Agent는 종료돼요.")
        }
        Phase.Secure, Phase.Sending -> Unit
    }
}

@Composable
private fun CardEyebrow(text: String) { Text(text, style = MaterialTheme.typography.labelMedium, color = ArsColors.Blue) }

@Composable
private fun AgentControls(state: AppState, dispatch: (Action) -> Unit) {
    Row(Modifier.widthIn(max = 304.dp).fillMaxWidth().heightIn(min = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        RoundAction(if (state.audioOn) "ARS 소리 끄기" else "ARS 소리 켜기", "agent_blue",
            { dispatch(Action.ToggleAudio) }, modifier = Modifier.weight(1f), enabled = state.phase != Phase.Dialing)
        RoundAction("키패드", "keypad", { dispatch(Action.OpenKeypad) }, modifier = Modifier.weight(1f), enabled = state.phase != Phase.Dialing)
        RoundAction("오디오", "speaker", { dispatch(Action.ToggleAudioMenu) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun NormalControls(state: AppState, dispatch: (Action) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundAction(if (state.muted) "음소거 해제" else "음소거", "mute", { dispatch(Action.ToggleMute) }, size = 72.dp)
            RoundAction("키패드", "keypad", { dispatch(Action.OpenKeypad) }, size = 72.dp)
            RoundAction("오디오", "speaker", { dispatch(Action.ToggleAudioMenu) }, size = 72.dp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
            RoundAction("통화 추가", "add_call", {}, size = 72.dp, enabled = false)
            RoundAction(if (state.held) "보류 해제" else "보류", "hold", { dispatch(Action.ToggleHold) }, size = 72.dp)
        }
    }
}

@Composable
private fun CardFreeKeypad(state: AppState, dispatch: (Action) -> Unit, modifier: Modifier, scrollable: Boolean = true) {
    val secure = state.phase == Phase.Secure || state.phase == Phase.Sending
    val scrollState = rememberScrollState()
    Column(modifier.fillMaxWidth().then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (secure) "생년월일 6자리 입력" else "번호를 직접 눌러 주세요", style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(if (secure) "6자리 입력 후 자동으로 전송돼요. 전송 후 입력값은 폐기해요." else "누른 번호가 바로 전송돼요. AI는 입력 후 10초간 기다려요.",
            style = MaterialTheme.typography.bodyMedium, color = ArsColors.Secondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
            Text(if (secure) "●".repeat(state.secureEntry.count) + "○".repeat(6 - state.secureEntry.count) else state.manualDigits,
                fontSize = 24.sp, textAlign = TextAlign.Center, maxLines = 1)
            if (secure) TextButton({ dispatch(Action.DeleteSecure) }, Modifier.align(Alignment.CenterEnd), enabled = state.phase == Phase.Secure) { Text("⌫") }
        }
        Spacer(Modifier.height(16.dp))
        PhoneKeypad({ dispatch(Action.Key(it)) }, Modifier.testTag("call-keypad"), compact = true, enabled = state.phase != Phase.Sending)
    }
}

@Composable
private fun BoxScope.AudioPopover(state: AppState, dispatch: (Action) -> Unit) {
    Box(Modifier.fillMaxSize().background(ArsColors.Ink.copy(alpha = .12f)).clickable { dispatch(Action.ToggleAudioMenu) })
    Column(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 240.dp).width(224.dp)
        .shadow(12.dp, RoundedCornerShape(18.dp)).background(Color.White, RoundedCornerShape(18.dp))
        .border(1.dp, ArsColors.Border.copy(alpha = .5f), RoundedCornerShape(18.dp)).padding(8.dp)) {
        listOf(Triple(AudioRoute.Phone, "휴대전화", "call_dark"), Triple(AudioRoute.Speaker, "스피커", "speaker")).forEach { (route, name, icon) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { dispatch(Action.Route(route)) }.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (state.audioRoute == route) "✓" else "", modifier = Modifier.width(20.dp), color = ArsColors.Blue)
                FigmaIcon(icon, tint = ArsColors.Ink)
                Text(name, fontWeight = FontWeight.Medium)
            }
        }
    }
}
