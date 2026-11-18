package com.phonecontrol.launcher

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

class ScreenLockAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticLogger.initialize(this)
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val flags = serviceInfo.flags
        DiagnosticLogger.record(this, "ACCESSIBILITY_SERVICE_CONNECTED",
            "serviceInfoFlags=$flags; requestTouchExploration=${(flags and android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE) != 0}; touchExplorationEnabled=${manager.isTouchExplorationEnabled}; api=${android.os.Build.VERSION.SDK_INT}")
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onGesture(gestureId: Int): Boolean {
        val name = when (gestureId) {
            GESTURE_DOUBLE_TAP -> "DOUBLE_TAP"
            GESTURE_DOUBLE_TAP_AND_HOLD -> "DOUBLE_TAP_AND_HOLD"
            else -> "OTHER"
        }
        DiagnosticLogger.record(this, "ACCESSIBILITY_GESTURE_RECEIVED", "gestureId=$gestureId; gestureName=$name")
        if (gestureId == GESTURE_DOUBLE_TAP) {
            val accepted = lockScreenNow()
            DiagnosticLogger.record(this, if (accepted) "DOUBLE_TAP_LOCK_REQUEST_ACCEPTED" else "DOUBLE_TAP_LOCK_REQUEST_REJECTED",
                "gestureId=$gestureId; globalActionLockScreenAccepted=$accepted")
            return accepted
        }
        return super.onGesture(gestureId)
    }

    override fun onInterrupt() {
        DiagnosticLogger.record(this, "ACCESSIBILITY_SERVICE_INTERRUPTED", "Android called onInterrupt")
    }

    fun lockScreenNow(): Boolean {
        val result = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        DiagnosticLogger.record(this, if (result) "LOCK_SCREEN_ACTION_ACCEPTED" else "LOCK_SCREEN_ACTION_REJECTED", "performGlobalAction result=$result")
        return result
    }

    fun showSystemPowerMenu(): Boolean {
        val result = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        DiagnosticLogger.record(this, if (result) "POWER_DIALOG_ACTION_ACCEPTED" else "POWER_DIALOG_ACTION_REJECTED", "performGlobalAction result=$result")
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
