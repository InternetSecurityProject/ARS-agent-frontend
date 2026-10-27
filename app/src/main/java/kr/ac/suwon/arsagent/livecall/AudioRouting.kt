package kr.ac.suwon.arsagent.livecall

import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.ac.suwon.arsagent.model.AudioRoute

interface AudioRouteDevice {
    fun enter()
    fun available(): Set<AudioRoute>
    fun current(): AudioRoute?
    fun select(route: AudioRoute): Boolean
    fun restore()
}

data class AudioRoutingState(val active: Boolean = false, val route: AudioRoute? = null,
    val available: Set<AudioRoute> = emptySet(), val error: String? = null)

/** Foreground-only owner. It reports the device's actual route, never the requested route. */
class AudioRouting(private val device: AudioRouteDevice) : Closeable {
    private val mutable = MutableStateFlow(AudioRoutingState())
    val state = mutable.asStateFlow()
    fun activate() {
        if (mutable.value.active) return
        try {
            device.enter()
            mutable.value = AudioRoutingState(active = true)
            refresh()
        } catch (_: Exception) {
            runCatching { device.restore() }
            mutable.value = AudioRoutingState(error = "오디오 출력을 준비하지 못했어요. 기기의 오디오 설정을 확인해 주세요.")
        }
    }
    fun refresh() {
        if (!mutable.value.active) return
        try { mutable.value = mutable.value.copy(route = device.current(), available = device.available()) }
        catch (_: Exception) { mutable.value = mutable.value.copy(error = "오디오 출력 상태를 확인하지 못했어요.") }
    }
    fun select(route: AudioRoute) {
        if (!mutable.value.active) return
        refresh()
        val accepted = route in mutable.value.available && runCatching { device.select(route) }.getOrDefault(false)
        refresh()
        mutable.value = mutable.value.copy(error = if (accepted) null else "이 오디오 출력은 사용할 수 없어요.")
    }
    fun restore() {
        if (!mutable.value.active) return
        val restored = runCatching { device.restore() }.isSuccess
        mutable.value = AudioRoutingState(error = if (restored) null else "기기의 오디오 설정을 확인해 주세요.")
    }
    override fun close() = restore()
}
