package dev.jaime.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.jaime.launcher.data.AppInfo
import dev.jaime.launcher.data.AppRepository
import dev.jaime.launcher.data.LauncherPrefs
import dev.jaime.launcher.data.ScreenTime
import dev.jaime.launcher.data.ScreenTimeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    val prefs = LauncherPrefs(application)
    private val screenTimeRepository = ScreenTimeRepository(application)

    /** Every launchable app; null until the first load finishes. */
    private val installedApps: StateFlow<List<AppInfo>?> =
        AppRepository(application).apps.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The A–Z list: installed apps minus hidden ones. */
    val apps: StateFlow<List<AppInfo>> =
        combine(installedApps, prefs.hidden) { apps, hidden ->
            apps.orEmpty().filterNot { it.key in hidden }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Favourites in the user's chosen order. */
    val favourites: StateFlow<List<AppInfo>> =
        combine(installedApps, prefs.favourites, prefs.hidden) { apps, keys, hidden ->
            val byKey = apps.orEmpty().associateBy { it.key }
            keys.filterNot { it in hidden }.mapNotNull { byKey[it] }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hiddenApps: StateFlow<List<AppInfo>> =
        combine(installedApps, prefs.hidden) { apps, hidden ->
            apps.orEmpty().filter { it.key in hidden }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _screenTime = MutableStateFlow<ScreenTime>(ScreenTime.Unknown)
    val screenTime: StateFlow<ScreenTime> = _screenTime.asStateFlow()

    private val _homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fires when the Home button/gesture targets the launcher. */
    val homePressed: SharedFlow<Unit> = _homePressed.asSharedFlow()

    private var screenTimeJob: Job? = null

    fun refreshScreenTime() {
        if (screenTimeJob?.isActive == true) return
        screenTimeJob = viewModelScope.launch(Dispatchers.Default) {
            _screenTime.value = runCatching { screenTimeRepository.today() }.getOrDefault(ScreenTime.Unknown)
        }
    }

    fun onHomePressed() {
        _homePressed.tryEmit(Unit)
    }

    fun toggleFavourite(app: AppInfo) {
        val current = prefs.favourites.value
        prefs.setFavourites(if (app.key in current) current - app.key else current + app.key)
    }

    /** Moves a favourite up (delta = -1) or down (+1). */
    fun moveFavourite(app: AppInfo, delta: Int) {
        // Work on the visible list so stale keys (uninstalled apps) can't swallow a move.
        val keys = favourites.value.map { it.key }.toMutableList()
        val from = keys.indexOf(app.key)
        val to = from + delta
        if (from < 0 || to !in keys.indices) return
        keys.add(to, keys.removeAt(from))
        prefs.setFavourites(keys)
    }

    /** Gives [app] a different name in favourites. Blank, or its own name, goes back to its own name. */
    fun rename(app: AppInfo, name: String) {
        val clean = name.trim()
        prefs.setCustomLabel(app.key, clean.takeIf { it.isNotEmpty() && it != app.label })
    }

    fun hide(app: AppInfo) {
        prefs.setHidden(prefs.hidden.value + app.key)
        prefs.setFavourites(prefs.favourites.value - app.key)
    }

    fun unhide(app: AppInfo) {
        prefs.setHidden(prefs.hidden.value - app.key)
    }
}
