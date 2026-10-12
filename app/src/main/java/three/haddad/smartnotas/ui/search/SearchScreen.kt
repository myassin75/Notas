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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import three.haddad.smartnotas.data.ShoulderEntity
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
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol
import three.haddad.smartnotas.ui.settings.SettingsDesignTokens
import java.text.SimpleDateFormat
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
    onRemoveRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onOpenNote: (NoteWithItems) -> Unit,
    onApplyFilters: (SearchFilters) -> Unit,
) {
    var showFilters by remember { mutableStateOf(false) }
    var draftFilters by remember(uiState.filters) { mutableStateOf(uiState.filters) }

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
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp),
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                RememberMaterialRoundedSymbol(
                                    name = "apps",
                                    size = 22.dp,
                                    tint = Color(0xFF272936),
                                )
                            }
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.main_tab_search),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    color = Color(0xFF272936),
                                ),
                            )
                            Text(
                                text = "Find your notes, tasks & events",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    color = Color(0xFF7C7D8B),
                                ),
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            RememberMaterialRoundedSymbol(
                                name = "notifications",
                                size = 22.dp,
                                tint = Color(0xFF272936),
                            )
                        }
                    }
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
                                tint = Color(0xFF272936),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Category Pills Row
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

                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) Color(0xFF945394) else Color.White,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onCategoryIndexChange(index) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = title,
                                    color = if (isSelected) Color.White else Color(0xFF7C7D8B),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                    ),
                                )
                                if (uiState.query.isNotBlank() && isSelected) {
                                    Text(
                                        text = typeCount.toString(),
                                        color = Color.White.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                        ),
                                    )
                                } else if (uiState.query.isNotBlank() && !isSelected) {
                                    Text(
                                        text = typeCount.toString(),
                                        color = Color(0xFF7C7D8B).copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
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
            val resultsCount = when (uiState.filters.typeIndex) {
                0 -> uiState.allResultsCount
                1 -> uiState.notesResultsCount
                2 -> uiState.tasksResultsCount
                3 -> 0
                else -> 0
            }

            if (uiState.query.isEmpty()) {
                // Initial State: Recent Searches & Informational Card
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
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
                                    text = "Recent searches",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = Color(0xFF272936),
                                    ),
                                )
                                Text(
                                    text = "Clear all",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Medium,
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
                                text = "Tap a query to search again. × removes it from history.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    color = Color(0xFF7C7D8B),
                                ),
                            )
                        }
                    }

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
                                        text = "One search, everything in Notas",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color(0xFF945394),
                                        ),
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Find a title or a word inside your notes, tasks and events. Use filters to narrow things down.",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 13.sp,
                                            color = Color(0xFF7C7D8B),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (resultsCount == 0) {
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
                            text = "No matches this time",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = Color(0xFF272936),
                            ),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No tasks or notes match \"${uiState.query}\" with these filters.\nTry widening your search.",
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
                                text = "Clear search",
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
                var selectedSort by remember { mutableStateOf("Relevance") }
                val sorts = listOf("Relevance", "Newest", "Oldest")

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "$resultsCount results",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF272936),
                            ),
                        )
                        val typeText = when (uiState.filters.typeIndex) {
                            1 -> "In Notes"
                            2 -> "In Tasks"
                            3 -> "In Events"
                            else -> "Across all types"
                        }
                        Text(
                            text = typeText,
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
                            text = "Sort by",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                color = Color(0xFF7C7D8B),
                            ),
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(sorts) { sort ->
                                val isSelected = selectedSort == sort
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) Color(0xFFF4EAF6) else Color.White,
                                    border = if (isSelected) null else BorderStroke(1.dp, Color(0xFFE9E3EF)),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { selectedSort = sort },
                                ) {
                                    Text(
                                        text = sort,
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

                    val filteredResults = when (uiState.filters.typeIndex) {
                        1 -> uiState.searchResults.filter { it.note.kind == NoteKind.NOTE }
                        2 -> uiState.searchResults.filter { it.note.kind == NoteKind.LIST }
                        else -> uiState.searchResults
                    }

                    val sortedResults = when (selectedSort) {
                        "Newest" -> filteredResults.sortedByDescending { it.note.updatedAt }
                        "Oldest" -> filteredResults.sortedBy { it.note.updatedAt }
                        else -> filteredResults
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(sortedResults, key = { it.note.id }) { result ->
                            SearchResultCard(
                                result = result,
                                onClick = { onOpenNote(result) },
                            )
                        }
                        item {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "You've seen all ${sortedResults.size} results",
                                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
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
        ModalBottomSheet(
            onDismissRequest = {
                showFilters = false
            },
            containerColor = SettingsDesignTokens.ScreenBg,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
            ) {
                item {
                    Text(
                        text = "Search filters",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color(0xFF272936),
                        ),
                    )
                    Text(
                        text = "Refine what you're looking for",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            color = Color(0xFF7C7D8B),
                        ),
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (uiState.query.isNotBlank()) {
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
                                text = uiState.query,
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
                                        text = "${draftFilters.activeCount} active",
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
                            // Type
                            Text(
                                text = "Type",
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

                            // Date range
                            Text(
                                text = "Date range",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF272936),
                                ),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val ranges = listOf("Any time", "This month", "Custom")
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
                                        modifier = Modifier.weight(1f).height(48.dp).clickable { /* TODO: open start picker */ },
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                        ) {
                                            RememberMaterialRoundedSymbol(name = "calendar_month", size = 18.dp, tint = Color(0xFF7C7D8B))
                                            Spacer(Modifier.width(8.dp))
                                            val startDateText = remember(draftFilters.customStartDate) {
                                                if (draftFilters.customStartDate != null) SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(draftFilters.customStartDate!!)) else "Start date"
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
                                        modifier = Modifier.weight(1f).height(48.dp).clickable { /* TODO: open end picker */ },
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                        ) {
                                            val endDateText = remember(draftFilters.customEndDate) {
                                                if (draftFilters.customEndDate != null) SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(draftFilters.customEndDate!!)) else "End date"
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
                                text = "Notes: updated · Tasks: due",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF89899C)),
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp), color = Color(0xFFEBEBF1))

                            // Task status
                            Text(
                                text = "Task status",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF272936),
                                ),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val statuses = listOf("Any status", "To do", "Completed")
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

                            // Shoulders
                            Text(
                                text = "Shoulders",
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
                                    modifier = Modifier.fillMaxWidth().height(48.dp).clickable { shoulderDropdownExpanded = true },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        val selectedShoulder = activeShoulders.find { it.id == draftFilters.shoulderId }
                                        Text(
                                            text = selectedShoulder?.name ?: "Any shoulder",
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
                                        text = { Text("Any shoulder") },
                                        onClick = {
                                            draftFilters = draftFilters.copy(shoulderId = null)
                                            shoulderDropdownExpanded = false
                                        },
                                    )
                                    activeShoulders.forEach { shoulder ->
                                        DropdownMenuItem(
                                            text = { Text(shoulder.name) },
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
                                text = "Includes assigned and unassigned items",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF89899C)),
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp), color = Color(0xFFEBEBF1))

                            // Include archived
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = "Include archived",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color(0xFF272936),
                                        ),
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Archived items are currently hidden",
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

                    Spacer(modifier = Modifier.height(32.dp))
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
                                text = "Reset",
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
                            modifier = Modifier.clip(RoundedCornerShape(100.dp)).clickable {
                                onApplyFilters(draftFilters)
                                showFilters = false
                            },
                        ) {
                            Text(
                                text = if (draftFilters.activeCount > 0) "Apply filters (${draftFilters.activeCount})" else "Apply filters",
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
    }
}

@Composable
fun SearchResultCard(
    result: NoteWithItems,
    onClick: () -> Unit,
) {
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
                val iconName = if (result.note.kind == NoteKind.NOTE) "description" else "checklist"
                RememberMaterialRoundedSymbol(
                    name = iconName,
                    size = 22.dp,
                    tint = Color(0xFF945394),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.note.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF272936),
                    ),
                )
                Spacer(modifier = Modifier.height(4.dp))

                val bodyPreview = if (result.note.kind == NoteKind.NOTE) {
                    result.note.body
                } else {
                    result.items.take(3).joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.text }
                }
                if (bodyPreview.isNotBlank()) {
                    Text(
                        text = bodyPreview,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            color = Color(0xFF7C7D8B),
                        ),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                val date = remember(result.note.updatedAt) {
                    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(result.note.updatedAt))
                }
                val metaText = if (result.note.kind == NoteKind.NOTE) {
                    "Note · Updated $date"
                } else {
                    "Task · Updated $date"
                }
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = Color(0xFF7C7D8B),
                    ),
                )
            }
        }
    }
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
