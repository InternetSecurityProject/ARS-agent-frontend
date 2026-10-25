package kr.ac.suwon.arsagent.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kr.ac.suwon.arsagent.model.*

@Composable
fun ArsApp(state: AppState, onAction: (Action) -> Unit) {
    var showScenarios by remember { mutableStateOf(false) }
    BackHandler(enabled = state.screen != Screen.Home) { onAction(Action.Back) }
    ArsTheme {
        val home = state.screen == Screen.Home || state.screen == Screen.Detail
        val background = if (home) Modifier.background(Color.White) else Modifier.background(
            Brush.verticalGradient(0f to ArsColors.CallGradient[0], .32f to ArsColors.CallGradient[1],
                .62f to ArsColors.CallGradient[2], 1f to ArsColors.CallGradient[3]))
        Box(Modifier.fillMaxSize().then(background).safeDrawingPadding().imePadding()) {
            if (home) PhoneHome(state, onAction) { showScenarios = true }
            else CallScreen(state, onAction)
        }
        if (showScenarios) {
            AlertDialog(onDismissRequest = { showScenarios = false }, title = { Text("UI 데모 시나리오") },
                text = {
                    Column {
                        Text("실제 전화·음성 인식 없이 화면 흐름을 확인해요.")
                        DemoScenario.entries.forEach { scenario ->
                            TextButton(onClick = { onAction(Action.Scenario(scenario)); showScenarios = false }) {
                                Text((if (state.scenario == scenario) "✓ " else "") + scenario.title)
                            }
                        }
                    }
                }, confirmButton = { TextButton({ showScenarios = false }) { Text("닫기") } })
        }
    }
}
