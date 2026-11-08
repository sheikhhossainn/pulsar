package bd.pulsar

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val bangla = Locale.forLanguageTag("bn-BD")

internal fun LocalDateTime.whenText(): String =
    format(DateTimeFormatter.ofPattern("d MMM, h:mm a", bangla)).let(::bnDigits)

internal data class AppEntry(val label: String, val pkg: String)

private sealed interface Sheet {
    data object Talk : Sheet

    data object Help : Sheet

    data object Apps : Sheet

    data object AddPerson : Sheet

    data class RemovePerson(val person: Person) : Sheet

    data class Edit(
        val id: Long? = null,
        val title: String = "",
        val at: LocalDateTime? = null,
        val daily: Boolean = false,
    ) : Sheet

    data class Detail(val id: Long) : Sheet

    data class Remove(val id: Long, val title: String) : Sheet

    data class Call(val person: Person) : Sheet
}

class MainActivity : ComponentActivity() {
    private lateinit var db: Store
    private lateinit var voice: Voice
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var page by mutableStateOf("home")
    private var sheet by mutableStateOf<Sheet?>(null)
    private var reminders by mutableStateOf(emptyList<Reminder>())
    private var people by mutableStateOf(emptyList<Person>())
    private var apps by mutableStateOf(emptyList<AppEntry>())
    private var speechInput by mutableStateOf("")
    private var speechStatus by mutableStateOf("")
    private var listening by mutableStateOf(false)
    private var appFilter by mutableStateOf("")
    private var notice by mutableStateOf("")
    private var permissionCall: Person? = null
    private var spokenRequest = false
    private var resumed by mutableStateOf(false)
    private var picked by mutableStateOf<Pair<String, String>?>(null)
    private val contactPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == RESULT_OK && uri != null && sheet == Sheet.AddPerson) {
                picked =
                    runCatching {
                            contentResolver
                                .query(
                                    uri,
                                    arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
                                    null,
                                    null,
                                    null,
                                )
                                ?.use {
                                    if (it.moveToFirst())
                                        it.getString(0).orEmpty() to it.getString(1).orEmpty()
                                    else null
                                }
                        }
                        .getOrNull()
                if (picked == null) notice = "পরিচিতি পড়া যায়নি। নাম ও নম্বর লিখুন।"
            }
        }
    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && sheet == Sheet.Talk) beginListening()
            else {
                speechStatus = "মাইক্রোফোনের অনুমতি দিন অথবা লিখুন।"
                listening = false
            }
        }
    private val notificationsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            Alarms.restore(this, db)
            reload()
        }
    private val callerPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val person = permissionCall
            permissionCall = null
            if (person != null && sheet == Sheet.Call(person)) dispatchCall(person, granted)
        }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        enableEdgeToEdge()
        db = Store(this)
        voice = Voice(applicationContext)
        Alarms.channel(this)
        tts =
            TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val local =
                        tts?.voices?.firstOrNull {
                            it.locale.language == "bn" && !it.isNetworkConnectionRequired
                        }
                    if (local != null) {
                        tts?.voice = local
                        ttsReady = true
                    }
                }
            }
        openNotification(intent)
        setContent {
            MaterialTheme(
                colorScheme =
                    lightColorScheme(
                        primary = Teal,
                        onPrimary = Color.White,
                        background = Paper,
                        surface = Paper,
                        onSurface = Ink,
                        secondary = Quiet,
                    )
            ) {
                AppScreen()
            }
        }
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        setIntent(i)
        openNotification(i)
    }

    private fun openNotification(i: Intent) {
        if (i.hasExtra("reminder")) {
            page = "reminders"
            sheet = Sheet.Detail(i.getLongExtra("reminder", -1))
            i.removeExtra("reminder")
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        Alarms.restore(this, db)
        reload()
    }

    override fun onPause() {
        resumed = false
        spokenRequest = false
        voice.stop()
        listening = false
        tts?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        voice.close()
        tts?.shutdown()
        db.close()
        super.onDestroy()
    }

    private fun reload() {
        reminders = db.reminders()
        people = db.people()
    }

    private fun show(s: Sheet?) {
        voice.stop()
        tts?.stop()
        listening = false
        spokenRequest = false
        sheet = s
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "prompt")
    }

    private fun talk() {
        speechInput = ""
        speechStatus = ""
        show(Sheet.Talk)
        requestListen()
    }

    private fun requestListen() {
        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
            beginListening()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun beginListening() {
        speechStatus = "মাইক্রোফোন প্রস্তুত হচ্ছে…"
        listening = true
        tts?.stop()
        voice.listen(
            { speechStatus = "শুনছি… এখন বলুন" },
            { speechInput = it },
            { text ->
                listening = false
                speechInput = text
                if (text.isBlank()) speechStatus = "শুনতে পাইনি। আবার বলুন অথবা লিখুন।"
                else interpret(text, true)
            },
            {
                listening = false
                speechStatus = "মাইক্রোফোন চালু করা যায়নি। আবার চেষ্টা করুন অথবা লিখুন।"
            },
        )
    }

    private fun interpret(text: String, fromVoice: Boolean = false) {
        voice.stop()
        listening = false
        spokenRequest = false
        when (val cmd = Commands.parse(text, LocalDateTime.now())) {
            is Command.Reminder -> {
                show(Sheet.Edit(title = cmd.title, at = cmd.at, daily = cmd.daily))
                spokenRequest = fromVoice && cmd.at != null
                if (cmd.at == null) speak("তারিখ ও সময় বেছে নিন")
            }
            is Command.Call -> {
                val matches = people.filter { normalized(it.name) == normalized(cmd.name) }
                if (matches.size == 1) {
                    show(Sheet.Call(matches[0]))
                    spokenRequest = fromVoice
                } else {
                    show(null)
                    page = "people"
                    notice =
                        if (matches.isEmpty()) "পরিচিত নাম বেছে নিন অথবা যোগ করুন।"
                        else "একই নামে একাধিক নম্বর আছে। বেছে নিন।"
                }
            }
            is Command.Open -> {
                val names =
                    when (normalized(cmd.name)) {
                        "ক্যালকুলেটর" -> listOf("ক্যালকুলেটর", "calculator")
                        "ক্যামেরা" -> listOf("ক্যামেরা", "camera")
                        else -> listOf(normalized(cmd.name))
                    }
                val matches = apps.filter { normalized(it.label) in names }
                if (matches.size == 1) launchApp(matches[0])
                else {
                    appFilter = ""
                    show(Sheet.Apps)
                }
            }
            Command.ListReminders -> {
                show(null)
                page = "reminders"
            }
            Command.Help -> show(Sheet.Help)
            Command.Unknown -> {
                speechStatus = "বুঝতে পারিনি। যেমন বলুন: আধা ঘণ্টা পরে পানি খেতে মনে করিও।"
                speak("আবার বলুন")
            }
        }
    }

    private fun launchApp(app: AppEntry) {
        val i = packageManager.getLaunchIntentForPackage(app.pkg)
        if (i != null && runCatching { startActivity(i) }.isSuccess) show(null)
        else notice = "অ্যাপটি খোলা যায়নি।"
    }

    private fun confirmCall(person: Person) {
        if (
            checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        )
            dispatchCall(person, true)
        else {
            permissionCall = person
            callerPermission.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun dispatchCall(person: Person, direct: Boolean) {
        show(null)
        val i =
            Intent(
                if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL,
                Uri.fromParts("tel", person.number, null),
            )
        if (runCatching { startActivity(i) }.isFailure) notice = "ফোন অ্যাপটি খোলা যায়নি।"
    }

    private fun save(edit: Sheet.Edit, title: String, at: LocalDateTime, daily: Boolean) {
        val id = db.save(edit.id, title.trim(), at.epoch(), daily)
        Alarms.schedule(this, db, Reminder(id, title, at.epoch(), daily, "pending"))
        reload()
        show(null)
        page = "reminders"
        notice =
            if (reminders.any { it.id == id && it.state == "blocked" })
                "সঠিক সময়ে জানানোর জন্য অনুমতি দিন।"
            else "রিমাইন্ডার রাখা হয়েছে"
        if (
            Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        )
            notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun repair() {
        val i =
            if (!Alarms.notifications(this))
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            else if (Build.VERSION.SDK_INT >= 31 && !Alarms.exact(this))
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + packageName),
                )
            else null
        if (i != null)
            runCatching { startActivity(i) }
                .onFailure { notice = "সেটিংস থেকে রিমাইন্ডারের অনুমতি দিন।" }
        else {
            Alarms.restore(this, db)
            reload()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AppScreen() {
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(notice) {
            if (notice.isNotEmpty()) {
                val message = notice
                snack.currentSnackbarData?.dismiss()
                snack.showSnackbar(message)
                if (notice == message) notice = ""
            }
        }
        LaunchedEffect(Unit) {
            apps =
                withContext(Dispatchers.IO) {
                    packageManager
                        .queryIntentActivities(
                            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                            0,
                        )
                        .filter { it.activityInfo.packageName != packageName }
                        .map {
                            AppEntry(
                                it.loadLabel(packageManager).toString(),
                                it.activityInfo.packageName,
                            )
                        }
                        .distinctBy { it.pkg }
                        .sortedBy { it.label.lowercase() }
                }
        }
        LaunchedEffect(page, sheet) {
            while (true) {
                reload()
                kotlinx.coroutines.delay(15_000)
            }
        }
        BackHandler(enabled = sheet == null && page != "home") { page = "home" }
        Scaffold(
            containerColor = Paper,
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                    listOf(
                            Triple("home", "home", "হোম"),
                            Triple("reminders", "clock", "রিমাইন্ডার"),
                            Triple("people", "person", "পরিচিতি"),
                        )
                        .forEach { (key, icon, label) ->
                            NavigationBarItem(
                                selected = page == key,
                                onClick = { page = key },
                                icon = { Glyph(icon, 22.dp, if (page == key) Teal else Quiet) },
                                label = { Text(label, fontSize = 13.sp) },
                                colors =
                                    NavigationBarItemDefaults.colors(
                                        indicatorColor = Mist,
                                        selectedTextColor = Teal,
                                        unselectedTextColor = Quiet,
                                    ),
                            )
                        }
                }
            },
        ) { pad ->
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding =
                    PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(34.dp).background(Teal, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Glyph("wave", 23.dp, Leaf)
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(
                            "Pulsar",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink,
                            letterSpacing = (-1).sp,
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { show(Sheet.Help) },
                            modifier =
                                Modifier.semantics { contentDescription = "ব্যবহারের সাহায্য" },
                        ) {
                            Glyph("help", 24.dp, Quiet)
                        }
                    }
                }
                if (page == "home") {
                    item {
                        Column {
                            Text(
                                LocalDate.now()
                                    .format(DateTimeFormatter.ofPattern("EEEE, d MMMM", bangla))
                                    .let(::bnDigits),
                                color = Quiet,
                                fontSize = 15.sp,
                            )
                            Text(
                                "আজ কী করতে চান?",
                                fontSize = 32.sp,
                                lineHeight = 44.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink,
                            )
                        }
                    }
                    item { TalkHero(::talk) }
                    item { Heading("দ্রুত কাজ") }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionTile(
                                "clock",
                                "মনে করান",
                                Color(0xFFFFE9C9),
                                Modifier.weight(1f),
                            ) {
                                show(Sheet.Edit())
                            }
                            ActionTile("phone", "কল করুন", Mist, Modifier.weight(1f)) {
                                page = "people"
                            }
                            ActionTile(
                                "apps",
                                "অ্যাপ খুলুন",
                                Color(0xFFE8E5F7),
                                Modifier.weight(1f),
                            ) {
                                appFilter = ""
                                show(Sheet.Apps)
                            }
                        }
                    }
                }
                if (page == "people") {
                    item { Heading("পরিচিত মানুষ") }
                    item {
                        Primary("পরিচিতি যোগ করুন") {
                            picked = null
                            show(Sheet.AddPerson)
                        }
                    }
                    if (people.isEmpty())
                        item {
                            EmptyNote(
                                "person",
                                "কাকে কল করতে চান?",
                                "নাম ও মোবাইল নম্বর একবার রাখুন। পরে নাম বলেই কল করুন।",
                            )
                        }
                    items(people, key = { it.id }) { p ->
                        ListRow("phone", p.name, bnDigits(p.number)) { show(Sheet.Call(p)) }
                    }
                } else {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Heading(if (page == "home") "পরের রিমাইন্ডার" else "আমার রিমাইন্ডার")
                            Spacer(Modifier.weight(1f))
                            if (page == "reminders")
                                IconButton(
                                    onClick = { show(Sheet.Edit()) },
                                    modifier =
                                        Modifier.semantics {
                                            contentDescription = "রিমাইন্ডার যোগ করুন"
                                        },
                                ) {
                                    Glyph("plus")
                                }
                            else if (reminders.isNotEmpty())
                                TextButton(onClick = { page = "reminders" }) { Text("সব দেখুন") }
                        }
                    }
                    if (reminders.any { it.state == "blocked" })
                        item {
                            OutlinedButton(onClick = ::repair, modifier = Modifier.fillMaxWidth()) {
                                Text("রিমাইন্ডারের অনুমতি দিন")
                            }
                        }
                    if (reminders.isEmpty())
                        item {
                            EmptyNote(
                                "clock",
                                "এখনও রিমাইন্ডার নেই",
                                "‘মনে করান’ চেপে প্রথম কাজটি যোগ করুন।",
                            )
                        }
                    val rows =
                        if (page == "home")
                            reminders
                                .filter { it.state != "fired" }
                                .take(2)
                                .ifEmpty { reminders.takeLast(1) }
                        else reminders
                    items(rows, key = { it.id }) { r ->
                        ListRow(
                            "clock",
                            r.title,
                            r.due.local().whenText() +
                                when (r.state) {
                                    "blocked" -> " · অনুমতি দরকার"
                                    "fired" -> " · সময় হয়েছে"
                                    else -> if (r.daily) " · প্রতিদিন" else ""
                                },
                        ) {
                            show(Sheet.Detail(r.id))
                        }
                    }
                }
            }
        }
        val current = sheet
        if (current != null)
            ModalBottomSheet(
                onDismissRequest = { show(null) },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Paper,
            ) {
                key(current) {
                    Column(
                        Modifier.fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 22.dp)
                            .imePadding()
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        when (current) {
                            Sheet.Talk -> {
                                Heading(if (listening) "কথা বলুন" else "আপনার কথা")
                                Text(speechStatus.ifBlank { "বলুন অথবা লিখুন" }, color = Quiet)
                                OutlinedTextField(
                                    speechInput,
                                    { speechInput = it },
                                    enabled = !listening,
                                    label = { Text("কী করতে চান?") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (listening)
                                    Primary("শোনা বন্ধ করুন") {
                                        voice.stop()
                                        listening = false
                                        speechStatus = "শোনা বন্ধ হয়েছে।"
                                    }
                                else {
                                    Primary("এগিয়ে যান", speechInput.isNotBlank()) {
                                        interpret(speechInput)
                                    }
                                    TextButton(onClick = ::requestListen) { Text("আবার বলুন") }
                                }
                            }
                            Sheet.Help -> {
                                Heading("যেভাবে বলবেন")
                                listOf(
                                        "clock" to "আধা ঘণ্টা পরে পানি খেতে মনে করিও",
                                        "phone" to "আম্মুকে কল করো",
                                        "apps" to "ক্যালকুলেটর খোলো",
                                    )
                                    .forEach { (icon, example) ->
                                        ListRow(icon, example, "চেপে চেষ্টা করুন") {
                                            speechInput = example
                                            show(Sheet.Talk)
                                            speechStatus = "এগিয়ে যান চেপে চেষ্টা করুন।"
                                        }
                                    }
                                Text(
                                    "ফোনের ভেতরেই আপনার কথা লেখায় রূপান্তর হয়। কল করার আগে নম্বর দেখে নিশ্চিত করুন।",
                                    color = Quiet,
                                )
                            }
                            Sheet.Apps -> {
                                Heading("কোন অ্যাপ খুলবেন?")
                                OutlinedTextField(
                                    appFilter,
                                    { appFilter = it },
                                    label = { Text("অ্যাপ খুঁজুন") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                val filtered = apps.filter { it.label.contains(appFilter, true) }
                                if (filtered.isEmpty())
                                    Text("এই নামে কোনো অ্যাপ পাওয়া যায়নি।", color = Quiet)
                                LazyColumn(
                                    Modifier.fillMaxWidth().heightIn(max = 360.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    items(filtered, key = { it.pkg }) { a ->
                                        ListRow("apps", a.label, "") { launchApp(a) }
                                    }
                                }
                            }
                            Sheet.AddPerson -> {
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                                contactPicker.launch(
                                                    Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)
                                                )
                                            }
                                            .onFailure {
                                                notice =
                                                    "ফোনের তালিকা খোলা যায়নি। নাম ও নম্বর লিখুন।"
                                            }
                                    }
                                ) {
                                    Text("ফোনের পরিচিতি থেকে নিন")
                                }
                                PersonEditor(picked) { name, number ->
                                    val result = db.favourite(name, number)
                                    reload()
                                    show(null)
                                    notice =
                                        if (result < 0) "এই পরিচিতি আগেই রাখা হয়েছে।"
                                        else "পরিচিতি রাখা হয়েছে"
                                }
                            }
                            is Sheet.Edit ->
                                ReminderEditor(current) { title, time, daily ->
                                    save(current, title, time, daily)
                                }
                            is Sheet.Call -> {
                                val p = current.person
                                Heading(p.name + "কে কল করবেন?")
                                Text(bnDigits(p.number), fontSize = 24.sp, color = Ink)
                                Primary("কল করুন") { confirmCall(p) }
                                ApprovalVoice(
                                    p.name + "। " + bnDigits(p.number) + "। কল করবেন?",
                                    true,
                                ) {
                                    confirmCall(p)
                                }
                                TextButton(onClick = { show(null) }) { Text("এখন নয়") }
                                TextButton(onClick = { show(Sheet.RemovePerson(p)) }) {
                                    Text("পছন্দের তালিকা থেকে সরান")
                                }
                            }
                            is Sheet.RemovePerson -> {
                                Heading("পছন্দের তালিকা থেকে সরাবেন?")
                                Text(current.person.name)
                                Primary("হ্যাঁ, সরান") {
                                    db.removePerson(current.person.id)
                                    reload()
                                    show(null)
                                }
                                TextButton(onClick = { show(null) }) { Text("ফিরে যান") }
                            }
                            is Sheet.Detail -> {
                                val r = reminders.firstOrNull { it.id == current.id }
                                if (r == null) Text("রিমাইন্ডার পাওয়া যায়নি।")
                                else {
                                    Heading(r.title)
                                    Text(
                                        r.due.local().whenText() +
                                            (if (r.daily) " · প্রতিদিন" else ""),
                                        color = Quiet,
                                        fontSize = 18.sp,
                                    )
                                    if (r.state == "fired") {
                                        Primary("হয়ে গেছে") {
                                            Alarms.cancel(this@MainActivity, r.id)
                                            db.delete(r.id)
                                            reload()
                                            show(null)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                save(
                                                    Sheet.Edit(r.id),
                                                    r.title,
                                                    LocalDateTime.now().plusMinutes(10),
                                                    false,
                                                )
                                            }
                                        ) {
                                            Text("১০ মিনিট পরে")
                                        }
                                    } else {
                                        Primary("সময় বা কাজ বদলান") {
                                            show(Sheet.Edit(r.id, r.title, r.due.local(), r.daily))
                                        }
                                        if (r.state == "blocked")
                                            OutlinedButton(onClick = ::repair) {
                                                Text("অনুমতি দিন")
                                            }
                                    }
                                    TextButton(onClick = { show(Sheet.Remove(r.id, r.title)) }) {
                                        Text("রিমাইন্ডার মুছুন")
                                    }
                                }
                            }
                            is Sheet.Remove -> {
                                Heading("রিমাইন্ডারটি মুছবেন?")
                                Text(current.title)
                                Primary("হ্যাঁ, মুছুন") {
                                    Alarms.cancel(this@MainActivity, current.id)
                                    db.delete(current.id)
                                    reload()
                                    show(null)
                                }
                                TextButton(onClick = { show(Sheet.Detail(current.id)) }) {
                                    Text("ফিরে যান")
                                }
                            }
                        }
                    }
                }
            }
    }

    @Composable
    private fun ReminderEditor(edit: Sheet.Edit, commit: (String, LocalDateTime, Boolean) -> Unit) {
        var title by rememberSaveable { mutableStateOf(edit.title) }
        var at by remember {
            mutableStateOf(edit.at ?: LocalDateTime.now().plusHours(1).withSecond(0).withNano(0))
        }
        var chosen by remember { mutableStateOf(edit.at != null) }
        var daily by remember { mutableStateOf(edit.daily) }
        var issue by remember { mutableStateOf("") }
        Heading(if (edit.at != null && edit.id == null) "সময় ঠিক আছে?" else "মনে করিয়ে দিন")
        OutlinedTextField(
            title,
            { title = it },
            label = { Text("কোন কাজটি মনে করাব?") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    DatePickerDialog(
                            this@MainActivity,
                            { _, y, m, d ->
                                at =
                                    at.withDayOfMonth(1)
                                        .withYear(y)
                                        .withMonth(m + 1)
                                        .withDayOfMonth(d)
                            },
                            at.year,
                            at.monthValue - 1,
                            at.dayOfMonth,
                        )
                        .show()
                },
            ) {
                Text(bnDigits(at.format(DateTimeFormatter.ofPattern("d MMM", bangla))))
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    TimePickerDialog(
                            this@MainActivity,
                            { _, h, m ->
                                at = at.withHour(h).withMinute(m)
                                chosen = true
                            },
                            at.hour,
                            at.minute,
                            false,
                        )
                        .show()
                },
            ) {
                Text(
                    if (chosen) bnDigits(at.format(DateTimeFormatter.ofPattern("h:mm a", bangla)))
                    else "সময় বাছুন"
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("প্রতিদিন একই সময়ে", Modifier.weight(1f))
            Switch(checked = daily, onCheckedChange = { daily = it })
        }
        if (issue.isNotBlank()) Text(issue, color = MaterialTheme.colorScheme.error)
        Primary("নিশ্চিত করে রাখুন", title.isNotBlank() && chosen) {
            if (at <= LocalDateTime.now()) issue = "ভবিষ্যতের একটি সময় বেছে নিন।"
            else commit(title, at, daily)
        }
        ApprovalVoice(
            title +
                "। " +
                at.whenText() +
                (if (daily) "। প্রতিদিন" else "") +
                "। রিমাইন্ডার রাখবেন?",
            title.isNotBlank() && chosen,
        ) {
            if (at > LocalDateTime.now()) commit(title, at, daily)
            else issue = "ভবিষ্যতের একটি সময় বেছে নিন।"
        }
    }

    @Composable
    private fun ApprovalVoice(prompt: String, valid: Boolean, accept: () -> Unit) {
        var hint by remember { mutableStateOf("") }
        val latestAccept by rememberUpdatedState(accept)
        LaunchedEffect(prompt, valid, resumed) {
            if (!valid || !spokenRequest || !resumed) return@LaunchedEffect
            spokenRequest = false
            if (ttsReady) {
                speak(prompt + " হ্যাঁ অথবা না বলুন।")
                kotlinx.coroutines.delay(350)
                while (tts?.isSpeaking == true) kotlinx.coroutines.delay(100)
            }
            if (!resumed) return@LaunchedEffect
            hint = "হ্যাঁ অথবা না বলুন"
            voice.listen(
                { hint = "শুনছি… হ্যাঁ অথবা না বলুন" },
                {},
                { s ->
                    when (normalized(s)) {
                        in listOf("হ্যাঁ", "হ্যা", "জি", "ঠিক আছে").map(::normalized) ->
                            latestAccept()
                        in listOf("না", "বাতিল") -> show(null)
                        else -> hint = "উত্তর বুঝতে পারিনি। নিচের বোতাম ব্যবহার করুন।"
                    }
                },
                { hint = "নিশ্চিত করতে বোতাম চাপুন।" },
            )
        }
        DisposableEffect(prompt, valid, resumed) {
            onDispose {
                voice.stop()
                tts?.stop()
                hint = ""
            }
        }
        if (hint.isNotEmpty()) Text(hint, color = Quiet, fontSize = 15.sp)
    }
}

@Composable
private fun ListRow(icon: String, title: String, sub: String, click: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White)
            .clickable(role = Role.Button, onClick = click)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(38.dp).background(Mist, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(icon, 20.dp, Teal)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                color = Ink,
                fontWeight = FontWeight.SemiBold,
            )
            if (sub.isNotEmpty()) Text(sub, color = Quiet, fontSize = 14.sp)
        }
        Glyph("next", 17.dp, Quiet)
    }
}

@Composable
private fun PersonEditor(picked: Pair<String, String>?, save: (String, String) -> Unit) {
    var name by rememberSaveable(picked) { mutableStateOf(picked?.first.orEmpty()) }
    var number by rememberSaveable(picked) { mutableStateOf(picked?.second.orEmpty()) }
    val dial = latinDigits(number).filterNot { it == ' ' || it == '-' }
    val valid = Regex("(?:\\+?88)?01[3-9][0-9]{8}").matches(dial)
    Heading("পরিচিতি যোগ করুন")
    OutlinedTextField(
        name,
        { name = it },
        label = { Text("যে নামে ডাকেন") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        number,
        { number = it },
        label = { Text("বাংলাদেশি মোবাইল নম্বর") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
        supportingText = {
            if (number.isNotBlank() && !valid) Text("০১ দিয়ে শুরু ১১ সংখ্যার নম্বর দিন")
        },
    )
    Primary("পরিচিতি রাখুন", name.isNotBlank() && valid) { save(name.trim(), dial) }
}
