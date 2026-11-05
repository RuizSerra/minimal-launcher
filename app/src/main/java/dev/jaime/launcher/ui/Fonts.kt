package dev.jaime.launcher.ui

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import java.util.concurrent.ConcurrentHashMap

/**
 * The fonts you can pick in launcher settings. Bundled ones are variable fonts from Google Fonts,
 * under the SIL Open Font License (texts in assets/licenses). [System] is the phone's own font.
 *
 * The bundled files live in assets, not res/font: weights are applied to a variable font only when
 * it's loaded from assets (from res/font, each one rendered at the file's default weight, which
 * for Manrope and Space Grotesk is their lightest).
 */
enum class LauncherFont(val id: String, val label: String, private val asset: String?) {
    System("system", "System default", null),
    Inter("inter", "Inter", "fonts/inter.ttf"),
    SpaceGrotesk("space_grotesk", "Space Grotesk", "fonts/space_grotesk.ttf"),
    Manrope("manrope", "Manrope", "fonts/manrope.ttf"),
    Lora("lora", "Lora", "fonts/lora.ttf"),
    JetBrainsMono("jetbrains_mono", "JetBrains Mono", "fonts/jetbrains_mono.ttf"),
    Condensed("condensed", "Condensed", null);

    /** For Compose text. Built once per font. */
    fun family(context: Context): FontFamily = families.getOrPut(this) { buildFamily(context.applicationContext) }

    /** For measuring with Android's own text tools (see [capHeightRatio]). */
    fun typeface(context: Context): Typeface = when (this) {
        System -> Typeface.DEFAULT
        Condensed -> Typeface.create(CONDENSED, Typeface.NORMAL)
        else -> asset?.let { Typeface.createFromAsset(context.assets, it) } ?: Typeface.DEFAULT
    }

    @OptIn(ExperimentalTextApi::class)
    private fun buildFamily(context: Context): FontFamily = when (this) {
        System -> FontFamily.Default
        Condensed -> FontFamily(Typeface.create(CONDENSED, Typeface.NORMAL))
        // One variable font file, set to each weight the launcher uses.
        else -> FontFamily(
            UsedWeights.map { weight ->
                Font(
                    path = requireNotNull(asset),
                    assetManager = context.assets,
                    weight = weight,
                    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
                )
            },
        )
    }

    companion object {
        fun fromId(id: String?): LauncherFont = entries.firstOrNull { it.id == id } ?: System
    }
}

private const val CONDENSED = "sans-serif-condensed"

/** Normal text, the clock (Medium) and the highlighted letter on the strip (Bold). */
private val UsedWeights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.Bold)

private val families = ConcurrentHashMap<LauncherFont, FontFamily>()

/** The font picked in settings, for code that needs more than the text style (see Header). */
val LocalLauncherFont = staticCompositionLocalOf { LauncherFont.System }

/** Height of capital letters (and digits) as a share of the font size. */
fun capHeightRatio(typeface: Typeface): Float {
    val paint = android.graphics.Paint().apply {
        this.typeface = typeface
        textSize = 100f
    }
    val bounds = Rect()
    paint.getTextBounds("H", 0, 1, bounds)
    return bounds.height() / 100f
}

/** Material's default text styles, all in [family]. */
fun typographyIn(family: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.inFamily() = copy(fontFamily = family)
    return Typography(
        displayLarge = base.displayLarge.inFamily(),
        displayMedium = base.displayMedium.inFamily(),
        displaySmall = base.displaySmall.inFamily(),
        headlineLarge = base.headlineLarge.inFamily(),
        headlineMedium = base.headlineMedium.inFamily(),
        headlineSmall = base.headlineSmall.inFamily(),
        titleLarge = base.titleLarge.inFamily(),
        titleMedium = base.titleMedium.inFamily(),
        titleSmall = base.titleSmall.inFamily(),
        bodyLarge = base.bodyLarge.inFamily(),
        bodyMedium = base.bodyMedium.inFamily(),
        bodySmall = base.bodySmall.inFamily(),
        labelLarge = base.labelLarge.inFamily(),
        labelMedium = base.labelMedium.inFamily(),
        labelSmall = base.labelSmall.inFamily(),
    )
}
