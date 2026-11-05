package dev.pulsar.core

import kotlinx.serialization.json.Json
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

/**
 * Native Kotlin Contact & Kinship Resolver for Pulsar
 * Ported from agent_core/contact_resolver.py with:
 * 1. Accusative Morphological Stemmer (-কে, -রে, -টারে, -ের)
 * 2. Prefix Prosthetic Vowel Stripper (handles "আবসের" -> "বসের" -> "বস" vocal hesitations)
 * 3. Levenshtein edit-distance (<= 2) fuzzy matching
 * 4. Elder Kinship Graph with multi-child ambiguity resolution
 */
class ContactResolver(
    contactsJson: String,
    kinshipJson: String
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val contacts: List<Contact> = json.decodeFromString(contactsJson)
    private val kinship: KinshipGraphData = json.decodeFromString(kinshipJson)

    private val contactById = contacts.associateBy { it.id }
    private val indexedContacts = mutableListOf<IndexedContact>()

    data class IndexedContact(
        val raw: Contact,
        val cleanTokens: Set<String>,
        val romanTokens: Set<String>,
        val nickTokens: Set<String>,
        val relation: String
    )

    companion object {
        private val TOKEN_REGEX = Pattern.compile("[\\u0980-\\u09FFa-zA-Z0-9]+")

        val BN_TO_EN_NAME_MAP = mapOf(
            "তানভীর" to "tanvir", "তানভির" to "tanvir", "আহমেদ" to "ahmed",
            "রিফাত" to "rifat", "নাবিলা" to "nabila", "তাসনিম" to "tasnim",
            "ফাহিম" to "fahim", "রোকেয়া" to "rokeya", "কামরুল" to "kamrul",
            "সাদিয়া" to "sadia", "আম্মু" to "ammu", "আম্মা" to "amma",
            "মা" to "ammu", "আব্বু" to "abbu", "আব্বা" to "abba", "বাবা" to "abbu",
            "তারেক" to "tareq", "মামুন" to "mamun", "আসিফ" to "asif",
            "সোহেল" to "sohel", "রহিম" to "rahim", "মিজান" to "mizan",
            "রফিক" to "rafiq", "আল-আমিন" to "al-amin", "বাদল" to "badal",
            "কালাম" to "kalam", "রুবেল" to "rubel", "বাবুল" to "babul",
            "মানিক" to "manik", "মাহফুজ" to "mahfuz", "শামসুল" to "shamsul",
            "ফাতেমা" to "fatema", "আনোয়ার" to "anwar", "জুবায়ের" to "zubair",
            "শাকিল" to "shakil", "মনির" to "monir", "হাসান" to "hasan",
            "ফারুক" to "faruk", "সুলতানা" to "sultana", "ইমরান" to "imran",
            "আয়ান" to "aayan", "আনিকা" to "anika", "বস" to "boss"
        )

        val STOPWORDS = setOf(
            "কল", "ফোন", "দাও", "মার", "পাঠা", "মেসেজ", "দে", "তো", "একটা", "কর",
            "আমার", "ডাক", "খাড়া", "বলো", "বল", "বলিস", "মারো", "মারেন", "একটু",
            "আপনি", "তুমি", "তুই", "আপনার", "তোমার", "তোর", "লাগাও", "লাগা", "আর্জেন্ট",
            "হোয়াটসঅ্যাপে", "হোয়াটসঅ্যাপে", "whatsapp"
        )

        val ACCUSATIVE_SUFFIXES = listOf(
            "টারেরে", "গুলো", "গুলা", "টারে", "টাকে", "দের", "গো", "ের", "কে", "রে"
        )

        val KNOWN_PREFIX_STEMS = setOf(
            "বস", "বসের", "বাবা", "ভাই", "পোলা", "মেয়ে", "ডাক্তার", "আম্মু", "আব্বু"
        )

        private val BN_DIGITS = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

        fun toBanglaDigits(num: Int): String {
            return num.toString().map { if (it in '0'..'9') BN_DIGITS[it - '0'] else it }.joinToString("")
        }
    }

    init {
        buildIndex()
    }

    private fun buildIndex() {
        for (c in contacts) {
            val nameTokens = tokenize(c.name).map { cleanToken(it) }
            val romanTokens = nameTokens.mapNotNull { BN_TO_EN_NAME_MAP[it] }
            val nickTokens = c.nicknames.flatMap { tokenize(it).map { cleanToken(it) } }

            indexedContacts.add(
                IndexedContact(
                    raw = c,
                    cleanTokens = nameTokens.toSet(),
                    romanTokens = romanTokens.toSet(),
                    nickTokens = nickTokens.toSet(),
                    relation = c.relation ?: ""
                )
            )
        }
    }

    fun tokenize(text: String): List<String> {
        val matcher = TOKEN_REGEX.matcher(text)
        val tokens = mutableListOf<String>()
        while (matcher.find()) {
            tokens.add(matcher.group())
        }
        return tokens
    }

    /**
     * Accusative Morphological Suffix Stemmer
     * Strips objective markers (-কে, -রে, -টারে, -ের) preserving root consonant stems.
     */
    fun stripCaseMarkers(word: String): String {
        for (suffix in ACCUSATIVE_SUFFIXES) {
            if (word.endsWith(suffix) && word.length > suffix.length + 1) {
                return word.substring(0, word.length - suffix.length)
            }
        }
        return word
    }

    /**
     * Prefix Prosthetic Vowel Stripper + Normalizer
     * Handles leading vocal hesitation vowels e.g. "আবসের" -> "বসের" -> "বস"
     */
    fun cleanToken(token: String): String {
        val t = token.trim().lowercase()

        // 1. Strip leading hesitation vowel prefix (আ-, এ-) if root follows
        if (t.length >= 3 && (t.startsWith("আ") || t.startsWith("এ"))) {
            val strippedPrefix = t.substring(1)
            // Check if stripped prefix is a known keyword stem or starts with a known stem
            val suffixStripped = stripCaseMarkers(strippedPrefix)
            if (KNOWN_PREFIX_STEMS.contains(strippedPrefix) || KNOWN_PREFIX_STEMS.contains(suffixStripped)) {
                return suffixStripped
            }
            // Check if any indexed contact matches this stripped root
            for (contact in indexedContacts) {
                if (contact.cleanTokens.contains(strippedPrefix) || contact.cleanTokens.contains(suffixStripped)) {
                    return suffixStripped
                }
            }
        }

        return t
    }

    /**
     * Normalizes a token by stripping both prosthetic prefix and case suffix
     */
    fun normalizeToken(token: String): String {
        val strippedSuffix = stripCaseMarkers(token.trim().lowercase())
        return cleanToken(strippedSuffix)
    }

    /**
     * Calculates Levenshtein edit distance between two strings
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[m][n]
    }

    /**
     * Kinship Modifier & Role Extractor
     */
    fun extractKinshipFromQuery(query: String): Pair<String?, String?> {
        val rawTokens = tokenize(query.lowercase())
        val tokens = rawTokens.map { normalizeToken(it) }

        var modifier: String? = null
        for (t in tokens) {
            when (t) {
                "ছোট", "ছুডু", "choto", "chuto" -> { modifier = "choto"; break }
                "বড়", "বড্ডা", "boro", "bodo" -> { modifier = "boro"; break }
                "মেজো", "মেজ", "mejo" -> { modifier = "mejo"; break }
                "সেজো", "সেজ", "sejo" -> { modifier = "sejo"; break }
            }
        }

        var role: String? = null
        for (t in tokens) {
            when {
                t in setOf("ছেলে", "পোলা", "বেটা", "chele", "pola", "beta", "son") -> role = "chele"
                t in setOf("মেয়ে", "মাইয়া", "কুইড়া", "meye", "maiya", "daughter") -> role = "meye"
                t in setOf("জামাই", "দামাদ", "jamai") -> role = "jamai"
                t in setOf("বউ", "স্ত্রী", "গিন্নি", "wife", "bou") -> role = "spouse"
                t in setOf("ভাবী", "ভাবি", "বৌদি", "bhabi", "bhabhi") -> role = "sister_in_law"
                t in setOf("দুলাভাই", "dulabhai") -> role = "brother_in_law_dulabhai"
                t in setOf("শালা", "shala", "saley") -> role = "brother_in_law_sala"
                t in setOf("ভাই", "ভায়রা", "bhai", "brother") -> role = "bhai"
                t in setOf("বোন", "বুবু", "আপা", "bon", "apa", "sister") -> role = "bon"
                t in setOf("আম্মু", "আম্মা", "মা", "ammu", "amma", "maa", "mother") -> role = "mother"
                t in setOf("আব্বু", "আব্বা", "বাবা", "abbu", "abba", "baba", "father") -> role = "father"
                t in setOf("চাচা", "কাকা", "মামা", "খালা", "ফুফু", "chacha", "kaka", "mama", "khala", "fupu") -> role = "relative"
                t in setOf("ড্রাইভার", "driver") -> role = "driver"
                t in setOf("ইলেকট্রিশিয়ান", "মিস্ত্রি", "electrician", "mistri") -> role = "electrician"
                t in setOf("ডাক্তার", "doctor", "daktar", "dr") -> role = "doctor"
                t in setOf("বস", "boss", "স্যার", "sir") -> role = "supervisor"
            }
            if (role != null) break
        }

        if (modifier != null && role == null) {
            if (rawTokens.any { it in setOf("ছোটকে", "ছোটরে", "বড়কে", "বড়রে") }) {
                role = "chele"
            }
        }

        return Pair(modifier, role)
    }

    /**
     * Resolves spoken query to contact with kinship reasoning and ambiguity detection
     */
    fun resolveContact(query: String): ResolveResult {
        val (modifier, role) = extractKinshipFromQuery(query)

        // 1. Kinship Graph Handling
        if (role != null) {
            // Case A: Generic role without modifier (e.g., "আমার ছেলেকে ফোন দাও" when 3 sons exist)
            val categoryGroup = kinship.categoryGroups[role]
            if (categoryGroup != null && modifier == null) {
                val candidates = categoryGroup.mapNotNull { kinship.relations[it] }
                if (candidates.size > 1) {
                    val descList = candidates.map {
                        val label = it.banglaLabels.firstOrNull() ?: it.name
                        "$label (${it.name})"
                    }
                    val sizeBn = toBanglaDigits(candidates.size)
                    val clarification = "আপনার $sizeBn জন $role তালিকায় রয়েছে: ${descList.joinToString(", ")}। আপনি কাকে কল করতে চান?"
                    return ResolveResult(
                        status = ResolveStatus.AMBIGUOUS,
                        relationRole = role,
                        candidates = candidates,
                        clarificationMessage = clarification
                    )
                } else if (candidates.size == 1) {
                    val matchInfo = candidates[0]
                    val contact = contactById[matchInfo.contactId]
                    return ResolveResult(
                        status = ResolveStatus.RESOLVED,
                        method = "kinship_single_child",
                        contact = contact,
                        phone = matchInfo.phone,
                        confidence = 0.95f
                    )
                }
            }

            // Case B: Specific Kinship Mapping
            var relKey: String? = null
            if (modifier != null) {
                relKey = when {
                    modifier == "boro" && role == "chele" -> "elder_son"
                    modifier == "choto" && role == "chele" -> "younger_son"
                    modifier in listOf("mejo", "sejo") && role == "chele" -> "middle_son"
                    modifier == "boro" && role == "meye" -> "elder_daughter"
                    modifier == "choto" && role == "meye" -> "younger_daughter"
                    modifier == "boro" && role == "jamai" -> "son_in_law"
                    else -> null
                }
            } else if (role in setOf(
                    "spouse", "sister_in_law", "brother_in_law_dulabhai", "brother_in_law_sala",
                    "father", "mother", "driver", "electrician", "doctor", "supervisor"
                )) {
                relKey = role
            }

            if (relKey != null && kinship.relations.containsKey(relKey)) {
                val relInfo = kinship.relations[relKey]!!
                val contactEntry = contactById[relInfo.contactId]
                if (contactEntry != null) {
                    return ResolveResult(
                        status = ResolveStatus.RESOLVED,
                        method = "kinship_graph",
                        relationRole = relKey,
                        contact = contactEntry,
                        phone = relInfo.phone,
                        confidence = 0.98f
                    )
                }
            }
        }

        // 2. Direct Name or Nickname Matching
        val rawTokens = tokenize(query)
        val strippedTokens = rawTokens.map { normalizeToken(it) }
        val queryNameTokens = strippedTokens.filter { it !in STOPWORDS && it.isNotBlank() }

        if (queryNameTokens.isEmpty()) {
            return ResolveResult(
                status = ResolveStatus.NOT_FOUND,
                clarificationMessage = "কোনো নাম খুঁজে পাওয়া যায়নি।"
            )
        }

        var bestMatch: IndexedContact? = null
        var bestScore = 0.0f

        for (c in indexedContacts) {
            var score = 0.0f
            var matched = 0

            for (qt in queryNameTokens) {
                // Exact token match (Bangla or Latin)
                if (c.cleanTokens.contains(qt) || c.nickTokens.contains(qt)) {
                    matched++
                }
                // Transliterated match
                else if (BN_TO_EN_NAME_MAP.containsKey(qt)) {
                    val mappedEn = BN_TO_EN_NAME_MAP[qt]!!
                    if (c.cleanTokens.contains(mappedEn) || c.nickTokens.contains(mappedEn)) {
                        matched++
                    }
                }
                // Fuzzy match via Levenshtein (distance <= 2 for words >= 4 chars)
                else {
                    var fuzzyFound = false
                    for (token in c.cleanTokens + c.nickTokens) {
                        if (token.length >= 4 && qt.length >= 4) {
                            val dist = levenshteinDistance(token, qt)
                            if (dist <= 1 || (dist <= 2 && token.length >= 6)) {
                                matched++
                                fuzzyFound = true
                                break
                            }
                        }
                    }
                }
            }

            if (matched > 0) {
                val totalTokens = c.cleanTokens.size
                score = matched.toFloat() / max(totalTokens, queryNameTokens.size).toFloat()
                if (role != null && role == c.relation) {
                    score += 0.30f
                }
            }

            if (score > bestScore) {
                bestScore = score
                bestMatch = c
            }
        }

        if (bestMatch != null && bestScore >= 0.35f) {
            return ResolveResult(
                status = ResolveStatus.RESOLVED,
                method = "direct_name_match",
                contact = bestMatch.raw,
                phone = bestMatch.raw.phone,
                confidence = min(1.0f, bestScore + 0.30f)
            )
        }

        return ResolveResult(
            status = ResolveStatus.NOT_FOUND,
            clarificationMessage = "কোনো কন্টাক্ট খুঁজে পাওয়া যায়নি। দয়া করে নামটি আবার বলুন।"
        )
    }
}
