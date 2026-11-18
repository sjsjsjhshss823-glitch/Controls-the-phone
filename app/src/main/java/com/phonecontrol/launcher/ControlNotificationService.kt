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
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Phone Control", NotificationManager.IMPORTANCE_LOW).apply {
                description = "כפתורי שליטה מהירה בטלפון"
            })
        }
        val notification = Notification.Builder(this).apply { if (Build.VERSION.SDK_INT >= 26) setChannelId(CHANNEL) }
        val built = notification
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Phone Control")
            .setContentText("שליטה מהירה בטלפון")
            .setOngoing(true)
            .addAction(action("🔒 נעילה", MainActivity.ACTION_LOCK, 1))
            .addAction(action("🔄 סיבוב", MainActivity.ACTION_ROTATE, 2))
            .addAction(action("📸 צילום מסך", MainActivity.ACTION_SCREENSHOT, 3))
            .build()
        startForeground(41, built)
    }

    private fun action(label: String, action: String, id: Int): Notification.Action {
        val intent = Intent(this, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_manage), label, pending).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object { private const val CHANNEL = "phone_control_actions" }
}
