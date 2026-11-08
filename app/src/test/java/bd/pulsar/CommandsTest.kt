package bd.pulsar

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class CommandsTest {
    private val now = LocalDateTime.of(2026, 11, 5, 15, 30)

    private fun remind(s: String) = Commands.parse(s, now) as Command.Reminder

    @Test
    fun relativeReminder() {
        val r = remind("আধা ঘণ্টা পরে পানি খেতে মনে করিও")
        assertEquals(now.plusMinutes(30), r.at)
        assertEquals("পানি খেতে", r.title)
    }

    @Test
    fun bengaliDigitsAndTomorrow() {
        assertEquals(
            now.plusDays(1).withHour(8).withMinute(0),
            remind("কাল সকাল ৮টায় হাঁটতে মনে করিও").at,
        )
    }

    @Test
    fun unclearOrPastTimesRequireChoice() {
        assertNull(remind("আটটায় বিল দিতে মনে করিও").at)
        assertNull(remind("আজ সকাল আটটায় বিল দিতে মনে করিও").at)
        assertNull(remind("শুক্রবার সকাল আটটায় বিল দিতে মনে করিও").at)
        assertNull(remind("সকাল আটটা আর রাত দশটায় পানি খেতে মনে করিও").at)
    }

    @Test
    fun aliasAndApp() {
        assertEquals(Command.Call("আম্মু"), Commands.parse("আম্মুকে কল করো", now))
        assertEquals(
            Command.Open(normalized("ক্যালকুলেটর")),
            Commands.parse("ক্যালকুলেটর খোলো", now),
        )
        assertEquals(Command.Unknown, Commands.parse("আজ কেমন আছো?", now))
    }
}
