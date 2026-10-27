package kr.ac.suwon.arsagent.ui

import androidx.compose.runtime.Composable
import kr.ac.suwon.arsagent.model.AudioRoute

/** Optional live bindings. Their absence preserves the demo and prepared-data renderer. */
data class ServerCallControls(
    val elapsedSeconds: Int? = null,
    val endEnabled: Boolean = true,
    val arsEnabled: Boolean = false,
    val arsAvailable: Boolean = false,
    val keypadAvailable: Boolean = false,
    val keypadOpen: Boolean = false,
    val manualKeys: String = "",
    val audioMenu: Boolean = false,
    val audioRoute: AudioRoute? = null,
    val availableRoutes: Set<AudioRoute> = emptySet(),
    val onToggleArs: () -> Unit = {},
    val onKeypad: () -> Unit = {},
    val onAudioMenu: () -> Unit = {},
    val onRoute: (AudioRoute) -> Unit = {},
    val onKey: (Char) -> Unit = {},
    val extras: @Composable () -> Unit = {},
)

data class LiveAudioUi(val route: AudioRoute?, val available: Set<AudioRoute>, val error: String? = null,
    val select: (AudioRoute) -> Unit)
