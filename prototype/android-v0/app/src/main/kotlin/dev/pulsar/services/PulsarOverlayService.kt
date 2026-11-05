package dev.pulsar.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.app.NotificationCompat
import dev.pulsar.PulsarApplication
import dev.pulsar.R

class PulsarOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var hudView: View? = null

    companion object {
        const val CHANNEL_ID = "pulsar_overlay_channel"
        const val NOTIFICATION_ID = 2001

        fun show(context: Context, query: String? = null, result: String? = null) {
            val intent = Intent(context, PulsarOverlayService::class.java).apply {
                putExtra("QUERY", query)
                putExtra("RESULT", result)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun hide(context: Context) {
            context.stopService(Intent(context, PulsarOverlayService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        setupFloatingWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val query = intent?.getStringExtra("QUERY")
        val result = intent?.getStringExtra("RESULT")

        val app = application as? PulsarApplication

        if (hudView != null) {
            val tvQuery = hudView?.findViewById<TextView>(R.id.tvTranscribedQuery)
            val tvResult = hudView?.findViewById<TextView>(R.id.tvHudResult)

            if (!query.isNullOrBlank()) {
                tvQuery?.text = query
            }
            if (!result.isNullOrBlank()) {
                tvResult?.visibility = View.VISIBLE
                tvResult?.text = result
            }
        }

        // Play 150 ms chime on wake
        app?.playEarconChime()

        return START_NOT_STICKY
    }

    private fun setupFloatingWindow() {
        if (!android.provider.Settings.canDrawOverlays(this)) return

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        hudView = inflater.inflate(R.layout.overlay_hud, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 80
        }

        hudView?.findViewById<ImageButton>(R.id.btnCloseHud)?.setOnClickListener {
            stopSelf()
        }

        windowManager?.addView(hudView, params)
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pulsar Voice HUD",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("পালসার সক্রিয়")
            .setContentText("ভয়েস কমান্ডের জন্য প্রস্তুত")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        if (hudView != null && windowManager != null) {
            windowManager?.removeView(hudView)
            hudView = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
