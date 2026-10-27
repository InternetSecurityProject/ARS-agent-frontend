package kr.ac.suwon.arsagent.servertest.voice

import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kr.ac.suwon.arsagent.data.api.CreatedSession
import kr.ac.suwon.arsagent.servertest.SERVER_TEST_URL
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.ByteString

object MuLaw {
    fun decode(bytes: ByteArray): ShortArray = ShortArray(bytes.size) { i ->
        val value = bytes[i].toInt().inv() and 255
        val amplitude = (((value and 15) shl 3) + 132) shl ((value ushr 4) and 7)
        (if (value and 128 != 0) 132 - amplitude else amplitude - 132).toShort()
    }
}

/** Receive-only: no send() method or microphone source exists in this transport. */
class ArsAudio(baseUrl: String = SERVER_TEST_URL, allowLocalHttp: Boolean = false) : Closeable {
    private val origin = baseUrl.toHttpUrl()
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).connectTimeout(10, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build()
    init {
        require(origin.isHttps || allowLocalHttp && origin.host in setOf("localhost", "127.0.0.1", "::1", "10.0.2.2"))
    }
    fun listen(session: CreatedSession): Flow<ShortArray> = callbackFlow {
        require(session.sessionId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        require(session.sessionToken.isNotBlank() && session.sessionToken.all { it.code in 33..126 })
        require(session.audioUrl.startsWith("wss://") || session.audioUrl.startsWith("ws://"))
        val url = session.audioUrl.replaceFirst("wss://", "https://").replaceFirst("ws://", "http://").toHttpUrl()
        require(url.scheme == origin.scheme && url.host == origin.host && url.port == origin.port &&
            url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null &&
            url.encodedPath == "/v1/sessions/${session.sessionId}/audio")
        val authenticated = url.newBuilder().addQueryParameter("token", session.sessionToken).build()
        val socket = http.newWebSocket(Request.Builder().url(authenticated).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (bytes.size > 8192) { channel.close(IllegalStateException("오디오 프레임 형식 오류")); webSocket.cancel() }
                else if (bytes.size > 0) trySend(MuLaw.decode(bytes.toByteArray()))
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                channel.close(IllegalStateException("오디오 형식 오류")); webSocket.cancel()
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                channel.close(); webSocket.close(1000, null)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                channel.close(IllegalStateException("ARS 소리에 연결하지 못했어요."))
            }
        })
        awaitClose { socket.cancel() }
    }.buffer(5, BufferOverflow.DROP_OLDEST)

    override fun close() { http.dispatcher.cancelAll(); http.connectionPool.evictAll(); http.dispatcher.executorService.shutdown() }
}
