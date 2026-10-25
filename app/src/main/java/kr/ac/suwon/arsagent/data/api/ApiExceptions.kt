package kr.ac.suwon.arsagent.data.api

import java.io.IOException

/** No response bodies, input values, tokenized URLs, or underlying URL-bearing causes. */
class ApiHttpException(val status: Int, val code: String) : IOException("ARS API request failed (HTTP $status)")
class ApiProtocolException : IOException("ARS API response does not match the contract")
class ApiConnectionException : IOException("ARS API connection failed; request delivery may be uncertain")
class ApiStreamException(val closeCode: Int? = null) : IOException("ARS event stream interrupted; reconnect for a fresh snapshot")
