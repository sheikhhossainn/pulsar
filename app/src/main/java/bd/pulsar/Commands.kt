package bd.pulsar

import java.text.Normalizer
import java.time.LocalDateTime
import java.time.LocalTime

internal sealed interface Command {
    data class Reminder(val title: String, val at: LocalDateTime?, val daily: Boolean) : Command

    data class Call(val name: String) : Command

    data class Open(val name: String) : Command

    data object ListReminders : Command

    data object Help : Command

    data object Unknown : Command
}

internal fun latinDigits(s: String) =
    s.map { if (it in '০'..'৯') '0' + (it - '০') else it }.joinToString("")

internal fun bnDigits(s: String) =
    s.map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("")

internal fun normalized(s: String) =
    Normalizer.normalize(s, Normalizer.Form.NFC)
        .lowercase()
        .replace(Regex("[।?!,‌‍]"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

internal object Commands {
    private val words =
        listOf("এক", "দুই", "তিন", "চার", "পাঁচ", "ছয়", "সাত", "আট", "নয়", "দশ", "এগারো", "বারো")
    private val relative = Regex("(আধা|[0-9]+)\\s*(মিনিট|ঘণ্টা|ঘন্টা)\\s*(?:পর(?:ে)?)")
    private val clock =
        Regex(
            "(?:(সকাল|রাত|বিকাল|বিকেল|দুপুর|সন্ধ্যা)\\s*)?([0-9]{1,2}|" +
                words.joinToString("|") +
                ")(?::([0-9]{2}))?\\s*(?:টা(?:য়|য়)?|টায়|টায়)"
        )

    fun parse(raw: String, now: LocalDateTime): Command {
        val s = normalized(latinDigits(raw))
        if (s in listOf("সাহায্য", "কী বলতে পারি", "কি বলতে পারি", "help")) return Command.Help
        if (
            s.contains("রিমাইন্ডার") &&
                listOf("দেখাও", "তালিকা", "কী আছে", "কি আছে").any { it in s }
        )
            return Command.ListReminders
        if (listOf("কল কর", "ফোন কর").any { it in s }) {
            val name =
                s.substringBefore("কল কর")
                    .substringBefore("ফোন কর")
                    .trim()
                    .removeSuffix("কে")
                    .trim()
            return Command.Call(name)
        }
        val open = listOf("খোলো", "খুলে দাও", "খুলুন", "খোল")
        open
            .firstOrNull { s.endsWith(it) }
            ?.let {
                return Command.Open(s.removeSuffix(it).trim())
            }
        if (listOf("মনে কর", "মনে করিয়ে", "রিমাইন্ডার").none { it in s }) return Command.Unknown
        var title = s.substringBefore("মনে কর").substringBefore("রিমাইন্ডার")
        val daily = "প্রতিদিন" in s || "রোজ" in s
        val rel = relative.find(s)
        val time = clock.find(s)
        var at: LocalDateTime? = null
        if (rel != null) {
            val unit = rel.groupValues[2]
            val amount = rel.groupValues[1]
            val minutes =
                if (amount == "আধা" && unit != "মিনিট") 30L
                else
                    amount
                        .toLongOrNull()
                        ?.takeIf { it in 1..10000 }
                        ?.times(if (unit == "মিনিট") 1 else 60)
            if (minutes != null && !daily) at = now.plusMinutes(minutes).withNano(0)
            title = title.replace(rel.value, "")
        } else if (time != null) {
            val part = time.groupValues[1]
            val h = time.groupValues[2].toIntOrNull() ?: (words.indexOf(time.groupValues[2]) + 1)
            val m = time.groupValues[3].toIntOrNull() ?: 0
            if (h in 1..12 && m in 0..59 && part.isNotEmpty() && clock.findAll(s).count() == 1) {
                val hour =
                    when (part) {
                        "সকাল" -> h % 12
                        "রাত" -> if (h in 6..11) h + 12 else h % 12
                        else -> h % 12 + 12
                    }
                val offset =
                    when {
                        "পরশু" in s -> 2L
                        "আগামীকাল" in s || Regex("(^| )কাল( |$)").containsMatchIn(s) -> 1L
                        else -> 0L
                    }
                val candidate = now.toLocalDate().plusDays(offset).atTime(LocalTime.of(hour, m))
                if (candidate.isAfter(now)) at = candidate
            }
            title = title.replace(time.value, "")
        }
        // A partial interpretation must never silently drop a weekday, interval or second time.
        if (
            listOf(
                    "বার ",
                    "বারে",
                    "প্রতি সপ্তাহ",
                    "দিন ধরে",
                    "সাড়ে",
                    "সাড়ে",
                    "পৌনে",
                    "সোয়া",
                    "সোয়া",
                )
                .any { it in s } || clock.findAll(s).count() > 1
        )
            at = null
        title =
            normalized(
                    title.replace(
                        Regex("আগামীকাল|পরশু|প্রতিদিন|রোজ|(^| )কাল(?= |$)|(^| )আজ(?= |$)"),
                        " ",
                    )
                )
                .removeSuffix(" কথা")
                .removeSuffix("র কথা")
                .trim()
        return Command.Reminder(title, at, daily)
    }
}
