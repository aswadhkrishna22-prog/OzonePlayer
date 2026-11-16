/*
 * Ozone Player (2026)
 * AutoEq Headphone Correction Repository
 * GPL-3.0 License
 */

package moe.rukamori.archivetune.playback.autoeq

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException

object AutoEqRepository {
    private const val TAG = "AutoEqRepository"
    private const val INDEX_URL = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/INDEX.md"
    private const val INDEX_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000 // 30 days

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var autoEqDir: File? = null
    private var indexFile: File? = null
    private var profilesDir: File? = null
    private var activeProfileFile: File? = null

    @Volatile
    private var cachedEntries: List<AutoEqEntry>? = null

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _activeProfile = MutableStateFlow<AutoEqProfile?>(null)
    val activeProfile: StateFlow<AutoEqProfile?> = _activeProfile.asStateFlow()

    fun init(context: Context) {
        val baseDir = File(context.filesDir, "autoeq").apply { mkdirs() }
        autoEqDir = baseDir
        indexFile = File(baseDir, "INDEX.md")
        profilesDir = File(baseDir, "profiles").apply { mkdirs() }
        activeProfileFile = File(baseDir, "active_profile.json")

        // Load active profile from disk if persisted
        scope.launch {
            loadPersistedActiveProfile()
        }
    }

    private suspend fun loadPersistedActiveProfile() = withContext(Dispatchers.IO) {
        val file = activeProfileFile ?: return@withContext
        if (file.exists()) {
            runCatching {
                val profile = json.decodeFromString<AutoEqProfile>(file.readText())
                _activeProfile.value = profile
                _isEnabled.value = true
                Timber.tag(TAG).d("Loaded active AutoEq profile: %s", profile.name)
            }.onFailure { e ->
                Timber.tag(TAG).w(e, "Failed to load active AutoEq profile")
            }
        }
    }

    suspend fun getEntries(): List<AutoEqEntry> = withContext(Dispatchers.IO) {
        cachedEntries?.let { return@withContext it }

        val file = indexFile
        val isFresh = file?.takeIf {
            it.exists() && System.currentTimeMillis() - it.lastModified() < INDEX_MAX_AGE_MS
        }

        val text = isFresh?.readText() ?: runCatching {
            fetchHttp(INDEX_URL)
        }.getOrElse { networkError ->
            // Offline fallback to existing cached index even if stale
            file?.takeIf { it.exists() }?.readText() ?: throw networkError
        }.also { content ->
            if (isFresh == null) {
                runCatching { file?.writeText(content) }
            }
        }

        val entries = AutoEqParser.parseIndex(text)
        cachedEntries = entries
        entries
    }

    suspend fun searchModels(query: String, limit: Int = 60): List<AutoEqEntry> {
        val all = getEntries()
        return AutoEqParser.search(all, query, limit)
    }

    suspend fun loadProfile(entry: AutoEqEntry): AutoEqProfile = withContext(Dispatchers.IO) {
        val safeFileName = entry.path.hashCode().toString() + ".json"
        val cachedProfileFile = profilesDir?.let { File(it, safeFileName) }

        if (cachedProfileFile != null && cachedProfileFile.exists()) {
            runCatching {
                return@withContext json.decodeFromString<AutoEqProfile>(cachedProfileFile.readText())
            }
        }

        val url = AutoEqParser.profileUrl(entry)
        val text = fetchHttp(url)
        val profile = AutoEqParser.parseParametric(text, entry.name, entry.source)

        // Cache profile to disk
        runCatching {
            cachedProfileFile?.writeText(json.encodeToString(profile))
        }

        profile
    }

    suspend fun selectProfile(profile: AutoEqProfile?) = withContext(Dispatchers.IO) {
        _activeProfile.value = profile
        _isEnabled.value = profile != null
        val file = activeProfileFile ?: return@withContext
        if (profile != null) {
            runCatching {
                file.writeText(json.encodeToString(profile))
            }
        } else {
            runCatching {
                if (file.exists()) file.delete()
            }
        }
    }

    suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        _isEnabled.value = enabled && (_activeProfile.value != null)
    }

    private fun fetchHttp(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "OzonePlayer/1.0 (Headphone AutoEq)")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code} fetching $url")
        }
        return response.body?.string() ?: throw IOException("Empty body from $url")
    }
}
