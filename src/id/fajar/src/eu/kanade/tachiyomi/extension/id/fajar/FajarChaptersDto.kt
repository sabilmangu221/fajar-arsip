package eu.kanade.tachiyomi.extension.id.fajar

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class FajarChaptersDto(
    val total: Int = 0,
    val chapters: List<List<JsonElement>> = emptyList(),
)
