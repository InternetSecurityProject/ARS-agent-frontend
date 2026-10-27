package kr.ac.suwon.arsagent.data.api

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ArsApiClientCloseTest {
    @Test fun closingAnIdleConnectionDoesNotPerformSocketIoOnTheCallingThread() = runBlocking {
        val callingThread = Thread.currentThread()
        val socketClosedOn = CompletableFuture<Thread>()
        val sockets = object : SocketFactory() {
            override fun createSocket(): Socket = object : Socket() {
                override fun close() {
                    try { super.close() }
                    finally { socketClosedOn.complete(Thread.currentThread()) }
                }
            }
            override fun createSocket(host: String, port: Int) = createSocket().apply {
                connect(InetSocketAddress(host, port))
            }
            override fun createSocket(host: InetAddress, port: Int) = createSocket().apply {
                connect(InetSocketAddress(host, port))
            }
            override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int) = createSocket().apply {
                bind(InetSocketAddress(local, localPort)); connect(InetSocketAddress(host, port))
            }
            override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int) = createSocket().apply {
                bind(InetSocketAddress(local, localPort)); connect(InetSocketAddress(host, port))
            }
        }
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"backend":"sim","stt":"none","interpreter":"MockInterpreter","mock_goals":[],"active_session":null}"""))
            val http = OkHttpClient.Builder().socketFactory(sockets).build()
            val api = ArsApiClient(ArsApiConfig(server.url("/v1").toString(), "unused-health-key", true), http)
            try {
                assertEquals("sim", api.health().backend)
                assertEquals(1, http.connectionPool.idleConnectionCount())
                api.close()
                assertNotSame("Socket cleanup must not run on the UI/calling thread", callingThread,
                    socketClosedOn.get(5, TimeUnit.SECONDS))
                assertThrows(IllegalStateException::class.java) { runBlocking { api.health() } }
                api.close() // Closing again is harmless and must not schedule work on a shut-down executor.
                assertEquals(1, server.requestCount)
                assertEquals("/v1/health", server.takeRequest(5, TimeUnit.SECONDS)?.path)
            } finally {
                api.close()
            }
        }
    }
}
