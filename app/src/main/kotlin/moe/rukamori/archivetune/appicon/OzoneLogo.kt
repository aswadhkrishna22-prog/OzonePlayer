package moe.rukamori.archivetune.appicon

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import moe.rukamori.archivetune.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Same rounded-square (superellipse) the icon PNGs/SVGs use. */
val SquircleShape: Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path().apply {
            val n = 5.0
            val a = size.width / 2f
            val b = size.height / 2f
            val steps = 160
            for (i in 0 until steps) {
                val t = 2.0 * PI * i / steps
                val c = cos(t)
                val s = sin(t)
                val x = a + a * sign(c) * abs(c).pow(2.0 / n)
                val y = b + b * sign(s) * abs(s).pow(2.0 / n)
                if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
            }
            close()
        }
        return Outline.Generic(path)
    }
}

/** The icon the user currently picked (read once per composition). Use this in the intro animation. */
@Composable
fun rememberCurrentAppIcon(): AppIconVariant {
    val context = LocalContext.current
    return remember { AppIconManager.current(context) }
}

/**
 * Draws the Ozone icon in any size: OzoneLogo(variant, Modifier.size(120.dp)).
 * revealProgress 0f..1f wipes the wave in from left to right (animate it for the intro).
 */
@Composable
fun OzoneLogo(
    variant: AppIconVariant,
    modifier: Modifier = Modifier,
    revealProgress: Float = 1f,
) {
    Box(
        modifier = modifier
            .clip(SquircleShape)
            .background(colorResource(variant.backgroundRes)),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_ozone_wave_logo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorResource(variant.waveColorRes)),
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(right = size.width * revealProgress.coerceIn(0f, 1f)) {
                        this@drawWithContent.drawContent()
                    }
                },
        )
    }
}
