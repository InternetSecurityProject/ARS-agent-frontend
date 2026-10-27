package kr.ac.suwon.arsagent.livecall

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kr.ac.suwon.arsagent.model.AudioRoute

/** Does not touch system audio until enter(). Only routes offered by the OS can be selected. */
@Suppress("DEPRECATION")
class AndroidAudioRoutes(context: Context) : AudioRouteDevice {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private var savedMode: Int? = null
    private var savedSpeaker = false
    private var savedDevice: AudioDeviceInfo? = null
    private var selected = false
    var onChange: (() -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) { onChange?.invoke() }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) { onChange?.invoke() }
    }
    private var communicationListener: AudioManager.OnCommunicationDeviceChangedListener? = null

    override fun enter() {
        if (savedMode != null) return
        check(audio.mode != AudioManager.MODE_IN_CALL) // Never hijack an actual SIM call.
        savedMode = audio.mode
        savedSpeaker = audio.isSpeakerphoneOn
        if (Build.VERSION.SDK_INT >= 31) savedDevice = audio.communicationDevice
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        audio.registerAudioDeviceCallback(devices, main)
        if (Build.VERSION.SDK_INT >= 31) {
            communicationListener = AudioManager.OnCommunicationDeviceChangedListener { onChange?.invoke() }
                .also { audio.addOnCommunicationDeviceChangedListener(contextExecutor, it) }
        }
    }
    private val contextExecutor = java.util.concurrent.Executor { main.post(it) }
    private fun route(device: AudioDeviceInfo?): AudioRoute? = when (device?.type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioRoute.Phone
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioRoute.Speaker
        else -> null
    }
    private fun outputs() = if (Build.VERSION.SDK_INT >= 31) audio.availableCommunicationDevices
        else audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
    private fun externalOutput() = outputs().any { it.type in setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_USB_HEADSET) }
    override fun available(): Set<AudioRoute> = outputs().mapNotNull(::route).toSet().let {
        if (Build.VERSION.SDK_INT < 31 && externalOutput()) it - AudioRoute.Phone else it
    }
    override fun current(): AudioRoute? = if (Build.VERSION.SDK_INT >= 31) route(audio.communicationDevice)
        else if (audio.isSpeakerphoneOn) AudioRoute.Speaker else if (externalOutput()) null else AudioRoute.Phone
    override fun select(route: AudioRoute): Boolean {
        if (savedMode == null || route !in available()) return false
        return if (Build.VERSION.SDK_INT >= 31) {
            val target = outputs().firstOrNull { route(it) == route } ?: return false
            audio.setCommunicationDevice(target).also { if (it) selected = true }
        } else {
            audio.isSpeakerphoneOn = route == AudioRoute.Speaker
            current() == route
        }
    }
    override fun restore() {
        val mode = savedMode ?: return
        savedMode = null
        audio.unregisterAudioDeviceCallback(devices)
        if (Build.VERSION.SDK_INT >= 31) {
            communicationListener?.let(audio::removeOnCommunicationDeviceChangedListener)
            communicationListener = null
            if (selected) {
                audio.clearCommunicationDevice()
                savedDevice?.takeIf { old -> audio.availableCommunicationDevices.any { it.id == old.id } }
                    ?.let(audio::setCommunicationDevice)
            }
        } else audio.isSpeakerphoneOn = savedSpeaker
        selected = false; savedDevice = null
        audio.mode = mode
    }
}
