package three.haddad.smartnotas

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import three.haddad.smartnotas.data.InteractionPrefs
import three.haddad.smartnotas.data.InteractionState
import three.haddad.smartnotas.data.LockPrefs
import three.haddad.smartnotas.data.NoteRepository
import three.haddad.smartnotas.data.NotesUiPrefs
import three.haddad.smartnotas.data.NotesUiState
import three.haddad.smartnotas.data.OnboardingPrefs
import three.haddad.smartnotas.data.OnboardingState
import three.haddad.smartnotas.data.ShoulderRepository
import three.haddad.smartnotas.data.ThemePrefs
import three.haddad.smartnotas.data.ThemeState
import three.haddad.smartnotas.data.UpdatePreferencesState
import three.haddad.smartnotas.data.UpdatePrefs
import three.haddad.smartnotas.data.effectiveDarkTheme
import three.haddad.smartnotas.di.ApplicationScope
import three.haddad.smartnotas.di.LaunchAction
import three.haddad.smartnotas.ui.InAppRatingAutoPromptHost
import three.haddad.smartnotas.ui.common.LocalSystemPaneScaffoldDirective
import three.haddad.smartnotas.ui.common.LocalSystemWindowAdaptiveInfo
import three.haddad.smartnotas.ui.components.UpdateChromeState
import three.haddad.smartnotas.ui.lock.AppLockSession
import three.haddad.smartnotas.ui.lock.LockScreen
import three.haddad.smartnotas.ui.nav.RememberNavGraph
import three.haddad.smartnotas.ui.settings.RememberUpdateViewModel
import three.haddad.smartnotas.ui.splash.SplashScreen
import three.haddad.smartnotas.ui.theme.RememberTheme
import three.haddad.smartnotas.update.AppReviewLauncher
import three.haddad.smartnotas.update.PlayInAppUpdateBannerUiState
import three.haddad.smartnotas.update.PlayInAppUpdateProgressController
import three.haddad.smartnotas.update.RememberUpdateState
import three.haddad.smartnotas.update.notificationDedupeKey
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var noteRepository: NoteRepository

    @Inject lateinit var shoulderRepository: ShoulderRepository

    @Inject lateinit var themePrefs: ThemePrefs

    @Inject lateinit var notesUiPrefs: NotesUiPrefs

    @Inject lateinit var interactionPrefs: InteractionPrefs

    @Inject lateinit var onboardingPrefs: OnboardingPrefs

    @Inject lateinit var lockPrefs: LockPrefs

    @Inject lateinit var updatePrefs: UpdatePrefs

    @Inject lateinit var rememberUpdateState: RememberUpdateState

    @Inject lateinit var appReviewLauncher: AppReviewLauncher

    @Inject lateinit var playInAppUpdateProgressController: PlayInAppUpdateProgressController

    @Inject lateinit var pendingLaunch: MutableStateFlow<LaunchAction?>

    @Inject lateinit var appLockSession: AppLockSession

    @ApplicationScope @Inject
    lateinit var applicationScope: CoroutineScope

    companion object {
        const val ACTION_SHORTCUT_NEW_NOTE = "three.haddad.smartnotas.action.SHORTCUT_NEW_NOTE"
        const val ACTION_SHORTCUT_NEW_LIST = "three.haddad.smartnotas.action.SHORTCUT_NEW_LIST"
        const val EXTRA_OPEN_NOTE_ID = "open_note_id"

        /**
         * Marks an open-note request as coming from outside the app. The key string predates the name
         * and is left alone on purpose: notifications and widget taps created by an older build are
         * still in flight with it, and renaming the key would make them look like in-app navigation.
         */
        const val EXTRA_OPEN_NOTE_EXTERNAL_LAUNCH = "open_note_exit_on_back"
        const val EXTRA_OPEN_SETTINGS_UPDATES = "extra_open_settings_updates"
    }

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val isInitialLaunch = savedInstanceState == null
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        handleIntent(intent)
        applicationScope.launch { notesUiPrefs.migrateFromThemePrefsIfNeeded() }
        applicationScope.launch { themePrefs.migrateLegacyBlackThemeIfNeeded() }
        applicationScope.launch { openSettingsUpdatesIfAppWasUpdated() }
        setContent {
            val systemWindowAdaptiveInfo = currentWindowAdaptiveInfoV2()
            val systemPaneScaffoldDirective = calculatePaneScaffoldDirective(systemWindowAdaptiveInfo)
            val themeState by themePrefs.state.collectAsStateWithLifecycle(
                initialValue = ThemeState(),
            )
            val notesUiState by notesUiPrefs.state.collectAsStateWithLifecycle(
                initialValue = NotesUiState(),
            )
            val systemDark = isSystemInDarkTheme()
            val darkTheme = themeState.themeMode.effectiveDarkTheme(systemDark)
            var splashActive by rememberSaveable { mutableStateOf(isInitialLaunch) }
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle =
                        SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
                            if (splashActive) false else darkTheme
                        },
                    navigationBarStyle =
                        SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
                            if (splashActive) false else darkTheme
                        },
                )
            }
            val interactionState by interactionPrefs.state.collectAsStateWithLifecycle(
                initialValue = InteractionState(),
            )
            CompositionLocalProvider(
                LocalSystemWindowAdaptiveInfo provides systemWindowAdaptiveInfo,
                LocalSystemPaneScaffoldDirective provides systemPaneScaffoldDirective,
            ) {
                RememberTheme(
                    themeState = themeState,
                    notesUiState = notesUiState,
                    hapticFeedbackEnabled = interactionState.hapticFeedbackEnabled,
                ) {
                    AppRoot(
                        noteRepository = noteRepository,
                        onboardingPrefs = onboardingPrefs,
                        interactionPrefs = interactionPrefs,
                        lockPrefs = lockPrefs,
                        updatePrefs = updatePrefs,
                        rememberUpdateState = rememberUpdateState,
                        appReviewLauncher = appReviewLauncher,
                        playInAppUpdateProgressController = playInAppUpdateProgressController,
                        appScope = applicationScope,
                        launchFlow = pendingLaunch,
                        appLockSession = appLockSession,
                        splashActive = splashActive,
                        onDismissSplash = { splashActive = false },
                    )
                }
            }
        }
    }

    override fun onStart() {
        appLockSession.onAppForegrounded()
        super.onStart()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS_UPDATES, false)) {
            pendingLaunch.value = LaunchAction.OpenSettingsUpdates
            intent.removeExtra(EXTRA_OPEN_SETTINGS_UPDATES)
            return
        }
        val dataUri = intent.data
        if (dataUri != null) {
            val mode = dataUri.getQueryParameter("mode")
            val oobCode = dataUri.getQueryParameter("oobCode")
            if (!mode.isNullOrBlank() && !oobCode.isNullOrBlank()) {
                pendingLaunch.value = LaunchAction.AuthEmailAction(mode = mode, oobCode = oobCode)
                return
            }
        }
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                pendingLaunch.value = LaunchAction.NewNote(prefill = text)
            }
            ACTION_SHORTCUT_NEW_NOTE -> {
                pendingLaunch.value = LaunchAction.NewNote()
            }
            ACTION_SHORTCUT_NEW_LIST -> {
                pendingLaunch.value = LaunchAction.NewList
            }
            // QS tile long-press. The manifest filter makes the system open Remember
            // instead of App Info. Bring the current screen forward and do not start a note.
            TileService.ACTION_QS_TILE_PREFERENCES -> Unit
            Intent.ACTION_VIEW -> {
                val shortcut = intent.getStringExtra("action")
                val openId = intent.getLongExtra(EXTRA_OPEN_NOTE_ID, -1L)
                val externalLaunch = intent.getBooleanExtra(EXTRA_OPEN_NOTE_EXTERNAL_LAUNCH, false)
                when {
                    shortcut == "new_note" -> pendingLaunch.value = LaunchAction.NewNote()
                    shortcut == "new_list" -> pendingLaunch.value = LaunchAction.NewList
                    openId > 0L -> pendingLaunch.value = LaunchAction.OpenNote(openId, externalLaunch)
                }
            }
            else -> {
                val openId = intent.getLongExtra(EXTRA_OPEN_NOTE_ID, -1L)
                val externalLaunch = intent.getBooleanExtra(EXTRA_OPEN_NOTE_EXTERNAL_LAUNCH, false)
                if (openId > 0L) pendingLaunch.value = LaunchAction.OpenNote(openId, externalLaunch)
            }
        }
    }

    /** First launch after an update (fresh installs are not announced): auto-opens the update sheet with the changelog. */
    private suspend fun openSettingsUpdatesIfAppWasUpdated() {
        val lastSeenVersion = updatePrefs.getLastSeenAppVersion()
        val currentVersion = BuildConfig.VERSION_NAME
        val wasUpdated = !lastSeenVersion.isNullOrBlank() && lastSeenVersion != currentVersion
        if (wasUpdated && BuildConfig.CHECK_UPDATES) {
            pendingLaunch.value = LaunchAction.OpenSettingsUpdates
        }
        updatePrefs.setLastSeenAppVersion(currentVersion)
    }
}

@Composable
private fun AppRoot(
    noteRepository: NoteRepository,
    onboardingPrefs: OnboardingPrefs,
    interactionPrefs: InteractionPrefs,
    lockPrefs: LockPrefs,
    updatePrefs: UpdatePrefs,
    rememberUpdateState: RememberUpdateState,
    appReviewLauncher: AppReviewLauncher,
    playInAppUpdateProgressController: PlayInAppUpdateProgressController,
    appScope: CoroutineScope,
    launchFlow: MutableStateFlow<LaunchAction?>,
    appLockSession: AppLockSession,
    splashActive: Boolean,
    onDismissSplash: () -> Unit,
) {
    val lockState by lockPrefs.state.collectAsStateWithLifecycle(
        initialValue = null,
    )
    val onboardingState by onboardingPrefs.state.collectAsStateWithLifecycle(
        initialValue = OnboardingState(),
    )
    val updatePreferencesState by updatePrefs.state.collectAsStateWithLifecycle(
        initialValue = UpdatePreferencesState(),
    )
    val unlocked by appLockSession.unlocked.collectAsStateWithLifecycle()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val updateInfo by rememberUpdateState.updateInfo.collectAsStateWithLifecycle(initialValue = null)
    val realPlayBannerState by playInAppUpdateProgressController.bannerUiState.collectAsStateWithLifecycle()
    val devReleaseMockPlayBannerState by rememberUpdateState.devReleasePlayBannerMockUiState.collectAsStateWithLifecycle()
    val playBannerState =
        if (devReleaseMockPlayBannerState != PlayInAppUpdateBannerUiState.Hidden) {
            devReleaseMockPlayBannerState
        } else {
            realPlayBannerState
        }
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val updateVm: RememberUpdateViewModel = hiltViewModel()
    var openSettingsRequest by rememberSaveable { mutableIntStateOf(0) }
    var dismissedUpdateBarKey by rememberSaveable { mutableStateOf<String?>(null) }
    // Every update-flow (re-)trigger resurrects a dismissed update bar. The handled
    // epoch is saveable so rotation does not count as a new trigger.
    val updateSignalEpoch by rememberUpdateState.updateSignalEpoch.collectAsStateWithLifecycle()
    var lastHandledUpdateSignalEpoch by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(updateSignalEpoch) {
        if (updateSignalEpoch != lastHandledUpdateSignalEpoch) {
            lastHandledUpdateSignalEpoch = updateSignalEpoch
            dismissedUpdateBarKey = null
        }
    }
    val currentLockState = lockState

    LaunchedEffect(currentLockState, splashActive) {
        if (currentLockState != null && splashActive) {
            kotlinx.coroutines.delay(500)
            onDismissSplash()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (currentLockState != null) {
            val deviceUnlockAvailable =
                remember(context) {
                    BiometricManager
                        .from(context)
                        .canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
                        BiometricManager.BIOMETRIC_SUCCESS
                }
            val deviceUnlockRequired = currentLockState.enabled && !currentLockState.hasPin
            LaunchedEffect(deviceUnlockRequired, deviceUnlockAvailable) {
                if (deviceUnlockRequired && !deviceUnlockAvailable) {
                    lockPrefs.disable()
                }
            }
            val showLockScreen =
                currentLockState.enabled &&
                    !unlocked &&
                    (currentLockState.hasPin || deviceUnlockAvailable)

            if (showLockScreen) {
                LockScreen(
                    biometricEnabled = currentLockState.biometric,
                    hasPin = currentLockState.hasPin,
                    pinLength = currentLockState.pinLength,
                    onUnlocked = appLockSession::unlock,
                    verify = { pin -> lockPrefs.verify(pin) },
                )
            } else {
                activity?.let { fragmentActivity ->
                    InAppRatingAutoPromptHost(
                        onboardingState = onboardingState,
                        updateState = updatePreferencesState,
                        activity = fragmentActivity,
                        updatePrefs = updatePrefs,
                        appReviewLauncher = appReviewLauncher,
                    )
                }
                CompositionLocalProvider(
                    three.haddad.smartnotas.ui.theme.LocalSnackbarHostState provides snackbarHostState,
                ) {
                    val currentUpdateInfo = updateInfo
                    val updateKey = currentUpdateInfo?.notificationDedupeKey()
                    val updateAvailable = BuildConfig.CHECK_UPDATES && currentUpdateInfo != null
                    val updateBarState =
                        when (val currentPlayState = playBannerState) {
                            is PlayInAppUpdateBannerUiState.Downloading ->
                                UpdateChromeState.Downloading(
                                    bytesDownloaded = currentPlayState.bytesDownloaded,
                                    totalBytesToDownload = currentPlayState.totalBytesToDownload,
                                    indeterminateProgress = currentPlayState.indeterminateProgress,
                                )
                            PlayInAppUpdateBannerUiState.ReadyToInstall -> UpdateChromeState.ReadyToInstall
                            PlayInAppUpdateBannerUiState.Hidden ->
                                if (updateAvailable && updateKey != dismissedUpdateBarKey) {
                                    UpdateChromeState.Available
                                } else {
                                    UpdateChromeState.Hidden
                                }
                        }
                    RememberNavGraph(
                        repository = noteRepository,
                        onboardingPrefs = onboardingPrefs,
                        interactionPrefs = interactionPrefs,
                        appScope = appScope,
                        launchFlow = launchFlow,
                        openSettingsRequest = openSettingsRequest,
                        updateVm = updateVm,
                        onUpdateCheckStarted = { dismissedUpdateBarKey = null },
                        updateBarState = updateBarState,
                        updateSignalEpoch = updateSignalEpoch,
                        onUpdateClick = {
                            if (updateBarState == UpdateChromeState.Available) {
                                dismissedUpdateBarKey = updateKey
                            }
                            // Check only surfaces the update sheet; starting the actual
                            // download is the sheet's job.
                            openSettingsRequest += 1
                            updateVm.requestOpenSheet()
                        },
                        onDismissUpdateAvailable = { dismissedUpdateBarKey = updateKey },
                        onInstallUpdate = {
                            if (!rememberUpdateState.devReleaseCompletePlayUpdateIfReady()) {
                                val activity = context as? Activity
                                if (activity != null) {
                                    playInAppUpdateProgressController.completeFlexibleUpdateIfReady(activity)
                                }
                            }
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = splashActive,
            enter = EnterTransition.None,
            exit = fadeOut(animationSpec = tween(durationMillis = 350)),
            modifier = Modifier.fillMaxSize(),
        ) {
            SplashScreen()
        }
    }
}
