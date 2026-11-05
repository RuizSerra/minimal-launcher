package dev.jaime.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import dev.jaime.launcher.SystemActions
import dev.jaime.launcher.data.ScreenTime
import dev.jaime.launcher.data.formatScreenTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Time, date, battery and screen time. Tap the time for the clock app, the date for the calendar,
 * the battery for battery usage, the screen time for the Digital Wellbeing dashboard; long-press
 * the time or date for launcher settings.
 * [textScale] multiplies every text size (1 = default).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Header(screenTime: ScreenTime, onLongPress: () -> Unit, textScale: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clock by rememberClock()
    val battery by rememberBattery()

    val timeText = remember(clock) {
        clock.time.format(DateTimeFormatter.ofPattern(if (clock.is24Hour) "HH:mm" else "h:mm"))
    }
    val dateText = remember(clock.time.toLocalDate()) {
        val pattern = runCatching { DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM") }
            .getOrDefault("EEEE, d MMMM")
        runCatching { clock.time.format(DateTimeFormatter.ofPattern(pattern)) }
            .getOrElse { clock.time.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) }
    }

    val clockSize = 44.sp * textScale
    val dateSize = 18.sp * textScale
    val statusSize = 15.sp * textScale
    val lineGap = LineGap * textScale
    val font = LocalLauncherFont.current
    val capRatio = remember(font) { capHeightRatio(font.typeface(context)) }

    // Lines placed by their baselines, so the gaps between them look the same whatever the font.
    Layout(
        modifier = modifier,
        content = {
            Text(
                text = timeText,
                color = Palette.Primary,
                fontSize = clockSize,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { SystemActions.openClock(context) },
                    onLongClick = onLongPress,
                ),
            )
            Text(
                text = dateText,
                color = Palette.Primary,
                fontSize = dateSize,
                modifier = Modifier.combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { SystemActions.openCalendar(context) },
                    onLongClick = onLongPress,
                ),
            )
            StatusLine(battery, screenTime, statusSize)
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val (clock, date, status) = measurables.map { it.measure(loose) }
        val gap = lineGap.toPx()

        // Top of the next line's capitals sits [gap] below the previous line's baseline.
        fun below(previousY: Int, previous: Placeable, next: Placeable, nextSize: TextUnit): Int {
            val previousBaseline = previous[FirstBaseline]
            val nextBaseline = next[FirstBaseline]
            if (previousBaseline == AlignmentLine.Unspecified || nextBaseline == AlignmentLine.Unspecified) {
                return previousY + previous.height // an empty line has no baseline
            }
            val capHeight = nextSize.toPx() * capRatio
            return (previousY + previousBaseline + gap + capHeight - nextBaseline).roundToInt()
        }

        val dateY = below(0, clock, date, dateSize)
        val statusY = below(dateY, date, status, statusSize)
        val width = maxOf(clock.width, date.width, status.width)
        val height = maxOf(dateY + date.height, statusY + status.height)
        layout(width, height) {
            clock.place(0, 0)
            date.place(0, dateY)
            status.place(0, statusY)
        }
    }
}

/** Space from one header line's baseline to the top of the capitals on the line below. */
private val LineGap = 22.dp


@Composable
private fun StatusLine(battery: BatteryState?, screenTime: ScreenTime, fontSize: TextUnit) {
    val context = LocalContext.current
    val noRipple = remember { MutableInteractionSource() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (battery != null) {
            Text(
                text = if (battery.charging) "${battery.percent}% charging" else "${battery.percent}%",
                color = Palette.Primary,
                fontSize = fontSize,
                modifier = Modifier.clickable(noRipple, indication = null) { SystemActions.openBatteryUsage(context) },
            )
        }
        when (screenTime) {
            is ScreenTime.Today -> {
                Gap(fontSize)
                Text(
                    text = formatScreenTime(screenTime.millis),
                    color = Palette.Primary,
                    fontSize = fontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable(noRipple, indication = null) { SystemActions.openScreenTime(context) },
                )
            }

            ScreenTime.NoPermission -> {
                Gap(fontSize)
                Text(
                    text = "enable screen time",
                    color = Palette.Primary,
                    fontSize = fontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable(noRipple, indication = null) { SystemActions.openUsageAccessSettings(context) },
                )
            }

            ScreenTime.Unknown -> Unit
        }
    }
}

@Composable
private fun Gap(fontSize: TextUnit) {
    // About one character wide, so it grows with the text size.
    Spacer(Modifier.width(with(LocalDensity.current) { fontSize.toDp() }))
}

private data class ClockState(val time: LocalDateTime, val is24Hour: Boolean)

private fun currentClock(context: Context) =
    ClockState(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES), DateFormat.is24HourFormat(context))

/** Updates on every minute tick, and on time / time-zone / 24h-setting changes. Only while visible. */
@Composable
private fun rememberClock(): State<ClockState> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(currentClock(context)) }
    LifecycleStartEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                state.value = currentClock(context)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        state.value = currentClock(context)
        onStopOrDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

private data class BatteryState(val percent: Int, val charging: Boolean)

private fun Intent.toBatteryState(): BatteryState? {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryState(level * 100 / scale, charging)
}

/** Battery level from the sticky ACTION_BATTERY_CHANGED broadcast; no permission needed. */
@Composable
private fun rememberBattery(): State<BatteryState?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<BatteryState?>(null) }
    LifecycleStartEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                state.value = intent.toBatteryState()
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        sticky?.toBatteryState()?.let { state.value = it }
        onStopOrDispose { context.unregisterReceiver(receiver) }
    }
    return state
}
