package com.stockwebcam.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.stockwebcam.app.MainActivity
import com.stockwebcam.app.server.HttpStreamServer
import com.stockwebcam.app.utils.NetworkUtils

class WebcamService : Service() {

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    var streamServer: HttpStreamServer? = null
        private set

    private var isStreaming = false

    inner class LocalBinder : Binder() {
        fun getService(): WebcamService = this@WebcamService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "StockWebcam::StreamWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
        }

        startForegroundService()
        return START_STICKY
    }

    private fun startForegroundService() {
        wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours maximum wake lock

        val ipInfo = NetworkUtils.getStreamIpAddress(this)
        val streamUrl = ipInfo.getStreamUrl(8080)

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, WebcamService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Stock Android Webcam Active")
            .setContentText("Live stream: $streamUrl")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Stream", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        isStreaming = true
    }

    fun initServer(onControlCommand: (String, String?) -> Unit) {
        if (streamServer == null) {
            streamServer = HttpStreamServer(8080, onControlCommand).apply {
                start()
            }
        }
    }

    fun pushFrame(jpegBytes: ByteArray) {
        streamServer?.pushFrame(jpegBytes)
    }

    private fun stopForegroundService() {
        isStreaming = false
        streamServer?.stop()
        streamServer = null

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopForegroundService()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Webcam Stream Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the webcam streaming active when screen is locked or in background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "stock_webcam_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.stockwebcam.app.ACTION_STOP"
    }
}

