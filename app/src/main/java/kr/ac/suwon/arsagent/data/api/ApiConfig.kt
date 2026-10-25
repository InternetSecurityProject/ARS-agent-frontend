package kr.ac.suwon.arsagent.data.api

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ArsApiConfig(baseUrl: String, val appKey: String, val allowLocalHttp: Boolean = false) {
    val baseUrl: HttpUrl

    init {
        val parsed = baseUrl.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid API base address")
        require(parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.query == null && parsed.fragment == null) {
            "API address must not contain credentials, a query, or a fragment"
        }
        require(parsed.isHttps || (allowLocalHttp && parsed.host in setOf("localhost", "127.0.0.1", "::1", "10.0.2.2"))) {
            "HTTPS is required except for explicitly enabled local testing"
        }
        require(appKey.isNotBlank() && appKey.all { it.code in 32..126 }) { "A valid app key is required" }
        val segments = parsed.pathSegments.filter { it.isNotEmpty() }
        require(segments.isEmpty() || segments == listOf("v1")) { "API base path must be empty or /v1" }
        this.baseUrl = parsed.newBuilder().encodedPath("/v1/").build()
    }

    override fun toString() = "ArsApiConfig(baseUrl=$baseUrl, appKey=[redacted])"
}
