package dev.pulsar.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class Contact(
    val id: String,
    val name: String,
    val phone: String,
    val nicknames: List<String> = emptyList(),
    val relation: String? = null
)

@Serializable
data class KinshipCandidate(
    @SerialName("contact_id") val contactId: String,
    val name: String,
    val phone: String,
    @SerialName("bangla_labels") val banglaLabels: List<String> = emptyList()
)

@Serializable
data class KinshipGraphData(
    val relations: Map<String, KinshipCandidate> = emptyMap(),
    @SerialName("category_groups") val categoryGroups: Map<String, List<String>> = emptyMap()
)

data class ResolveResult(
    val status: ResolveStatus,
    val method: String? = null,
    val contact: Contact? = null,
    val phone: String? = null,
    val confidence: Float = 0.0f,
    val relationRole: String? = null,
    val clarificationMessage: String? = null,
    val candidates: List<KinshipCandidate> = emptyList()
)

enum class ResolveStatus {
    RESOLVED,
    AMBIGUOUS,
    NOT_FOUND
}

data class FileItem(
    val path: String,
    val relPath: String,
    val filename: String,
    val ext: String,
    val mtime: Long,
    val ageMinutes: Double,
    val size: Long,
    val sender: String = "",
    val tags: List<String> = emptyList(),
    val searchBlob: String = ""
)

data class PinpointResult(
    val status: String,
    val file: FileItem? = null,
    val confidence: Float = 0.0f,
    val matchedReasons: List<String> = emptyList(),
    val alternatives: List<FileItem> = emptyList(),
    val message: String? = null
)
