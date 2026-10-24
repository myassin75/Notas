package three.haddad.smartnotas.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.toPersistentSet
import three.haddad.smartnotas.R
import three.haddad.smartnotas.data.FilterType
import three.haddad.smartnotas.data.GroupBy
import three.haddad.smartnotas.data.NotesFilter
import three.haddad.smartnotas.data.ShoulderEntity
import three.haddad.smartnotas.data.SortDir
import three.haddad.smartnotas.data.SortKey
import three.haddad.smartnotas.data.ViewOptions
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.components.RememberCheckbox
import three.haddad.smartnotas.ui.components.RememberDropdownMenuItem
import three.haddad.smartnotas.ui.components.RememberInputChip

private val ActiveChipHeight = 32.dp

@Composable
internal fun ActiveFilterChips(
    filter: NotesFilter,
    onChange: (NotesFilter) -> Unit,
    totalActiveCount: Int = 0,
    activeShoulders: List<ShoulderEntity> = emptyList(),
    modifier: Modifier = Modifier,
    viewOptions: ViewOptions = ViewOptions(),
    onViewOptionsChange: ((ViewOptions) -> Unit)? = null,
    scrollState: ScrollState = rememberScrollState(),
    expandedDropdown: ActiveFilterDropdown? = null,
    onExpandedDropdownChange: (ActiveFilterDropdown?) -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // "All" Chip
            val isDark = three.haddad.smartnotas.ui.theme.LocalIsDark.current
            val semantic = three.haddad.smartnotas.ui.theme.LocalSemanticPalette.current
            val allSelected = filter.shoulderId == null
            val allBg =
                if (allSelected) {
                    if (isDark) semantic.selectedStateContainer else MaterialTheme.colorScheme.primaryContainer
                } else {
                    three.haddad.smartnotas.ui.theme.glassContainerColor(
                        variant = three.haddad.smartnotas.ui.theme.GlassVariant.Chip,
                        isDark = isDark,
                    )
                }
            val allBorder =
                if (allSelected) {
                    if (isDark) BorderStroke(1.dp, semantic.primaryAction.copy(alpha = 0.5f)) else null
                } else {
                    three.haddad.smartnotas.ui.theme.glassBorderStroke(
                        variant = three.haddad.smartnotas.ui.theme.GlassVariant.Chip,
                        isDark = isDark,
                    )
                }
            val allTextColor =
                if (allSelected) {
                    if (isDark) semantic.primaryAction else MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    if (isDark) Color(0xFFA6A4B4) else MaterialTheme.colorScheme.onSurfaceVariant
                }

            Surface(
                shape = CircleShape,
                color = allBg,
                border = allBorder,
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .clickable { onChange(filter.copy(shoulderId = null)) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "All",
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp,
                                color = allTextColor,
                            ),
                    )
                    if (totalActiveCount > 0) {
                        Text(
                            text = totalActiveCount.toString(),
                            style =
                                MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 13.sp,
                                    color = allTextColor,
                                ),
                        )
                    }
                }
            }

            // Shoulder Chips
            activeShoulders.forEach { shoulder ->
                val selected = filter.shoulderId == shoulder.id
                val shoulderColor = parseColorHex(shoulder.colorHex, MaterialTheme.colorScheme.primary)
                val (badgeBg, badgeText) = three.haddad.smartnotas.ui.components.readableBadgeColors(shoulderColor, isDark)
                val chipBg =
                    if (selected) {
                        badgeBg
                    } else {
                        three.haddad.smartnotas.ui.theme.glassContainerColor(
                            variant = three.haddad.smartnotas.ui.theme.GlassVariant.Chip,
                            isDark = isDark,
                        )
                    }
                val chipBorder =
                    if (selected) {
                        BorderStroke(1.dp, badgeText.copy(alpha = 0.5f))
                    } else {
                        three.haddad.smartnotas.ui.theme.glassBorderStroke(
                            variant = three.haddad.smartnotas.ui.theme.GlassVariant.Chip,
                            isDark = isDark,
                        )
                    }
                val chipTextColor =
                    if (selected) {
                        badgeText
                    } else if (isDark) {
                        Color(0xFFDDDCE5)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }

                Surface(
                    shape = CircleShape,
                    color = chipBg,
                    border = chipBorder,
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .clickable {
                                onChange(filter.copy(shoulderId = if (selected) null else shoulder.id))
                            },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (!selected) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(shoulderColor),
                            )
                        }
                        Text(
                            text = shoulder.name,
                            style =
                                MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = chipTextColor,
                                ),
                        )
                    }
                }
            }
        }

        // Sort button on the right
        Box {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable {
                            onExpandedDropdownChange(if (expandedDropdown == ActiveFilterDropdown.SORT) null else ActiveFilterDropdown.SORT)
                        }
                        .semantics { contentDescription = "Filter settings" },
                contentAlignment = Alignment.Center,
            ) {
                RememberMaterialRoundedSymbol(
                    name = "tune",
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    weight = FontWeight.Normal,
                    filled = false,
                )
            }

            DropdownMenu(
                expanded = expandedDropdown == ActiveFilterDropdown.SORT,
                onDismissRequest = { onExpandedDropdownChange(null) },
            ) {
                sortOptions().forEach { sortOption ->
                    RadioMenuItem(
                        label = sortOption.label,
                        selected = viewOptions.sortKey == sortOption.sortKey && viewOptions.sortDir == sortOption.sortDir,
                        onClick = {
                            onViewOptionsChange?.invoke(
                                viewOptions.copy(sortKey = sortOption.sortKey, sortDir = sortOption.sortDir),
                            )
                            onExpandedDropdownChange(null)
                        },
                    )
                }
            }
        }
    }
}

private fun parseColorHex(
    hex: String?,
    fallback: Color,
): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        val clean = hex.removePrefix("#")
        val colorLong = clean.toLong(16)
        if (clean.length == 6) {
            Color(colorLong or 0xFF000000L)
        } else if (clean.length == 8) {
            Color(colorLong)
        } else {
            fallback
        }
    } catch (_: Throwable) {
        fallback
    }
}

internal enum class ActiveFilterDropdown {
    TYPE,
    GROUP,
    SORT,
    TAGS,
    OTHERS,
}

@Composable
private fun FilterDropdownChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    avatar: @Composable (() -> Unit)? = null,
    menuContent: @Composable () -> Unit,
) {
    Box {
        RememberInputChip(
            selected = selected,
            onClick = { onExpandedChange(true) },
            enabled = enabled,
            label = { Text(label) },
            modifier = Modifier.height(ActiveChipHeight),
            avatar = avatar,
            trailingIcon = {
                RememberMaterialRoundedSymbol(
                    name = "expand_more",
                    size = 16.dp,
                    weight = FontWeight.Medium,
                )
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            menuContent()
        }
    }
}

@Composable
private fun CheckableMenuItem(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    RememberDropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = {
            RememberCheckbox(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.size(24.dp),
            )
        },
    )
}

@Composable
private fun RadioMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    RememberDropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = {
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier.size(24.dp),
            )
        },
    )
}

private data class SortOption(
    val sortKey: SortKey,
    val sortDir: SortDir,
    val label: String,
)

@Composable
private fun sortLabel(
    sortKey: SortKey,
    sortDir: SortDir,
): String =
    when (sortKey) {
        SortKey.REMINDER ->
            if (sortDir == SortDir.ASC) {
                stringResource(R.string.view_options_sort_reminder_soonest)
            } else {
                stringResource(R.string.view_options_sort_reminder_latest)
            }
        SortKey.LAST_MODIFIED ->
            if (sortDir == SortDir.ASC) {
                stringResource(R.string.view_options_sort_modified_oldest)
            } else {
                stringResource(R.string.view_options_sort_modified_newest)
            }
        SortKey.CREATED ->
            if (sortDir == SortDir.ASC) {
                stringResource(R.string.view_options_sort_created_oldest)
            } else {
                stringResource(R.string.view_options_sort_created_newest)
            }
        SortKey.ALPHABETICAL ->
            if (sortDir == SortDir.ASC) {
                stringResource(R.string.view_options_sort_alphabetical_az)
            } else {
                stringResource(R.string.view_options_sort_alphabetical_za)
            }
    }

@Composable
private fun sortOptions(): List<SortOption> =
    listOf(
        SortOption(SortKey.REMINDER, SortDir.ASC, stringResource(R.string.view_options_sort_reminder_soonest)),
        SortOption(SortKey.REMINDER, SortDir.DESC, stringResource(R.string.view_options_sort_reminder_latest)),
        SortOption(SortKey.LAST_MODIFIED, SortDir.DESC, stringResource(R.string.view_options_sort_modified_newest)),
        SortOption(SortKey.LAST_MODIFIED, SortDir.ASC, stringResource(R.string.view_options_sort_modified_oldest)),
        SortOption(SortKey.CREATED, SortDir.DESC, stringResource(R.string.view_options_sort_created_newest)),
        SortOption(SortKey.CREATED, SortDir.ASC, stringResource(R.string.view_options_sort_created_oldest)),
        SortOption(SortKey.ALPHABETICAL, SortDir.ASC, stringResource(R.string.view_options_sort_alphabetical_az)),
        SortOption(SortKey.ALPHABETICAL, SortDir.DESC, stringResource(R.string.view_options_sort_alphabetical_za)),
    )

@Composable
private fun groupOptions(): List<Pair<GroupBy, String>> =
    listOf(
        GroupBy.NONE to stringResource(R.string.view_options_group_none),
        GroupBy.DATE to stringResource(R.string.view_options_group_date),
        GroupBy.SHOULDER to stringResource(R.string.view_options_group_shoulders),
        GroupBy.TYPE to stringResource(R.string.view_options_group_type),
    )

@Composable
private fun typeLabel(filterType: FilterType): String =
    when (filterType) {
        FilterType.ALL -> stringResource(R.string.filter_type_all)
        FilterType.NOTE -> stringResource(R.string.filter_type_notes)
        FilterType.LIST -> stringResource(R.string.filter_type_lists)
    }

@Composable
private fun groupLabel(groupBy: GroupBy): String =
    when (groupBy) {
        GroupBy.DATE -> stringResource(R.string.view_options_group_date)
        GroupBy.NONE -> stringResource(R.string.view_options_group_none)
        GroupBy.SHOULDER -> stringResource(R.string.view_options_group_shoulders)
        GroupBy.TYPE -> stringResource(R.string.view_options_group_type)
    }
