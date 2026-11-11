package haddad.three.notas.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import haddad.three.notas.R
import haddad.three.notas.data.NotesFilter
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.common.emptyStateSpacing
import haddad.three.notas.ui.common.isSmallLandscape
import haddad.three.notas.ui.components.EmptyNotesIllustration
import haddad.three.notas.ui.components.RememberButton
import haddad.three.notas.ui.components.RememberOutlinedButton

@Composable
internal fun NotesEmptyState(
    filter: NotesFilter,
    totalUnfilteredNotes: Int,
    onCreateNote: () -> Unit,
    onCreateList: () -> Unit,
    modifier: Modifier = Modifier,
    // Pane mode hosts its own create FAB, so the inline buttons are redundant there —
    // and dropping them frees the height the subtitle needs on short landscape panes.
    showCreateActions: Boolean = true,
) {
    val pristineVault =
        totalUnfilteredNotes == 0 && filter.text.isBlank() && !filter.facetActive
    val isSmallLandscape = isSmallLandscape()
    val heroSpacing = emptyStateSpacing(prominent = true)
    val filterSpacing = emptyStateSpacing(prominent = false)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (pristineVault) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                EmptyNotesIllustration()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.home_no_notes_title),
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.home_no_notes_subtitle),
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                )
            }
            if (showCreateActions) {
                Spacer(Modifier.height(24.dp))
                val isDark = haddad.three.notas.ui.theme.LocalIsDark.current
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color =
                        if (isDark) {
                            Color(0xFF242424)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    border =
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDark) Color(0xFF3A3A3A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { onCreateNote() }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "edit_note",
                                    size = 22.dp,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    weight = FontWeight.Normal,
                                    filled = false,
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.home_create_note),
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        ),
                                )
                                Text(
                                    text = stringResource(R.string.home_create_note_subtitle),
                                    style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                )
                            }
                            RememberMaterialRoundedSymbol(
                                name = "chevron_right",
                                size = 20.dp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                weight = FontWeight.Normal,
                                filled = false,
                            )
                        }

                        HorizontalDivider(
                            color = if (isDark) Color(0xFF3A3A3A) else MaterialTheme.colorScheme.outlineVariant,
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { onCreateList() }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "checklist",
                                    size = 22.dp,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    weight = FontWeight.Normal,
                                    filled = false,
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.home_create_list),
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        ),
                                )
                                Text(
                                    text = stringResource(R.string.home_create_list_subtitle),
                                    style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                )
                            }
                            RememberMaterialRoundedSymbol(
                                name = "chevron_right",
                                size = 20.dp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                weight = FontWeight.Normal,
                                filled = false,
                            )
                        }
                    }
                }
            }
        } else {
            val titleText =
                when {
                    filter.text.isNotBlank() ->
                        stringResource(R.string.home_no_results_for, filter.text)
                    filter.facetActive ->
                        stringResource(R.string.home_no_results_filters_title)
                    else -> stringResource(R.string.home_nothing_here)
                }
            val hintText =
                if (filter.text.isNotBlank()) {
                    stringResource(R.string.home_no_results_hint)
                } else {
                    stringResource(R.string.home_no_results_filters_hint)
                }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                EmptyNotesIllustration()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = titleText,
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = hintText,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
