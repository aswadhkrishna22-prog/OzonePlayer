/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.roundToInt

class FastBlurTransformation(
    val radius: Int = 25,
    val sampling: Float = 1f,
) : Transformation() {

    override val cacheKey: String = "${FastBlurTransformation::class.qualifiedName}-$radius-$sampling"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (radius <= 0) return input

        val safeSampling = sampling.coerceAtLeast(1f)
        val targetWidth = (input.width / safeSampling).roundToInt().coerceAtLeast(1)
        val targetHeight = (input.height / safeSampling).roundToInt().coerceAtLeast(1)

        val workingBitmap = if (targetWidth != input.width || targetHeight != input.height) {
            Bitmap.createScaledBitmap(input, targetWidth, targetHeight, true)
        } else {
            input
        }

        val blurred = ImageBlurUtils.blur(workingBitmap, radius.toFloat())

        if (workingBitmap !== input && workingBitmap !== blurred) {
            workingBitmap.recycle()
        }

        return blurred
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is FastBlurTransformation &&
            radius == other.radius &&
            sampling == other.sampling
    }

    override fun hashCode(): Int {
        var result = radius.hashCode()
        result = 31 * result + sampling.hashCode()
        return result
    }
}
