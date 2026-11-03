package moe.rukamori.archivetune.kemo

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.serialization.json.Json
import timber.log.Timber

data class LoadedKemoAssets(
    val bitmap: ImageBitmap,
    val spec: KemoSpriteSpec,
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
            val bitmap = context.assets.open("kemo/kemo_spritesheet.png").use { stream ->
                BitmapFactory.decodeStream(stream).asImageBitmap()
            }
            val jsonText = context.assets.open("kemo/kemo_spec.json").bufferedReader().use { it.readText() }
            val spec = jsonParser.decodeFromString<KemoSpriteSpec>(jsonText)
            LoadedKemoAssets(bitmap, spec)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load Kemo assets from assets/kemo")
            null
        }
    }
}
