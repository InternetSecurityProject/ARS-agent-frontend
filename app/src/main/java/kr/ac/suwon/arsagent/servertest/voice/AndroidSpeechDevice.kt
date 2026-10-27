package kr.ac.suwon.arsagent.servertest.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import java.util.Locale

/** One foreground utterance at a time. Raw speech is never written, saved, or sent to the institution. */
class AndroidSpeechDevice(context: Context, private val report: (String) -> Unit) : SpeechDevice {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private val initialization = SpeechInitialization({ delay, action -> main.postDelayed({ action() }, delay) }, {
        report("음성 안내 준비가 지연되고 있어요. 화면으로 확인해 주세요.")
    })
    private var recognizer: SpeechRecognizer? = null
    private var epoch = 0L
    private var closed = false

    override fun speak(text: String, onDone: () -> Unit) {
        stop(); val token = epoch
        val speak = {
            if (!closed && token == epoch) {
                val engine = tts
                if (engine == null || !ready || failed) report("한국어 음성 안내를 사용할 수 없어요. 화면으로 확인해 주세요.")
                else {
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            main.post { if (!closed && token == epoch && utteranceId == "agent-$token") onDone() }
                        }
                        @Suppress("OVERRIDE_DEPRECATION")
                        override fun onError(utteranceId: String?) {
                            main.post { if (!closed && token == epoch && utteranceId == "agent-$token") report("음성 안내에 실패했어요. 화면으로 확인해 주세요.") }
                        }
                    })
                    if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agent-$token") == TextToSpeech.ERROR)
                        report("음성 안내에 실패했어요. 화면으로 확인해 주세요.")
                }
            }
        }
        if (ready || failed) speak()
        else {
            initialization.replace(speak)
            if (tts == null) {
                try {
                    tts = TextToSpeech(app) { status -> main.post {
                        if (!closed) {
                            ready = status == TextToSpeech.SUCCESS && (tts?.setLanguage(Locale.KOREAN) ?: -1) >= 0
                            failed = !ready
                            initialization.complete()
                        }
                    } }
                } catch (_: Exception) { failed = true; initialization.clear(); speak() }
            }
        }
    }

    override fun listen(onResult: (String) -> Unit, onError: (String) -> Unit) {
        stop(); val token = epoch
        if (closed) return
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError("말로 답하려면 마이크 권한이 필요해요."); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(app)) {
            onError("음성 인식 서비스를 사용할 수 없어요. 글로 입력해 주세요."); return
        }
        try {
            val device = if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(app))
                SpeechRecognizer.createOnDeviceSpeechRecognizer(app) else SpeechRecognizer.createSpeechRecognizer(app)
            recognizer = device
            device.setRecognitionListener(object : RecognitionListener {
                private fun active() = !closed && epoch == token
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onError(error: Int) {
                    if (active()) {
                        epoch++; recognizer = null; device.destroy()
                        onError(when (error) {
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한을 허용해 주세요."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "음성 인식 네트워크를 확인해 주세요."
                            else -> "음성을 인식하지 못했어요. 다시 말하거나 글로 입력해 주세요."
                        })
                    }
                }
                override fun onResults(results: Bundle?) {
                    if (active()) {
                        epoch++; recognizer = null; device.destroy()
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        onResult(text)
                    }
                }
            })
            device.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        } catch (_: Exception) { stop(); onError("음성 인식을 시작하지 못했어요. 글로 입력해 주세요.") }
    }

    override fun stop() {
        epoch++; initialization.clear()
        recognizer?.let { it.cancel(); it.destroy() }; recognizer = null
        tts?.stop()
    }
    override fun close() {
        if (closed) return
        closed = true; stop(); tts?.shutdown(); tts = null
    }
}
