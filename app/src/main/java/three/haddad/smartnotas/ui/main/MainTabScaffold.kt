package three.haddad.smartnotas.ui.main

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
import kotlinx.coroutines.launch
import three.haddad.smartnotas.R
import three.haddad.smartnotas.data.NoteRepository
import three.haddad.smartnotas.notifications.appNotificationSettingsIntent
import three.haddad.smartnotas.ui.common.LocalAllowCompactControls
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.common.RememberPredictiveBackHandler
import three.haddad.smartnotas.ui.common.isLandscape
import three.haddad.smartnotas.ui.common.isSmallLandscape
import three.haddad.smartnotas.ui.common.rememberShareAppAction
import three.haddad.smartnotas.ui.components.AlertChromeSummary
import three.haddad.smartnotas.ui.components.AlertFloatingActionButtonMenu
import three.haddad.smartnotas.ui.components.RememberConfirmDialog
import three.haddad.smartnotas.ui.components.RememberFloatingActionButton
import three.haddad.smartnotas.ui.components.RememberIconButton
import three.haddad.smartnotas.ui.components.UpdateChromeState
import three.haddad.smartnotas.ui.edit.DEFAULT_LIST_HEADER_SYMBOL
import three.haddad.smartnotas.ui.edit.DEFAULT_NOTE_HEADER_SYMBOL
import three.haddad.smartnotas.ui.feedback.appClickable
import three.haddad.smartnotas.ui.history.HistorySection
import three.haddad.smartnotas.ui.nav.LocalNavAnimatedVisibilityScope
import three.haddad.smartnotas.ui.nav.LocalSharedTransitionScope
import three.haddad.smartnotas.ui.theme.MorphPolygonShape
import three.haddad.smartnotas.ui.theme.RoundedPolygonShape
import three.haddad.smartnotas.ui.theme.reducedMotionAwareSpec
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

    val isDark = three.haddad.smartnotas.ui.theme.LocalIsDark.current
    val semanticPalette = three.haddad.smartnotas.ui.theme.LocalSemanticPalette.current
    val themeState = three.haddad.smartnotas.ui.theme.LocalThemeState.current
    val isBlackTheme = themeState.blackThemeActive(isDark) || themeState.accentTheme == three.haddad.smartnotas.data.AccentTheme.PURE_BLACK

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
        if (isDark) Color(0xFFA6A4B4) else Color(0xFF757482)

    val navDividerColor =
        if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)

    Scaffold(
        containerColor = if (isDark) Color.Transparent else MaterialTheme.colorScheme.background,
        bottomBar = {
            if (chromeVisible) {
                Column {
                    androidx.compose.material3.HorizontalDivider(
                        color = navDividerColor,
                    )
                    NavigationBar(
                        containerColor =
                            three.haddad.smartnotas.ui.theme.glassContainerColor(
                                variant = three.haddad.smartnotas.ui.theme.GlassVariant.BottomNav,
                                isDark = isDark,
                            ),
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets.navigationBars,
                    ) {
                        MainTab.entries.forEach { tabItem ->
                            val selected = currentTab == tabItem
                            val itemColor = if (selected) selectedAccent else unselectedNavColor
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
                                            tint = itemColor,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    } else {
                                        RememberMaterialRoundedSymbol(
                                            name = tabItem.symbolName,
                                            tint = itemColor,
                                            weight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            filled = false,
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = stringResource(tabItem.labelRes),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = itemColor,
                                    )
                                },
                                colors =
                                    NavigationBarItemDefaults.colors(
                                        selectedIconColor = selectedAccent,
                                        selectedTextColor = selectedAccent,
                                        indicatorColor = activePillTint,
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
