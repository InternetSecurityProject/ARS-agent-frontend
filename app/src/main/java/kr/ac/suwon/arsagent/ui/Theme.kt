package kr.ac.suwon.arsagent.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.sp
import kr.ac.suwon.arsagent.R

object ArsColors {
    val Blue = Color(0xFF007AFF)
    val Green = Color(0xFF34C759)
    val Red = Color(0xFFFF3B30)
    val Ink = Color(0xFF1C1C1E)
    val Secondary = Color(0xFF636366)
    val Neutral = Color(0xFFF2F2F7)
    val Border = Color(0xFFD1D1D6)
    val CallGradient = listOf(Color(0xFFFFF1E8), Color(0xFFE9E6FA), Color(0xFFDDF4EE), Neutral)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val Noto = FontFamily(
    Font(R.font.noto_sans_kr, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.noto_sans_kr, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.noto_sans_kr, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

@Composable
fun ArsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = ArsColors.Blue, background = Color.White,
            surface = Color.White, onSurface = ArsColors.Ink, onBackground = ArsColors.Ink),
        typography = Typography(
            bodyLarge = TextStyle(fontFamily = Noto, fontSize = 15.sp, lineHeight = 23.sp),
            bodyMedium = TextStyle(fontFamily = Noto, fontSize = 13.sp, lineHeight = 20.sp),
            labelMedium = TextStyle(fontFamily = Noto, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
            titleLarge = TextStyle(fontFamily = Noto, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
            headlineLarge = TextStyle(fontFamily = Noto, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 38.sp),
        ),
        content = content,
    )
}
