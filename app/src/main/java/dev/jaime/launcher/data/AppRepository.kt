package dev.jaime.launcher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.text.Collator

/** Lists launchable apps across the user's profiles, and keeps the list current. */
class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    /** Emits the sorted app list, then re-emits whenever apps are installed/removed/changed. */
    val apps: Flow<List<AppInfo>> = callbackFlow {
        val reloads = Channel<Unit>(Channel.CONFLATED)
        fun requestReload() {
            reloads.trySend(Unit)
        }

        // Nullable params on purpose: a stray null from the system must not crash the launcher.
        val packageCallback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = requestReload()
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = requestReload()
            override fun onPackageChanged(packageName: String?, user: UserHandle?) = requestReload()
            override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) =
                requestReload()

            override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) =
                requestReload()
        }

        // Work profile toggled on/off, or the system language changed (labels change).
        val profileReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = requestReload()
        }
        val profileFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }

        launcherApps.registerCallback(packageCallback, Handler(Looper.getMainLooper()))
        ContextCompat.registerReceiver(context, profileReceiver, profileFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Reloads run one at a time, so an older list can never overwrite a newer one.
        val loader = launch(Dispatchers.Default) {
            reloads.consumeEach { send(loadApps()) }
        }
        requestReload()

        awaitClose {
            loader.cancel()
            launcherApps.unregisterCallback(packageCallback)
            context.unregisterReceiver(profileReceiver)
        }
    }

    private fun loadApps(): List<AppInfo> {
        val myUser = Process.myUserHandle()
        val apps = userManager.userProfiles.flatMap { user ->
            val activities: List<LauncherActivityInfo> = try {
                launcherApps.getActivityList(null, user)
            } catch (e: RuntimeException) {
                emptyList() // e.g. a locked / unavailable profile
            }
            val serial = userManager.getSerialNumberForUser(user)
            val isWork = user != myUser
            activities
                .filter { it.componentName.packageName != context.packageName }
                .map { info -> info.toAppInfo(serial, isWork) }
        }

        val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
        return apps.sortedWith(
            compareBy<AppInfo> { if (it.section == "#") 1 else 0 } // "#" goes last
                .thenBy { it.section }
                .thenBy(collator) { it.label }
        )
    }

    private fun LauncherActivityInfo.toAppInfo(userSerial: Long, isWork: Boolean): AppInfo {
        val baseLabel = label?.toString()?.trim().orEmpty().ifEmpty { componentName.packageName }
        val normalized = normalizeForSearch(baseLabel)
        val words = splitWords(normalized)
        val flags = applicationInfo.flags
        return AppInfo(
            key = "${componentName.flattenToString()}#$userSerial",
            label = if (isWork) "$baseLabel (work)" else baseLabel,
            component = componentName,
            user = user,
            isWorkProfile = isWork,
            isSystemApp = (flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0,
            section = sectionOf(normalized),
            searchLabel = normalized,
            searchWords = words,
            initials = words.joinToString("") { it.take(1) },
        )
    }
}
