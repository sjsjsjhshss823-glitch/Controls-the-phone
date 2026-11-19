package com.phonecontrol.launcher

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

class ScreenLockAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticLogger.initialize(this)
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        DiagnosticLogger.record(this, "ACCESSIBILITY_SERVICE_CONNECTED",
            "serviceConnected=true; touchExplorationEnabled=${manager.isTouchExplorationEnabled}; touchExplorationRequestDisabled=true; api=${android.os.Build.VERSION.SDK_INT}")
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    // Deliberately do not request or process touch-exploration gestures.
    // This service must not change system-wide touch behavior.
    override fun onGesture(gestureId: Int): Boolean = super.onGesture(gestureId)

    override fun onInterrupt() {
        DiagnosticLogger.record(this, "ACCESSIBILITY_SERVICE_INTERRUPTED", "Android called onInterrupt")
    }

    fun lockScreenNow(): Boolean {
        val result = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        DiagnosticLogger.record(this, if (result) "LOCK_SCREEN_ACTION_ACCEPTED" else "LOCK_SCREEN_ACTION_REJECTED", "performGlobalAction result=${result}")
        return result
    }

    fun showSystemPowerMenu(): Boolean {
        val result = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        DiagnosticLogger.record(this, if (result) "POWER_DIALOG_ACTION_ACCEPTED" else "POWER_DIALOG_ACTION_REJECTED", "performGlobalAction result=${result}")
        return result
    }

    override fun onDestroy() {
        DiagnosticLogger.record(this, "ACCESSIBILITY_SERVICE_DESTROYED", "Accessibility service disconnected")
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var activeService: ScreenLockAccessibilityService? = null
            private set
    }
}
