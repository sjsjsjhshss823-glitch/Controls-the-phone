package com.phonecontrol.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent, privacy-conscious diagnostic history.
 * Records Phone Control events only; it never records other apps' screen contents,
 * messages, contacts, passwords, or browsing activity.
 */
object DiagnosticLogger {
    private const val TAG = "PhoneControlDiag"
    private const val FILE_NAME = "phone-control-diagnostics.log"
    private const val MAX_LOG_BYTES = 2 * 1024 * 1024
    private val lock = Any()
    @Volatile private var initialized = false

    fun initialize(context: Context) {
        synchronized(lock) {
            if (initialized) return
            initialized = true
            val app = context.applicationContext
            val prefs = app.getSharedPreferences("phone_control_diagnostics", Context.MODE_PRIVATE)
            if (!prefs.contains("first_diagnostic_start")) prefs.edit().putString("first_diagnostic_start", Date().toString()).apply()
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                record(app, "UNCAUGHT_EXCEPTION", "thread=${thread.name}\n${Log.getStackTraceString(error)}")
                previous?.uncaughtException(thread, error)
            }
            record(app, "DIAGNOSTICS_STARTED", "Diagnostic history initialized; install/update metadata captured; firstDiagnosticStart=${prefs.getString("first_diagnostic_start", "unknown")}")
        }
    }

    fun record(context: Context, event: String, details: String = "") {
        try {
            val app = context.applicationContext
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date())
            val line = "[$timestamp] $event${if (details.isNotBlank()) "\n$details" else ""}\n"
            synchronized(lock) {
                val file = File(app.filesDir, FILE_NAME)
                if (file.exists() && file.length() + line.toByteArray().size > MAX_LOG_BYTES) {
                    val old = file.readLines()
                    file.writeText(old.takeLast(3000).joinToString("\n") + "\n--- LOG ROTATED ---\n")
                }
                file.appendText(line)
            }
            Log.i(TAG, "$event $details")
        } catch (e: Exception) {
            Log.e(TAG, "Could not persist diagnostic event", e)
        }
    }

    fun exception(context: Context, event: String, error: Throwable) {
        record(context, event, Log.getStackTraceString(error))
    }

    fun buildReport(context: Context): String {
        val app = context.applicationContext
        val pm = app.packageManager
        val info = try {
            if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(0))
            else @Suppress("DEPRECATION") pm.getPackageInfo(app.packageName, 0)
        } catch (e: Exception) { null }
        val prefs = app.getSharedPreferences("phone_control_diagnostics", Context.MODE_PRIVATE)
        val firstInstall = info?.firstInstallTime?.let { Date(it).toString() } ?: "unknown"
        val lastUpdate = info?.lastUpdateTime?.let { Date(it).toString() } ?: "unknown"
        val battery = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = try { cm.activeNetworkInfo?.let { "connected=${it.isConnected}, type=${it.typeName}, state=${it.state}" } ?: "none" } catch (e: Exception) { "error: ${e.message}" }
        val accessibilityEnabled = try {
            Settings.Secure.getInt(app.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        } catch (_: Exception) { false }
        val enabledServices = try { Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "none" } catch (e: Exception) { "error: ${e.message}" }
        val logFile = File(app.filesDir, FILE_NAME)
        val logText = try { if (logFile.exists()) logFile.readText() else "(no event log found)" } catch (e: Exception) { "Could not read log: ${e.message}" }

        return buildString {
            appendLine("PHONE CONTROL — FULL DIAGNOSTIC REPORT")
            appendLine("Generated: ${Date()}")
            appendLine("Report covers events recorded by this diagnostic-enabled app version since its first launch.")
            appendLine()
            appendLine("=== APP / INSTALLATION ===")
            appendLine("Package: ${app.packageName}")
            appendLine("Version: ${info?.versionName ?: "unknown"} (${if (Build.VERSION.SDK_INT >= 28) info?.longVersionCode else info?.versionCode})")
            appendLine("First install time: $firstInstall")
            appendLine("Last update time: $lastUpdate")
            appendLine("App data created marker: ${prefs.getString("first_diagnostic_start", "not recorded")}")
            appendLine("Log file bytes: ${logFile.length()}")
            appendLine()
            appendLine("=== DEVICE / ANDROID ===")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Product: ${Build.PRODUCT}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Build ID: ${Build.DISPLAY}")
            appendLine("Security patch: ${Build.VERSION.SECURITY_PATCH}")
            appendLine("Locale: ${Locale.getDefault()}")
            appendLine("Screen: ${app.resources.displayMetrics.widthPixels}x${app.resources.displayMetrics.heightPixels} @ ${app.resources.displayMetrics.density} density")
            appendLine()
            appendLine("=== LIVE SYSTEM STATE ===")
            appendLine("Battery percent: ${battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}")
            appendLine("Power save mode: ${power.isPowerSaveMode}")
            appendLine("Interactive/screen on: ${power.isInteractive}")
            appendLine("Ignoring battery optimizations: ${if (Build.VERSION.SDK_INT >= 23) power.isIgnoringBatteryOptimizations(app.packageName) else "not supported"}")
            appendLine("Network: $activeNetwork")
            appendLine("Accessibility enabled globally: $accessibilityEnabled")
            appendLine("Enabled accessibility services: $enabledServices")
            appendLine("Overlay permission: ${Settings.canDrawOverlays(app)}")
            appendLine("Write system settings permission: ${Settings.System.canWrite(app)}")
            appendLine("Notification permission: ${if (Build.VERSION.SDK_INT >= 33) app.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else "not required"}")
            appendLine("Auto-rotate setting: ${Settings.System.getInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION, -1)}")
            appendLine("User rotation setting: ${Settings.System.getInt(app.contentResolver, Settings.System.USER_ROTATION, -1)}")
            appendLine()
            appendLine("=== RECORDED EVENT HISTORY (OLDEST FIRST) ===")
            appendLine(logText)
            appendLine()
            appendLine("=== END OF REPORT ===")
        }
    }

    fun copyReport(context: Context) {
        record(context, "DIAGNOSTIC_REPORT_REQUESTED", "User pressed Copy error report")
        val report = buildReport(context)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Phone Control diagnostic report", report))
        Toast.makeText(context, "דוח האבחון המלא הועתק. אפשר להדביק אותו כאן בצ׳אט.", Toast.LENGTH_LONG).show()
        record(context, "DIAGNOSTIC_REPORT_COPIED", "characters=${report.length}")
    }
}
