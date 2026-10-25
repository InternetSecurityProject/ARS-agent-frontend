package kr.ac.suwon.arsagent

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** Static debug-only preview; the normal demo flow and release APK are unchanged. */
@Preview(showBackground = true)
@Composable
fun B04Preview() {
    ScreenGallery(initiallyShowPicker = false)
}
