package kr.ac.suwon.arsagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kr.ac.suwon.arsagent.model.*
import kr.ac.suwon.arsagent.ui.ArsApp
import kr.ac.suwon.arsagent.ui.ArsTheme

/** Debug launcher only: visual effects run, but no ViewModel, call-state timers, recording or real calls. */
class ScreenGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ScreenGallery(onExit = ::finish) }
    }
}

private data class ScreenSample(val id: String, val title: String, val state: AppState)

private val screenSamples: List<ScreenSample> = buildList {
    fun sample(id: String, title: String, state: AppState) { add(ScreenSample(id, title, state)) }
    val mobile = "01012345678"
    val ars = "07000000000"
    sample("A-keypad", "A · 키패드 (빈 번호)", AppState())
    sample("A-keypad-010", "A · 키패드 (010)", AppState(number = mobile))
    sample("A-keypad-070", "A · 키패드 (070)", AppState(number = ars))
    sample("A-keypad-131", "A · 키패드 (131)", AppState(number = "131"))
    sample("A-calls", "A · 통화 목록", AppState(tab = PhoneTab.Calls))
    sample("A-contacts", "A · 연락처 목록", AppState(tab = PhoneTab.Contacts))
    listOf("010" to mobile, "070" to ars).forEach { (prefix, number) ->
        sample("A-contact-$prefix", "A · 연락처 상세 ($prefix)",
            AppState(screen = Screen.Detail, tab = PhoneTab.Contacts, number = number))
        sample("A-call-detail-$prefix", "A · 통화 상세 ($prefix)",
            AppState(screen = Screen.Detail, tab = PhoneTab.Calls, number = number))
        sample("A-call-$prefix", "A · 일반 통화 ($prefix)",
            AppState(screen = Screen.Call, number = number, phase = Phase.Listening, elapsedSeconds = 18))
        sample("A-ended-$prefix", "A · 일반 통화 종료 ($prefix)",
            AppState(screen = Screen.Ended, number = number, elapsedSeconds = 37))
    }
    val agent = AppState(screen = Screen.Call, number = "131", agent = true,
        goal = "수원 오늘 날씨 알려줘", elapsedSeconds = 18)
    sample("B01", "B01 · 목적 듣기", agent.copy(screen = Screen.Purpose, elapsedSeconds = 0))
    sample("B02", "B02 · 기관 연결 중", agent.copy(phase = Phase.Dialing, elapsedSeconds = 0))
    sample("B03", "B03 · ARS 안내", agent.copy(phase = Phase.Listening))
    sample("B04", "B04 · 이동 경로", agent.copy(phase = Phase.Selecting))
    sample("B04-audio", "B04 · 이동 경로 (ARS 소리 켬)", agent.copy(phase = Phase.Selecting, audioOn = true))
    sample("B05", "B05 · 기관 안내", agent.copy(phase = Phase.ListeningResult))
    sample("B06", "B06 · 안내 결과", agent.copy(phase = Phase.Done))
    sample("B07", "B07 · 오디오 선택", agent.copy(phase = Phase.Selecting, audioMenu = true))
    sample("C01", "C01 · 후보 선택", agent.copy(phase = Phase.Choice))
    sample("C02", "C02 · 추가 답변", agent.copy(phase = Phase.Answer))
    sample("C03", "C03 · 민감정보 입력", agent.copy(phase = Phase.Secure))
    sample("C04", "C04 · 수동 키패드", agent.copy(phase = Phase.Selecting, keypadOpen = true, manualDigits = "123"))
    sample("D01", "D01 · 상담원 연결 대기", agent.copy(phase = Phase.WaitingAgent))
}

@Composable
fun ScreenGallery(initiallyShowPicker: Boolean = true, onExit: () -> Unit = {}) {
    var selectedId by rememberSaveable { mutableStateOf("B04") }
    var showPicker by rememberSaveable { mutableStateOf(initiallyShowPicker) }
    val sample = screenSamples.first { it.id == selectedId }
    ArsTheme {
        Box(Modifier.fillMaxSize()) {
            key(selectedId) {
                ArsApp(sample.state) { action -> if (action == Action.Back) showPicker = true }
            }
            // Overlay the existing demo label, preserving the preview's full screen dimensions.
            Row(Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(horizontal = 12.dp, vertical = 4.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("${sample.id} · 고정 미리보기", style = MaterialTheme.typography.labelMedium)
                TextButton({ showPicker = true }) { Text("화면 선택") }
            }
        }
        BackHandler(enabled = !showPicker) { showPicker = true }
        if (showPicker) {
            var selectedGroup by rememberSaveable(selectedId) { mutableStateOf(sample.id.take(1)) }
            AlertDialog(onDismissRequest = { showPicker = false },
                title = { Text("화면 미리보기") },
                text = {
                    Column {
                        Text("화면 상태와 버튼은 고정이고, 점 같은 시각 효과만 재생돼요.",
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("A", "B", "C", "D").forEach { group ->
                                val selected = selectedGroup == group
                                Tab(selected = selected, onClick = { selectedGroup = group },
                                    modifier = Modifier.weight(1f).background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(12.dp)),
                                    text = { Text(group) })
                            }
                        }
                        Text(when (selectedGroup) {
                            "A" -> "일반 전화"
                            "B" -> "Agent 진행"
                            "C" -> "추가 입력"
                            else -> "상담원 연결"
                        }, modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                        key(selectedGroup) {
                            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                                screenSamples.filter { it.id.startsWith(selectedGroup) }.forEach { item ->
                                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                        .clickable(role = Role.Button) { selectedId = item.id; showPicker = false }
                                        .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        if (selectedId == item.id) Text("✓", color = MaterialTheme.colorScheme.primary)
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton({ showPicker = false }) { Text("닫기") } },
                dismissButton = { TextButton(onExit) { Text("미리보기 나가기") } },
            )
        }
    }
}
