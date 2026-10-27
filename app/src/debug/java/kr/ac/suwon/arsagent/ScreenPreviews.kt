package kr.ac.suwon.arsagent

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** Debug-only gallery: call state stays fixed while decorative effects can animate. */
@Preview(showBackground = true)
@Composable
fun B04Preview() {
    ScreenGallery(initiallyShowPicker = false)
}
