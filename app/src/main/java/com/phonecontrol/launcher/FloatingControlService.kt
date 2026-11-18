package com.phonecontrol.launcher

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class FloatingControlService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private var panel: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Settings.canDrawOverlays(this)) showBubble() else stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (bubble == null && Settings.canDrawOverlays(this)) showBubble()
        return START_STICKY
    }

    private fun makeParams(width: Int, height: Int, gravityValue: Int = Gravity.TOP or Gravity.END): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        return WindowManager.LayoutParams(width, height, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = gravityValue; x = 18; y = 220 }
    }

    private fun showBubble() {
        if (bubble != null) return
        val lp = makeParams(58, 58)
        val view = TextView(this).apply {
            text = "📱"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = shape(Color.rgb(45, 82, 190), 30f)
            elevation = 12f
        }
        view.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var dragged = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { sx=event.rawX; sy=event.rawY; ox=lp.x; oy=lp.y; dragged=false; return true }
                    MotionEvent.ACTION_MOVE -> {
                        val dx=(sx-event.rawX).toInt(); val dy=(event.rawY-sy).toInt()
                        if (kotlin.math.abs(dx)>6 || kotlin.math.abs(dy)>6) dragged=true
                        lp.x=(ox+dx).coerceAtLeast(0); lp.y=(oy+dy).coerceAtLeast(0)
                        windowManager.updateViewLayout(view, lp); return true
                    }
                    MotionEvent.ACTION_UP -> { if (!dragged) togglePanel(); return true }
                }
                return false
            }
        })
        bubble = view
        windowManager.addView(view, lp)
    }

    private fun togglePanel() {
        if (panel != null) { removePanel(); return }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(7, 7, 7, 7)
            background = shape(Color.rgb(20, 27, 48), 20f)
            elevation = 18f
        }
        row.addView(makeButton("⏻", "כיבוי", Color.rgb(170, 42, 55)) {
            sendAction(MainActivity.ACTION_POWER_MENU)
        }, LinearLayout.LayoutParams(0, 58, 1f))
        row.addView(makeButton("🔒", "נעילה", Color.rgb(48, 62, 99)) {
            sendAction(MainActivity.ACTION_LOCK)
        }, LinearLayout.LayoutParams(0, 58, 1f))
        row.addView(makeButton("↻", "סיבוב", Color.rgb(48, 62, 99)) {
            sendAction(MainActivity.ACTION_ROTATE)
        }, LinearLayout.LayoutParams(0, 58, 1f))
        row.addView(makeButton("▣", "צילום", Color.rgb(48, 62, 99)) {
            removePanel()
            startActivity(Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_SCREENSHOT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }, LinearLayout.LayoutParams(0, 58, 1f))
        val lp = makeParams((resources.displayMetrics.widthPixels - 20).coerceAtMost(360),
            WindowManager.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        lp.x = 0
        lp.y = 110
        panel = row
        windowManager.addView(row, lp)
    }

    private fun makeButton(symbol: String, label: String, color: Int, action: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = shape(color, 13f)
            isClickable = true
            setOnClickListener { action() }
            addView(TextView(this@FloatingControlService).apply {
                text = symbol
                textSize = 21f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(-1, 32))
            addView(TextView(this@FloatingControlService).apply {
                text = label
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(-1, 18))
        }

    private fun sendAction(action: String) {
        sendBroadcast(Intent(this, ControlActionReceiver::class.java).setAction(action))
        removePanel()
    }

    private fun shape(color: Int, radius: Float) =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun removePanel() {
        panel?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        panel = null
    }

    override fun onDestroy() {
        removePanel()
        bubble?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
