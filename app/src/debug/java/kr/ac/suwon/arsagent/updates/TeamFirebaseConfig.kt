package kr.ac.suwon.arsagent.updates

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Selected public Android client fields only; never an administrator credential. */
@Serializable
data class TeamFirebaseConfig(
    val application_id: String, val api_key: String, val project_id: String,
    val sender_id: String, val package_name: String,
) {
    companion object {
        fun parse(text: String, packageName: String): TeamFirebaseConfig? = runCatching {
            Json.decodeFromString<TeamFirebaseConfig>(text).takeIf {
                it.package_name == packageName && it.api_key.isNotBlank() && it.project_id.isNotBlank() &&
                    it.sender_id.matches(Regex("[0-9]+")) &&
                    it.application_id.matches(Regex("1:${it.sender_id}:android:[a-f0-9]+"))
            }
        }.getOrNull()
    }
}
