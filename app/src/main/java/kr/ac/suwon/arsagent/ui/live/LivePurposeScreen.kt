package kr.ac.suwon.arsagent.ui.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.delay
import kr.ac.suwon.arsagent.livecall.*
import kr.ac.suwon.arsagent.model.*
import kr.ac.suwon.arsagent.servertest.ServerTestState
import kr.ac.suwon.arsagent.servertest.voice.ServerVoiceUi
import kr.ac.suwon.arsagent.ui.*

@Composable
internal fun LivePurposeScreen(entry: LiveEntry, server: ServerTestState, owner: LiveCallOwner, voice: ServerVoiceUi?) {
    var preparedGoal by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(entry.goal) {
        val goal = entry.goal.trim()
        if (goal.isNotEmpty() && goal != preparedGoal) {
            delay(1_200)
            preparedGoal = goal
            voice?.cancelInput?.invoke()
            owner.prepare() // Opens consent, never a session.
        }
    }
    BackHandler { owner.cancelPurpose() }
    ArsTheme {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to ArsColors.CallGradient[0], .32f to ArsColors.CallGradient[1],
            .62f to ArsColors.CallGradient[2], 1f to ArsColors.CallGradient[3])).safeDrawingPadding().imePadding()) {
            CallScreen(AppState(screen = Screen.Purpose, number = entry.number, agent = true),
                { if (it == Action.Back) owner.cancelPurpose() }, liveControls = ServerCallControls(), purposeContent = {
                    Text("어떤 일을 도와드릴까요?", style = MaterialTheme.typography.titleLarge)
                    VoiceInput(entry.goal, "원하는 일을 말하거나 입력해 주세요", owner::editGoal,
                        { voice?.goal?.invoke(owner::editGoal) }, microphoneDescription = "목적 말하기", microphoneEnabled = voice != null)
                    Text("목적을 확인한 뒤 실제 발신을 승인해 주세요.", color = ArsColors.Secondary)
                    voice?.let {
                        if (it.status.listening) Text("듣고 있어요. Agent에게 말씀해 주세요.", color = ArsColors.Blue)
                        it.status.error?.let { error -> Text(error, color = ArsColors.Red) }
                    }
                    server.error?.let { Text(it, color = ArsColors.Red) }
                    if (entry.goal.isNotBlank()) TextButton({ voice?.cancelInput?.invoke(); owner.prepare() }) { Text("발신 확인") }
                })
        }
        entry.confirmation?.let { consent ->
            LiveConfirmDialog(consent, { owner.confirm(it) }, owner::dismissConfirmation)
        }
    }
}
