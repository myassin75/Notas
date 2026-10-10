package three.haddad.smartnotas.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import three.haddad.smartnotas.R
import three.haddad.smartnotas.data.NotesUiPrefs
import three.haddad.smartnotas.data.NotesUiState
import three.haddad.smartnotas.ui.components.settings.GroupPosition
import three.haddad.smartnotas.ui.components.settings.GroupedListColumn
import three.haddad.smartnotas.ui.components.settings.GroupedListItem



@Composable
fun NotesUiSection(
    prefs: NotesUiPrefs,
    state: NotesUiState,
) {
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxWidth()) {
        GroupedListColumn {
            GroupedListItem(position = GroupPosition.FIRST) {
                AppearanceSettingsToggleItem(
                    title = stringResource(R.string.appearance_adaptive_note_themes_title),
                    subtitle = stringResource(R.string.appearance_adaptive_note_themes_subtitle),
                    checked = state.adaptiveNoteThemes,
                    onCheckedChange = { scope.launch { prefs.setAdaptiveNoteThemes(it) } },
                )
            }
            GroupedListItem(position = GroupPosition.MIDDLE) {
                AppearanceSettingsToggleItem(
                    title = stringResource(R.string.appearance_cover_title),
                    subtitle = stringResource(R.string.appearance_cover_subtitle),
                    checked = state.heroOnCards,
                    onCheckedChange = { scope.launch { prefs.setHeroOnCards(it) } },
                )
            }
            GroupedListItem(position = GroupPosition.LAST) {
                AppearanceSettingsToggleItem(
                    title = stringResource(R.string.appearance_note_content_title),
                    subtitle = stringResource(R.string.appearance_note_content_subtitle),
                    checked = state.showNoteContentOnCards,
                    onCheckedChange = { scope.launch { prefs.setShowNoteContentOnCards(it) } },
                )
            }
        }
    }
}


@Composable
fun AppearanceSettingsToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
