/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth for sensitive preference keys across OzonePlayer.
 * Any key in this set is stored strictly in `secure_settings.preferences_pb`
 * and excluded from Android backup and device-to-device transfers.
 */
object SensitivePreferences {
    val KEYS: Set<String> = setOf(
        // InnerTube & YouTube Session / Auth
        "innerTubeCookie",
        "visitorData",
        "dataSyncId",
        "poToken",
        "poTokenGvs",
        "poTokenPlayer",
        "poTokenSourceUrl",
        "webClientPoTokenEnabled",
        "accountName",
        "accountEmail",
        "accountChannelHandle",
        "savedAccounts",
        "useLoginForBrowse",

        // Spotify Session & Tokens
        "spotify_sp_dc",
        "spotify_sp_key",
        "spotify_access_token",
        "spotify_access_token_expires_at",
        "spotify_account_name",
        "spotify_account_avatar_url",

        // Discord OAuth & Identity
        "discordToken",
        "discordRefreshToken",
        "discordTokenExpiresAt",
        "discordUsername",
        "discordName",
        "discordAvatarUrl",

        // Qobuz Lossless Streaming Credentials
        "qobuz_app_id",
        "qobuz_app_secret",
        "qobuz_user_auth_token",
        "flac_kv_pasted_token",
        "flac_kv_pinned_token",

        // AI Services
        "ai_api_key",
        "ai_custom_endpoint",

        // Scrobblers (Last.fm & ListenBrainz)
        "lastfmSession",
        "lastfmUsername",
        "lastfmApiKeyOverride",
        "lastfmSecretOverride",
        "listenbrainz_token",

        // Proxy Auth Credentials
        "proxyUsername",
        "proxyPassword",
    )

    const val CONFLICT_SUFFIX = "__conflict_legacy"

    fun isConflictKey(keyName: String): Boolean = keyName.endsWith(CONFLICT_SUFFIX)

    fun isSensitive(keyName: String): Boolean {
        val baseName = keyName.removeSuffix(CONFLICT_SUFFIX)
        return baseName in KEYS || baseName.startsWith("flac_kv_")
    }

    @Suppress("UNCHECKED_CAST")
    fun conflictKeyFor(key: Preferences.Key<*>, value: Any): Preferences.Key<Any> {
        val conflictName = "${key.name}$CONFLICT_SUFFIX"
        return when (value) {
            is String -> stringPreferencesKey(conflictName)
            is Boolean -> booleanPreferencesKey(conflictName)
            is Int -> intPreferencesKey(conflictName)
            is Long -> longPreferencesKey(conflictName)
            is Float -> floatPreferencesKey(conflictName)
            is Set<*> -> stringSetPreferencesKey(conflictName)
            else -> stringPreferencesKey(conflictName)
        } as Preferences.Key<Any>
    }
}

/**
 * Composite DataStore that transparently partitions preferences:
 * - Sensitive keys (tokens, credentials, cookies) -> [secureStore] (`secure_settings.preferences_pb`)
 * - Non-sensitive UI / app preferences -> [normalStore] (`settings.preferences_pb`)
 *
 * It transparently merges both stores for reads, routes updates to the appropriate store,
 * and migrates legacy sensitive keys out of [normalStore] on existing installations.
 */
class SecureRoutedPreferencesDataStore(
    val normalStore: DataStore<Preferences>,
    val secureStore: DataStore<Preferences>,
    private val coroutineScope: CoroutineScope? = null,
) : DataStore<Preferences> {

    private val writeMutex = Mutex()
    private val migrationMutex = Mutex()
    @Volatile private var migrationDone = false

    val isMigrationDone: Boolean
        get() = migrationDone

    init {
        coroutineScope?.launch {
            try {
                ensureMigrated()
            } catch (_: Exception) {
                // Background migration attempt failed (e.g. transient I/O).
                // migrationDone remains false so subsequent read or write retries safely.
            }
        }
    }

    suspend fun ensureMigrated() {
        if (migrationDone) return
        runMigration()
    }

    suspend fun runMigration() {
        if (migrationDone) return
        migrationMutex.withLock {
            if (migrationDone) return

            val normalPrefs = normalStore.data.first()
            val sensitiveInNormal = normalPrefs.asMap().filter { (key, _) ->
                SensitivePreferences.isSensitive(key.name)
            }
            if (sensitiveInNormal.isNotEmpty()) {
                // Step 1: Write sensitive credentials to secureStore FIRST
                secureStore.edit { secMutable ->
                    for ((k, v) in sensitiveInNormal) {
                        if (!secMutable.contains(k)) {
                            // Clean migration: key not yet in secureStore
                            @Suppress("UNCHECKED_CAST")
                            secMutable[k as Preferences.Key<Any>] = v
                        } else if (secMutable[k] != v) {
                            // Conflict policy: Divergent values between normalStore and secureStore.
                            // In the absence of timestamps/versions, neither value can be assumed 'newer'.
                            // To prevent silent overwrite or data loss:
                            // 1. secMutable[k] remains the active value in secureStore.
                            // 2. The legacy conflicting value v is safely archived in secureStore
                            //    under a typed conflict key ("${k.name}__conflict_legacy").
                            val conflictKey = SensitivePreferences.conflictKeyFor(k, v)
                            if (!secMutable.contains(conflictKey)) {
                                secMutable[conflictKey] = v
                            }
                        }
                        // If secMutable[k] == v, it is an identical duplicate (e.g. from crash after step 1),
                        // so no action needed in secureStore.
                    }
                }
                // Step 2: Delete sensitive credentials from normalStore
                normalStore.edit { normMutable ->
                    for ((k, _) in sensitiveInNormal) {
                        @Suppress("UNCHECKED_CAST")
                        normMutable.remove(k as Preferences.Key<Any>)
                    }
                }
            }
            // Mark migrationDone ONLY after all migration steps succeed without error
            migrationDone = true
        }
    }

    override val data: Flow<Preferences> = flow {
        ensureMigrated()
        emitAll(
            combine(normalStore.data, secureStore.data) { normal, secure ->
                merge(normal, secure)
            }
        )
    }

    private fun merge(normal: Preferences, secure: Preferences): Preferences {
        val mutable = normal.toMutablePreferences()
        // Defense-in-depth: Never expose sensitive keys from normal store even if lingering
        for (key in normal.asMap().keys) {
            if (SensitivePreferences.isSensitive(key.name)) {
                @Suppress("UNCHECKED_CAST")
                mutable.remove(key as Preferences.Key<Any>)
            }
        }
        for ((key, value) in secure.asMap()) {
            @Suppress("UNCHECKED_CAST")
            mutable[key as Preferences.Key<Any>] = value
        }
        return mutable.toPreferences()
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        writeMutex.withLock {
            ensureMigrated()

            val curNormal = normalStore.data.first()
            val curSecure = secureStore.data.first()
            val curCombined = merge(curNormal, curSecure)

            val newCombined = transform(curCombined)

            val secureUpdates = mutableMapOf<Preferences.Key<*>, Any>()
            val secureRemovals = mutableSetOf<Preferences.Key<*>>()
            val normalUpdates = mutableMapOf<Preferences.Key<*>, Any>()
            val normalRemovals = mutableSetOf<Preferences.Key<*>>()

            for ((key, value) in newCombined.asMap()) {
                if (SensitivePreferences.isSensitive(key.name)) {
                    if (curCombined[key] != value) {
                        secureUpdates[key] = value
                    }
                } else {
                    if (curCombined[key] != value) {
                        normalUpdates[key] = value
                    }
                }
            }

            for (key in curCombined.asMap().keys) {
                if (!newCombined.contains(key)) {
                    if (SensitivePreferences.isSensitive(key.name)) {
                        secureRemovals.add(key)
                    } else {
                        normalRemovals.add(key)
                    }
                }
            }

            if (secureUpdates.isNotEmpty() || secureRemovals.isNotEmpty()) {
                secureStore.edit { secMutable ->
                    for ((k, v) in secureUpdates) {
                        @Suppress("UNCHECKED_CAST")
                        secMutable[k as Preferences.Key<Any>] = v
                    }
                    for (k in secureRemovals) {
                        @Suppress("UNCHECKED_CAST")
                        secMutable.remove(k as Preferences.Key<Any>)
                    }
                }
                val normalPurges = secureUpdates.keys.filter { curNormal.contains(it) } +
                        secureRemovals.filter { curNormal.contains(it) }
                if (normalPurges.isNotEmpty()) {
                    normalStore.edit { normMutable ->
                        for (k in normalPurges) {
                            @Suppress("UNCHECKED_CAST")
                            normMutable.remove(k as Preferences.Key<Any>)
                        }
                    }
                }
            }

            if (normalUpdates.isNotEmpty() || normalRemovals.isNotEmpty()) {
                normalStore.edit { normMutable ->
                    for ((k, v) in normalUpdates) {
                        @Suppress("UNCHECKED_CAST")
                        normMutable[k as Preferences.Key<Any>] = v
                    }
                    for (k in normalRemovals) {
                        @Suppress("UNCHECKED_CAST")
                        normMutable.remove(k as Preferences.Key<Any>)
                    }
                }
            }

            newCombined
        }
}

object SecureDataStoreRegistry {
    private val instances = ConcurrentHashMap<Context, SecureRoutedPreferencesDataStore>()

    fun get(
        context: Context,
        normalStore: DataStore<Preferences>,
        secureStore: DataStore<Preferences>,
    ): DataStore<Preferences> {
        val target = context.applicationContext ?: context
        return instances.computeIfAbsent(target) {
            SecureRoutedPreferencesDataStore(
                normalStore = normalStore,
                secureStore = secureStore,
                coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            )
        }
    }
}
