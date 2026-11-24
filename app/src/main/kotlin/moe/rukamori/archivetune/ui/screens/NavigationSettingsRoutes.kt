/*
 * Ozone Player (2026)
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.defaultUpdateChannel
import moe.rukamori.archivetune.ui.screens.settings.AboutScreen
import moe.rukamori.archivetune.ui.screens.settings.AccountSettings
import moe.rukamori.archivetune.ui.screens.settings.AiIntegrationSettings
import moe.rukamori.archivetune.ui.screens.settings.AppearanceContract
import moe.rukamori.archivetune.ui.screens.settings.AppearanceSettings
import moe.rukamori.archivetune.ui.screens.settings.BackupAndRestore
import moe.rukamori.archivetune.ui.screens.settings.ChangelogScreen
import moe.rukamori.archivetune.ui.screens.settings.ContentSettings
import moe.rukamori.archivetune.ui.screens.settings.DebugSettings
import moe.rukamori.archivetune.ui.screens.settings.DiscordSettings
import moe.rukamori.archivetune.ui.screens.settings.HiddenPlaylistsScreen
import moe.rukamori.archivetune.ui.screens.settings.IconScreen
import moe.rukamori.archivetune.ui.screens.settings.IntegrationScreen
import moe.rukamori.archivetune.ui.screens.settings.InternetSettings
import moe.rukamori.archivetune.ui.screens.settings.LastFMSettings
import moe.rukamori.archivetune.ui.screens.settings.LyricsSettings
import moe.rukamori.archivetune.ui.screens.settings.MusicTogetherScreen
import moe.rukamori.archivetune.ui.screens.settings.PalettePickerScreen
import moe.rukamori.archivetune.ui.screens.settings.player.PlayerSettings
import moe.rukamori.archivetune.ui.screens.settings.PoTokenScreen
import moe.rukamori.archivetune.ui.screens.settings.PrivacySettings
import moe.rukamori.archivetune.ui.screens.settings.SpatialAudioSettings
import moe.rukamori.archivetune.ui.screens.settings.StorageSettings
import moe.rukamori.archivetune.ui.screens.settings.ThemeCreatorScreen
import moe.rukamori.archivetune.ui.state.UpdateState
import moe.rukamori.archivetune.ui.theme.OzoneSettingsTheme

private fun NavGraphBuilder.settingsComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(route = route, arguments = arguments) { entry ->
        OzoneSettingsTheme { content(entry) }
    }
}

internal fun NavGraphBuilder.settingsNavigationRoutes(
    navController: NavHostController,
    updateState: UpdateState,
) {
    settingsComposable("settings/account") {
        AccountSettings(navController, updateState)
    }
    settingsComposable("settings/hidden_playlists") {
        HiddenPlaylistsScreen(navController)
    }
    settingsComposable("settings/appearance") {
        AppearanceSettings(navController)
    }
    settingsComposable(AppearanceContract.CANVAS_ROUTE) {
        moe.rukamori.archivetune.ui.screens.settings.canvas.CanvasSettings(navController)
    }
    settingsComposable("settings/appearance/icon") {
        IconScreen(navController)
    }
    settingsComposable("settings/appearance/palette_picker") {
        PalettePickerScreen(navController)
    }
    settingsComposable("settings/appearance/theme_creator") {
        ThemeCreatorScreen(navController)
    }
    settingsComposable("settings/content") {
        ContentSettings(navController)
    }
    settingsComposable("settings/player") {
        PlayerSettings(navController)
    }
    settingsComposable("settings/lyrics") {
        LyricsSettings(navController)
    }
    settingsComposable("settings/internet") {
        InternetSettings(navController)
    }
    settingsComposable("settings/storage") {
        StorageSettings(navController)
    }
    settingsComposable("settings/privacy") {
        PrivacySettings(navController)
    }
    settingsComposable("settings/backup_restore") {
        BackupAndRestore(navController)
    }
    settingsComposable("settings/discord") {
        DiscordSettings(navController)
    }
    settingsComposable("settings/integration") {
        IntegrationScreen(navController)
    }
    settingsComposable("settings/ai_integration") {
        AiIntegrationSettings(navController)
    }
    settingsComposable("settings/music_together") {
        MusicTogetherScreen(navController)
    }
    settingsComposable("settings/lastfm") {
        LastFMSettings(navController)
    }
    settingsComposable("settings/discord/experimental") {
        moe.rukamori.archivetune.ui.screens.settings
            .DiscordExperimental(navController)
    }
    settingsComposable("settings/misc") {
        DebugSettings(navController)
    }
    settingsComposable("settings/spatial_audio") {
        SpatialAudioSettings(navController)
    }
    settingsComposable("settings/dual_play_debug") {
        moe.rukamori.archivetune.ui.screens.settings.DualPlayDebugScreen(navController)
    }
    settingsComposable(
        route = "settings/changelog?channel={channel}",
        arguments =
            listOf(
                navArgument("channel") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        val channelName = backStackEntry.arguments?.getString("channel")
        val channel =
            channelName?.let {
                runCatching { UpdateChannel.valueOf(it) }.getOrNull()
            } ?: defaultUpdateChannel
        ChangelogScreen(navController, channel = channel)
    }
    settingsComposable("settings/about") {
        AboutScreen(navController)
    }
    settingsComposable("settings/po_token") {
        PoTokenScreen(navController)
    }
    settingsComposable(
        route = "$LOGIN_ROUTE?$LOGIN_URL_ARGUMENT={$LOGIN_URL_ARGUMENT}",
        arguments =
            listOf(
                navArgument(LOGIN_URL_ARGUMENT) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        LoginScreen(
            navController,
            startUrl = backStackEntry.arguments?.getString(LOGIN_URL_ARGUMENT)?.let(Uri::decode),
        )
    }
}
