package dev.jaime.launcher.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Text size of each part of the launcher, and the app list's line spacing, in percent of the default. */
data class TextSettings(
    val clock: Int = 100,
    val favourites: Int = 100,
    val appList: Int = 100,
    val appListSpacing: Int = 100,
) {
    companion object {
        /** The values the − / + buttons in settings step through. */
        val SizeSteps = listOf(70, 80, 90, 100, 110, 125, 140, 160, 180, 200)
        val SpacingSteps = listOf(25, 50, 75, 100, 125, 150, 175, 200)
    }
}

/** Favourites (ordered), hidden apps and favourite names, keyed by [AppInfo.key], plus launcher options. */
class LauncherPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    private val _favourites = MutableStateFlow(
        prefs.getString(KEY_FAVOURITES, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
    )
    val favourites: StateFlow<List<String>> = _favourites.asStateFlow()

    private val _hidden = MutableStateFlow(prefs.getStringSet(KEY_HIDDEN, null)?.toSet().orEmpty())
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()

    /** Names you've given favourites, e.g. "Weather" for Weawow. Apps without one use their own name. */
    private val _customLabels = MutableStateFlow(
        prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(LABEL_PREFIX) && value is String) key.removePrefix(LABEL_PREFIX) to value else null
        }.toMap()
    )
    val customLabels: StateFlow<Map<String, String>> = _customLabels.asStateFlow()

    /** Open the keyboard as soon as a swipe up opens the app list. */
    private val _autoOpenSearch = MutableStateFlow(prefs.getBoolean(KEY_AUTO_OPEN_SEARCH, true))
    val autoOpenSearch: StateFlow<Boolean> = _autoOpenSearch.asStateFlow()

    private val _textSettings = MutableStateFlow(
        TextSettings(
            clock = prefs.getInt(KEY_SIZE_CLOCK, 100),
            favourites = prefs.getInt(KEY_SIZE_FAVOURITES, 100),
            appList = prefs.getInt(KEY_SIZE_APP_LIST, 100),
            appListSpacing = prefs.getInt(KEY_SPACING_APP_LIST, 100),
        )
    )
    val textSettings: StateFlow<TextSettings> = _textSettings.asStateFlow()

    /** Hide the status bar (time, signal, battery) on the launcher. A swipe down from the top edge shows it briefly. */
    private val _hideStatusBar = MutableStateFlow(prefs.getBoolean(KEY_HIDE_STATUS_BAR, true))
    val hideStatusBar: StateFlow<Boolean> = _hideStatusBar.asStateFlow()

    fun setHideStatusBar(hidden: Boolean) {
        _hideStatusBar.value = hidden
        prefs.edit { putBoolean(KEY_HIDE_STATUS_BAR, hidden) }
    }

    /** Launcher font, by id (see LauncherFont). */
    private val _font = MutableStateFlow(prefs.getString(KEY_FONT, null) ?: "system")
    val font: StateFlow<String> = _font.asStateFlow()

    fun setFont(id: String) {
        _font.value = id
        prefs.edit { putString(KEY_FONT, id) }
    }

    fun setAutoOpenSearch(enabled: Boolean) {
        _autoOpenSearch.value = enabled
        prefs.edit { putBoolean(KEY_AUTO_OPEN_SEARCH, enabled) }
    }

    fun setTextSettings(settings: TextSettings) {
        _textSettings.value = settings
        prefs.edit {
            putInt(KEY_SIZE_CLOCK, settings.clock)
            putInt(KEY_SIZE_FAVOURITES, settings.favourites)
            putInt(KEY_SIZE_APP_LIST, settings.appList)
            putInt(KEY_SPACING_APP_LIST, settings.appListSpacing)
        }
    }

    /** Sets the name shown for [appKey], or goes back to the app's own name when [label] is null. */
    fun setCustomLabel(appKey: String, label: String?) {
        _customLabels.value = if (label == null) _customLabels.value - appKey else _customLabels.value + (appKey to label)
        prefs.edit { if (label == null) remove(LABEL_PREFIX + appKey) else putString(LABEL_PREFIX + appKey, label) }
    }

    /** Height (px, above the navigation bar) the keyboard last settled at. Sizes the letter strip. */
    var keyboardHeight: Int
        get() = prefs.getInt(KEY_KEYBOARD_HEIGHT, 0)
        set(value) = prefs.edit { putInt(KEY_KEYBOARD_HEIGHT, value) }

    var askedForHomeRole: Boolean
        get() = prefs.getBoolean(KEY_ASKED_HOME_ROLE, false)
        set(value) = prefs.edit { putBoolean(KEY_ASKED_HOME_ROLE, value) }

    fun setFavourites(keys: List<String>) {
        val distinct = keys.distinct()
        _favourites.value = distinct
        prefs.edit { putString(KEY_FAVOURITES, distinct.joinToString("\n")) }
    }

    fun setHidden(keys: Set<String>) {
        _hidden.value = keys
        prefs.edit { putStringSet(KEY_HIDDEN, keys) }
    }

    private companion object {
        const val KEY_FAVOURITES = "favourites"
        const val KEY_HIDDEN = "hidden"
        const val KEY_ASKED_HOME_ROLE = "asked_home_role"
        const val KEY_AUTO_OPEN_SEARCH = "auto_open_search"
        const val KEY_HIDE_STATUS_BAR = "hide_status_bar"
        const val KEY_KEYBOARD_HEIGHT = "keyboard_height"
        const val KEY_FONT = "font"
        const val KEY_SIZE_CLOCK = "text_size_clock"
        const val KEY_SIZE_FAVOURITES = "text_size_favourites"
        const val KEY_SIZE_APP_LIST = "text_size_app_list"
        const val KEY_SPACING_APP_LIST = "line_spacing_app_list"
        const val LABEL_PREFIX = "label:"
    }
}
