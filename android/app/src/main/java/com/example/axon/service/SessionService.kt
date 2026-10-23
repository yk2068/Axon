package com.example.axon.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.axon.R

/**
 * Keeps the process out of the cached-app freezer while a touchpad session is up.
 *
 * Wakelocks alone do not stop Android from freezing a backgrounded process; without a
 * foreground service the WebSocket dies shortly after the screen goes off. The service
 * itself does no work beyond showing a minimal, silent notification.
 */
class SessionService : Service() {

    companion object {
        private const val CHANNEL_ID = "axon_session"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, SessionService::class.java).setAction("axon.START")
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Starting a foreground service can be refused (e.g. background restrictions);
                // the session still works, it just may be frozen while the screen is off.
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, SessionService::class.java))
            } catch (e: Exception) {
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_axon_notify)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.session_notification))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.session_channel),
                NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
            manager.createNotificationChannel(channel)
        }
    }
}
