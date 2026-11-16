/*
 * Ozone Player (2026)
 * Adapted from Mochy Music (GPL-3.0)
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.carousel

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object HeroPaletteHelper {
    suspend fun extractRollTint(context: Context, url: String): HeroRollTint? {
        val bitmap = withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(64)
                .allowHardware(false)
                .build()
            context.imageLoader.execute(request).image?.toBitmap()
        } ?: return null

        return withContext(Dispatchers.Default) {
            val palette = Palette.from(bitmap).maximumColorCount(12).generate()
            val lit = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch ?: return@withContext null
            val deep = palette.darkVibrantSwatch ?: palette.darkMutedSwatch ?: palette.mutedSwatch ?: lit

            HeroRollTint(
                light = shiftHsv(lit.rgb, minVal = 0.78f, maxVal = 1f, maxSat = 0.8f),
                deep = shiftHsv(deep.rgb, minVal = 0.28f, maxVal = 0.5f, maxSat = 0.85f),
            )
        }
    }

    private fun shiftHsv(rgb: Int, minVal: Float, maxVal: Float, maxSat: Float): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(rgb, hsv)
        hsv[1] = hsv[1].coerceAtMost(maxSat)
        hsv[2] = hsv[2].coerceIn(minVal, maxVal)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}
