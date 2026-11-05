package dev.jaime.launcher.data

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

sealed interface ScreenTime {
    data object Unknown : ScreenTime
    data object NoPermission : ScreenTime
    /** [millis]: total today. [byPackage]: each app's share of it, by package name. */
    data class Today(val millis: Long, val byPackage: Map<String, Long> = emptyMap()) : ScreenTime
}

/** "12m", "1h23m", or "2h" on the hour. */
fun formatScreenTime(millis: Long): String {
    val minutes = millis / 60_000
    val hours = minutes / 60
    return when {
        hours == 0L -> "${minutes}m"
        minutes % 60 == 0L -> "${hours}h"
        else -> "${hours}h${minutes % 60}m"
    }
}

class ScreenTimeRepository(private val context: Context) {
    private val usageStats = context.getSystemService(UsageStatsManager::class.java)
    private val appOps = context.getSystemService(AppOpsManager::class.java)

    fun hasPermission(): Boolean {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /**
     * Time spent in apps since local midnight, in total and per app, not counting launchers or
     * System UI.
     *
     * Built from raw resume/pause events rather than the pre-aggregated daily buckets,
     * which are not aligned to local midnight and can be off by hours.
     */
    fun today(): ScreenTime {
        if (!hasPermission()) return ScreenTime.NoPermission

        val now = System.currentTimeMillis()
        val midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val excluded = excludedPackages()
        // Start a bit before midnight so an app already open at 00:00 is counted from 00:00.
        val events = usageStats.queryEvents(midnight - LOOKBACK_MS, now) ?: return ScreenTime.Today(0)

        var total = 0L
        val byPackage = HashMap<String, Long>()
        var fgPackage: String? = null
        var fgClass: String? = null
        var fgSince = 0L

        fun closeAt(time: Long) {
            val pkg = fgPackage ?: return
            if (pkg !in excluded) {
                val start = maxOf(fgSince, midnight)
                val end = minOf(time, now)
                if (end > start) {
                    total += end - start
                    byPackage[pkg] = (byPackage[pkg] ?: 0L) + (end - start)
                }
            }
            fgPackage = null
            fgClass = null
        }

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                ACTIVITY_RESUMED -> {
                    if (event.packageName == fgPackage) {
                        fgClass = event.className // moved to another activity of the same app
                    } else {
                        closeAt(event.timeStamp)
                        fgPackage = event.packageName
                        fgClass = event.className
                        fgSince = event.timeStamp
                    }
                }

                ACTIVITY_PAUSED ->
                    if (event.packageName == fgPackage && event.className == fgClass) closeAt(event.timeStamp)

                SCREEN_NON_INTERACTIVE, KEYGUARD_SHOWN, DEVICE_SHUTDOWN -> closeAt(event.timeStamp)
            }
        }
        closeAt(now)
        return ScreenTime.Today(total, byPackage)
    }

    private fun excludedPackages(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val launchers = context.packageManager.queryIntentActivities(home, 0)
            .filter { it.priority >= 0 } // skips Settings' "FallbackHome" (priority -1000)
            .map { it.activityInfo.packageName }
        return launchers.toSet() + context.packageName + "com.android.systemui"
    }

    private companion object {
        const val LOOKBACK_MS = 6 * 60 * 60 * 1000L

        // UsageEvents.Event constants, inlined to avoid API-level lint noise.
        const val ACTIVITY_RESUMED = 1
        const val ACTIVITY_PAUSED = 2
        const val SCREEN_NON_INTERACTIVE = 16
        const val KEYGUARD_SHOWN = 17
        const val DEVICE_SHUTDOWN = 26
    }
}
