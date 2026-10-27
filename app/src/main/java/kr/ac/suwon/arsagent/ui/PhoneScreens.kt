package kr.ac.suwon.arsagent.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kr.ac.suwon.arsagent.model.*

@Composable
fun PhoneHome(state: AppState, dispatch: (Action) -> Unit, openScenarios: () -> Unit, modeLabel: String? = null) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        TextButton(openScenarios, modifier = Modifier.heightIn(min = 32.dp)) {
            Text(modeLabel ?: "UI 데모 · ${state.scenario.title}", style = MaterialTheme.typography.labelMedium)
        }
        when {
            state.screen == Screen.Detail -> ContactDetail(state, dispatch, Modifier.weight(1f))
            state.tab == PhoneTab.Keypad -> Dialer(state, dispatch, Modifier.weight(1f))
            else -> ContactList(state.tab, dispatch, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        BottomTabs(state.tab) { dispatch(Action.Tab(it)) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Dialer(state: AppState, dispatch: (Action) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 64.dp), contentAlignment = Alignment.Center) {
            Text(formatPhone(state.number), fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                fontSize = 32.sp, lineHeight = 44.sp, textAlign = TextAlign.Center, maxLines = 2)
        }
        Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (state.number.isNotEmpty()) {
                TextButton({ dispatch(Action.ClearDial) }) { Text("전체 지우기", style = MaterialTheme.typography.bodyMedium) }
                TextButton({ dispatch(Action.DeleteDial) }) { Text("⌫") }
            }
        }
        Spacer(Modifier.weight(1f))
        PhoneKeypad({ if (state.number.length < 13) dispatch(Action.Dial(state.number + it)) })
        Spacer(Modifier.height(16.dp))
        CallActions(state.number, dispatch)
    }
}

@Composable
fun CallActions(number: String, dispatch: (Action) -> Unit) {
    val phone = PhoneNumber(number)
    Row(Modifier.fillMaxWidth().heightIn(min = 104.dp), horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally)) {
        RoundAction("일반 전화", "call_white", { dispatch(Action.StartNormal) }, size = 72.dp,
            color = ArsColors.Green, enabled = phone.canCall)
        if (phone.canUseAgent) {
            RoundAction("ARS Agent 전화", "agent_white", { dispatch(Action.StartAgent) }, size = 72.dp,
                color = ArsColors.Blue, labelColor = ArsColors.Blue)
        }
    }
}

@Composable
private fun BottomTabs(selected: PhoneTab, onTab: (PhoneTab) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).shadow(8.dp, CircleShape).background(Color.White, CircleShape).padding(4.dp)) {
        listOf(Triple(PhoneTab.Calls, "통화", "history"), Triple(PhoneTab.Contacts, "연락처", "contacts"),
            Triple(PhoneTab.Keypad, "키패드", "keypad")).forEach { (tab, title, icon) ->
            val active = selected == tab
            Column(Modifier.weight(1f).clip(CircleShape).background(if (active) ArsColors.Neutral else Color.White)
                .clickable(role = Role.Tab) { onTab(tab) }.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                FigmaIcon(icon, tint = if (active) ArsColors.Blue else ArsColors.Ink)
                Text(title, style = MaterialTheme.typography.labelMedium, color = if (active) ArsColors.Blue else ArsColors.Ink)
            }
        }
    }
}

@Composable
private fun ContactList(tab: PhoneTab, dispatch: (Action) -> Unit, modifier: Modifier) {
    var query by remember(tab) { mutableStateOf("") }
    val searchText = query.trim()
    val numberQuery = searchText.filterNot { it == '-' || it.isWhitespace() }
    val isNumberQuery = numberQuery.isNotEmpty() && numberQuery.all { it in '0'..'9' }
    val contacts = demoContacts.filter {
        it.name.contains(searchText, ignoreCase = true) ||
            (isNumberQuery && PhoneNumber(it.number).digits.contains(numberQuery))
    }
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(12.dp))
        Text(if (tab == PhoneTab.Contacts) "연락처" else "통화", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().background(ArsColors.Neutral, RoundedCornerShape(12.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FigmaIcon("search")
            Spacer(Modifier.width(8.dp))
            BasicTextField(query, { query = it }, textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                decorationBox = { inner -> Box { if (query.isEmpty()) Text(if (tab == PhoneTab.Contacts) "이름 또는 전화번호 검색" else "통화 기록 검색", color = ArsColors.Secondary); inner() } })
        }
        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(if (tab == PhoneTab.Calls) "오늘 · 예시 기록" else "예시 연락처", style = MaterialTheme.typography.bodyMedium, color = ArsColors.Secondary)
            if (contacts.isEmpty()) Text("검색 결과가 없어요.", modifier = Modifier.padding(vertical = 24.dp))
            contacts.forEach { contact ->
                Row(Modifier.fillMaxWidth().clickable { dispatch(Action.Detail(contact.number)) }.padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(ArsColors.Neutral, CircleShape), contentAlignment = Alignment.Center) {
                        FigmaIcon(if (tab == PhoneTab.Contacts) "contacts" else "call_dark")
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(contact.name, fontWeight = FontWeight.Medium,
                            color = if (tab == PhoneTab.Calls && contact.note == "부재중") ArsColors.Red else ArsColors.Ink)
                        Text(if (tab == PhoneTab.Contacts) formatPhone(contact.number) else "${formatPhone(contact.number)} · ${contact.note}",
                            style = MaterialTheme.typography.bodyMedium, color = ArsColors.Secondary)
                    }
                    Text(if (tab == PhoneTab.Contacts) "›" else contact.time, style = MaterialTheme.typography.bodyMedium, color = ArsColors.Secondary)
                }
                HorizontalDivider(color = ArsColors.Neutral)
            }
        }
    }
}

@Composable
private fun ContactDetail(state: AppState, dispatch: (Action) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton({ dispatch(Action.Back) }, Modifier.align(Alignment.Start)) {
            Text(if (state.tab == PhoneTab.Calls) "‹ 통화" else "‹ 연락처")
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(88.dp).background(ArsColors.Neutral, CircleShape), contentAlignment = Alignment.Center) {
            FigmaIcon("contacts", 48.dp, ArsColors.Secondary)
        }
        Spacer(Modifier.height(20.dp))
        Text(contactName(state.number), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth().background(ArsColors.Neutral, RoundedCornerShape(12.dp)).padding(16.dp)) {
            Text(if (PhoneNumber(state.number).canUseAgent) "전화번호" else "휴대전화", style = MaterialTheme.typography.bodyMedium, color = ArsColors.Secondary)
            Text(formatPhone(state.number), fontSize = 24.sp, color = ArsColors.Blue)
        }
        if (state.tab == PhoneTab.Calls) {
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().background(ArsColors.Neutral, RoundedCornerShape(12.dp)).padding(16.dp)) {
                Text("오늘 · 예시 통화 기록", style = MaterialTheme.typography.bodyMedium, color = ArsColors.Secondary)
                Text("발신 통화 · 00:18")
            }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(20.dp))
        Text(if (PhoneNumber(state.number).canUseAgent) "통화 방식을 선택해 주세요." else "일반 전화로 연결합니다.",
            style = MaterialTheme.typography.bodyMedium, color = ArsColors.Secondary)
        Spacer(Modifier.height(16.dp))
        CallActions(state.number, dispatch)
    }
}
