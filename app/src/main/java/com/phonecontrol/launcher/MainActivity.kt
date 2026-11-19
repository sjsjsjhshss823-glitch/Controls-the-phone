package com.phonecontrol.launcher

import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.media.ImageReader
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.content.ContentValues
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
    private var gridColumns = 5
    private var homePageIndex = 0
    private lateinit var homeContent: LinearLayout
    private lateinit var pageDots: TextView
    private var selectedBackgroundUri: Uri? = null

    private data class AppEntry(val label: String, val packageName: String, val icon: android.graphics.drawable.Drawable)
    private val allApps = mutableListOf<AppEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagnosticLogger.initialize(this)
        DiagnosticLogger.record(this, "ACTIVITY_ON_CREATE", "savedState=${savedInstanceState != null}; action=${intent?.action}")
        window.statusBarColor = Color.rgb(15, 19, 35)
        window.navigationBarColor = Color.rgb(15, 19, 35)
        window.decorView.systemUiVisibility = 0
        gridColumns = getSharedPreferences("phone_control_layout", MODE_PRIVATE).getInt("grid_columns", 5).coerceIn(4, 6)
        selectedBackgroundUri = getSharedPreferences("phone_control_layout", MODE_PRIVATE).getString("home_background_uri", null)?.let { Uri.parse(it) }
        loadApps()
        buildInterface()
        startService(Intent(this, ControlNotificationService::class.java))
        stopService(Intent(this, FloatingControlService::class.java))
        handleControlAction(intent)
        showHome()
    }

    override fun onResume() {
        super.onResume()
        DiagnosticLogger.record(this, "ACTIVITY_ON_RESUME", "drawerOpen=$drawerOpen")
        stopService(Intent(this, FloatingControlService::class.java))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DiagnosticLogger.record(this, "ACTIVITY_ON_NEW_INTENT", "action=${intent.action}")
        setIntent(intent)
        handleControlAction(intent)
    }

    private fun handleControlAction(intent: Intent?) {
        DiagnosticLogger.record(this, "CONTROL_ACTION_RECEIVED", "action=${intent?.action ?: "none"}")
        when (intent?.action) {
            ACTION_LOCK -> lockScreen()
            ACTION_POWER_MENU -> showSystemPowerMenu()
            ACTION_ROTATE -> toggleRotation()
            ACTION_SCREENSHOT -> requestScreenshot()
        }
    }

    private fun toggleRotation() {
        val canWrite = Settings.System.canWrite(this)
        DiagnosticLogger.record(this, "ROTATION_REQUESTED", "canWrite=$canWrite")
        if (!canWrite) {
            DiagnosticLogger.record(this, "ROTATION_FAILED_PERMISSION", "WRITE_SETTINGS not granted")
            Toast.makeText(this, "יש לאשר שינוי הגדרות מערכת כדי לשלוט בסיבוב", Toast.LENGTH_LONG).show()
            startActivityForResult(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")), REQUEST_WRITE_SETTINGS)
            return
        }
        try {
            val autoBefore = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
            val current = Settings.System.getInt(contentResolver, Settings.System.USER_ROTATION, 0)
            val next = if (autoBefore == 1 || current == 0 || current == 2) 1 else 0
            val autoWritten = Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
            val rotationWritten = Settings.System.putInt(contentResolver, Settings.System.USER_ROTATION, next)
            val savedAuto = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, -1)
            val savedRotation = Settings.System.getInt(contentResolver, Settings.System.USER_ROTATION, -1)
            val success = autoWritten && rotationWritten && savedAuto == 0 && savedRotation == next
            DiagnosticLogger.record(this, if (success) "ROTATION_SUCCEEDED" else "ROTATION_FAILED",
                "autoBefore=$autoBefore; current=$current; target=$next; autoWritten=$autoWritten; rotationWritten=$rotationWritten; savedAuto=$savedAuto; savedRotation=$savedRotation")
            val message = if (!success) "שינוי כיוון המסך נכשל. בדוק את הרשאת שינוי הגדרות המערכת." else if (next == 1) "המסך הוגדר לרוחב" else "המסך הוגדר לאורך"
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            DiagnosticLogger.exception(this, "ROTATION_EXCEPTION", e)
            Toast.makeText(this, "שגיאה בשינוי כיוון המסך: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestScreenshot() {
        DiagnosticLogger.record(this, "SCREENSHOT_REQUESTED", "Launching Android MediaProjection consent")
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION") startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    @Deprecated("Deprecated Android callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_WRITE_SETTINGS) {
            val approved = Settings.System.canWrite(this)
            DiagnosticLogger.record(this, if (approved) "ROTATION_PERMISSION_APPROVED" else "ROTATION_PERMISSION_DENIED", "canWrite=$approved; resultCode=$resultCode")
            if (approved) {
                Toast.makeText(this, "האישור התקבל. מבצע שינוי כיוון מסך…", Toast.LENGTH_SHORT).show()
                toggleRotation()
            } else {
                Toast.makeText(this, "לא אושרה הרשאת שינוי הגדרות מערכת. יש להפעיל את המתג עבור Phone Control.", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode == REQUEST_HOME_BACKGROUND) {
            if (resultCode != RESULT_OK || data?.data == null) {
                DiagnosticLogger.record(this, "HOME_BACKGROUND_SELECTION_CANCELLED", "resultCode=$resultCode")
                return
            }
            val uri = data.data!!
            try {
                val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                contentResolver.takePersistableUriPermission(uri, flags)
                selectedBackgroundUri = uri
                getSharedPreferences("phone_control_layout", MODE_PRIVATE).edit().putString("home_background_uri", uri.toString()).apply()
                applySavedHomeBackground()
                DiagnosticLogger.record(this, "HOME_BACKGROUND_SELECTED", "uri=$uri; flags=$flags")
                Toast.makeText(this, "הרקע נשמר בהצלחה", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                DiagnosticLogger.exception(this, "HOME_BACKGROUND_SAVE_FAILED", e)
                Toast.makeText(this, "שמירת הרקע נכשלה", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode != REQUEST_CAPTURE) return
        DiagnosticLogger.record(this, "SCREENSHOT_PERMISSION_RESULT", "resultCode=$resultCode; dataPresent=${data != null}")
        if (resultCode != RESULT_OK || data == null) {
            DiagnosticLogger.record(this, "SCREENSHOT_CONSENT_NOT_GRANTED", "resultCode=$resultCode; dataPresent=${data != null}; Android returned RESULT_CANCELED or no consent data; no screenshot was captured")
            return
        }
        try {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            DiagnosticLogger.record(this, "SCREENSHOT_CONSENT_GRANTED", "Creating MediaProjection")
            val projection = manager.getMediaProjection(resultCode, data)
            DiagnosticLogger.record(this, "SCREENSHOT_PROJECTION_CREATED", "width=${resources.displayMetrics.widthPixels}; height=${resources.displayMetrics.heightPixels}")
            val metrics = resources.displayMetrics
            val reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
            var display: VirtualDisplay? = null
            projection.registerCallback(object : android.media.projection.MediaProjection.Callback() {
                override fun onStop() { display?.release(); reader.close(); projection.unregisterCallback(this) }
            }, Handler(Looper.getMainLooper()))
            display = projection.createVirtualDisplay("PhoneControlScreenshot", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val plane = image.planes[0]
                        val buffer = plane.buffer
                        val pixelStride = plane.pixelStride
                        val rowStride = plane.rowStride
                        val bitmap = android.graphics.Bitmap.createBitmap(rowStride / pixelStride, metrics.heightPixels, android.graphics.Bitmap.Config.ARGB_8888)
                        bitmap.copyPixelsFromBuffer(buffer)
                        image.close()
                        val cropped = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, metrics.widthPixels, metrics.heightPixels)
                        val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, "PhoneControl_${System.currentTimeMillis()}.png"); put(MediaStore.Images.Media.MIME_TYPE, "image/png"); if (android.os.Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhoneControl") }
                        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        uri?.let { contentResolver.openOutputStream(it)?.use { out -> cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) } }
                        bitmap.recycle(); cropped.recycle()
                        DiagnosticLogger.record(this, if (uri != null) "SCREENSHOT_SAVED" else "SCREENSHOT_SAVE_FAILED", "uri=$uri")
                        Toast.makeText(this, if (uri != null) "צילום המסך נשמר בתמונות" else "שמירת צילום המסך נכשלה", Toast.LENGTH_LONG).show()
                    } else { DiagnosticLogger.record(this, "SCREENSHOT_EMPTY", "ImageReader returned null image"); Toast.makeText(this, "לא התקבל צילום מסך", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) { DiagnosticLogger.exception(this, "SCREENSHOT_CAPTURE_EXCEPTION", e); Toast.makeText(this, "שגיאה בצילום המסך: ${e.message}", Toast.LENGTH_LONG).show() }
                finally { display?.release(); reader.close(); projection.stop() }
            }, 450)
        } catch (e: Exception) { DiagnosticLogger.exception(this, "SCREENSHOT_SETUP_EXCEPTION", e); Toast.makeText(this, "לא ניתן לצלם מסך: ${e.message}", Toast.LENGTH_LONG).show() }
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
            setPadding(22, 22, 22, 18)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(33, 38, 72), Color.rgb(75, 52, 107), Color.rgb(18, 35, 58))).apply { cornerRadius = 0f }
        }
        val clock = TextView(this).apply {
            text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            textSize = 76f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }
        val date = TextView(this).apply {
            text = java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale("he", "IL")).format(java.util.Date())
            textSize = 17f; setTextColor(Color.rgb(238, 232, 247)); gravity = Gravity.CENTER
        }
        homePanel.addView(clock, LinearLayout.LayoutParams(-1, -2))
        homePanel.addView(date, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 4 })
        homeContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        homePanel.addView(homeContent, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 24 })
        pageDots = TextView(this).apply {
            text = "●  ○  ○  ○"
            textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.rgb(105, 205, 246))
            setPadding(0, 8, 0, 6)
        }
        homePanel.addView(pageDots)
        val quickControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, 6, 0, 2)
        }
        quickControls.addView(homeControl(android.R.drawable.ic_lock_power_off, "תפריט כיבוי") { showSystemPowerMenu() }, LinearLayout.LayoutParams(0, 54, 1f))
        quickControls.addView(homeControl(android.R.drawable.ic_lock_lock, "נעילת מסך") { lockScreen() }, LinearLayout.LayoutParams(0, 54, 1f))
        quickControls.addView(homeControl(android.R.drawable.ic_menu_rotate, "סיבוב מסך") { toggleRotation() }, LinearLayout.LayoutParams(0, 54, 1f))
        quickControls.addView(homeControl(android.R.drawable.ic_menu_camera, "צילום מסך") { requestScreenshot() }, LinearLayout.LayoutParams(0, 54, 1f))
        homePanel.addView(quickControls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 })
        val wallpaperButton = TextView(this).apply {
            text = "🖼️  בחירת רקע"
            textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = rounded(Color.rgb(58, 68, 98), 20f); setPadding(16, 9, 16, 9)
            setOnClickListener { chooseHomeBackground() }
        }
        homePanel.addView(wallpaperButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 })
        applySavedHomeBackground()
        renderHomePage()
        homePanel.setOnTouchListener { _, event -> handleHomeTouch(event) }
        root.addView(homePanel, FrameLayout.LayoutParams(-1, -1))

        drawerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 0)
            visibility = View.GONE
            setBackgroundColor(Color.rgb(20, 25, 44))
        }
        // Compact, edge-to-edge app drawer header: no redundant "all apps" title.
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(39, 47, 72), 22f)
            setPadding(14, 2, 8, 2)
        }
        search = EditText(this).apply {
            this.hint = "חיפוש אפליקציות..."
            textSize = 16f
            setSingleLine(true)
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
        val layoutControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(12, 8, 12, 4)
        }
        val layoutButtons = mutableListOf<TextView>()
        fun refreshLayoutButtons() {
            layoutButtons.forEachIndexed { index, button ->
                val columns = index + 4
                val selected = gridColumns == columns
                button.background = rounded(if (selected) Color.rgb(83, 105, 210) else Color.rgb(35, 43, 66), 18f)
                button.setTextColor(if (selected) Color.WHITE else Color.rgb(190, 199, 222))
                button.alpha = if (selected) 1f else 0.82f
            }
        }
        (4..6).forEach { columns ->
            val button = TextView(this).apply {
                text = "$columns בעמודה"
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(12, 9, 12, 9)
                setOnClickListener {
                    gridColumns = columns
                    getSharedPreferences("phone_control_layout", MODE_PRIVATE).edit().putInt("grid_columns", columns).apply()
                    // Clear children before changing GridLayout's column count.
                    // Existing child specs may retain indices/spans incompatible with the new count.
                    appsGrid.removeAllViews()
                    appsGrid.columnCount = gridColumns
                    refreshLayoutButtons()
                    renderApps(search.text?.toString().orEmpty())
                    DiagnosticLogger.record(this@MainActivity, "APP_DRAWER_LAYOUT_CHANGED", "columns=$columns")
                }
            }
            layoutButtons.add(button)
            layoutControls.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (columns != 4) marginStart = 6
            })
        }
        refreshLayoutButtons()
        appsGrid = GridLayout(this).apply { columnCount = gridColumns; useDefaultMargins = false }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(appsGrid)
        }
        drawerPanel.addView(searchRow, LinearLayout.LayoutParams(-1, -2).apply { marginStart = 12; marginEnd = 12 })
        drawerPanel.addView(layoutControls)
        drawerPanel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
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

    private fun chooseHomeBackground() {
        DiagnosticLogger.record(this, "HOME_BACKGROUND_PICKER_OPENED", "launch ACTION_OPEN_DOCUMENT image picker")
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            @Suppress("DEPRECATION") startActivityForResult(intent, REQUEST_HOME_BACKGROUND)
        } catch (e: Exception) {
            DiagnosticLogger.exception(this, "HOME_BACKGROUND_PICKER_FAILED", e)
            Toast.makeText(this, "לא ניתן לפתוח את בחירת התמונות", Toast.LENGTH_LONG).show()
        }
    }

    private fun applySavedHomeBackground() {
        val uri = selectedBackgroundUri
        if (uri == null) {
            homePanel.background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(33, 38, 72), Color.rgb(75, 52, 107), Color.rgb(18, 35, 58)))
            return
        }
        try {
            val drawable = contentResolver.openInputStream(uri)?.use { android.graphics.drawable.Drawable.createFromStream(it, null) }
            if (drawable != null) {
                if (drawable is android.graphics.drawable.BitmapDrawable) drawable.gravity = Gravity.FILL
                homePanel.background = drawable
                DiagnosticLogger.record(this, "HOME_BACKGROUND_APPLIED", "uri=$uri")
            } else {
                DiagnosticLogger.record(this, "HOME_BACKGROUND_LOAD_FAILED", "uri=$uri; stream returned no drawable")
            }
        } catch (e: Exception) {
            DiagnosticLogger.exception(this, "HOME_BACKGROUND_LOAD_EXCEPTION", e)
            Toast.makeText(this, "לא ניתן לטעון את תמונת הרקע שנשמרה", Toast.LENGTH_SHORT).show()
        }
    }

    private fun homeControl(iconRes: Int, description: String, action: () -> Unit): View {
        return FrameLayout(this).apply {
            background = rounded(Color.rgb(44, 53, 79), 22f)
            contentDescription = description; isClickable = true; isFocusable = true
            val icon = ImageView(this@MainActivity).apply {
                setImageResource(iconRes)
                setColorFilter(Color.WHITE)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            addView(icon, FrameLayout.LayoutParams(32, 32, Gravity.CENTER))
            setOnClickListener { DiagnosticLogger.record(this@MainActivity, "HOME_CONTROL_TAPPED", "control=$description"); action() }
        }
    }

    private fun renderHomePage() {
        homeContent.removeAllViews()
        val preferred = if (homePageIndex == 0)
            listOf("Gallery", "Photos", "Camera", "Phone", "Contacts", "Chrome")
        else
            listOf("Settings", "Play Store", "ChatGPT", "Messages", "Clock", "Files")
        val chosen = preferred.mapNotNull { key -> allApps.firstOrNull { it.label.contains(key, true) } }
            .distinctBy { it.packageName }.take(6)
        val grid = GridLayout(this).apply { columnCount = 3; useDefaultMargins = false }
        chosen.forEach { app ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(8, 12, 8, 12); isClickable = true
                setOnClickListener {
                    DiagnosticLogger.record(this@MainActivity, "HOME_SHORTCUT_TAPPED", "label=${app.label}; package=${app.packageName}")
                    packageManager.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) }
                }
            }
            val icon = ImageView(this).apply { setImageDrawable(app.icon); scaleType = ImageView.ScaleType.FIT_CENTER }
            cell.addView(icon, LinearLayout.LayoutParams(66, 66))
            val label = TextView(this).apply { text = app.label; textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, 7, 0, 0) }
            cell.addView(label, LinearLayout.LayoutParams(-1, -2))
            grid.addView(cell, GridLayout.LayoutParams().apply { width = 0; height = -2; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) })
        }
        homeContent.addView(grid, LinearLayout.LayoutParams(-1, -2))
        pageDots.text = if (homePageIndex == 0) "●  ○  ○  ○" else "○  ●  ○  ○"
    }

    private fun handleHomeTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { downY = event.y; downX = event.x; return true }
            MotionEvent.ACTION_UP -> {
                val dy = event.y - downY
                val dx = event.x - downX
                if (dy < -65) { DiagnosticLogger.record(this, "HOME_SWIPE_UP", "opening in-app drawer"); openDrawer() }
                else if (abs(dx) > 70 && abs(dy) < 55) {
                    homePageIndex = if (dx < 0) 1 else 0
                    renderHomePage()
                    DiagnosticLogger.record(this, "HOME_PAGE_SWIPED", "page=$homePageIndex; dx=$dx")
                }
                return true
            }
        }
        return true
    }

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        search.setText("")
        renderApps("")
        drawerPanel.alpha = 0f
        drawerPanel.translationY = 34f
        drawerPanel.visibility = View.VISIBLE
        homePanel.animate().cancel()
        homePanel.animate().alpha(0f).setDuration(110).withEndAction { homePanel.visibility = View.GONE }.start()
        drawerPanel.animate().alpha(1f).translationY(0f).setDuration(260)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }

    private fun showHome() {
        drawerOpen = false
        drawerPanel.animate().cancel()
        drawerPanel.animate().alpha(0f).translationY(24f).setDuration(150).withEndAction {
            drawerPanel.visibility = View.GONE
            drawerPanel.translationY = 0f
        }.start()
        homePanel.visibility = View.VISIBLE
        homePanel.alpha = 0f
        homePanel.animate().alpha(1f).setDuration(180).start()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken, 0)
    }

    private fun renderApps(query: String) {
        appsGrid.removeAllViews()
        val filtered = allApps.filter { it.label.contains(query, ignoreCase = true) }
        filtered.forEach { app ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(1, 8, 1, 8)
                setOnClickListener {
                    val launch = packageManager.getLaunchIntentForPackage(app.packageName)
                    if (launch != null) startActivity(launch)
                }
            }
            val icon = ImageView(this).apply {
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val iconSize = if (gridColumns >= 6) 40 else if (gridColumns == 5) 46 else 52
            item.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))
            val label = TextView(this).apply {
                text = app.label
                textSize = 11.5f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 2
                setPadding(1, 5, 1, 0)
            }
            item.addView(label, LinearLayout.LayoutParams(-1, 42))
            item.alpha = 0f
            appsGrid.addView(item, GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(0, 3, 0, 3)
            })
            val index = appsGrid.childCount - 1
            item.animate().alpha(1f).setStartDelay((index.coerceAtMost(10) * 18L)).setDuration(180).start()
        }
        if (filtered.isEmpty()) {
            val empty = TextView(this).apply {
                text = "לא נמצאו אפליקציות"
                textSize = 16f
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
            }
            appsGrid.addView(empty, GridLayout.LayoutParams().apply { width = -1; height = 100; columnSpec = GridLayout.spec(0, gridColumns) })
        }
    }

    private fun showSystemPowerMenu() {
        val service = ScreenLockAccessibilityService.activeService
        if (service == null) {
            Toast.makeText(this, "כדי לפתוח את תפריט הכיבוי המקורי, יש להפעיל את שירות הנגישות של Phone Control", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } else if (!service.showSystemPowerMenu()) {
            Toast.makeText(this, "Android לא אפשר לפתוח את תפריט הכיבוי", Toast.LENGTH_LONG).show()
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
        val message = "זיהוי הקשה כפולה דרך שירות הנגישות עדיין ניסיוני ואינו נתמך כרגע. אין להפעיל Touch Exploration לצורך Phone Control, כי הוא משנה את אופן פעולת המגע בכל הטלפון ועלול לגרום לתקיעות. הגרסה המתוקנת לא מבקשת את המצב הזה.\n\nלא נחליף את מסך הבית של Pixel ולא נציג כפתור צף. נעילת מסך ידנית מתוך האפליקציה תמשיך להשתמש בשירות הנגישות."
        android.app.AlertDialog.Builder(this)
            .setTitle("הקשה כפולה לנעילה")
            .setMessage(message)
            .setPositiveButton("פתיחת הגדרות נגישות") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNegativeButton("ביטול", null)
            .show()
    }

    companion object {
        const val ACTION_LOCK = "com.phonecontrol.ACTION_LOCK"
        const val ACTION_POWER_MENU = "com.phonecontrol.ACTION_POWER_MENU"
        const val ACTION_ROTATE = "com.phonecontrol.ACTION_ROTATE"
        const val ACTION_SCREENSHOT = "com.phonecontrol.ACTION_SCREENSHOT"
        private const val REQUEST_CAPTURE = 741
        private const val REQUEST_WRITE_SETTINGS = 742
        private const val REQUEST_HOME_BACKGROUND = 743
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        DiagnosticLogger.record(this, "BACK_PRESSED", "drawerOpen=$drawerOpen")
        if (drawerOpen) showHome() else super.onBackPressed()
    }
}
