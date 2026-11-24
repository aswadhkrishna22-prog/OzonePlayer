package moe.rukamori.archivetune.ui.scaffold

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import android.widget.Toast
import moe.rukamori.archivetune.ui.component.headerLiquidGlass
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import moe.rukamori.archivetune.ui.component.playlist.AddPlaylistBottomSheet
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.ui.component.OzoneHeaderLogo
import moe.rukamori.archivetune.ui.screens.Screens
import dev.chrisbanes.haze.HazeState
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.state.UpdateState
import moe.rukamori.archivetune.viewmodels.NewsViewModel
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ScaffoldTopAppBar(
    visible: Boolean,
    navController: NavHostController,
    navBackStackEntry: NavBackStackEntry?,
    scrollController: ScaffoldScrollController,
    useRail: Boolean,
    pureBlack: Boolean,
    splashDone: Boolean,
    updateState: UpdateState,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    accountName: String = "",
    accentColor: Color? = null,
    hazeState: HazeState? = null,
    blurRadius: Float = SettingsDimensions.BlurRadiusDefault,
    blurNavBar: Boolean = true,
) {
    if (!visible) return

    val newsViewModel: NewsViewModel = hiltViewModel()
    val hasUnreadNews by newsViewModel.hasUnreadNews.collectAsStateWithLifecycle()
    val isLibraryRoute = navBackStackEntry?.destination?.route == Screens.Library.route
    val currentRoute = navBackStackEntry?.destination?.route
    val shouldUseFloatingTopBar = remember(currentRoute) {
        currentRoute == Screens.Home.route || currentRoute == Screens.Search.route || currentRoute == Screens.Library.route
    }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val currentScrollBehavior = scrollController.currentScrollBehavior(currentRoute)
    var showAddPlaylistSheet by rememberSaveable { mutableStateOf(false) }

    var headerHeightPx by remember { mutableStateOf(0) }
    LaunchedEffect(currentScrollBehavior, headerHeightPx) {
        if (headerHeightPx > 0 && !isLibraryRoute) {
            val limit = -headerHeightPx.toFloat()
            val state = currentScrollBehavior.state
            if (state.heightOffsetLimit != limit) {
                state.heightOffsetLimit = limit
                state.heightOffset = state.heightOffset.coerceIn(limit, 0f)
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { if (it.height > 0) headerHeightPx = it.height }
            .offset { IntOffset(0, if (isLibraryRoute) 0 else currentScrollBehavior.state.heightOffset.roundToInt()) },
    ) {
        val appBarHeightPx = with(LocalDensity.current) { AppBarHeight.toPx() }
        val statusBarTop = with(LocalDensity.current) { WindowInsets.systemBars.getTop(this).toDp() }
        val totalHeaderHeight = AppBarHeight + statusBarTop
        val bg = if (pureBlack) Color.Black else MaterialTheme.colorScheme.background
        val gradientBrush = remember(bg) {
            Brush.verticalGradient(
                colors = listOf(
                    bg.copy(alpha = 0.85f),
                    bg.copy(alpha = 0.0f),
                ),
            )
        }

        Box(
            modifier = Modifier
                .offset {
                    if (isLibraryRoute) IntOffset(0, 0) else {
                        val raw = currentScrollBehavior.state.heightOffset
                        IntOffset(0, (raw.coerceAtLeast(-appBarHeightPx) - raw).roundToInt())
                    }
                }
                .fillMaxWidth()
                .height(totalHeaderHeight)
                .background(gradientBrush),
        )

        TopAppBar(
            windowInsets = WindowInsets.safeDrawing.only(
                (if (useRail) WindowInsetsSides.Right else WindowInsetsSides.Horizontal) + WindowInsetsSides.Top,
            ),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (currentRoute == Screens.Home.route) {
                        Text(
                            text = getGreetingText(),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        OzoneHeaderLogo(
                            isHomeScreen = false,
                            modifier = Modifier
                                .size(35.dp)
                                .padding(end = 6.dp),
                        )
                        Text(
                            text = stringResource(R.string.ui_app_name),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            actions = {
                val hasUpdate = splashDone && (updateState is UpdateState.SoftUpdate || updateState is UpdateState.CriticalUpdate)
                if (isLibraryRoute) {
                    TopBarAddPlaylistButton(
                        onClick = { showAddPlaylistSheet = true },
                    )
                }
                HomeTopBarAccountMenu(
                    avatarUrl = avatarUrl,
                    accountName = accountName,
                    accentColor = accentColor,
                    hasUnreadNews = hasUnreadNews,
                    hasUpdate = hasUpdate,
                    onNavigateProfile = { navController.navigate("account") },
                    onNavigateHistory = { navController.navigate("history") },
                    onNavigateNews = { navController.navigate("news") },
                    onNavigateNewRelease = { navController.navigate("new_release") },
                    onNavigateSettings = { navController.navigate("settings") },
                    hazeState = hazeState,
                    blurRadius = blurRadius,
                    blurNavBar = blurNavBar,
                    pureBlack = pureBlack,
                )
            },
            scrollBehavior = if (isLibraryRoute || shouldUseFloatingTopBar) null else scrollController.topAppBarScrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )

        if (showAddPlaylistSheet) {
            val context = LocalContext.current
            AddPlaylistBottomSheet(
                onDismissRequest = { showAddPlaylistSheet = false },
                onPlaylistAdded = { _ ->
                    val message = context.getString(R.string.playlist_added)
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

@Composable
private fun getGreetingText(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> stringResource(R.string.good_morning)
        in 12..17 -> stringResource(R.string.good_afternoon)
        in 18..23 -> stringResource(R.string.good_evening)
        else -> stringResource(R.string.good_night)
    }
}
