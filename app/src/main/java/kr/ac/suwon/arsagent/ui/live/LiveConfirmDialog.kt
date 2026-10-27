package kr.ac.suwon.arsagent.ui.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import kr.ac.suwon.arsagent.livecall.LiveConsent
import kr.ac.suwon.arsagent.model.formatPhone

@Composable
internal fun LiveConfirmDialog(target: LiveConsent, confirm: (String) -> Unit, dismiss: () -> Unit) {
    var key by remember(target) { mutableStateOf("") } // Never saved across recreation.
    val validKey = key.isNotBlank() && key.all { it.code in 32..126 }
    AlertDialog(onDismissRequest = dismiss, title = { Text("실제로 전화를 걸까요?") },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatPhone(target.number), style = MaterialTheme.typography.titleLarge)
                Text(target.goal)
                Text("서버의 070 회선으로 실제 전화가 걸려요. 통화 시간과 요금이 발생할 수 있어요.")
                OutlinedTextField(key, { key = it.take(1024) }, Modifier.fillMaxWidth().testTag("live-key"),
                    label = { Text("API 키") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password))
                Text("키는 저장하지 않고 발신 요청 후 지워요.", style = MaterialTheme.typography.bodyMedium)
            }
        }, confirmButton = {
            TextButton({ val consumed = key; key = ""; confirm(consumed) }, enabled = validKey) { Text("실제 발신") }
        }, dismissButton = { TextButton(dismiss, Modifier.testTag("live-cancel-dial")) { Text("취소") } })
}
