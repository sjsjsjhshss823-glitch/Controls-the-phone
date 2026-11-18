package com.phonecontrol.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast

class ControlActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        DiagnosticLogger.record(context, "NOTIFICATION_ACTION_RECEIVED", "action=${intent?.action ?: "none"}")
        when (intent?.action) {
            MainActivity.ACTION_LOCK -> lockScreen(context)
            MainActivity.ACTION_POWER_MENU -> showPowerMenu(context)
            MainActivity.ACTION_ROTATE -> rotateScreen(context)
        }
    }

    private fun lockScreen(context: Context) {
        DiagnosticLogger.record(context, "LOCK_SCREEN_REQUESTED", "accessibilityServiceActive=${ScreenLockAccessibilityService.activeService != null}")
        val service = ScreenLockAccessibilityService.activeService
        if (service?.lockScreenNow() != true) {
            DiagnosticLogger.record(context, "LOCK_SCREEN_FAILED", "Accessibility service unavailable or global action rejected")
            Toast.makeText(context, "כדי לנעול את המסך, יש להפעיל את שירות הנגישות של Phone Control", Toast.LENGTH_LONG).show()
            openAccessibilitySettings(context)
        }
    }

    private fun showPowerMenu(context: Context) {
        DiagnosticLogger.record(context, "POWER_MENU_REQUESTED", "accessibilityServiceActive=${ScreenLockAccessibilityService.activeService != null}")
        val service = ScreenLockAccessibilityService.activeService
        if (service?.showSystemPowerMenu() != true) {
            DiagnosticLogger.record(context, "POWER_MENU_FAILED", "Accessibility service unavailable or global action rejected")
            Toast.makeText(context, "כדי לפתוח את תפריט הכיבוי, יש להפעיל את שירות הנגישות של Phone Control", Toast.LENGTH_LONG).show()
            openAccessibilitySettings(context)
        }
    }

    private fun rotateScreen(context: Context) {
        DiagnosticLogger.record(context, "ROTATION_REQUESTED_FROM_CONTROL", "canWrite=${Settings.System.canWrite(context)}")
        if (!Settings.System.canWrite(context)) {
            DiagnosticLogger.record(context, "ROTATION_FAILED_PERMISSION", "WRITE_SETTINGS not granted")
            Toast.makeText(context, "יש לאשר ל־Phone Control שינוי הגדרות מערכת", Toast.LENGTH_LONG).show()
            val settingsIntent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            return
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val currentRotation = windowManager.defaultDisplay.rotation
        val targetRotation = if (currentRotation == Surface.ROTATION_0 || currentRotation == Surface.ROTATION_180) {
            Surface.ROTATION_90
        } else {
            Surface.ROTATION_0
        }

        val autoRotateDisabled = Settings.System.putInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0
        )
        val rotationWritten = Settings.System.putInt(
            context.contentResolver,
            Settings.System.USER_ROTATION,
            targetRotation
        )
        val savedRotation = Settings.System.getInt(
            context.contentResolver,
            Settings.System.USER_ROTATION,
            -1
        )
        DiagnosticLogger.record(context, if (autoRotateDisabled && rotationWritten && savedRotation == targetRotation) "ROTATION_SUCCEEDED" else "ROTATION_FAILED", "target=$targetRotation; autoRotateDisabled=$autoRotateDisabled; rotationWritten=$rotationWritten; saved=$savedRotation")
        if (autoRotateDisabled && rotationWritten && savedRotation == targetRotation) {
            Toast.makeText(
                context,
                if (targetRotation == Surface.ROTATION_90) "הוגדר סיבוב לרוחב" else "הוגדר סיבוב לאורך",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(context, "לא ניתן לשנות את כיוון המסך. בדוק את הרשאת שינוי הגדרות המערכת.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
