package kr.ac.suwon.arsagent.data.api

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import okio.BufferedSink

/** Unwired transport: construction and app launch never initiate a request. */
class ArsApiClient(private val config: ArsApiConfig) : Closeable {
    private val http = OkHttpClient.Builder()
        .retryOnConnectionFailure(false) // POST /sessions can place a call: never repeat it implicitly.
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    private val closed = AtomicBoolean(false)

    suspend fun health(): HealthResponse = withContext(Dispatchers.IO) {
        decode(exchange(Request.Builder().url(endpoint("health")).get().build(), setOf(200)).body)
    }

    suspend fun createSession(phoneNumber: String, goal: String): CreatedSession = withContext(Dispatchers.IO) {
        val digits = phoneNumber.trim().replace("-", "").replace(" ", "")
        require(digits.matches(Regex("[0-9]{3,13}"))) { "Phone number must contain 3 to 13 digits" }
        require(goal.isNotBlank()) { "A goal is required" }
        val request = Request.Builder().url(endpoint("sessions")).header("X-App-Key", config.appKey)
            .post(apiJson.encodeToString(CreateSessionRequest(digits, goal.trim())).jsonBody()).build()
        decode<CreatedSession>(exchange(request, setOf(201)).body)
    }

    suspend fun sendAction(session: CreatedSession, action: SessionAction): ActionResponse = withContext(Dispatchers.IO) {
        validateAction(action)
        val request = authenticated(session, "actions")
            .post(apiJson.encodeToString<SessionAction>(action).jsonBody()).build()
        val response = decode<ActionResponse>(exchange(request, setOf(200)).body)
        if (!response.accepted) throw ApiProtocolException()
        response
    }

    suspend fun getResult(session: CreatedSession): SessionResult = withContext(Dispatchers.IO) {
        val response = exchange(authenticated(session, "result").get().build(), setOf(200, 202))
        if (response.status == 202) {
            val status = try { apiJson.parseToJsonElement(response.body).jsonObject["status"]?.jsonPrimitive?.content }
            catch (_: IllegalArgumentException) { throw ApiProtocolException() }
            if (status.isNullOrBlank()) throw ApiProtocolException()
            SessionResult.InProgress(status)
        } else SessionResult.Completed(decode(response.body))
    }

    /** Collect again after a failure to receive a fresh snapshot. Never replay mutating actions. */
    fun events(session: CreatedSession): Flow<SessionEvent> = callbackFlow {
        check(!closed.get()) { "API client is closed" }
        val url = eventUrl(session)
        val socket = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = try { EventCodec.decode(text) } catch (_: ApiProtocolException) {
                    channel.close(ApiProtocolException()); webSocket.cancel(); return
                }
                if (trySend(event).isFailure) {
                    // Fail rather than silently losing a request/ended event when a consumer is slow.
                    channel.close(ApiStreamException()); webSocket.cancel(); return
                }
                if (event is SessionEvent.Ended || (event is SessionEvent.Snapshot && event.data.status == "ended")) {
                    channel.close(); webSocket.close(1000, null)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                channel.close(ApiProtocolException()); webSocket.cancel() // Events are JSON text, not audio.
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                channel.close(ApiStreamException(code)); webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { channel.close(ApiStreamException(code)) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { channel.close(ApiStreamException(response?.code)) }
        })
        awaitClose { socket.cancel() }
    }

    private fun authenticated(session: CreatedSession, suffix: String): Request.Builder {
        validateCredentials(session)
        return Request.Builder().url(endpoint("sessions", session.sessionId, suffix))
            .header("Authorization", "Bearer ${session.sessionToken}")
    }

    private fun eventUrl(session: CreatedSession): HttpUrl {
        validateCredentials(session)
        val raw = session.eventsUrl
        if (!raw.startsWith("ws://") && !raw.startsWith("wss://")) throw ApiProtocolException()
        val url = raw.replaceFirst("wss://", "https://").replaceFirst("ws://", "http://").toHttpUrlOrNull()
            ?: throw ApiProtocolException()
        val origin = config.baseUrl
        if (url.scheme != origin.scheme || url.host != origin.host || url.port != origin.port ||
            url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null ||
            url.encodedPath != endpoint("sessions", session.sessionId, "events").encodedPath) throw ApiProtocolException()
        return url.newBuilder().addQueryParameter("token", session.sessionToken).build()
    }

    private fun validateCredentials(session: CreatedSession) {
        require(session.sessionId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Invalid session identifier" }
        require(session.sessionToken.isNotBlank() && session.sessionToken.all { it.code in 33..126 }) { "Invalid session token" }
    }

    private fun validateAction(action: SessionAction) {
        fun requestId(id: String) { require(id.isNotBlank()) { "A request identifier is required" } }
        when (action) {
            is SessionAction.Answer -> { requestId(action.requestId); require(action.text.isNotBlank()) { "An answer is required" } }
            is SessionAction.Select -> { requestId(action.requestId); require(action.key.isNotBlank()) { "A selected key is required" } }
            is SessionAction.Key -> require(action.key.matches(Regex("[0-9*#]{1,4}"))) { "Invalid keypad input" }
            is SessionAction.SecureInput -> {
                requestId(action.requestId)
                require(action.digits.matches(Regex("[0-9*#]{1,20}"))) { "Invalid secure input" }
            }
            is SessionAction.ReviseGoal -> require(action.text.isNotBlank()) { "A goal is required" }
            SessionAction.Cancel -> Unit
        }
    }

    private fun endpoint(vararg segments: String): HttpUrl = config.baseUrl.newBuilder().apply {
        segments.forEach { addPathSegment(it) }
    }.build()

    private fun String.jsonBody(): RequestBody {
        val delegate = toRequestBody("application/json; charset=utf-8".toMediaType())
        return object : RequestBody() {
            override fun contentType() = delegate.contentType()
            override fun contentLength() = delegate.contentLength()
            // OkHttp may follow 503 Retry-After:0 even with connection retries disabled.
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) = delegate.writeTo(sink)
        }
    }

    private suspend fun exchange(request: Request, success: Set<Int>): WireResponse {
        check(!closed.get()) { "API client is closed" }
        val response = suspendCancellableCoroutine<WireResponse> { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(ApiConnectionException())
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val wire = response.use {
                            val body = it.body ?: throw ApiProtocolException()
                            if (body.contentLength() > MAX_API_BYTES) throw ApiProtocolException()
                            val source = body.source()
                            source.request(MAX_API_BYTES.toLong() + 1)
                            if (source.buffer.size > MAX_API_BYTES) throw ApiProtocolException()
                            WireResponse(it.code, source.readUtf8())
                        }
                        if (continuation.isActive) continuation.resume(wire)
                    } catch (_: ApiProtocolException) {
                        if (continuation.isActive) continuation.resumeWithException(ApiProtocolException())
                    } catch (_: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(ApiConnectionException())
                    }
                }
            })
        }
        if (response.status !in success) {
            if (response.status in 200..299) throw ApiProtocolException()
            val code = try {
                apiJson.parseToJsonElement(response.body).jsonObject["error"]?.jsonObject?.get("code")?.jsonPrimitive?.content
            } catch (_: IllegalArgumentException) { null }
            throw ApiHttpException(response.status, code?.takeIf { it.matches(Regex("[a-z_]{1,64}")) } ?: "http_error")
        }
        return response
    }

    private inline fun <reified T> decode(body: String): T = try { apiJson.decodeFromString<T>(body) }
        catch (_: SerializationException) { throw ApiProtocolException() }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            http.dispatcher.cancelAll()
            http.connectionPool.evictAll()
            http.dispatcher.executorService.shutdown()
        }
    }

    private data class WireResponse(val status: Int, val body: String)
}
