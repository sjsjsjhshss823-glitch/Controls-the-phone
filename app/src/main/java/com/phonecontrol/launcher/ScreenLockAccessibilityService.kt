package com.phonecontrol.launcher

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class ScreenLockAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticLogger.initialize(this)
        DiagnosticLogger.record(this, "ACCESSIBILITY_SERVICE_CONNECTED", "Screen lock and power dialog actions available")
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    fun lockScreenNow(): Boolean = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    fun showSystemPowerMenu(): Boolean = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)

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
