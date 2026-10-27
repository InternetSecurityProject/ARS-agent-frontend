package kr.ac.suwon.arsagent.updates

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import kr.ac.suwon.arsagent.ui.ArsTheme

@Composable
fun UpdatePrompt(ui: UpdateUi, signIn: () -> Unit, install: () -> Unit, dismiss: () -> Unit) {
    if (ui.stage == UpdateStage.Hidden || ui.stage == UpdateStage.Checking) return
    val busy = ui.stage == UpdateStage.Installing || ui.stage == UpdateStage.SigningIn
    ArsTheme {
        AlertDialog(
            onDismissRequest = { if (!busy) dismiss() },
            title = { Text(when (ui.stage) {
                UpdateStage.Consent -> "팀 앱 업데이트 안내"
                UpdateStage.Available -> "새 버전이 있어요"
                UpdateStage.Installing -> "업데이트 준비 중"
                UpdateStage.SigningIn -> "테스터 로그인 중"
                else -> "업데이트 확인을 마치지 못했어요"
            }) },
            text = { Column {
                Text(when (ui.stage) {
                    UpdateStage.Consent -> "초대받은 Google 계정으로 로그인하면 새 버전을 앱에서 안내해 드려요. 지금 하지 않아도 앱을 사용할 수 있어요."
                    UpdateStage.Available -> "${ui.version}\n지금 설치할까요? 설치는 Android 확인창에서 직접 승인해 주세요."
                    UpdateStage.Installing -> "다운로드 후 Android 설치 확인창이 열려요."
                    UpdateStage.SigningIn -> "Google 로그인 화면에서 계속해 주세요."
                    else -> "네트워크나 테스터 초대 상태를 확인해 주세요. 앱은 계속 사용할 수 있어요. 다음에 앱을 다시 열면 재시도해요."
                })
                if (busy) LinearProgressIndicator()
            } },
            confirmButton = { when (ui.stage) {
                UpdateStage.Consent -> TextButton(signIn) { Text("로그인") }
                UpdateStage.Available -> TextButton(install) { Text("업데이트") }
                UpdateStage.Failed -> TextButton(dismiss) { Text("닫기") }
                else -> Unit
            } },
            dismissButton = {
                if (!busy && ui.stage != UpdateStage.Failed) TextButton(dismiss) { Text("나중에") }
            },
        )
    }
}
