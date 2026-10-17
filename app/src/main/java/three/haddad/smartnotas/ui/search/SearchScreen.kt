package three.haddad.smartnotas.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import three.haddad.smartnotas.R
import three.haddad.smartnotas.data.NoteKind
import three.haddad.smartnotas.data.NoteWithItems
import three.haddad.smartnotas.data.SearchHistoryEntry
import three.haddad.smartnotas.data.ShoulderEntity
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.settings.SettingsDesignTokens
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
    val uiState by viewModel.uiState.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val activeShoulders by viewModel.activeShoulders.collectAsState()

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

    val categories = listOf(
        stringResource(R.string.search_tab_all),
        stringResource(R.string.search_tab_notes),
        stringResource(R.string.search_tab_tasks),
        stringResource(R.string.search_tab_events),
    )

    Scaffold(
        containerColor = SettingsDesignTokens.ScreenBg,
        topBar = {
            Column(
                modifier = Modifier
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
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = Color(0xFF272936),
                        ),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.search_subtitle),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            color = Color(0xFF7C7D8B),
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
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RememberMaterialRoundedSymbol(
                                name = "search",
                                size = 22.dp,
                                tint = Color(0xFF7C7D8B),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (uiState.query.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.search_placeholder),
                                        color = Color(0xFF7C7D8B),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                    )
                                }
                                BasicTextField(
                                    value = uiState.query,
                                    onValueChange = onQueryChange,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(
                                        onSearch = { onSubmitSearch(uiState.query) },
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = Color(0xFF272936),
                                        fontSize = 15.sp,
                                    ),
                                )
                            }
                            if (uiState.query.isNotEmpty()) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable { onQueryChange("") },
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        RememberMaterialRoundedSymbol(
                                            name = "close",
                                            size = 18.dp,
                                            tint = Color(0xFF7C7D8B),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Filter Button with Active Badge
                    Box {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showFilters = true },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                RememberMaterialRoundedSymbol(
                                    name = "tune",
                                    size = 22.dp,
                                    tint = if (uiState.filters.activeCount > 0) Color(0xFF945394) else Color(0xFF272936),
                                )
                            }
                        }
                        if (uiState.filters.activeCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF945394),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(18.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = uiState.filters.activeCount.toString(),
                                        color = Color.White,
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

                        val typeCount = when (index) {
                            0 -> uiState.allResultsCount
                            1 -> uiState.notesResultsCount
                            2 -> uiState.tasksResultsCount
                            3 -> uiState.eventsResultsCount
                            else -> 0
                        }

                        val iconName = when (index) {
                            1 -> "description"
                            2 -> "checklist"
                            3 -> "event"
                            else -> null
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) Color(0xFF945394) else Color.White,
                            border = if (isSelected) null else BorderStroke(1.dp, Color(0xFFE9E3EF)),
                            modifier = Modifier
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
                                        tint = if (isSelected) Color.White else Color(0xFF7C7D8B),
                                    )
                                }
                                Text(
                                    text = title,
                                    color = if (isSelected) Color.White else Color(0xFF7C7D8B),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                    ),
                                )
                                if (uiState.query.isNotBlank()) {
                                    Text(
                                        text = typeCount.toString(),
                                        color = if (isSelected) Color.White.copy(alpha = 0.85f) else Color(0xFF945394),
                                        style = MaterialTheme.typography.labelLarge.copy(
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
            modifier = Modifier
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
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFFF4EAF6),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Canvas(modifier = Modifier.size(24.dp)) {
                                    val purple = Color(0xFF945394)
                                    val p = Path().apply {
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
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color(0xFF945394),
                                        ),
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.search_intro_desc),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 13.sp,
                                            color = Color(0xFF7C7D8B),
                                        ),
                                    )
                                }
                            }
                        }
                    }

                    item {
                        if (recentSearches.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.search_recent_searches_title),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = Color(0xFF272936),
                                    ),
                                )
                                Text(
                                    text = stringResource(R.string.search_clear_all),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF945394),
                                    ),
                                    modifier = Modifier.clickable { onClearRecentSearches() },
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = Color.White,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column {
                                    recentSearches.forEachIndexed { index, entry ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onSubmitSearch(entry.query)
                                                }
                                                .padding(horizontal = 18.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RememberMaterialRoundedSymbol(
                                                name = "history",
                                                size = 20.dp,
                                                tint = Color(0xFF7C7D8B),
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = entry.query,
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = 15.sp,
                                                        color = Color(0xFF272936),
                                                    ),
                                                )
                                                Text(
                                                    text = formatRecentSearchDate(entry.timestamp),
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF7C7D8B),
                                                    ),
                                                )
                                            }
                                            Surface(
                                                shape = CircleShape,
                                                color = Color.Transparent,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .clickable { onRemoveRecentSearch(entry.query) },
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    RememberMaterialRoundedSymbol(
                                                        name = "close",
                                                        size = 18.dp,
                                                        tint = Color(0xFF7C7D8B),
                                                    )
                                                }
                                            }
                                        }
                                        if (index < recentSearches.size - 1) {
                                            HorizontalDivider(
                                                color = Color(0xFFF0EFF4),
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
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    color = Color(0xFF7C7D8B),
                                ),
                            )
                        }
                    }
                }
            } else if (uiState.searchResults.isEmpty() && !uiState.isLoading) {
                // Empty Search Results State
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF4EAF6),
                            modifier = Modifier.size(80.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                RememberMaterialRoundedSymbol(
                                    name = "search_off",
                                    size = 36.dp,
                                    tint = Color(0xFF945394),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.search_empty_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Color(0xFF272936),
                            ),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.search_empty_subtitle, uiState.query),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                color = Color(0xFF7C7D8B),
                            ),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF945394),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onQueryChange("") },
                        ) {
                            Text(
                                text = stringResource(R.string.search_clear_button),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                ),
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            } else {
                // Results State
                val sorts = listOf(
                    SearchSortMode.RELEVANCE to stringResource(R.string.search_sort_relevance),
                    SearchSortMode.NEWEST to stringResource(R.string.search_sort_newest),
                    SearchSortMode.OLDEST to stringResource(R.string.search_sort_oldest),
                )

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val resultsCount = uiState.searchResults.size
                        Text(
                            text = pluralStringResource(R.plurals.search_results_count, resultsCount, resultsCount),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF272936),
                            ),
                        )
                        val scopeText = when (uiState.filters.typeIndex) {
                            1 -> stringResource(R.string.search_scope_in_notes)
                            2 -> stringResource(R.string.search_scope_in_tasks)
                            3 -> stringResource(R.string.search_scope_in_events)
                            else -> stringResource(R.string.search_scope_across_all)
                        }
                        Text(
                            text = scopeText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                color = Color(0xFF7C7D8B),
                            ),
                        )
                    }

                    // Sort row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.search_sort_by),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                color = Color(0xFF7C7D8B),
                            ),
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(sorts) { (mode, label) ->
                                val isSelected = uiState.sortMode == mode
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) Color(0xFFF4EAF6) else Color.White,
                                    border = if (isSelected) null else BorderStroke(1.dp, Color(0xFFE9E3EF)),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { onSortModeChange(mode) },
                                ) {
                                    Text(
                                        text = label,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        color = if (isSelected) Color(0xFF945394) else Color(0xFF89899C),
                                        style = MaterialTheme.typography.labelLarge.copy(
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    color = Color(0xFF7C7D8B),
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SettingsDesignTokens.ScreenBg,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.search_filter_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color(0xFF272936),
                    ),
                )
                Text(
                    text = stringResource(R.string.search_filter_subtitle),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        color = Color(0xFF7C7D8B),
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
                            tint = Color(0xFF7C7D8B),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = searchQuery,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                color = Color(0xFF7C7D8B),
                            ),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (draftFilters.activeCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = Color(0xFFF4EAF6),
                            ) {
                                Text(
                                    text = stringResource(R.string.search_filter_active_count, draftFilters.activeCount),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF945394),
                                    ),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // 1. Type
                        Text(
                            text = stringResource(R.string.search_filter_type),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF272936),
                            ),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(categories) { idx, type ->
                                val selected = draftFilters.typeIndex == idx
                                FilterChip(
                                    selected = selected,
                                    onClick = { draftFilters = draftFilters.copy(typeIndex = idx) },
                                    label = { Text(type) },
                                    border = if (selected) null else FilterChipDefaults.filterChipBorder(enabled = true, selected = false, borderColor = Color.Transparent),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = Color(0xFFF5F5F8),
                                        labelColor = Color(0xFF89899C),
                                        selectedContainerColor = Color(0xFFF4EAF6),
                                        selectedLabelColor = Color(0xFF945394),
                                    ),
                                    shape = RoundedCornerShape(100.dp),
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp), color = Color(0xFFEBEBF1))

                        // 2. Date range
                        Text(
                            text = stringResource(R.string.search_filter_date_range),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF272936),
                            ),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val ranges = listOf(
                            stringResource(R.string.search_filter_range_any),
                            stringResource(R.string.search_filter_range_this_month),
                            stringResource(R.string.search_filter_range_custom),
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(ranges) { idx, range ->
                                val selected = draftFilters.dateRangeIndex == idx
                                FilterChip(
                                    selected = selected,
                                    onClick = { draftFilters = draftFilters.copy(dateRangeIndex = idx) },
                                    label = { Text(range) },
                                    border = if (selected) null else FilterChipDefaults.filterChipBorder(enabled = true, selected = false, borderColor = Color.Transparent),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = Color(0xFFF5F5F8),
                                        labelColor = Color(0xFF89899C),
                                        selectedContainerColor = Color(0xFFF4EAF6),
                                        selectedLabelColor = Color(0xFF945394),
                                    ),
                                    shape = RoundedCornerShape(100.dp),
                                )
                            }
                        }
                        if (draftFilters.dateRangeIndex == 2) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF5F5F8),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clickable { showStartDatePicker = true },
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                    ) {
                                        RememberMaterialRoundedSymbol(name = "calendar_month", size = 18.dp, tint = Color(0xFF7C7D8B))
                                        Spacer(Modifier.width(8.dp))
                                        val startDateText = if (draftFilters.customStartDate != null) {
                                            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(draftFilters.customStartDate!!))
                                        } else {
                                            stringResource(R.string.search_filter_start_date)
                                        }
                                        Text(
                                            text = startDateText,
                                            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF272936)),
                                        )
                                    }
                                }
                                Text("–", color = Color(0xFF7C7D8B))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF5F5F8),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clickable { showEndDatePicker = true },
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                    ) {
                                        RememberMaterialRoundedSymbol(name = "calendar_month", size = 18.dp, tint = Color(0xFF7C7D8B))
                                        Spacer(Modifier.width(8.dp))
                                        val endDateText = if (draftFilters.customEndDate != null) {
                                            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(draftFilters.customEndDate!!))
                                        } else {
                                            stringResource(R.string.search_filter_end_date)
                                        }
                                        Text(
                                            text = endDateText,
                                            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF272936)),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.search_filter_date_hint),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF89899C)),
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp), color = Color(0xFFEBEBF1))

                        // 3. Task status
                        Text(
                            text = stringResource(R.string.search_filter_task_status),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF272936),
                            ),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val statuses = listOf(
                            stringResource(R.string.search_filter_status_any),
                            stringResource(R.string.search_filter_status_todo),
                            stringResource(R.string.search_filter_status_completed),
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(statuses) { idx, status ->
                                val selected = draftFilters.taskStatusIndex == idx
                                FilterChip(
                                    selected = selected,
                                    onClick = { draftFilters = draftFilters.copy(taskStatusIndex = idx) },
                                    label = { Text(status) },
                                    border = if (selected) null else FilterChipDefaults.filterChipBorder(enabled = true, selected = false, borderColor = Color.Transparent),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = Color(0xFFF5F5F8),
                                        labelColor = Color(0xFF89899C),
                                        selectedContainerColor = Color(0xFFF4EAF6),
                                        selectedLabelColor = Color(0xFF945394),
                                    ),
                                    shape = RoundedCornerShape(100.dp),
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp), color = Color(0xFFEBEBF1))

                        // 4. Shoulders
                        Text(
                            text = stringResource(R.string.search_filter_shoulders),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF272936),
                            ),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        var shoulderDropdownExpanded by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF5F5F8),
                                modifier = Modifier
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
                                    Text(
                                        text = selectedShoulder?.name ?: stringResource(R.string.search_filter_shoulders_any),
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF272936)),
                                    )
                                    RememberMaterialRoundedSymbol(name = "expand_more", size = 20.dp, tint = Color(0xFF7C7D8B))
                                }
                            }
                            DropdownMenu(
                                expanded = shoulderDropdownExpanded,
                                onDismissRequest = { shoulderDropdownExpanded = false },
                                modifier = Modifier.background(Color.White),
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.search_filter_shoulders_any)) },
                                    onClick = {
                                        draftFilters = draftFilters.copy(shoulderId = null)
                                        shoulderDropdownExpanded = false
                                    },
                                )
                                activeShoulders.forEach { shoulder ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (!shoulder.colorHex.isNullOrBlank()) {
                                                    val color = runCatching { Color(android.graphics.Color.parseColor(shoulder.colorHex)) }.getOrDefault(Color(0xFF945394))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(10.dp)
                                                            .clip(CircleShape)
                                                            .background(color)
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                }
                                                Text(shoulder.name)
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
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF89899C)),
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp), color = Color(0xFFEBEBF1))

                        // 5. Include archived
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.search_filter_include_archived),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF272936),
                                    ),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val archiveHint = if (draftFilters.includeArchived) {
                                    stringResource(R.string.search_filter_archived_shown_hint)
                                } else {
                                    stringResource(R.string.search_filter_archived_hidden_hint)
                                }
                                Text(
                                    text = archiveHint,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF89899C)),
                                )
                            }
                            Switch(
                                checked = draftFilters.includeArchived,
                                onCheckedChange = { draftFilters = draftFilters.copy(includeArchived = it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF945394),
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFDDDDE5),
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
                            draftFilters = SearchFilters(typeIndex = draftFilters.typeIndex)
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.search_filter_reset),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF945394),
                                fontSize = 16.sp,
                            ),
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Color(0xFF945394),
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .clickable {
                                onApply(draftFilters)
                            },
                    ) {
                        val applyText = if (draftFilters.activeCount > 0) {
                            stringResource(R.string.search_filter_apply_with_count, draftFilters.activeCount)
                        } else {
                            stringResource(R.string.search_filter_apply)
                        }
                        Text(
                            text = applyText,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp,
                            ),
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = draftFilters.customStartDate ?: System.currentTimeMillis()
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
                    }
                ) {
                    Text("OK", color = Color(0xFF945394), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text("Cancel", color = Color(0xFF7C7D8B))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = draftFilters.customEndDate ?: System.currentTimeMillis()
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
                    }
                ) {
                    Text("OK", color = Color(0xFF945394), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text("Cancel", color = Color(0xFF7C7D8B))
                }
            }
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

    val iconName = when {
        isEvent -> "event"
        isTask -> "checklist"
        else -> "description"
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF4EAF6)),
                contentAlignment = Alignment.Center,
            ) {
                RememberMaterialRoundedSymbol(
                    name = iconName,
                    size = 22.dp,
                    tint = Color(0xFF945394),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Title with Match Highlight
                val titleText = result.note.title.ifBlank { "Untitled" }
                Text(
                    text = buildHighlightedString(titleText, searchQuery),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF272936),
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
                                tint = Color(0xFF945394),
                            )
                            Text(
                                text = formatEventDateTime(eventTimestamp),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF945394),
                                ),
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (result.note.body.isNotBlank()) {
                        val bodySnippet = buildSnippetAroundMatch(result.note.body, searchQuery)
                        Text(
                            text = buildHighlightedString(bodySnippet, searchQuery),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                color = Color(0xFF7C7D8B),
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
                                val itemAnnotated = buildHighlightedString(item.text, searchQuery)
                                Text(
                                    text = buildAnnotatedString {
                                        append(checkPrefix)
                                        append(itemAnnotated)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        color = if (item.checked) Color(0xFF89899C) else Color(0xFF272936),
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
                            text = buildHighlightedString(bodySnippet, searchQuery),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                color = Color(0xFF7C7D8B),
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
                    val dateText = remember(result.note.updatedAt, result.note.reminderAt) {
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
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = Color(0xFF7C7D8B),
                        ),
                    )

                    // Shoulder chip
                    if (result.shoulder != null) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Color(0xFFF5F5F8),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (!result.shoulder.colorHex.isNullOrBlank()) {
                                    val sColor = runCatching { Color(android.graphics.Color.parseColor(result.shoulder.colorHex)) }.getOrDefault(Color(0xFF945394))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(sColor)
                                    )
                                }
                                Text(
                                    text = result.shoulder.name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF272936),
                                    ),
                                )
                            }
                        }
                    }

                    // Archive badge
                    if (result.note.archived) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Color(0xFFFFF4E5),
                        ) {
                            Text(
                                text = "Archived",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB76E00),
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
    highlightColor: Color = Color(0xFF945394),
    highlightBg: Color = Color(0x33945394),
): AnnotatedString {
    val trimmed = query.trim()
    if (trimmed.isEmpty() || !text.contains(trimmed, ignoreCase = true)) {
        return AnnotatedString(text)
    }

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
                    color = highlightColor,
                    background = highlightBg,
                )
            )
            append(text.substring(matchIndex, matchEnd))
            pop()

            currentIndex = matchEnd
        }
    }
}

fun buildSnippetAroundMatch(text: String, query: String, maxChars: Int = 110): String {
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
    val isToday = eventCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
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

private fun formatSimpleDate(timestamp: Long): String {
    return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
}

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
