@file:Suppress("ConfigurationScreenWidthHeight")

package three.haddad.smartnotas.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.biometric.BiometricManager
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import three.haddad.smartnotas.BuildConfig
import three.haddad.smartnotas.R
import three.haddad.smartnotas.data.DefaultNotePreferencesState
import three.haddad.smartnotas.data.InteractionState
import three.haddad.smartnotas.data.LockPrefs
import three.haddad.smartnotas.data.QuickCaptureState
import three.haddad.smartnotas.data.ReminderPreferencesState
import three.haddad.smartnotas.data.UpdateCheckSchedule
import three.haddad.smartnotas.data.UpdatePreferencesState
import three.haddad.smartnotas.data.ViewOptions
import three.haddad.smartnotas.di.SettingsDependenciesEntryPoint
import three.haddad.smartnotas.diagnostics.DiagnosticLog
import three.haddad.smartnotas.ui.common.AppBottomSheetDragHandle
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.common.isLandscape
import three.haddad.smartnotas.ui.components.RememberFilledTonalIconButton
import three.haddad.smartnotas.ui.components.RememberTextButton
import three.haddad.smartnotas.ui.components.rememberResponsiveActionButtonSize
import three.haddad.smartnotas.ui.components.rememberResponsiveActionIconSize
import three.haddad.smartnotas.ui.components.settings.GroupPosition
import three.haddad.smartnotas.ui.components.settings.GroupedListColumn
import three.haddad.smartnotas.ui.components.settings.GroupedListItem
import three.haddad.smartnotas.ui.edit.DEFAULT_NOTE_HEADER_SYMBOL
import three.haddad.smartnotas.ui.feedback.appClickable
import three.haddad.smartnotas.ui.components.SettingsRow
import three.haddad.smartnotas.ui.components.SettingsRowCard
import three.haddad.smartnotas.ui.components.UserAvatar
import three.haddad.smartnotas.ui.modifiers.PillBottomBarHeight
import three.haddad.smartnotas.ui.modifiers.PillBottomScrimExtra
import three.haddad.smartnotas.ui.theme.LocalSemanticPalette
import three.haddad.smartnotas.ui.modifiers.applyToScrollableList
import three.haddad.smartnotas.ui.modifiers.rememberContentOverflowScrollEnabled
import three.haddad.smartnotas.ui.modifiers.rememberProgressiveBlurStyle
import three.haddad.smartnotas.ui.nav.DEV_OPTIONS_SHARED_BOUNDS_KEY
import three.haddad.smartnotas.ui.nav.LocalNavAnimatedVisibilityScope
import three.haddad.smartnotas.ui.nav.LocalSharedTransitionScope
import three.haddad.smartnotas.ui.theme.LocalNotesUiState
import three.haddad.smartnotas.ui.theme.LocalThemeState
import three.haddad.smartnotas.ui.theme.reducedMotionAwareSpec
import three.haddad.smartnotas.update.PlayInAppUpdateBannerUiState
import three.haddad.smartnotas.update.RememberUpdateInfo
import three.haddad.smartnotas.update.RememberUpdateState


private const val SETTINGS_SECTION_EXPAND_SETTLE_DELAY_MS = 900L

enum class SettingsSectionKey(
    val routeKey: String,
    val iconName: String,
    @param:StringRes val titleRes: Int,
    /**
     * Historic [routeKey] values. [routeKey] is a *stored identifier*: it is persisted in the
     * collapsed-sections preference and accepted from deep links. Renaming one would orphan that
     * stored state, so move the old value here rather than deleting it and everything keeps
     * resolving.
     *
     * This has nothing to do with [titleRes]. The displayed heading is never persisted or compared,
     * so section labels in `strings.xml` can be reworded freely with no effect anywhere else.
     */
    val legacyRouteKeys: List<String> = emptyList(),
) {
    History("history", "history", R.string.main_tab_history),
    Appearance("appearance", "palette", R.string.settings_section_appearance),

    // Remember-only. FilePipe has no note cards, so this section is not part of the shared settings list.
    NotesUi("notes_ui", DEFAULT_NOTE_HEADER_SYMBOL, R.string.settings_section_notes_ui),
    Defaults("defaults", "tune", R.string.settings_defaults_section),
    Notifications("notifications", "notifications", R.string.settings_notifications_section),

    // FilePipe files the same toggle under its broader "Touch & Sound" section. Divergent heading
    // only; the toggle and its behaviour are in parity.
    Haptics("haptics", "vibration", R.string.settings_haptics_section),
    Swipe("swipe", "swipe_left", R.string.settings_swipe_section, legacyRouteKeys = listOf("swipe_actions")),
    Security("security", "security", R.string.settings_section_security),
    Backup("backup", "save", R.string.settings_backup_section),
    Updates("updates", "system_update", R.string.settings_updates_section),
    About("about", "info", R.string.settings_section_about),
    DevOptions("dev_options", "developer_board", R.string.dev_options_title),
}

/**
 * Resolves a stored or deep-linked route key - current or historic - to its section's current
 * [SettingsSectionKey.routeKey]. Returns null when the key matches no section.
 */
fun canonicalSettingsSectionRouteKey(storedKey: String): String? =
    SettingsSectionKey.entries
        .firstOrNull { section -> section.routeKey == storedKey || storedKey in section.legacyRouteKeys }
        ?.routeKey

// The two-pane section list. On offline it keeps Updates for the ObtainX row, which the single-list
// page shows as a standalone card instead (showsUpdatesSection / showsStandaloneObtainXCard).
val settingsPaneSections: List<SettingsSectionKey>
    get() =
        SettingsSectionKey.entries.filter { sectionKey ->
            sectionKey != SettingsSectionKey.DevOptions &&
                (sectionKey != SettingsSectionKey.Updates || showsUpdatesSection(singleList = false))
        }

/**
 * About gets 24dp of extra room only where it directly follows a section on the single-list page.
 * After a standalone item (the dev options entry, offline's ObtainX card) the list's normal spacing
 * is enough.
 */
private fun aboutSectionTopPadding(
    singleList: Boolean,
    devModeEnabled: Boolean,
): Dp = if (singleList && !devModeEnabled && !showsStandaloneObtainXCard(singleList)) 24.dp else 0.dp

/**
 * Route keys of the sections the page emits with a collapse control. About has none, and Updates is
 * left out wherever it isn't emitted, so "all sections collapsed" can still become true.
 */
private fun expandableSettingsSectionKeys(singleList: Boolean): Set<String> =
    settingsPaneSections
        .filter { sectionKey ->
            sectionKey != SettingsSectionKey.About &&
                (sectionKey != SettingsSectionKey.Updates || showsUpdatesSection(singleList))
        }.map { sectionKey -> sectionKey.routeKey }
        .toSet()

// Resolved from the enum rather than a hand-maintained `when`, so a routeKey has exactly one home.
// settingsPaneSections already excludes DevOptions, which is precisely the set this used to accept.
fun settingsSectionKeyForHighlight(highlightSectionKey: String?): SettingsSectionKey? {
    val routeKey = highlightSectionKey?.substringBefore(".") ?: return null
    return settingsPaneSections.firstOrNull { section ->
        section.routeKey == routeKey || routeKey in section.legacyRouteKeys
    }
}

private object SettingsScreenSessionState {
    var collapsedSectionKeys: Set<String> = emptySet()
    var listFirstVisibleItemIndex: Int = 0
    var listFirstVisibleItemScrollOffset: Int = 0
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun SettingsRoute(
    onOpenIntro: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenDevOptions: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenGuestProfile: () -> Unit = {},
    onBack: () -> Unit = {},
    updateVm: RememberUpdateViewModel = hiltViewModel(),
    onUpdateCheckStarted: () -> Unit = {},
    selectedSectionKey: SettingsSectionKey? = null,
    showTopActions: Boolean = true,
    showBackArrow: Boolean = false,
    showSectionHeaders: Boolean = true,
    showAboutHeader: Boolean = true,
    showAboutHeaderTitle: Boolean = true,
    highlightSectionKey: String? = null,
    onHighlightHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val resources = LocalResources.current
    val settingsDependencies =
        remember(context) {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                SettingsDependenciesEntryPoint::class.java,
            )
        }
    val devModePrefs = settingsDependencies.devModePrefs()
    val lockPrefs = settingsDependencies.lockPrefs()
    val interactionPrefs = settingsDependencies.interactionPrefs()
    val quickCapturePrefs = settingsDependencies.quickCapturePrefs()
    val reminderPrefs = settingsDependencies.reminderPrefs()
    val defaultNotePrefs = settingsDependencies.defaultNotePrefs()
    val cloudBackupManager = settingsDependencies.cloudBackupManager()
    val authRepository = settingsDependencies.authRepository()
    val themePrefs = settingsDependencies.themePrefs()
    val notesUiPrefs = settingsDependencies.notesUiPrefs()
    val viewOptionsPrefs = settingsDependencies.viewOptionsPrefs()
    val updatePrefs = settingsDependencies.updatePrefs()
    val playInAppUpdateProgressController = settingsDependencies.playInAppUpdateProgressController()
    val rememberUpdateState: RememberUpdateState = settingsDependencies.rememberUpdateState()
    val updateCheckWorkScheduler = settingsDependencies.updateCheckWorkScheduler()
    val appReviewLauncher = settingsDependencies.appReviewLauncher()
    val scope = rememberCoroutineScope()

    val devModeEnabled by devModePrefs.isEnabled.collectAsStateWithLifecycle(initialValue = false)
    val lockState by lockPrefs.state.collectAsStateWithLifecycle(
        initialValue = LockPrefs.State(),
    )
    val themeState = LocalThemeState.current
    val notesUiState = LocalNotesUiState.current
    val interactionState by interactionPrefs.state.collectAsStateWithLifecycle(
        initialValue = InteractionState(),
    )
    val quickCaptureState by quickCapturePrefs.state.collectAsStateWithLifecycle(
        initialValue = QuickCaptureState(),
    )
    val reminderState by reminderPrefs.state.collectAsStateWithLifecycle(
        initialValue = ReminderPreferencesState(),
    )
    val defaultNoteState by defaultNotePrefs.state.collectAsStateWithLifecycle(
        initialValue = DefaultNotePreferencesState(),
    )

    val biometricAvailable =
        remember(context) {
            BiometricManager
                .from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }
    val deviceCredentialAvailable =
        remember(context) {
            BiometricManager
                .from(context)
                .canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }

    val snackbarHostState = remember { SnackbarHostState() }
    val updateState by updatePrefs.state.collectAsStateWithLifecycle(
        initialValue = UpdatePreferencesState(),
    )
    val viewOptionsState by viewOptionsPrefs.state.collectAsStateWithLifecycle(initialValue = null)
    val globalUpdateInfo by rememberUpdateState.updateInfo.collectAsStateWithLifecycle(initialValue = null)
    val realPlayBannerState by playInAppUpdateProgressController.bannerUiState.collectAsStateWithLifecycle()
    val devReleaseMockPlayBannerState by rememberUpdateState.devReleasePlayBannerMockUiState.collectAsStateWithLifecycle()
    val playBannerState =
        if (devReleaseMockPlayBannerState != PlayInAppUpdateBannerUiState.Hidden) {
            devReleaseMockPlayBannerState
        } else {
            realPlayBannerState
        }
    val configuration = LocalConfiguration.current
    val isLandscape = isLandscape()
    val heightFraction = if (isLandscape) 0.95f else 0.85f
    val maxUpdateSheetHeight = (configuration.screenHeightDp * heightFraction).dp

    val showUpdateSheet by updateVm.showUpdateSheet.collectAsStateWithLifecycle()
    val isCheckingUpdate by updateVm.isCheckingUpdate.collectAsStateWithLifecycle()
    val updateCheckFinishedWithoutResult by updateVm.updateCheckFinishedWithoutResult.collectAsStateWithLifecycle()
    val updateCheckFailed by updateVm.updateCheckFailed.collectAsStateWithLifecycle()
    val downloadProgress by updateVm.downloadProgress.collectAsStateWithLifecycle()
    val updateInfo by updateVm.updateInfo.collectAsStateWithLifecycle()
    val updateSheetChangelog by updateVm.updateSheetChangelog.collectAsStateWithLifecycle()
    val openSheetRequested by updateVm.openSheetRequested.collectAsStateWithLifecycle()

    val playInAppUpdateLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                playInAppUpdateProgressController.onFlexibleUpdateFlowStarted()
            } else {
                Toast
                    .makeText(
                        context,
                        resources.getString(R.string.settings_play_in_app_update_canceled),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }

    LaunchedEffect(globalUpdateInfo) {
        updateVm.adoptGlobalUpdateIfNone(globalUpdateInfo)
    }


    var notificationsGranted by rememberSaveable {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var pendingEnableUpdateNotificationsAfterPermission by rememberSaveable { mutableStateOf(false) }
    val singleList = selectedSectionKey == null
    val settingsExpandableSectionKeys = remember(singleList) { expandableSettingsSectionKeys(singleList) }
    var collapsedSettingsSectionKeys by rememberSaveable {
        mutableStateOf<Set<String>?>(null)
    }
    // Stored keys are canonicalised first so a key written under an older routeKey still resolves.
    val currentCollapsedSectionKeys =
        collapsedSettingsSectionKeys
            ?: viewOptionsState
                ?.settingsCollapsedSectionKeys
                ?.mapNotNull { storedKey -> canonicalSettingsSectionRouteKey(storedKey) }
                ?.filter { it in settingsExpandableSectionKeys }
                ?.toSet()
            ?: SettingsScreenSessionState.collapsedSectionKeys
    LaunchedEffect(viewOptionsState?.settingsCollapsedSectionKeys, settingsExpandableSectionKeys) {
        val keys = viewOptionsState?.settingsCollapsedSectionKeys ?: return@LaunchedEffect
        collapsedSettingsSectionKeys =
            keys
                .mapNotNull { storedKey -> canonicalSettingsSectionRouteKey(storedKey) }
                .filter { sectionKey -> sectionKey in settingsExpandableSectionKeys }
                .toSet()
    }

    fun updateCollapsedSettingsSectionKeys(sectionKeys: Set<String>) {
        val filteredSectionKeys = sectionKeys.filter { sectionKey -> sectionKey in settingsExpandableSectionKeys }.toSet()
        collapsedSettingsSectionKeys = filteredSectionKeys
        scope.launch {
            viewOptionsPrefs.setSettingsCollapsedSectionKeys(filteredSectionKeys)
        }
    }
    val selectedSectionRouteKey = selectedSectionKey?.routeKey
    val visibleCollapsedSectionKeys =
        selectedSectionRouteKey?.let { sectionKey -> currentCollapsedSectionKeys - sectionKey }
            ?: currentCollapsedSectionKeys
    val includeSettingsSection: (SettingsSectionKey) -> Boolean =
        remember(selectedSectionKey) {
            { sectionKey -> selectedSectionKey == null || selectedSectionKey == sectionKey }
        }
    val allSettingsSectionsCollapsed =
        settingsExpandableSectionKeys.all { sectionKey ->
            sectionKey in currentCollapsedSectionKeys
        }
    val settingsListState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = SettingsScreenSessionState.listFirstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset = SettingsScreenSessionState.listFirstVisibleItemScrollOffset,
        )
    // The section a Help deep link last pointed at, and when its pulse ends. One at a time: a newer
    // link moves it. SettingsExpandableSection draws the outline, so every section can be a target.
    var sectionHighlightKey by rememberSaveable { mutableStateOf<String?>(null) }
    var sectionHighlightExpiresAtMillis by rememberSaveable { mutableLongStateOf(0L) }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) {
            notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
            if (pendingEnableUpdateNotificationsAfterPermission) {
                pendingEnableUpdateNotificationsAfterPermission = false
                if (notificationsGranted && updateState.updateCheckSchedule != UpdateCheckSchedule.NEVER) {
                    scope.launch { updatePrefs.setNotifyOnNewUpdates(true) }
                }
            }
        }
    val alarmManager = remember { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    val permissionLinked = remember { isPermissionLinked() }
    var canScheduleExactAlarms by remember {
        mutableStateOf(alarmManager.canScheduleExactAlarms())
    }
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    androidx.compose.runtime.DisposableEffect(settingsListState) {
        onDispose {
            SettingsScreenSessionState.collapsedSectionKeys = currentCollapsedSectionKeys
            SettingsScreenSessionState.listFirstVisibleItemIndex = settingsListState.firstVisibleItemIndex
            SettingsScreenSessionState.listFirstVisibleItemScrollOffset = settingsListState.firstVisibleItemScrollOffset
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, alarmManager, powerManager) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
                    canScheduleExactAlarms = alarmManager.canScheduleExactAlarms()
                    isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val settingsScrollEnabled =
        rememberContentOverflowScrollEnabled(
            listState = settingsListState,
            additionalScrollEnabled = true,
        )
    val blurStyle = rememberProgressiveBlurStyle(blurTop = false)
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pillInset = navBarInset + PillBottomBarHeight + PillBottomScrimExtra
    LaunchedEffect(showUpdateSheet) {
        if (showUpdateSheet && BuildConfig.CHECK_UPDATES) {
            updateVm.loadChangelog()
        }
    }

    val beginUpdateCheck: (Boolean) -> Unit = { redisplayAvailableAlert ->
        if (redisplayAvailableAlert) onUpdateCheckStarted()
        updateVm.openSheetAndCheck()
    }
    val downloadUpdate = { availableUpdate: RememberUpdateInfo ->
        updateVm.downloadOrInstall(availableUpdate, context as? ComponentActivity, playInAppUpdateLauncher)
    }
    LaunchedEffect(openSheetRequested) {
        if (!BuildConfig.CHECK_UPDATES) return@LaunchedEffect
        if (openSheetRequested) {
            updateVm.markOpenSheetHandled()
            updateVm.openSheetAndCheck()
        }
    }
    LaunchedEffect(playBannerState, showUpdateSheet) {
        if (!showUpdateSheet || !BuildConfig.USE_PLAY_IN_APP_UPDATES) return@LaunchedEffect
        when (playBannerState) {
            is PlayInAppUpdateBannerUiState.Downloading,
            PlayInAppUpdateBannerUiState.ReadyToInstall,
            -> {
                updateVm.closeSheetForPlayProgress()
            }
            PlayInAppUpdateBannerUiState.Hidden -> Unit
        }
    }
    val highlightSection = highlightSectionKey?.substringBefore(".")
    val highlightItem = highlightSectionKey?.substringAfter(".", "")?.takeIf { it.isNotEmpty() }
    // rememberSaveable, NOT remember: this request counter is acknowledged by a matching
    // rememberSaveable counter in the receiving section (see RemindersSection's
    // `rememberSettingsItemHighlightActive`). A monotonic-counter handshake only works if both
    // halves have the same lifetime. With a plain remember, the request side reset to 0 every time
    // Settings left the back stack while the ack side survived at 1 - so the second item-level deep
    // link raised request 1 again, the ack already read 1, and the highlight silently never fired.
    var activeHighlightItem by rememberSaveable { mutableStateOf<String?>(null) }
    var activeHighlightItemRequestId by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(highlightSectionKey) {
        val key = highlightSection ?: return@LaunchedEffect
        activeHighlightItem = null
        // Canonicalise so a deep link written against an older routeKey still expands its section.
        val sectionRouteKey = canonicalSettingsSectionRouteKey(key) ?: key
        val wasCollapsed = sectionRouteKey in currentCollapsedSectionKeys
        updateCollapsedSettingsSectionKeys(currentCollapsedSectionKeys - sectionRouteKey)
        // Wait for expandVertically to finish before scrolling or starting item-level highlights.
        if (wasCollapsed) delay(SETTINGS_SECTION_EXPAND_SETTLE_DELAY_MS)
        val index =
            settingsSectionScrollIndex(sectionRouteKey, includeSettingsSection) ?: run {
                onHighlightHandled()
                return@LaunchedEffect
            }
        settingsListState.animateScrollToItem(index)
        if (highlightItem == null) {
            sectionHighlightKey = sectionRouteKey
            sectionHighlightExpiresAtMillis = SystemClock.elapsedRealtime() + SETTINGS_SECTION_HIGHLIGHT_DURATION_MS
            onHighlightHandled()
        } else {
            activeHighlightItem = highlightItem
            activeHighlightItemRequestId += 1
            onHighlightHandled()
        }
    }
    LaunchedEffect(sectionHighlightKey, sectionHighlightExpiresAtMillis) {
        if (sectionHighlightKey == null) return@LaunchedEffect
        val remainingHighlightMillis = sectionHighlightExpiresAtMillis - SystemClock.elapsedRealtime()
        if (remainingHighlightMillis > 0) delay(remainingHighlightMillis)
        sectionHighlightKey = null
        sectionHighlightExpiresAtMillis = 0L
    }
    val activeSectionHighlightKey =
        sectionHighlightKey.takeIf { sectionHighlightExpiresAtMillis > SystemClock.elapsedRealtime() }

    if (showUpdateSheet && BuildConfig.CHECK_UPDATES) {
        val updateSheetState =
            rememberBottomSheetState(
                initialValue = SheetValue.Expanded,
                confirmValueChange = { true },
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            )
        val currentOrientation = LocalConfiguration.current.orientation
        LaunchedEffect(currentOrientation) {
            updateSheetState.expand()
        }
        // Deliberately a raw ModalBottomSheet rather than the shared AppBottomSheet wrapper:
        // 1. AppBottomSheet always wraps its body in its own verticalScroll. The changelog needs
        //    orientation-specific scrolling - a single outer scroll in landscape (so a downward
        //    drag still bubbles up to dismiss the sheet) vs. an inner-scrolled changelog box in
        //    portrait. A nested inner scroll under AppBottomSheet's outer scroll swallows the
        //    drag-to-dismiss delta in landscape, which is exactly the "won't drag down" bug.
        // 2. It needs a height cap (maxUpdateSheetHeight) and to re-expand on rotation below.
        // The shared AppBottomSheetDragHandle is still reused so the handle stays consistent.
        ModalBottomSheet(
            onDismissRequest = { updateVm.dismissSheet() },
            sheetState = updateSheetState,
            dragHandle = { AppBottomSheetDragHandle() },
        ) {
            UpdateCheckBottomSheetContent(
                maxSheetHeight = maxUpdateSheetHeight,
                isCheckingUpdate = isCheckingUpdate,
                updateInfo = updateInfo,
                updateCheckFinishedWithoutResult = updateCheckFinishedWithoutResult,
                updateCheckFailed = updateCheckFailed,
                downloadProgress = downloadProgress,
                changelogState = updateSheetChangelog,
                showGithubExtraUi = BuildConfig.FLAVOR == "github",
                usePlayInAppUpdates = BuildConfig.USE_PLAY_IN_APP_UPDATES,
                onDownloadClick = downloadUpdate,
                onSkipVersionClick = { updateInfo?.let { availableUpdate -> updateVm.skipVersion(availableUpdate) } },
            )
        }
    }

    Scaffold(
        containerColor = SettingsDesignTokens.ScreenBg,
        topBar = {
            if (showTopActions) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(SettingsDesignTokens.ScreenBg)
                            .statusBarsPadding()
                            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (showBackArrow) {
                            // Left button: Back Arrow button taking user back to Home
                            Surface(
                                shape = CircleShape,
                                color = SettingsDesignTokens.CardBg,
                                border = BorderStroke(1.dp, SettingsDesignTokens.DividerColor),
                                modifier =
                                    Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .clickable { onBack() },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    RememberMaterialRoundedSymbol(
                                        name = "arrow_back",
                                        size = 20.dp,
                                        tint = SettingsDesignTokens.TextPrimary,
                                        weight = FontWeight.Normal,
                                        filled = false,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = stringResource(R.string.main_tab_settings),
                                style =
                                    MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 26.sp,
                                        color = SettingsDesignTokens.TextPrimary,
                                    ),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Make Notas your own",
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        color = SettingsDesignTokens.TextSecondary,
                                    ),
                            )
                        }

                        // Right button: Information/Help icon taking user to Help screen
                        Surface(
                            shape = CircleShape,
                            color = SettingsDesignTokens.CardBg,
                            border = BorderStroke(1.dp, SettingsDesignTokens.DividerColor),
                            modifier =
                                Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .clickable { onOpenHelp() },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                RememberMaterialRoundedSymbol(
                                    name = "info",
                                    size = 20.dp,
                                    tint = SettingsDesignTokens.TextPrimary,
                                    weight = FontWeight.Normal,
                                    filled = false,
                                )
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.padding(bottom = 80.dp),
            )
        },
    ) { innerPadding ->
        val paneHosted = selectedSectionKey != null
        val blurMod =
            remember(blurStyle, paneHosted) {
                blurStyle
                    ?.applyToScrollableList(bottomAlphaMultiplier = if (paneHosted) 0f else 1f)
                    ?: Modifier
            }
        val topPadding = if (showTopActions) 12.dp else (statusBarInset + 8.dp)
        val bottomPadding = pillInset + 24.dp
        val listContentPadding =
            remember(topPadding, bottomPadding) {
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = topPadding,
                    bottom = bottomPadding,
                )
            }
        if (viewOptionsState == null) {
            Box(Modifier.fillMaxSize())
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
            ) {
                LazyColumn(
                    state = settingsListState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .then(blurMod),
                    contentPadding = listContentPadding,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    userScrollEnabled = settingsScrollEnabled,
                ) {
                    if (selectedSectionKey == null) {
                        item(key = "guest_profile_card") {
                            val palette = LocalSemanticPalette.current
                            SettingsRowCard {
                                SettingsRow(
                                    title = "Guest User",
                                    subtitle = "Sign in to sync & back up your notes",
                                    leadingIcon = {
                                        UserAvatar(
                                            initials = "G",
                                            size = 36.dp,
                                        )
                                    },
                                    trailingContent = {
                                        RememberMaterialRoundedSymbol(
                                            name = "chevron_right",
                                            size = 20.dp,
                                            tint = palette.secondaryText,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = onOpenGuestProfile,
                                    showDivider = false,
                                )
                            }
                        }
                    }

                    if (includeSettingsSection(SettingsSectionKey.History)) {
                        item(key = "history") {
                            SettingsExpandableSection(
                                sectionKey = SettingsSectionKey.History.routeKey,
                                materialSymbolName = SettingsSectionKey.History.iconName,
                                title = stringResource(SettingsSectionKey.History.titleRes),
                                collapsedSectionKeys = visibleCollapsedSectionKeys,
                                onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                                showHeader = false,
                                highlightedSectionKey = activeSectionHighlightKey,
                            ) {
                                SettingsSectionCard {
                                    SettingsRowItem(
                                        materialSymbolName = "history",
                                        title = stringResource(R.string.main_tab_history),
                                        onClick = { onOpenHistory() },
                                        trailingContent = {
                                            RememberMaterialRoundedSymbol(
                                                name = "chevron_right",
                                                size = 20.dp,
                                                tint = SettingsDesignTokens.TextSecondary,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (includeSettingsSection(SettingsSectionKey.Appearance)) {
                        item(key = "appearance") {
                            SettingsExpandableSection(
                                sectionKey = SettingsSectionKey.Appearance.routeKey,
                                materialSymbolName = SettingsSectionKey.Appearance.iconName,
                                title = stringResource(SettingsSectionKey.Appearance.titleRes),
                                collapsedSectionKeys = visibleCollapsedSectionKeys,
                                onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                                showHeader = showSectionHeaders,
                                highlightedSectionKey = activeSectionHighlightKey,
                            ) {
                                AppearanceSection(
                                    prefs = themePrefs,
                                    state = themeState,
                                    snackbarHostState = snackbarHostState,
                                )
                            }
                        }
                    }

                    if (includeSettingsSection(SettingsSectionKey.Defaults)) {
                        item(key = "defaults") {
                            SettingsExpandableSection(
                                sectionKey = SettingsSectionKey.Defaults.routeKey,
                                materialSymbolName = SettingsSectionKey.Defaults.iconName,
                                title = stringResource(SettingsSectionKey.Defaults.titleRes),
                                collapsedSectionKeys = visibleCollapsedSectionKeys,
                                onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                                showHeader = showSectionHeaders,
                                highlightedSectionKey = activeSectionHighlightKey,
                            ) {
                                DefaultsSection(
                                    defaultsState = defaultNoteState,
                                    defaultNotePrefs = defaultNotePrefs,
                                    scope = scope,
                                )
                            }
                        }
                    }

                    if (includeSettingsSection(SettingsSectionKey.Notifications)) {
                        item(key = "notifications") {
                            SettingsExpandableSection(
                                sectionKey = SettingsSectionKey.Notifications.routeKey,
                                materialSymbolName = SettingsSectionKey.Notifications.iconName,
                                title = stringResource(SettingsSectionKey.Notifications.titleRes),
                                collapsedSectionKeys = visibleCollapsedSectionKeys,
                                onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                                showHeader = showSectionHeaders,
                                highlightedSectionKey = activeSectionHighlightKey,
                            ) {
                                RemindersSection(
                                    reminderState = reminderState,
                                    reminderPrefs = reminderPrefs,
                                    quickCaptureState = quickCaptureState,
                                    quickCapturePrefs = quickCapturePrefs,
                                    notificationsGranted = notificationsGranted,
                                    notificationPermissionLauncher = notificationPermissionLauncher,
                                    permissionLinked = permissionLinked,
                                    canScheduleExactAlarms = canScheduleExactAlarms,
                                    isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                                    scope = scope,
                                    highlightItemKey = activeHighlightItem,
                                    highlightItemRequestId = activeHighlightItemRequestId,
                                )
                            }
                        }
                    }

                    if (includeSettingsSection(SettingsSectionKey.Haptics)) {
                        item(key = "haptics") {
                            SettingsExpandableSection(
                                sectionKey = SettingsSectionKey.Haptics.routeKey,
                                materialSymbolName = SettingsSectionKey.Haptics.iconName,
                                title = stringResource(SettingsSectionKey.Haptics.titleRes),
                                collapsedSectionKeys = visibleCollapsedSectionKeys,
                                onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                                showHeader = showSectionHeaders,
                                highlightedSectionKey = activeSectionHighlightKey,
                            ) {
                                SettingsSectionCard {
                                    SettingsToggleRow(
                                        materialSymbolName = "vibration",
                                        title = stringResource(R.string.settings_haptic_feedback),
                                        subtitle = stringResource(R.string.settings_haptic_feedback_desc),
                                        checked = interactionState.hapticFeedbackEnabled,
                                        onCheckedChange = { enabled ->
                                            scope.launch { interactionPrefs.setHapticFeedbackEnabled(enabled) }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (includeSettingsSection(SettingsSectionKey.Swipe)) {
                        item(key = "swipe") {
                            SettingsExpandableSection(
                                sectionKey = SettingsSectionKey.Swipe.routeKey,
                                materialSymbolName = SettingsSectionKey.Swipe.iconName,
                                title = stringResource(SettingsSectionKey.Swipe.titleRes),
                                collapsedSectionKeys = visibleCollapsedSectionKeys,
                                onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                                showHeader = showSectionHeaders,
                                highlightedSectionKey = activeSectionHighlightKey,
                            ) {
                                SettingsSectionCard {
                                    SwipeGestureSettingsPanel(
                                        currentMode = interactionState.swipeGestureMode,
                                        onModeChange = { mode ->
                                            scope.launch { interactionPrefs.setSwipeGestureMode(mode) }
                                        },
                                        startAction = interactionState.swipeStartToEnd,
                                        endAction = interactionState.swipeEndToStart,
                                        onStartActionChange = { action ->
                                            scope.launch { interactionPrefs.setSwipeStartToEnd(action) }
                                        },
                                        onEndActionChange = { action ->
                                            scope.launch { interactionPrefs.setSwipeEndToStart(action) }
                                        },
                                        startActions = interactionState.swipeStartToEndRevealActions,
                                        endActions = interactionState.swipeEndToStartRevealActions,
                                        onRevealActionsChange = { startActions, endActions ->
                                            scope.launch {
                                                interactionPrefs.setSwipeRevealActions(startActions, endActions)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (includeSettingsSection(SettingsSectionKey.Security)) {
                        item(key = "security") {
                            SettingsExpandableSection(
                                sectionKey = SettingsSectionKey.Security.routeKey,
                                materialSymbolName = SettingsSectionKey.Security.iconName,
                                title = stringResource(SettingsSectionKey.Security.titleRes),
                                collapsedSectionKeys = visibleCollapsedSectionKeys,
                                onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                                showHeader = showSectionHeaders,
                                highlightedSectionKey = activeSectionHighlightKey,
                            ) {
                                LockSection(
                                    lockState = lockState,
                                    lockPrefs = lockPrefs,
                                    biometricAvailable = biometricAvailable,
                                    deviceCredentialAvailable = deviceCredentialAvailable,
                                    snackbarHostState = snackbarHostState,
                                    scope = scope,
                                )
                            }
                        }
                    }

                    if (includeSettingsSection(SettingsSectionKey.Backup)) {
                        item(key = "backup") {
                            SettingsExpandableSection(
                                sectionKey = SettingsSectionKey.Backup.routeKey,
                                materialSymbolName = SettingsSectionKey.Backup.iconName,
                                title = stringResource(SettingsSectionKey.Backup.titleRes),
                                collapsedSectionKeys = visibleCollapsedSectionKeys,
                                onCollapsedSectionKeysChange = ::updateCollapsedSettingsSectionKeys,
                                showHeader = showSectionHeaders,
                                highlightedSectionKey = activeSectionHighlightKey,
                            ) {
                                BackupSection(
                                    cloudBackupManager = cloudBackupManager,
                                    authRepository = authRepository,
                                    snackbarHostState = snackbarHostState,
                                    scope = scope,
                                )
                            }
                        }
                    }

                    if (devModeEnabled && selectedSectionKey == null) {
                        item(key = "dev_options_entry") {
                            DevOptionsSettingsEntry(
                                onClick = onOpenDevOptions,
                            )
                        }
                    }

                    if (includeSettingsSection(SettingsSectionKey.About)) {
                        item(key = "about") {
                            AboutSection(
                                modifier = Modifier.padding(top = aboutSectionTopPadding(singleList, devModeEnabled)),
                                onOpenIntro = onOpenIntro,
                                devModeEnabled = devModeEnabled,
                                onDevModeActivated = {
                                    scope.launch { devModePrefs.setEnabled(true) }
                                    onOpenDevOptions()
                                },
                                onLaunchPlayReview = { onFlowFinished ->
                                    val hostActivity = context as? ComponentActivity
                                    if (hostActivity != null) {
                                        appReviewLauncher.tryLaunchInAppReview(hostActivity, onFlowFinished)
                                    } else {
                                        onFlowFinished()
                                    }
                                },
                                showHeader = showAboutHeader,
                                showHeaderTitle = showAboutHeaderTitle,
                            )
                        }
                    }
                }
            }
        }


    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DevOptionsSettingsEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sharedScope = LocalSharedTransitionScope.current
    val navScope = LocalNavAnimatedVisibilityScope.current
    val sharedBoundsSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<Rect>())
    val sharedBoundsTransform = BoundsTransform { _, _ -> sharedBoundsSpec }
    val sharedModifier =
        if (sharedScope != null && navScope != null) {
            with(sharedScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = DEV_OPTIONS_SHARED_BOUNDS_KEY),
                    animatedVisibilityScope = navScope,
                    boundsTransform = sharedBoundsTransform,
                )
            }
        } else {
            Modifier
        }
    Row(
        modifier =
            modifier
                .then(sharedModifier)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .appClickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(MaterialTheme.shapes.extraExtraLarge)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            RememberMaterialRoundedSymbol(
                name = "developer_board",
                size = 21.dp,
                tint = MaterialTheme.colorScheme.primary,
                weight = FontWeight.Medium,
            )
        }
        Text(
            text = stringResource(R.string.dev_options_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SettingsArrowOutwardBadge()
    }
}

/** Trailing badge for settings rows that leave the settings list (Developer options, ObtainX). */
@Composable
internal fun SettingsArrowOutwardBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(32.dp)
                .clip(MaterialTheme.shapes.extraExtraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        RememberMaterialRoundedSymbol(
            name = "arrow_outward",
            size = 20.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            weight = FontWeight.Medium,
        )
    }
}

private fun notificationsAppSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }


/**
 * Index of a section's item within the settings list, derived from declaration order and the
 * sections actually emitted.
 *
 * This was a hardcoded `routeKey -> index` map, which silently pointed at the wrong section whenever
 * the list changed: removing the Haptics section left every index below it off by one, and in
 * two-pane mode - where [includeSettingsSection] emits only the selected section - all of them were
 * wrong. Deriving it means adding, removing or hiding a section cannot leave it stale.
 *
 * About and DevOptions are emitted after a non-section item, so they are not scroll targets, which
 * matches the old map having no entry for them.
 */
private fun settingsSectionScrollIndex(
    storedRouteKey: String,
    isIncluded: (SettingsSectionKey) -> Boolean,
): Int? {
    val canonicalRouteKey = canonicalSettingsSectionRouteKey(storedRouteKey) ?: return null
    val target =
        SettingsSectionKey.entries.firstOrNull { section -> section.routeKey == canonicalRouteKey }
            ?: return null
    if (target == SettingsSectionKey.About || target == SettingsSectionKey.DevOptions) return null
    if (!isIncluded(target)) return null
    return SettingsSectionKey.entries
        .takeWhile { section -> section != target }
        .count { section -> isIncluded(section) }
}
