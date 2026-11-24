/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R

@Composable
fun buildSettingsGroups(
    navController: NavController,
    isAndroid12OrLater: Boolean,
    hasUpdate: Boolean,
    context: Context,
    onOpenReportIssue: () -> Unit = {},
): List<SettingsGroup> =
    buildList {
        // Group 1: Account & Integrations
        add(
            SettingsGroup(
                title = stringResource(R.string.settings_section_account),
                items =
                    listOf(
                        SettingsItem(
                            key = "account",
                            icon = painterResource(R.drawable.ozn_user_circle),
                            title = stringResource(R.string.ozone_account_title),
                            subtitle = stringResource(R.string.ozone_account_subtitle),
                            accentColor = MaterialTheme.colorScheme.primary,
                            keywords = listOf("account", "login", "google", "profile", "sync", "user"),
                            onClick = { navController.navigate("settings/account") },
                        ),
                        SettingsItem(
                            key = "ai_integration",
                            badge = stringResource(R.string.ozone_beta),
                            icon = painterResource(R.drawable.ozn_sparkles),
                            title = stringResource(R.string.ozone_ai_title),
                            subtitle = stringResource(R.string.ozone_ai_subtitle),
                            accentColor = MaterialTheme.colorScheme.secondary,
                            keywords = listOf("ai", "gemini", "smart", "chat", "assistant", "recommendation", "claude", "chatgpt"),
                            onClick = { navController.navigate("settings/ai_integration") },
                        ),
                    ),
            ),
        )

        // Group 2: Interface & Appearance
        add(
            SettingsGroup(
                title = stringResource(R.string.settings_section_appearance),
                items =
                    listOf(
                        SettingsItem(
                            key = "appearance",
                            icon = painterResource(R.drawable.ozn_palette),
                            title = stringResource(R.string.ozone_appearance_title),
                            subtitle = stringResource(R.string.ozone_appearance_subtitle),
                            accentColor = MaterialTheme.colorScheme.secondary,
                            keywords = listOf("theme", "appearance", "dark", "light", "color", "accent", "icon", "oled", "pure black"),
                            onClick = { navController.navigate("settings/appearance") },
                        ),
                        SettingsItem(
                            key = "lyrics",
                            icon = painterResource(R.drawable.ozn_message_text),
                            title = stringResource(R.string.ozone_lyrics_title),
                            subtitle = stringResource(R.string.ozone_lyrics_subtitle),
                            accentColor = MaterialTheme.colorScheme.primary,
                            keywords = listOf("lyrics", "lrc", "ttml", "karaoke", "synced", "translation", "provider"),
                            onClick = { navController.navigate("settings/lyrics") },
                        ),
                        SettingsItem(
                            key = "stats",
                            icon = painterResource(R.drawable.ozn_bar_chart),
                            title = stringResource(R.string.ozone_stats_title),
                            subtitle = stringResource(R.string.ozone_stats_subtitle),
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            keywords = listOf("stats", "history", "charts", "top", "playtime", "listening"),
                            onClick = { navController.navigate("stats") },
                        ),
                    ),
            ),
        )

        // Group 3: Player & Content
        add(
            SettingsGroup(
                title = stringResource(R.string.settings_section_player_content),
                items =
                    listOf(
                        SettingsItem(
                            key = "player",
                            icon = painterResource(R.drawable.ozn_play),
                            title = stringResource(R.string.ozone_player_title),
                            subtitle = stringResource(R.string.ozone_player_subtitle),
                            accentColor = MaterialTheme.colorScheme.primary,
                            keywords = listOf("player", "audio", "sound", "volume", "equalizer", "eq", "crossfade", "enhance", "spatial", "bitrate"),
                            onClick = { navController.navigate("settings/player") },
                        ),
                        SettingsItem(
                            key = "content",
                            icon = painterResource(R.drawable.ozn_globe),
                            title = stringResource(R.string.ozone_content_title),
                            subtitle = stringResource(R.string.ozone_content_subtitle),
                            accentColor = MaterialTheme.colorScheme.secondary,
                            keywords = listOf("content", "source", "region", "language", "explicit", "blacklist", "whitelist"),
                            onClick = { navController.navigate("settings/content") },
                        ),
                        SettingsItem(
                            key = "storage",
                            trailing = { OzoneStorageMeter() },
                            icon = painterResource(R.drawable.ozn_database),
                            title = stringResource(R.string.ozone_storage_title),
                            subtitle = stringResource(R.string.ozone_storage_subtitle),
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            keywords = listOf("storage", "cache", "download", "space", "disk", "clear"),
                            onClick = { navController.navigate("settings/storage") },
                        ),
                        SettingsItem(
                            key = "backup_restore",
                            icon = painterResource(R.drawable.ozn_cloud_upload),
                            title = stringResource(R.string.ozone_backup_title),
                            subtitle = stringResource(R.string.ozone_backup_subtitle),
                            accentColor = MaterialTheme.colorScheme.primary,
                            keywords = listOf("backup", "restore", "export", "import", "database", "library"),
                            onClick = { navController.navigate("settings/backup_restore") },
                        ),
                    ),
            ),
        )

        // Group 4: Network & Services
        add(
            SettingsGroup(
                title = stringResource(R.string.settings_section_network),
                items =
                    listOf(
                        SettingsItem(
                            key = "internet",
                            icon = painterResource(R.drawable.ozn_globe_wifi),
                            title = stringResource(R.string.ozone_internet_title),
                            subtitle = stringResource(R.string.ozone_internet_subtitle),
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            keywords = listOf("internet", "network", "wifi", "proxy", "cellular", "data", "offline"),
                            onClick = { navController.navigate("settings/internet") },
                        ),
                    ),
            ),
        )

        // Group 5: About & System
        add(
            SettingsGroup(
                title = stringResource(R.string.settings_section_about),
                items =
                    buildList {
                        add(
                            SettingsItem(
                                key = "behavior",
                                icon = painterResource(R.drawable.ozn_pointer),
                                title = stringResource(R.string.ozone_behavior_title),
                                subtitle = stringResource(R.string.ozone_behavior_subtitle),
                                accentColor = MaterialTheme.colorScheme.primary,
                                keywords = listOf("behavior", "privacy", "history", "cache", "defaults"),
                                onClick = { navController.navigate("settings/privacy") },
                            ),
                        )
                        if (isAndroid12OrLater) {
                            add(
                                SettingsItem(
                                    key = "default_links",
                                    icon = painterResource(R.drawable.ozn_link),
                                    title = stringResource(R.string.ozone_links_title),
                                    subtitle = stringResource(R.string.ozone_links_subtitle),
                                    accentColor = MaterialTheme.colorScheme.secondary,
                                    keywords = listOf("links", "url", "open", "default", "browser"),
                                    onClick = {
                                        try {
                                            val intent =
                                                Intent(
                                                    Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                                    Uri.parse("package:${context.packageName}"),
                                                )
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.error_unknown),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                ),
                            )
                        }
                        if (BuildConfig.UPDATER_AVAILABLE) {
                            add(
                                SettingsItem(
                                    key = "updates",
                                    icon = painterResource(R.drawable.ozn_refresh),
                                    title = stringResource(R.string.ozone_updates_title),
                                    subtitle =
                                        if (hasUpdate) {
                                            stringResource(R.string.new_version_available)
                                        } else {
                                            stringResource(R.string.ozone_updates_subtitle)
                                        },
                                    showUpdateIndicator = hasUpdate,
                                    badge = if (hasUpdate) "v${BuildConfig.VERSION_NAME}" else BuildConfig.VERSION_NAME,
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    keywords = listOf("update", "version", "changelog", "github", "release"),
                                    onClick = { navController.navigate("settings/update") },
                                ),
                            )
                        }
                        add(
                            SettingsItem(
                                key = "report_issue",
                                icon = painterResource(R.drawable.ozn_bug),
                                title = stringResource(R.string.ozone_report_title),
                                subtitle = stringResource(R.string.ozone_report_subtitle),
                                accentColor = MaterialTheme.colorScheme.secondary,
                                keywords = listOf("report", "issue", "bug", "crash", "feedback", "github", "problem"),
                                onClick = onOpenReportIssue,
                            ),
                        )
                        add(
                            SettingsItem(
                                key = "about",
                                icon = painterResource(R.drawable.ozn_info_bubble),
                                title = stringResource(R.string.ozone_about_title),
                                subtitle = "v${BuildConfig.VERSION_NAME}",
                                showUpdateIndicator = false,
                                accentColor = MaterialTheme.colorScheme.primary,
                                keywords = listOf("about", "version", "license", "author", "ozone"),
                                onClick = { navController.navigate("settings/about") },
                            ),
                        )
                    },
            ),
        )
    }
