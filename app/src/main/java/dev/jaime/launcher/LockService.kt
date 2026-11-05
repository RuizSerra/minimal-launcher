package dev.jaime.launcher

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service whose only job is locking the screen when you double-tap the home
 * screen. It locks the same way the power button does, so fingerprint and face unlock keep
 * working (device-admin locking would force your PIN every time). It subscribes to no
 * accessibility events and can't read the screen.
 *
 * Turn it on once in Settings → Accessibility → Minimal Launcher.
 */
class LockService : AccessibilityService() {

    override fun onServiceConnected() {
        connected = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        connected = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connected = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        // Only set while the system has the service bound, and cleared on unbind, so it can't leak.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var connected: LockService? = null

        /** Whether the user has turned the service on in Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = ComponentName(context, LockService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** Locks the screen. False if the service isn't on, or on Android 8 (no lock action there). */
        fun lock(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                connected?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true

        /** Pulls down the notification shade. False if the service isn't on. */
        fun openNotifications(): Boolean =
            connected?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) == true
    }
}
