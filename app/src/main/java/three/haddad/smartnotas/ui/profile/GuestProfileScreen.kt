package three.haddad.smartnotas.ui.profile

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import three.haddad.smartnotas.data.NoteKind
import three.haddad.smartnotas.data.NoteRepository
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.components.AccountHeader
import three.haddad.smartnotas.ui.components.PrimaryButton
import three.haddad.smartnotas.ui.components.SecondaryButton
import three.haddad.smartnotas.ui.components.SettingsRow
import three.haddad.smartnotas.ui.components.SettingsRowCard
import three.haddad.smartnotas.ui.theme.LocalSemanticPalette
import three.haddad.smartnotas.ui.theme.NotasTokens
import three.haddad.smartnotas.ui.theme.NotasTypography
import javax.inject.Inject

data class GuestProfileStats(
    val notesCount: Int = 0,
    val tasksCount: Int = 0,
    val eventsCount: Int = 0,
)

@HiltViewModel
class GuestProfileViewModel @Inject constructor(
    noteRepository: NoteRepository,
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
}

/**
 * Guest Profile screen matching Notas Figma reference (Node 14:317).
 */
@Composable
fun GuestProfileScreen(
    onBackClick: () -> Unit,
    onCreateAccountClick: () -> Unit,
    onLoginClick: () -> Unit,
    onNotificationsClick: () -> Unit = {},
    onAppearanceClick: () -> Unit = {},
    onStartOfWeekClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: GuestProfileViewModel = hiltViewModel(),
) {
    val palette = LocalSemanticPalette.current
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

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
                        text = "Profile",
                        style = NotasTypography.SectionTitle,
                        color = palette.primaryText,
                    )
                    Text(
                        text = "Your personal space in Notas",
                        style = NotasTypography.Caption,
                        color = palette.secondaryText,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Guest Profile Card
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
                    AccountHeader(
                        displayName = "Guest",
                        subtitle = "Using Notas without an account",
                        initials = "G",
                        badgeText = "Data stored on this device only",
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Local Stats Columns (Notes, Tasks, Events)
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

            // Backup & Sync Promo Card
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

            Spacer(modifier = Modifier.height(20.dp))

            // Preferences Card
            SettingsRowCard(
                title = "Preferences",
                subtitle = "These preferences apply to this device.",
            ) {
                SettingsRow(
                    title = "Notifications",
                    valueText = "On",
                    onClick = onNotificationsClick,
                )
                SettingsRow(
                    title = "Appearance",
                    valueText = "Light",
                    onClick = onAppearanceClick,
                )
                SettingsRow(
                    title = "Start of week",
                    valueText = "Monday",
                    showDivider = false,
                    onClick = onStartOfWeekClick,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
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

