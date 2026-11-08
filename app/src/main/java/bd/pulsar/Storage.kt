package bd.pulsar

import android.app.*
import android.content.*
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Build
import java.time.*

internal data class Reminder(
    val id: Long,
    val title: String,
    val due: Long,
    val daily: Boolean,
    val state: String,
)

internal data class Person(val id: Long, val name: String, val number: String)

internal fun LocalDateTime.epoch() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

internal fun Long.local() =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

internal class Store(c: Context) : SQLiteOpenHelper(c.applicationContext, "pulsar.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE reminders(id INTEGER PRIMARY KEY,title TEXT NOT NULL,due INTEGER NOT NULL,daily INTEGER NOT NULL,state TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE people(id INTEGER PRIMARY KEY,name TEXT NOT NULL,number TEXT NOT NULL, UNIQUE(name,number))"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit

    fun reminders(): List<Reminder> =
        readableDatabase.rawQuery("SELECT * FROM reminders ORDER BY due", null).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    Reminder(
                        c.getLong(0),
                        c.getString(1),
                        c.getLong(2),
                        c.getInt(3) != 0,
                        c.getString(4),
                    )
                )
            }
        }

    fun people(): List<Person> =
        readableDatabase.rawQuery("SELECT * FROM people ORDER BY name", null).use { c ->
            buildList {
                while (c.moveToNext()) add(Person(c.getLong(0), c.getString(1), c.getString(2)))
            }
        }

    fun favourite(name: String, number: String) =
        writableDatabase.insertWithOnConflict(
            "people",
            null,
            ContentValues().apply {
                put("name", name)
                put("number", number)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )

    fun removePerson(id: Long) = writableDatabase.delete("people", "id=?", arrayOf(id.toString()))

    fun save(id: Long?, title: String, due: Long, daily: Boolean): Long {
        val v =
            ContentValues().apply {
                put("title", title)
                put("due", due)
                put("daily", if (daily) 1 else 0)
                put("state", "pending")
            }
        if (id == null) return writableDatabase.insertOrThrow("reminders", null, v)
        require(writableDatabase.update("reminders", v, "id=?", arrayOf(id.toString())) == 1)
        return id
    }

    fun state(id: Long, value: String) =
        writableDatabase.update(
            "reminders",
            ContentValues().apply { put("state", value) },
            "id=?",
            arrayOf(id.toString()),
        )

    fun delete(id: Long) = writableDatabase.delete("reminders", "id=?", arrayOf(id.toString()))

    fun delivered(r: Reminder): Reminder? {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val changed =
                db.update(
                    "reminders",
                    ContentValues().apply {
                        put("state", "fired")
                        put("daily", 0)
                    },
                    "id=? AND due=? AND state!='fired'",
                    arrayOf(r.id.toString(), r.due.toString()),
                )
            val next =
                if (changed == 1 && r.daily) {
                    val due = Alarms.nextDay(r.due)
                    Reminder(save(null, r.title, due, true), r.title, due, true, "pending")
                } else null
            db.setTransactionSuccessful()
            return next
        } finally {
            db.endTransaction()
        }
    }
}

internal object Alarms {
    const val CHANNEL = "reminders"

    private fun manager(c: Context) = c.getSystemService(AlarmManager::class.java)

    fun exact(c: Context) = Build.VERSION.SDK_INT < 31 || manager(c).canScheduleExactAlarms()

    fun notifications(c: Context): Boolean {
        val nm = c.getSystemService(NotificationManager::class.java)
        return nm.areNotificationsEnabled() &&
            nm.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun channel(c: Context) =
        c.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(CHANNEL, "রিমাইন্ডার", NotificationManager.IMPORTANCE_HIGH)
            )

    private fun intent(c: Context, id: Long, due: Long) =
        PendingIntent.getBroadcast(
            c,
            0,
            Intent(c, ReminderReceiver::class.java)
                .setData(Uri.parse("pulsar://reminder/" + id))
                .putExtra("due", due),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(c: Context, db: Store, r: Reminder) {
        if (!exact(c) || !notifications(c)) {
            db.state(r.id, "blocked")
            return
        }
        try {
            manager(c)
                .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.due, intent(c, r.id, r.due))
            db.state(r.id, "scheduled")
        } catch (_: SecurityException) {
            db.state(r.id, "blocked")
        }
    }

    fun cancel(c: Context, id: Long) {
        manager(c).cancel(intent(c, id, 0))
        c.getSystemService(NotificationManager::class.java).cancel(id.toInt())
    }

    fun nextDay(due: Long): Long {
        val now = LocalDateTime.now()
        val time = due.local().toLocalTime()
        val today = now.toLocalDate().atTime(time)
        return (if (today > now) today else today.plusDays(1)).epoch()
    }

    fun restore(c: Context, db: Store) {
        channel(c)
        db.reminders().filter { it.state != "fired" }.forEach { schedule(c, db, it) }
    }
}

class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Store(c).use { Alarms.restore(c, it) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.data?.lastPathSegment?.toLongOrNull() ?: return
        Store(c).use { db ->
            val r =
                db.reminders().firstOrNull { it.id == id && it.due == i.getLongExtra("due", -1) }
                    ?: return
            if (i.action == "bd.pulsar.DONE" || i.action == "bd.pulsar.SNOOZE") {
                if (r.state != "fired") return
                Alarms.cancel(c, id)
                if (i.action == "bd.pulsar.DONE") db.delete(id)
                else {
                    val due = System.currentTimeMillis() + 600_000L
                    db.save(id, r.title, due, false)
                    Alarms.schedule(c, db, r.copy(due = due, daily = false))
                }
                return
            }
            if (r.state == "fired") return
            if (!Alarms.notifications(c)) {
                db.state(id, "blocked")
                return
            }
            val launch =
                PendingIntent.getActivity(
                    c,
                    id.toInt(),
                    Intent(c, MainActivity::class.java)
                        .putExtra("reminder", id)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            fun action(key: String) =
                PendingIntent.getBroadcast(
                    c,
                    0,
                    Intent(c, ReminderReceiver::class.java)
                        .setAction(key)
                        .setData(i.data)
                        .putExtra("due", r.due),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val note =
                Notification.Builder(c, Alarms.CHANNEL)
                    .setSmallIcon(R.drawable.ic_alarm)
                    .setContentTitle("সময় হয়েছে")
                    .setContentText(r.title)
                    .addAction(
                        Notification.Action.Builder(null, "হয়ে গেছে", action("bd.pulsar.DONE"))
                            .build()
                    )
                    .addAction(
                        Notification.Action.Builder(
                                null,
                                "১০ মিনিট পরে",
                                action("bd.pulsar.SNOOZE"),
                            )
                            .build()
                    )
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setContentIntent(launch)
                    .setAutoCancel(true)
                    .build()
            c.getSystemService(NotificationManager::class.java).notify(id.toInt(), note)
            db.delivered(r)?.let { Alarms.schedule(c, db, it) }
        }
    }
}
