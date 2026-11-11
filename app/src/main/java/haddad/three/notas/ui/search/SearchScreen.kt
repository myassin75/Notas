package haddad.three.notas.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.compositeOver
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import haddad.three.notas.R
import haddad.three.notas.data.NoteKind
import haddad.three.notas.data.NoteWithItems
import haddad.three.notas.data.SearchHistoryEntry
import haddad.three.notas.data.ShoulderEntity
import haddad.three.notas.ui.common.RememberMaterialRoundedSymbol
import haddad.three.notas.ui.components.parseNoteColorHex
import haddad.three.notas.ui.components.readableBadgeColors
import haddad.three.notas.ui.settings.SettingsDesignTokens
import haddad.three.notas.ui.theme.LocalIsDark
import haddad.three.notas.ui.theme.NoteColors
import haddad.three.notas.ui.theme.elevatedCardColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchRoute(
    onOpenNote: (NoteWithItems) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val activeShoulders by viewModel.activeShoulders.collectAsStateWithLifecycle()

    SearchScreen(
        uiState = uiState,
        recentSearches = recentSearches,
        activeShoulders = activeShoulders,
        onQueryChange = viewModel::updateQuery,
        onSubmitSearch = viewModel::submitSearch,
        onCategoryIndexChange = viewModel::updateCategoryIndex,
        onSortModeChange = viewModel::setSortMode,
        onRemoveRecentSearch = viewModel::removeRecentSearch,
        onClearRecentSearches = viewModel::clearRecentSearches,
        onOpenNote = onOpenNote,
        onApplyFilters = viewModel::applyFilters,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    recentSearches: List<SearchHistoryEntry>,
    activeShoulders: List<ShoulderEntity>,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onCategoryIndexChange: (Int) -> Unit,
    onSortModeChange: (SearchSortMode) -> Unit,
    onRemoveRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onOpenNote: (NoteWithItems) -> Unit,
    onApplyFilters: (SearchFilters) -> Unit,
) {
    var showFilters by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val isDark = LocalIsDark.current

    val categories =
        listOf(
            stringResource(R.string.search_tab_all),
            stringResource(R.string.search_tab_notes),
            stringResource(R.string.search_tab_tasks),
            stringResource(R.string.search_tab_events),
        )

    Scaffold(
        containerColor = if (isDark) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp),
            ) {
                // Header
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.main_tab_search),
                        style =
                            MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.search_subtitle),
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search Bar + Filter Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val searchBarBg =
                        if (isDark) {
                            Color(0xFF262626)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    val searchBarBorder =
                        if (isSearchFocused) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.85f else 0.75f))
                        } else {
                            BorderStroke(1.dp, if (isDark) Color(0xFF3A3A3A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        }

                    Surface(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = searchBarBg,
                        border = searchBarBorder,
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val iconTint =
                                if (isSearchFocused || uiState.query.isNotEmpty()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            RememberMaterialRoundedSymbol(
                                name = "search",
                                size = 22.dp,
                                tint = iconTint,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (uiState.query.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.search_placeholder),
                                        color = if (isDark) Color(0xFFC8C6D4) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                    )
                                }
                                BasicTextField(
                                    value = uiState.query,
                                    onValueChange = onQueryChange,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions =
                                        KeyboardActions(
                                            onSearch = { onSubmitSearch(uiState.query) },
                                        ),
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { isSearchFocused = it.isFocused },
                                    textStyle =
                                        MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 15.sp,
                                        ),
                                )
                            }
                            if (uiState.query.isNotEmpty()) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    modifier =
                                        Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .clickable { onQueryChange("") },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        RememberMaterialRoundedSymbol(
                                            name = "close",
                                            size = 18.dp,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Filter Button with Active Badge
                    val hasActiveFilters = uiState.filters.activeCount > 0
                    val filterBorder =
                        if (hasActiveFilters) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            BorderStroke(1.dp, if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        }

                    Box {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = searchBarBg,
                            border = filterBorder,
                            modifier =
                                Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { showFilters = true },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                RememberMaterialRoundedSymbol(
                                    name = "tune",
                                    size = 22.dp,
                                    tint = if (hasActiveFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (hasActiveFilters) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier =
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(18.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = uiState.filters.activeCount.toString(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scope Pills Row
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(categories) { index, title ->
                        val isSelected = uiState.filters.typeIndex == index

                        val typeCount =
                            when (index) {
                                0 -> uiState.allResultsCount
                                1 -> uiState.notesResultsCount
                                2 -> uiState.tasksResultsCount
                                3 -> uiState.eventsResultsCount
                                else -> 0
                            }

                        val iconName =
                            when (index) {
                                1 -> "description"
                                2 -> "checklist"
                                3 -> "event"
                                else -> null
                            }

                        val pillBg =
                            if (isSelected) {
                                if (isDark) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                }
                            } else {
                                if (isDark) {
                                    Color(0xFF141419).copy(alpha = 0.60f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            }

                        val pillBorder =
                            if (isSelected) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.40f else 0.50f))
                            }

                        val pillContentColor =
                            if (isSelected) {
                                if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }

                        Surface(
                            shape = CircleShape,
                            color = pillBg,
                            border = pillBorder,
                            modifier =
                                Modifier
                                    .clip(CircleShape)
                                    .clickable { onCategoryIndexChange(index) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (iconName != null) {
                                    RememberMaterialRoundedSymbol(
                                        name = iconName,
                                        size = 16.dp,
                                        tint = pillContentColor,
                                    )
                                }
                                Text(
                                    text = title,
                                    color = pillContentColor,
                                    style =
                                        MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.sp,
                                        ),
                                )
                                if (uiState.query.isNotBlank()) {
                                    Text(
                                        text = typeCount.toString(),
                                        color = if (isSelected) pillContentColor else MaterialTheme.colorScheme.primary,
                                        style =
                                            MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
        ) {
            if (uiState.query.isEmpty()) {
                // Initial State: Intro Card & Recent Searches
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        // Informational Card
                        val infoCardBg =
                            if (isDark) {
                                Color(0xFF262626)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        val infoCardBorder =
                            if (isDark) {
                                BorderStroke(1.dp, Color(0xFF3A3A3A))
                            } else {
                                null
                            }

                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = infoCardBg,
                            border = infoCardBorder,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                val purple = MaterialTheme.colorScheme.primary
                                Canvas(modifier = Modifier.size(24.dp)) {
                                    val p =
                                        Path().apply {
                                            moveTo(size.width * 0.5f, 0f)
                                            cubicTo(size.width * 0.5f, size.height * 0.35f, size.width * 0.65f, size.height * 0.5f, size.width, size.height * 0.5f)
                                            cubicTo(size.width * 0.65f, size.height * 0.5f, size.width * 0.5f, size.height * 0.65f, size.width * 0.5f, size.height)
                                            cubicTo(size.width * 0.5f, size.height * 0.65f, size.width * 0.35f, size.height * 0.5f, 0f, size.height * 0.5f)
                                            cubicTo(size.width * 0.35f, size.height * 0.5f, size.width * 0.5f, size.height * 0.35f, size.width * 0.5f, 0f)
                                            close()
                                        }
                                    drawPath(p, color = purple)
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.search_intro_title),
                                        style =
                                            MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = if (isDark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer,
                                            ),
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.search_intro_desc),
                                        style =
                                            MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            ),
                                    )
                                }
                            }
                        }
                    }

                    item {
                        if (recentSearches.isNotEmpty()) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.search_recent_searches_title),
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        ),
                                )
                                Text(
                                    text = stringResource(R.string.search_clear_all),
                                    style =
                                        MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                        ),
                                    modifier = Modifier.clickable { onClearRecentSearches() },
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val recentCardBg =
                                if (isDark) {
                                    Color(0xFF262626)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            val recentBorder =
                                BorderStroke(1.dp, if (isDark) Color(0xFF3A3A3A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f))

                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = recentCardBg,
                                border = recentBorder,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column {
                                    recentSearches.forEachIndexed { index, entry ->
                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        onSubmitSearch(entry.query)
                                                    }.padding(horizontal = 18.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RememberMaterialRoundedSymbol(
                                                name = "history",
                                                size = 20.dp,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = entry.query,
                                                    style =
                                                        MaterialTheme.typography.bodyLarge.copy(
                                                            fontWeight = FontWeight.Medium,
                                                            fontSize = 15.sp,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                        ),
                                                )
                                                Text(
                                                    text = formatRecentSearchDate(entry.timestamp),
                                                    style =
                                                        MaterialTheme.typography.bodySmall.copy(
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        ),
                                                )
                                            }
                                            Surface(
                                                shape = CircleShape,
                                                color = Color.Transparent,
                                                modifier =
                                                    Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .clickable { onRemoveRecentSearch(entry.query) },
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    RememberMaterialRoundedSymbol(
                                                        name = "close",
                                                        size = 18.dp,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                        if (index < recentSearches.size - 1) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.50f),
                                                thickness = 1.dp,
                                                modifier = Modifier.padding(horizontal = 18.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.search_recent_searches_hint),
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                            )
                        }
                    }
                }
            } else if (uiState.isLoading) {
                // Loading State with Semantic Accent
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                    )
                }
            } else if (uiState.searchResults.isEmpty()) {
                // Understated Empty Search Results State
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    ) {
                        val emptyCircleBg =
                            if (isDark) {
                                Color(0xFF181820).copy(alpha = 0.80f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        val emptyCircleBorder =
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.45f else 0.35f))

                        Surface(
                            shape = CircleShape,
                            color = emptyCircleBg,
                            border = emptyCircleBorder,
                            modifier = Modifier.size(76.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                RememberMaterialRoundedSymbol(
                                    name = "search_off",
                                    size = 32.dp,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.search_empty_title),
                            style =
                                MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.search_empty_subtitle, uiState.query),
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        val clearBtnBg =
                            if (isDark) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        val clearBtnColor =
                            if (isDark) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        Surface(
                            shape = CircleShape,
                            color = clearBtnBg,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier =
                                Modifier
                                    .clip(CircleShape)
                                    .clickable { onQueryChange("") },
                        ) {
                            Text(
                                text = stringResource(R.string.search_clear_button),
                                style =
                                    MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = clearBtnColor,
                                    ),
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            } else {
                // Results State
                val sorts =
                    listOf(
                        SearchSortMode.RELEVANCE to stringResource(R.string.search_sort_relevance),
                        SearchSortMode.NEWEST to stringResource(R.string.search_sort_newest),
                        SearchSortMode.OLDEST to stringResource(R.string.search_sort_oldest),
                    )

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val resultsCount = uiState.searchResults.size
                        Text(
                            text = pluralStringResource(R.plurals.search_results_count, resultsCount, resultsCount),
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                        )
                        val scopeText =
                            when (uiState.filters.typeIndex) {
                                1 -> stringResource(R.string.search_scope_in_notes)
                                2 -> stringResource(R.string.search_scope_in_tasks)
                                3 -> stringResource(R.string.search_scope_in_events)
                                else -> stringResource(R.string.search_scope_across_all)
                            }
                        Text(
                            text = scopeText,
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                        )
                    }

                    // Sort row
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.search_sort_by),
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(sorts) { (mode, label) ->
                                val isSelected = uiState.sortMode == mode
                                val sortPillBg =
                                    if (isSelected) {
                                        if (isDark) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                        } else {
                                            MaterialTheme.colorScheme.primaryContainer
                                        }
                                    } else {
                                        if (isDark) {
                                            Color(0xFF141419).copy(alpha = 0.60f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        }
                                    }
                                val sortPillBorder =
                                    if (isSelected) {
                                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.40f else 0.50f))
                                    }
                                val sortPillColor =
                                    if (isSelected) {
                                        if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }

                                Surface(
                                    shape = CircleShape,
                                    color = sortPillBg,
                                    border = sortPillBorder,
                                    modifier =
                                        Modifier
                                            .clip(CircleShape)
                                            .clickable { onSortModeChange(mode) },
                                ) {
                                    Text(
                                        text = label,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        color = sortPillColor,
                                        style =
                                            MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.sp,
                                            ),
                                    )
                                }
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.searchResults, key = { it.note.id }) { result ->
                            SearchResultCard(
                                result = result,
                                searchQuery = uiState.query,
                                onClick = { onOpenNote(result) },
                            )
                        }
                        item {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.search_results_seen_all, uiState.searchResults.size),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 24.dp),
                                textAlign = TextAlign.Center,
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        SearchFiltersBottomSheet(
            filters = uiState.filters,
            searchQuery = uiState.query,
            activeShoulders = activeShoulders,
            categories = categories,
            onDismiss = { showFilters = false },
            onApply = { newFilters ->
                onApplyFilters(newFilters)
                showFilters = false
            },
        )
    }
}

@Composable
private fun SearchFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDark.current
    val chipBg =
        if (selected) {
            if (isDark) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        } else {
            if (isDark) {
                Color(0xFF3A3A3A)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        }
    val chipBorder =
        if (selected) {
            BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, if (isDark) Color(0xFF3A3A3A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f))
        }
    val chipLabelColor =
        if (selected) {
            if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            if (isDark) Color(0xFFA6A4B4) else MaterialTheme.colorScheme.onSurfaceVariant
        }

    Surface(
        shape = CircleShape,
        color = chipBg,
        border = chipBorder,
        modifier =
            modifier
                .clip(CircleShape)
                .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    color = chipLabelColor,
                ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFiltersBottomSheet(
    filters: SearchFilters,
    searchQuery: String,
    activeShoulders: List<ShoulderEntity>,
    categories: List<String>,
    onDismiss: () -> Unit,
    onApply: (SearchFilters) -> Unit,
) {
    var draftFilters by remember(filters) { mutableStateOf(filters) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    val isDark = LocalIsDark.current

    val sheetContainerColor =
        if (isDark) {
            Color(30, 30, 30).copy(alpha = 0.75f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetContainerColor,
        scrimColor = Color.Black.copy(alpha = 0.40f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDark) 0.50f else 0.40f),
            )
        },
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.search_filter_title),
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                )
                Text(
                    text = stringResource(R.string.search_filter_subtitle),
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (searchQuery.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "search",
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = searchQuery,
                            style =
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (draftFilters.activeCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = stringResource(R.string.search_filter_active_count, draftFilters.activeCount),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style =
                                        MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        ),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            item {
                val innerCardBg =
                    if (isDark) {
                        Color(0xFF262626)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                val innerCardBorder =
                    BorderStroke(
                        1.dp,
                        if (isDark) Color(0xFF3A3A3A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = innerCardBg,
                    border = innerCardBorder,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // 1. Type
                        Text(
                            text = stringResource(R.string.search_filter_type),
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(categories) { idx, type ->
                                val selected = draftFilters.typeIndex == idx
                                SearchFilterChip(
                                    selected = selected,
                                    onClick = { draftFilters = draftFilters.copy(typeIndex = idx) },
                                    label = type,
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.50f),
                        )

                        // 2. Date range
                        Text(
                            text = stringResource(R.string.search_filter_date_range),
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val ranges =
                            listOf(
                                stringResource(R.string.search_filter_range_any),
                                stringResource(R.string.search_filter_range_this_month),
                                stringResource(R.string.search_filter_range_custom),
                            )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(ranges) { idx, range ->
                                val selected = draftFilters.dateRangeIndex == idx
                                SearchFilterChip(
                                    selected = selected,
                                    onClick = { draftFilters = draftFilters.copy(dateRangeIndex = idx) },
                                    label = range,
                                )
                            }
                        }
                        if (draftFilters.dateRangeIndex == 2) {
                            Spacer(modifier = Modifier.height(12.dp))
                            val dateBoxBg =
                                if (isDark) {
                                    Color(0xFF22222C).copy(alpha = 0.65f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }
                            val dateBoxBorder =
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.45f))

                            val locale = LocalConfiguration.current.locales[0]

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = dateBoxBg,
                                    border = dateBoxBorder,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .clickable { showStartDatePicker = true },
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                    ) {
                                        RememberMaterialRoundedSymbol(name = "calendar_month", size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(8.dp))
                                        val startDateText =
                                            if (draftFilters.customStartDate != null) {
                                                SimpleDateFormat("MMM d, yyyy", locale).format(Date(draftFilters.customStartDate!!))
                                            } else {
                                                stringResource(R.string.search_filter_start_date)
                                            }
                                        Text(
                                            text = startDateText,
                                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                        )
                                    }
                                }
                                Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = dateBoxBg,
                                    border = dateBoxBorder,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .clickable { showEndDatePicker = true },
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                    ) {
                                        RememberMaterialRoundedSymbol(name = "calendar_month", size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(8.dp))
                                        val endDateText =
                                            if (draftFilters.customEndDate != null) {
                                                SimpleDateFormat("MMM d, yyyy", locale).format(Date(draftFilters.customEndDate!!))
                                            } else {
                                                stringResource(R.string.search_filter_end_date)
                                            }
                                        Text(
                                            text = endDateText,
                                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.search_filter_date_hint),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.50f),
                        )

                        // 3. Task status
                        Text(
                            text = stringResource(R.string.search_filter_task_status),
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val statuses =
                            listOf(
                                stringResource(R.string.search_filter_status_any),
                                stringResource(R.string.search_filter_status_todo),
                                stringResource(R.string.search_filter_status_completed),
                            )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(statuses) { idx, status ->
                                val selected = draftFilters.taskStatusIndex == idx
                                SearchFilterChip(
                                    selected = selected,
                                    onClick = { draftFilters = draftFilters.copy(taskStatusIndex = idx) },
                                    label = status,
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.50f),
                        )

                        // 4. Shoulders (with preserved category colors)
                        Text(
                            text = stringResource(R.string.search_filter_shoulders),
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        var shoulderDropdownExpanded by remember { mutableStateOf(false) }
                        val shoulderTriggerBg =
                            if (isDark) {
                                Color(0xFF22222C).copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        val shoulderTriggerBorder =
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.45f))

                        Box {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = shoulderTriggerBg,
                                border = shoulderTriggerBorder,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clickable { shoulderDropdownExpanded = true },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    val selectedShoulder = activeShoulders.find { it.id == draftFilters.shoulderId }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (selectedShoulder != null && !selectedShoulder.colorHex.isNullOrBlank()) {
                                            val sColor = parseNoteColorHex(selectedShoulder.colorHex, MaterialTheme.colorScheme.primary)
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(sColor),
                                            )
                                        }
                                        Text(
                                            text = selectedShoulder?.name ?: stringResource(R.string.search_filter_shoulders_any),
                                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                        )
                                    }
                                    RememberMaterialRoundedSymbol(name = "expand_more", size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            val dropdownBg = if (isDark) Color(0xFF262626) else MaterialTheme.colorScheme.surface
                            DropdownMenu(
                                expanded = shoulderDropdownExpanded,
                                onDismissRequest = { shoulderDropdownExpanded = false },
                                modifier =
                                    Modifier
                                        .background(dropdownBg)
                                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.50f else 0.35f))),
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.search_filter_shoulders_any),
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    },
                                    onClick = {
                                        draftFilters = draftFilters.copy(shoulderId = null)
                                        shoulderDropdownExpanded = false
                                    },
                                )
                                activeShoulders.forEach { shoulder ->
                                    val fallbackPrimary = MaterialTheme.colorScheme.primary
                                    val shoulderColor = parseNoteColorHex(shoulder.colorHex, fallbackPrimary)
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier =
                                                        Modifier
                                                            .size(10.dp)
                                                            .clip(CircleShape)
                                                            .background(shoulderColor),
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = shoulder.name,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                        },
                                        onClick = {
                                            draftFilters = draftFilters.copy(shoulderId = shoulder.id)
                                            shoulderDropdownExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.search_filter_shoulders_hint),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.50f),
                        )

                        // 5. Include archived
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.search_filter_include_archived),
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        ),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val archiveHint =
                                    if (draftFilters.includeArchived) {
                                        stringResource(R.string.search_filter_archived_shown_hint)
                                    } else {
                                        stringResource(R.string.search_filter_archived_hidden_hint)
                                    }
                                Text(
                                    text = archiveHint,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
                                )
                            }
                            Switch(
                                checked = draftFilters.includeArchived,
                                onCheckedChange = { draftFilters = draftFilters.copy(includeArchived = it) },
                                colors =
                                    SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                        uncheckedThumbColor = if (isDark) Color(0xFF9E9EA8) else MaterialTheme.colorScheme.outline,
                                        uncheckedTrackColor = if (isDark) Color(0xFF282834) else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        uncheckedBorderColor = Color.Transparent,
                                    ),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            draftFilters = SearchFilters()
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.search_filter_reset),
                            style =
                                MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 16.sp,
                                ),
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onApply(draftFilters)
                                },
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val applyText =
                                if (draftFilters.activeCount > 0) {
                                    stringResource(R.string.search_filter_apply_with_count, draftFilters.activeCount)
                                } else {
                                    stringResource(R.string.search_filter_apply)
                                }
                            Text(
                                text = applyText,
                                style =
                                    MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 15.sp,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showStartDatePicker) {
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = draftFilters.customStartDate ?: System.currentTimeMillis(),
            )
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            draftFilters = draftFilters.copy(customStartDate = millis)
                        }
                        showStartDatePicker = false
                    },
                ) {
                    Text(
                        text = stringResource(R.string.common_ok),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = draftFilters.customEndDate ?: System.currentTimeMillis(),
            )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            draftFilters = draftFilters.copy(customEndDate = millis)
                        }
                        showEndDatePicker = false
                    },
                ) {
                    Text(
                        text = stringResource(R.string.common_ok),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun SearchResultCard(
    result: NoteWithItems,
    searchQuery: String,
    onClick: () -> Unit,
) {
    val isEvent = SearchViewModel.isEvent(result)
    val isTask = SearchViewModel.isTask(result)

    val iconName =
        when {
            isEvent -> "event"
            isTask -> "checklist"
            else -> "description"
        }

    val primaryColor = MaterialTheme.colorScheme.primary
    val highlightColor = MaterialTheme.colorScheme.primary
    val isDark = LocalIsDark.current
    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.25f else 0.15f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val noteAccent =
        remember(result.note.colorIndex, isDark) {
            if (!NoteColors.isNone(result.note.colorIndex)) {
                if (isDark) NoteColors.at(result.note.colorIndex).dark else NoteColors.at(result.note.colorIndex).light
            } else {
                null
            }
        }
    val cardColors = elevatedCardColors()
    val baseContainer =
        if (isDark) {
            Color(0xFF262626).copy(alpha = 0.85f)
        } else {
            cardColors.containerColor
        }
    val cardBg =
        if (noteAccent != null) {
            val tintAlpha = if (isDark) 0.08f else 0.06f
            noteAccent.copy(alpha = tintAlpha).compositeOver(baseContainer)
        } else {
            baseContainer
        }
    val cardBorder =
        if (noteAccent != null) {
            BorderStroke(1.dp, noteAccent.copy(alpha = if (isDark) 0.35f else 0.25f))
        } else {
            BorderStroke(1.dp, if (isDark) Color(0xFF3A3A3A) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f))
        }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = cardBg,
        border = cardBorder,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val iconBoxBg =
                if (isDark) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBoxBg),
                contentAlignment = Alignment.Center,
            ) {
                RememberMaterialRoundedSymbol(
                    name = iconName,
                    size = 22.dp,
                    tint = primaryColor,
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Title with Match Highlight
                val titleText =
                    result.note.title.ifBlank {
                        if (result.note.kind == NoteKind.NOTE) {
                            stringResource(R.string.edit_note_title_new)
                        } else {
                            stringResource(R.string.edit_list_title_new)
                        }
                    }
                Text(
                    text = buildHighlightedString(titleText, searchQuery, highlightColor, highlightBg),
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = onSurface,
                        ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Entity-specific Snippet
                if (isEvent) {
                    val eventTimestamp = SearchViewModel.getEntityTimestamp(result)
                    if (eventTimestamp != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "schedule",
                                size = 14.dp,
                                tint = primaryColor,
                            )
                            Text(
                                text = formatEventDateTime(eventTimestamp),
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryColor,
                                    ),
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (result.note.body.isNotBlank()) {
                        val bodySnippet = buildSnippetAroundMatch(result.note.body, searchQuery)
                        Text(
                            text = buildHighlightedString(bodySnippet, searchQuery, highlightColor, highlightBg),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    color = onSurfaceVariant,
                                ),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                } else if (isTask) {
                    if (result.items.isNotEmpty()) {
                        val matchingItems = result.items.filter { it.text.contains(searchQuery, ignoreCase = true) }
                        val displayItems = if (matchingItems.isNotEmpty()) matchingItems.take(3) else result.items.take(3)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            displayItems.forEach { item ->
                                val checkPrefix = if (item.checked) "☑ " else "☐ "
                                val itemAnnotated = buildHighlightedString(item.text, searchQuery, highlightColor, highlightBg)
                                Text(
                                    text =
                                        buildAnnotatedString {
                                            append(checkPrefix)
                                            append(itemAnnotated)
                                        },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 13.sp,
                                            color = if (item.checked) onSurfaceVariant.copy(alpha = 0.6f) else onSurface,
                                        ),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                } else {
                    if (result.note.body.isNotBlank()) {
                        val bodySnippet = buildSnippetAroundMatch(result.note.body, searchQuery)
                        Text(
                            text = buildHighlightedString(bodySnippet, searchQuery, highlightColor, highlightBg),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    color = onSurfaceVariant,
                                ),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                // Metadata Row (Type + Date + Shoulder badge + Archive badge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val dateText =
                        remember(result.note.updatedAt, result.note.reminderAt) {
                            when {
                                isEvent -> {
                                    val t = SearchViewModel.getEntityTimestamp(result)
                                    if (t != null) "Event · " + formatSimpleDate(t) else "Event"
                                }
                                isTask -> {
                                    if (result.note.reminderAt != null) {
                                        "Task · Due " + formatSimpleDate(result.note.reminderAt)
                                    } else {
                                        "Task · Updated " + formatSimpleDate(result.note.updatedAt)
                                    }
                                }
                                else -> "Note · Updated " + formatSimpleDate(result.note.updatedAt)
                            }
                        }

                    Text(
                        text = dateText,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                color = onSurfaceVariant,
                            ),
                    )

                    // Shoulder chip with actual stored category color and high contrast
                    if (result.shoulder != null) {
                        val fallbackPrimary = MaterialTheme.colorScheme.primary
                        val shoulderColor =
                            remember(result.shoulder.colorHex, fallbackPrimary) {
                                parseNoteColorHex(result.shoulder.colorHex, fallbackPrimary)
                            }
                        val (badgeBg, badgeFg) =
                            remember(shoulderColor, isDark) {
                                readableBadgeColors(shoulderColor, isDark)
                            }
                        Surface(
                            shape = CircleShape,
                            color = badgeBg,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                RememberMaterialRoundedSymbol(
                                    name = "local_offer",
                                    size = 11.dp,
                                    tint = badgeFg,
                                    weight = FontWeight.Bold,
                                    filled = false,
                                )
                                Text(
                                    text = result.shoulder.name,
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeFg,
                                        ),
                                )
                            }
                        }
                    }

                    // Archive badge
                    if (result.note.archived) {
                        val archiveBg =
                            if (isDark) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.60f)
                            }
                        val archiveFg =
                            if (isDark) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        Surface(
                            shape = CircleShape,
                            color = archiveBg,
                            border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)) else null,
                        ) {
                            Text(
                                text = stringResource(R.string.edit_archived_notice_title),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                style =
                                    MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = archiveFg,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

fun buildHighlightedString(
    text: String,
    query: String,
    highlightColor: Color = Color.Unspecified,
    highlightBg: Color = Color.Unspecified,
): AnnotatedString {
    val trimmed = query.trim()
    if (trimmed.isEmpty() || !text.contains(trimmed, ignoreCase = true)) {
        return AnnotatedString(text)
    }

    val resolvedHighlightColor = if (highlightColor != Color.Unspecified) highlightColor else Color.Unspecified
    val resolvedHighlightBg = if (highlightBg != Color.Unspecified) highlightBg else if (resolvedHighlightColor != Color.Unspecified) resolvedHighlightColor.copy(alpha = 0.20f) else Color(0x33808080)

    return buildAnnotatedString {
        var currentIndex = 0
        val lowerText = text.lowercase()
        val lowerQuery = trimmed.lowercase()
        val queryLen = lowerQuery.length

        while (currentIndex < text.length) {
            val matchIndex = lowerText.indexOf(lowerQuery, currentIndex)
            if (matchIndex == -1) {
                append(text.substring(currentIndex))
                break
            }

            if (matchIndex > currentIndex) {
                append(text.substring(currentIndex, matchIndex))
            }

            val matchEnd = matchIndex + queryLen
            pushStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = resolvedHighlightColor,
                    background = resolvedHighlightBg,
                ),
            )
            append(text.substring(matchIndex, matchEnd))
            pop()

            currentIndex = matchEnd
        }
    }
}

fun buildSnippetAroundMatch(
    text: String,
    query: String,
    maxChars: Int = 110,
): String {
    val clean = text.replace("\n", " ").trim()
    if (clean.isBlank()) return ""
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) {
        return if (clean.length <= maxChars) clean else clean.take(maxChars).trimEnd() + "…"
    }

    val matchIndex = clean.indexOf(trimmedQuery, ignoreCase = true)
    if (matchIndex == -1 || clean.length <= maxChars) {
        return if (clean.length <= maxChars) clean else clean.take(maxChars).trimEnd() + "…"
    }

    val prefixChars = 25
    val start = maxOf(0, matchIndex - prefixChars)
    val end = minOf(clean.length, start + maxChars)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < clean.length) "…" else ""
    return prefix + clean.substring(start, end).trim() + suffix
}

private fun formatEventDateTime(reminderAt: Long): String {
    val eventCal = Calendar.getInstance().apply { timeInMillis = reminderAt }
    val nowCal = Calendar.getInstance()
    val isToday =
        eventCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
            eventCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    val timeStr = timeFormat.format(Date(reminderAt))
    return if (isToday) {
        "Today at $timeStr"
    } else {
        "${dateFormat.format(Date(reminderAt))} at $timeStr"
    }
}

private fun formatSimpleDate(timestamp: Long): String = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))

private fun formatRecentSearchDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffMillis = now - timestamp
    val diffDays = diffMillis / (24 * 60 * 60 * 1000)
    return when {
        diffDays == 0L -> "Today"
        diffDays == 1L -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
