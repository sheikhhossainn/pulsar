package dev.pulsar

import android.app.Application
import android.media.AudioAttributes
import android.media.SoundPool
import android.speech.tts.TextToSpeech
import dev.pulsar.core.ContactResolver
import dev.pulsar.core.FilePinpointer
import dev.pulsar.tools.ContactSaverTool
import dev.pulsar.tools.DocumentEngineTool
import dev.pulsar.tools.FraudShieldTool
import dev.pulsar.tools.GeminiFactCheckTool
import dev.pulsar.tools.KinshipDialerTool
import dev.pulsar.tools.StoragePinpointerTool
import java.io.File
import java.util.Locale

class PulsarApplication : Application(), TextToSpeech.OnInitListener {

    lateinit var contactResolver: ContactResolver
        private set

    lateinit var filePinpointer: FilePinpointer
        private set

    // The 6 Capped Atomic Tools
    lateinit var kinshipDialer: KinshipDialerTool
        private set

    lateinit var contactSaver: ContactSaverTool
        private set

    lateinit var fraudShield: FraudShieldTool
        private set

    lateinit var geminiFactCheck: GeminiFactCheckTool
        private set

    lateinit var documentEngine: DocumentEngineTool
        private set

    lateinit var storagePinpointer: StoragePinpointerTool
        private set

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private var soundPool: SoundPool? = null
    private var earconSoundId: Int = 0

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Core Models & Resolvers
        initializeResolvers()

        // 2. Initialize the 6 Capped Atomic Tools
        initializeTools()

        // 3. Initialize Audio Chime (150ms Earcon)
        initializeEarcon()

        // 4. Initialize Bangla Text-To-Speech
        tts = TextToSpeech(this, this)
    }

    private fun initializeResolvers() {
        val contactsJson = assets.open("contacts_100.json").bufferedReader().use { it.readText() }
        val kinshipJson = assets.open("kinship_graph.json").bufferedReader().use { it.readText() }

        contactResolver = ContactResolver(contactsJson, kinshipJson)
        filePinpointer = FilePinpointer(storageRoot = getExternalFilesDir(null) ?: filesDir)
    }

    private fun initializeTools() {
        kinshipDialer = KinshipDialerTool(this, contactResolver)
        contactSaver = ContactSaverTool(this)
        fraudShield = FraudShieldTool()
        geminiFactCheck = GeminiFactCheckTool(this, apiKey = null)
        documentEngine = DocumentEngineTool(this)
        storagePinpointer = StoragePinpointerTool(this, filePinpointer)
    }

    private fun initializeEarcon() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build()

        val afd = resources.openRawResourceFd(dev.pulsar.R.raw.earcon)
        earconSoundId = soundPool?.load(afd, 1) ?: 0
    }

    /**
     * Plays the crisp 150 ms Earcon chime upon wake
     * Opens ASR stream only AFTER chime finishes to prevent self-transcription!
     */
    fun playEarconChime(onChimeComplete: (() -> Unit)? = null) {
        if (earconSoundId != 0) {
            soundPool?.play(earconSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
            // Schedule ASR stream open after 150ms
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                onChimeComplete?.invoke()
            }, 150)
        } else {
            onChimeComplete?.invoke()
        }
    }

    fun speakAloud(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "PulsarUtteranceId")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("bn", "BD"))
            isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            if (!isTtsReady) {
                // Fallback to default
                tts?.setLanguage(Locale.getDefault())
                isTtsReady = true
            }
        }
    }

    override fun onTerminate() {
        tts?.stop()
        tts?.shutdown()
        soundPool?.release()
        super.onTerminate()
    }
}
