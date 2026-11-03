package haddad.three.notas.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteRepository
import haddad.three.notas.data.NotesUiPrefs
import haddad.three.notas.data.NotesUiState
import haddad.three.notas.data.ThemeMode
import haddad.three.notas.data.ThemePrefs
import haddad.three.notas.data.ThemeState
import haddad.three.notas.notifications.appNotificationSettingsIntent
import haddad.three.notas.notifications.canPostNotifications
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.components.AccountHeader
import haddad.three.notas.ui.components.PrimaryButton
import haddad.three.notas.ui.components.SecondaryButton
import haddad.three.notas.ui.components.SettingsRow
import haddad.three.notas.ui.components.SettingsRowCard
import haddad.three.notas.ui.theme.LocalSemanticPalette
import haddad.three.notas.ui.theme.NotasTokens
import haddad.three.notas.ui.theme.NotasTypography
import javax.inject.Inject

import haddad.three.notas.data.auth.AuthRepository
import haddad.three.notas.data.auth.AuthState
import haddad.three.notas.data.auth.AppAuthState
import haddad.three.notas.data.auth.AuthUser

data class GuestProfileStats(
    val notesCount: Int = 0,
    val tasksCount: Int = 0,
    val eventsCount: Int = 0,
)

@HiltViewModel
class GuestProfileViewModel @Inject constructor(
    noteRepository: NoteRepository,
    val authRepository: AuthRepository? = null,
    private val themePrefs: ThemePrefs? = null,
    private val notesUiPrefs: NotesUiPrefs? = null,
) : ViewModel() {
    val stats: StateFlow<GuestProfileStats> = noteRepository.observeActive()
        .map { list ->
            GuestProfileStats(
                notesCount = list.count { it.note.kind == NoteKind.NOTE && it.note.reminderAt == null },
                tasksCount = list.count { it.note.kind == NoteKind.LIST },
                eventsCount = list.count { it.note.reminderAt != null || it.note.endAt != null },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GuestProfileStats(),
        )

    val authState: StateFlow<AuthState> = authRepository?.authState
        ?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AuthState.Loading,
        ) ?: MutableStateFlow(AuthState.Unauthenticated)

    val appAuthState: StateFlow<AppAuthState> = authRepository?.appAuthState
        ?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppAuthState.Initializing,
        ) ?: MutableStateFlow(AppAuthState.Guest)

    val currentUser: AuthUser?
        get() = authRepository?.currentUser

    fun signOut() {
        viewModelScope.launch {
            authRepository?.signOut()
        }
    }

    fun syncBackendProfile() {
        viewModelScope.launch {
            authRepository?.syncBackendProfile()
        }
    }

    val themeState: StateFlow<ThemeState> = themePrefs?.state
        ?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeState(),
        ) ?: MutableStateFlow(ThemeState())

    val notesUiState: StateFlow<NotesUiState> = notesUiPrefs?.state
        ?.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotesUiState(),
        ) ?: MutableStateFlow(NotesUiState())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePrefs?.setThemeMode(mode)
        }
    }

    fun setFirstDayOfWeek(day: String) {
        viewModelScope.launch {
            notesUiPrefs?.setFirstDayOfWeek(day)
        }
    }
}

/**
 * Guest Profile screen matching Notas Figma reference (Node 14:317).
 */
@Composable
fun GuestProfileScreen(
    onBackClick: () -> Unit,
    onCreateAccountClick: () -> Unit,
    onLoginClick: () -> Unit,
    onVerifyEmailClick: ((email: String) -> Unit)? = null,
    onNotificationsClick: (() -> Unit)? = null,
    onAppearanceClick: (() -> Unit)? = null,
    onStartOfWeekClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: GuestProfileViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val palette = LocalSemanticPalette.current
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val appAuthState by viewModel.appAuthState.collectAsStateWithLifecycle()
    val currentUser = (authState as? AuthState.Authenticated)?.user ?: viewModel.currentUser
    val isAuth = currentUser != null

    val themeState by viewModel.themeState.collectAsStateWithLifecycle()
    val notesUiState by viewModel.notesUiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showStartOfWeekDialog by remember { mutableStateOf(false) }

    var notificationsEnabled by remember { mutableStateOf(canPostNotifications(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = canPostNotifications(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.notasBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(36.dp)
                        .background(palette.cardSurface, shape = RoundedCornerShape(10.dp)),
                ) {
                    RememberMaterialRoundedSymbol(
                        name = "arrow_back",
                        size = 20.dp,
                        tint = palette.primaryText,
                        contentDescription = "Back",
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = if (isAuth) "Account" else "Profile",
                        style = NotasTypography.SectionTitle,
                        color = palette.primaryText,
                    )
                    Text(
                        text = if (isAuth) "Your Notas Cloud Profile" else "Your personal space in Notas",
                        style = NotasTypography.Caption,
                        color = palette.secondaryText,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Profile Card (Authenticated vs Guest)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(NotasTokens.CardRadius),
                colors = CardDefaults.cardColors(containerColor = palette.cardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (currentUser != null) {
                        val displayName = currentUser.displayName?.takeIf { it.isNotBlank() } ?: currentUser.email
                        val initials = currentUser.displayName?.split(" ")?.mapNotNull { it.firstOrNull()?.toString() }?.take(2)?.joinToString("")?.uppercase()?.ifBlank { null }
                            ?: currentUser.email.firstOrNull()?.uppercase()
                            ?: "U"
                        val badgeText = if (currentUser.isEmailVerified) "Email Verified" else "Email Unverified"

                        AccountHeader(
                            displayName = displayName,
                            subtitle = currentUser.email,
                            initials = initials,
                            photoUrl = currentUser.photoUrl,
                            badgeText = badgeText,
                            avatarSize = NotasTokens.AvatarSizeLarge,
                        )
                    } else {
                        AccountHeader(
                            displayName = "Guest",
                            subtitle = "Using Notas without an account",
                            initials = "G",
                            badgeText = "Data stored on this device only",
                            avatarSize = NotasTokens.AvatarSizeLarge,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Stats Columns (Notes, Tasks, Events)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatItem(count = stats.notesCount, label = "Notes")
                        StatDivider(color = palette.divider)
                        StatItem(count = stats.tasksCount, label = "Tasks")
                        StatDivider(color = palette.divider)
                        StatItem(count = stats.eventsCount, label = "Events")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (currentUser != null) {
                // Authenticated: Cloud Sync & Account Actions Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(NotasTokens.CardRadius),
                    colors = CardDefaults.cardColors(containerColor = palette.cardSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                    ) {
                        Text(
                            text = "Cloud Sync & Account",
                            style = NotasTypography.CardHeader,
                            color = palette.primaryText,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (val state = appAuthState) {
                                is AppAuthState.LoadingProfile -> "Synchronizing profile with Notas Cloud..."
                                is AppAuthState.Authenticated -> "Cloud sync active & connected"
                                is AppAuthState.RecoverableError -> "Sync status: ${state.message}"
                                else -> "Connected to Notas Cloud"
                            },
                            style = NotasTypography.BodyMedium,
                            color = if (appAuthState is AppAuthState.RecoverableError) palette.error else palette.primaryText,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Your private notes and settings are backed up to your account.",
                            style = NotasTypography.Body,
                            color = palette.secondaryText,
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                SecondaryButton(
                                    text = "Sync Now",
                                    onClick = { viewModel.syncBackendProfile() },
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                PrimaryButton(
                                    text = "Sign Out",
                                    onClick = { viewModel.signOut() },
                                )
                            }
                        }
                    }
                }

                if (!currentUser.isEmailVerified && onVerifyEmailClick != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SettingsRowCard {
                        SettingsRow(
                            title = "Verify Email Address",
                            subtitle = "Verify ${currentUser.email} to secure your cloud sync.",
                            trailingContent = {
                                RememberMaterialRoundedSymbol(
                                    name = "chevron_right",
                                    size = 20.dp,
                                    tint = palette.primaryAction,
                                    contentDescription = null,
                                )
                            },
                            onClick = { onVerifyEmailClick(currentUser.email) },
                            showDivider = false,
                        )
                    }
                }
            } else {
                // Guest: Backup & Sync Promo Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(NotasTokens.CardRadius),
                    colors = CardDefaults.cardColors(containerColor = palette.cardSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                    ) {
                        Text(
                            text = "Backup & sync",
                            style = NotasTypography.CardHeader,
                            color = palette.primaryText,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Keep your ideas with you",
                            style = NotasTypography.BodyMedium,
                            color = palette.primaryText,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Online backup requires an account",
                            style = NotasTypography.Label,
                            color = palette.secondaryText,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Create an account or log in to back up to your private server and sync across devices.",
                            style = NotasTypography.Body,
                            color = palette.secondaryText,
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                PrimaryButton(
                                    text = "Create account",
                                    onClick = onCreateAccountClick,
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                SecondaryButton(
                                    text = "Log in",
                                    onClick = onLoginClick,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Preferences Card
            SettingsRowCard(
                title = "Preferences",
                subtitle = "These preferences apply to this device.",
            ) {
                SettingsRow(
                    title = "Notifications",
                    valueText = if (notificationsEnabled) "On" else "Off",
                    onClick = {
                        if (onNotificationsClick != null) {
                            onNotificationsClick()
                        } else {
                            context.startActivity(appNotificationSettingsIntent(context))
                        }
                    },
                )
                SettingsRow(
                    title = "Appearance",
                    valueText = when (themeState.themeMode) {
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK, ThemeMode.BLACK -> "Dark"
                        ThemeMode.SYSTEM -> "System"
                    },
                    onClick = {
                        if (onAppearanceClick != null) {
                            onAppearanceClick()
                        } else {
                            showAppearanceDialog = true
                        }
                    },
                )
                SettingsRow(
                    title = "Start of week",
                    valueText = when (notesUiState.firstDayOfWeek.uppercase()) {
                        "SUNDAY" -> "Sunday"
                        "SATURDAY" -> "Saturday"
                        else -> "Monday"
                    },
                    showDivider = false,
                    onClick = {
                        if (onStartOfWeekClick != null) {
                            onStartOfWeekClick()
                        } else {
                            showStartOfWeekDialog = true
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showAppearanceDialog) {
        AlertDialog(
            onDismissRequest = { showAppearanceDialog = false },
            title = {
                Text(
                    text = "Appearance",
                    style = NotasTypography.SectionTitle,
                    color = palette.primaryText,
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(
                        ThemeMode.LIGHT to "Light",
                        ThemeMode.DARK to "Dark",
                        ThemeMode.SYSTEM to "System default",
                    ).forEach { (mode, label) ->
                        val isSelected = (themeState.themeMode == mode || (mode == ThemeMode.DARK && themeState.themeMode == ThemeMode.BLACK))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setThemeMode(mode)
                                    showAppearanceDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = label,
                                style = NotasTypography.BodyMedium,
                                color = palette.primaryText,
                            )
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setThemeMode(mode)
                                    showAppearanceDialog = false
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAppearanceDialog = false }) {
                    Text("Cancel", color = palette.secondaryText)
                }
            },
            containerColor = palette.cardSurface,
            shape = RoundedCornerShape(NotasTokens.CardRadius),
        )
    }

    if (showStartOfWeekDialog) {
        AlertDialog(
            onDismissRequest = { showStartOfWeekDialog = false },
            title = {
                Text(
                    text = "Start of week",
                    style = NotasTypography.SectionTitle,
                    color = palette.primaryText,
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(
                        "MONDAY" to "Monday",
                        "SUNDAY" to "Sunday",
                        "SATURDAY" to "Saturday",
                    ).forEach { (key, label) ->
                        val isSelected = notesUiState.firstDayOfWeek.equals(key, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setFirstDayOfWeek(key)
                                    showStartOfWeekDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = label,
                                style = NotasTypography.BodyMedium,
                                color = palette.primaryText,
                            )
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setFirstDayOfWeek(key)
                                    showStartOfWeekDialog = false
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showStartOfWeekDialog = false }) {
                    Text("Cancel", color = palette.secondaryText)
                }
            },
            containerColor = palette.cardSurface,
            shape = RoundedCornerShape(NotasTokens.CardRadius),
        )
    }
}

@Composable
private fun StatItem(
    count: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSemanticPalette.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = count.toString(),
            style = NotasTypography.SectionTitle,
            color = palette.primaryText,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = NotasTypography.Label,
            color = palette.secondaryText,
        )
    }
}

@Composable
private fun StatDivider(color: Color) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .width(1.dp)
            .background(color = color),
    )
}

