package kr.ac.suwon.arsagent.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonObject

object EventCodec {
    fun decode(text: String): SessionEvent {
        if (text.length > MAX_API_BYTES) throw ApiProtocolException()
        return try {
            val wire = apiJson.decodeFromString<Envelope>(text)
            when (wire.event) {
                "snapshot" -> SessionEvent.Snapshot(wire.at, apiJson.decodeFromJsonElement(wire.data))
                "status_changed" -> SessionEvent.StatusChanged(wire.at, apiJson.decodeFromJsonElement(wire.data))
                "caption" -> SessionEvent.CaptionAdded(wire.at, apiJson.decodeFromJsonElement(wire.data))
                "path_updated" -> SessionEvent.PathUpdated(wire.at, apiJson.decodeFromJsonElement(wire.data))
                "request" -> SessionEvent.InputRequested(wire.at, apiJson.decodeFromJsonElement(wire.data))
                "input_rejected" -> SessionEvent.InputRejected(wire.at, apiJson.decodeFromJsonElement(wire.data))
                "summary" -> SessionEvent.SummaryReceived(wire.at, apiJson.decodeFromJsonElement(wire.data))
                "ended" -> SessionEvent.Ended(wire.at, apiJson.decodeFromJsonElement(wire.data))
                "error" -> SessionEvent.ErrorReceived(wire.at, apiJson.decodeFromJsonElement(wire.data))
                else -> SessionEvent.Unknown(wire.at, wire.event, wire.data)
            }
        } catch (_: SerializationException) {
            throw ApiProtocolException()
        }
    }

    @Serializable private data class Envelope(val event: String, val at: String, val data: JsonObject)
}

internal const val MAX_API_BYTES = 1_048_576
