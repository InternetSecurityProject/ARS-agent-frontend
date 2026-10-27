package kr.ac.suwon.arsagent.servertest

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kr.ac.suwon.arsagent.data.api.CreatedSession
import kr.ac.suwon.arsagent.servertest.voice.*
import okhttp3.*
import okhttp3.mockwebserver.*
import okio.ByteString.Companion.toByteString
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ArsAudioTest {
    @Test fun muLawDecodesHandCalculatedSilenceAndPeaks() {
        assertArrayEquals(shortArrayOf(0, 0, -32124, 32124), MuLaw.decode(byteArrayOf(0xff.toByte(), 0x7f, 0, 0x80.toByte())))
    }

    @Test fun receivesBinaryAudioUsingOnlySessionTokenAndNeverTransmitsMic() = runBlocking {
        val server = MockWebServer(); server.start()
        val received = AtomicInteger()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(byteArrayOf(0xff.toByte(), 0).toByteString())
            }
            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) { received.incrementAndGet() }
            override fun onMessage(webSocket: WebSocket, text: String) { received.incrementAndGet() }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
        }))
        val audio = ArsAudio(server.url("/v1").toString(), true)
        try {
            val ws = server.url("/v1/sessions/s1/audio").toString().replace("http://", "ws://")
            val session = CreatedSession("s1", "test-audio-secret", "", ws, "07000000000", "listening", "조회")
            val frame = withTimeout(3_000) { audio.listen(session).first() }
            assertArrayEquals(shortArrayOf(0, -32124), frame)
            val request = server.takeRequest()
            assertEquals("/v1/sessions/s1/audio?token=test-audio-secret", request.path)
            assertNull(request.getHeader("X-App-Key")); assertNull(request.getHeader("Authorization"))
            assertEquals(0, received.get())
        } finally { audio.close(); server.shutdown() }
    }

    @Test fun externalOriginWrongPathAndQueryAreRejectedBeforeConnecting() = runBlocking {
        val audio = ArsAudio("https://api.example/v1")
        try {
            for (url in listOf("wss://evil.example/v1/sessions/s1/audio", "ws://api.example/v1/sessions/s1/audio",
                "wss://api.example/v1/sessions/other/audio", "wss://api.example/v1/sessions/s1/audio?token=unexpected")) {
                val session = CreatedSession("s1", "token", "", url, "131", "listening", "조회")
                try { withTimeout(500) { audio.listen(session).first() }; fail("unsafe URL allowed") }
                catch (expected: IllegalArgumentException) { /* local validation, no network */ }
            }
        } finally { audio.close() }
    }

    @Test fun redirectsNeverForwardTheAudioTokenOrRetry() = runBlocking {
        val server = MockWebServer(); val other = MockWebServer(); server.start(); other.start()
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/audio")))
        val audio = ArsAudio(server.url("/v1").toString(), true)
        try {
            val url = server.url("/v1/sessions/s1/audio").toString().replace("http://", "ws://")
            try { withTimeout(3_000) { audio.listen(CreatedSession("s1", "secret", "", url, "131", "listening", "조회")).first() }; fail() }
            catch (expected: IllegalStateException) { assertFalse(expected.message.orEmpty().contains("secret")) }
            assertEquals(1, server.requestCount); assertEquals(0, other.requestCount)
        } finally { audio.close(); server.shutdown(); other.shutdown() }
    }
}
