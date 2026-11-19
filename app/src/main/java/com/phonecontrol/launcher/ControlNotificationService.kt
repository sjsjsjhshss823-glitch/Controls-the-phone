package com.phonecontrol.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class ControlNotificationService : Service() {
    override fun onCreate() {
        super.onCreate()
        DiagnosticLogger.initialize(this)
        DiagnosticLogger.record(this, "NOTIFICATION_SERVICE_CREATED", "Persistent notification service started")
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Phone Control", NotificationManager.IMPORTANCE_LOW).apply {
                description = "כפתורי שליטה מהירה בטלפון"
            })
        }
        val notification = Notification.Builder(this).apply { if (Build.VERSION.SDK_INT >= 26) setChannelId(CHANNEL) }
        val built = notification
            .setSmallIcon(R.drawable.ic_notification_phone)
            .setContentTitle("Phone Control")
            .setContentText("שליטה מהירה בטלפון")
            .setOngoing(true)
            .addAction(action("⏻", MainActivity.ACTION_POWER_MENU, 4, android.R.drawable.ic_lock_power_off))
            .addAction(action("🔒", MainActivity.ACTION_LOCK, 1, android.R.drawable.ic_lock_lock))
            .addAction(action("↻", MainActivity.ACTION_ROTATE, 2, android.R.drawable.ic_menu_rotate))
            .addAction(action("📷", MainActivity.ACTION_SCREENSHOT, 3, android.R.drawable.ic_menu_camera))
            .build()
        startForeground(41, built)
    }

    private fun action(label: String, action: String, id: Int, iconRes: Int): Notification.Action {
        val intent = if (action == MainActivity.ACTION_SCREENSHOT || action == MainActivity.ACTION_ROTATE) {
            Intent(this, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        } else {
            Intent(this, ControlActionReceiver::class.java).setAction(action)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val pending = if (action == MainActivity.ACTION_SCREENSHOT) {
            PendingIntent.getActivity(this, id, intent, flags)
        } else {
            PendingIntent.getBroadcast(this, id, intent, flags)
        }
        return Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, iconRes), label, pending).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        DiagnosticLogger.record(this, "NOTIFICATION_SERVICE_DESTROYED", "Persistent notification service stopped")
        super.onDestroy()
    }

    companion object { private const val CHANNEL = "phone_control_actions" }
}
