package kr.ac.suwon.arsagent.updates

import androidx.compose.runtime.Composable

/** Release/Play builds do not depend on Firebase's self-update implementation. */
@Composable
fun TeamUpdates(@Suppress("UNUSED_PARAMETER") eligible: Boolean) = Unit
