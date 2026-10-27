package kr.ac.suwon.arsagent.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest

@Composable
fun FigmaIcon(name: String, size: Dp = 24.dp, tint: Color? = null) {
    val context = LocalContext.current
    val loader = remember(context) { ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build() }
    AsyncImage(
        model = ImageRequest.Builder(context).data("file:///android_asset/figma/$name.svg").build(),
        imageLoader = loader, contentDescription = null,
        colorFilter = tint?.let { ColorFilter.tint(it) }, modifier = Modifier.size(size),
    )
}

@Composable
fun RoundAction(
    label: String, icon: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    size: Dp = 60.dp, color: Color = Color.White, labelColor: Color = ArsColors.Ink,
    iconSize: Dp = 24.dp, enabled: Boolean = true, tint: Color? = null,
) {
    Column(modifier.width(96.dp).alpha(if (enabled) 1f else .38f)
        .clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size).shadow(if (color == Color.White) 8.dp else 0.dp, CircleShape)
            .background(color, CircleShape), contentAlignment = Alignment.Center) {
            FigmaIcon(icon, iconSize, tint)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor, textAlign = TextAlign.Center)
    }
}

private val letters = mapOf('2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL", '6' to "MNO",
    '7' to "PQRS", '8' to "TUV", '9' to "WXYZ", '0' to "+")

@Composable
fun PhoneKeypad(
    onKey: (Char) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false,
    enabled: Boolean = true,
    keyEnabled: (Char) -> Boolean = { true },
    rowSpacing: Dp = 24.dp,
) {
    // App-specific phone grid, exceeding Android's 48dp touch-target / 8dp spacing minimum.
    val size = 72.dp
    val keypadWidth = 280.dp
    Column(modifier.widthIn(max = keypadWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        listOf("123", "456", "789", "*0#").forEach { digits ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                digits.forEach { digit ->
                    Column(Modifier.size(size).shadow(if (compact) 0.dp else 7.dp, CircleShape)
                        .background(Color.White.copy(alpha = if (compact) .76f else 1f), CircleShape)
                        .clip(CircleShape).clickable(enabled = enabled && keyEnabled(digit), role = Role.Button) { onKey(digit) }
                        .semantics { contentDescription = "키 $digit" },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(digit.toString(), fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            fontSize = 32.sp, lineHeight = 36.sp)
                        Text(letters[digit] ?: "", fontSize = 10.sp, lineHeight = 12.sp, modifier = Modifier.heightIn(min = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressCard(modifier: Modifier = Modifier, scrollable: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val scrollState = rememberScrollState()
    Column(modifier.shadow(8.dp, RoundedCornerShape(20.dp)).background(Color.White, RoundedCornerShape(20.dp))
        .padding(16.dp).then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Scope the paragraph font to this card. Explicit title/label/action styles stay unchanged.
        ProvideTextStyle(LocalTextStyle.current.copy(fontFamily = ProgressBodyFont)) { content() }
    }
}

@Composable
fun VoiceInput(value: String, placeholder: String, onValue: (String) -> Unit, onExample: () -> Unit,
    microphoneDescription: String = "예시 음성 문장 입력, 녹음하지 않음", microphoneEnabled: Boolean = true,
    inputEnabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).background(ArsColors.Neutral, RoundedCornerShape(16.dp))
        .border(1.dp, ArsColors.Border, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(value, onValue, enabled = inputEnabled, textStyle = MaterialTheme.typography.bodyLarge.copy(color = ArsColors.Ink),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f).semantics { contentDescription = "음성 대신 텍스트 입력" },
            decorationBox = { inner -> Box {
                if (value.isEmpty()) Text(placeholder, color = ArsColors.Secondary, style = MaterialTheme.typography.bodyLarge)
                inner()
            } })
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(48.dp).background(ArsColors.Blue, CircleShape).clip(CircleShape)
            .clickable(enabled = microphoneEnabled, role = Role.Button, onClick = onExample).semantics { contentDescription = microphoneDescription },
            contentAlignment = Alignment.Center) { FigmaIcon("mic") }
    }
}

@Composable
fun PrimaryTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick, modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ArsColors.Border)) { Text(text) }
}
