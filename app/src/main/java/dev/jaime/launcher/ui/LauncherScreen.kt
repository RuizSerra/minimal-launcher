package dev.jaime.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.jaime.launcher.LauncherViewModel
import dev.jaime.launcher.SystemActions
import dev.jaime.launcher.data.AppInfo
import dev.jaime.launcher.data.ScreenTime
import dev.jaime.launcher.data.TextSettings
import dev.jaime.launcher.data.formatScreenTime
import dev.jaime.launcher.data.searchApps
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** List items before the A–Z apps: just the home page. */
private const val LEADING_ITEMS = 1

/** Left margin of every line of text. */
val SidePadding = 28.dp

/** App names at the default text size and spacing (Launcher settings → Text size and spacing). */
private val AppFontSize = 20.sp
private val AppRowPadding = 12.dp // above and below each name in the app list
private val FavouriteFontSize = 26.sp

/** Home page: where the clock block starts, as a share of the screen height from the top. */
private const val HomeClockShare = 1f / 6f

/** Home page: the least gap between the clock block and the favourites. */
private val HomeBlockGap = 64.dp

/** Home page: the least space above the clock block, when there are enough favourites to need it. */
private val HomeMinTop = 24.dp

/** Home page: how far above the bottom the favourites end, as a share of the screen height. */
private const val HomeBottomShare = 0.12f

/** Space between the alphabet strip and the right edge of the screen. */
private val StripEdgeGap = 4.dp

/** An app's time today, shown after its name once it reaches [MinShownUsageMs]: this much smaller. */
private const val UsageTextScale = 0.7f
private val UsageGap = 10.dp
private const val MinShownUsageMs = 60_000L

/** Opacity of the apps outside the letter you're scrubbing to on the alphabet strip. */
private const val DimmedAlpha = 0.2f

/** How far a pull down on the home page has to go to open the notification shade. */
private val PullDownDistance = 40.dp

/** Space the search row's fade takes above it; search results start above this. */
private val SearchFade = 24.dp

/**
 * The whole launcher, as one list: a full-screen home page (clock, date, status, favourites),
 * then every app A–Z below it.
 *
 * - Swipe up: the app list slides in with the search row at the bottom, and (if enabled in
 *   settings) the keyboard opens straight away. Typing swaps the list for search results.
 * - Scrolling the list or touching the alphabet strip puts the keyboard away.
 * - Swipe down on the home page: notification shade. Double-tap empty space: lock.
 */
@OptIn(ExperimentalLayoutApi::class) // statusBarsIgnoringVisibility
@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel,
    isDefaultLauncher: () -> Boolean,
    onRequestDefaultLauncher: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val screenTime by viewModel.screenTime.collectAsStateWithLifecycle()
    val autoOpenSearch by viewModel.prefs.autoOpenSearch.collectAsStateWithLifecycle()
    val textSettings by viewModel.prefs.textSettings.collectAsStateWithLifecycle()
    val customLabels by viewModel.prefs.customLabels.collectAsStateWithLifecycle()
    val appFontSize = AppFontSize * (textSettings.appList / 100f)
    // Each app's time today ("1h23m"), for apps used at least a minute. Updated with the screen
    // time (on return to the launcher, then once a minute). Personal apps only: usage stats don't
    // cover the work profile, whose apps share package names with personal ones.
    val appUsage = remember(screenTime) {
        (screenTime as? ScreenTime.Today)?.byPackage.orEmpty()
            .filterValues { it >= MinShownUsageMs }
            .mapValues { formatScreenTime(it.value) }
    }
    val usageOf: (AppInfo) -> String? = { app -> if (app.isWorkProfile) null else appUsage[app.packageName] }
    val appRowPadding = AppRowPadding * (textSettings.appListSpacing / 100f)
    // Lower bound on an app row's height (a line of text is always at least as tall as its font
    // size). Used to pad short app lists so the list can always fill the screen and fully open.
    val appRowMinHeight = with(density) { appFontSize.toDp() } + appRowPadding * 2

    // The status bar's space stays reserved even when the bar is hidden (launcher settings), so the
    // layout never jumps when it comes and goes, and nothing sits under a camera cut-out.
    val statusTop = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Height the keyboard last settled at: the letter strip is spaced to fit above it before it
    // opens, and the search row never overshoots it while the keyboard slides in.
    var keyboardHeight by remember { mutableIntStateOf(viewModel.prefs.keyboardHeight) }
    val keyboardOrNavBar = rememberKeyboardInsets { keyboardHeight }
    // The search row and navigation bar, which cover the bottom of the app list.
    val bottomChrome = SearchFieldHeight + navBottom
    val bottomChromePx = with(density) { bottomChrome.roundToPx() }
    val rowEndPadding = ScrollerWidth + StripEdgeGap + 12.dp

    val listState = rememberLazyListState()
    val searchFocus = remember { FocusRequester() }
    var fieldFocused by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    val searching = query.isNotBlank()
    val results = remember(query, apps) { searchApps(apps, query) }

    // Letter → index of its first app in the LazyColumn.
    val sectionStarts = remember(apps) {
        LinkedHashMap<String, Int>().apply {
            apps.forEachIndexed { i, app -> putIfAbsent(app.section, i + LEADING_ITEMS) }
        }
    }
    val sectionSizes = remember(apps) { apps.groupingBy { it.section }.eachCount() }
    val letters = remember(sectionStarts) { sectionStarts.keys.toList() }
    // The letter of the app in the middle of the visible list, which the alphabet strip highlights.
    val currentLetter by remember(apps, bottomChromePx) {
        derivedStateOf {
            val info = listState.layoutInfo
            val middle = (info.viewportSize.height - bottomChromePx) / 2
            val row = info.visibleItemsInfo.firstOrNull { it.index >= LEADING_ITEMS && it.offset + it.size > middle }
            row?.let { apps.getOrNull(it.index - LEADING_ITEMS)?.section }
        }
    }
    // 0 on the home page, 1 once the app list fills the screen. Read only in draw-phase lambdas.
    val listProgress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex >= LEADING_ITEMS) {
                1f
            } else {
                val pageHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
                if (pageHeight > 0) listState.firstVisibleItemScrollOffset / pageHeight.toFloat() else 0f
            }
        }
    }
    val atHome by remember { derivedStateOf { listProgress < 0.5f } }
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }

    // Alphabet scrubbing: apps outside the letter under your thumb fade back. The letter is kept
    // after you let go so the highlighted group doesn't flicker while the others fade back in.
    // Only read inside graphicsLayer, so moving between letters redraws rows without recomposing.
    var scrubLetter by remember { mutableStateOf<String?>(null) }
    var scrubbing by remember { mutableStateOf(false) }
    val dim = animateFloatAsState(
        targetValue = if (scrubbing) 1f else 0f,
        animationSpec = tween(durationMillis = if (scrubbing) 90 else 220),
        label = "dim",
    )
    val appAlpha: (AppInfo) -> Float = { app ->
        if (app.section == scrubLetter) 1f else 1f - (1f - DimmedAlpha) * dim.value
    }
    // The letter you last scrubbed to stays highlighted on the strip until you scroll, even when
    // its apps couldn't be centred (the first and last letters). Set on release, not per letter.
    var pinnedLetter by remember { mutableStateOf<String?>(null) }

    // Set once a typed query has opened its only match, so the next keystrokes (typed while the
    // app is still opening) don't launch it again. Cleared when the query gets shorter or resets.
    var autoLaunched by remember { mutableStateOf(false) }

    // The search field is always composed, so focusing it brings the keyboard up right away, already
    // set up for this field. (Also asking for the keyboard separately, before the field had connected
    // to it, sometimes made Gboard size itself for some other field first and then shrink.) If the
    // field still has focus with the keyboard put away, just show the keyboard.
    fun openKeyboard() {
        if (fieldFocused) keyboard?.show() else searchFocus.requestFocus()
    }

    fun hideKeyboard() {
        if (!fieldFocused) return
        keyboard?.hide()
        focusManager.clearFocus()
    }

    fun clearQuery() {
        query = ""
        autoLaunched = false
    }

    fun closeSearch() {
        clearQuery()
        hideKeyboard()
    }

    // Straight to the home page, no scrolling animation, from however far down the list.
    fun goHome() {
        closeSearch()
        sheet = null
        scope.launch { listState.scrollToItem(0) }
    }

    // Scrubbing to a letter brings its apps to the middle of the visible list, as far as the list
    // allows: the first letters never pull the home page into view, and the last ones can't go
    // past the end of the list.
    fun scrollToSection(letter: String) {
        val start = sectionStarts[letter] ?: return
        val info = listState.layoutInfo
        val appRows = LEADING_ITEMS until LEADING_ITEMS + apps.size
        val rowHeight = info.visibleItemsInfo.firstOrNull { it.index in appRows }?.size
        val offset = if (rowHeight == null) {
            0
        } else {
            val visible = info.viewportSize.height - bottomChromePx
            val group = rowHeight * (sectionSizes[letter] ?: 1)
            ((visible - group) / 2).coerceIn(0, rowHeight * (start - LEADING_ITEMS))
        }
        // A negative offset leaves that much of the list showing above the letter's first app.
        scope.launch { listState.scrollToItem(start, -offset) }
    }

    val launch: (AppInfo) -> Unit = { app -> SystemActions.launch(context, app) }

    // Typing that narrows the search to exactly one app opens it. Only when the query grows:
    // deleting back to a single match (e.g. from a typo with no matches) never launches anything.
    val onQueryChange: (String) -> Unit = { new ->
        val typedMore = new.length > query.length
        query = new
        if (!typedMore) {
            autoLaunched = false
        } else if (!autoLaunched) {
            searchApps(apps, new).singleOrNull()?.let { app ->
                autoLaunched = true
                launch(app)
            }
        }
    }
    val showActions: (AppInfo) -> Unit = { app ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        sheet = Sheet.AppActions(app)
    }
    val openSettings: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        sheet = Sheet.Settings
    }
    val onListOpened: () -> Unit = { if (autoOpenSearch) openKeyboard() }

    val pullDownPx = with(density) { PullDownDistance.toPx() }
    val pullDownVelocity = with(density) { 300.dp.toPx() }
    val currentOnListOpened by rememberUpdatedState(onListOpened)
    val gestures = remember(listState, pullDownPx, pullDownVelocity) {
        ListGestures(
            listState = listState,
            pullDistance = pullDownPx,
            pullVelocity = pullDownVelocity,
            onPullDown = { SystemActions.expandNotifications(context) },
            onScrollInList = {
                hideKeyboard()
                pinnedLetter = null
            },
            onOpenedList = { currentOnListOpened() },
        )
    }
    // The keyboard starts opening the moment a swipe up commits to the list, while it slides in.
    val homePageFling = rememberHomePageFling(listState) { toList ->
        if (toList && gestures.startedAtHome) onListOpened()
    }

    // Back on the home page (however you got there) → no search, no keyboard.
    LaunchedEffect(atHome) {
        if (atHome) {
            closeSearch()
            pinnedLetter = null
        }
    }
    // Home pressed while already home → back to the home page.
    LaunchedEffect(viewModel) { viewModel.homePressed.collect { goHome() } }
    // Leaving the launcher (an app opened, screen off) → come back to a clean home page.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { goHome() }
    // Screen time: refresh on return and once a minute while visible.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.refreshScreenTime()
                delay(60_000)
            }
        }
    }

    // Always swallow Back on the home screen. It clears the search, then goes back home.
    // (While the keyboard is up, the first Back just closes the keyboard.)
    BackHandler(enabled = true) {
        when {
            searching -> clearQuery()
            scrolled -> scope.launch { listState.animateScrollToItem(0) }
        }
    }

    val showSearchRow = searching || !atHome

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background),
    ) {
        if (searching) {
            SearchResults(
                results = results,
                query = query,
                fontSize = appFontSize,
                rowPadding = appRowPadding,
                usageOf = usageOf,
                contentPadding = PaddingValues(top = statusTop + 16.dp, bottom = SearchFade),
                onClick = launch,
                onLongClick = showActions,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(keyboardOrNavBar)
                    .padding(bottom = SearchFieldHeight)
                    .nestedScroll(gestures),
            )
        } else {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(top = statusTop)
                    .nestedScroll(gestures),
            ) {
                val pageHeight = maxHeight
                LazyColumn(state = listState, flingBehavior = homePageFling, modifier = Modifier.fillMaxSize()) {
                    item(key = "home", contentType = "home") {
                        HomePage(
                            screenTime = screenTime,
                            favourites = favourites,
                            customLabels = customLabels,
                            usageOf = usageOf,
                            textSettings = textSettings,
                            minHeight = pageHeight,
                            // The page starts below the status bar; the clock goes a sixth of the way down the screen.
                            clockTop = (pageHeight + statusTop) * HomeClockShare - statusTop,
                            bottomSpace = navBottom + pageHeight * HomeBottomShare,
                            onSettings = openSettings,
                            onDoubleTap = {
                                TickHaptics.get(context).tick()
                                SystemActions.lockScreen(context)
                            },
                            onClick = launch,
                            onLongClick = showActions,
                        )
                    }
                    items(apps, key = { it.key }, contentType = { "app" }) { app ->
                        AppRow(
                            label = app.label,
                            usage = usageOf(app),
                            fontSize = appFontSize,
                            verticalPadding = appRowPadding,
                            endPadding = rowEndPadding,
                            onClick = { launch(app) },
                            onLongClick = { showActions(app) },
                            alpha = { appAlpha(app) },
                        )
                    }
                    item(key = "end", contentType = "end") {
                        // Room to scroll the last apps above the search row. With only a few apps,
                        // also enough height for the list to fill the screen when opened.
                        Spacer(Modifier.height(maxOf(bottomChrome + 24.dp, pageHeight - appRowMinHeight * apps.size)))
                    }
                }
            }

            // Composed even on the home page (invisible, and ignoring touches there), so it's ready
            // before the app list slides in rather than built halfway through the slide.
            AlphabetScroller(
                letters = letters,
                active = !atHome,
                currentLetter = pinnedLetter ?: currentLetter,
                keyboardInsets = keyboardOrNavBar,
                keyboardHeight = keyboardHeight,
                onKeyboardSettled = { height ->
                    if (height != keyboardHeight) {
                        keyboardHeight = height
                        viewModel.prefs.keyboardHeight = height
                    }
                },
                onLetterSelected = { letter ->
                    hideKeyboard()
                    scrubLetter = letter
                    scrubbing = true
                    scrollToSection(letter)
                },
                onScrubEnd = {
                    scrubbing = false
                    pinnedLetter = scrubLetter
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = statusTop + 24.dp, bottom = bottomChrome, end = StripEdgeGap)
                    .width(ScrollerWidth)
                    .fillMaxHeight()
                    .graphicsLayer { alpha = ((listProgress - 0.5f) * 2f).coerceIn(0f, 1f) },
            )
        }

        // Always composed (see openKeyboard). On the home page it's invisible and tucked
        // underneath the list; it fades in on top as the app list slides up.
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            onSubmit = { results.firstOrNull()?.let(launch) },
            focusRequester = searchFocus,
            insets = keyboardOrNavBar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(if (showSearchRow) 1f else -1f)
                .graphicsLayer {
                    alpha = if (searching) 1f else ((listProgress - 0.5f) * 2f).coerceIn(0f, 1f)
                }
                .onFocusChanged { fieldFocused = it.hasFocus },
        )
    }

    LauncherSheet(
        sheet = sheet,
        viewModel = viewModel,
        isDefaultLauncher = isDefaultLauncher,
        onRequestDefaultLauncher = onRequestDefaultLauncher,
        onSheetChange = { sheet = it },
        // Text size and spacing: show whatever is being adjusted behind the sheet.
        onPreview = { target ->
            scope.launch {
                when {
                    target == PreviewTarget.AppList && atHome -> listState.animateScrollToItem(LEADING_ITEMS)
                    target == PreviewTarget.Home && !atHome -> listState.animateScrollToItem(0)
                }
            }
        },
    )
}

/**
 * Watches swipes on the list (and on search results):
 * - A swipe that starts inside the app list puts the keyboard away ([onScrollInList]).
 * - Pulling down on the home page, which can't scroll up any further, opens the notification
 *   shade after [pullDistance] or on a quick flick. Only for swipes that start on the home page
 *   and never went up, so scrolling back up through the app list never opens it.
 * - A swipe from home that drags all the way into the list counts as opening it ([onOpenedList]).
 *   The usual case, a swipe the page then snaps open, is reported by the fling instead.
 */
private class ListGestures(
    private val listState: LazyListState,
    private val pullDistance: Float,
    private val pullVelocity: Float,
    private val onPullDown: () -> Unit,
    private val onScrollInList: () -> Unit,
    private val onOpenedList: () -> Unit,
) : NestedScrollConnection {
    /** Whether the latest swipe started on the home page, as opposed to inside the app list. */
    var startedAtHome = false
        private set

    private var inGesture = false
    private var pullEligible = false
    private var pulled = 0f
    private var pullFired = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && !inGesture) {
            inGesture = true
            startedAtHome = !listState.canScrollBackward
            pullEligible = startedAtHome
            pulled = 0f
            pullFired = false
            if (!startedAtHome) onScrollInList()
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || !pullEligible) return Offset.Zero
        if (consumed.y < 0f) {
            pullEligible = false // this swipe went up, into the app list
            return Offset.Zero
        }
        if (available.y <= 0f) return Offset.Zero
        pulled += available.y
        if (!pullFired && pulled >= pullDistance) {
            pullFired = true
            onPullDown()
        }
        return Offset(0f, available.y) // take the pull, so the home page doesn't stretch
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (pullEligible && !pullFired && available.y >= pullVelocity) onPullDown()
        if (startedAtHome && listState.firstVisibleItemIndex >= LEADING_ITEMS) onOpenedList()
        inGesture = false
        pullEligible = false
        return Velocity.Zero
    }
}

/**
 * Clock, date and status starting [clockTop] down the page; favourites low down where your thumb
 * reaches, ending [bottomSpace] above the bottom. With many favourites the clock moves up to keep
 * [HomeBlockGap] between them, and past that the page grows taller than the screen.
 * Empty space: double-tap locks the screen, long-press opens launcher settings.
 */
@Composable
private fun HomePage(
    screenTime: ScreenTime,
    favourites: List<AppInfo>,
    customLabels: Map<String, String>,
    usageOf: (AppInfo) -> String?,
    textSettings: TextSettings,
    minHeight: Dp,
    clockTop: Dp,
    bottomSpace: Dp,
    onSettings: () -> Unit,
    onDoubleTap: () -> Unit,
    onClick: (AppInfo) -> Unit,
    onLongClick: (AppInfo) -> Unit,
) {
    BackgroundGestures(onDoubleTap = onDoubleTap, onLongPress = onSettings) {
        Layout(
            modifier = Modifier.fillMaxWidth(),
            content = {
                Header(
                    screenTime = screenTime,
                    onLongPress = onSettings,
                    textScale = textSettings.clock / 100f,
                    modifier = Modifier.padding(horizontal = SidePadding),
                )
                Favourites(
                    favourites = favourites,
                    customLabels = customLabels,
                    usageOf = usageOf,
                    fontSize = FavouriteFontSize * (textSettings.favourites / 100f),
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
            },
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val header = measurables[0].measure(loose)
            val favouriteRows = measurables[1].measure(loose)
            val pageHeight = minHeight.roundToPx()
            val gap = HomeBlockGap.roundToPx()
            val bottom = bottomSpace.roundToPx()

            val favouritesTop = pageHeight - bottom - favouriteRows.height
            val headerTop = minOf(clockTop.roundToPx(), favouritesTop - gap - header.height)
                .coerceAtLeast(HomeMinTop.roundToPx())
            val favouritesY = maxOf(favouritesTop, headerTop + header.height + gap)
            val height = maxOf(pageHeight, favouritesY + favouriteRows.height + bottom)
            layout(constraints.maxWidth, height) {
                header.place(0, headerTop)
                favouriteRows.place(0, favouritesY)
            }
        }
    }
}

/** Search results, bottom-up so the best match sits right above the search row (Enter opens it). */
@Composable
private fun SearchResults(
    results: List<AppInfo>,
    query: String,
    fontSize: TextUnit,
    rowPadding: Dp,
    usageOf: (AppInfo) -> String?,
    contentPadding: PaddingValues,
    onClick: (AppInfo) -> Unit,
    onLongClick: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(reverseLayout = true, contentPadding = contentPadding, modifier = modifier) {
        if (results.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No apps match “${query.trim()}”",
                    color = Palette.Tertiary,
                    fontSize = fontSize * 0.9f,
                    modifier = Modifier.padding(horizontal = SidePadding, vertical = 12.dp),
                )
            }
        }
        itemsIndexed(results, key = { _, app -> app.key }) { index, app ->
            AppRow(
                label = app.label,
                usage = usageOf(app),
                fontSize = fontSize,
                verticalPadding = rowPadding,
                color = if (index == 0) Palette.Primary else Palette.Secondary,
                endPadding = SidePadding,
                onClick = { onClick(app) },
                onLongClick = { onLongClick(app) },
            )
        }
    }
}

@Composable
private fun Favourites(
    favourites: List<AppInfo>,
    customLabels: Map<String, String>,
    usageOf: (AppInfo) -> String?,
    fontSize: TextUnit,
    onClick: (AppInfo) -> Unit,
    onLongClick: (AppInfo) -> Unit,
) {
    Column {
        if (favourites.isEmpty()) {
            Text(
                "Swipe up, then long-press an app to add it here",
                color = Palette.Primary,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = SidePadding),
            )
        }
        favourites.forEach { app ->
            key(app.key) {
                AppRow(
                    label = customLabels[app.key] ?: app.label,
                    usage = usageOf(app),
                    fontSize = fontSize,
                    verticalPadding = 10.dp,
                    endPadding = SidePadding,
                    onClick = { onClick(app) },
                    onLongClick = { onLongClick(app) },
                )
            }
        }
    }
}

/**
 * One app name, followed by its time today ([usage], smaller and fainter) when given. A long name
 * is cut short ("…") before the time is. [alpha], if given, is read at draw time, so changing it
 * never recomposes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    endPadding: Dp,
    fontSize: TextUnit = AppFontSize,
    verticalPadding: Dp = AppRowPadding,
    color: Color = Palette.Primary,
    usage: String? = null,
    alpha: (() -> Float)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (alpha != null) Modifier.graphicsLayer { this.alpha = alpha() } else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = SidePadding, end = endPadding, top = verticalPadding, bottom = verticalPadding),
    ) {
        Text(
            text = label,
            color = color,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .alignByBaseline(),
        )
        if (usage != null) {
            Text(
                text = usage,
                color = if (color == Palette.Primary) Palette.Secondary else Palette.Tertiary,
                fontSize = fontSize * UsageTextScale,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = UsageGap)
                    .alignByBaseline(),
            )
        }
    }
}

/**
 * Double-tap and long-press on empty space. Sits *behind* the content, so taps on clickable
 * children (the clock, battery, favourites…) go to them and never trigger these.
 */
@Composable
private fun BackgroundGestures(onDoubleTap: () -> Unit, onLongPress: () -> Unit, content: @Composable () -> Unit) {
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { currentOnDoubleTap() },
                        onLongPress = { currentOnLongPress() },
                    )
                },
        )
        content()
    }
}
