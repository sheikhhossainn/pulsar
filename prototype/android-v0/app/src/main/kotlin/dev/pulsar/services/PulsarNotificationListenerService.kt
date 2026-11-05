package dev.pulsar.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import dev.pulsar.PulsarApplication
import dev.pulsar.tools.FraudShieldTool
import dev.pulsar.ui.MainActivity

/**
 * Service: PulsarNotificationListenerService
 * Scans incoming notifications for:
 * 1. bKash / Nagad Fraud SMS detection (FraudShieldTool)
 * 2. Contact number parsing (ContactSaverTool)
 * Avoids restricted READ_SMS permission entirely!
 */
class PulsarNotificationListenerService : NotificationListenerService() {

    private val fraudShield = FraudShieldTool()

    companion object {
        const val FRAUD_CHANNEL_ID = "pulsar_fraud_alerts"
        const val NOTIF_ID_FRAUD = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        // 1. Run FraudShield Check
        val verdict = fraudShield.inspectNotification(sender = title, messageBody = text)
        if (verdict.isFraud) {
            triggerFraudAlert(verdict)
        }
    }

    private fun triggerFraudAlert(verdict: FraudShieldTool.FraudVerdict) {
        val app = application as? PulsarApplication
        // Play earcon / speak alert via TTS
        app?.speakAloud(verdict.spokenAlertBengali)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("FRAUD_ALERT", true)
            putExtra("FRAUD_DETAILS", verdict.details)
            putExtra("SPOKEN_ALERT", verdict.spokenAlertBengali)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, FRAUD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("⚠️ ভুয়া টাকা পাওয়ার মেসেজ! (Fraud Alert)")
            .setContentText(verdict.spokenAlertBengali)
            .setStyle(NotificationCompat.BigTextStyle().bigText(verdict.spokenAlertBengali))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID_FRAUD, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FRAUD_CHANNEL_ID,
                "Pulsar Fraud Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alarms for bKash/Nagad scam attempts"
                enableVibration(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
