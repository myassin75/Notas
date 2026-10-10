package three.haddad.smartnotas.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import three.haddad.smartnotas.R
import three.haddad.smartnotas.data.NoteKind
import three.haddad.smartnotas.data.NoteWithItems
import three.haddad.smartnotas.ui.common.RememberMaterialRoundedSymbol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchRoute(viewModel: SearchViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    SearchScreen(
        uiState = uiState,
        onQueryChange = viewModel::updateQuery
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    var showFilters by remember { mutableStateOf(false) }
    val tabs = listOf(
        stringResource(R.string.search_tab_all),
        stringResource(R.string.search_tab_notes),
        stringResource(R.string.search_tab_tasks),
        stringResource(R.string.search_tab_events)
    )

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.main_tab_search), fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { /*TODO*/ }) {
                            RememberMaterialRoundedSymbol(name = "notifications")
                        }
                        IconButton(onClick = { /*TODO*/ }) {
                            RememberMaterialRoundedSymbol(name = "account_circle")
                        }
                    }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Search Bar
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest // or Color.White
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RememberMaterialRoundedSymbol(name = "search", tint = Color.Gray)
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (uiState.query.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.search_placeholder),
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                BasicTextField(
                                    value = uiState.query,
                                    onValueChange = onQueryChange,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Filter button
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest) // or Color.White
                            .clickable { showFilters = true },
                        contentAlignment = Alignment.Center
                    ) {
                        RememberMaterialRoundedSymbol(
                            name = "tune",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        // Purple dot indicator
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = (-12).dp, y = 12.dp)
                                .background(Color(0xFFA64FA8), CircleShape) // Purple color
                        )
                    }
                }
                
                // Tabs
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(tabs) { index, title ->
                        val isSelected = selectedTabIndex == index
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isSelected) Color(0xFFA64FA8) else MaterialTheme.colorScheme.surfaceContainerLowest, // or Color.White
                            onClick = { selectedTabIndex = index }
                        ) {
                            Text(
                                text = title,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                color = if (isSelected) Color.White else Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val resultsCount = when (selectedTabIndex) {
                0 -> uiState.allResultsCount
                1 -> uiState.notesResultsCount
                2 -> uiState.tasksResultsCount
                3 -> 0 // Events always 0
                else -> 0
            }

            if (uiState.query.isEmpty()) {
                // Initial State
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent searches",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Clear all",
                        color = Color(0xFFA64FA8),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { /*TODO*/ }
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) // or Color.White
                ) {
                    Column {
                        val recentSearches = listOf("grocery list", "meeting notes", "gym routine")
                        recentSearches.forEachIndexed { index, query ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onQueryChange(query) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RememberMaterialRoundedSymbol(name = "history", tint = Color.Gray)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(query, fontWeight = FontWeight.Medium)
                                    Text("Today", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(
                                    onClick = { /*TODO*/ },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    RememberMaterialRoundedSymbol(name = "close", tint = Color.Gray)
                                }
                            }
                            if (index < recentSearches.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }

                Text(
                    text = "Tap a query to search again. x removes it from history.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E8F4)) // Soft purple
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_sparkle),
                            contentDescription = null,
                            tint = Color(0xFFA64FA8),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "One search, everything in Notas",
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFA64FA8),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

            } else if (resultsCount == 0) {
                // No Results State
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(Color(0xFFE6D6F4), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("S") // Placeholder for magnifying glass
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No matches this time", fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { onQueryChange("") }) {
                            Text("Clear filters")
                        }
                    }
                }
            } else {
                // Results State
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "$resultsCount results",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            val typeText = when(selectedTabIndex) {
                                1 -> "In Notes"
                                2 -> "In Tasks"
                                3 -> "In Events"
                                else -> "Across all types"
                            }
                            Text(
                                text = typeText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }

                    // Sort chips
                    var selectedSort by remember { mutableStateOf("Relevance") }
                    val sorts = listOf("Relevance", "Newest", "Oldest")
                    LazyRow(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sorts) { sort ->
                            FilterChip(
                                selected = selectedSort == sort,
                                onClick = { selectedSort = sort },
                                label = { Text(sort) },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }

                    val filteredResults = when(selectedTabIndex) {
                        1 -> uiState.searchResults.filter { it.note.kind == NoteKind.NOTE }
                        2 -> uiState.searchResults.filter { it.note.kind == NoteKind.LIST }
                        // events will be empty
                        else -> uiState.searchResults
                    }
                    
                    val sortedResults = when(selectedSort) {
                        "Newest" -> filteredResults.sortedByDescending { it.note.updatedAt }
                        "Oldest" -> filteredResults.sortedBy { it.note.updatedAt }
                        else -> filteredResults // Relevance keeps the original order
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(sortedResults, key = { it.note.id }) { result ->
                            SearchResultCard(result = result)
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            containerColor = Color(0xFFF8F7FC), // requested background
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text("Filters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                // Type
                Text("Type", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Notes", "Tasks", "Events").forEach { type ->
                        FilterChip(
                            selected = type == "All",
                            onClick = { /* TODO */ },
                            label = { Text(type) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0xFFFFFFFF),
                                selectedContainerColor = Color(0xFFF1E5F4),
                                selectedLabelColor = Color(0xFFA64FA8)
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Date range
                Text("Date range", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Any time", "Today", "Past week", "Past month").forEach { range ->
                        FilterChip(
                            selected = range == "Any time",
                            onClick = { /* TODO */ },
                            label = { Text(range) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0xFFFFFFFF),
                                selectedContainerColor = Color(0xFFF1E5F4),
                                selectedLabelColor = Color(0xFFA64FA8)
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Task status
                Text("Task status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Any", "To do", "Done").forEach { status ->
                        FilterChip(
                            selected = status == "Any",
                            onClick = { /* TODO */ },
                            label = { Text(status) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0xFFFFFFFF),
                                selectedContainerColor = Color(0xFFF1E5F4),
                                selectedLabelColor = Color(0xFFA64FA8)
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Archived Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Include Archived", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = false,
                        onCheckedChange = { /* TODO */ },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFA64FA8)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    OutlinedButton(
                        onClick = { /* Reset */ },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA64FA8))
                    ) { Text("Reset") }
                    Button(
                        onClick = { showFilters = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA64FA8))
                    ) { Text("Apply") }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SearchResultCard(result: NoteWithItems) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val iconName = if (result.note.kind == NoteKind.NOTE) "description" else "check_circle"
                RememberMaterialRoundedSymbol(
                    name = iconName,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            // Content
            Column {
                Text(
                    text = result.note.title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                val date = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()).format(java.util.Date(result.note.updatedAt))
                val metaText = if (result.note.kind == NoteKind.NOTE) {
                    "Note · Updated $date"
                } else {
                    "Task · To do"
                }
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}
