package dev.jaime.launcher.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jaime.launcher.LauncherViewModel
import dev.jaime.launcher.LockService
import dev.jaime.launcher.SystemActions
import dev.jaime.launcher.data.AppInfo
import dev.jaime.launcher.data.ScreenTime
import dev.jaime.launcher.data.TextSettings
import kotlinx.coroutines.launch

sealed interface Sheet {
    data class AppActions(val app: AppInfo) : Sheet
    data object Settings : Sheet
    data object EditFavourites : Sheet
    data object HiddenApps : Sheet
    data object TextSize : Sheet
    data object Font : Sheet
    data class Rename(val app: AppInfo) : Sheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherSheet(
    sheet: Sheet?,
    viewModel: LauncherViewModel,
    isDefaultLauncher: () -> Boolean,
    onRequestDefaultLauncher: () -> Unit,
    onSheetChange: (Sheet?) -> Unit,
    onPreview: (PreviewTarget) -> Unit,
) {
    if (sheet == null) return
    val context = LocalContext.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    /** Slides the sheet away, then runs [then]. */
    fun dismiss(then: () -> Unit = {}) {
        scope.launch { state.hide() }.invokeOnCompletion {
            onSheetChange(null)
            then()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { onSheetChange(null) },
        sheetState = state,
        containerColor = Palette.SurfaceRaised,
        contentColor = Palette.Primary,
        // Text size and font: dim the home screen only a little, so you can see the change behind the sheet.
        scrimColor = Color.Black.copy(alpha = if (sheet == Sheet.TextSize || sheet == Sheet.Font) 0.15f else 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Palette.Tertiary) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            when (sheet) {
                is Sheet.AppActions -> {
                    val app = sheet.app
                    val favouriteKeys by viewModel.prefs.favourites.collectAsStateWithLifecycle()
                    val labels by viewModel.prefs.customLabels.collectAsStateWithLifecycle()
                    val isFavourite = app.key in favouriteKeys

                    SheetTitle(labels[app.key]?.takeIf { isFavourite } ?: app.label)
                    SheetItem(if (isFavourite) "Remove from favourites" else "Add to favourites") {
                        viewModel.toggleFavourite(app)
                        dismiss()
                    }
                    if (isFavourite) {
                        SheetItem("Rename") { onSheetChange(Sheet.Rename(app)) }
                        SheetItem("Reorder favourites") { onSheetChange(Sheet.EditFavourites) }
                    }
                    SheetItem("Hide") {
                        viewModel.hide(app)
                        dismiss {
                            Toast.makeText(context, "Hidden. Long-press the clock to unhide.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    SheetItem("App info") {
                        SystemActions.openAppInfo(context, app)
                        onSheetChange(null)
                    }
                    if (!app.isSystemApp) {
                        SheetItem("Uninstall") {
                            SystemActions.uninstall(context, app)
                            onSheetChange(null)
                        }
                    }
                }

                Sheet.Settings -> {
                    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
                    val hidden by viewModel.hiddenApps.collectAsStateWithLifecycle()
                    val screenTime by viewModel.screenTime.collectAsStateWithLifecycle()
                    val autoOpenSearch by viewModel.prefs.autoOpenSearch.collectAsStateWithLifecycle()
                    val hideStatusBar by viewModel.prefs.hideStatusBar.collectAsStateWithLifecycle()
                    val isDefault = isDefaultLauncher()

                    SheetTitle("Launcher")
                    SheetItem("Favourites", trailing = "${favourites.size}") { onSheetChange(Sheet.EditFavourites) }
                    SheetItem("Hidden apps", trailing = "${hidden.size}") { onSheetChange(Sheet.HiddenApps) }
                    SheetItem("Keyboard on swipe up", trailing = if (autoOpenSearch) "On" else "Off") {
                        viewModel.prefs.setAutoOpenSearch(!autoOpenSearch)
                    }
                    SheetItem("Status bar", trailing = if (hideStatusBar) "Hidden" else "Shown") {
                        viewModel.prefs.setHideStatusBar(!hideStatusBar)
                    }
                    SheetItem("Font", trailing = LocalLauncherFont.current.label) { onSheetChange(Sheet.Font) }
                    SheetItem("Text size and spacing") { onSheetChange(Sheet.TextSize) }
                    SheetItem(
                        "Screen time access",
                        trailing = if (screenTime == ScreenTime.NoPermission) "Off" else "On",
                    ) { SystemActions.openUsageAccessSettings(context) }
                    SheetItem(
                        "Double-tap to lock",
                        trailing = if (LockService.isEnabled(context)) "On" else "Off",
                    ) { SystemActions.openAccessibilitySettings(context) }
                    if (isDefault) {
                        SheetItem("Default home app", trailing = "This one") { SystemActions.openHomeSettings(context) }
                    } else {
                        SheetItem("Set as default launcher") { onRequestDefaultLauncher() }
                    }
                    SheetItem("Device settings") {
                        SystemActions.openDeviceSettings(context)
                        onSheetChange(null)
                    }
                }

                Sheet.EditFavourites -> {
                    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
                    val labels by viewModel.prefs.customLabels.collectAsStateWithLifecycle()
                    SheetTitle("Favourites")
                    if (favourites.isEmpty()) {
                        SheetHint("Long-press any app and choose “Add to favourites”.")
                    }
                    favourites.forEachIndexed { index, app ->
                        key(app.key) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, end = 8.dp),
                            ) {
                                Text(
                                    labels[app.key] ?: app.label,
                                    fontSize = 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { viewModel.moveFavourite(app, -1) }, enabled = index > 0) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                                }
                                IconButton(
                                    onClick = { viewModel.moveFavourite(app, +1) },
                                    enabled = index < favourites.lastIndex,
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                                }
                                IconButton(onClick = { viewModel.toggleFavourite(app) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove from favourites")
                                }
                            }
                        }
                    }
                }

                Sheet.HiddenApps -> {
                    val hidden by viewModel.hiddenApps.collectAsStateWithLifecycle()
                    SheetTitle("Hidden apps")
                    if (hidden.isEmpty()) {
                        SheetHint("Nothing hidden. Long-press any app and choose “Hide”.")
                    }
                    hidden.forEach { app ->
                        key(app.key) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, end = 12.dp),
                            ) {
                                Text(
                                    app.label,
                                    fontSize = 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { viewModel.unhide(app) }) { Text("Unhide") }
                            }
                        }
                    }
                }

                Sheet.TextSize -> {
                    val text by viewModel.prefs.textSettings.collectAsStateWithLifecycle()
                    // Each change also brings what it affects into view behind the sheet.
                    fun update(target: PreviewTarget, settings: TextSettings) {
                        viewModel.prefs.setTextSettings(settings)
                        onPreview(target)
                    }
                    SheetTitle("Text size and spacing")
                    StepperItem("Clock and date", text.clock, TextSettings.SizeSteps) {
                        update(PreviewTarget.Home, text.copy(clock = it))
                    }
                    StepperItem("Favourites", text.favourites, TextSettings.SizeSteps) {
                        update(PreviewTarget.Home, text.copy(favourites = it))
                    }
                    StepperItem("App list", text.appList, TextSettings.SizeSteps) {
                        update(PreviewTarget.AppList, text.copy(appList = it))
                    }
                    StepperItem("App list spacing", text.appListSpacing, TextSettings.SpacingSteps) {
                        update(PreviewTarget.AppList, text.copy(appListSpacing = it))
                    }
                }

                Sheet.Font -> {
                    val current = LocalLauncherFont.current
                    SheetTitle("Font")
                    LauncherFont.entries.forEach { font ->
                        FontItem(font, selected = font == current) { viewModel.prefs.setFont(font.id) }
                    }
                }

                is Sheet.Rename -> {
                    val labels by viewModel.prefs.customLabels.collectAsStateWithLifecycle()
                    RenameFavourite(app = sheet.app, customLabel = labels[sheet.app.key]) { name ->
                        viewModel.rename(sheet.app, name)
                        dismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        fontSize = 22.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
    )
}

@Composable
private fun SheetHint(text: String) {
    Text(
        text,
        color = Palette.Secondary,
        fontSize = 15.sp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun SheetItem(text: String, trailing: String? = null, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 15.dp),
    ) {
        Text(text, fontSize = 18.sp, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = Palette.Secondary, fontSize = 16.sp)
    }
}

/** Which part of the launcher a text setting affects, so it can be shown behind the sheet. */
enum class PreviewTarget { Home, AppList }

/** A − / + stepper through [steps], showing the current value in percent. */
@Composable
private fun StepperItem(text: String, percent: Int, steps: List<Int>, onChange: (Int) -> Unit) {
    val smaller = steps.lastOrNull { it < percent }
    val larger = steps.firstOrNull { it > percent }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp),
    ) {
        Text(text, fontSize = 18.sp, modifier = Modifier.weight(1f))
        StepButton("−", "Smaller", enabled = smaller != null) { smaller?.let(onChange) }
        Text(
            "$percent%",
            color = Palette.Secondary,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp),
        )
        StepButton("+", "Larger", enabled = larger != null) { larger?.let(onChange) }
    }
}

@Composable
private fun StepButton(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Text(symbol, fontSize = 24.sp, color = if (enabled) Palette.Primary else Palette.Tertiary)
    }
}

/**
 * Name field for a favourite, opened with the keyboard up and the current name selected, so
 * typing replaces it. Done or Save keeps it; a blank name goes back to the app's own name.
 */
@Composable
private fun RenameFavourite(app: AppInfo, customLabel: String?, onRename: (String) -> Unit) {
    val initial = customLabel ?: app.label
    var name by remember(app.key) { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    SheetTitle("Rename")
    BasicTextField(
        value = name,
        onValueChange = { name = it },
        singleLine = true,
        textStyle = TextStyle(color = Palette.Primary, fontSize = 20.sp, fontFamily = LocalTextStyle.current.fontFamily),
        cursorBrush = SolidColor(Palette.Primary),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onRename(name.text) }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .focusRequester(focusRequester),
        decorationBox = { innerTextField ->
            Column {
                Box {
                    if (name.text.isEmpty()) Text(app.label, color = Palette.Tertiary, fontSize = 20.sp)
                    innerTextField()
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Palette.Tertiary),
                )
            }
        },
    )
    SheetItem("Save") { onRename(name.text) }
    if (customLabel != null) SheetItem("Use its own name", trailing = app.label) { onRename(app.label) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
}

/** A font's name, written in that font; the one in use is ticked. */
@Composable
private fun FontItem(font: LauncherFont, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 13.dp),
    ) {
        Text(font.label, fontFamily = font.family(LocalContext.current), fontSize = 20.sp, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Default.Check, contentDescription = "In use", tint = Palette.Primary)
    }
}
