package dev.jaime.launcher.data

import android.content.ComponentName
import android.os.UserHandle
import java.text.Normalizer
import java.util.Locale

/** One launchable activity, as shown in the list. */
data class AppInfo(
    /** Stable id for prefs (favourites / hidden): component + user serial number. */
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val isWorkProfile: Boolean,
    val isSystemApp: Boolean,
    /** Alphabet bucket: "A".."Z", or "#" for everything else. */
    val section: String,
    /** Accent-stripped, lower-cased label, used for search. */
    val searchLabel: String,
    val searchWords: List<String>,
    /** First letter of each word, e.g. "gm" for Google Maps. */
    val initials: String,
) {
    val packageName: String get() = component.packageName
}

private val combiningMarks = Regex("\\p{Mn}+")
private val wordSeparators = Regex("[^\\p{L}\\p{N}]+")

fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .lowercase(Locale.ROOT)
        .trim()

fun splitWords(normalized: String): List<String> =
    normalized.split(wordSeparators).filter { it.isNotEmpty() }

fun sectionOf(normalized: String): String {
    val first = normalized.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: return "#"
    return if (first in 'A'..'Z') first.toString() else "#"
}
