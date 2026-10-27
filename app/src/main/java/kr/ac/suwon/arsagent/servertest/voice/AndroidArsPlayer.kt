package kr.ac.suwon.arsagent.servertest.voice

import android.media.*
import java.io.Closeable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.ac.suwon.arsagent.data.api.CreatedSession

data class ArsPlaybackStatus(val enabled: Boolean = false, val playing: Boolean = false, val error: String? = null)

class AndroidArsPlayer(private val scope: CoroutineScope, private val communicationAudio: Boolean = false) : Closeable {
    private val mutable = MutableStateFlow(ArsPlaybackStatus())
    val state = mutable.asStateFlow()
    @Volatile private var job: Job? = null
    private val gate = ArsPlaybackGate()

    fun start(session: CreatedSession) {
        stop(); mutable.value = ArsPlaybackStatus(enabled = true)
        val token = gate.begin()
        val next = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            val source = ArsAudio()
            try {
                if (!gate.attach(token, AndroidPcmSink(communicationAudio))) return@launch
                source.listen(session).collect { samples ->
                    ensureActive()
                    gate.publish(token) {
                        if (gate.write(token, samples)) mutable.value = ArsPlaybackStatus(enabled = true, playing = true)
                    }
                }
                gate.publish(token) { mutable.value = ArsPlaybackStatus(error = "ARS 소리 연결이 종료됐어요. 다시 켤 수 있어요.") }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                gate.publish(token) { mutable.value = ArsPlaybackStatus(error = "ARS 소리를 재생하지 못했어요. 상태 확인 후 다시 켜 주세요.") }
            } finally {
                gate.release(token); source.close()
            }
        }
        job = next
        next.start()
    }

    fun mute(value: Boolean) {
        gate.mute(value)
        if (value) mutable.value = mutable.value.copy(playing = false)
    }

    fun stop() {
        gate.stop()
        job?.cancel(); job = null
        mutable.value = ArsPlaybackStatus()
    }
    override fun close() = stop()
}

private class AndroidPcmSink(communicationAudio: Boolean) : PcmSink {
    private val track: AudioTrack
    init {
        val size = AudioTrack.getMinBufferSize(8000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(size > 0)
        val created = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder()
            .setUsage(if (communicationAudio) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(8000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(size, 1600)).setTransferMode(AudioTrack.MODE_STREAM).build()
        if (created.state != AudioTrack.STATE_INITIALIZED) { created.release(); error("음성 출력 초기화 실패") }
        track = created
    }
    override fun play() = track.play()
    override fun pauseAndFlush() { track.pause(); track.flush() }
    override fun write(samples: ShortArray) { check(track.write(samples, 0, samples.size, AudioTrack.WRITE_NON_BLOCKING) >= 0) }
    override fun close() { runCatching { track.stop() }; track.release() }
}
