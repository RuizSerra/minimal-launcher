package dev.jaime.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import dev.jaime.launcher.data.AppInfo

/** Thin wrappers around the system intents the launcher fires. */
object SystemActions {

    fun launch(context: Context, app: AppInfo) {
        try {
            context.getSystemService(LauncherApps::class.java)
                .startMainActivity(app.component, app.user, null, null)
        } catch (e: RuntimeException) {
            Toast.makeText(context, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAppInfo(context: Context, app: AppInfo) {
        try {
            context.getSystemService(LauncherApps::class.java)
                .startAppDetailsActivity(app.component, app.user, null, null)
        } catch (e: RuntimeException) {
            Toast.makeText(context, "Can't open app info", Toast.LENGTH_SHORT).show()
        }
    }

    fun uninstall(context: Context, app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
        if (app.isWorkProfile) intent.putExtra(Intent.EXTRA_USER, app.user)
        context.tryStart(intent)
    }

    fun openUsageAccessSettings(context: Context) {
        // Try this app's own page first; not every device supports it.
        val ownPage = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        if (!context.tryStart(ownPage)) context.tryStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    /**
     * The clock app on its own main screen. There's no standard "open the clock app" intent, so
     * this finds the app that handles "show alarms" and opens it like the launcher would, falling
     * back to its alarms (or a chooser, if there are several clock apps).
     */
    fun openClock(context: Context) {
        val showAlarms = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        @Suppress("DEPRECATION")
        val clockPackage = context.packageManager
            .resolveActivity(showAlarms, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            ?.takeIf { it != "android" } // the chooser: several clock apps and no default
        val clockApp = clockPackage?.let { context.packageManager.getLaunchIntentForPackage(it) }
        if (clockApp == null || !context.tryStart(clockApp)) context.tryStart(showAlarms)
    }

    /** The calendar app (the default one, if there's a choice), else today in whatever handles calendar links. */
    fun openCalendar(context: Context) {
        val calendarApp = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
        if (context.tryStart(calendarApp)) return
        val today = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .appendPath(System.currentTimeMillis().toString())
            .build()
        context.tryStart(Intent(Intent.ACTION_VIEW, today))
    }

    /**
     * Today's screen time, app by app: Samsung's Digital Wellbeing dashboard, else Google's, else
     * Settings. Neither has a public API; these are the dashboards' own intent actions.
     */
    fun openScreenTime(context: Context) {
        val dashboards = listOf(
            Intent("com.samsung.android.forest.OPEN_DASHBOARD").setPackage("com.samsung.android.forest"),
            Intent("com.google.android.apps.wellbeing.action.APP_USAGE_DASHBOARD")
                .setPackage("com.google.android.apps.wellbeing"),
            Intent("com.google.android.apps.wellbeing.action.HOME").setPackage("com.google.android.apps.wellbeing"),
        )
        if (dashboards.none { context.tryStart(it) }) openDeviceSettings(context)
    }

    fun openBatteryUsage(context: Context) = context.tryStart(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))

    fun openHomeSettings(context: Context) = context.tryStart(Intent(Settings.ACTION_HOME_SETTINGS))

    fun openDeviceSettings(context: Context) = context.tryStart(Intent(Settings.ACTION_SETTINGS))

    fun openAccessibilitySettings(context: Context) = context.tryStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    /**
     * Pulls down the notification shade. Uses StatusBarManager.expandNotificationsPanel, a hidden
     * but app-callable API that most minimal launchers rely on. If a device blocks it, falls back
     * to the accessibility service when that's turned on.
     */
    @SuppressLint("WrongConstant", "PrivateApi", "DiscouragedPrivateApi")
    fun expandNotifications(context: Context) {
        val expanded = try {
            val statusBar = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(statusBar)
            true
        } catch (e: Exception) {
            false
        }
        if (!expanded) LockService.openNotifications()
    }

    /** Locks the screen like the power button. Points you to Accessibility settings if needed. */
    fun lockScreen(context: Context) {
        if (LockService.lock()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Toast.makeText(context, "Double-tap to lock needs Android 9 or newer", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(context, "To lock with a double tap, turn on “Minimal Launcher” here", Toast.LENGTH_LONG).show()
        openAccessibilitySettings(context)
    }

    private fun Context.tryStart(intent: Intent): Boolean = try {
        if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
