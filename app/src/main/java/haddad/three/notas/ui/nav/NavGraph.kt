package haddad.three.notas.ui.nav

import android.net.Uri
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import haddad.three.notas.BuildConfig
import haddad.three.notas.data.InteractionPrefs
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.OnboardingPrefs
import haddad.three.notas.di.LaunchAction
import haddad.three.notas.googletasks.GoogleTasksImportRoute
import haddad.three.notas.ui.auth.AuthViewModel
import haddad.three.notas.ui.auth.ForgotPasswordScreen
import haddad.three.notas.ui.auth.LoginScreen
import haddad.three.notas.ui.auth.RegisterScreen
import haddad.three.notas.ui.auth.SetNewPasswordScreen
import haddad.three.notas.ui.auth.VerifyEmailScreen
import haddad.three.notas.ui.auth.WelcomeScreen
import haddad.three.notas.ui.common.LocalSystemPaneScaffoldDirective
import haddad.three.notas.ui.common.LocalSystemWindowAdaptiveInfo
import haddad.three.notas.ui.common.rememberNotificationsAllowed
import haddad.three.notas.ui.common.rememberShareAppAction
import haddad.three.notas.ui.common.supportsMultiPaneLayout
import haddad.three.notas.ui.components.UpdateChromeState
import haddad.three.notas.ui.components.alertChromeSummary
import haddad.three.notas.ui.edit.EditListRoute
import haddad.three.notas.ui.edit.EditNoteRoute
import haddad.three.notas.ui.help.HelpScreen
import haddad.three.notas.ui.help.HelpViewModel
import haddad.three.notas.ui.help.LocalOpenHelp
import haddad.three.notas.ui.history.HistoryRoute
import haddad.three.notas.ui.history.HistorySection
import haddad.three.notas.ui.home.HomeRoute
import haddad.three.notas.ui.main.MainTab
import haddad.three.notas.ui.main.MainTabScaffold
import haddad.three.notas.ui.onboarding.OnboardingPermissionsScreen
import haddad.three.notas.ui.onboarding.OnboardingTitleScreen
import haddad.three.notas.ui.profile.GuestProfileScreen
import haddad.three.notas.ui.settings.DevOptionsRoute
import haddad.three.notas.ui.settings.RememberUpdateViewModel
import haddad.three.notas.ui.settings.SettingsRoute
import haddad.three.notas.ui.theme.LocalReducedMotion
import haddad.three.notas.ui.theme.reducedMotionAwareSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object Routes {
    const val WELCOME = "welcome"
    const val GUEST_PROFILE = "guestProfile"
    const val REGISTER = "register"
    const val LOGIN = "login"
    const val VERIFY_EMAIL = "verifyEmail"
    const val FORGOT_PASSWORD = "forgotPassword"
    const val SET_NEW_PASSWORD = "setNewPassword"
    const val ARG_EMAIL = "email"
    const val ARG_ACTION_CODE = "actionCode"

    const val ONBOARDING_TITLE = "onboardingTitle"
    const val ONBOARDING_PERMISSIONS = "onboardingPermissions"
    const val EXTERNAL_LAUNCH = "externalLaunch"

    /**
     * The three main tabs, each a **real registered destination** (see [mainTabComposable]).
     *
     * PARITY: matches FilePipe's `Screen.Rules` / `Screen.History` / `Screen.Settings`. Because these
     * are destinations, "go to a tab" is expressible as ordinary navigation - `popBackStack(route)`,
     * `navigate(route)` - instead of assigning tab state and then navigating to a shared host route.
     *
     * A previous revision hosted all three as `MainTab` state inside one `main` destination while
     * *also* keeping these constants around unregistered. That combination caused a real bug:
     * `popBackStack(Routes.SETTINGS, inclusive = false)` looked correct but could never match, so a
     * Help deep link switched the tab behind a Help screen that was never dismissed. Keep them
     * registered.
     */
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val SEARCH = "search"
    const val NOTES = "notes"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val EDIT_CONTENT = "editContent"
    const val EDIT_NOTE = "editNote"
    const val EDIT_LIST = "editList"
    const val GOOGLE_TASKS_IMPORT = "googleTasksImport"
    const val HELP = "help"
    const val DEV_OPTIONS = "devOptions"
    const val CLOUD_SYNC = "cloudSync"
    const val ARG_ID = "id"
    const val ARG_TYPE = "type"
    const val ARG_PREFILL = "prefill"
    const val ARG_FORCE_EDIT = "forceEdit"
    const val ARG_FOCUS_SECTION = "focusSection"
    const val ARG_INITIAL_DATE = "initialDate"
    const val ARG_DATE_MILLIS = "dateMillis"
    const val TYPE_NOTE = "note"
    const val TYPE_LIST = "checklist"
    const val EDIT_EVENT = "editEvent"
    const val CALENDAR_DETAILS = "calendarDetails"
    const val ARG_ITEM_ID = "itemId"
    const val ARG_IS_TASK = "isTask"

    /**
     * Help, optionally opened at the subsection a HELP_FOCUS_* id names. Same shape as FilePipe's
     * `Screen.Faq.createRoute(focusSection)`.
     */
    fun help(focusSection: String = ""): String = "$HELP?$ARG_FOCUS_SECTION=$focusSection"

    fun verifyEmail(
        email: String = "",
        actionCode: String = "",
    ): String {
        val params = mutableListOf<String>()
        if (email.isNotEmpty()) {
            val encoded =
                java.net.URLEncoder.encode(
                    email,
                    java.nio.charset.StandardCharsets.UTF_8
                        .toString(),
                )
            params.add("$ARG_EMAIL=$encoded")
        }
        if (actionCode.isNotEmpty()) {
            val encoded =
                java.net.URLEncoder.encode(
                    actionCode,
                    java.nio.charset.StandardCharsets.UTF_8
                        .toString(),
                )
            params.add("$ARG_ACTION_CODE=$encoded")
        }
        return if (params.isNotEmpty()) {
            "$VERIFY_EMAIL?${params.joinToString("&")}"
        } else {
            VERIFY_EMAIL
        }
    }

    fun setNewPassword(actionCode: String = ""): String =
        if (actionCode.isNotEmpty()) {
            val encoded =
                java.net.URLEncoder.encode(
                    actionCode,
                    java.nio.charset.StandardCharsets.UTF_8
                        .toString(),
                )
            "$SET_NEW_PASSWORD?$ARG_ACTION_CODE=$encoded"
        } else {
            SET_NEW_PASSWORD
        }

    fun editContent(
        id: Long?,
        type: NoteKind? = null,
        prefill: String = "",
        forceEdit: Boolean = false,
        initialDateMillis: Long? = null,
    ): String {
        val t =
            type?.let { kind ->
                "&$ARG_TYPE=" +
                    when (kind) {
                        NoteKind.NOTE -> TYPE_NOTE
                        NoteKind.LIST -> TYPE_LIST
                    }
            } ?: ""
        val p = if (prefill.isNotEmpty()) "&$ARG_PREFILL=${Uri.encode(prefill)}" else ""
        val f = if (forceEdit) "&$ARG_FORCE_EDIT=true" else ""
        val d = if (initialDateMillis != null && initialDateMillis > 0L) "&$ARG_INITIAL_DATE=$initialDateMillis" else ""
        return "$EDIT_CONTENT?${ARG_ID}=${id ?: -1L}$t$p$f$d"
    }

    fun editNote(
        id: Long?,
        prefill: String = "",
        forceEdit: Boolean = false,
        initialDateMillis: Long? = null,
    ): String =
        editContent(
            id = id,
            type = NoteKind.NOTE,
            prefill = prefill,
            forceEdit = forceEdit,
            initialDateMillis = initialDateMillis,
        )

    fun editList(
        id: Long?,
        forceEdit: Boolean = false,
    ): String =
        editContent(
            id = id,
            type = NoteKind.LIST,
            forceEdit = forceEdit,
        )

    fun editEvent(
        id: Long? = null,
        dateMillis: Long? = null,
    ): String = editNote(id = id, initialDateMillis = dateMillis)

    fun calendarDetails(
        id: Long,
        isTask: Boolean = false,
        dateMillis: Long? = null,
    ): String {
        val d = if (dateMillis != null && dateMillis > 0L) "&$ARG_DATE_MILLIS=$dateMillis" else ""
        return "$CALENDAR_DETAILS?$ARG_ITEM_ID=$id&$ARG_IS_TASK=$isTask$d"
    }

    fun noteKindFor(type: String?): NoteKind? =
        when (type) {
            TYPE_NOTE -> NoteKind.NOTE
            TYPE_LIST -> NoteKind.LIST
            else -> null
        }
}

/**
 * The destination that backs each main tab. Exhaustive `when` on purpose: a new [MainTab] will not
 * compile until it is given a route, so a tab can never silently lack a destination.
 */
private val MainTab.route: String
    get() =
        when (this) {
            MainTab.Home -> Routes.HOME
            MainTab.Calendar -> Routes.CALENDAR
            MainTab.Search -> Routes.SEARCH
            MainTab.Settings -> Routes.SETTINGS
        }

/** Tab order, keyed by route - drives the direction of the tab-switch slide. */
private val mainTabRouteOrdinals: Map<String, Int> =
    MainTab.entries.associate { tab -> tab.route to tab.ordinal }

/**
 * Destinations that cross-fade with a tab instead of sliding.
 *
 * The editors morph a note card into the editor with `sharedBounds`, so the tab has to cross-fade for
 * the morph to read; Dev options declares its own fade. For these the tab transition returns `null`,
 * deferring to the NavHost default so both halves of the transition agree.
 *
 * This short exception list is the whole reason Remember attaches tab transitions per destination
 * while FilePipe attaches them to the entire NavHost: FilePipe has no shared-element morph out of a
 * tab, so it can slide unconditionally.
 */
private fun crossFadesWithTab(route: String?): Boolean =
    route != null &&
        (
            route.startsWith(Routes.EDIT_CONTENT) ||
                route.startsWith(Routes.EDIT_NOTE) ||
                route.startsWith(Routes.EDIT_LIST) ||
                route.startsWith(Routes.EDIT_EVENT) ||
                route.startsWith(Routes.CALENDAR_DETAILS) ||
                route == Routes.DEV_OPTIONS
        )

/**
 * Registers one main-tab destination with its transitions.
 *
 * PARITY: tab-to-tab is FilePipe's `primaryTabEnterTransition` / `primaryTabExitTransition` - the
 * directional slide keyed on tab order. Anything else slides horizontally, matching FilePipe's
 * NavHost-level default, except for [crossFadesWithTab].
 */
private fun NavGraphBuilder.mainTabComposable(
    route: String,
    reducedMotion: Boolean,
    verticalMotion: Boolean,
    spatialSpec: FiniteAnimationSpec<IntOffset>,
    fadeInSpec: FiniteAnimationSpec<Float>,
    fadeOutSpec: FiniteAnimationSpec<Float>,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    enterTransition = { mainTabEnterTransition(reducedMotion, verticalMotion, spatialSpec, fadeInSpec, pop = false) },
    exitTransition = { mainTabExitTransition(reducedMotion, verticalMotion, spatialSpec, fadeOutSpec, pop = false) },
    popEnterTransition = { mainTabEnterTransition(reducedMotion, verticalMotion, spatialSpec, fadeInSpec, pop = true) },
    popExitTransition = { mainTabExitTransition(reducedMotion, verticalMotion, spatialSpec, fadeOutSpec, pop = true) },
    content = content,
)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.mainTabEnterTransition(
    reducedMotion: Boolean,
    verticalMotion: Boolean,
    spatialSpec: FiniteAnimationSpec<IntOffset>,
    fadeInSpec: FiniteAnimationSpec<Float>,
    pop: Boolean,
): EnterTransition? {
    val initialRoute = initialState.destination.route
    val initialOrdinal = mainTabRouteOrdinals[initialRoute]
    val targetOrdinal = mainTabRouteOrdinals[targetState.destination.route]

    // Tab to tab: slide in from the edge we are travelling from, per tab order.
    if (initialOrdinal != null && targetOrdinal != null) {
        if (reducedMotion) return EnterTransition.None
        // The same tab on both sides means the back stack was rebuilt under us, not that the user
        // moved between tabs. There is no relative position to convey, so either slide direction
        // would be a lie about where the screen came from.
        if (initialOrdinal == targetOrdinal) return fadeIn(animationSpec = fadeInSpec)
        val offset: (Int) -> Int = if (targetOrdinal > initialOrdinal) { size -> size } else { size -> -size }
        return if (verticalMotion) {
            slideInVertically(animationSpec = spatialSpec, initialOffsetY = offset)
        } else {
            slideInHorizontally(animationSpec = spatialSpec, initialOffsetX = offset)
        } + fadeIn(animationSpec = fadeInSpec)
    }

    if (crossFadesWithTab(initialRoute)) return null
    if (reducedMotion) return EnterTransition.None
    // Entering a tab from any other screen (Help, Google Tasks import, onboarding): FilePipe's
    // NavHost-level default. A pop comes back in from the left, a forward navigation from the right.
    return slideInHorizontally(animationSpec = spatialSpec) { size -> if (pop) -size else size } +
        fadeIn(animationSpec = fadeInSpec)
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.mainTabExitTransition(
    reducedMotion: Boolean,
    verticalMotion: Boolean,
    spatialSpec: FiniteAnimationSpec<IntOffset>,
    fadeOutSpec: FiniteAnimationSpec<Float>,
    pop: Boolean,
): ExitTransition? {
    val targetRoute = targetState.destination.route
    val initialOrdinal = mainTabRouteOrdinals[initialState.destination.route]
    val targetOrdinal = mainTabRouteOrdinals[targetRoute]

    if (initialOrdinal != null && targetOrdinal != null) {
        if (reducedMotion) return ExitTransition.None
        if (initialOrdinal == targetOrdinal) return fadeOut(animationSpec = fadeOutSpec)
        val offset: (Int) -> Int =
            if (targetOrdinal > initialOrdinal) { size -> -size / 3 } else { size -> size }
        return if (verticalMotion) {
            slideOutVertically(animationSpec = spatialSpec, targetOffsetY = offset)
        } else {
            slideOutHorizontally(animationSpec = spatialSpec, targetOffsetX = offset)
        } + fadeOut(animationSpec = fadeOutSpec)
    }

    if (crossFadesWithTab(targetRoute)) return null
    if (reducedMotion) return ExitTransition.None
    return slideOutHorizontally(animationSpec = spatialSpec) { size -> if (pop) size else -size / 3 } +
        fadeOut(animationSpec = fadeOutSpec)
}

@Composable
fun RememberNavGraph(
    repository: NoteRepository,
    onboardingPrefs: OnboardingPrefs,
    interactionPrefs: InteractionPrefs,
    appScope: CoroutineScope,
    launchFlow: MutableStateFlow<LaunchAction?>? = null,
    openSettingsRequest: Int = 0,
    updateVm: RememberUpdateViewModel,
    onUpdateCheckStarted: () -> Unit = {},
    updateBarState: UpdateChromeState = UpdateChromeState.Hidden,
    updateSignalEpoch: Int = 0,
    onUpdateClick: () -> Unit = {},
    onDismissUpdateAvailable: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val helpVm: HelpViewModel = hiltViewModel()
    var settingsHighlightSection by remember { mutableStateOf<String?>(null) }
    var openSettingsUpdatesRequest by remember { mutableIntStateOf(0) }
    val onboardingState by onboardingPrefs.state.collectAsStateWithLifecycle(initialValue = null)
    val currentOnboardingState = onboardingState
    val onboardingScope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    val navSpatialSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>())
    val navFadeInSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.defaultEffectsSpec<Float>())
    val navFadeOutSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    val devOptionsFadeSpec = reducedMotionAwareSpec(MaterialTheme.motionScheme.fastEffectsSpec<Float>())
    var historySection by rememberSaveable { mutableStateOf(HistorySection.ARCHIVE) }
    var historyVisibleItemCount by rememberSaveable { mutableIntStateOf(0) }
    // PARITY with FilePipe: the selected tab is derived from the back stack, not held as state.
    // There is exactly one source of truth, so the chrome can never show a tab the NavHost isn't
    // actually displaying (which is what let a Help deep link switch tabs behind a live Help screen).
    val currentMainTab =
        MainTab.entries.firstOrNull { tab ->
            currentDestination?.hierarchy?.any { it.route == tab.route } == true
        }
    val onMainTab = currentMainTab != null
    var alertBarsExpanded by rememberSaveable { mutableStateOf(false) }
    var lastPresentedAlertKey by rememberSaveable { mutableStateOf<String?>(null) }
    var createNoteInPane by remember { mutableStateOf<(() -> Unit)?>(null) }
    var createListInPane by remember { mutableStateOf<(() -> Unit)?>(null) }
    val shareApp = rememberShareAppAction()
    val windowAdaptiveInfo = LocalSystemWindowAdaptiveInfo.current ?: currentWindowAdaptiveInfoV2()
    val paneScaffoldDirective =
        LocalSystemPaneScaffoldDirective.current ?: calculatePaneScaffoldDirective(windowAdaptiveInfo)

    // Lint prefers LocalWindowInfo.current.containerSize, but converting that to dp needs
    // LocalDensity, which the theme replaces for visual scaling - the exact distortion this gate
    // exists to defend against. Configuration reports the OS width directly.
    @Suppress("ConfigurationScreenWidthHeight")
    val systemWindowWidthDp = LocalConfiguration.current.screenWidthDp
    val useDualPaneMode =
        supportsMultiPaneLayout(
            maxHorizontalPartitions = paneScaffoldDirective.maxHorizontalPartitions,
            screenWidthDp = systemWindowWidthDp,
        )

    if (currentOnboardingState == null) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val notificationsAllowed = rememberNotificationsAllowed()
    val blockedReminderCountFlow =
        remember(repository, notificationsAllowed) {
            if (notificationsAllowed) {
                flowOf(0)
            } else {
                repository
                    .observeActive()
                    .map { activeNotes ->
                        val alertWindowEnd = System.currentTimeMillis() + REMINDER_NOTIFICATION_ALERT_WINDOW_MS
                        activeNotes.count { noteWithItems ->
                            val reminderAt = noteWithItems.note.reminderAt
                            reminderAt != null &&
                                reminderAt <= alertWindowEnd &&
                                noteWithItems.note.completedAt == null
                        }
                    }.distinctUntilChanged()
            }
        }
    val blockedReminderCount =
        blockedReminderCountFlow.collectAsStateWithLifecycle(initialValue = 0).value
    val alertSummary =
        remember(updateBarState, blockedReminderCount) {
            alertChromeSummary(updateBarState, blockedReminderCount)
        }
    val alertPresentationKey =
        remember(updateBarState, alertSummary.count, blockedReminderCount > 0) {
            if (alertSummary.count == 0) {
                null
            } else {
                "${updateStatePresentationKey(updateBarState)}:notifications-disabled-${blockedReminderCount > 0}"
            }
        }
    // Also keyed on lastPresentedAlertKey: re-running the update check clears it below,
    // which must re-present the alert even when the alert state itself never changed
    // (e.g. an update that is still "available" after a manual re-check).
    LaunchedEffect(alertPresentationKey, lastPresentedAlertKey) {
        val currentAlertKey = alertPresentationKey
        if (currentAlertKey == null) {
            alertBarsExpanded = false
            lastPresentedAlertKey = null
        } else if (currentAlertKey != lastPresentedAlertKey) {
            alertBarsExpanded = true
            lastPresentedAlertKey = currentAlertKey
        }
    }
    val handleUpdateCheckStarted = {
        // Forget the presented alert so the chrome pops again for the re-checked result.
        lastPresentedAlertKey = null
        onUpdateCheckStarted()
    }
    // Any (re-)delivery of an update — manual check, background worker, dev mock — bumps
    // the epoch and re-presents the alert. The handled epoch is saveable so rotation
    // does not count as a new delivery.
    var lastHandledUpdateSignalEpoch by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(updateSignalEpoch) {
        if (updateSignalEpoch != lastHandledUpdateSignalEpoch) {
            lastHandledUpdateSignalEpoch = updateSignalEpoch
            lastPresentedAlertKey = null
        }
    }

    // Latched on the first composition that has a loaded onboarding state - the null branch above
    // returns before this point, so there is no default-value frame to guard against.
    //
    // It must be the launch-time value, never the live one: NavHost rebuilds its graph whenever
    // startDestination changes, and NavController.setGraph reacts to a graph with a different start
    // destination by clearing the whole back stack and re-navigating to the new start. Finishing
    // onboarding flips hasSeenIntro, so keying on it tore Notes down and re-entered it mid-flight,
    // which is why the home screen slid in from the left instead of from the right.
    //
    // PARITY: FilePipe latches the same value as `introSeenAtLaunch` in AppNavigation.kt.
    val introSeenAtLaunch = remember { currentOnboardingState.hasSeenIntro }
    val initialExternalLaunch =
        remember(launchFlow, introSeenAtLaunch) {
            introSeenAtLaunch &&
                launchFlow?.value?.let { action ->
                    action is LaunchAction.OpenNote && action.externalLaunch
                } == true
        }
    val lockedStartDestination =
        remember(introSeenAtLaunch, initialExternalLaunch) {
            when {
                !introSeenAtLaunch -> Routes.ONBOARDING_TITLE
                initialExternalLaunch -> Routes.EXTERNAL_LAUNCH
                else -> Routes.HOME
            }
        }

    /**
     * Select a main tab. Identical to FilePipe's bottom-nav / navigation-rail click handler.
     *
     * Pops to [Routes.HOME] rather than to `graph.findStartDestination()`. Notes is always the root
     * of the back stack once tabs are reachable - both the onboarding hand-off and an external note
     * launch re-root onto it - but the graph's *nominal* start destination is whatever the app
     * launched into, which after either of those paths is no longer on the back stack at all. A
     * popUpTo that matches nothing silently pops nothing, so tab taps would stack up instead of
     * returning to the root.
     */
    val openMainTab: (MainTab) -> Unit = { selectedTab ->
        navController.navigate(selectedTab.route) {
            popUpTo(Routes.HOME) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    /** Bring the Settings tab to the front for an external request. Mirrors FilePipe's `openSettingsRoot`. */
    val openSettingsRoot: () -> Unit = {
        if (navController.currentDestination?.route != Routes.SETTINGS) {
            val poppedToSettings = navController.popBackStack(Routes.SETTINGS, inclusive = false)
            if (!poppedToSettings) {
                navController.navigate(Routes.SETTINGS) {
                    popUpTo(Routes.HOME) {
                        saveState = true
                    }
                    launchSingleTop = true
                }
            }
        }
    }

    // Collected only while this activity is RESUMED. The launch action is an app-scoped flow, so an
    // outgoing activity instance that is on its way out (an intent carrying FLAG_ACTIVITY_CLEAR_TASK
    // finishes the running instance, and it is destroyed only after the replacement has resumed) would
    // otherwise consume the action with its own doomed NavController, leaving the fresh instance with
    // nothing pending and parked on the start destination.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(launchFlow, currentOnboardingState.hasSeenIntro, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            launchFlow?.collectLatest { action ->
                if (action == null) return@collectLatest
                var handled = false
                try {
                    when (action) {
                        is LaunchAction.AuthEmailAction -> {
                            when (action.mode) {
                                "resetPassword" -> navController.navigate(Routes.setNewPassword(action.oobCode))
                                "verifyEmail" -> navController.navigate(Routes.verifyEmail(actionCode = action.oobCode))
                            }
                        }
                        is LaunchAction.NewNote -> {
                            if (currentOnboardingState.hasSeenIntro) {
                                navController.navigate(Routes.editNote(null, action.prefill))
                            }
                        }
                        LaunchAction.NewList -> {
                            if (currentOnboardingState.hasSeenIntro) {
                                navController.navigate(Routes.editList(null))
                            }
                        }
                        is LaunchAction.OpenNote -> {
                            if (currentOnboardingState.hasSeenIntro) {
                                val note = repository.get(action.id)
                                if (note == null) {
                                    if (action.externalLaunch) {
                                        navController.navigate(Routes.HOME) {
                                            popUpTo(navController.graph.id) {
                                                inclusive = true
                                            }
                                            launchSingleTop = true
                                        }
                                    }
                                    handled = true
                                    return@collectLatest
                                }
                                navController.openEditRouteFor(note, externalLaunch = action.externalLaunch)
                            }
                        }
                        LaunchAction.OpenSettingsUpdates -> {
                            if (currentOnboardingState.hasSeenIntro) {
                                openSettingsRoot()
                                openSettingsUpdatesRequest += 1
                                updateVm.requestOpenSheet()
                            }
                        }
                    }
                    handled = true
                } finally {
                    // Left pending when this collector is cancelled part-way through (the activity
                    // stopped being resumed while the note was still being read), so the next resume
                    // retries it. A genuine failure still clears it, so a bad action cannot loop.
                    if (handled || currentCoroutineContext().isActive) launchFlow.value = null
                }
            }
        }
    }

    val settingsRequest = openSettingsRequest + openSettingsUpdatesRequest
    LaunchedEffect(settingsRequest) {
        if (settingsRequest > 0) {
            openSettingsRoot()
        }
    }
    // No LaunchedEffect on settingsHighlightSection: the Help deep link navigates itself via
    // goToSettingsFromHelp. Reacting to the highlight separately is what previously switched the tab
    // behind a Help screen that was never dismissed. FilePipe has no such effect either.
    androidx.compose.animation.SharedTransitionLayout {
        // Passed as a State, not an Int: NavHost keys its remembered graph on the builder lambda, so
        // capturing a value that changes on every tab tap would rebuild the whole graph mid-navigation.
        // A State has stable identity, and the .value read happens inside the tab content instead.
        // Navigation 2.10.0 added a built-in scale/shrink default (scaleOut(0.7f)) for the
        // predictive-back gesture preview when NavHost's predictivePop*Transition params aren't
        // set, replacing the previous behavior where popEnterTransition/popExitTransition
        // (mainTabEnterTransition/mainTabExitTransition below) doubled as that preview. Reuse the
        // exact same tab-slide logic here instead, so predictive back looks the same as it did
        // pre-2.10.0. Parity: FilePipe hit and fixed the same regression in AppNavigation.kt
        // (primaryTabEnterTransition/primaryTabExitTransition), confirming plain
        // navSpatialSpec/navFadeInSpec/navFadeOutSpec (spring-based, from MaterialTheme.motionScheme)
        // scrub fine with the gesture there - an earlier attempt here to blame the spring for a
        // missing-slide bug was wrong; the bug was simply a fadeIn()/fadeOut()-only override with
        // no slideIn/slideOut at all.
        val navHostContent: @Composable (State<Int>) -> Unit = { closeNotesRevealRequest ->
            NavHost(
                navController = navController,
                startDestination = lockedStartDestination,
                modifier = Modifier.fillMaxSize(),
                predictivePopEnterTransition = {
                    mainTabEnterTransition(reducedMotion, useDualPaneMode, navSpatialSpec, navFadeInSpec, pop = true)
                        ?: (if (reducedMotion) EnterTransition.None else fadeIn(animationSpec = navFadeInSpec))
                },
                predictivePopExitTransition = {
                    mainTabExitTransition(reducedMotion, useDualPaneMode, navSpatialSpec, navFadeOutSpec, pop = true)
                        ?: (if (reducedMotion) ExitTransition.None else fadeOut(animationSpec = navFadeOutSpec))
                },
            ) {
                composable(
                    route = Routes.WELCOME,
                ) {
                    OnboardingTitleScreen(
                        onLetsBegin = {
                            if (onboardingState?.hasSeenIntro != true) {
                                navController.navigate(Routes.ONBOARDING_PERMISSIONS) {
                                    popUpTo(Routes.WELCOME) { inclusive = true }
                                }
                            } else {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                }
                            }
                        },
                    )
                }

                composable(
                    route = Routes.ONBOARDING_TITLE,
                ) {
                    OnboardingTitleScreen(
                        onLetsBegin = {
                            if (onboardingState?.hasSeenIntro != true) {
                                navController.navigate(Routes.ONBOARDING_PERMISSIONS) {
                                    popUpTo(Routes.ONBOARDING_TITLE) { inclusive = true }
                                }
                            } else {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                }
                            }
                        },
                    )
                }

                composable(route = Routes.GUEST_PROFILE) {
                    GuestProfileScreen(
                        onBackClick = { navController.popBackStack() },
                        onCreateAccountClick = { navController.navigate(Routes.REGISTER) },
                        onLoginClick = { navController.navigate(Routes.LOGIN) },
                        onVerifyEmailClick = { email -> navController.navigate(Routes.verifyEmail(email)) },
                    )
                }

                composable(route = Routes.REGISTER) {
                    val context = LocalContext.current
                    val authVm: AuthViewModel = hiltViewModel()
                    RegisterScreen(
                        viewModel = authVm,
                        onBackClick = {
                            authVm.clearErrors()
                            navController.popBackStack()
                        },
                        onLoginClick = {
                            authVm.clearErrors()
                            navController.navigate(Routes.LOGIN)
                        },
                        onGoogleSignInClick = {
                            authVm.signInWithGoogle(context) {
                                if (onboardingState?.hasSeenIntro != true) {
                                    navController.navigate(Routes.ONBOARDING_PERMISSIONS) {
                                        popUpTo(Routes.REGISTER) { inclusive = true }
                                    }
                                } else {
                                    navController.navigate(Routes.HOME) {
                                        popUpTo(navController.graph.id) { inclusive = true }
                                    }
                                }
                            }
                        },
                        onRegisteredSuccess = { email ->
                            navController.navigate(Routes.verifyEmail(email))
                        },
                    )
                }

                composable(
                    route = "${Routes.VERIFY_EMAIL}?${Routes.ARG_EMAIL}={${Routes.ARG_EMAIL}}&${Routes.ARG_ACTION_CODE}={${Routes.ARG_ACTION_CODE}}",
                    arguments =
                        listOf(
                            navArgument(Routes.ARG_EMAIL) {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument(Routes.ARG_ACTION_CODE) {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                        ),
                ) { entry ->
                    val email = entry.arguments?.getString(Routes.ARG_EMAIL).orEmpty()
                    val actionCode =
                        entry.arguments
                            ?.getString(Routes.ARG_ACTION_CODE)
                            .orEmpty()
                            .ifBlank { null }
                    val authVm: AuthViewModel = hiltViewModel()
                    VerifyEmailScreen(
                        email = email,
                        actionCode = actionCode,
                        viewModel = authVm,
                        onBackClick = { navController.popBackStack() },
                        onChangeEmailClick = { navController.popBackStack() },
                        onVerifiedSuccess = {
                            if (onboardingState?.hasSeenIntro != true) {
                                navController.navigate(Routes.ONBOARDING_PERMISSIONS) {
                                    popUpTo(Routes.VERIFY_EMAIL) { inclusive = true }
                                }
                            } else {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                }
                            }
                        },
                    )
                }

                composable(route = Routes.LOGIN) {
                    val context = LocalContext.current
                    val authVm: AuthViewModel = hiltViewModel()
                    LoginScreen(
                        viewModel = authVm,
                        onBackClick = {
                            authVm.clearErrors()
                            navController.popBackStack()
                        },
                        onRegisterClick = {
                            authVm.clearErrors()
                            navController.navigate(Routes.REGISTER)
                        },
                        onForgotPasswordClick = {
                            authVm.clearErrors()
                            navController.navigate(Routes.FORGOT_PASSWORD)
                        },
                        onGoogleSignInClick = {
                            authVm.signInWithGoogle(context) {
                                if (onboardingState?.hasSeenIntro != true) {
                                    navController.navigate(Routes.ONBOARDING_PERMISSIONS) {
                                        popUpTo(Routes.LOGIN) { inclusive = true }
                                    }
                                } else {
                                    navController.navigate(Routes.HOME) {
                                        popUpTo(navController.graph.id) { inclusive = true }
                                    }
                                }
                            }
                        },
                        onLoginSuccess = {
                            val isVerified = authVm.currentUser?.isEmailVerified ?: true
                            val email = authVm.currentUser?.email.orEmpty()
                            if (!isVerified) {
                                navController.navigate(Routes.verifyEmail(email))
                            } else if (onboardingState?.hasSeenIntro != true) {
                                navController.navigate(Routes.ONBOARDING_PERMISSIONS) {
                                    popUpTo(Routes.LOGIN) { inclusive = true }
                                }
                            } else {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                }
                            }
                        },
                    )
                }

                composable(route = Routes.FORGOT_PASSWORD) {
                    val authVm: AuthViewModel = hiltViewModel()
                    ForgotPasswordScreen(
                        viewModel = authVm,
                        onBackClick = { navController.popBackStack() },
                        onBackToLoginClick = { navController.popBackStack() },
                    )
                }

                composable(
                    route = "${Routes.SET_NEW_PASSWORD}?${Routes.ARG_ACTION_CODE}={${Routes.ARG_ACTION_CODE}}",
                    arguments =
                        listOf(
                            navArgument(Routes.ARG_ACTION_CODE) {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                        ),
                ) { entry ->
                    val actionCode =
                        entry.arguments
                            ?.getString(Routes.ARG_ACTION_CODE)
                            .orEmpty()
                            .ifBlank { null }
                    val authVm: AuthViewModel = hiltViewModel()
                    SetNewPasswordScreen(
                        actionCode = actionCode,
                        viewModel = authVm,
                        onBackClick = { navController.popBackStack() },
                        onBackToLoginClick = {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        },
                        onRequestNewLinkClick = {
                            navController.navigate(Routes.FORGOT_PASSWORD)
                        },
                    )
                }

                composable(
                    route = Routes.ONBOARDING_PERMISSIONS,
                    enterTransition = {
                        if (initialState.destination.route == Routes.ONBOARDING_TITLE) {
                            if (reducedMotion) {
                                EnterTransition.None
                            } else {
                                slideInHorizontally(animationSpec = navSpatialSpec) { it } +
                                    fadeIn(animationSpec = navFadeInSpec)
                            }
                        } else {
                            null
                        }
                    },
                    exitTransition = {
                        if (targetState.destination.route == Routes.ONBOARDING_TITLE ||
                            targetState.destination.route == Routes.HOME
                        ) {
                            if (reducedMotion) {
                                ExitTransition.None
                            } else {
                                slideOutHorizontally(animationSpec = navSpatialSpec) { -it / 3 } +
                                    fadeOut(animationSpec = navFadeOutSpec)
                            }
                        } else {
                            null
                        }
                    },
                    popEnterTransition = {
                        if (initialState.destination.route == Routes.ONBOARDING_TITLE) {
                            if (reducedMotion) {
                                EnterTransition.None
                            } else {
                                slideInHorizontally(animationSpec = navSpatialSpec) { it } +
                                    fadeIn(animationSpec = navFadeInSpec)
                            }
                        } else {
                            null
                        }
                    },
                    popExitTransition = {
                        if (targetState.destination.route == Routes.ONBOARDING_TITLE) {
                            if (reducedMotion) {
                                ExitTransition.None
                            } else {
                                slideOutHorizontally(animationSpec = navSpatialSpec) { it } +
                                    fadeOut(animationSpec = navFadeOutSpec)
                            }
                        } else {
                            null
                        }
                    },
                ) {
                    OnboardingPermissionsScreen(
                        onContinue = {
                            onboardingScope.launch {
                                onboardingPrefs.markIntroSeen()
                                navController.navigate(Routes.HOME) {
                                    popUpTo(navController.graph.id) {
                                        inclusive = true
                                    }
                                }
                            }
                        },
                    )
                }

                composable(Routes.EXTERNAL_LAUNCH) {
                    Box(modifier = Modifier.fillMaxSize())
                }

                mainTabComposable(
                    route = Routes.HOME,
                    reducedMotion = reducedMotion,
                    verticalMotion = useDualPaneMode,
                    spatialSpec = navSpatialSpec,
                    fadeInSpec = navFadeInSpec,
                    fadeOutSpec = navFadeOutSpec,
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalNavAnimatedVisibilityScope provides this,
                    ) {
                        if (useDualPaneMode) {
                            NotesTwoPaneRoute(
                                paneScaffoldDirective = paneScaffoldDirective,
                                interactionPrefs = interactionPrefs,
                                appScope = appScope,
                                closeRevealRequest = closeNotesRevealRequest.value,
                                onOpenIntro = { navController.navigate(Routes.ONBOARDING_TITLE) },
                                onImportGoogleTasks = { navController.navigate(Routes.GOOGLE_TASKS_IMPORT) },
                                onOpenNoteInSinglePane = { note, forceEdit ->
                                    navController.openEditRouteFor(note, forceEdit)
                                },
                                onCreateNoteInSinglePane = { navController.navigate(Routes.editNote(null)) },
                                onCreateListInSinglePane = { navController.navigate(Routes.editList(null)) },
                                onRegisterCreateNoteInPane = { callback -> createNoteInPane = callback },
                                onRegisterCreateListInPane = { callback -> createListInPane = callback },
                            )
                        } else {
                            HomeRoute(
                                interactionPrefs = interactionPrefs,
                                closeRevealRequest = closeNotesRevealRequest.value,
                                onOpenNote = { note, forceEdit -> navController.openEditRouteFor(note, forceEdit) },
                                onCreateNote = { navController.navigate(Routes.editNote(null)) },
                                onCreateList = { navController.navigate(Routes.editList(null)) },
                            )
                        }
                    }
                }

                composable(
                    route = Routes.HISTORY,
                    enterTransition = { slideInHorizontally { it } + fadeIn() },
                    exitTransition = { slideOutHorizontally { -it / 3 } + fadeOut() },
                    popEnterTransition = { slideInHorizontally { -it / 3 } + fadeIn() },
                    popExitTransition = { slideOutHorizontally { it } + fadeOut() },
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalNavAnimatedVisibilityScope provides this,
                    ) {
                        if (useDualPaneMode) {
                            HistoryTwoPaneRoute(
                                paneScaffoldDirective = paneScaffoldDirective,
                                interactionPrefs = interactionPrefs,
                                appScope = appScope,
                                onOpenIntro = { navController.navigate(Routes.ONBOARDING_TITLE) },
                                section = historySection,
                                onSectionChange = { selectedSection -> historySection = selectedSection },
                                onVisibleItemCountChange = { visibleItemCount ->
                                    historyVisibleItemCount = visibleItemCount
                                },
                                onOpenNoteInSinglePane = { note, forceEdit ->
                                    navController.openEditRouteFor(note, forceEdit)
                                },
                                onBack = { navController.popBackStack() },
                            )
                        } else {
                            HistoryRoute(
                                interactionPrefs = interactionPrefs,
                                section = historySection,
                                onSectionChange = { selectedSection -> historySection = selectedSection },
                                onVisibleItemCountChange = { visibleItemCount ->
                                    historyVisibleItemCount = visibleItemCount
                                },
                                onOpenNote = { note, forceEdit -> navController.openEditRouteFor(note, forceEdit) },
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }

                mainTabComposable(
                    route = Routes.CALENDAR,
                    reducedMotion = reducedMotion,
                    verticalMotion = useDualPaneMode,
                    spatialSpec = navSpatialSpec,
                    fadeInSpec = navFadeInSpec,
                    fadeOutSpec = navFadeOutSpec,
                ) {
                    haddad.three.notas.ui.calendar.CalendarRoute(
                        onOpenItemDetails = { itemId, isTask ->
                            navController.navigate(Routes.calendarDetails(id = itemId, isTask = isTask)) {
                                launchSingleTop = true
                            }
                        },
                        onCreateEvent = { selectedDate ->
                            val initialMillis =
                                selectedDate
                                    .atStartOfDay(java.time.ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli()
                            navController.navigate(Routes.editNote(id = null, initialDateMillis = initialMillis)) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToSearch = {
                            openMainTab(MainTab.Search)
                        },
                    )
                }

                mainTabComposable(
                    route = Routes.SEARCH,
                    reducedMotion = reducedMotion,
                    verticalMotion = useDualPaneMode,
                    spatialSpec = navSpatialSpec,
                    fadeInSpec = navFadeInSpec,
                    fadeOutSpec = navFadeOutSpec,
                ) {
                    haddad.three.notas.ui.search.SearchRoute(
                        onOpenNote = { item ->
                            navController.openEditRouteFor(item)
                        },
                    )
                }

                mainTabComposable(
                    route = Routes.SETTINGS,
                    reducedMotion = reducedMotion,
                    verticalMotion = useDualPaneMode,
                    spatialSpec = navSpatialSpec,
                    fadeInSpec = navFadeInSpec,
                    fadeOutSpec = navFadeOutSpec,
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalNavAnimatedVisibilityScope provides this,
                    ) {
                        if (useDualPaneMode) {
                            SettingsTwoPaneRoute(
                                paneScaffoldDirective = paneScaffoldDirective,
                                onOpenIntro = { navController.navigate(Routes.ONBOARDING_TITLE) },
                                onOpenHelp = { navController.navigate(Routes.help()) },
                                onOpenDevOptions = { navController.navigate(Routes.DEV_OPTIONS) },
                                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                                onOpenGuestProfile = { navController.navigate(Routes.GUEST_PROFILE) },
                                onOpenNoteInSinglePane = { note, forceEdit -> navController.openEditRouteFor(note, forceEdit) },
                                updateVm = updateVm,
                                onUpdateCheckStarted = handleUpdateCheckStarted,
                                onShareApp = shareApp,
                                highlightSectionKey = settingsHighlightSection,
                                onHighlightHandled = { settingsHighlightSection = null },
                            )
                        } else {
                            SettingsRoute(
                                onOpenIntro = { navController.navigate(Routes.ONBOARDING_TITLE) },
                                onOpenHelp = { navController.navigate(Routes.help()) },
                                onOpenDevOptions = { navController.navigate(Routes.DEV_OPTIONS) },
                                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                                onOpenGuestProfile = { navController.navigate(Routes.GUEST_PROFILE) },
                                updateVm = updateVm,
                                onUpdateCheckStarted = handleUpdateCheckStarted,
                                highlightSectionKey = settingsHighlightSection,
                                onHighlightHandled = { settingsHighlightSection = null },
                            )
                        }
                    }
                }

                composable(
                    route = "${Routes.EDIT_CONTENT}?${Routes.ARG_ID}={${Routes.ARG_ID}}&${Routes.ARG_TYPE}={${Routes.ARG_TYPE}}&${Routes.ARG_PREFILL}={${Routes.ARG_PREFILL}}&${Routes.ARG_FORCE_EDIT}={${Routes.ARG_FORCE_EDIT}}&${Routes.ARG_INITIAL_DATE}={${Routes.ARG_INITIAL_DATE}}",
                    arguments =
                        listOf(
                            navArgument(Routes.ARG_ID) {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                            navArgument(Routes.ARG_TYPE) {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument(Routes.ARG_PREFILL) {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument(Routes.ARG_FORCE_EDIT) {
                                type = NavType.BoolType
                                defaultValue = false
                            },
                            navArgument(Routes.ARG_INITIAL_DATE) {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                        ),
                ) { entry ->
                    val id = entry.arguments?.getLong(Routes.ARG_ID) ?: -1L
                    val requestedType = entry.arguments?.getString(Routes.ARG_TYPE).orEmpty()
                    val forceEdit = entry.arguments?.getBoolean(Routes.ARG_FORCE_EDIT) ?: false
                    var resolvedKind by remember(id, requestedType) {
                        mutableStateOf(
                            Routes.noteKindFor(requestedType)
                                ?: if (id <= 0) NoteKind.NOTE else null,
                        )
                    }
                    LaunchedEffect(id, requestedType) {
                        if (resolvedKind == null && id > 0) {
                            resolvedKind = repository.get(id)?.note?.kind ?: NoteKind.NOTE
                        }
                    }
                    // Back and up are the same plain pop, including for an editor opened from outside
                    // the app: openEditRouteFor plants the Notes tab underneath such an editor, so
                    // there is always something to pop to.
                    val leaveEditor = {
                        navController.popBackStack()
                        Unit
                    }
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalNavAnimatedVisibilityScope provides this@composable,
                    ) {
                        when (resolvedKind) {
                            NoteKind.LIST ->
                                EditListRoute(
                                    appScope = appScope,
                                    noteId = id.takeIf { it > 0 },
                                    forceEdit = forceEdit,
                                    onBack = leaveEditor,
                                    onNavigateUp = leaveEditor,
                                )
                            NoteKind.NOTE ->
                                EditNoteRoute(
                                    appScope = appScope,
                                    noteId = id.takeIf { it > 0 },
                                    forceEdit = forceEdit,
                                    onBack = leaveEditor,
                                    onNavigateUp = leaveEditor,
                                )
                            null -> Box(modifier = Modifier.fillMaxSize())
                        }
                    }
                }

                composable(
                    route = "${Routes.EDIT_NOTE}?${Routes.ARG_ID}={${Routes.ARG_ID}}&${Routes.ARG_PREFILL}={${Routes.ARG_PREFILL}}&${Routes.ARG_FORCE_EDIT}={${Routes.ARG_FORCE_EDIT}}&${Routes.ARG_INITIAL_DATE}={${Routes.ARG_INITIAL_DATE}}",
                    arguments =
                        listOf(
                            navArgument(Routes.ARG_ID) {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                            navArgument(Routes.ARG_PREFILL) {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument(Routes.ARG_FORCE_EDIT) {
                                type = NavType.BoolType
                                defaultValue = false
                            },
                            navArgument(Routes.ARG_INITIAL_DATE) {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                        ),
                ) { entry ->
                    val id = entry.arguments?.getLong(Routes.ARG_ID) ?: -1L
                    val forceEdit = entry.arguments?.getBoolean(Routes.ARG_FORCE_EDIT) ?: false
                    // Back and up are the same plain pop, including for an editor opened from outside
                    // the app: openEditRouteFor plants the Notes tab underneath such an editor, so
                    // there is always something to pop to.
                    val leaveEditor = {
                        navController.popBackStack()
                        Unit
                    }
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalNavAnimatedVisibilityScope provides this@composable,
                    ) {
                        EditNoteRoute(
                            appScope = appScope,
                            noteId = id.takeIf { it > 0 },
                            forceEdit = forceEdit,
                            onBack = leaveEditor,
                            onNavigateUp = leaveEditor,
                        )
                    }
                }

                composable(Routes.GOOGLE_TASKS_IMPORT) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalNavAnimatedVisibilityScope provides this@composable,
                    ) {
                        GoogleTasksImportRoute(onBack = { navController.popBackStack() })
                    }
                }

                composable(
                    route = "${Routes.HELP}?${Routes.ARG_FOCUS_SECTION}={${Routes.ARG_FOCUS_SECTION}}",
                    arguments =
                        listOf(
                            navArgument(Routes.ARG_FOCUS_SECTION) {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                        ),
                    enterTransition = {
                        if (reducedMotion) {
                            EnterTransition.None
                        } else {
                            slideInHorizontally(animationSpec = navSpatialSpec) { it } +
                                fadeIn(animationSpec = navFadeInSpec)
                        }
                    },
                    exitTransition = {
                        if (reducedMotion) {
                            ExitTransition.None
                        } else {
                            slideOutHorizontally(animationSpec = navSpatialSpec) { it } +
                                fadeOut(animationSpec = navFadeOutSpec)
                        }
                    },
                    popEnterTransition = {
                        if (reducedMotion) {
                            EnterTransition.None
                        } else {
                            slideInHorizontally(animationSpec = navSpatialSpec) { it } +
                                fadeIn(animationSpec = navFadeInSpec)
                        }
                    },
                    popExitTransition = {
                        if (reducedMotion) {
                            ExitTransition.None
                        } else {
                            slideOutHorizontally(animationSpec = navSpatialSpec) { it } +
                                fadeOut(animationSpec = navFadeOutSpec)
                        }
                    },
                ) { helpEntry ->
                    /**
                     * Open the Settings section a help deep link points at, dismissing the help
                     * screen on the way.
                     *
                     * Identical to FilePipe's `goToSettingsFromHelp`, now that Settings is a real
                     * destination in both apps. The fallback branch pops explicitly because the
                     * observable result must be that the target section is selected AND the help
                     * screen is gone - a bare `navigate` would leave help on the back stack under
                     * the Settings tab.
                     */
                    val goToSettingsFromHelp: () -> Unit = {
                        val poppedToExistingSettings =
                            navController.popBackStack(Routes.SETTINGS, inclusive = false)
                        if (!poppedToExistingSettings) {
                            navController.popBackStack()
                            navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                        }
                    }
                    HelpScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAppSection = { sectionKey ->
                            settingsHighlightSection = sectionKey
                            goToSettingsFromHelp()
                        },
                        helpVm = helpVm,
                        initialFocusSectionId = helpEntry.arguments?.getString(Routes.ARG_FOCUS_SECTION).orEmpty(),
                    )
                }

                composable(
                    route = Routes.DEV_OPTIONS,
                    enterTransition = {
                        if (reducedMotion) {
                            EnterTransition.None
                        } else {
                            fadeIn(animationSpec = devOptionsFadeSpec)
                        }
                    },
                    exitTransition = {
                        if (reducedMotion) {
                            ExitTransition.None
                        } else {
                            fadeOut(animationSpec = devOptionsFadeSpec)
                        }
                    },
                    popEnterTransition = {
                        if (reducedMotion) {
                            EnterTransition.None
                        } else {
                            fadeIn(animationSpec = devOptionsFadeSpec)
                        }
                    },
                    popExitTransition = {
                        if (reducedMotion) {
                            ExitTransition.None
                        } else {
                            fadeOut(animationSpec = devOptionsFadeSpec)
                        }
                    },
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalNavAnimatedVisibilityScope provides this@composable,
                    ) {
                        DevOptionsRoute(onBack = { navController.popBackStack() })
                    }
                }

                composable(
                    route = Routes.CLOUD_SYNC,
                ) {
                    haddad.three.notas.ui.settings.sync.CloudSyncScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    route = "${Routes.EDIT_LIST}?${Routes.ARG_ID}={${Routes.ARG_ID}}&${Routes.ARG_FORCE_EDIT}={${Routes.ARG_FORCE_EDIT}}",
                    arguments =
                        listOf(
                            navArgument(Routes.ARG_ID) {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                            navArgument(Routes.ARG_FORCE_EDIT) {
                                type = NavType.BoolType
                                defaultValue = false
                            },
                        ),
                ) { entry ->
                    val id = entry.arguments?.getLong(Routes.ARG_ID) ?: -1L
                    val forceEdit = entry.arguments?.getBoolean(Routes.ARG_FORCE_EDIT) ?: false
                    // Back and up are the same plain pop, including for an editor opened from outside
                    // the app: openEditRouteFor plants the Notes tab underneath such an editor, so
                    // there is always something to pop to.
                    val leaveEditor = {
                        navController.popBackStack()
                        Unit
                    }
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalSharedTransitionScope provides this@SharedTransitionLayout,
                        LocalNavAnimatedVisibilityScope provides this@composable,
                    ) {
                        EditListRoute(
                            appScope = appScope,
                            noteId = id.takeIf { it > 0 },
                            forceEdit = forceEdit,
                            onBack = leaveEditor,
                            onNavigateUp = leaveEditor,
                        )
                    }
                }

                composable(
                    route = "${Routes.CALENDAR_DETAILS}?${Routes.ARG_ITEM_ID}={${Routes.ARG_ITEM_ID}}&${Routes.ARG_IS_TASK}={${Routes.ARG_IS_TASK}}&${Routes.ARG_DATE_MILLIS}={${Routes.ARG_DATE_MILLIS}}",
                    arguments =
                        listOf(
                            navArgument(Routes.ARG_ITEM_ID) {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                            navArgument(Routes.ARG_IS_TASK) {
                                type = NavType.BoolType
                                defaultValue = false
                            },
                            navArgument(Routes.ARG_DATE_MILLIS) {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                        ),
                    enterTransition = {
                        if (reducedMotion) {
                            EnterTransition.None
                        } else {
                            slideInHorizontally(animationSpec = navSpatialSpec) { it } +
                                fadeIn(animationSpec = navFadeInSpec)
                        }
                    },
                    exitTransition = {
                        if (reducedMotion) {
                            ExitTransition.None
                        } else {
                            slideOutHorizontally(animationSpec = navSpatialSpec) { it } +
                                fadeOut(animationSpec = navFadeOutSpec)
                        }
                    },
                    popEnterTransition = {
                        if (reducedMotion) {
                            EnterTransition.None
                        } else {
                            slideInHorizontally(animationSpec = navSpatialSpec) { -it } +
                                fadeIn(animationSpec = navFadeInSpec)
                        }
                    },
                    popExitTransition = {
                        if (reducedMotion) {
                            ExitTransition.None
                        } else {
                            slideOutHorizontally(animationSpec = navSpatialSpec) { -it } +
                                fadeOut(animationSpec = navFadeOutSpec)
                        }
                    },
                ) {
                    haddad.three.notas.ui.calendar.details.CalendarDetailsRoute(
                        onBack = { navController.popBackStack() },
                        onEditItem = { noteWithItems ->
                            if (noteWithItems.note.kind == NoteKind.LIST) {
                                navController.navigate(Routes.editList(noteWithItems.note.id, forceEdit = true)) {
                                    launchSingleTop = true
                                }
                            } else {
                                navController.navigate(Routes.editNote(noteWithItems.note.id, forceEdit = true)) {
                                    launchSingleTop = true
                                }
                            }
                        },
                    )
                }
            }
        }

        // PARITY with FilePipe: the tab chrome (nav pill / rail, alert FAB, per-tab FAB, snackbar host)
        // lives OUTSIDE the NavHost and is gated on whether the current destination is a tab. It
        // therefore survives tab-to-tab navigation instead of being torn down and rebuilt with each
        // tab's destination, and it cannot display a tab the NavHost isn't showing.
        androidx.compose.runtime.CompositionLocalProvider(
            LocalSharedTransitionScope provides this@SharedTransitionLayout,
        ) {
            MainTabScaffold(
                repository = repository,
                currentTab = currentMainTab ?: MainTab.Home,
                chromeVisible = onMainTab,
                useDualPaneMode = useDualPaneMode,
                onTabSelected = openMainTab,
                onCreateNote = {
                    if (useDualPaneMode && createNoteInPane != null) {
                        createNoteInPane?.invoke()
                    } else {
                        navController.navigate(Routes.editNote(null))
                    }
                },
                onCreateList = {
                    if (useDualPaneMode && createListInPane != null) {
                        createListInPane?.invoke()
                    } else {
                        navController.navigate(Routes.editList(null))
                    }
                },
                onImportGoogleTasks = { navController.navigate(Routes.GOOGLE_TASKS_IMPORT) },
                historySection = historySection,
                historyVisibleItemCount = historyVisibleItemCount,
                updateBarState = updateBarState,
                onUpdateClick = onUpdateClick,
                onDismissUpdateAvailable = onDismissUpdateAvailable,
                onInstallUpdate = onInstallUpdate,
                alertSummary = alertSummary,
                blockedReminderCount = blockedReminderCount,
                alertBarsExpanded = alertBarsExpanded,
                onAlertBarsExpandedChange = { expanded -> alertBarsExpanded = expanded },
            ) { closeRevealRequest ->
                // Lets the editor's Behavior sheet open Help at a subsection; see LocalOpenHelp.
                // Pushed on top, so Back returns to the editor with its state intact.
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalOpenHelp provides { focusSection -> navController.navigate(Routes.help(focusSection)) },
                ) {
                    navHostContent(rememberUpdatedState(closeRevealRequest))
                }
            }
        }
    }
}

/**
 * [externalLaunch] means the editor is being opened from outside the app (a reminder notification or
 * a widget) rather than from a list the user was already looking at. Such a launch replaces whatever
 * was on the back stack with the Notes tab and then opens the editor on top of it, so the editor sits
 * exactly where an in-app open would put it: leaving it is an ordinary pop that reveals the Notes tab
 * beneath, instead of a forward navigation that flashes the tab chrome over the note on its way out.
 */
private fun NavController.openEditRouteFor(
    note: NoteWithItems,
    forceEdit: Boolean = false,
    externalLaunch: Boolean = false,
) {
    if (externalLaunch) {
        navigate(Routes.HOME) {
            popUpTo(graph.id) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }
    val route =
        when (note.note.kind) {
            NoteKind.NOTE -> Routes.editNote(note.note.id, forceEdit = forceEdit)
            NoteKind.LIST -> Routes.editList(note.note.id, forceEdit = forceEdit)
        }
    navigate(route) {
        launchSingleTop = true
    }
}

private const val REMINDER_NOTIFICATION_ALERT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

private fun updateStatePresentationKey(updateState: UpdateChromeState): String =
    when (updateState) {
        UpdateChromeState.Hidden -> "hidden"
        UpdateChromeState.Available -> "available"
        is UpdateChromeState.Downloading -> "downloading"
        UpdateChromeState.ReadyToInstall -> "ready"
    }
