package dev.pulsar.core

import java.io.File
import java.util.regex.Pattern
import kotlin.math.max

/**
 * Native Kotlin Smart File Pinpointing Engine for Pulsar
 * Ported from agent_core/file_pinpointer.py with:
 * 1. Multi-facet indexing: Channel, Media Type, Temporal Recency, Semantic Concepts
 * 2. Bangla morphology suffix stripper for accurate facet token extraction
 * 3. Scoped Storage / Local Mock Index query support
 */
class FilePinpointer(
    private val storageRoot: File? = null,
    initialFiles: List<FileItem> = emptyList()
) {
    private val filesIndex = mutableListOf<FileItem>()

    companion object {
        private val TOKEN_REGEX = Pattern.compile("[\\u0980-\\u09FFa-zA-Z0-9]+")

        val SEMANTIC_STEMS = mapOf(
            "invoice" to listOf("invoice", "bill", "memo", "বিল", "ইনভয়েস", "ইনভয়েস", "চালান", "রিসিপ্ট"),
            "bill" to listOf("bill", "electricity", "desco", "বিল", "বিদ্যুৎ", "কারেন্ট"),
            "prescription" to listOf("prescription", "doctor", "hospital", "rx", "প্রেসক্রিপশন", "প্রেস্ক্রিপশন", "ডাক্তার", "ওষুধ", "হাসপাতাল"),
            "nid" to listOf("nid", "smart_nid", "identity", "ভোট", "এনআইডি", "জাতীয়_পরিচয়পত্র", "ভোটার"),
            "ticket" to listOf("ticket", "biman", "flight", "টিকেট", "টিকিট", "বিমান"),
            "meter" to listOf("meter", "reading", "মিটার"),
            "meeting" to listOf("meeting", "conference", "মিটিং", "বৈঠক"),
            "boss" to listOf("boss", "tareq", "বস", "অফিস", "তারেক"),
            "son" to listOf("son", "rifat", "tanvir", "ছেলে", "পোলা", "রিফাত", "তানভীর"),
            "daughter" to listOf("daughter", "nabila", "tasnim", "মেয়ে", "মাইয়া", "নাবিলা", "তাসনিম")
        )

        val IMAGE_EXTS = setOf(".jpg", ".jpeg", ".png", ".webp")
        val DOC_EXTS = setOf(".pdf", ".docx", ".doc", ".txt")
        val AUDIO_EXTS = setOf(".m4a", ".mp3", ".opus", ".wav", ".aac")

        val SUFFIXES = listOf(
            "টারেরে", "গুলো", "গুলা", "টারে", "টাকে", "দের", "গো",
            "টা", "টি", "কে", "রে", "তে", "ের", "ায়", "র", "ে", "য়"
        )
    }

    init {
        if (initialFiles.isNotEmpty()) {
            filesIndex.addAll(initialFiles)
        } else if (storageRoot != null && storageRoot.exists()) {
            refreshIndex()
        }
    }

    fun setFiles(files: List<FileItem>) {
        filesIndex.clear()
        filesIndex.addAll(files)
    }

    fun refreshIndex() {
        filesIndex.clear()
        if (storageRoot == null || !storageRoot.exists()) return

        val nowMs = System.currentTimeMillis()
        storageRoot.walkTopDown().forEach { file ->
            if (file.isFile) {
                val ageMs = max(0L, nowMs - file.lastModified())
                val ageMinutes = ageMs / (1000.0 * 60.0)
                val ext = "." + file.extension.lowercase()
                val relPath = file.relativeTo(storageRoot).path.replace('\\', '/')

                var sender = ""
                val tags = mutableListOf<String>()

                // Optional header sniffing for test/mock files
                try {
                    file.useLines { lines ->
                        lines.take(5).forEach { line ->
                            if (line.startsWith("Sender:")) {
                                sender = line.substringAfter("Sender:").trim()
                            } else if (line.startsWith("Tags:")) {
                                tags.addAll(
                                    line.substringAfter("Tags:")
                                        .split(",")
                                        .map { it.trim().lowercase() }
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}

                val searchBlob = "${file.name} $relPath $sender ${tags.joinToString(" ")}".lowercase()

                filesIndex.add(
                    FileItem(
                        path = file.absolutePath,
                        relPath = relPath,
                        filename = file.name,
                        ext = ext,
                        mtime = file.lastModified(),
                        ageMinutes = ageMinutes,
                        size = file.length(),
                        sender = sender,
                        tags = tags,
                        searchBlob = searchBlob
                    )
                )
            }
        }
    }

    fun tokenize(text: String): List<String> {
        val matcher = TOKEN_REGEX.matcher(text.lowercase())
        val tokens = mutableListOf<String>()
        while (matcher.find()) {
            tokens.add(matcher.group())
        }
        return tokens
    }

    fun stripSuffixes(word: String): String {
        for (suffix in SUFFIXES) {
            if (word.endsWith(suffix) && word.length > suffix.length + 1) {
                return word.substring(0, word.length - suffix.length)
            }
        }
        return word
    }

    data class FacetConstraints(
        val channel: String?,
        val mediaType: String?,
        val temporal: String?,
        val semanticStems: List<String>,
        val allTokens: Set<String>
    )

    fun extractFacets(query: String): FacetConstraints {
        val rawTokens = tokenize(query)
        val stripped = rawTokens.map { stripSuffixes(it) }
        val allTokens = (rawTokens + stripped).toSet()

        // 1. Channel
        val channel = when {
            allTokens.any { it in setOf("হোয়াটসঅ্যাপ", "হোয়াটসঅ্যাপে", "whatsapp", "হোয়াটসঅ্যাপ", "হোয়াটসএপ") } -> "whatsapp"
            allTokens.any { it in setOf("ক্যামেরা", "ক্যামেরায়", "camera") } -> "camera"
            allTokens.any { it in setOf("ডাউনলোড", "download", "নামাইছি") } -> "download"
            allTokens.any { it in setOf("রেকর্ডিং", "রেকর্ড", "recording", "ভয়েস") } -> "recordings"
            else -> null
        }

        // 2. Media Type
        val mediaType = when {
            allTokens.any { it in setOf("ছবি", "ফটো", "পিকচার", "image", "photo", "pic") } -> "image"
            allTokens.any { it in setOf("পিডিএফ", "কাগজ", "ডকুমেন্ট", "pdf", "document", "doc") } -> "document"
            allTokens.any { it in setOf("ভয়েস", "রেকর্ডিং", "গান", "voice", "audio") } -> "audio"
            else -> null
        }

        // 3. Temporal
        val temporal = when {
            allTokens.any { it in setOf("লাস্টের", "শেষের", "সর্বশেষ", "লাস্ট", "নতুন", "latest", "recent") } -> "latest"
            allTokens.any { it in setOf("আজকের", "আজকে", "today") } -> "today"
            allTokens.any { it in setOf("গতকালকের", "গতকালের", "গতকাল", "yesterday") } -> "yesterday"
            allTokens.any { it in setOf("সপ্তাহের", "week") } -> "this_week"
            else -> null
        }

        // 4. Semantic Stems
        val matchedStems = mutableSetOf<String>()
        for ((stem, keywords) in SEMANTIC_STEMS) {
            if (keywords.any { it in allTokens }) {
                matchedStems.add(stem)
            }
        }

        return FacetConstraints(channel, mediaType, temporal, matchedStems.toList(), allTokens)
    }

    fun pinpointFile(query: String): PinpointResult {
        val facets = extractFacets(query)
        val channel = facets.channel
        val mediaType = facets.mediaType
        val temporal = facets.temporal
        val stems = facets.semanticStems

        val scoredCandidates = mutableListOf<Pair<FileItem, Float>>()
        val candidateReasons = mutableMapOf<FileItem, List<String>>()

        for (f in filesIndex) {
            var score = 0.0f
            val reasons = mutableListOf<String>()

            // 1. Channel Filter
            val relLower = f.relPath.lowercase()
            if (channel != null) {
                if (channel == "whatsapp" && relLower.contains("whatsapp")) {
                    score += 0.35f
                    reasons.add("channel:whatsapp")
                } else if (channel == "camera" && relLower.contains("camera")) {
                    score += 0.35f
                    reasons.add("channel:camera")
                } else if (channel == "download" && relLower.contains("download")) {
                    score += 0.35f
                    reasons.add("channel:download")
                } else if (channel == "recordings" && relLower.contains("recordings")) {
                    score += 0.35f
                    reasons.add("channel:recordings")
                } else {
                    score -= 0.25f
                }
            }

            // 2. Media Type Filter (Strict skip on explicit mismatch)
            when (mediaType) {
                "image" -> {
                    if (f.ext in IMAGE_EXTS) {
                        score += 0.25f
                        reasons.add("type:image")
                    } else continue
                }
                "document" -> {
                    if (f.ext in DOC_EXTS) {
                        score += 0.25f
                        reasons.add("type:document")
                    } else continue
                }
                "audio" -> {
                    if (f.ext in AUDIO_EXTS) {
                        score += 0.25f
                        reasons.add("type:audio")
                    } else continue
                }
            }

            // 3. Temporal Filter
            val ageMin = f.ageMinutes
            when (temporal) {
                "latest" -> {
                    val recencyBoost = max(0.0, 0.40 - (ageMin / 1440.0) * 0.20).toFloat()
                    score += recencyBoost
                    reasons.add("temporal:latest")
                }
                "today" -> {
                    if (ageMin <= 1440) {
                        score += 0.30f
                        reasons.add("temporal:today")
                    } else {
                        score -= 0.20f
                    }
                }
                "yesterday" -> {
                    if (ageMin in 1200.0..2880.0) {
                        score += 0.35f
                        reasons.add("temporal:yesterday")
                    } else {
                        score -= 0.15f
                    }
                }
                else -> {
                    score += max(0.0, 0.08 - (ageMin / 5000.0) * 0.04).toFloat()
                }
            }

            // 4. Semantic Stems Filter
            var matchedStemCount = 0
            for (stem in stems) {
                val keywords = SEMANTIC_STEMS[stem] ?: emptyList()
                val hit = keywords.any { kw ->
                    f.searchBlob.contains(kw) || f.tags.contains(kw)
                }
                if (hit) {
                    matchedStemCount++
                    reasons.add("semantic:$stem")
                }
            }

            if (stems.isNotEmpty()) {
                if (matchedStemCount > 0) {
                    score += 0.45f * (matchedStemCount.toFloat() / stems.size.toFloat())
                } else {
                    score -= 0.20f
                }
            }

            if (score > 0.15f) {
                scoredCandidates.add(Pair(f, score))
                candidateReasons[f] = reasons
            }
        }

        if (scoredCandidates.isEmpty()) {
            return PinpointResult(
                status = "NOT_FOUND",
                message = "কোনো ফাইল খুঁজে পাওয়া যায়নি।"
            )
        }

        scoredCandidates.sortByDescending { it.second }
        val best = scoredCandidates.first()

        return PinpointResult(
            status = "FOUND",
            file = best.first,
            confidence = kotlin.math.min(1.0f, best.second),
            matchedReasons = candidateReasons[best.first] ?: emptyList(),
            alternatives = scoredCandidates.drop(1).take(3).map { it.first }
        )
    }
}
