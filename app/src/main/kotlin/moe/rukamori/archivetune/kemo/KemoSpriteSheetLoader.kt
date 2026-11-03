package moe.rukamori.archivetune.kemo

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.serialization.json.Json
import timber.log.Timber

data class LoadedKemoAssets(
    val spriteSheet: ImageBitmap,
    val particlesBitmap: ImageBitmap?,
    val spec: KemoSpec,
)

private val jsonParser = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Composable
fun rememberKemoAssets(): LoadedKemoAssets? {
    val context = LocalContext.current
    return remember(context) {
        try {
            val options = BitmapFactory.Options().apply { inScaled = false }
            val spriteBitmap = context.assets.open("kemo/kemo_spritesheet.png").use { stream ->
                BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
            } ?: return@remember null

            val particlesBitmap = try {
                context.assets.open("kemo/kemo_particles.png").use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to load kemo_particles.png")
                null
            }

            val jsonText = context.assets.open("kemo/kemo_spec.json").bufferedReader().use { it.readText() }
            val spec = jsonParser.decodeFromString<KemoSpec>(jsonText)
            LoadedKemoAssets(spriteBitmap, particlesBitmap, spec)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load Kemo assets from assets/kemo")
            null
        }
    }
}
