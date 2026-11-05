package dev.jaime.launcher.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * The bottom of the usable screen: the top of the keyboard, or of the navigation bar when there's
 * no keyboard. The search row rides on it and the letter strip sits above it.
 *
 * While the keyboard is sliding in, it never rises above the height the keyboard settled at last
 * time ([knownHeight], px above the navigation bar; 0 = not known yet). Gboard sometimes announces
 * a keyboard taller than it really is, then corrects it at the very end of the slide (seen on a
 * Galaxy S24: 972px announced, 883px real). Without this cap, the search row would overshoot and
 * drop back. Once the keyboard has settled, its real height always wins, so a keyboard that really
 * did get taller is followed straight away and remembered for next time.
 */
@OptIn(ExperimentalLayoutApi::class) // imeAnimationTarget
@Composable
fun rememberKeyboardInsets(knownHeight: () -> Int): WindowInsets {
    val ime = WindowInsets.ime
    val target = WindowInsets.imeAnimationTarget
    val navigationBars = WindowInsets.navigationBars
    val currentKnownHeight = rememberUpdatedState(knownHeight)
    return remember(ime, target, navigationBars) {
        KeyboardInsets(ime, target, navigationBars) { currentKnownHeight.value() }
    }
}

/** Bottom inset only; read during layout, so following the keyboard never recomposes. */
@Stable
private class KeyboardInsets(
    private val ime: WindowInsets,
    private val target: WindowInsets,
    private val navigationBars: WindowInsets,
    private val knownHeight: () -> Int,
) : WindowInsets {
    override fun getLeft(density: Density, layoutDirection: LayoutDirection) = 0
    override fun getTop(density: Density) = 0
    override fun getRight(density: Density, layoutDirection: LayoutDirection) = 0

    override fun getBottom(density: Density): Int {
        val navigationBar = navigationBars.getBottom(density)
        var keyboard = ime.getBottom(density)
        val known = knownHeight()
        val sliding = keyboard != target.getBottom(density)
        if (sliding && known > 0) keyboard = keyboard.coerceAtMost(navigationBar + known)
        return maxOf(keyboard, navigationBar)
    }
}
