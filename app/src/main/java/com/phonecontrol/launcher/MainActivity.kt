package com.phonecontrol.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import android.text.Editable
import android.text.TextWatcher
import kotlin.math.abs

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var homePanel: LinearLayout
    private lateinit var drawerPanel: LinearLayout
    private lateinit var search: EditText
    private lateinit var clearButton: TextView
    private lateinit var appsGrid: GridLayout
    private var drawerOpen = false
    private var downY = 0f
    private var downX = 0f
    private var lastTap = 0L

    private data class AppEntry(val label: String, val packageName: String, val icon: android.graphics.drawable.Drawable)
    private val allApps = mutableListOf<AppEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 19, 35)
        window.navigationBarColor = Color.rgb(15, 19, 35)
        window.decorView.systemUiVisibility = 0
        loadApps()
        buildInterface()
        showHome()
    }

    private fun loadApps() {
        allApps.clear()
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0).forEach { info ->
            val packageName = info.activityInfo.packageName
            val label = info.loadLabel(pm)?.toString() ?: packageName
            allApps.add(AppEntry(label, packageName, info.loadIcon(pm)))
        }
        allApps.sortBy { it.label.lowercase() }
    }

    private fun buildInterface() {
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(15, 19, 35))
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { downY = event.y; downX = event.x; true }
                    MotionEvent.ACTION_UP -> {
                        val dy = event.y - downY
                        if (!drawerOpen && dy < -65) openDrawer()
                        else if (drawerOpen && dy > 90) showHome()
                        else if (!drawerOpen && abs(dy) < 12 && abs(event.x - downX) < 12) {
                            val now = System.currentTimeMillis()
                            if (now - lastTap < 350) lockScreen()
                            lastTap = now
                        }
                        true
                    }
                    else -> true
                }
            }
        }

        homePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 30, 28, 36)
        }
        val clock = TextView(this).apply {
            text = "PHONE CONTROL"
            textSize = 13f
            letterSpacing = 0.18f
            setTextColor(Color.rgb(158, 174, 215))
            gravity = Gravity.CENTER
        }
        val title = TextView(this).apply {
            text = "הטלפון שלך.\nבשליטה שלך."
            textSize = 34f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 12)
        }
        val hint = TextView(this).apply {
            text = "החלק למעלה כדי לפתוח את כל האפליקציות"
            textSize = 16f
            setTextColor(Color.rgb(190, 199, 222))
            gravity = Gravity.CENTER
        }
        val handle = TextView(this).apply {
            text = "⌃"
            textSize = 34f
            setTextColor(Color.rgb(133, 151, 255))
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 0)
        }
        val settings = TextView(this).apply {
            text = "הגדרות הקשה כפולה  ⚙"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(43, 52, 82), 24)
            setPadding(24, 16, 24, 16)
            setOnClickListener { showDoubleTapHelp() }
        }
        homePanel.addView(clock)
        homePanel.addView(title)
        homePanel.addView(hint)
        homePanel.addView(handle)
        homePanel.addView(settings, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 46 })
        homePanel.setOnTouchListener { _, event -> handleHomeTouch(event) }
        root.addView(homePanel, FrameLayout.LayoutParams(-1, -1))

        drawerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 24, 18, 10)
            visibility = View.GONE
            setBackgroundColor(Color.rgb(20, 25, 44))
        }
        val drawerTitle = TextView(this).apply {
            text = "כל האפליקציות"
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 4, 4, 18)
        }
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(39, 47, 72), 18)
            setPadding(12, 2, 8, 2)
        }
        search = EditText(this).apply {
            hint = "חיפוש אפליקציות..."
            textSize = 16f
            singleLine = true
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(158, 169, 196))
            background = null
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        clearButton = TextView(this).apply {
            text = "×"
            textSize = 29f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            visibility = View.GONE
            setPadding(12, 0, 12, 0)
            setOnClickListener {
                search.setText("")
                search.requestFocus()
            }
        }
        searchRow.addView(search, LinearLayout.LayoutParams(0, 52, 1f))
        searchRow.addView(clearButton, LinearLayout.LayoutParams(42, 52))
        appsGrid = GridLayout(this).apply { columnCount = 4; useDefaultMargins = true }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(appsGrid)
        }
        drawerPanel.addView(drawerTitle)
        drawerPanel.addView(searchRow)
        drawerPanel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 16 })
        root.addView(drawerPanel, FrameLayout.LayoutParams(-1, -1))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                clearButton.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                renderApps(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        setContentView(root)
    }

    private fun handleHomeTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { downY = event.y; downX = event.x; return true }
            MotionEvent.ACTION_UP -> {
                val dy = event.y - downY
                if (dy < -65) openDrawer()
                else if (abs(dy) < 12 && abs(event.x - downX) < 12) {
                    val now = System.currentTimeMillis()
                    if (now - lastTap < 350) lockScreen()
                    lastTap = now
                }
                return true
            }
        }
        return true
    }

    private fun openDrawer() {
        drawerOpen = true
        homePanel.visibility = View.GONE
        drawerPanel.visibility = View.VISIBLE
        search.setText("")
        renderApps("")
    }

    private fun showHome() {
        drawerOpen = false
        drawerPanel.visibility = View.GONE
        homePanel.visibility = View.VISIBLE
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken, 0)
    }

    private fun renderApps(query: String) {
        appsGrid.removeAllViews()
        val filtered = allApps.filter { it.label.contains(query, ignoreCase = true) }
        filtered.forEach { app ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(4, 12, 4, 12)
                setOnClickListener {
                    val launch = packageManager.getLaunchIntentForPackage(app.packageName)
                    if (launch != null) startActivity(launch)
                }
            }
            val icon = ImageView(this).apply {
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            item.addView(icon, LinearLayout.LayoutParams(52, 52))
            val label = TextView(this).apply {
                text = app.label
                textSize = 11.5f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 2
                setPadding(0, 7, 0, 0)
            }
            item.addView(label, LinearLayout.LayoutParams(-1, 42))
            appsGrid.addView(item, GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(3, 4, 3, 4)
            })
        }
        if (filtered.isEmpty()) {
            val empty = TextView(this).apply {
                text = "לא נמצאו אפליקציות"
                textSize = 16f
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
            }
            appsGrid.addView(empty, GridLayout.LayoutParams().apply { width = -1; height = 100; columnSpec = GridLayout.spec(0, 4) })
        }
    }

    private fun lockScreen() {
        val service = ScreenLockAccessibilityService.activeService
        if (service == null) {
            Toast.makeText(this, "כדי לנעול בהקשה כפולה, יש להפעיל את שירות הנגישות בהגדרות", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } else {
            service.lockScreenNow()
        }
    }

    private fun showDoubleTapHelp() {
        val message = "נעילת מסך: הפעל את שירות הנגישות של Phone Control. לאחר מכן, שתי הקשות מהירות על אזור ריק במסך הבית ינעלו את המסך.\n\nהדלקה בהקשה כפולה כשהמסך כבוי תלויה בתמיכת מערכת הטלפון. חפש בהגדרות: 'הקשה כפולה להעיר' או 'Double tap to wake'."
        android.app.AlertDialog.Builder(this)
            .setTitle("הקשה כפולה")
            .setMessage(message)
            .setPositiveButton("פתיחת הגדרות נגישות") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNegativeButton("סגור", null)
            .show()
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (drawerOpen) showHome() else super.onBackPressed()
    }
}
