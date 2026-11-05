package dev.pulsar.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dev.pulsar.PulsarApplication
import dev.pulsar.R
import dev.pulsar.services.PulsarOverlayService

class MainActivity : AppCompatActivity() {

    private lateinit var tvLiveOutput: TextView
    private lateinit var app: PulsarApplication

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val callGranted = permissions[Manifest.permission.CALL_PHONE] ?: false
        tvLiveOutput.text = "পারমিশন স্ট্যাটাস:\nMicrophone: $audioGranted\nPhone Call: $callGranted"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        app = application as PulsarApplication
        tvLiveOutput = findViewById(R.id.tvLiveOutput)

        setupPermissionRequests()
        setupTestButtons()
        handleIncomingIntents(intent)
    }

    private fun setupPermissionRequests() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionsLauncher.launch(missing.toTypedArray())
        }

        // Overlay Permission Check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val overlayIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(overlayIntent)
        }
    }

    private fun setupTestButtons() {
        // Test 1: "আম্মুকে কল দে"
        findViewById<Button>(R.id.btnTestAmmuCall).setOnClickListener {
            val result = app.kinshipDialer.executeDial("আম্মুকে কল দে")
            tvLiveOutput.text = "টেস্ট ১ (আম্মুকে কল):\n${result.message}\nটার্গেট: ${result.targetName} (${result.targetPhone})"
            app.speakAloud(result.message)
        }

        // Test 2: "আমার ছেলেকে ফোন দাও" (Ambiguity: 3 sons exist)
        findViewById<Button>(R.id.btnTestSonAmbiguity).setOnClickListener {
            val result = app.kinshipDialer.executeDial("আমার ছেলেকে ফোন দাও")
            tvLiveOutput.text = "টেস্ট ২ (৩ জন ছেলে অস্পষ্টতা):\n${result.message}"
            app.speakAloud(result.message)
        }

        // Test 3: "আবসের ইমেইলটা চেক করত" (Prosthetic hesitation vowel 'আবসের' -> 'বসের')
        findViewById<Button>(R.id.btnTestBossQuirk).setOnClickListener {
            val resolved = app.contactResolver.resolveContact("আবসের ইমেইলটা চেক করত")
            val output = "টেস্ট ৩ (ভেসটেশন 'আবসের' -> 'বসের'):\nস্ট্যাটাস: ${resolved.status}\nনাম: ${resolved.contact?.name}\nফোন: ${resolved.phone}\nকনফিডেন্স: ${resolved.confidence}"
            tvLiveOutput.text = output
            app.speakAloud("আবসের নাম সফলভাবে সনাক্ত হয়েছে: ${resolved.contact?.name}")
        }

        // Test 4: Fake bKash SMS Scam from personal 017... number
        findViewById<Button>(R.id.btnTestFraudSms).setOnClickListener {
            val fakeSender = "01712998877"
            val fakeBody = "You have received Tk 15,000.00 from 01712998877. Ref: gift. TrxID: 9H7G6F5D"
            val verdict = app.fraudShield.inspectNotification(fakeSender, fakeBody)
            val output = "টেস্ট ৪ (জালিয়াতি SMS সনাক্তকরণ):\nপ্রতারণা?: ${verdict.isFraud}\nরিস্ক স্কোর: ${verdict.riskScore}\nসতর্কতা: ${verdict.spokenAlertBengali}"
            tvLiveOutput.text = output
            app.speakAloud(verdict.spokenAlertBengali)
        }

        // Test 5: Floating HUD & 150ms Earcon Chime Test
        findViewById<Button>(R.id.btnTestOverlayHud).setOnClickListener {
            app.playEarconChime {
                PulsarOverlayService.show(this, "আজকে ক্যামেরায় তোলা ছবিটা ওপেন কর", "খোঁজা হচ্ছে…")
                tvLiveOutput.text = "টেস্ট ৫: 150ms Earcon বাজানো হয়েছে এবং ফ্লোটিং HUD সক্রিয় করা হয়েছে।"
            }
        }
    }

    private fun handleIncomingIntents(intent: Intent?) {
        if (intent?.getBooleanExtra("FRAUD_ALERT", false) == true) {
            val alert = intent.getStringExtra("SPOKEN_ALERT") ?: ""
            tvLiveOutput.text = "⚠️ সক্রিয় সতর্কতা:\n$alert"
        }
    }
}
