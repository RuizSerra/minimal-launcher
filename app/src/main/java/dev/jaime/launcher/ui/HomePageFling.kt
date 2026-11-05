package dev.jaime.launcher.ui

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Fling for the launcher's one list, whose first item is the full-screen home page.
 *
 * - A swipe that ends with the home page partly on screen settles on either the home page or the
 *   top of the app list, never in between. A quick swipe goes the way it was thrown. A slower one
 *   goes the way the finger was last moving once it has travelled [CommitDistance], and otherwise
 *   springs back.
 * - A fling down through the app list stops at the top of the list. One more swipe down goes home.
 *
 * [onSettle] is told which way it's going (true = app list) as soon as that's decided, before
 * the page starts moving, so work like opening the keyboard can overlap the animation.
 */
@Composable
fun rememberHomePageFling(listState: LazyListState, onSettle: (toList: Boolean) -> Unit): FlingBehavior {
    val decay = rememberSplineBasedDecay<Float>()
    val density = LocalDensity.current
    val velocityThreshold = with(density) { 400.dp.toPx() }
    val commitDistance = with(density) { CommitDistance.toPx() }
    val currentOnSettle by rememberUpdatedState(onSettle)
    return remember(listState, decay, velocityThreshold, commitDistance) {
        HomePageFling(listState, decay, velocityThreshold, commitDistance) { currentOnSettle(it) }
    }
}

/** How far a slow swipe has to move the page to switch between home and the app list. */
private val CommitDistance = 80.dp

/**
 * The snap once you let go. Raise the stiffness for a faster snap, lower it for a gentler one.
 * Damping just under 1 means it arrives with a little speed left and stops dead (see the clamp in
 * settle), which reads as quick rather than abrupt.
 */
private val SnapSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 1200f)

private class HomePageFling(
    private val listState: LazyListState,
    private val decay: DecayAnimationSpec<Float>,
    private val velocityThreshold: Float,
    private val commitDistance: Float,
    private val onSettle: (toList: Boolean) -> Unit,
) : FlingBehavior {

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val pageHeight = visibleHomePageHeight() ?: return flingWithinList(initialVelocity)
        settle(listState.firstVisibleItemScrollOffset, pageHeight, initialVelocity)
        return 0f
    }

    /** Height of the home page while any of it is on screen, otherwise null. */
    private fun visibleHomePageHeight(): Int? =
        if (listState.firstVisibleItemIndex == 0) listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size else null

    private suspend fun ScrollScope.settle(scrolled: Int, pageHeight: Int, velocity: Float) {
        val toList = when {
            velocity > velocityThreshold -> true
            velocity < -velocityThreshold -> false
            listState.lastScrolledForward -> scrolled >= commitDistance // was swiping up, out of home
            listState.lastScrolledBackward -> pageHeight - scrolled < commitDistance // was pulling home in
            else -> scrolled > pageHeight / 2
        }
        onSettle(toList)
        val distance = (if (toList) pageHeight - scrolled else -scrolled).toFloat()
        if (distance == 0f) return
        var previous = 0f
        AnimationState(0f, velocity).animateTo(distance, SnapSpring) {
            // Stop exactly on the target: there's nothing above the home page, and overshooting
            // the top of the list would read as a bounce. This is also what keeps the snap short:
            // the slightly bouncy spring reaches the target quickly and stops there, instead of
            // creeping over the last few pixels.
            val clamped = if (distance > 0) value.coerceAtMost(distance) else value.coerceAtLeast(distance)
            scrollBy(clamped - previous)
            previous = clamped
            if (clamped == distance) cancelAnimation()
        }
    }

    private suspend fun ScrollScope.flingWithinList(initialVelocity: Float): Float {
        if (abs(initialVelocity) <= 1f) return initialVelocity
        var previous = 0f
        var velocityLeft = initialVelocity
        AnimationState(0f, initialVelocity).animateDecay(decay) {
            val delta = value - previous
            val consumed = scrollBy(delta)
            previous = value
            velocityLeft = velocity
            val pageHeight = visibleHomePageHeight()
            if (abs(delta - consumed) > 0.5f) {
                cancelAnimation() // reached the end of the list
            } else if (delta < 0 && pageHeight != null) {
                // Flew back past the first app: stop at the top of the list, not on the home page.
                scrollBy((pageHeight - listState.firstVisibleItemScrollOffset).toFloat())
                velocityLeft = 0f
                cancelAnimation()
            }
        }
        return velocityLeft
    }
}
