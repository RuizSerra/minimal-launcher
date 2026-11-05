package dev.jaime.launcher.data

/**
 * Ranks matches: label prefix, then word prefix ("maps" → Google Maps),
 * then substring, then initials ("gm" → Google Maps). Alphabetical within a rank.
 */
fun searchApps(apps: List<AppInfo>, query: String): List<AppInfo> {
    val q = normalizeForSearch(query)
    if (q.isEmpty()) return emptyList()
    val compact = q.replace(" ", "")

    return apps
        .mapNotNull { app ->
            val rank = when {
                app.searchLabel.startsWith(q) -> 0
                app.searchWords.any { it.startsWith(q) } -> 1
                app.searchLabel.contains(q) -> 2
                compact.length >= 2 && app.initials.startsWith(compact) -> 3
                else -> return@mapNotNull null
            }
            rank to app
        }
        .sortedBy { it.first } // stable, so alphabetical order is kept within a rank
        .map { it.second }
}
