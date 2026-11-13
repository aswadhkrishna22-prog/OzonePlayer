/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings.player

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AudioNormalizationKey
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.constants.AudioQualityKey
import moe.rukamori.archivetune.constants.AutoLowDataModeKey
import moe.rukamori.archivetune.constants.AutoSkipNextOnErrorKey
import moe.rukamori.archivetune.lowdata.AutoLowDataMode
import moe.rukamori.archivetune.constants.AutoStartOnBluetoothKey
import moe.rukamori.archivetune.constants.BeastModeAutoPromptKey
import moe.rukamori.archivetune.constants.SmartVibeQueueKey
import moe.rukamori.archivetune.constants.AutomixEnabledKey
import moe.rukamori.archivetune.constants.AutomixTransitionDuration
import moe.rukamori.archivetune.constants.AutomixTransitionDurationKey
import moe.rukamori.archivetune.constants.CrossfadeDurationKey
import moe.rukamori.archivetune.constants.CrossfadeEnabledKey
import moe.rukamori.archivetune.constants.CrossfadeGaplessKey
import moe.rukamori.archivetune.constants.DoubleTapSeekStepKey
import moe.rukamori.archivetune.constants.DoubleTapToSeekKey
import moe.rukamori.archivetune.constants.DownloadLocationUriKey
import moe.rukamori.archivetune.constants.EnableLosslessKey
import moe.rukamori.archivetune.constants.Prefer320KbpsKey
import moe.rukamori.archivetune.constants.FlacDownloadQualityKey
import moe.rukamori.archivetune.constants.FlacQuality
import moe.rukamori.archivetune.constants.FlacStreamingQualityKey
import moe.rukamori.archivetune.constants.LowDataModeKey
import moe.rukamori.archivetune.constants.MemoryCacheToggleKey
import moe.rukamori.archivetune.constants.PauseOnDeviceMuteKey
import moe.rukamori.archivetune.constants.PlaybackSource
import moe.rukamori.archivetune.constants.PlaybackSourceKey
import moe.rukamori.archivetune.constants.QobuzAppIdKey
import moe.rukamori.archivetune.constants.QobuzAppSecretKey
import moe.rukamori.archivetune.constants.QobuzUserAuthTokenKey
import moe.rukamori.archivetune.constants.SkipSilenceKey
import moe.rukamori.archivetune.constants.StopMusicOnTaskClearKey
import moe.rukamori.archivetune.constants.WakelockKey
import moe.rukamori.archivetune.sponsorblock.normalizeSponsorBlockApiUrl
import moe.rukamori.archivetune.ui.component.GlassDefaults
import moe.rukamori.archivetune.ui.component.GlassScaffold
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.theme.TestThemeWrapper
import moe.rukamori.archivetune.ui.theme.ThemePreviews
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.SponsorBlockCategoryUiModel
import moe.rukamori.archivetune.viewmodels.SponsorBlockSettingsScreenState
import moe.rukamori.archivetune.viewmodels.SponsorBlockSettingsViewModel

@Composable
fun PlayerSettings(navController: NavController) {
    val context = LocalContext.current

    val (playbackSource, onPlaybackSourceChange) = rememberEnumPreference(
        PlaybackSourceKey,
        defaultValue = PlaybackSource.YT_MUSIC,
    )
    val (flacStreamingQuality, onFlacStreamingQualityChange) = rememberEnumPreference(
        FlacStreamingQualityKey,
        defaultValue = FlacQuality.CD,
    )
    val (flacDownloadQuality, onFlacDownloadQualityChange) = rememberEnumPreference(
        FlacDownloadQualityKey,
        defaultValue = FlacQuality.HI_RES,
    )
    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(
        AudioQualityKey,
        defaultValue = AudioQuality.AUTO,
    )
    val (lowDataMode, onLowDataModeChange) = rememberPreference(
        LowDataModeKey,
        defaultValue = false,
    )
    val (autoLowDataMode, onAutoLowDataModeChange) = rememberEnumPreference(
        AutoLowDataModeKey,
        defaultValue = AutoLowDataMode.OFF,
    )
    val (skipSilence, onSkipSilenceChange) = rememberPreference(
        SkipSilenceKey,
        defaultValue = false,
    )
    val (audioNormalization, onAudioNormalizationChange) = rememberPreference(
        AudioNormalizationKey,
        defaultValue = true,
    )
    val (autoSkipNextOnError, onAutoSkipNextOnErrorChange) = rememberPreference(
        AutoSkipNextOnErrorKey,
        defaultValue = false,
    )
    val (pauseOnDeviceMute, onPauseOnDeviceMuteChange) = rememberPreference(
        PauseOnDeviceMuteKey,
        defaultValue = false,
    )
    val (autoStartOnBluetooth, onAutoStartOnBluetoothChange) = rememberPreference(
        AutoStartOnBluetoothKey,
        defaultValue = false,
    )
    val (beastModeAutoPrompt, onBeastModeAutoPromptChange) = rememberPreference(
        BeastModeAutoPromptKey,
        defaultValue = true,
    )
    val (smartVibeQueue, onSmartVibeQueueChange) = rememberPreference(
        SmartVibeQueueKey,
        defaultValue = true,
    )
    val (stopMusicOnTaskClear, onStopMusicOnTaskClearChange) = rememberPreference(
        StopMusicOnTaskClearKey,
        defaultValue = false,
    )
    val (wakelockEnabled, onWakelockChange) = rememberPreference(
        WakelockKey,
        defaultValue = false,
    )
    val (doubleTapToSeek, onDoubleTapToSeekChange) = rememberPreference(
        DoubleTapToSeekKey,
        defaultValue = true,
    )
    val (doubleTapSeekStep, onDoubleTapSeekStepChange) = rememberPreference(
        DoubleTapSeekStepKey,
        defaultValue = 10,
    )

    val (crossfadeEnabled, onCrossfadeEnabledChange) = rememberPreference(
        CrossfadeEnabledKey,
        defaultValue = false,
    )
    val (crossfadeDurationSeconds, onCrossfadeDurationSecondsChange) = rememberPreference(
        CrossfadeDurationKey,
        defaultValue = 5f,
    )
    val (crossfadeGapless, onCrossfadeGaplessChange) = rememberPreference(
        CrossfadeGaplessKey,
        defaultValue = true,
    )
    val (automixEnabled, onAutomixEnabledChange) = rememberPreference(
        AutomixEnabledKey,
        defaultValue = false,
    )
    val (automixTransitionDurationStr, onAutomixTransitionDurationChangeStr) = rememberPreference(
        AutomixTransitionDurationKey,
        defaultValue = AutomixTransitionDuration.AUTO.value,
    )
    val automixTransitionDuration = remember(automixTransitionDurationStr) {
        AutomixTransitionDuration.entries.find { it.value.equals(automixTransitionDurationStr, ignoreCase = true) }
            ?: AutomixTransitionDuration.AUTO
    }

    val (_, onEnableLosslessChange) = rememberPreference(EnableLosslessKey, false)
    val (prefer320Kbps, onPrefer320KbpsChange) = rememberPreference(Prefer320KbpsKey, true)
    val (memoryCacheToggle, onMemoryCacheToggleChange) = rememberPreference(MemoryCacheToggleKey, false)
    val (downloadLocationUri, onDownloadLocationUriChange) = rememberPreference(DownloadLocationUriKey, "")

    val flacFolderPath = remember(downloadLocationUri, context) {
        resolveFlacFolderPath(context, downloadLocationUri)
    }
    val (qobuzAppId, onQobuzAppIdChange) = rememberPreference(QobuzAppIdKey, "")
    val (qobuzAppSecret, onQobuzAppSecretChange) = rememberPreference(QobuzAppSecretKey, "")
    val (qobuzUserAuthToken, onQobuzUserAuthTokenChange) = rememberPreference(QobuzUserAuthTokenKey, "")

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            uri?.let {
                onDownloadLocationUriChange(it.toString())
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    Toast.makeText(context, context.getString(R.string.folder_persist_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    val onEqualizerClick = remember(context) {
        {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.equalizer),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    val state = PlayerSettingsUiState(
        playbackSource = playbackSource,
        flacStreamingQuality = flacStreamingQuality,
        flacDownloadQuality = flacDownloadQuality,
        audioQuality = audioQuality,
        lowDataMode = lowDataMode,
        autoLowDataMode = autoLowDataMode,
        skipSilence = skipSilence,
        audioNormalization = audioNormalization,
        autoSkipNextOnError = autoSkipNextOnError,
        pauseOnDeviceMute = pauseOnDeviceMute,
        autoStartOnBluetooth = autoStartOnBluetooth,
        beastModeAutoPrompt = beastModeAutoPrompt,
        smartVibeQueue = smartVibeQueue,
        stopMusicOnTaskClear = stopMusicOnTaskClear,
        wakelockEnabled = wakelockEnabled,
        crossfadeEnabled = crossfadeEnabled,
        crossfadeDurationSeconds = crossfadeDurationSeconds,
        crossfadeGapless = crossfadeGapless,
        automixEnabled = automixEnabled,
        automixTransitionDuration = automixTransitionDuration,
        memoryCacheToggle = memoryCacheToggle,
        downloadLocationUri = downloadLocationUri,
        flacFolderPath = flacFolderPath,
        qobuzAppId = qobuzAppId,
        qobuzAppSecret = qobuzAppSecret,
        qobuzUserAuthToken = qobuzUserAuthToken,
        prefer320Kbps = prefer320Kbps,
        doubleTapToSeek = doubleTapToSeek,
        doubleTapSeekStep = doubleTapSeekStep,
    )

    val actions = remember(
        navController,
        folderPickerLauncher,
        onEqualizerClick,
        onAutomixTransitionDurationChangeStr,
    ) {
        PlayerSettingsUiActions(
            onNavigateUp = navController::navigateUp,
            onNavigateHome = navController::backToMain,
            onPlaybackSourceChange = onPlaybackSourceChange,
            onEnableLosslessChange = onEnableLosslessChange,
            onFlacStreamingQualityChange = onFlacStreamingQualityChange,
            onFlacDownloadQualityChange = onFlacDownloadQualityChange,
            onAudioQualityChange = onAudioQualityChange,
            onLowDataModeChange = onLowDataModeChange,
            onAutoLowDataModeChange = onAutoLowDataModeChange,
            onSkipSilenceChange = onSkipSilenceChange,
            onAudioNormalizationChange = onAudioNormalizationChange,
            onAutoSkipNextOnErrorChange = onAutoSkipNextOnErrorChange,
            onPauseOnDeviceMuteChange = onPauseOnDeviceMuteChange,
            onAutoStartOnBluetoothChange = onAutoStartOnBluetoothChange,
            onBeastModeAutoPromptChange = onBeastModeAutoPromptChange,
            onSmartVibeQueueChange = onSmartVibeQueueChange,
            onStopMusicOnTaskClearChange = onStopMusicOnTaskClearChange,
            onWakelockChange = onWakelockChange,
            onDoubleTapToSeekChange = onDoubleTapToSeekChange,
            onDoubleTapSeekStepChange = onDoubleTapSeekStepChange,
            onCrossfadeEnabledChange = onCrossfadeEnabledChange,
            onCrossfadeDurationSecondsChange = onCrossfadeDurationSecondsChange,
            onCrossfadeGaplessChange = onCrossfadeGaplessChange,
            onAutomixEnabledChange = onAutomixEnabledChange,
            onAutomixTransitionDurationChange = { newDuration ->
                onAutomixTransitionDurationChangeStr(newDuration.value)
            },
            onMemoryCacheToggleChange = onMemoryCacheToggleChange,
            onSelectFlacDownloadFolder = { folderPickerLauncher.launch(null) },
            onQobuzAppIdChange = onQobuzAppIdChange,
            onQobuzAppSecretChange = onQobuzAppSecretChange,
            onQobuzUserAuthTokenChange = onQobuzUserAuthTokenChange,
            onPrefer320KbpsChange = onPrefer320KbpsChange,
            onEqualizerClick = onEqualizerClick,
            onSpatialAudioClick = { navController.navigate("settings/spatial_audio") },
            onDualPlayClick = { navController.navigate("settings/dual_play_debug") },
        )
    }

    val sponsorBlockViewModel: SponsorBlockSettingsViewModel = hiltViewModel()
    val sponsorBlockState by sponsorBlockViewModel.uiState.collectAsStateWithLifecycle()

    val onSponsorBlockEnabledChange = remember(sponsorBlockViewModel) {
        sponsorBlockViewModel::onEnabledChange
    }
    val onSponsorBlockCategorySheetOpen = remember(sponsorBlockViewModel) {
        sponsorBlockViewModel::onCategorySheetOpen
    }
    val onSponsorBlockCategorySheetDismiss = remember(sponsorBlockViewModel) {
        sponsorBlockViewModel::onCategorySheetDismiss
    }
    val onSponsorBlockCategoryCheckedChange = remember(sponsorBlockViewModel) {
        sponsorBlockViewModel::onCategoryOptionCheckedChange
    }
    val onSponsorBlockCategorySelectionConfirm = remember(sponsorBlockViewModel) {
        sponsorBlockViewModel::onCategorySelectionConfirm
    }
    val onSponsorBlockApiUrlEditorOpen = remember(sponsorBlockViewModel) {
        sponsorBlockViewModel::onApiUrlEditorOpen
    }
    val onSponsorBlockApiUrlEditorDismiss = remember(sponsorBlockViewModel) {
        sponsorBlockViewModel::onApiUrlEditorDismiss
    }
    val onSponsorBlockApiUrlDraftChange = remember(sponsorBlockViewModel) {
        sponsorBlockViewModel::onApiUrlDraftChange
    }
    val onSponsorBlockApiUrlConfirm = remember(sponsorBlockViewModel) {
        sponsorBlockViewModel::onApiUrlConfirm
    }
    val onSponsorBlockRetry = remember(sponsorBlockViewModel) {
        sponsorBlockViewModel::retry
    }

    SponsorBlockApiUrlDialog(
        state = sponsorBlockState,
        onValueChange = onSponsorBlockApiUrlDraftChange,
        onConfirm = onSponsorBlockApiUrlConfirm,
        onDismiss = onSponsorBlockApiUrlEditorDismiss,
    )

    PlayerSettingsScreen(
        state = state,
        actions = actions,
        sponsorBlockState = sponsorBlockState,
        onSponsorBlockEnabledChange = onSponsorBlockEnabledChange,
        onCategorySheetOpen = onSponsorBlockCategorySheetOpen,
        onCategorySheetDismiss = onSponsorBlockCategorySheetDismiss,
        onCategoryCheckedChange = onSponsorBlockCategoryCheckedChange,
        onCategorySelectionConfirm = onSponsorBlockCategorySelectionConfirm,
        onApiUrlEditorOpen = onSponsorBlockApiUrlEditorOpen,
        onRetry = onSponsorBlockRetry,
    )
}

@Composable
private fun SponsorBlockApiUrlDialog(
    state: SponsorBlockSettingsScreenState,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val data = (state as? SponsorBlockSettingsScreenState.Success)?.data ?: return
    if (!data.isApiUrlEditorVisible) return

    val isInputValid = remember(data.isApiUrlDraftValid) {
        { input: String -> normalizeSponsorBlockApiUrl(input) != null }
    }

    TextFieldDialog(
        title = { Text(stringResource(R.string.sponsor_block_api_url)) },
        initialTextFieldValue = TextFieldValue(
            text = data.apiUrlDraft,
            selection = TextRange(data.apiUrlDraft.length),
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
        ),
        isInputValid = isInputValid,
        onDone = { value ->
            onValueChange(value)
            onConfirm()
        },
        onDismiss = onDismiss,
    )
}

@Composable
internal fun PlayerSettingsScreen(
    state: PlayerSettingsUiState,
    actions: PlayerSettingsUiActions,
    sponsorBlockState: SponsorBlockSettingsScreenState = SponsorBlockSettingsScreenState.Loading,
    onSponsorBlockEnabledChange: (Boolean) -> Unit = {},
    onCategorySheetOpen: () -> Unit = {},
    onCategorySheetDismiss: () -> Unit = {},
    onCategoryCheckedChange: (SponsorBlockCategoryUiModel, Boolean) -> Unit = { _, _ -> },
    onCategorySelectionConfirm: () -> Unit = {},
    onApiUrlEditorOpen: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    GlassScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.player_and_audio)) },
                navigationIcon = {
                    IconButton(
                        onClick = actions.onNavigateUp,
                        onLongClick = actions.onNavigateHome,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                colors = GlassDefaults.topAppBarColors(),
            )
        },
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()

        PlayerSettingsContent(
            state = state,
            actions = actions,
            sponsorBlockState = sponsorBlockState,
            onSponsorBlockEnabledChange = onSponsorBlockEnabledChange,
            onCategorySheetOpen = onCategorySheetOpen,
            onCategorySheetDismiss = onCategorySheetDismiss,
            onCategoryCheckedChange = onCategoryCheckedChange,
            onCategorySelectionConfirm = onCategorySelectionConfirm,
            onApiUrlEditorOpen = onApiUrlEditorOpen,
            onRetry = onRetry,
            modifier = Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal)
                ),
        )
    }
}

@ThemePreviews
@Composable
private fun PlayerSettingsPreview() {
    TestThemeWrapper {
        PlayerSettings(navController = rememberNavController())
    }
}

private fun resolveFlacFolderPath(context: Context, downloadLocationUri: String): String? {
    if (downloadLocationUri.isBlank()) {
        return null
    }
    return runCatching {
        val uri = Uri.parse(downloadLocationUri)
        val docFile = DocumentFile.fromTreeUri(context, uri)
        val name = docFile?.name?.takeIf { it.isNotBlank() }
        val rawPath = uri.lastPathSegment?.substringAfterLast(":")?.takeIf { it.isNotBlank() }
        name ?: rawPath ?: downloadLocationUri
    }.getOrNull() ?: downloadLocationUri
}
