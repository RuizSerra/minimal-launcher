package dev.jaime.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt

/** Width of the touch strip. Letters bulge out to the left of it while you scrub. */
val ScrollerWidth = 44.dp

// Tuning knobs.
private val MaxLetterSpacing = 20.dp // distance between letters, when there's room
private val TouchMarginTop = 24.dp // touch area above the first letter, for a thumb that lands a little high
private val BottomGap = 28.dp // keeps the last letter clear of the search row and the fade above it
private val BulgeDistance = 76.dp // how far the letter under your thumb moves left
private val BulgeSpread = 60.dp // how far up/down the curve reaches

/**
 * Vertical A–Z index, packed at the bottom of its area, right above the search row, so it's
 * within easy reach of your thumb. Drag along it to jump through the list: letters near your
 * thumb curve outwards so they stay visible, and each new letter gives a haptic tick.
 * [onLetterSelected] fires on each new letter, [onScrubEnd] when the thumb lifts.
 *
 * While the keyboard is open the letters sit on top of it ([keyboardInsets]: the top of the keyboard
 * or navigation bar, see [rememberKeyboardInsets]). Touching them puts the keyboard
 * away (see the caller), so they hold still under your thumb until you let go, then glide down.
 * Touches above the letters fall through to the list, as do all touches while not [active].
 *
 * The letters are always spaced to fit above the keyboard, open or not, using [keyboardHeight]:
 * the height (px, above the navigation bar) the keyboard last settled at, reported through
 * [onKeyboardSettled]. So when the keyboard opens or closes they move as one rigid block, and
 * never squeeze or stretch with its animation (keyboards don't always end exactly where their
 * animation was heading). 0 = not known yet: they use whatever room is free at the moment.
 */
@OptIn(ExperimentalLayoutApi::class) // imeAnimationTarget
@Composable
fun AlphabetScroller(
    letters: List<String>,
    active: Boolean,
    currentLetter: String?,
    keyboardInsets: WindowInsets,
    keyboardHeight: Int,
    onKeyboardSettled: (Int) -> Unit,
    onLetterSelected: (String) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val onSelected by rememberUpdatedState(onLetterSelected)
    val onEnd by rememberUpdatedState(onScrubEnd)
    // The raw keyboard insets, used only to learn the height it settles at.
    val rawKeyboard = WindowInsets.ime
    val keyboardTarget = WindowInsets.imeAnimationTarget
    val navigationBarInsets = WindowInsets.navigationBars
    val knownKeyboard by rememberUpdatedState(keyboardHeight)
    val onSettled by rememberUpdatedState(onKeyboardSettled)

    var touchY by remember { mutableFloatStateOf(0f) }
    var touching by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(-1) }
    val bulge = animateFloatAsState(
        targetValue = if (touching) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "bulge",
    )
    // Extra offset while the letters glide from where they were held back down to their place.
    val glide = remember { Animatable(0f) }
    val strip = remember(density) {
        with(density) {
            StripLayout(MaxLetterSpacing.toPx(), TouchMarginTop.toPx(), BottomGap.toPx())
        }
    }
    val bulgePx = with(density) { BulgeDistance.toPx() }
    val spreadPx = with(density) { BulgeSpread.toPx() }

    // How much of the strip the keyboard covers. Read during layout, so the letters follow the
    // keyboard frame by frame without recomposing.
    fun keyboardOverlap(): Int =
        (keyboardInsets.getBottom(density) - navigationBarInsets.getBottom(density)).coerceAtLeast(0)

    // The keyboard height the letters are spaced for (see above).
    fun spacingOverlap(currentOverlap: Int): Int = if (knownKeyboard > 0) knownKeyboard else currentOverlap

    fun updateStrip(areaHeight: Int) {
        val overlap = keyboardOverlap()
        strip.update(areaHeight, overlap, spacingOverlap(overlap), letters.size)
    }

    // Learn the keyboard's height once it has finished opening.
    LaunchedEffect(density) {
        snapshotFlow {
            val now = rawKeyboard.getBottom(density)
            val target = keyboardTarget.getBottom(density)
            val navigationBar = navigationBarInsets.getBottom(density)
            if (target > navigationBar && now == target) target - navigationBar else 0
        }.collect { settled -> if (settled > 0) onSettled(settled) }
    }

    Layout(
        modifier = modifier,
        content = {
            // The letters, in a band only as tall as they need.
            Layout(
                content = {
                    letters.forEachIndexed { index, letter ->
                        val isSelected = index == selected
                        val highlighted = isSelected || (selected < 0 && letter == currentLetter)
                        Text(
                            text = letter,
                            color = if (highlighted) Palette.Primary else Palette.Secondary,
                            fontSize = 11.sp,
                            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                            // Read touch state in the layer block so dragging only redraws, never recomposes.
                            modifier = Modifier.graphicsLayer {
                                val distance = (strip.centerOf(index) - touchY) / spreadPx
                                val amount = exp(-distance * distance) * bulge.value
                                translationX = -bulgePx * amount
                                val scale = 1f + 0.7f * amount + if (isSelected) 0.6f * bulge.value else 0f
                                scaleX = scale
                                scaleY = scale
                            },
                        )
                    }
                },
                // Inactive (on the home page): no touch handling at all, so touches pass through.
                modifier = if (!active) Modifier else Modifier.pointerInput(letters) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        touching = true

                        fun track(y: Float) {
                            touchY = y
                            val index = strip.indexAt(y, letters.size)
                            if (index != selected) {
                                selected = index
                                TickHaptics.get(context).tick()
                                onSelected(letters[index])
                            }
                        }

                        try {
                            track(down.position.y)
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                track(change.position.y)
                            }
                        } finally {
                            // Glide from where the letters were held to where they now belong.
                            // Undispatched, so the offset is set before the next frame is laid out.
                            val heldAt = strip.top
                            updateStrip(strip.areaHeight)
                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                glide.snapTo((heldAt - strip.top).toFloat())
                                glide.animateTo(0f, tween(durationMillis = 220))
                            }
                            touching = false
                            selected = -1
                            onEnd()
                        }
                    }
                },
            ) { measurables, constraints ->
                val placeables = measurables.map { it.measure(Constraints()) }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeables.forEachIndexed { index, placeable ->
                        placeable.place(
                            x = (constraints.maxWidth - placeable.width) / 2,
                            y = (strip.centerOf(index) - placeable.height / 2f).roundToInt(),
                        )
                    }
                }
            }
        },
    ) { measurables, constraints ->
        // Held still while a thumb is on the letters, even as the keyboard slides away.
        if (!touching) updateStrip(constraints.maxHeight)
        val band = measurables.single().measure(Constraints.fixed(constraints.maxWidth, strip.height))
        layout(constraints.maxWidth, constraints.maxHeight) {
            band.place(0, strip.top + glide.value.roundToInt())
        }
    }
}

/**
 * Where the letters go: bottom-aligned in whatever part of the strip the keyboard leaves free,
 * spaced to fit above a keyboard of a given height. Plain fields rather than state: written
 * during layout, read by the touch handler and drawing.
 */
private class StripLayout(
    private val maxSpacing: Float,
    private val marginTop: Float,
    private val gapBottom: Float,
) {
    var areaHeight = 0
        private set
    var spacing = maxSpacing
        private set

    /** Top of the touch band, within the strip. */
    var top = 0
        private set

    /** Height of the touch band: the letters plus their margins. */
    var height = 0
        private set

    /** [keyboardOverlap]: how much the keyboard covers now. [spacingOverlap]: the keyboard to fit above. */
    fun update(areaHeight: Int, keyboardOverlap: Int, spacingOverlap: Int, count: Int) {
        this.areaHeight = areaHeight
        val room = (areaHeight - keyboardOverlap).coerceAtLeast(0)
        val roomToFit = (areaHeight - spacingOverlap).coerceAtLeast(0)
        spacing = min((roomToFit - marginTop - gapBottom) / count, maxSpacing).coerceAtLeast(1f)
        height = (marginTop + spacing * count + gapBottom).roundToInt()
        top = room - height
    }

    fun centerOf(index: Int): Float = marginTop + spacing * (index + 0.5f)

    fun indexAt(y: Float, count: Int): Int = ((y - marginTop) / spacing).toInt().coerceIn(0, count - 1)
}
