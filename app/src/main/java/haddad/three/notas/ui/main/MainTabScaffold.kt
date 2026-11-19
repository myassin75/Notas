package haddad.three.notas.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.graphics.shapes.Morph
import haddad.three.notas.R
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.notifications.appNotificationSettingsIntent
import haddad.three.notas.ui.common.LocalAllowCompactControls
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.common.RememberPredictiveBackHandler
import haddad.three.notas.ui.common.isLandscape
import haddad.three.notas.ui.common.isSmallLandscape
import haddad.three.notas.ui.common.rememberShareAppAction
import haddad.three.notas.ui.components.AlertChromeSummary
import haddad.three.notas.ui.components.AlertFloatingActionButtonMenu
import haddad.three.notas.ui.components.RememberConfirmDialog
import haddad.three.notas.ui.components.RememberFloatingActionButton
import haddad.three.notas.ui.components.RememberIconButton
import haddad.three.notas.ui.components.UpdateChromeState
import haddad.three.notas.ui.edit.DEFAULT_LIST_HEADER_SYMBOL
import haddad.three.notas.ui.edit.DEFAULT_NOTE_HEADER_SYMBOL
import haddad.three.notas.ui.feedback.appClickable
import haddad.three.notas.ui.history.HistorySection
import haddad.three.notas.ui.nav.LocalNavAnimatedVisibilityScope
import haddad.three.notas.ui.nav.LocalSharedTransitionScope
import haddad.three.notas.ui.theme.MorphPolygonShape
import haddad.three.notas.ui.theme.RoundedPolygonShape
import haddad.three.notas.ui.theme.reducedMotionAwareSpec
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class MainTab(
    @param:androidx.annotation.StringRes val labelRes: Int,
    val symbolName: String,
) {
    Home(R.string.main_tab_home, "home"),
    Calendar(R.string.main_tab_calendar, "calendar_month"),
    Settings(R.string.main_tab_settings, "settings"),
    Search(R.string.main_tab_search, "search"),
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3AdaptiveNavigationSuiteApi::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
fun MainTabScaffold(
    repository: NoteRepository,
    currentTab: MainTab,
    chromeVisible: Boolean = true,
    useDualPaneMode: Boolean = false,
    onTabSelected: (MainTab) -> Unit,
    onCreateNote: () -> Unit,
    onCreateList: () -> Unit,
    onImportGoogleTasks: () -> Unit,
    historySection: HistorySection,
    historyVisibleItemCount: Int,
    updateBarState: UpdateChromeState,
    onUpdateClick: () -> Unit,
    onDismissUpdateAvailable: () -> Unit,
    onInstallUpdate: () -> Unit,
    alertSummary: AlertChromeSummary,
    blockedReminderCount: Int,
    alertBarsExpanded: Boolean,
    onAlertBarsExpandedChange: (Boolean) -> Unit,
    content: @Composable (closeRevealRequest: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var clearTrashOpen by rememberSaveable { mutableStateOf(false) }
    var moveArchiveToTrashOpen by rememberSaveable { mutableStateOf(false) }
    var closeNotesRevealRequest by rememberSaveable { mutableIntStateOf(0) }

    val isDark = haddad.three.notas.ui.theme.LocalIsDark.current
    val semanticPalette = haddad.three.notas.ui.theme.LocalSemanticPalette.current
    val themeState = haddad.three.notas.ui.theme.LocalThemeState.current
    val isBlackTheme = themeState.blackThemeActive(isDark) || themeState.accentTheme == haddad.three.notas.data.AccentTheme.PURE_BLACK

    val selectedAccent =
        if (isDark) {
            if (isBlackTheme) Color(0xFFD4D4DE) else semanticPalette.primaryAction
        } else {
            if (isBlackTheme) Color(0xFF16161A) else MaterialTheme.colorScheme.primary
        }

    val activePillTint =
        if (isDark) {
            if (isBlackTheme) Color(0xFFD4D4DE).copy(alpha = 0.18f) else selectedAccent.copy(alpha = 0.16f)
        } else {
            if (isBlackTheme) Color(0xFF16161A).copy(alpha = 0.10f) else selectedAccent.copy(alpha = 0.12f)
        }

    val unselectedNavColor =
        if (isDark) Color(0xFFA3A3A3) else Color(0xFF757482)

    val navDividerColor =
        if (isDark) Color(0xFF3A3A3A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (chromeVisible) {
                Column {
                    androidx.compose.material3.HorizontalDivider(
                        color = navDividerColor,
                    )
                    NavigationBar(
                        containerColor =
                            haddad.three.notas.ui.theme.glassContainerColor(
                                variant = haddad.three.notas.ui.theme.GlassVariant.BottomNav,
                                isDark = isDark,
                            ),
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets.navigationBars,
                    ) {
                        MainTab.entries.forEach { tabItem ->
                            val selected = currentTab == tabItem
                            val iconColor = if (selected) (if (isDark) (if (isBlackTheme) Color(0xFF16161C) else Color.White) else selectedAccent) else unselectedNavColor
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    closeNotesRevealRequest++
                                    onTabSelected(tabItem)
                                },
                                icon = {
                                    if (tabItem == MainTab.Home) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_home_outlined),
                                            contentDescription = null,
                                            tint = iconColor,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    } else {
                                        RememberMaterialRoundedSymbol(
                                            name = tabItem.symbolName,
                                            tint = iconColor,
                                            weight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            filled = false,
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = stringResource(tabItem.labelRes),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) selectedAccent else unselectedNavColor,
                                    )
                                },
                                colors =
                                    NavigationBarItemDefaults.colors(
                                        selectedIconColor = if (isDark) (if (isBlackTheme) Color(0xFF16161C) else Color.White) else selectedAccent,
                                        selectedTextColor = selectedAccent,
                                        indicatorColor = if (isDark) (if (isBlackTheme) Color(0xFFD4D4DE) else selectedAccent) else activePillTint,
                                        unselectedIconColor = unselectedNavColor,
                                        unselectedTextColor = unselectedNavColor,
                                    ),
                            )
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            content(closeNotesRevealRequest)
        }
    }

    if (clearTrashOpen) {
        RememberConfirmDialog(
            title = stringResource(R.string.main_empty_trash_title),
            text = stringResource(R.string.main_empty_trash_subtitle),
            confirmLabel = stringResource(R.string.common_empty),
            onConfirm = {
                clearTrashOpen = false
                scope.launch { repository.emptyTrash() }
            },
            onDismiss = { clearTrashOpen = false },
            destructive = true,
        )
    }

    if (moveArchiveToTrashOpen) {
        RememberConfirmDialog(
            title = stringResource(R.string.main_move_archive_to_trash_title),
            text = stringResource(R.string.main_move_archive_to_trash_subtitle),
            confirmLabel = stringResource(R.string.edit_bottom_bar_trash),
            onConfirm = {
                moveArchiveToTrashOpen = false
                scope.launch { repository.moveAllArchivedToTrash() }
            },
            onDismiss = { moveArchiveToTrashOpen = false },
            destructive = true,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NotesCreateFabMenu(
    expanded: Boolean,
    onToggle: () -> Unit,
    onPickImport: () -> Unit,
    onPickList: () -> Unit,
    onPickNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Just a placeholder to keep it compiling if it's not actually used here
}
