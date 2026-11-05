package dev.jaime.launcher.ui

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Strength of each letter's tick, 0–1, on phones that support fine-grained haptics. */
private const val TickStrength = 0.6f

/**
 * The tick you feel for each letter while scrubbing the alphabet strip.
 *
 * Plays a short click through the vibrator directly. The system's "tick" haptic constants
 * (CLOCK_TICK, SEGMENT_TICK) are so faint that plenty of phones play nothing at all for them.
 * Still marked as touch feedback, so it follows the phone's touch-vibration setting.
 */
class TickHaptics private constructor(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }

    // Worked out on the first tick, so opening the app list never waits on it.
    private val effect: VibrationEffect? by lazy {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return@lazy null
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK) ->
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, TickStrength)
                    .compose()
            // The keyboard-tap click: every phone with a vibrator has one.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            else -> VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    fun tick() {
        val v = vibrator ?: return
        val e = effect ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(e, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(e, touchAudioAttributes)
        }
    }

    companion object {
        private val touchAudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        @Volatile
        private var instance: TickHaptics? = null

        fun get(context: Context): TickHaptics =
            instance ?: TickHaptics(context.applicationContext).also { instance = it }
    }
}
